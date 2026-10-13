package com.paopaoime.aikeyboard.ime.ui.activity

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.paopaoime.aikeyboard.ime.R

class CommonWebViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"

        fun createIntent(
            context: android.content.Context,
            url: String,
            title: String
        ) = android.content.Intent(context, CommonWebViewActivity::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_TITLE, title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        setContentView(R.layout.activity_common_webview)

        val targetUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.shizuku_guide_title) }
        val header = findViewById<View>(R.id.layout_header)
        val headerBar = findViewById<View>(R.id.layout_header_bar)
        val loadingView = findViewById<View>(R.id.layout_loading)
        val errorView = findViewById<TextView>(R.id.tv_error)
        val webView = findViewById<WebView>(R.id.web_common)
        findViewById<TextView>(R.id.tv_page_title).text = title
        findViewById<View>(R.id.btn_back_shell).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        val initialHeaderHeight = header.layoutParams.height
        val initialHeaderBarHeight = headerBar.layoutParams.height
        val headerExtraTop = (6 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(header) { view, windowInsets ->
            val topInset = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            view.layoutParams = view.layoutParams.apply {
                height = initialHeaderHeight + topInset + headerExtraTop
            }
            headerBar.layoutParams = headerBar.layoutParams.apply {
                height = initialHeaderBarHeight
            }
            headerBar.translationY = topInset.toFloat() + headerExtraTop
            windowInsets
        }
        ViewCompat.requestApplyInsets(header)

        if (targetUrl.isBlank()) {
            showError(errorView, loadingView, webView, "未配置说明页地址")
            return
        }
        val normalizedUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl
        } else {
            "https://$targetUrl"
        }

        @Suppress("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                return runCatching { startActivity(intent) }.isSuccess
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingView.visibility = View.GONE
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showError(errorView, loadingView, webView, "说明页加载失败，请稍后重试")
                }
            }
        }
        webView.loadUrl(normalizedUrl)
    }

    private fun showError(errorView: TextView, loadingView: View, webView: WebView, message: String) {
        loadingView.visibility = View.GONE
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorView.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
