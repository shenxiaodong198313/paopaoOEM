package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import android.util.DisplayMetrics
import kotlin.math.roundToInt

/** Skin Lab design units. System navigation insets remain physical pixels. */
object SkinLabStandard {
    fun enabled(theme: Theme) = (theme as? Theme.Custom)?.renderStyle?.skinLabStandard == true
    fun circleToolbar(context: Context) = context.getSharedPreferences("paopao_skin_lab", Context.MODE_PRIVATE).getBoolean("circle_toolbar", false)
    fun landscape(context: Context) = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    fun width(context: Context) = if (landscape(context)) 800 else 400
    fun grid(context: Context) = if (landscape(context)) 160 else 240
    fun bar(context: Context) = if (landscape(context)) 36 else 40
    fun gapX(context: Context) = if (landscape(context)) 8f else 6f
    fun gapY(context: Context) = if (landscape(context)) 8f else 10f

    // Use a private Resources instance: never mutate the service/application density.
    // dp and sp now resolve through one uniform factor for artwork, text and hit areas.
    @Suppress("DEPRECATION")
    fun contextFor(service: InputMethodService, theme: Theme): Context {
        if (!enabled(theme)) return service
        val source = service.resources
        val metrics = DisplayMetrics().apply { setTo(source.displayMetrics) }
        val usableWidth = service.maxWidth.takeIf { it > 0 } ?: metrics.widthPixels
        metrics.density = usableWidth.toFloat() / width(service)
        metrics.scaledDensity = metrics.density
        metrics.densityDpi = (metrics.density * 160).roundToInt()
        val configuration = Configuration(source.configuration).apply {
            densityDpi = metrics.densityDpi
            fontScale = 1f
        }
        val scoped = Resources(source.assets, metrics, configuration)
        scoped.displayMetrics.density = metrics.density
        scoped.displayMetrics.scaledDensity = metrics.density
        return object : ContextWrapper(service) {
            override fun getResources(): Resources = scoped
        }
    }
}
