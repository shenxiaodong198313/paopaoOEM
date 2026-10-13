package com.paopaoime.aikeyboard.ime.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.adapter.CandidatesBarAdapter
import com.paopaoime.aikeyboard.ime.adapter.CandidatesMenuAdapter
import com.paopaoime.aikeyboard.ime.callback.CandidateViewListener
import com.paopaoime.aikeyboard.ime.application.CustomConstant
import com.paopaoime.aikeyboard.ime.data.flower.FlowerTypefaceMode
import com.paopaoime.aikeyboard.ime.data.menuSkbFunsPreset
import com.paopaoime.aikeyboard.ime.data.theme.ThemeManager
import com.paopaoime.aikeyboard.ime.database.DataBaseKT
import com.paopaoime.aikeyboard.ime.entity.SkbFunItem
import com.paopaoime.aikeyboard.ime.prefs.AppPrefs
import com.paopaoime.aikeyboard.ime.prefs.behavior.KeyboardOneHandedMod
import com.paopaoime.aikeyboard.ime.prefs.behavior.SkbMenuMode
import com.paopaoime.aikeyboard.ime.service.DecodingInfo
import com.paopaoime.aikeyboard.ime.singleton.EnvironmentSingleton.Companion.instance
import com.paopaoime.aikeyboard.ime.keyboard.KeyboardManager
import com.paopaoime.aikeyboard.ime.keyboard.container.CandidatesContainer
import com.paopaoime.aikeyboard.ime.ui.settings.InputMethodSettingsBridge
import com.paopaoime.aikeyboard.ime.keyboard.container.ClipBoardContainer
import com.paopaoime.aikeyboard.ime.keyboard.container.InputBaseContainer
import com.paopaoime.aikeyboard.ime.manager.layout.CustomLinearLayoutManager
import android.graphics.drawable.GradientDrawable
import splitties.dimensions.dp

/**
 * 候选词集装箱
 */
class CandidatesBar(context: Context?, attrs: AttributeSet?) : RelativeLayout(context, attrs) {

    private lateinit var mCvListener: CandidateViewListener // 候选词视图监听器
    private lateinit var mRightArrowBtn: ImageView // 右边箭头按钮
    private lateinit var mMenuRightArrowBtn: ImageView
    private lateinit var mCandidatesDataContainer: LinearLayout //候选词视图
    private lateinit var mCandidatesMenuContainer: LinearLayout //控制菜单视图
    private lateinit var mComposingView: TextView // 组成字符串的View，用于显示输入的拼音。
    private lateinit var mRVCandidates: RecyclerView    //候选词列表
    private lateinit var mIvMenuSetting: ImageView
    private lateinit var mLlContainer: LinearLayout
    private lateinit var mFlowerType: TextView
    private lateinit var mCandidatesAdapter: CandidatesBarAdapter
    private lateinit var mRVContainerMenu:RecyclerView   // 候选词栏菜单
    private lateinit var mCandidatesMenuAdapter: CandidatesMenuAdapter
    private lateinit var candidatesData: LinearLayout //候选词视图
    private var activeCandNo:Int = 0
    private var phrasesAddToolbarMode: Boolean = false

    fun setPhrasesAddToolbarMode(enabled: Boolean) {
        if (phrasesAddToolbarMode == enabled) return
        phrasesAddToolbarMode = enabled
        if (::mCandidatesMenuContainer.isInitialized) {
            applyToolbarMode()
            mCandidatesMenuAdapter.notifyChanged()
        }
    }

    private fun applyToolbarMode() {
        if (!::mCandidatesMenuContainer.isInitialized) return
        if (phrasesAddToolbarMode) {
            mIvMenuSetting.visibility = GONE
            mMenuRightArrowBtn.visibility = GONE
            mLlContainer.visibility = GONE
        } else {
            mIvMenuSetting.visibility = VISIBLE
            mMenuRightArrowBtn.visibility = VISIBLE
            mLlContainer.visibility =
                if (CustomConstant.flowerTypeface == FlowerTypefaceMode.Disabled) GONE else VISIBLE
        }
    }

