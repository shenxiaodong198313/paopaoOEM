package org.fcitx.fcitx5.android.input.favorite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.view.inputmethod.EditorInfo
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.setPadding
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import splitties.dimensions.dp
import java.io.File
import java.net.URL
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class FavoriteUi(
    private val ctx: Context,
    private val enableFloatingAddMenu: Boolean = false,
) {
    private enum class PanelMode {
        LIST,
        COMPOSER,
    }

    data class TextEditorViewHolder(
        val root: View,
        val scrollView: ScrollView,
        val contentContainer: LinearLayout,
        val contentView: AppCompatEditText,
        val bottomSafeSpacer: View,
    )

    private data class AsyncPreviewTarget(
        val imageView: WeakReference<ImageView>,
        val placeholderView: WeakReference<View>,
        val requestToken: String,
    )

    data class EmbeddedMediaPickerRecyclerBinding(
        val root: RecyclerView,
        val adapter: EmbeddedMediaPickerAdapter,
    )

    companion object {
        private const val PANEL_BACKGROUND = "#F5F3FB"
        private const val CARD_BACKGROUND = "#FFFFFF"
        private const val SEARCH_ICON = "#6B4EFF"
        private const val TAB_SELECTED_BG = "#E9EDFF"
        private const val TAB_SELECTED_TEXT = "#5867D8"
        private const val TAB_IDLE_TEXT = "#98A0BF"
        private const val PRIMARY_NAV_SELECTED_TEXT = "#1F2329"
        private const val PRIMARY_NAV_IDLE_TEXT = "#8E8E93"
        private const val PRIMARY_NAV_SELECTED_INDICATOR = "#9EE7B5"
        private const val SECONDARY_NAV_SELECTED_BG = "#D9F25B"
        private const val SECONDARY_NAV_IDLE_BG = "#E5ECEA"
        private const val SECONDARY_NAV_SELECTED_TEXT = "#111111"
        private const val SECONDARY_NAV_IDLE_TEXT = "#666666"
        private const val TEXT_PRIMARY = "#2A2251"
        private const val TEXT_SECONDARY = "#6F7690"
        private const val TEXT_TERTIARY = "#A8AFC2"
        private const val DIVIDER = "#D9DCE6"
        private const val MEDIA_BG = "#F1E9FF"
        private const val DOCUMENT_BG = "#F4F5F7"
        private const val MEDIA_SKELETON_BG = "#D8D8D8"
        private const val MEDIA_SKELETON_ACCENT = "#ECECEC"
        private const val DIM_BG = "#66000000"
        private const val ACTION_BG = "#CED8F7"
        private const val DRAWER_BG = "#8C8C8C"
        private const val FLOATING_MENU_BG = "#FFFFFFFF"
        private const val FLOATING_MENU_STROKE = "#F1EEF8"
        private const val FLOATING_MENU_TEXT = "#49454F"
        private const val FLOATING_MENU_ICON_SLOT_BG = "#FFFFFFFF"
        private const val FLOATING_MENU_ICON_TINT = "#5F5A75"
        private const val COMPOSER_HEADER_HEIGHT_DP = 84
        private const val COMPOSER_HEADER_BAR_HEIGHT_DP = 70
        private const val COMPOSER_TOP_INSET_DP = 8
        private const val PANEL_CORNER_RADIUS_DP = 28
        private const val PANEL_BACKGROUND_SHADOW_TOP_INSET_DP = 18
        private val PREVIEW_CACHE_MAX_KB = (
            Runtime.getRuntime().maxMemory() / 1024 / 8
            ).toInt().coerceAtLeast(32 * 1024)
        private const val HEADER_ACTION_SIZE_DP = 40
        private const val HEADER_ICON_SIZE_DP = 24
        private const val HEADER_ICON_INSET_DP = 8
        private const val HEADER_ICON_TINT = "#111111"
        private const val HEADER_ACTION_DISABLED_BG = "#D8D8D8"
        private const val HEADER_ACTION_ENABLED_BG = "#07C160"
        private const val HEADER_ACTION_TEXT = "#FFFFFF"
        private const val HEADER_START_PADDING_DP = 20
        private const val HEADER_END_PADDING_DP = 10
        private const val HEADER_TOP_PADDING_DP = 16
        private const val HEADER_BOTTOM_PADDING_DP = 12
        private const val LIST_TITLE_TEXT_SIZE_SP = 18f
        private const val LIST_HEADER_START_PADDING_DP = 0
        private const val LIST_HEADER_END_PADDING_DP = 0
        private const val LIST_PANEL_HORIZONTAL_PADDING_DP = 18
        private const val TOP_CLOSE_BUTTON_SIZE_DP = 24
        private const val TOP_CLOSE_BUTTON_PADDING_DP = 4
        private const val TOP_CLOSE_BUTTON_HOST_SIZE_DP = 40
        private const val TOP_HEADER_ACTION_GAP_DP = 12
        private const val EMPTY_STATE_IMAGE_WIDTH_RATIO = 0.4f
        private const val EMPTY_STATE_TEXT_TOP_MARGIN_DP = 20
        private const val SEARCH_INPUT_BG = "#F2F3F7"
        private const val SEARCH_INPUT_RADIUS_DP = 22
        private const val SEARCH_INPUT_TOP_MARGIN_DP = 14
        private const val SEARCH_INPUT_TEXT_SIZE_SP = 15f
        private const val MEDIA_PICKER_COLUMN_COUNT = 4
        private const val MEDIA_PICKER_GAP_DP = 2
        private const val MEDIA_PICKER_BADGE_SIZE_DP = 20
        private const val MEDIA_PICKER_BADGE_MARGIN_DP = 6
        private const val MEDIA_PICKER_BADGE_TEXT_SIZE_SP = 11f
        private const val FILE_PICKER_TOGGLE_HOT_ZONE_MIN_WIDTH_DP = 84
        private const val FILE_PICKER_HORIZONTAL_PADDING_DP = LIST_PANEL_HORIZONTAL_PADDING_DP
        private const val FILE_PICKER_CATEGORY_TOP_MARGIN_DP = 12
        private const val FILE_PICKER_CATEGORY_BOTTOM_MARGIN_DP = 8
        private const val FILE_PICKER_UNSELECTED_BADGE_FILL = "#D9DDE8"
        private const val FILE_PICKER_UNSELECTED_BADGE_STROKE = "#C2C8D6"
        private const val FLOATING_MENU_WIDTH_DP = 120
        private const val FLOATING_MENU_ITEM_HEIGHT_DP = 46
        private const val FLOATING_MENU_ICON_SLOT_SIZE_DP = 46
        private const val FLOATING_MENU_ITEM_ICON_SIZE_DP = 25
        private const val FLOATING_MENU_MAIN_BUTTON_SIZE_DP = 46
        private const val FLOATING_MENU_MAIN_ICON_SIZE_DP = 26
        private const val FLOATING_MENU_MAIN_SHADOW_SIZE_DP = 62
        private const val FLOATING_MENU_ITEM_GAP_DP = 14
        private const val FLOATING_MENU_SIDE_MARGIN_DP = 18
        private const val FLOATING_MENU_BOTTOM_MARGIN_DP = 54
        private const val FLOATING_MENU_ITEM_ELEVATION_DP = 8
        private const val FLOATING_MENU_MAIN_BUTTON_ELEVATION_DP = 10
        private const val FLOATING_MENU_SHADOW_PADDING_DP = 10
        private const val FLOATING_MENU_TEXT_LEADING_SPACER_DP = 18
        private const val FLOATING_MENU_TEXT_SIZE_SP = 12f
        private const val FLOATING_MENU_ICON_SLOT_START_MARGIN_DP = 4
        private const val TEXT_EDITOR_BOTTOM_SAFE_GAP_DP = 32
        private const val TEXT_EDITOR_OUTER_BOTTOM_GAP_DP = 12
        private const val TEXT_EDITOR_LAST_LINE_EXTRA_GAP_DP = 12
        private const val TEXT_EDITOR_MAX_VISIBLE_LINES = 5
        private const val TEXT_EDITOR_CARD_VERTICAL_PADDING_DP = 20
        private const val TEXT_EDITOR_MIN_VIEWPORT_HEIGHT_DP = 132
        private const val TEXT_CURSOR_COLOR = "#111111"
        private const val TEXT_CURSOR_HIDDEN_ALPHA = "#00111111"
        private const val CONTENT_SWITCH_DURATION_MS = 240L
        private val previewMainHandler = Handler(Looper.getMainLooper())
        private val previewExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(4, 6)
        )
        private val previewBitmapCache = object : LruCache<String, Bitmap>(PREVIEW_CACHE_MAX_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    val barExtension: View = FrameLayout(ctx).apply {
        background = roundedDrawable(PANEL_BACKGROUND, 0)
        addView(
            View(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(3).toFloat()
                    setColor(Color.parseColor("#D8DBE7"))
                }
            },
            FrameLayout.LayoutParams(ctx.dp(48), ctx.dp(5), Gravity.CENTER)
        )
    }

    val searchActionView = FrameLayout(ctx).apply {
        minimumWidth = ctx.dp(HEADER_ACTION_SIZE_DP)
        minimumHeight = ctx.dp(HEADER_ACTION_SIZE_DP)
        isClickable = true
        isFocusable = true
    }

    private val typeTabOrder = listOf(
        FavoriteCategory.RECENT,
        FavoriteCategory.NOTES,
        FavoriteCategory.FILES,
        FavoriteCategory.IMAGES,
        FavoriteCategory.VIDEOS,
    )

    val recyclerView = RecyclerView(ctx).apply {
        clipToPadding = false
        overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS
        setPadding(0, ctx.dp(6), 0, ctx.dp(24))
    }

    private val defaultEmptyStateView = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_favorite_empty_state)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(
                (ctx.resources.displayMetrics.widthPixels * EMPTY_STATE_IMAGE_WIDTH_RATIO).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            TextView(ctx).apply {
                text = ctx.getString(R.string.favorite_empty_state)
                setTextColor(Color.parseColor(TEXT_SECONDARY))
                textSize = 15f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(EMPTY_STATE_TEXT_TOP_MARGIN_DP)
            }
        )
    }

    val emptyStateView = FrameLayout(ctx).apply {
        visibility = View.GONE
        addView(
            defaultEmptyStateView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    var onPrimaryNavigationSelected: ((String) -> Unit)? = null
    var onSecondaryNavigationSelected: ((String) -> Unit)? = null

    private var primaryNavigationItems: List<FavoriteNavigationItem> = emptyList()
    private var secondaryNavigationItems: List<FavoriteNavigationItem> = emptyList()

    val searchInputView = AppCompatEditText(ctx).apply {
        hint = ctx.getString(R.string.favorite_search_hint)
        setHintTextColor(Color.parseColor(TEXT_TERTIARY))
        setTextColor(Color.parseColor(TEXT_PRIMARY))
        textSize = SEARCH_INPUT_TEXT_SIZE_SP
        maxLines = 1
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        background = null
        setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(48), ctx.dp(10))
    }

    private val searchClearActionView = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_search_clear)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val searchClearActionHost = FrameLayout(ctx).apply {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        setPadding(ctx.dp(6), ctx.dp(10), ctx.dp(10), ctx.dp(10))
        addView(
            searchClearActionView,
            FrameLayout.LayoutParams(
                ctx.dp(16),
                ctx.dp(16),
                Gravity.CENTER,
            ),
        )
        setOnClickListener {
            searchInputView.setText("")
            focusSearchInput()
        }
    }

    private val searchInputRow = FrameLayout(ctx).apply {
        background = roundedDrawable(SEARCH_INPUT_BG, SEARCH_INPUT_RADIUS_DP)
        visibility = View.GONE
        addView(
            searchInputView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            searchClearActionHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ),
        )
    }

    init {
        searchInputView.addTextChangedListener { syncSearchClearVisibility() }
    }

    val searchEmptyStateView = TextView(ctx).apply {
        text = ctx.getString(R.string.favorite_search_no_result)
        setTextColor(Color.parseColor(TEXT_SECONDARY))
        textSize = SEARCH_INPUT_TEXT_SIZE_SP
        gravity = Gravity.CENTER
    }

    val searchIdleStateView = FrameLayout(ctx)

    val composerCloseButton = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_panel_back)
        imageTintList = null
        contentDescription = ctx.getString(R.string.favorite_back)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val composerBackLabel = TextView(ctx).apply {
        text = ctx.getString(R.string.favorite_back)
        setTextColor(Color.parseColor(HEADER_ICON_TINT))
        textSize = 17f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }

    val composerBackAction = FrameLayout(ctx).apply {
        isClickable = true
        isFocusable = true
        minimumWidth = ctx.dp(HEADER_ACTION_SIZE_DP)
        minimumHeight = ctx.dp(HEADER_ACTION_SIZE_DP)
        addView(
            composerCloseButton,
            FrameLayout.LayoutParams(
                ctx.dp(HEADER_ICON_SIZE_DP),
                ctx.dp(HEADER_ICON_SIZE_DP),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )
    }

    val composerSaveButton = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_save_action_24)
        contentDescription = ctx.getString(R.string.save)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP)
        )
    }

    val composerTextActionButton = TextView(ctx).apply {
        visibility = View.GONE
        gravity = Gravity.CENTER
        minWidth = ctx.dp(74)
        minHeight = ctx.dp(32)
        setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        textSize = 14f
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        setTextColor(Color.parseColor(HEADER_ACTION_TEXT))
        background = roundedDrawable(HEADER_ACTION_DISABLED_BG, 16)
        isClickable = true
        isFocusable = true
    }

    val composerTitleView = TextView(ctx).apply {
        setTextColor(Color.parseColor(TEXT_PRIMARY))
        textSize = 18f
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        gravity = Gravity.CENTER
        visibility = View.GONE
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    val composerContentScroll = ScrollView(ctx).apply {
        isFillViewport = true
        overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    val composerContent = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(18), ctx.dp(16), ctx.dp(18), ctx.dp(28))
    }

    val composerTransientContentContainer = FrameLayout(ctx).apply {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
    }

    val composerKeyboardContainer = FrameLayout(ctx).apply {
        visibility = View.GONE
    }

    val addActionButton = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_add_action_24)
        contentDescription = ctx.getString(R.string.favorite_add)
        isClickable = true
        isFocusable = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP)
        )
    }

    val downloadActionButton = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_download_24)
        contentDescription = ctx.getString(R.string.favorite_download)
        isClickable = true
        isFocusable = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.GONE
        setPadding(
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP),
            ctx.dp(HEADER_ICON_INSET_DP)
        )
    }

    val listAddActionView = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_panel_close)
        contentDescription = ctx.getString(R.string.favorite_close)
        isClickable = true
        isFocusable = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        imageTintList = null
        setPadding(0, 0, 0, 0)
    }

    val listManageActionView = TextView(ctx).apply {
        text = ctx.getString(R.string.favorite_manage_taxonomy)
        setTextColor(Color.parseColor("#2B95EB"))
        textSize = 14f
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = ctx.dp(30)
        setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        background = roundedDrawable("#E9F4FF", 15)
        visibility = View.VISIBLE
        isClickable = true
        isFocusable = true
    }

    private val listCloseActionHost = FrameLayout(ctx).apply {
        visibility = View.VISIBLE
        addView(
            listAddActionView,
            FrameLayout.LayoutParams(
                ctx.dp(TOP_CLOSE_BUTTON_SIZE_DP),
                ctx.dp(TOP_CLOSE_BUTTON_SIZE_DP),
                Gravity.CENTER
            )
        )
    }

    private val listSearchActionHost = FrameLayout(ctx).apply {
        visibility = View.VISIBLE
        addView(
            searchActionView,
            FrameLayout.LayoutParams(
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                Gravity.CENTER
            )
        )
    }

    val aiActionButton = TextView(ctx).apply {
        text = "Ai"
        setTextColor(Color.parseColor("#2B95EB"))
        textSize = 18f
        gravity = Gravity.CENTER
        minHeight = ctx.dp(70)
        isClickable = true
        isFocusable = true
        background = roundedDrawable("#D8E3FF", 36)
    }

    private val composerActionGroup = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            composerTextActionButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            downloadActionButton,
            LinearLayout.LayoutParams(ctx.dp(HEADER_ACTION_SIZE_DP), ctx.dp(HEADER_ACTION_SIZE_DP))
                .apply {
                    marginStart = ctx.dp(12)
                }
        )
        addView(
            addActionButton,
            LinearLayout.LayoutParams(ctx.dp(HEADER_ACTION_SIZE_DP), ctx.dp(HEADER_ACTION_SIZE_DP))
                .apply {
                    marginStart = ctx.dp(12)
                }
        )
        addView(
            composerSaveButton,
            LinearLayout.LayoutParams(ctx.dp(HEADER_ACTION_SIZE_DP), ctx.dp(HEADER_ACTION_SIZE_DP)).apply {
                marginStart = ctx.dp(12)
            }
        )
    }

    private lateinit var composerHeaderBar: FrameLayout

    val drawerMask = View(ctx).apply {
        setBackgroundColor(Color.parseColor(DIM_BG))
        visibility = View.GONE
    }

    val drawerSheet = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(DRAWER_BG, 0)
        setPadding(ctx.dp(18), ctx.dp(18), ctx.dp(18), ctx.dp(24))
        visibility = View.GONE
        isClickable = true
        isFocusable = true
    }

    val drawerButtons: Map<FavoritePickerType, TextView> = listOf(
        FavoritePickerType.TEXT to ctx.getString(R.string.favorite_picker_text),
        FavoritePickerType.IMAGE to ctx.getString(R.string.favorite_picker_image),
        FavoritePickerType.FILE to ctx.getString(R.string.favorite_picker_file),
        FavoritePickerType.VIDEO to ctx.getString(R.string.favorite_picker_video),
    ).associate { (type, label) ->
        type to createDrawerButton(label)
    }

    val listBackAction = createBackAction()

    private val panelBackgroundView = ImageView(ctx).apply {
        setImageResource(R.drawable.bg_favorite_panel_rectangle)
        scaleType = ImageView.ScaleType.FIT_XY
    }

    private val listContainer = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            ctx.dp(LIST_PANEL_HORIZONTAL_PADDING_DP),
            ctx.dp(LIST_PANEL_HORIZONTAL_PADDING_DP),
            ctx.dp(LIST_PANEL_HORIZONTAL_PADDING_DP),
            0
        )
        background = topRoundedDrawable("#00FFFFFF", PANEL_CORNER_RADIUS_DP)
        clipToOutline = true
    }

    private lateinit var navigationRowsContainer: LinearLayout
    private lateinit var primaryNavigationRowContainer: HorizontalScrollView
    private lateinit var secondaryNavigationRowContainer: HorizontalScrollView
    private lateinit var primaryNavigationRow: LinearLayout
    private lateinit var secondaryNavigationRow: LinearLayout

    private val composerContainer = FrameLayout(ctx).apply {
        background = topRoundedDrawable("#00FFFFFF", PANEL_CORNER_RADIUS_DP)
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        clipToOutline = true
    }

    val floatingMenuScrim = ImageView(ctx).apply {
        setImageResource(R.drawable.bg_favorite_panel_scrim)
        scaleType = ImageView.ScaleType.FIT_XY
        visibility = View.GONE
        alpha = 0f
        isClickable = true
        isFocusable = false
    }

    private val panelBackgroundTopInsetPx = ctx.dp(PANEL_BACKGROUND_SHADOW_TOP_INSET_DP)
    private val baseComposerTopMarginPx = panelBackgroundTopInsetPx + ctx.dp(COMPOSER_TOP_INSET_DP)
    private var currentPanelMode = PanelMode.LIST
    private val embeddedPreviewTargets = mutableMapOf<String, MutableList<AsyncPreviewTarget>>()
    private val embeddedPreviewLoadingKeys = mutableSetOf<String>()
    private val embeddedPreviewFutures = mutableMapOf<String, Future<*>>()

    val root = FrameLayout(ctx).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = false
        clipToPadding = false
        addView(panelBackgroundView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            calculateBackgroundHeightPx(),
            Gravity.TOP
        ))
        addView(listContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = panelBackgroundTopInsetPx
        })
        addView(composerContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = baseComposerTopMarginPx
        })
        addView(
            listSearchActionHost,
            FrameLayout.LayoutParams(
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = panelBackgroundTopInsetPx +
                    ctx.dp(LIST_PANEL_HORIZONTAL_PADDING_DP + HEADER_TOP_PADDING_DP) -
                    ctx.dp((TOP_CLOSE_BUTTON_HOST_SIZE_DP - TOP_CLOSE_BUTTON_SIZE_DP) / 2)
                marginEnd = ctx.dp(HEADER_END_PADDING_DP + TOP_CLOSE_BUTTON_HOST_SIZE_DP + TOP_HEADER_ACTION_GAP_DP)
            }
        )
        addView(
            listCloseActionHost,
            FrameLayout.LayoutParams(
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                ctx.dp(TOP_CLOSE_BUTTON_HOST_SIZE_DP),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = panelBackgroundTopInsetPx +
                    ctx.dp(LIST_PANEL_HORIZONTAL_PADDING_DP + HEADER_TOP_PADDING_DP) -
                    ctx.dp((TOP_CLOSE_BUTTON_HOST_SIZE_DP - TOP_CLOSE_BUTTON_SIZE_DP) / 2)
                marginEnd = ctx.dp(HEADER_END_PADDING_DP)
            }
        )
        addView(floatingMenuScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            calculateBackgroundHeightPx(),
            Gravity.TOP
        ))
        addView(drawerMask, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = panelBackgroundTopInsetPx + ctx.dp(COMPOSER_HEADER_HEIGHT_DP + COMPOSER_TOP_INSET_DP)
        })
        addView(drawerSheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            topMargin = panelBackgroundTopInsetPx + ctx.dp(COMPOSER_HEADER_HEIGHT_DP + COMPOSER_TOP_INSET_DP)
        })
    }

    private val titleView = TextView(ctx).apply {
        id = View.generateViewId()
        text = ctx.getString(R.string.favorite_panel_title)
        setTextColor(Color.parseColor(TEXT_PRIMARY))
        textSize = LIST_TITLE_TEXT_SIZE_SP
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private var isBrowsingFolder = false
    private var isSearchMode = false
    private var listTitleVisible = true
    private var listSearchVisible = true
    private var listCategoryRowVisible = true

    private val searchIcon = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_favorite_panel_search)
        contentDescription = ctx.getString(R.string.search)
        scaleType = ImageView.ScaleType.FIT_CENTER
        imageTintList = null
    }

    private val floatingAddButtonBackground by lazy {
        ImageView(ctx).apply {
            setImageResource(R.drawable.ic_favorite_floating_add_background)
            scaleType = ImageView.ScaleType.FIT_XY
        }
    }

    private val floatingAddButtonShadow by lazy {
        View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#33000000"),
                    Color.parseColor("#18000000"),
                    Color.parseColor("#00000000"),
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = ctx.dp(FLOATING_MENU_MAIN_SHADOW_SIZE_DP / 2).toFloat()
            }
            alpha = 0.85f
        }
    }

    private val floatingAddButtonIcon by lazy {
        ImageView(ctx).apply {
            setImageResource(R.drawable.ic_favorite_floating_add_plus)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    val floatingAddButton by lazy {
        FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = true
            clipChildren = false
            clipToPadding = false
            visibility = if (enableFloatingAddMenu) View.VISIBLE else View.GONE
            addView(
                floatingAddButtonShadow,
                FrameLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_MAIN_SHADOW_SIZE_DP),
                    ctx.dp(FLOATING_MENU_MAIN_SHADOW_SIZE_DP),
                    Gravity.CENTER,
                )
            )
            addView(
                floatingAddButtonBackground,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
            )
            addView(
                floatingAddButtonIcon,
                FrameLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_MAIN_ICON_SIZE_DP),
                    ctx.dp(FLOATING_MENU_MAIN_ICON_SIZE_DP),
                    Gravity.CENTER,
                )
            )
        }
    }

    val floatingActionItems by lazy {
        linkedMapOf(
            FavoritePickerType.TEXT to createFloatingActionItem(
                label = ctx.getString(R.string.favorite_quick_action_send_text),
                iconRes = R.drawable.ic_favorite_quick_text,
            ),
            FavoritePickerType.IMAGE to createFloatingActionItem(
                label = ctx.getString(R.string.favorite_quick_action_send_image),
                iconRes = R.drawable.ic_favorite_quick_image,
            ),
            FavoritePickerType.FILE to createFloatingActionItem(
                label = ctx.getString(R.string.favorite_quick_action_send_file),
                iconRes = R.drawable.ic_favorite_quick_file,
            ),
            FavoritePickerType.VIDEO to createFloatingActionItem(
                label = ctx.getString(R.string.favorite_quick_action_send_video),
                iconRes = R.drawable.ic_favorite_quick_video,
            ),
        )
    }

    val floatingActionMenu by lazy {
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
            setPadding(
                ctx.dp(FLOATING_MENU_SHADOW_PADDING_DP),
                ctx.dp(FLOATING_MENU_SHADOW_PADDING_DP),
                0,
                ctx.dp(FLOATING_MENU_SHADOW_PADDING_DP),
            )
            visibility = View.GONE
            alpha = 0f
        }
    }

    init {
        searchActionView.addView(searchIcon, FrameLayout.LayoutParams(
            ctx.dp(HEADER_ICON_SIZE_DP),
            ctx.dp(HEADER_ICON_SIZE_DP),
            Gravity.CENTER
        ))
        floatingActionItems.values.forEachIndexed { index, item ->
            floatingActionMenu.addView(
                item,
                LinearLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_WIDTH_DP),
                    ctx.dp(FLOATING_MENU_ITEM_HEIGHT_DP),
                ).apply {
                    if (index > 0) topMargin = ctx.dp(FLOATING_MENU_ITEM_GAP_DP)
                }
            )
        }
        if (enableFloatingAddMenu) {
            root.addView(
                floatingActionMenu,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM,
                ).apply {
                    marginEnd = ctx.dp(FLOATING_MENU_SIDE_MARGIN_DP)
                    bottomMargin = ctx.dp(
                        FLOATING_MENU_BOTTOM_MARGIN_DP +
                            FLOATING_MENU_MAIN_BUTTON_SIZE_DP +
                            FLOATING_MENU_ITEM_GAP_DP
                    )
                }
            )
            root.addView(
                floatingAddButton,
                FrameLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_MAIN_BUTTON_SIZE_DP),
                    ctx.dp(FLOATING_MENU_MAIN_BUTTON_SIZE_DP),
                    Gravity.END or Gravity.BOTTOM,
                ).apply {
                    marginEnd = ctx.dp(FLOATING_MENU_SIDE_MARGIN_DP)
                    bottomMargin = ctx.dp(FLOATING_MENU_BOTTOM_MARGIN_DP)
                }
            )
        }

        listContainer.addView(buildListHeader(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        listContainer.addView(searchInputRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(SEARCH_INPUT_TOP_MARGIN_DP)
        })
        listContainer.addView(buildNavigationRows(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(18)
        })
        listContainer.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            topMargin = ctx.dp(14)
        })
        listContainer.addView(emptyStateView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        composerContentScroll.addView(composerContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        composerContentScroll.isClickable = true
        composerContentScroll.isFocusable = true
        composerContainer.addView(buildComposerLayout(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        buildDrawerContent()
    }

    fun renderPrimaryNavigation(items: List<FavoriteNavigationItem>, selectedId: String?) {
        primaryNavigationItems = items
        primaryNavigationRow.removeAllViews()
        items.forEachIndexed { index, item ->
            primaryNavigationRow.addView(createPrimaryNavigationTab(item, item.id == selectedId), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index < items.lastIndex) {
                    marginEnd = ctx.dp(28)
                }
            })
        }
        updateNavigationVisibility()
    }

    fun renderSecondaryNavigation(items: List<FavoriteNavigationItem>, selectedId: String?) {
        secondaryNavigationItems = items
        secondaryNavigationRow.removeAllViews()
        items.forEachIndexed { index, item ->
            secondaryNavigationRow.addView(createSecondaryNavigationChip(item, item.id == selectedId), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index < items.lastIndex) {
                    marginEnd = ctx.dp(10)
                }
            })
        }
        updateNavigationVisibility()
    }

    fun renderFolderBrowsingState(folder: FavoriteFolderItem?) {
        isBrowsingFolder = folder != null
        listBackAction.visibility = if (isBrowsingFolder || isSearchMode) View.VISIBLE else View.GONE
        updateNavigationVisibility()
        titleView.text = when {
            folder != null -> folder.title
            isSearchMode -> ctx.getString(R.string.favorite_search_title)
            else -> ctx.getString(R.string.favorite_panel_title)
        }
        titleView.textSize = if (isBrowsingFolder) 18f else LIST_TITLE_TEXT_SIZE_SP
        titleView.isClickable = false
        titleView.isFocusable = false
        updateListHeaderTitleLayout(isBrowsingFolder || isSearchMode)
    }

    fun setSearchMode(active: Boolean) {
        isSearchMode = active
        if (currentPanelMode == PanelMode.LIST) {
            searchInputRow.visibility = if (active) View.VISIBLE else View.GONE
            listSearchActionHost.visibility = if (active || !listSearchVisible) View.GONE else View.VISIBLE
        }
        listBackAction.visibility = if (active || isBrowsingFolder) View.VISIBLE else View.GONE
        updateListHeaderTitleLayout(active || isBrowsingFolder)
        if (enableFloatingAddMenu) {
            floatingAddButton.visibility = if (active) View.GONE else View.VISIBLE
        }
        syncSearchClearVisibility()
    }

    fun focusSearchInput() {
        searchInputView.requestFocus()
        searchInputView.setSelection(searchInputView.text?.length ?: 0)
        searchInputView.post {
            searchInputView.requestFocus()
            val inputMethodManager = ctx.getSystemService(InputMethodManager::class.java)
            inputMethodManager?.showSoftInput(searchInputView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun syncSearchClearVisibility() {
        val visible = isSearchMode && !searchInputView.text.isNullOrEmpty()
        searchClearActionHost.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun clearSearchInputText() {
        searchInputView.setText("")
        syncSearchClearVisibility()
    }

    fun setListTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setListTitleVisible(visible: Boolean) {
        listTitleVisible = visible
        if (currentPanelMode == PanelMode.LIST) {
            titleView.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun setListSearchVisible(visible: Boolean) {
        listSearchVisible = visible
        if (currentPanelMode == PanelMode.LIST) {
            listSearchActionHost.visibility = if (visible && !isSearchMode) View.VISIBLE else View.GONE
        }
    }

    fun setListCategoryRowVisible(visible: Boolean) {
        listCategoryRowVisible = visible
        updateNavigationVisibility()
    }

    fun setListManageActionVisible(visible: Boolean) {
        listManageActionView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showListMode(animated: Boolean = false) {
        switchPanelMode(PanelMode.LIST, animated)
        setDrawerVisible(false)
    }

    fun showComposerMode(animated: Boolean = false) {
        switchPanelMode(PanelMode.COMPOSER, animated)
    }

    fun configureComposerHeader(
        title: String?,
        showAddAction: Boolean,
        showSaveAction: Boolean,
        textEditorMode: Boolean = false,
        actionText: String? = null,
        actionTextEnabled: Boolean = false,
        showDownloadAction: Boolean = false,
    ) {
        composerTitleView.text = title.orEmpty()
        composerTitleView.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
        val showTextAction = !actionText.isNullOrBlank()
        composerActionGroup.visibility =
            if (showAddAction || showSaveAction || showTextAction || showDownloadAction) View.VISIBLE else View.INVISIBLE
        downloadActionButton.visibility = if (showDownloadAction) View.VISIBLE else View.GONE
        composerTextActionButton.visibility = if (showTextAction) View.VISIBLE else View.GONE
        composerTextActionButton.text = actionText.orEmpty()
        composerTextActionButton.isEnabled = actionTextEnabled
        composerTextActionButton.alpha = if (actionTextEnabled) 1f else 0.9f
        composerTextActionButton.background = roundedDrawable(
            if (actionTextEnabled) HEADER_ACTION_ENABLED_BG else HEADER_ACTION_DISABLED_BG,
            16
        )
        addActionButton.visibility = if (showAddAction) View.VISIBLE else View.GONE
        composerSaveButton.visibility = if (showSaveAction) View.VISIBLE else View.GONE
        composerCloseButton.setImageResource(R.drawable.ic_favorite_panel_back)
        composerCloseButton.imageTintList = null
        addActionButton.setImageResource(R.drawable.ic_favorite_add_action_24)
        composerSaveButton.setImageResource(R.drawable.ic_favorite_save_action_24)
        addActionButton.scaleX = 1f
        addActionButton.scaleY = 1f
        composerSaveButton.scaleX = 1f
        composerSaveButton.scaleY = 1f
        addActionButton.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(HEADER_ICON_TINT))
        composerSaveButton.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(HEADER_ICON_TINT))
        composerHeaderBar.setPadding(
            ctx.dp(HEADER_START_PADDING_DP),
            ctx.dp(HEADER_TOP_PADDING_DP),
            ctx.dp(HEADER_END_PADDING_DP),
            ctx.dp(HEADER_BOTTOM_PADDING_DP)
        )
    }

    fun updateComposerTextActionState(
        actionText: String,
        enabled: Boolean,
    ) {
        composerTextActionButton.visibility = View.VISIBLE
        composerTextActionButton.text = actionText
        composerTextActionButton.isEnabled = enabled
        composerTextActionButton.alpha = if (enabled) 1f else 0.9f
        composerTextActionButton.background = roundedDrawable(
            if (enabled) HEADER_ACTION_ENABLED_BG else HEADER_ACTION_DISABLED_BG,
            16
        )
    }

    fun configureComposerAddAction(
        iconRes: Int,
        tintColor: Int? = Color.parseColor(HEADER_ICON_TINT),
        iconScale: Float = 1f,
        onClick: () -> Unit,
    ) {
        addActionButton.setImageResource(iconRes)
        addActionButton.imageTintList = tintColor?.let(android.content.res.ColorStateList::valueOf)
        addActionButton.scaleX = iconScale
        addActionButton.scaleY = iconScale
        addActionButton.setOnClickListener { onClick() }
    }

    fun configureComposerSaveAction(
        iconRes: Int,
        tintColor: Int? = Color.parseColor(HEADER_ICON_TINT),
        iconScale: Float = 1f,
        onClick: () -> Unit,
    ) {
        composerSaveButton.setImageResource(iconRes)
        composerSaveButton.imageTintList = tintColor?.let(android.content.res.ColorStateList::valueOf)
        composerSaveButton.scaleX = iconScale
        composerSaveButton.scaleY = iconScale
        composerSaveButton.setOnClickListener { onClick() }
    }

    fun playPreviewHeaderEntranceAnimation() {
        val travel = ctx.dp(44).toFloat()
        val duration = 320L
        val interpolator = OvershootInterpolator(0.95f)

        composerBackAction.animate().cancel()
        composerBackAction.alpha = 0f
        composerBackAction.translationX = -travel
        composerBackAction.post {
            composerBackAction.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
        }

        listOf(addActionButton, composerSaveButton, composerTextActionButton, downloadActionButton)
            .filter { it.visibility == View.VISIBLE }
            .forEachIndexed { index, actionView ->
                actionView.animate().cancel()
                actionView.alpha = 0f
                actionView.translationX = travel
                actionView.post {
                    actionView.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setStartDelay(40L + index * 35L)
                        .setDuration(duration)
                        .setInterpolator(interpolator)
                        .start()
                }
            }
    }

    fun buildComposerEmptyStateView(): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(View(ctx), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ctx.dp(280)
            ))
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.favorite_composer_empty_tip)
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = 18f
                typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    fun buildPickerLoadingView(message: String): View {
        return TextView(ctx).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setPadding(ctx.dp(16), ctx.dp(36), ctx.dp(16), ctx.dp(16))
        }
    }

    fun buildPickerEmptyStateView(message: String): View {
        return TextView(ctx).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setPadding(ctx.dp(16), ctx.dp(40), ctx.dp(16), ctx.dp(16))
        }
    }

    fun buildEmbeddedPickerView(
        type: FavoritePickerType,
        items: List<FavoriteEmbeddedPickerItem>,
        selectedFileCategory: FavoriteFileCategory = FavoriteFileCategory.ALL,
        onSelectFileCategory: (FavoriteFileCategory) -> Unit = {},
        onPick: (FavoriteEmbeddedPickerItem) -> Unit,
        onToggleMediaSelection: (FavoriteEmbeddedPickerItem) -> Unit = {},
        selectedMediaOrder: (FavoriteEmbeddedPickerItem) -> Int = { 0 },
        onPreviewFile: (FavoriteEmbeddedPickerItem) -> Unit = {},
        onToggleFileSelection: (FavoriteEmbeddedPickerItem) -> Unit = {},
        selectedFileOrder: (FavoriteEmbeddedPickerItem) -> Int = { 0 },
    ): View {
        return if (type == FavoritePickerType.FILE) {
            buildEmbeddedFilePickerView(
                items = items,
                selectedCategory = selectedFileCategory,
                onSelectCategory = onSelectFileCategory,
                onPreview = onPreviewFile,
                onToggleSelection = onToggleFileSelection,
                selectedOrder = selectedFileOrder
            )
        } else {
            buildEmbeddedMediaPickerView(
                items = items,
                type = type,
                onToggleSelection = onToggleMediaSelection,
                selectedOrder = selectedMediaOrder
            )
        }
    }

    private fun buildEmbeddedMediaPickerView(
        items: List<FavoriteEmbeddedPickerItem>,
        type: FavoritePickerType,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ): View {
        val gapPx = ctx.dp(MEDIA_PICKER_GAP_DP)
        val contentHorizontalPaddingPx = 0
        val availableWidth = (
            ctx.resources.displayMetrics.widthPixels -
                contentHorizontalPaddingPx -
                gapPx * (MEDIA_PICKER_COLUMN_COUNT - 1)
            ).coerceAtLeast(ctx.dp(200))
        val cellSize = availableWidth / MEDIA_PICKER_COLUMN_COUNT
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            items.chunked(MEDIA_PICKER_COLUMN_COUNT).forEachIndexed { index, rowItems ->
                addView(
                    buildEmbeddedMediaRow(
                        rowItems = rowItems,
                        pickerType = type,
                        cellSize = cellSize,
                        gapPx = gapPx,
                        onToggleSelection = onToggleSelection,
                        selectedOrder = selectedOrder
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = gapPx
                    }
                )
            }
        }
    }

    fun buildEmbeddedMediaPickerRecyclerView(
        items: List<FavoriteEmbeddedPickerItem>,
        type: FavoritePickerType,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
        onLoadMore: () -> Unit,
    ): EmbeddedMediaPickerRecyclerBinding {
        val adapter = EmbeddedMediaPickerAdapter(
            pickerType = type,
            onToggleSelection = onToggleSelection,
            selectedOrder = selectedOrder,
        ).apply {
            submitItems(items)
        }
        val recyclerView = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, MEDIA_PICKER_COLUMN_COUNT)
            this.adapter = adapter
            setHasFixedSize(true)
            setItemViewCacheSize(MEDIA_PICKER_COLUMN_COUNT * 3)
            clipToPadding = false
            val gap = ctx.dp(MEDIA_PICKER_GAP_DP)
            setPadding(0, 0, 0, gap)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy < 0) return
                    val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = manager.findLastVisibleItemPosition()
                    if (lastVisible >= adapter.itemCount - MEDIA_PICKER_COLUMN_COUNT * 2) {
                        onLoadMore()
                    }
                }
            })
        }
        return EmbeddedMediaPickerRecyclerBinding(root = recyclerView, adapter = adapter)
    }

    inner class EmbeddedMediaPickerAdapter(
        private val pickerType: FavoritePickerType,
        private val onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        private val selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ) : RecyclerView.Adapter<EmbeddedMediaPickerAdapter.ViewHolder>() {
        private val entries = mutableListOf<FavoriteEmbeddedPickerItem>()
        private val gapPx = ctx.dp(MEDIA_PICKER_GAP_DP)
        private val cellSize = mediaPickerCellSize(gapPx)

        inner class ViewHolder(val root: FrameLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(cellSize, cellSize).apply {
                    setMargins(gapPx / 2, gapPx / 2, gapPx / 2, gapPx / 2)
                }
            })
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            cancelEmbeddedPreviewRequests(holder.root)
            holder.root.removeAllViews()
            val item = entries[position]
            holder.root.addView(
                buildEmbeddedMediaCard(
                    item = item,
                    pickerType = pickerType,
                    previewSizePx = cellSize,
                    onToggleSelection = onToggleSelection,
                    selectedOrder = selectedOrder,
                ),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        override fun onViewRecycled(holder: ViewHolder) {
            cancelEmbeddedPreviewRequests(holder.root)
            holder.root.removeAllViews()
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = entries.size

        fun submitItems(items: List<FavoriteEmbeddedPickerItem>) {
            val oldEntries = entries.toList()
            val previousSize = entries.size
            entries.clear()
            entries.addAll(items)
            when {
                previousSize == 0 -> notifyItemRangeInserted(0, entries.size)
                entries.size > previousSize && oldEntries == entries.take(previousSize) -> {
                    notifyItemRangeInserted(previousSize, entries.size - previousSize)
                }
                else -> notifyDataSetChanged()
            }
        }

        fun refreshSelection() {
            notifyItemRangeChanged(0, entries.size, Unit)
        }
    }

    private fun mediaPickerCellSize(gapPx: Int): Int {
        val availableWidth = (
            ctx.resources.displayMetrics.widthPixels -
                gapPx * MEDIA_PICKER_COLUMN_COUNT
            ).coerceAtLeast(ctx.dp(200))
        return availableWidth / MEDIA_PICKER_COLUMN_COUNT
    }

    private fun buildEmbeddedMediaRow(
        rowItems: List<FavoriteEmbeddedPickerItem>,
        pickerType: FavoritePickerType,
        cellSize: Int,
        gapPx: Int,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            rowItems.forEachIndexed { index, item ->
                addView(
                    buildEmbeddedMediaCard(
                        item = item,
                        pickerType = pickerType,
                        previewSizePx = cellSize,
                        onToggleSelection = onToggleSelection,
                        selectedOrder = selectedOrder
                    ),
                    LinearLayout.LayoutParams(cellSize, cellSize).apply {
                        if (index > 0) marginStart = gapPx
                    }
                )
            }
        }
    }

    private data class SelectionBadgeBinding(
        val root: FrameLayout,
        val textView: TextView,
    )

    private data class EmbeddedMediaCardBinding(
        val item: FavoriteEmbeddedPickerItem,
        val selectionMask: View,
        val badgeBinding: SelectionBadgeBinding,
    )

    private fun buildEmbeddedMediaCard(
        item: FavoriteEmbeddedPickerItem,
        pickerType: FavoritePickerType,
        previewSizePx: Int,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ): View {
        val order = selectedOrder(item)
        val preview = buildEmbeddedMediaGridPreviewView(item, previewSizePx)
        val selectionMask = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
            visibility = if (order > 0) View.VISIBLE else View.GONE
        }
        val badgeBinding = buildMediaSelectionBadgeBinding(order)
        return FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onToggleSelection(item) }
            addView(preview, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(selectionMask, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            if (pickerType == FavoritePickerType.VIDEO) {
                addView(buildPickerVideoIndicator(), FrameLayout.LayoutParams(
                    ctx.dp(24),
                    ctx.dp(24),
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    leftMargin = ctx.dp(4)
                    bottomMargin = ctx.dp(4)
                })
            }
            addView(
                badgeBinding.root,
                FrameLayout.LayoutParams(
                    ctx.dp(MEDIA_PICKER_BADGE_SIZE_DP),
                    ctx.dp(MEDIA_PICKER_BADGE_SIZE_DP),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = ctx.dp(MEDIA_PICKER_BADGE_MARGIN_DP)
                    rightMargin = ctx.dp(MEDIA_PICKER_BADGE_MARGIN_DP)
                }
            )
            tag = EmbeddedMediaCardBinding(
                item = item,
                selectionMask = selectionMask,
                badgeBinding = badgeBinding,
            )
        }
    }

    private fun buildEmbeddedMediaGridPreviewView(
        item: FavoriteEmbeddedPickerItem,
        previewSizePx: Int,
    ): View {
        val placeholderView = buildEmbeddedMediaSkeletonView()
        val imageView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        bindEmbeddedContentPreviewAsync(
            imageView = imageView,
            placeholderView = placeholderView,
            item = item,
            reqWidthPx = previewSizePx,
            reqHeightPx = previewSizePx,
        )
        return FrameLayout(ctx).apply {
            addView(
                placeholderView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                imageView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun buildEmbeddedMediaSkeletonView(): View {
        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor(MEDIA_SKELETON_BG))
            addView(
                View(ctx).apply {
                    setBackgroundColor(Color.parseColor(MEDIA_SKELETON_ACCENT))
                    alpha = 0.8f
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(18),
                    Gravity.BOTTOM
                )
            )
            addView(
                View(ctx).apply {
                    background = roundedDrawable(Color.parseColor("#F6F6F6"), 3)
                    alpha = 0.85f
                },
                FrameLayout.LayoutParams(
                    ctx.dp(36),
                    ctx.dp(6),
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    leftMargin = ctx.dp(8)
                    bottomMargin = ctx.dp(6)
                }
            )
        }
    }

    private fun bindEmbeddedContentPreviewAsync(
        imageView: ImageView,
        placeholderView: View,
        item: FavoriteEmbeddedPickerItem,
        reqWidthPx: Int,
        reqHeightPx: Int,
    ) {
        val previewKey = embeddedPreviewCacheKey(item, reqWidthPx, reqHeightPx)
        val requestToken = "$previewKey#${System.identityHashCode(imageView)}"
        imageView.tag = requestToken
        previewBitmapFromCache(previewKey)?.let { cached ->
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            placeholderView.visibility = View.GONE
            return
        }
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        placeholderView.visibility = View.VISIBLE
        val shouldStartDecode = synchronized(embeddedPreviewTargets) {
            embeddedPreviewTargets
                .getOrPut(previewKey) { mutableListOf() }
                .add(
                    AsyncPreviewTarget(
                        imageView = WeakReference(imageView),
                        placeholderView = WeakReference(placeholderView),
                        requestToken = requestToken
                    )
                )
            embeddedPreviewLoadingKeys.add(previewKey)
        }
        if (!shouldStartDecode) return
        val future = previewExecutor.submit {
            val bitmap = decodeContentPreview(item, reqWidthPx, reqHeightPx)
            if (bitmap != null) {
                previewBitmapCache.put(previewKey, bitmap)
            }
            previewMainHandler.post {
                val targets = synchronized(embeddedPreviewTargets) {
                    embeddedPreviewLoadingKeys.remove(previewKey)
                    embeddedPreviewFutures.remove(previewKey)
                    embeddedPreviewTargets.remove(previewKey).orEmpty()
                }
                targets.forEach { target ->
                    val targetImageView = target.imageView.get() ?: return@forEach
                    val targetPlaceholderView = target.placeholderView.get() ?: return@forEach
                    if (targetImageView.tag != target.requestToken) return@forEach
                    if (bitmap != null) {
                        targetImageView.setImageBitmap(bitmap)
                        targetImageView.visibility = View.VISIBLE
                        targetPlaceholderView.visibility = View.GONE
                    } else {
                        targetImageView.visibility = View.GONE
                        targetPlaceholderView.visibility = View.VISIBLE
                    }
                }
            }
        }
        synchronized(embeddedPreviewTargets) {
            embeddedPreviewFutures[previewKey] = future
        }
    }

    private fun cancelEmbeddedPreviewRequests(view: View) {
        if (view is ImageView) {
            (view.tag as? String)?.let(::cancelEmbeddedPreviewRequest)
            view.tag = null
            view.setImageDrawable(null)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                cancelEmbeddedPreviewRequests(view.getChildAt(index))
            }
        }
    }

    private fun cancelEmbeddedPreviewRequest(requestToken: String) {
        val previewKey = requestToken.substringBefore('#')
        synchronized(embeddedPreviewTargets) {
            val targets = embeddedPreviewTargets[previewKey] ?: return@synchronized
            targets.removeAll { it.requestToken == requestToken }
            if (targets.isEmpty()) {
                embeddedPreviewTargets.remove(previewKey)
                embeddedPreviewLoadingKeys.remove(previewKey)
                embeddedPreviewFutures.remove(previewKey)?.cancel(true)
            }
        }
    }

    fun updateEmbeddedMediaPickerSelection(
        contentView: View,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ) {
        fun traverse(view: View) {
            (view.tag as? EmbeddedMediaCardBinding)?.let { binding ->
                applyEmbeddedMediaCardSelection(binding, selectedOrder(binding.item))
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    traverse(view.getChildAt(index))
                }
            }
        }
        traverse(contentView)
    }

    private fun applyEmbeddedMediaCardSelection(
        binding: EmbeddedMediaCardBinding,
        order: Int,
    ) {
        binding.selectionMask.visibility = if (order > 0) View.VISIBLE else View.GONE
        applySelectionBadgeState(
            binding = binding.badgeBinding,
            order = order,
            unselectedFillColor = Color.TRANSPARENT,
            unselectedStrokeColor = Color.WHITE,
            unselectedStrokeWidthDp = 2,
        )
    }

    private fun buildMediaSelectionBadgeBinding(order: Int): SelectionBadgeBinding {
        return buildSelectionBadgeBinding(
            order = order,
            unselectedFillColor = Color.TRANSPARENT,
            unselectedStrokeColor = Color.WHITE,
            unselectedStrokeWidthDp = 2
        )
    }

    private fun buildFileSelectionBadge(order: Int): View {
        return buildSelectionBadgeBinding(
            order = order,
            unselectedFillColor = Color.parseColor(FILE_PICKER_UNSELECTED_BADGE_FILL),
            unselectedStrokeColor = Color.parseColor(FILE_PICKER_UNSELECTED_BADGE_STROKE),
            unselectedStrokeWidthDp = 1
        ).root
    }

    private fun buildSelectionBadgeBinding(
        order: Int,
        unselectedFillColor: Int,
        unselectedStrokeColor: Int,
        unselectedStrokeWidthDp: Int,
    ): SelectionBadgeBinding {
        val textView = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = MEDIA_PICKER_BADGE_TEXT_SIZE_SP
            typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        }
        val root = FrameLayout(ctx).apply {
            addView(
                textView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        return SelectionBadgeBinding(root = root, textView = textView).also { binding ->
            applySelectionBadgeState(
                binding = binding,
                order = order,
                unselectedFillColor = unselectedFillColor,
                unselectedStrokeColor = unselectedStrokeColor,
                unselectedStrokeWidthDp = unselectedStrokeWidthDp,
            )
        }
    }

    private fun applySelectionBadgeState(
        binding: SelectionBadgeBinding,
        order: Int,
        unselectedFillColor: Int,
        unselectedStrokeColor: Int,
        unselectedStrokeWidthDp: Int,
    ) {
        binding.root.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (order > 0) {
                setColor(Color.parseColor("#07C160"))
            } else {
                setColor(unselectedFillColor)
                setStroke(ctx.dp(unselectedStrokeWidthDp), unselectedStrokeColor)
            }
        }
        binding.textView.text = if (order > 0) order.toString() else ""
        binding.textView.visibility = if (order > 0) View.VISIBLE else View.GONE
    }

    private fun buildPickerVideoIndicator(): View {
        return FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#66000000"))
            }
            addView(
                ImageView(ctx).apply {
                    setImageResource(android.R.drawable.ic_media_play)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(
                    ctx.dp(14),
                    ctx.dp(14),
                    Gravity.CENTER
                )
            )
        }
    }

    private fun buildEmbeddedFilePickerView(
        items: List<FavoriteEmbeddedPickerItem>,
        selectedCategory: FavoriteFileCategory,
        onSelectCategory: (FavoriteFileCategory) -> Unit,
        onPreview: (FavoriteEmbeddedPickerItem) -> Unit,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ): View {
        val visibleItems = items.filter { item ->
            selectedCategory == FavoriteFileCategory.ALL || item.fileCategory == selectedCategory
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                buildEmbeddedFileCategoryBar(selectedCategory, onSelectCategory),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ctx.dp(FILE_PICKER_CATEGORY_TOP_MARGIN_DP)
                    bottomMargin = ctx.dp(FILE_PICKER_CATEGORY_BOTTOM_MARGIN_DP)
                }
            )
            if (visibleItems.isEmpty()) {
                addView(
                    buildPickerEmptyStateView("当前没有可用的${selectedCategory.label}文档"),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = ctx.dp(18)
                    }
                )
                return@apply
            }
            visibleItems.forEachIndexed { index, item ->
                addView(
                    buildEmbeddedFileCard(
                        item = item,
                        onPreview = onPreview,
                        onToggleSelection = onToggleSelection,
                        selectedOrder = selectedOrder
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index == 0) topMargin = ctx.dp(16)
                        if (index > 0) topMargin = ctx.dp(4)
                    }
                )
            }
        }
    }

    private fun buildEmbeddedFileCategoryBar(
        selectedCategory: FavoriteFileCategory,
        onSelectCategory: (FavoriteFileCategory) -> Unit,
    ): View {
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(ctx.dp(FILE_PICKER_HORIZONTAL_PADDING_DP), 0, ctx.dp(FILE_PICKER_HORIZONTAL_PADDING_DP), 0)
                    FavoriteFileCategory.entries.forEachIndexed { index, category ->
                        addView(TextView(ctx).apply {
                            text = category.label
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(ctx.dp(14), ctx.dp(8), ctx.dp(14), ctx.dp(8))
                            val selected = category == selectedCategory
                            setTextColor(Color.parseColor(if (selected) TAB_SELECTED_TEXT else TAB_IDLE_TEXT))
                            typeface = Typeface.defaultFromStyle(if (selected) Typeface.BOLD else Typeface.NORMAL)
                            background = if (selected) {
                                roundedDrawable(TAB_SELECTED_BG, 14)
                            } else {
                                roundedDrawable(Color.TRANSPARENT, 14)
                            }
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { onSelectCategory(category) }
                        }, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index > 0) marginStart = ctx.dp(6)
                        })
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun buildEmbeddedFileCard(
        item: FavoriteEmbeddedPickerItem,
        onPreview: (FavoriteEmbeddedPickerItem) -> Unit,
        onToggleSelection: (FavoriteEmbeddedPickerItem) -> Unit,
        selectedOrder: (FavoriteEmbeddedPickerItem) -> Int,
    ): View {
        val order = selectedOrder(item)
        val iconResId = resolveEmbeddedFileIconRes(item)
        val hotZoneWidth = (
            (ctx.resources.displayMetrics.widthPixels * 0.2f).toInt()
            ).coerceAtLeast(ctx.dp(FILE_PICKER_TOGGLE_HOT_ZONE_MIN_WIDTH_DP))
        return FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onPreview(item) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    ctx.dp(FILE_PICKER_HORIZONTAL_PADDING_DP),
                    ctx.dp(10),
                    ctx.dp(FILE_PICKER_HORIZONTAL_PADDING_DP),
                    ctx.dp(10)
                )
                addView(
                    if (iconResId != null) {
                        ImageView(ctx).apply {
                            setImageResource(iconResId)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    } else {
                        TextView(ctx).apply {
                            text = resolveEmbeddedFileBadgeLabel(item)
                            gravity = Gravity.CENTER
                            textSize = 12f
                            typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                            setTextColor(Color.parseColor("#2B5FD9"))
                        }
                    },
                    LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42))
                )
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(ctx).apply {
                        text = displayFileNameWithoutExtension(item.displayName)
                        setTextColor(Color.parseColor(TEXT_PRIMARY))
                        textSize = 16f
                        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    if (item.subtitle.isNotBlank()) {
                        addView(TextView(ctx).apply {
                            text = item.subtitle
                            setTextColor(Color.parseColor(TEXT_SECONDARY))
                            textSize = 12f
                        }, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = ctx.dp(4)
                        })
                    }
                }, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = ctx.dp(12)
                    marginEnd = ctx.dp(12)
                })
                addView(
                    View(ctx),
                    LinearLayout.LayoutParams(hotZoneWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(FrameLayout(ctx).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onToggleSelection(item)
                }
                addView(
                    buildFileSelectionBadge(order),
                    FrameLayout.LayoutParams(
                        ctx.dp(MEDIA_PICKER_BADGE_SIZE_DP),
                        ctx.dp(MEDIA_PICKER_BADGE_SIZE_DP),
                        Gravity.CENTER_VERTICAL or Gravity.END
                    ).apply {
                        rightMargin = ctx.dp(FILE_PICKER_HORIZONTAL_PADDING_DP)
                    }
                )
            }, FrameLayout.LayoutParams(
                hotZoneWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            ))
        }
    }

    private fun displayFileNameWithoutExtension(displayName: String): String {
        val dotIndex = displayName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == displayName.lastIndex) return displayName
        return displayName.substring(0, dotIndex)
    }

    private fun resolveEmbeddedFileExtension(item: FavoriteEmbeddedPickerItem): String {
        return item.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun resolveEmbeddedFileBadgeLabel(item: FavoriteEmbeddedPickerItem): String {
        return when (item.fileCategory) {
            FavoriteFileCategory.WORD -> "DOC"
            FavoriteFileCategory.PRESENTATION -> "PPT"
            FavoriteFileCategory.SHEET -> "XLS"
            FavoriteFileCategory.PDF -> "PDF"
            else -> "FILE"
        }
    }

    private fun resolveEmbeddedFileIconRes(item: FavoriteEmbeddedPickerItem): Int? {
        return when (resolveEmbeddedFileExtension(item)) {
            "doc" -> R.drawable.ic_file_doc
            "docx" -> R.drawable.ic_file_docx
            "ppt" -> R.drawable.ic_file_ppt
            "pptx" -> R.drawable.ic_file_pptx
            "xls" -> R.drawable.ic_file_xls
            "xlsx" -> R.drawable.ic_file_xlsx
            "pdf" -> R.drawable.ic_file_pdf
            else -> null
        }
    }

    fun setComposerKeyboardHeight(heightPx: Int) {
        composerKeyboardContainer.layoutParams = (composerKeyboardContainer.layoutParams as? LinearLayout.LayoutParams)
            ?.apply { height = heightPx }
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
    }

    fun setComposerKeyboardVisible(visible: Boolean) {
        composerKeyboardContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showEmptyState(show: Boolean) {
        emptyStateView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    fun setEmptyStateContent(contentView: View?) {
        emptyStateView.removeAllViews()
        val target = contentView ?: defaultEmptyStateView
        (target.parent as? ViewGroup)?.removeView(target)
        emptyStateView.addView(
            target,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    fun setDrawerVisible(visible: Boolean) {
        if (visible) {
            root.bringChildToFront(drawerMask)
            root.bringChildToFront(drawerSheet)
        }
        drawerMask.visibility = if (visible) View.VISIBLE else View.GONE
        drawerSheet.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setFloatingAddButtonExpanded(expanded: Boolean, animate: Boolean = false) {
        val targetRotation = if (expanded) -45f else 0f
        floatingAddButtonIcon.animate().cancel()
        if (animate) {
            floatingAddButtonIcon.animate()
                .rotation(targetRotation)
                .setDuration(180L)
                .start()
        } else {
            floatingAddButtonIcon.rotation = targetRotation
        }
    }

    fun collapseFloatingMenuImmediate() {
        setFloatingAddButtonExpanded(false)
        floatingAddButton.scaleX = 1f
        floatingAddButton.scaleY = 1f
        floatingMenuScrim.visibility = View.GONE
        floatingMenuScrim.alpha = 0f
        floatingActionMenu.visibility = View.GONE
        floatingActionMenu.alpha = 0f
        floatingActionItems.values.forEach { item ->
            item.alpha = 0f
            item.translationX = ctx.dp(40).toFloat()
            item.scaleX = 0.92f
            item.scaleY = 0.92f
        }
    }

    private fun switchPanelMode(targetMode: PanelMode, animated: Boolean) {
        val incoming = if (targetMode == PanelMode.LIST) listContainer else composerContainer
        val outgoing = if (targetMode == PanelMode.LIST) composerContainer else listContainer
        listCloseActionHost.visibility = if (targetMode == PanelMode.LIST) View.VISIBLE else View.GONE
        listSearchActionHost.visibility = if (targetMode == PanelMode.LIST && listSearchVisible && !isSearchMode) View.VISIBLE else View.GONE
        titleView.visibility = if (targetMode == PanelMode.LIST && listTitleVisible) View.VISIBLE else View.GONE
        if (!animated || root.height == 0 || currentPanelMode == targetMode) {
            currentPanelMode = targetMode
            updateNavigationVisibility()
            incoming.visibility = View.VISIBLE
            incoming.translationY = 0f
            incoming.alpha = 1f
            outgoing.visibility = View.GONE
            outgoing.translationY = 0f
            outgoing.alpha = 1f
            return
        }
        currentPanelMode = targetMode
        updateNavigationVisibility()
        root.bringChildToFront(panelBackgroundView)
        root.bringChildToFront(incoming)
        if (targetMode == PanelMode.LIST) {
            root.bringChildToFront(listSearchActionHost)
            root.bringChildToFront(listCloseActionHost)
        }
        root.bringChildToFront(floatingMenuScrim)
        root.bringChildToFront(drawerMask)
        root.bringChildToFront(drawerSheet)
        if (enableFloatingAddMenu) {
            root.bringChildToFront(floatingActionMenu)
            root.bringChildToFront(floatingAddButton)
        }
        val slideDistance = (incoming.height.takeIf { it > 0 } ?: calculateBackgroundHeightPx()).toFloat()
        incoming.animate().cancel()
        outgoing.animate().cancel()
        incoming.visibility = View.VISIBLE
        incoming.alpha = 1f
        incoming.translationY = slideDistance
        outgoing.visibility = View.VISIBLE
        outgoing.alpha = 1f
        outgoing.translationY = 0f
        incoming.animate()
            .translationY(0f)
            .setDuration(CONTENT_SWITCH_DURATION_MS)
            .start()
        outgoing.animate()
            .translationY(slideDistance)
            .setDuration(CONTENT_SWITCH_DURATION_MS)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.translationY = 0f
            }
            .start()
    }

    fun scrollComposerPartNearTop(target: View) {
        fun alignToTop() {
            if (target.parent !== composerContent) return
            val desiredScrollY = (target.top - composerContent.paddingTop).coerceAtLeast(0)
            composerContentScroll.scrollTo(0, desiredScrollY)
        }
        composerContentScroll.post { alignToTop() }
        composerContentScroll.postDelayed({ alignToTop() }, 80L)
        composerContentScroll.postDelayed({ alignToTop() }, 180L)
        composerContentScroll.postDelayed({ alignToTop() }, 320L)
    }

    fun scrollComposerToBottom() {
        fun alignToBottom() {
            val targetY = (composerContent.height + composerContent.paddingBottom).coerceAtLeast(0)
            composerContentScroll.scrollTo(0, targetY)
        }
        composerContentScroll.post { alignToBottom() }
        composerContentScroll.postDelayed({ alignToBottom() }, 80L)
        composerContentScroll.postDelayed({ alignToBottom() }, 180L)
        composerContentScroll.postDelayed({ alignToBottom() }, 320L)
    }

    fun captureComposerScrollY(): Int = composerContentScroll.scrollY

    fun restoreComposerScrollY(scrollY: Int) {
        fun alignToSavedPosition() {
            composerContentScroll.scrollTo(0, scrollY.coerceAtLeast(0))
        }
        composerContentScroll.post { alignToSavedPosition() }
        composerContentScroll.postDelayed({ alignToSavedPosition() }, 80L)
        composerContentScroll.postDelayed({ alignToSavedPosition() }, 180L)
        composerContentScroll.postDelayed({ alignToSavedPosition() }, 320L)
    }

    fun showComposerTransientContent(contentView: View) {
        composerTransientContentContainer.removeAllViews()
        (contentView.parent as? ViewGroup)?.removeView(contentView)
        composerTransientContentContainer.addView(
            contentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        composerTransientContentContainer.visibility = View.VISIBLE
        (composerTransientContentContainer.parent as? ViewGroup)?.bringChildToFront(composerTransientContentContainer)
        composerTransientContentContainer.bringToFront()
    }

    fun hideComposerTransientContent() {
        composerTransientContentContainer.removeAllViews()
        composerTransientContentContainer.visibility = View.GONE
    }

    fun buildTextEditorView(
        text: String,
        composingText: String,
        cursorVisible: Boolean,
        onRequestKeyboard: () -> Unit,
        onTextChanged: (String) -> Unit,
    ): TextEditorViewHolder {
        var suppressTextCallback = false
        val contentView = AppCompatEditText(ctx).apply {
            textSize = 18f
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setLineSpacing(ctx.dp(6).toFloat(), 1f)
            background = null
            setPadding(0, 0, 0, 0)
            hint = ctx.getString(R.string.favorite_text_editor_placeholder)
            setHintTextColor(Color.parseColor(TEXT_TERTIARY))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 1
            maxLines = TEXT_EDITOR_MAX_VISIBLE_LINES
            isSingleLine = false
            isVerticalScrollBarEnabled = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            setText(text)
            setSelection(text.length)
            addTextChangedListener { editable ->
                if (suppressTextCallback) return@addTextChangedListener
                onTextChanged(editable?.toString().orEmpty())
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onRequestKeyboard()
                }
            }
            setOnClickListener { onRequestKeyboard() }
        }
        val bottomSafeSpacer = View(ctx)
        val contentContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                bottomSafeSpacer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(TEXT_EDITOR_BOTTOM_SAFE_GAP_DP)
                )
            )
        }
        val editorScrollView = ScrollView(ctx).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            addView(
                contentContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val root = FrameLayout(ctx).apply {
            background = null
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onRequestKeyboard() }
            addView(
                editorScrollView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        contentView.tag = { updating: Boolean -> suppressTextCallback = updating }
        return TextEditorViewHolder(
            root = root,
            scrollView = editorScrollView,
            contentContainer = contentContainer,
            contentView = contentView,
            bottomSafeSpacer = bottomSafeSpacer,
        )
    }

    fun bindTextEditorView(
        holder: TextEditorViewHolder,
        text: String,
        composingText: String,
        cursorVisible: Boolean,
    ) {
        bindTextEditorView(holder.contentView, text, composingText, cursorVisible)
    }

    fun scrollTextEditorCursorIntoView(holder: TextEditorViewHolder) {
        fun alignToCursor() {
            if (holder.root.parent !== composerContent) return
            val safeGap = holder.bottomSafeSpacer.layoutParams.height.takeIf { it > 0 }
                ?: ctx.dp(TEXT_EDITOR_BOTTOM_SAFE_GAP_DP)
            val lastLineExtraGap = ctx.dp(TEXT_EDITOR_LAST_LINE_EXTRA_GAP_DP)
            val currentLineBottom = holder.contentView.layout?.let { layout ->
                holder.contentView.top +
                    holder.contentView.paddingTop +
                    layout.getLineBottom((layout.lineCount - 1).coerceAtLeast(0))
            } ?: holder.contentView.bottom
            val targetY = (
                currentLineBottom + lastLineExtraGap - (holder.scrollView.height - safeGap)
            ).coerceAtLeast(0)
            holder.scrollView.scrollTo(0, targetY)
        }
        holder.scrollView.post { alignToCursor() }
        holder.scrollView.postDelayed({ alignToCursor() }, 80L)
        holder.scrollView.postDelayed({ alignToCursor() }, 180L)
        holder.scrollView.postDelayed({ alignToCursor() }, 320L)
    }

    fun layoutTextEditorViewport(holder: TextEditorViewHolder) {
        fun applyHeight() {
            val available = (
                composerContentScroll.height -
                    composerContent.paddingTop -
                    composerContent.paddingBottom -
                    ctx.dp(TEXT_EDITOR_OUTER_BOTTOM_GAP_DP)
                ).coerceAtLeast(ctx.dp(TEXT_EDITOR_MIN_VIEWPORT_HEIGHT_DP))
            val desired = (
                holder.contentView.lineHeight * TEXT_EDITOR_MAX_VISIBLE_LINES +
                    ctx.dp(TEXT_EDITOR_BOTTOM_SAFE_GAP_DP) +
                    ctx.dp(TEXT_EDITOR_LAST_LINE_EXTRA_GAP_DP) +
                    ctx.dp(TEXT_EDITOR_CARD_VERTICAL_PADDING_DP)
                ).coerceAtLeast(ctx.dp(TEXT_EDITOR_MIN_VIEWPORT_HEIGHT_DP))
            val params = (holder.root.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    desired.coerceAtMost(available)
                )
            val targetHeight = desired.coerceAtMost(available)
            if (params.height != targetHeight) {
                params.height = targetHeight
                params.gravity = Gravity.TOP
                holder.root.layoutParams = params
            }
        }
        composerContentScroll.post { applyHeight() }
        composerContentScroll.postDelayed({ applyHeight() }, 80L)
    }

    private fun bindTextEditorView(
        contentView: AppCompatEditText,
        text: String,
        composingText: String,
        cursorVisible: Boolean,
    ) {
        @Suppress("UNCHECKED_CAST")
        val suppressCallback = contentView.tag as? ((Boolean) -> Unit)
        val targetText = text
        if (contentView.text?.toString() != targetText) {
            suppressCallback?.invoke(true)
            contentView.setText(targetText)
            contentView.setSelection(targetText.length)
            suppressCallback?.invoke(false)
        } else if (contentView.selectionStart != targetText.length || contentView.selectionEnd != targetText.length) {
            contentView.setSelection(targetText.length)
        }
        contentView.isCursorVisible = cursorVisible
    }

    fun buildTextPartView(
        part: FavoritePart.Text,
        onSelect: () -> Unit,
    ): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(CARD_BACKGROUND, 12)
            setPadding(ctx.dp(16))
            setOnClickListener { onSelect() }
        }
        val content = TextView(ctx).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = 16f
            minHeight = ctx.dp(92)
            gravity = Gravity.TOP or Gravity.START
            val rawText = if (part.text.isBlank()) {
                ctx.getString(R.string.favorite_composer_hint)
            } else {
                part.text
            }
            text = rawText
            if (part.text.isBlank()) {
                setTextColor(Color.parseColor(TEXT_TERTIARY))
            }
        }
        card.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return card
    }

    fun buildAttachmentPartView(
        part: FavoritePart.Attachment,
        onPreview: () -> Unit,
    ): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(CARD_BACKGROUND, 12)
            setPadding(ctx.dp(16))
            setOnClickListener { onPreview() }
        }
        val preview = buildAttachmentPreviewView(part)
        card.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(140)
        ))
        if (part.pickerType == FavoritePickerType.FILE) {
            card.addView(TextView(ctx).apply {
                text = part.displayName
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = 15f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(12)
            })
        }
        if (part.pickerType == FavoritePickerType.FILE) {
            card.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.favorite_picker_file)
                setTextColor(Color.parseColor(TEXT_SECONDARY))
                textSize = 13f
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(6)
            })
        }
        return card
    }

    private fun buildListHeader(): View {
        return RelativeLayout(ctx).apply {
            minimumHeight = ctx.dp(TOP_CLOSE_BUTTON_SIZE_DP)
            setPadding(
                ctx.dp(LIST_HEADER_START_PADDING_DP),
                ctx.dp(HEADER_TOP_PADDING_DP),
                ctx.dp(LIST_HEADER_END_PADDING_DP),
                ctx.dp(HEADER_BOTTOM_PADDING_DP)
            )
            addView(listBackAction, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ctx.dp(HEADER_ACTION_SIZE_DP)
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            })
            addView(titleView, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            })
            addView(listManageActionView, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            })
        }
    }

    private fun buildNavigationRows(): View {
        return LinearLayout(ctx).apply {
            navigationRowsContainer = this
            orientation = LinearLayout.VERTICAL
            addView(HorizontalScrollView(ctx).apply {
                primaryNavigationRowContainer = this
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(LinearLayout(ctx).apply {
                    primaryNavigationRow = this
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(HorizontalScrollView(ctx).apply {
                secondaryNavigationRowContainer = this
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(LinearLayout(ctx).apply {
                    secondaryNavigationRow = this
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(12)
            })
            updateNavigationVisibility()
        }
    }

    private fun createPrimaryNavigationTab(item: FavoriteNavigationItem, selected: Boolean): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = ctx.dp(34)
            isClickable = true
            isFocusable = true
            addView(
                TextView(ctx).apply {
                    text = item.label
                    gravity = Gravity.CENTER
                    textSize = 16f
                    typeface = Typeface.defaultFromStyle(if (selected) Typeface.BOLD else Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    background = null
                    setTextColor(Color.parseColor(if (selected) PRIMARY_NAV_SELECTED_TEXT else PRIMARY_NAV_IDLE_TEXT))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                View(ctx).apply {
                    background = if (selected) {
                        roundedDrawable(PRIMARY_NAV_SELECTED_INDICATOR, 999)
                    } else {
                        null
                    }
                    visibility = if (selected) View.VISIBLE else View.INVISIBLE
                },
                LinearLayout.LayoutParams(
                    ctx.dp(36),
                    ctx.dp(3),
                ).apply {
                    topMargin = ctx.dp(2)
                },
            )
            setOnClickListener { onPrimaryNavigationSelected?.invoke(item.id) }
        }
    }

    private fun createSecondaryNavigationChip(item: FavoriteNavigationItem, selected: Boolean): TextView {
        return TextView(ctx).apply {
            text = item.label
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.defaultFromStyle(Typeface.BOLD)
            minHeight = ctx.dp(28)
            minimumHeight = ctx.dp(28)
            setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = roundedDrawable(if (selected) SECONDARY_NAV_SELECTED_BG else SECONDARY_NAV_IDLE_BG, 14)
            setTextColor(Color.parseColor(if (selected) SECONDARY_NAV_SELECTED_TEXT else SECONDARY_NAV_IDLE_TEXT))
            setOnClickListener { onSecondaryNavigationSelected?.invoke(item.id) }
        }
    }

    private fun updateNavigationVisibility() {
        if (!::navigationRowsContainer.isInitialized) return
        val containerVisible = currentPanelMode == PanelMode.LIST && !isBrowsingFolder && listCategoryRowVisible
        navigationRowsContainer.visibility = if (containerVisible) View.VISIBLE else View.GONE
        if (!containerVisible) return
        primaryNavigationRowContainer.visibility = if (primaryNavigationItems.isNotEmpty()) View.VISIBLE else View.GONE
        secondaryNavigationRowContainer.visibility = if (secondaryNavigationItems.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun defaultTypeNavigationItems(): List<FavoriteNavigationItem> {
        return typeTabOrder.map { category ->
            FavoriteNavigationItem(
                id = category.name,
                label = if (category == FavoriteCategory.NOTES) {
                    ctx.getString(R.string.favorite_category_text)
                } else {
                    ctx.getString(category.labelRes)
                },
            )
        }
    }

    private fun createBackAction(): LinearLayout {
        val icon = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_favorite_panel_back)
            imageTintList = null
            contentDescription = ctx.getString(R.string.favorite_back)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            minimumWidth = ctx.dp(HEADER_ACTION_SIZE_DP)
            minimumHeight = ctx.dp(HEADER_ACTION_SIZE_DP)
            addView(icon, LinearLayout.LayoutParams(ctx.dp(HEADER_ICON_SIZE_DP), ctx.dp(HEADER_ICON_SIZE_DP)))
        }
    }

    private fun buildComposerLayout(): View {
        val headerBar = FrameLayout(ctx).apply {
            setPadding(
                ctx.dp(HEADER_START_PADDING_DP),
                ctx.dp(HEADER_TOP_PADDING_DP),
                ctx.dp(HEADER_END_PADDING_DP),
                ctx.dp(HEADER_BOTTOM_PADDING_DP)
            )
            addView(
                composerBackAction,
                FrameLayout.LayoutParams(
                    ctx.dp(HEADER_ACTION_SIZE_DP),
                    ctx.dp(HEADER_ACTION_SIZE_DP),
                    Gravity.START or Gravity.CENTER_VERTICAL
                )
            )
            addView(
                composerTitleView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            addView(
                composerActionGroup,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
            )
        }
        composerHeaderBar = headerBar
        val headerDivider = View(ctx).apply {
            setBackgroundColor(Color.parseColor(DIVIDER))
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                headerBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(COMPOSER_HEADER_BAR_HEIGHT_DP)
                )
            )
            addView(
                headerDivider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
            )
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                header,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                FrameLayout(ctx).apply {
                    addView(
                        composerContentScroll,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    addView(
                        composerTransientContentContainer,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                composerKeyboardContainer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                )
            )
        }
    }

    private fun updateListHeaderTitleLayout(browsingFolder: Boolean) {
        (titleView.layoutParams as? RelativeLayout.LayoutParams)?.let { params ->
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
            params.removeRule(RelativeLayout.END_OF)
            params.removeRule(RelativeLayout.CENTER_IN_PARENT)
            params.removeRule(RelativeLayout.CENTER_VERTICAL)
            if (browsingFolder) {
                params.addRule(RelativeLayout.CENTER_IN_PARENT)
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_START)
                params.addRule(RelativeLayout.CENTER_VERTICAL)
            }
            titleView.layoutParams = params
        }
    }

    private fun buildDrawerContent() {
        drawerSheet.removeAllViews()
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(drawerButtons.getValue(FavoritePickerType.TEXT), LinearLayout.LayoutParams(0, ctx.dp(80), 1f))
            addView(drawerButtons.getValue(FavoritePickerType.IMAGE), LinearLayout.LayoutParams(0, ctx.dp(80), 1f).apply {
                marginStart = ctx.dp(18)
            })
        }
        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(drawerButtons.getValue(FavoritePickerType.FILE), LinearLayout.LayoutParams(0, ctx.dp(80), 1f))
            addView(drawerButtons.getValue(FavoritePickerType.VIDEO), LinearLayout.LayoutParams(0, ctx.dp(80), 1f).apply {
                marginStart = ctx.dp(18)
            })
        }
        drawerSheet.addView(row1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        drawerSheet.addView(row2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = ctx.dp(18)
        })
    }

    private fun createDrawerButton(label: String): TextView {
        return TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            background = roundedDrawable(CARD_BACKGROUND, 6)
        }
    }

    private fun createFloatingActionItem(label: String, iconRes: Int): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = ctx.dp(FLOATING_MENU_ITEM_ELEVATION_DP).toFloat()
            background = roundedDrawableWithStroke(
                fillColor = Color.parseColor(FLOATING_MENU_BG),
                radiusDp = FLOATING_MENU_ITEM_HEIGHT_DP / 2,
                strokeColor = Color.parseColor(FLOATING_MENU_STROKE),
                strokeWidthDp = 1,
            )
            clipChildren = false
            clipToPadding = false
            addView(
                View(ctx),
                LinearLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_TEXT_LEADING_SPACER_DP),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
            addView(
                TextView(ctx).apply {
                    text = label
                    alpha = 1f
                    textSize = FLOATING_MENU_TEXT_SIZE_SP
                    typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    applyFloatingMenuGradient(this)
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f,
                )
            )
            addView(
                FrameLayout(ctx).apply {
                    background = roundedDrawable(FLOATING_MENU_ICON_SLOT_BG, FLOATING_MENU_ICON_SLOT_SIZE_DP / 2)
                    elevation = ctx.dp(FLOATING_MENU_ITEM_ELEVATION_DP).toFloat()
                    addView(
                        ImageView(ctx).apply {
                            setImageResource(iconRes)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        },
                        FrameLayout.LayoutParams(
                            ctx.dp(FLOATING_MENU_ITEM_ICON_SIZE_DP),
                            ctx.dp(FLOATING_MENU_ITEM_ICON_SIZE_DP),
                            Gravity.CENTER,
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    ctx.dp(FLOATING_MENU_ICON_SLOT_SIZE_DP),
                    ctx.dp(FLOATING_MENU_ICON_SLOT_SIZE_DP),
                ).apply {
                    marginStart = ctx.dp(FLOATING_MENU_ICON_SLOT_START_MARGIN_DP)
                }
            )
        }
    }

    private fun applyFloatingMenuGradient(textView: TextView) {
        val gradientColors = intArrayOf(
            Color.parseColor("#4BC4FF"),
            Color.parseColor("#1A9AFF"),
            Color.parseColor("#9676FF"),
            Color.parseColor("#BE64FE"),
            Color.parseColor("#E157CB"),
            Color.parseColor("#EF5794"),
            Color.parseColor("#FD683F"),
            Color.parseColor("#FE7C2B"),
            Color.parseColor("#FFA10B"),
        )
        val gradientPositions = floatArrayOf(
            0.0034f,
            0.1376f,
            0.2718f,
            0.4457f,
            0.5849f,
            0.6892f,
            0.8284f,
            0.9427f,
            0.9973f,
        )
        textView.paint.shader = null
        textView.setTextColor(Color.parseColor(FLOATING_MENU_TEXT))
        textView.post {
            val textWidth = textView.paint.measureText(textView.text.toString()).coerceAtLeast(1f)
            textView.paint.shader = LinearGradient(
                0f,
                0f,
                textWidth,
                0f,
                gradientColors,
                gradientPositions,
                Shader.TileMode.CLAMP,
            )
            textView.invalidate()
        }
    }

    private fun buildAttachmentPreviewView(part: FavoritePart.Attachment): View {
        val file = part.storedPath.takeIf { it.isNotBlank() }?.let(::File)
        val imageBitmap = when (part.pickerType) {
            FavoritePickerType.IMAGE -> file?.let(::decodeImagePreview)
            FavoritePickerType.VIDEO -> file?.let(::decodeVideoPreview)
            else -> null
        }
        if (imageBitmap != null) {
            return ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(imageBitmap)
                clipToOutline = true
                background = roundedDrawable(MEDIA_BG, 10)
            }
        }
        if (part.pickerType == FavoritePickerType.IMAGE && part.downloadUrl.isNotBlank()) {
            val placeholderView = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(TEXT_SECONDARY))
            }
            val previewView = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            return FrameLayout(ctx).apply {
                background = roundedDrawable(MEDIA_BG, 10)
                clipToOutline = true
                val previewKey = "outer:${part.pickerType}:${part.downloadUrl}:${ctx.dp(220)}:${ctx.dp(220)}"
                addView(
                    previewView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    placeholderView,
                    FrameLayout.LayoutParams(
                        ctx.dp(24),
                        ctx.dp(24),
                        Gravity.CENTER
                    )
                )
                previewView.tag = previewKey
                previewBitmapFromCache(previewKey)?.let { cached ->
                    previewView.setImageBitmap(cached)
                    previewView.visibility = View.VISIBLE
                    placeholderView.visibility = View.GONE
                    return@apply
                }
                previewExecutor.execute {
                    val bitmap = decodeRemoteImagePreview(part.downloadUrl)
                    if (bitmap != null) {
                        previewBitmapCache.put(previewKey, bitmap)
                    }
                    previewMainHandler.post {
                        if (previewView.tag != previewKey) return@post
                        if (bitmap != null) {
                            previewView.setImageBitmap(bitmap)
                            previewView.visibility = View.VISIBLE
                            placeholderView.visibility = View.GONE
                        } else {
                            previewView.visibility = View.GONE
                            placeholderView.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
        return ImageView(ctx).apply {
            setImageResource(
                if (part.attachmentType == FavoriteAttachmentType.DOCUMENT) {
                    R.drawable.ic_baseline_library_books_24
                } else {
                    R.drawable.ic_baseline_auto_awesome_24
                }
            )
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(TEXT_SECONDARY))
            background = roundedDrawable(
                if (part.attachmentType == FavoriteAttachmentType.DOCUMENT) DOCUMENT_BG else MEDIA_BG,
                10
            )
            setPadding(ctx.dp(18))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun buildEmbeddedPickerPreviewView(item: FavoriteEmbeddedPickerItem): View {
        val imageBitmap = when (item.pickerType) {
            FavoritePickerType.IMAGE,
            FavoritePickerType.VIDEO -> decodeContentPreview(item)
            FavoritePickerType.FILE,
            FavoritePickerType.TEXT -> null
        }
        if (imageBitmap != null) {
            return ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(imageBitmap)
                background = roundedDrawable(MEDIA_BG, 10)
                clipToOutline = true
            }
        }
        return ImageView(ctx).apply {
            setImageResource(
                if (item.pickerType == FavoritePickerType.VIDEO) {
                    android.R.drawable.ic_media_play
                } else {
                    R.drawable.ic_baseline_library_books_24
                }
            )
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(TEXT_SECONDARY))
            background = roundedDrawable(
                if (item.pickerType == FavoritePickerType.FILE) DOCUMENT_BG else MEDIA_BG,
                10
            )
            setPadding(ctx.dp(18))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun decodeImagePreview(file: File): Bitmap? {
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun decodeRemoteImagePreview(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            URL(url).openStream().use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun decodeVideoPreview(file: File): Bitmap? {
        if (!file.exists()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.media.ThumbnailUtils.createVideoThumbnail(file, Size(ctx.dp(160), ctx.dp(160)), null)
            } else {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(file.absolutePath)
                    retriever.frameAtTime
                }
            }
        }.getOrNull()
    }

    private fun decodeContentPreview(item: FavoriteEmbeddedPickerItem): Bitmap? {
        val reqWidthPx = ctx.dp(220)
        val reqHeightPx = ctx.dp(220)
        val cacheKey = embeddedPreviewCacheKey(item, reqWidthPx, reqHeightPx)
        previewBitmapFromCache(cacheKey)?.let { return it }
        return decodeContentPreview(item, reqWidthPx, reqHeightPx)?.also { bitmap ->
            previewBitmapCache.put(cacheKey, bitmap)
        }
    }

    private fun decodeContentPreview(
        item: FavoriteEmbeddedPickerItem,
        reqWidthPx: Int,
        reqHeightPx: Int,
    ): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ctx.contentResolver.loadThumbnail(item.uri, Size(reqWidthPx, reqHeightPx), null)
            } else {
                when (item.pickerType) {
                    FavoritePickerType.IMAGE ->
                        ctx.contentResolver.openInputStream(item.uri)?.use(BitmapFactory::decodeStream)
                    FavoritePickerType.VIDEO ->
                        MediaMetadataRetriever().use { retriever ->
                            retriever.setDataSource(ctx, item.uri)
                            retriever.frameAtTime
                        }
                    FavoritePickerType.FILE,
                    FavoritePickerType.TEXT -> null
                }
            }
        }.getOrNull()
    }

    private fun embeddedPreviewCacheKey(
        item: FavoriteEmbeddedPickerItem,
        reqWidthPx: Int,
        reqHeightPx: Int,
    ): String {
        return "embedded:${item.pickerType}:${item.uri}"
    }

    private fun previewBitmapFromCache(cacheKey: String): Bitmap? {
        previewBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
            previewBitmapCache.remove(cacheKey)
        }
        return null
    }

    private fun calculateBackgroundHeightPx(): Int {
        val displayWidth = ctx.resources.displayMetrics.widthPixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.bg_favorite_panel_rectangle, bounds)
        val imageWidth = bounds.outWidth.takeIf { it > 0 } ?: return displayWidth
        val imageHeight = bounds.outHeight.takeIf { it > 0 } ?: return displayWidth
        return (displayWidth.toFloat() * imageHeight / imageWidth).toInt()
    }

    private fun roundedDrawable(color: String, radiusDp: Int): GradientDrawable {
        return roundedDrawable(Color.parseColor(color), radiusDp)
    }

    private fun roundedDrawable(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = ctx.dp(radiusDp).toFloat()
            setColor(color)
        }
    }

    private fun topRoundedDrawable(color: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            val radius = ctx.dp(radiusDp).toFloat()
            cornerRadii = floatArrayOf(
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
            )
            setColor(Color.parseColor(color))
        }
    }

    private fun roundedDrawableWithStroke(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int,
        strokeWidthDp: Int,
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = ctx.dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(ctx.dp(strokeWidthDp), strokeColor)
        }
    }

    internal class FavoriteAddCardView(private val ctx: Context) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = ctx.dp(172)
            background = GradientDrawable().apply {
                cornerRadius = ctx.dp(18).toFloat()
                setColor(Color.parseColor("#F2EEFF"))
                setStroke(ctx.dp(1), Color.parseColor("#9F8CFF"), ctx.dp(4).toFloat(), ctx.dp(4).toFloat())
            }
            setPadding(ctx.dp(18))
            addView(FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FFFFFF"))
                    setStroke(ctx.dp(1), Color.parseColor("#9F8CFF"), ctx.dp(3).toFloat(), ctx.dp(3).toFloat())
                }
                addView(ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_baseline_plus_24)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6B4EFF"))
                }, FrameLayout.LayoutParams(ctx.dp(18), ctx.dp(18), Gravity.CENTER))
            }, LayoutParams(ctx.dp(44), ctx.dp(44)))
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.favorite_new_entry)
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = 15f
                gravity = Gravity.CENTER
            }, LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(14)
            })
        }
    }

    internal class FavoriteCardView(private val ctx: Context) : LinearLayout(ctx) {
        init {
            orientation = VERTICAL
            background = roundedDrawableStatic(CARD_BACKGROUND, 9)
            setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(12))
            elevation = ctx.dp(2).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = Color.parseColor("#24000000")
                outlineSpotShadowColor = Color.parseColor("#18000000")
            }
        }

        fun bind(
            item: FavoriteRecord,
            onPreviewClick: () -> Unit,
        ) {
            removeAllViews()
            val texts = item.parts
                .filterIsInstance<FavoritePart.Text>()
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
            val attachments = item.parts.filterIsInstance<FavoritePart.Attachment>()
            val primaryText = texts.firstOrNull()
            val documents = attachments.filter(::isDocumentAttachment)
            val body = LinearLayout(ctx).apply {
                orientation = VERTICAL
                setPadding(0, 0, 0, 0)
                isClickable = true
                isFocusable = true
                // #region debug-point flash-preview-blank-touch
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        Log.d(
                            "FlashPreviewDebug",
                            "recordTouchDown record=${item.id} rawX=${event.rawX} rawY=${event.rawY}"
                        )
                    }
                    false
                }
                // #endregion
                setOnClickListener { onPreviewClick() }
            }
            when {
                primaryText != null && attachments.isEmpty() -> {
                    body.addView(buildTextBlock(primaryText))
                }
                primaryText != null && attachments.size >= 3 && documents.size == attachments.size -> {
                    body.addView(buildTextBlock(primaryText))
                    body.addView(buildSpacer(10))
                    body.addView(buildAttachmentStrip(attachments, maxVisible = 3, tileHeightDp = 40, cornerRadiusDp = 6))
                }
                primaryText != null && attachments.size >= 3 -> {
                    body.addView(buildTextBlock(primaryText))
                    body.addView(buildSpacer(10))
                    body.addView(buildAttachmentStrip(attachments, maxVisible = 3, tileHeightDp = 40, cornerRadiusDp = 6))
                }
                primaryText != null && attachments.size == 2 -> {
                    body.addView(buildTextBlock(primaryText))
                    body.addView(buildSpacer(10))
                    body.addView(buildAttachmentStrip(attachments, maxVisible = 2, tileHeightDp = 68, cornerRadiusDp = 6))
                }
                primaryText != null && attachments.size == 1 && isDocumentAttachment(attachments.first()) -> {
                    body.addView(buildTextBlock(primaryText))
                    body.addView(buildSpacer(10))
                    body.addView(buildDocumentRow(attachments.first()))
                }
                primaryText != null && attachments.size == 1 -> {
                    body.addView(buildTextBlock(primaryText))
                    body.addView(buildSpacer(10))
                    body.addView(buildLargeMediaCard(attachments.first()))
                }
                primaryText == null && attachments.size == 1 && isDocumentAttachment(attachments.first()) -> {
                    body.addView(buildDocumentRow(attachments.first()))
                }
                primaryText == null && attachments.size == 1 -> {
                    body.addView(buildLargeMediaCard(attachments.first()))
                }
                primaryText == null && attachments.size == 2 && documents.size == attachments.size -> {
                    body.addView(buildDocumentColumn(attachments, maxVisible = 2))
                }
                primaryText == null && attachments.size >= 3 && documents.size == attachments.size -> {
                    body.addView(buildDocumentColumn(attachments, maxVisible = 3))
                }
                primaryText == null && attachments.size >= 2 -> {
                    body.addView(
                        buildAttachmentStrip(
                            attachments,
                            maxVisible = if (attachments.size >= 3) 3 else 2,
                            tileHeightDp = if (attachments.size >= 3) 40 else 68,
                            cornerRadiusDp = 6
                        )
                    )
                }
                else -> {
                    body.addView(buildTextBlock(ctx.getString(R.string.favorite_empty_record_preview)))
                }
            }
            addView(body, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        private fun buildTextBlock(text: String): View {
            return TextView(ctx).apply {
                this.text = text
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                textSize = 12f
                maxLines = 6
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(ctx.dp(4).toFloat(), 1f)
            }
        }

        private fun buildLargeMediaCard(part: FavoritePart.Attachment): View {
            return buildAttachmentTile(
                part = part,
                tileHeightDp = 156,
                cornerRadiusDp = 6,
                overlayCount = null
            ).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(156)
                )
            }
        }

        private fun buildAttachmentStrip(
            attachments: List<FavoritePart.Attachment>,
            maxVisible: Int,
            tileHeightDp: Int,
            cornerRadiusDp: Int,
        ): View {
            val visibleCount = attachments.size.coerceAtMost(maxVisible)
            return LinearLayout(ctx).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                repeat(visibleCount) { index ->
                    val overlayCount = if (attachments.size > maxVisible && index == visibleCount - 1) {
                        attachments.size - (visibleCount - 1)
                    } else {
                        null
                    }
                    addView(
                        buildAttachmentTile(
                            part = attachments[index],
                            tileHeightDp = tileHeightDp,
                            cornerRadiusDp = cornerRadiusDp,
                            overlayCount = overlayCount
                        ),
                        LayoutParams(
                            0,
                            ctx.dp(tileHeightDp),
                            1f
                        ).apply {
                            if (index > 0) marginStart = ctx.dp(6)
                        }
                    )
                }
            }
        }

        private fun buildDocumentColumn(
            attachments: List<FavoritePart.Attachment>,
            maxVisible: Int,
        ): View {
            val visibleCount = attachments.size.coerceAtMost(maxVisible)
            return LinearLayout(ctx).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                repeat(visibleCount) { index ->
                    val overlayCount = if (attachments.size > maxVisible && index == visibleCount - 1) {
                        attachments.size - (visibleCount - 1)
                    } else {
                        null
                    }
                    addView(
                        buildDocumentRow(attachments[index], overlayCount),
                        LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index > 0) topMargin = ctx.dp(10)
                        }
                    )
                }
            }
        }

        private fun buildDocumentRow(
            part: FavoritePart.Attachment,
            overlayCount: Int? = null,
        ): View {
            return FrameLayout(ctx).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(
                    LinearLayout(ctx).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = roundedDrawableStatic(DOCUMENT_BG, 6)
                        setPadding(ctx.dp(12), ctx.dp(11), ctx.dp(10), ctx.dp(11))
                        addView(buildDocumentIconView(part, 36), LayoutParams(ctx.dp(36), ctx.dp(36)))
                        addView(TextView(ctx).apply {
                            text = part.displayName.ifBlank { ctx.getString(R.string.favorite_attachment_document) }
                            setTextColor(Color.parseColor(TEXT_PRIMARY))
                            textSize = 12f
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                            setLineSpacing(ctx.dp(4).toFloat(), 1f)
                        }, LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = ctx.dp(10)
                        })
                    },
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                overlayCount?.let { count ->
                    addView(
                        buildCountOverlay(count, 6),
                        LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        private fun buildAttachmentTile(
            part: FavoritePart.Attachment,
            tileHeightDp: Int,
            cornerRadiusDp: Int,
            overlayCount: Int?,
        ): View {
            val backgroundColor = if (isDocumentAttachment(part)) DOCUMENT_BG else MEDIA_BG
            return FrameLayout(ctx).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(tileHeightDp)
                )
                background = roundedDrawableStatic(backgroundColor, cornerRadiusDp)
                clipToOutline = true
                when {
                    isDocumentAttachment(part) -> {
                        addView(
                            buildDocumentIconView(
                                part,
                                iconSizeDp = if (tileHeightDp <= 44) 28 else 32
                            ),
                            FrameLayout.LayoutParams(
                                if (tileHeightDp <= 44) ctx.dp(28) else ctx.dp(32),
                                if (tileHeightDp <= 44) ctx.dp(28) else ctx.dp(32),
                                Gravity.CENTER
                            )
                        )
                    }
                    else -> {
                        val placeholderView = ImageView(ctx).apply {
                            setImageResource(R.drawable.ic_baseline_auto_awesome_24)
                            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(TEXT_SECONDARY))
                        }
                        val previewView = ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            visibility = View.GONE
                        }
                        addView(
                            previewView,
                            LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        addView(
                            placeholderView,
                            FrameLayout.LayoutParams(
                                ctx.dp(24),
                                ctx.dp(24),
                                Gravity.CENTER
                            )
                        )
                        bindPreviewAsync(
                            imageView = previewView,
                            placeholderView = placeholderView,
                            part = part,
                            reqWidthPx = ctx.dp(tileHeightDp),
                            reqHeightPx = ctx.dp(tileHeightDp)
                        )
                    }
                }
                if (part.attachmentType == FavoriteAttachmentType.VIDEO) {
                    addView(
                        buildPlayIndicator(),
                        FrameLayout.LayoutParams(
                            ctx.dp(if (tileHeightDp >= 120) 42 else 30),
                            ctx.dp(if (tileHeightDp >= 120) 42 else 30),
                            Gravity.CENTER
                        )
                    )
                }
                overlayCount?.let { count ->
                    addView(
                        buildCountOverlay(count, cornerRadiusDp),
                        LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        private fun bindPreviewAsync(
            imageView: ImageView,
            placeholderView: View,
            part: FavoritePart.Attachment,
            reqWidthPx: Int,
            reqHeightPx: Int,
        ) {
            val sourceKey = part.storedPath.takeIf { it.isNotBlank() } ?: part.downloadUrl
            val previewKey = "${part.pickerType}:$sourceKey:$reqWidthPx:$reqHeightPx"
            imageView.tag = previewKey
            val cached = previewBitmapCache.get(previewKey)
            if (cached != null) {
                imageView.setImageBitmap(cached)
                imageView.visibility = View.VISIBLE
                placeholderView.visibility = View.GONE
                return
            }
            imageView.setImageDrawable(null)
            imageView.visibility = View.GONE
            placeholderView.visibility = View.VISIBLE
            previewExecutor.execute {
                val bitmap = when (part.pickerType) {
                    FavoritePickerType.IMAGE -> {
                        if (part.storedPath.isNotBlank()) {
                            decodeImagePreview(File(part.storedPath), reqWidthPx, reqHeightPx)
                        } else {
                            decodeRemoteImagePreview(part.downloadUrl)
                        }
                    }
                    FavoritePickerType.VIDEO -> {
                        if (part.storedPath.isNotBlank()) {
                            decodeVideoPreview(File(part.storedPath), reqWidthPx, reqHeightPx)
                        } else {
                            null
                        }
                    }
                    FavoritePickerType.FILE,
                    FavoritePickerType.TEXT -> null
                }
                if (bitmap != null) {
                    previewBitmapCache.put(previewKey, bitmap)
                }
                previewMainHandler.post {
                    if (imageView.tag != previewKey) return@post
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        placeholderView.visibility = View.GONE
                    } else {
                        imageView.visibility = View.GONE
                        placeholderView.visibility = View.VISIBLE
                    }
                }
            }
        }

        private fun buildDocumentIconView(part: FavoritePart.Attachment, iconSizeDp: Int): View {
            return ImageView(ctx).apply {
                setImageResource(resolveDocumentIconRes(part))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }

        private fun buildPlayIndicator(): View {
            return FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#66FFFFFF"))
                    setStroke(ctx.dp(1), Color.parseColor("#CCFFFFFF"))
                }
                addView(
                    ImageView(ctx).apply {
                        setImageResource(android.R.drawable.ic_media_play)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
        }

        private fun buildCountOverlay(count: Int, cornerRadiusDp: Int): View {
            return FrameLayout(ctx).apply {
                background = roundedDrawableStatic("#91000000", cornerRadiusDp)
                addView(
                    TextView(ctx).apply {
                        text = "+$count"
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
                        gravity = Gravity.CENTER
                    },
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }

        private fun buildSpacer(heightDp: Int): View {
            return View(ctx).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ctx.dp(heightDp)
                )
            }
        }

        private fun isDocumentAttachment(part: FavoritePart.Attachment): Boolean {
            return part.attachmentType == FavoriteAttachmentType.DOCUMENT
        }

        private fun resolveDocumentIconRes(part: FavoritePart.Attachment): Int {
            val lowerName = part.displayName.lowercase()
            val mime = part.mimeType.lowercase()
            return when {
                lowerName.endsWith(".pdf") || mime.contains("pdf") -> R.drawable.ic_office_pdf_36
                lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") ||
                    mime.contains("presentation") -> R.drawable.ic_office_ppt_36
                lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") ||
                    lowerName.endsWith(".csv") || mime.contains("spreadsheet") ||
                    mime.contains("excel") || mime.contains("sheet") -> R.drawable.ic_office_excel_36
                lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                    mime.contains("word") || mime.contains("document") -> R.drawable.ic_office_word_36
                else -> R.drawable.ic_baseline_library_books_24
            }
        }

        private fun decodeImagePreview(file: File): Bitmap? {
            if (!file.exists()) return null
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        private fun decodeImagePreview(file: File, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidthPx, reqHeightPx)
            }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private fun decodeVideoPreview(file: File): Bitmap? {
            if (!file.exists()) return null
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.media.ThumbnailUtils.createVideoThumbnail(file, Size(ctx.dp(160), ctx.dp(160)), null)
                } else {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(file.absolutePath)
                        retriever.frameAtTime
                    }
                }
            }.getOrNull()
        }

        private fun decodeVideoPreview(file: File, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
            if (!file.exists()) return null
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.media.ThumbnailUtils.createVideoThumbnail(file, Size(reqWidthPx, reqHeightPx), null)
                } else {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(file.absolutePath)
                        retriever.frameAtTime
                    }
                }
            }.getOrNull()
        }

        private fun decodeRemoteImagePreview(url: String): Bitmap? {
            if (url.isBlank()) return null
            return runCatching {
                URL(url).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
        }

        private fun calculateInSampleSize(
            bounds: BitmapFactory.Options,
            reqWidthPx: Int,
            reqHeightPx: Int,
        ): Int {
            val sourceWidth = bounds.outWidth
            val sourceHeight = bounds.outHeight
            if (sourceWidth <= 0 || sourceHeight <= 0 || reqWidthPx <= 0 || reqHeightPx <= 0) {
                return 1
            }
            var sampleSize = 1
            var halfWidth = sourceWidth / 2
            var halfHeight = sourceHeight / 2
            while (halfWidth / sampleSize >= reqWidthPx && halfHeight / sampleSize >= reqHeightPx) {
                sampleSize *= 2
            }
            return sampleSize.coerceAtLeast(1)
        }

        private fun roundedDrawableStatic(color: String, radiusDp: Int): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = ctx.dp(radiusDp).toFloat()
                setColor(Color.parseColor(color))
            }
        }
    }

    internal class FavoriteFolderCardView(private val ctx: Context) : FrameLayout(ctx) {
        private val backgroundView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_XY
        }

        private val iconView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        private val titleView = TextView(ctx).apply {
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            textSize = 16f
            typeface = Typeface.defaultFromStyle(Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        private val countView = TextView(ctx).apply {
            setTextColor(Color.parseColor("#99000000"))
            textSize = 16f
            maxLines = 1
        }

        init {
            minimumHeight = ctx.dp(172)
            addView(backgroundView, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(23), ctx.dp(24), ctx.dp(23), ctx.dp(23))
                addView(iconView, LinearLayout.LayoutParams(ctx.dp(53), ctx.dp(53)))
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(titleView)
                    addView(countView, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = ctx.dp(10)
                    })
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ctx.dp(18)
                })
            }, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        fun bind(item: FavoriteFolderItem) {
            backgroundView.setImageResource(item.backgroundRes)
            iconView.setImageResource(item.iconRes)
            titleView.text = item.title
            countView.text = ctx.getString(R.string.favorite_folder_material_count, item.materialCount)
        }
    }
}