    fun initialize(cvListener: CandidateViewListener) {
        mCvListener = cvListener
        initMenuView()
        initCandidateView()
    }

    // 初始化候选词界面
    private fun initCandidateView() {
        if(!::mCandidatesDataContainer.isInitialized) {
            mCandidatesDataContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = GONE
            }
            mComposingView = TextView(context).apply {
                includeFontPadding = false
                setPadding(dp(10), 0, dp(10), 0)
            }
            candidatesData = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            mRightArrowBtn = ImageView(context).apply {
                isClickable = true
                isEnabled = true
                setImageResource(R.drawable.sdk_level_list_candidates_display)
            }
            mRVCandidates = RecyclerView(context).apply {
                setItemAnimator(null)
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                layoutManager =
                    CustomLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            mCandidatesAdapter = CandidatesBarAdapter(context)
            mCandidatesAdapter.setOnItemClickLitener { _: RecyclerView.Adapter<*>?, _: View?, position: Int ->
                mCvListener.onClickChoice(position)
            }
            mRVCandidates.setAdapter(mCandidatesAdapter)
            mRVCandidates.addOnScrollListener(object : OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        DecodingInfo.activeCandidateBar =
                            layoutManager.findLastVisibleItemPosition()
                        val itemCount = recyclerView.adapter?.itemCount
                        if (KeyboardManager.instance.currentContainer !is CandidatesContainer && itemCount != null && DecodingInfo.activeCandidateBar >= itemCount - 1) {
                            DecodingInfo.nextPageCandidates
                        }
                    }
                }
            })
            mCandidatesDataContainer.addView(mComposingView)
            mCandidatesDataContainer.addView(candidatesData)
            this.addView(mCandidatesDataContainer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            (mRightArrowBtn.parent as ViewGroup).removeView(mRightArrowBtn)
            (mRVCandidates.parent as ViewGroup).removeView(mRVCandidates)
        }
        var candidatesHeight = instance.heightForCandidates
        mComposingView.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, instance.heightForcomposing)
        mRightArrowBtn.layoutParams = LinearLayout.LayoutParams(candidatesHeight, candidatesHeight, 0f).apply { marginEnd = dp(10) }
        candidatesData.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, candidatesHeight)
        mRightArrowBtn.setOnClickListener { view: View ->
            when (val level = (view as ImageView).drawable.level) {
                2 -> mCvListener.onClickClearCandidate()
                else -> {
                    mCvListener.onClickMore(level)
                    view.drawable.setLevel(1 - level)
                }
            }
        }
        val oneHandedModSwitch = AppPrefs.getInstance().keyboardSetting.oneHandedModSwitch.getValue()
        val oneHandedMod = AppPrefs.getInstance().keyboardSetting.oneHandedMod.getValue()
        if (oneHandedModSwitch && oneHandedMod == KeyboardOneHandedMod.LEFT) {
            candidatesData.addView(mRightArrowBtn)
            candidatesData.addView(mRVCandidates, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, candidatesHeight, 1f))
        } else {
            candidatesData.addView(mRVCandidates, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, candidatesHeight, 1f))
            candidatesData.addView(mRightArrowBtn)
        }
        mComposingView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, instance.composingTextSize)
        mCandidatesAdapter.notifyChanged()
    }

    //初始化标题栏
    fun initMenuView() {
        if(!::mCandidatesMenuContainer.isInitialized) {
            this.removeAllViews()
            mCandidatesMenuContainer = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            mIvMenuSetting = ImageView(context).apply {
                setImageResource(R.drawable.sdk_level_candidates_menu_left)
                isClickable = true
                isEnabled = true
                setOnClickListener {
                if (KeyboardManager.instance.currentContainer is ClipBoardContainer) {
                    KeyboardManager.instance.switchKeyboard()
                } else {
                    mCvListener.onClickMenu(SkbMenuMode.SettingsMenu)
                }
            }
            }
            mLlContainer = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            mFlowerType = TextView(context).apply {
                setTextColor(ThemeManager.activeTheme.keyTextColor)
                setPadding(dp(10), 0, 0, 0)
            }
            val flowerTypefaces = arrayOf(FlowerTypefaceMode.Mars, FlowerTypefaceMode.FlowerVine, FlowerTypefaceMode.Messy, FlowerTypefaceMode.Germinate,
                FlowerTypefaceMode.Fog,FlowerTypefaceMode.ProhibitAccess, FlowerTypefaceMode.Grass, FlowerTypefaceMode.Wind, FlowerTypefaceMode.Disabled)
            val flowerTypefacesName = resources.getStringArray(R.array.FlowerTypeface)
            if (CustomConstant.flowerTypeface == FlowerTypefaceMode.Disabled) {
                mLlContainer.visibility = GONE
            } else {
                mFlowerType.text =
                    flowerTypefacesName[flowerTypefaces.indexOf(CustomConstant.flowerTypeface)]
            }
            mFlowerType.setOnClickListener { _: View ->
                val popupMenu = PopupMenu(context, mLlContainer).apply {
                    menuInflater.inflate(R.menu.flower_typeface_menu, menu)
                    setOnMenuItemClickListener { menuItem ->
                        val ids = listOf(R.id.flower_type_mars, R.id.flower_type_flowervine, R.id.flower_type_messy, R.id.flower_type_grminate,
                            R.id.flower_type_fog, R.id.flower_type_prohibitaccess, R.id.flower_type_grass, R.id.flower_type_wind, R.id.flower_type_disabled
                        )
                        val position = ids.indexOf(menuItem.itemId)
                        val select = flowerTypefaces[position]
                        mFlowerType.text = flowerTypefacesName[position]
                        CustomConstant.flowerTypeface = select
                        if (select == FlowerTypefaceMode.Disabled) {
                            mLlContainer.visibility = GONE
                        }
                        mCandidatesMenuAdapter.notifyChanged()// 鍒锋柊鑿滃崟鏍?
                        false
                    }
                }
                popupMenu.show()
            }
            mLlContainer.addView(mFlowerType, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            mRVContainerMenu = RecyclerView(context).apply {
                setItemAnimator(null)
                layoutManager = CustomLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, true)
            }
            mCandidatesMenuAdapter = CandidatesMenuAdapter(context)
            mCandidatesMenuAdapter.setOnItemClickLitener { _: RecyclerView.Adapter<*>?, view: View?, position: Int ->
                val skbMenuMode = mCandidatesMenuAdapter.getMenuMode(position)
                if (skbMenuMode != null) onClickMenu(skbMenuMode, view)
            }
            mRVContainerMenu.setAdapter(mCandidatesMenuAdapter)
            mMenuRightArrowBtn = ImageView(context).apply {
                isClickable = true
                isEnabled = true
                setImageResource(R.drawable.ic_menu_arrow_down)
            }
            mMenuRightArrowBtn.setOnClickListener { _: View ->
                if (KeyboardManager.instance.currentContainer is ClipBoardContainer) {
                    KeyboardManager.instance.switchKeyboard()
                } else {
                    mCvListener.onClickMenu(SkbMenuMode.CloseSKB)
                }
            }
            mCandidatesMenuContainer.addView(mIvMenuSetting)
            mCandidatesMenuContainer.addView(mLlContainer)
            mCandidatesMenuContainer.addView(mRVContainerMenu)
            mCandidatesMenuContainer.addView(mMenuRightArrowBtn)
            this.addView(
                mCandidatesMenuContainer,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
        }
        applyToolbarMode()
        var menuHeight = (instance.heightForCandidatesArea * 0.8).toInt()
        mFlowerType.textSize = instance.candidateTextSize
        mIvMenuSetting.layoutParams = LinearLayout.LayoutParams(menuHeight, menuHeight, 0f).apply { marginStart = dp(10) }
        mMenuRightArrowBtn.layoutParams = LinearLayout.LayoutParams(menuHeight, menuHeight, 0f).apply { marginEnd = dp(10) }
        mLlContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, menuHeight,0f)
        mRVContainerMenu.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, menuHeight, 1f)
        mCandidatesMenuAdapter.notifyChanged()  // 鐐瑰嚮涓嬫媺鑿滃崟鍚庯紝闇€瑕佸埛鏂拌彍鍗曟爮
    }

    private fun onClickMenu(skbMenuMode: SkbMenuMode, view: View?) {
        if(skbMenuMode == SkbMenuMode.ClearClipBoard){
            val contextWrapper = ContextThemeWrapper(context, R.style.Theme_AppTheme)
            val popupMenu = PopupMenu(contextWrapper, view).apply {
                menuInflater.inflate(R.menu.clear_clipboard, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.clear -> {
                            mCvListener.onClickClearClipBoard()
                        }
                    }
                    false
                }
            }
            popupMenu.show()
        } else {
            mCvListener.onClickMenu(skbMenuMode)
        }
    }

    /**
     * 鏄剧ず鍊欓€夎瘝
     */
    fun showCandidates() {
        mComposingView.text = DecodingInfo.composingStrForDisplay
        val container = KeyboardManager.instance.currentContainer
        mIvMenuSetting.drawable.setLevel( if(container is InputBaseContainer) 0 else 1)
        val keyboardContainer = (parent as? View)?.findViewById<View>(R.id.ll_input_keyboard_container)
        if (container is ClipBoardContainer) {
            (parent?.parent as? View)?.findViewById<View>(R.id.view_overlay)?.apply {
                visibility = VISIBLE
                layoutParams.height = (instance.mScreenHeight * 0.2).toInt()
                requestLayout()
                setOnClickListener {
                    KeyboardManager.instance.switchKeyboard()
                }
            }
            // 璁剧疆鍦嗚鑳屾櫙
            val bg = GradientDrawable().apply {
                setColor(ThemeManager.activeTheme.keyboardColor)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(), // Top-left
                    dp(16).toFloat(), dp(16).toFloat(), // Top-right
                    0f, 0f, // Bottom-right
                    0f, 0f  // Bottom-left
                )
            }
            keyboardContainer?.background = bg

            showViewVisibility(mCandidatesMenuContainer)
            mCandidatesMenuAdapter.items = if(container.getMenuMode() == SkbMenuMode.ClipBoard) {
                listOf(menuSkbFunsPreset[SkbMenuMode.ClearClipBoard]!!, menuSkbFunsPreset[SkbMenuMode.ClipBoard]!!, menuSkbFunsPreset[SkbMenuMode.Phrases]!!, menuSkbFunsPreset[SkbMenuMode.LockClipBoard]!!)
            } else {
                listOf(menuSkbFunsPreset[SkbMenuMode.AddPhrases]!!)
            }
        } else {
            (parent?.parent as? View)?.findViewById<View>(R.id.view_overlay)?.visibility = GONE
            keyboardContainer?.background = null // 鎭㈠榛樿鑳屾櫙
            if (DecodingInfo.isCandidatesListEmpty) {
                mRightArrowBtn.drawable.setLevel(0)
                showViewVisibility(mCandidatesMenuContainer)
                mCandidatesMenuAdapter.items = if (phrasesAddToolbarMode) {
                    listOf(menuSkbFunsPreset[SkbMenuMode.Emojicon]!!, menuSkbFunsPreset[SkbMenuMode.ClipBoard]!!)
                } else {
                    val mFunItems: MutableList<SkbFunItem> = mutableListOf()
                    val barMenus = DataBaseKT.instance.skbFunDao().getALlBarMenu()
                    for (item in barMenus) {
                        val skbMenuMode = SkbMenuMode.decode(item.name)
                        if (skbMenuMode == SkbMenuMode.TextEdit || skbMenuMode == SkbMenuMode.Emojicon) {
                            continue
                        }
                        if (
                            skbMenuMode == SkbMenuMode.PinyinHandWriting &&
                            !InputMethodSettingsBridge.isHandwritingEnabled(context)
                        ) {
                            continue
                        }
                        val skbFunItem = menuSkbFunsPreset[skbMenuMode]
                        if (skbFunItem != null) {
                            mFunItems.add(skbFunItem)
                        }
                    }
                    mFunItems
                }
            } else {
                if (DecodingInfo.candidateSize > DecodingInfo.activeCandidateBar) mRVCandidates.layoutManager?.scrollToPosition(DecodingInfo.activeCandidateBar)
                showViewVisibility(mCandidatesDataContainer)
                mRightArrowBtn.drawable.setLevel(if (DecodingInfo.isAssociate) 2 else if (KeyboardManager.instance.currentContainer is CandidatesContainer) 1 else 0)
            }
        }
        activeCandNo = 0
        mCandidatesAdapter.activeCandidates(activeCandNo)
        mCandidatesAdapter.notifyChanged()
        mCandidatesMenuAdapter.notifyChanged()
        if (container !is ClipBoardContainer && !DecodingInfo.isAssociate && DecodingInfo.candidateSize == 1) {
            post { mCvListener.onClickChoice(0) }
        }
    }

    /**
     * 鏄剧ず琛ㄦ儏
     */
    fun showEmoji() {
        showViewVisibility(mCandidatesMenuContainer)
        mCandidatesMenuAdapter.items = listOf(menuSkbFunsPreset[SkbMenuMode.Emoticon]!!,menuSkbFunsPreset[SkbMenuMode.Emojicon]!!)
        activeCandNo = 0
        mCandidatesAdapter.activeCandidates(activeCandNo)
        mCandidatesAdapter.notifyChanged()
        mCandidatesMenuAdapter.notifyChanged()
    }

    /**
     * 鏇存柊婵€娲荤殑鍊欓€夎瘝
     */
    fun updateActiveCandidateNo(keyCode: Int) {
        if (!DecodingInfo.isCandidatesListEmpty) {
            when(keyCode){
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if(--activeCandNo <= 0) activeCandNo = 0
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if(++activeCandNo > DecodingInfo.candidateSize) activeCandNo = DecodingInfo.candidateSize
                }
            }
            mCandidatesAdapter.activeCandidates(activeCandNo)
            mCandidatesAdapter.notifyChanged()
            mRVCandidates.layoutManager?.scrollToPosition(if(activeCandNo - 1 > 0) activeCandNo - 1 else 0 )
        }
    }

    /**
     * 鑾峰彇婵€娲荤殑鍊欓€夎瘝
     */
    fun getActiveCandNo():Int {
        return if(activeCandNo > 0) activeCandNo -1 else 0
    }

    /**
     * 鏄惁鎿嶄綔閫夎瘝
     */
    fun isActiveCand():Boolean {
        return activeCandNo > 0
    }

    /**
     * 閫夋嫨鑺辨季瀛?
     */
    fun showFlowerTypeface() {
        if(CustomConstant.flowerTypeface == FlowerTypefaceMode.Disabled) {
            mLlContainer.visibility = GONE
        } else {
            CustomConstant.flowerTypeface = FlowerTypefaceMode.Mars
            mFlowerType.text = "火星文"
            mLlContainer.visibility = VISIBLE
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMeasure = MeasureSpec.makeMeasureSpec(instance.heightForCandidatesArea, MeasureSpec.EXACTLY)
        val widthMeasure = MeasureSpec.makeMeasureSpec(instance.skbWidth, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasure, heightMeasure)
    }

    private fun showViewVisibility(candidatesContainer: View) {
        if(candidatesContainer === mCandidatesMenuContainer){
            mCandidatesMenuContainer.visibility = VISIBLE
            mCandidatesDataContainer.visibility = GONE
        } else {
            mCandidatesMenuContainer.visibility = GONE
            mCandidatesDataContainer.visibility = VISIBLE
        }
    }

    // 鍒锋柊涓婚
    fun updateTheme(textColor: Int) {
        initMenuView()
        initCandidateView()
        mIvMenuSetting.setImageResource(R.drawable.sdk_level_candidates_menu_left)
        mComposingView.setTextColor(textColor)
        mRightArrowBtn.drawable.setTint(textColor)
        mMenuRightArrowBtn.drawable.setTint(textColor)
        mIvMenuSetting.drawable.setTint(textColor)
        mCandidatesAdapter.notifyChanged()
        mCandidatesMenuAdapter.notifyChanged()
        mFlowerType.setTextColor(textColor)
    }
}

