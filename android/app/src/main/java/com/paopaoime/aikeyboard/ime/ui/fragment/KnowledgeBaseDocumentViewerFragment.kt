package com.paopaoime.aikeyboard.ime.ui.fragment

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.ime.auth.AuthApiClient
import com.paopaoime.aikeyboard.ime.auth.AuthSessionStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.KnowledgeBaseUiStore
import com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import kotlin.math.max
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument

class KnowledgeBaseDocumentViewerFragment : Fragment() {

    companion object {
        private const val ARG_DOCUMENT_ID = "document_id"
        private const val ARG_REMOTE_OBJECT_KEY = "remote_object_key"
        private const val ARG_REMOTE_TITLE = "remote_title"
        private const val ARG_REMOTE_MIME = "remote_mime"
        private const val ARG_HIDE_HEADER = "hide_header"
        private const val REMOTE_PREVIEW_DIR = "__remote_preview__"

        fun newInstance(
            documentId: String,
            remoteObjectKey: String = "",
            remoteTitle: String = "",
            remoteMime: String = "",
            hideHeader: Boolean = false,
        ): KnowledgeBaseDocumentViewerFragment {
            val fragment = KnowledgeBaseDocumentViewerFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_DOCUMENT_ID, documentId)
                putString(ARG_REMOTE_OBJECT_KEY, remoteObjectKey)
                putString(ARG_REMOTE_TITLE, remoteTitle)
                putString(ARG_REMOTE_MIME, remoteMime)
                putBoolean(ARG_HIDE_HEADER, hideHeader)
            }
            return fragment
        }
    }

    private var documentId: String = ""
    private var remoteObjectKey: String = ""
    private var remoteTitle: String = ""
    private var remoteMimeType: String = ""
    private var remoteDownloading = false
    private var remoteDownloadFailed = false
    private var remoteErrorMessage: String = ""
    private var hideHeader = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    private var showParsedText = false
    private var pdfRenderer: PdfRenderer? = null
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfPageAdapter? = null
    private var currentPdfPath: String? = null
    private val pdfExecutor = Executors.newSingleThreadExecutor()
    private val originalExecutor = Executors.newSingleThreadExecutor()
    private var cachedWordHtmlId: String? = null
    private var cachedWordHtml: String? = null
    private var cachedPresentationTextId: String? = null
    private var cachedPresentationText: String? = null
    private var excelWorkbook: Workbook? = null
    private var excelFilePath: String? = null
    private var excelGridAdapter: ExcelGridAdapter? = null
    private var excelRowHeaderAdapter: ExcelRowHeaderAdapter? = null
    private var excelColHeaderAdapter: ExcelColumnHeaderAdapter? = null
    private var excelLoadingPath: String? = null
    private var presentationLoadingPath: String? = null
    private var excelDecorationAddedGrid = false
    private var excelDecorationAddedRow = false
    private var excelDecorationAddedCol = false
    private val urlMarkdownCache = mutableMapOf<String, String>()
    private val urlLoading = mutableSetOf<String>()
    private val webClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentId = arguments?.getString(ARG_DOCUMENT_ID).orEmpty()
        remoteObjectKey = arguments?.getString(ARG_REMOTE_OBJECT_KEY).orEmpty()
        remoteTitle = arguments?.getString(ARG_REMOTE_TITLE).orEmpty()
        remoteMimeType = arguments?.getString(ARG_REMOTE_MIME).orEmpty()
        hideHeader = arguments?.getBoolean(ARG_HIDE_HEADER, false) == true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_kb_document_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btn_kb_doc_back)
        val tvTitle = view.findViewById<TextView>(R.id.tv_kb_doc_title_bar)
        val tvBody = view.findViewById<TextView>(R.id.tv_kb_doc_body)
        val btnToggle = view.findViewById<TextView>(R.id.btn_kb_doc_toggle)
        val rvPdf = view.findViewById<RecyclerView>(R.id.rv_kb_doc_pdf)
        val layoutExcel = view.findViewById<View>(R.id.layout_kb_doc_excel)
        val rvExcelRowHeader = view.findViewById<RecyclerView>(R.id.rv_kb_doc_excel_row_header)
        val rvExcelColHeader = view.findViewById<RecyclerView>(R.id.rv_kb_doc_excel_col_header)
        val rvExcelGrid = view.findViewById<RecyclerView>(R.id.rv_kb_doc_excel_grid)
        val webView = view.findViewById<WebView>(R.id.wv_kb_doc_web)
        val svText = view.findViewById<ScrollView>(R.id.sv_kb_doc_text)
        val layoutHeader = view.findViewById<View>(R.id.layout_kb_doc_header)

        if (hideHeader) {
            layoutHeader.visibility = View.GONE
        } else {
            val initialHeaderPaddingTop = layoutHeader.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(top = initialHeaderPaddingTop + bars.top)
                insets
            }
        }

        tvBody.setTextIsSelectable(true)
        tvBody.setLineSpacing(0f, 1.35f)

        webView.settings.defaultTextEncodingName = "utf-8"
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.isBlank() || isHttpUrl(url)) return false
                view?.context?.let { ctx ->
                    Toast.makeText(ctx, "该链接无法在应用内打开", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val target = url.orEmpty()
                if (target.isBlank() || isHttpUrl(target)) return false
                view?.context?.let { ctx ->
                    Toast.makeText(ctx, "该链接无法在应用内打开", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }

        rvPdf.layoutManager = LinearLayoutManager(requireContext())
        rvExcelRowHeader.isNestedScrollingEnabled = false
        rvExcelColHeader.isNestedScrollingEnabled = false
        rvExcelRowHeader.setOnTouchListener { _, _ -> true }
        rvExcelColHeader.setOnTouchListener { _, _ -> true }
        rvExcelGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    rvExcelRowHeader.scrollBy(0, dy)
                }
            }
        })

        render(tvTitle, tvBody, btnToggle, rvPdf, layoutExcel, rvExcelRowHeader, rvExcelColHeader, rvExcelGrid, webView, svText)
        startAutoRefreshIfNeeded(tvTitle, tvBody, btnToggle, rvPdf, layoutExcel, rvExcelRowHeader, rvExcelColHeader, rvExcelGrid, webView, svText)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        btnToggle.setOnClickListener {
            showParsedText = !showParsedText
            render(tvTitle, tvBody, btnToggle, rvPdf, layoutExcel, rvExcelRowHeader, rvExcelColHeader, rvExcelGrid, webView, svText)
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
        closePdf()
        closeExcel()
        pdfExecutor.shutdownNow()
        originalExecutor.shutdownNow()
        val remoteFile = getRemoteDocumentFile()
        if (remoteFile.exists()) {
            runCatching { remoteFile.delete() }
        }
    }

    private fun render(
        tvTitle: TextView,
        tvBody: TextView,
        btnToggle: TextView,
        rvPdf: RecyclerView,
        layoutExcel: View,
        rvExcelRowHeader: RecyclerView,
        rvExcelColHeader: RecyclerView,
        rvExcelGrid: RecyclerView,
        webView: WebView,
        svText: ScrollView
    ) {
        var doc = KnowledgeBaseUiStore.getDocument(documentId)
        if (doc == null && remoteObjectKey.isNotBlank()) {
            val remoteFile = getRemoteDocumentFile()
            if (remoteFile.exists()) {
                doc = KbDocumentUi(
                    id = documentId,
                    directoryId = REMOTE_PREVIEW_DIR,
                    fileName = remoteTitle.ifBlank { remoteFile.name },
                    mimeType = remoteMimeType.ifBlank { "application/octet-stream" },
                    updatedAtMillis = remoteFile.lastModified(),
                    parseStatus = 0,
                    parseProgress = 0,
                    parsedTextPath = null,
                    parseError = null,
                    vectorStatus = 0,
                    vectorError = null,
                    vectorUpdatedAtMillis = 0L
                )
            } else {
                if (!remoteDownloading && !remoteDownloadFailed) {
                    downloadRemoteFile()
                }
                tvTitle.text = remoteTitle.ifBlank { getString(R.string.kb_doc_not_found) }
                tvBody.text = if (remoteDownloadFailed) {
                    remoteErrorMessage.ifBlank { "云端文档加载失败" }
                } else {
                    "正在加载云端文档…"
                }
                setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
                btnToggle.visibility = View.GONE
                return
            }
        }
        if (doc == null) {
            tvTitle.text = getString(R.string.kb_doc_not_found)
            tvBody.text = getString(R.string.kb_doc_not_found_body)
            setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
            btnToggle.visibility = View.GONE
            return
        }

        tvTitle.text = doc.fileName
        val ext = doc.fileName.substringAfterLast('.', "").lowercase()
        val mime = doc.mimeType.lowercase()
        val sourceUrl = extractSourceUrl(doc)
        val isUrlDoc = !sourceUrl.isNullOrBlank()
        val isPdf = mime.contains("pdf") || ext == "pdf"
        val isWord = mime.contains("wordprocessingml") || mime.contains("msword") || ext == "doc" || ext == "docx"
        val isHtml = mime.contains("html") || ext == "html" || ext == "htm" || isUrlDoc
        val isExcel = mime.contains("spreadsheetml") || mime.contains("ms-excel") || ext == "xls" || ext == "xlsx"
        val isPpt = mime.contains("presentationml") || mime.contains("ms-powerpoint") || ext == "ppt" || ext == "pptx"
        btnToggle.visibility = if (isPdf || isWord || isExcel || isHtml) View.VISIBLE else View.GONE
        btnToggle.text = if (showParsedText) "原文" else "文本"

        val isPreviewSupported = isParsablePreview(doc.mimeType, doc.fileName)
        if (!isPreviewSupported) {
            tvBody.text = getString(R.string.kb_preview_unsupported, doc.mimeType)
            setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
            return
        }

        val showOriginal = !showParsedText && (isPdf || isWord || isHtml || isExcel)
        if (showOriginal) {
            val shown = when {
                isPdf -> renderPdfOriginal(doc, rvPdf, layoutExcel, webView, svText)
                isWord -> renderWordOriginal(doc, rvPdf, layoutExcel, webView, svText)
                isHtml -> renderHtmlOriginal(doc, rvPdf, layoutExcel, webView, svText)
                isExcel -> renderExcelOriginal(doc, tvBody, rvPdf, layoutExcel, rvExcelRowHeader, rvExcelColHeader, rvExcelGrid, webView, svText)
                else -> false
            }
            if (shown) return
        }

        if (isPpt) {
            val shown = renderPowerPointPreview(doc, tvBody, rvPdf, layoutExcel, webView, svText)
            if (shown) return
        }

        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
        when (doc.parseStatus) {
            1 -> {
                tvBody.text = getString(R.string.kb_waiting_parse)
            }
            2 -> {
                tvBody.text = getString(R.string.kb_parsing, doc.parseProgress.coerceIn(0, 100))
            }
            3 -> {
                val path = doc.parsedTextPath?.trim().orEmpty()
                if (path.isBlank()) {
                    tvBody.text = getString(R.string.kb_parsed_no_file)
                    return
                }
                val file = File(path)
                if (!file.exists()) {
                    tvBody.text = getString(R.string.kb_parsed_file_missing)
                    return
                }

                val (rawText, truncated) = readTextLimited(file, 300_000)
                val parsed = normalizeWebParsedText(rawText)
                val markdown = buildMarkdownContent(parsed, truncated)
                if (markdown.isBlank()) {
                    loadUrlMarkdown(doc, tvBody, webView, svText, rvPdf, layoutExcel)
                } else {
                    renderMarkdownToTextView(tvBody, markdown, webView, svText, rvPdf, layoutExcel)
                }
            }
            4 -> {
                val err = doc.parseError?.trim().orEmpty()
                tvBody.text = if (err.isBlank()) "解析失败。" else "解析失败：$err"
            }
            else -> {
                tvBody.text = buildPendingPreviewMessage(doc)
            }
        }
    }

    private fun startAutoRefreshIfNeeded(
        tvTitle: TextView,
        tvBody: TextView,
        btnToggle: TextView,
        rvPdf: RecyclerView,
        layoutExcel: View,
        rvExcelRowHeader: RecyclerView,
        rvExcelColHeader: RecyclerView,
        rvExcelGrid: RecyclerView,
        webView: WebView,
        svText: ScrollView
    ) {
        stopAutoRefresh()
        val doc = KnowledgeBaseUiStore.getDocument(documentId) ?: return
        if (doc.parseStatus != 1 && doc.parseStatus != 2) return

        autoRefreshRunnable = Runnable {
            if (!isAdded || view == null) return@Runnable
            render(tvTitle, tvBody, btnToggle, rvPdf, layoutExcel, rvExcelRowHeader, rvExcelColHeader, rvExcelGrid, webView, svText)
            val latest = KnowledgeBaseUiStore.getDocument(documentId) ?: return@Runnable
            if (latest.parseStatus == 1 || latest.parseStatus == 2) {
                handler.postDelayed(autoRefreshRunnable!!, 700L)
            } else {
                stopAutoRefresh()
            }
        }
        handler.postDelayed(autoRefreshRunnable!!, 700L)
    }

    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
        autoRefreshRunnable = null
    }

    private fun renderPdfOriginal(doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi, rvPdf: RecyclerView, layoutExcel: View, webView: WebView, svText: ScrollView): Boolean {
        val file = getDocumentFile(requireContext(), doc)
        if (!file.exists()) return false
        if (currentPdfPath != file.absolutePath) {
            closePdf()
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            pdfDescriptor = fd
            pdfRenderer = renderer
            val paddingPx = (resources.displayMetrics.density * 24f).toInt()
            val targetWidth = (resources.displayMetrics.widthPixels - paddingPx).coerceAtLeast(1)
            val adapter = PdfPageAdapter(renderer, targetWidth)
            rvPdf.adapter = adapter
            pdfAdapter = adapter
            currentPdfPath = file.absolutePath
        }
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = true, showExcel = false, showWeb = false)
        return true
    }

    private fun renderPowerPointPreview(
        doc: KbDocumentUi,
        tvBody: TextView,
        rvPdf: RecyclerView,
        layoutExcel: View,
        webView: WebView,
        svText: ScrollView,
    ): Boolean {
        val file = getDocumentFile(requireContext(), doc)
        if (!file.exists()) return false
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
        val cacheKey = file.absolutePath
        val cached = cachedPresentationText
        if (cacheKey == cachedPresentationTextId && cached != null) {
            tvBody.text = cached.ifBlank { "未提取到可预览内容。" }
            return true
        }
        if (presentationLoadingPath == cacheKey) {
            tvBody.text = "加载中…"
            return true
        }
        presentationLoadingPath = cacheKey
        tvBody.text = "加载中…"
        originalExecutor.execute {
            val text = extractPresentationPreviewText(file)
            tvBody.post {
                presentationLoadingPath = null
                if (!isAdded) return@post
                cachedPresentationTextId = cacheKey
                cachedPresentationText = text
                tvBody.text = text.ifBlank { "未提取到可预览内容。" }
            }
        }
        return true
    }

    private fun renderWordOriginal(doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi, rvPdf: RecyclerView, layoutExcel: View, webView: WebView, svText: ScrollView): Boolean {
        val file = getDocumentFile(requireContext(), doc)
        if (!file.exists()) return false
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = false, showExcel = false, showWeb = true)
        val cacheKey = file.absolutePath
        val cached = cachedWordHtml
        if (cacheKey == cachedWordHtmlId && cached != null) {
            webView.loadDataWithBaseURL(null, cached, "text/html", "utf-8", null)
            return true
        }
        webView.loadDataWithBaseURL(null, "<html><body style=\"padding:16px;color:#666;\">加载中…</body></html>", "text/html", "utf-8", null)
        originalExecutor.execute {
            val html = buildDocxHtml(file)
            cachedWordHtmlId = cacheKey
            cachedWordHtml = html
            webView.post {
                if (!isAdded) return@post
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }
        return true
    }

    private fun renderHtmlOriginal(doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi, rvPdf: RecyclerView, layoutExcel: View, webView: WebView, svText: ScrollView): Boolean {
        val file = getDocumentFile(requireContext(), doc)
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = false, showExcel = false, showWeb = true)
        val html = if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty() else ""
        if (html.isNotBlank()) {
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            return true
        }
        val sourceUrl = extractSourceUrl(doc)
        if (!sourceUrl.isNullOrBlank()) {
            val normalizedUrl = ensureUrlScheme(sourceUrl)
            if (!isHttpUrl(normalizedUrl)) return false
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.loadUrl(normalizedUrl)
            return true
        }
        return false
    }

    private fun renderExcelOriginal(
        doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi,
        tvBody: TextView,
        rvPdf: RecyclerView,
        layoutExcel: View,
        rvExcelRowHeader: RecyclerView,
        rvExcelColHeader: RecyclerView,
        rvExcelGrid: RecyclerView,
        webView: WebView,
        svText: ScrollView
    ): Boolean {
        val file = getDocumentFile(requireContext(), doc)
        if (!file.exists()) return false
        if (excelFilePath == file.absolutePath && excelGridAdapter != null) {
            setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = false, showExcel = true, showWeb = false)
            return true
        }
        if (excelLoadingPath == file.absolutePath) {
            setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
            tvBody.text = "加载中…"
            return true
        }
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
        tvBody.text = "加载中…"
        excelLoadingPath = file.absolutePath
        originalExecutor.execute {
            val data = buildExcelGridData(file)
            rvExcelGrid.post {
                excelLoadingPath = null
                if (!isAdded) return@post
                if (data == null) {
                    setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = true, showPdf = false, showExcel = false, showWeb = false)
                    tvBody.text = "解析失败。"
                    return@post
                }
                closeExcel()
                excelWorkbook = data.workbook
                excelFilePath = file.absolutePath
                val density = resources.displayMetrics.density
                val cellWidth = (density * 96f).toInt()
                val cellHeight = (density * 36f).toInt()
                val rowHeaderWidth = (density * 48f).toInt()

                excelRowHeaderAdapter = ExcelRowHeaderAdapter(data.rowCount, rowHeaderWidth, cellHeight)
                excelColHeaderAdapter = ExcelColumnHeaderAdapter(data.colCount, cellWidth, cellHeight)
                excelGridAdapter = ExcelGridAdapter(data.workbook, data.sheetIndex, data.rowCount, data.colCount, cellWidth, cellHeight, DataFormatter())

                rvExcelRowHeader.layoutManager = LinearLayoutManager(requireContext())
                rvExcelRowHeader.adapter = excelRowHeaderAdapter
                rvExcelColHeader.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
                rvExcelColHeader.adapter = excelColHeaderAdapter
                val gridLayoutManager = GridLayoutManager(requireContext(), data.colCount)
                rvExcelGrid.layoutManager = gridLayoutManager
                rvExcelGrid.adapter = excelGridAdapter

                if (!excelDecorationAddedRow) {
                    rvExcelRowHeader.addItemDecoration(ExcelGridDecoration())
                    excelDecorationAddedRow = true
                }
                if (!excelDecorationAddedCol) {
                    rvExcelColHeader.addItemDecoration(ExcelGridDecoration())
                    excelDecorationAddedCol = true
                }
                if (!excelDecorationAddedGrid) {
                    rvExcelGrid.addItemDecoration(ExcelGridDecoration())
                    excelDecorationAddedGrid = true
                }

                rvExcelRowHeader.scrollToPosition(0)
                rvExcelColHeader.scrollToPosition(0)
                rvExcelGrid.scrollToPosition(0)
                setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = false, showExcel = true, showWeb = false)
            }
        }
        return true
    }

    private fun buildDocxHtml(file: File): String {
        return runCatching {
            val sb = StringBuilder()
            sb.append("<html><head><meta charset=\"utf-8\"/>")
            sb.append("<style>body{padding:16px;font-size:14px;color:#333;}p{margin:0 0 12px 0;}</style>")
            sb.append("</head><body>")
            FileInputStream(file).use { input ->
                XWPFDocument(input).use { document ->
                    document.paragraphs.forEach { para ->
                        val align = when (para.alignment) {
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER -> "center"
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT -> "right"
                            org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH -> "justify"
                            else -> "left"
                        }
                        sb.append("<p style=\"text-align:").append(align).append(";\">")
                        if (para.runs.isEmpty()) {
                            val text = escapeHtml(para.text.orEmpty())
                            sb.append(text.ifBlank { "&nbsp;" })
                        } else {
                            para.runs.forEach { run ->
                                val text = escapeHtml(run.text().orEmpty())
                                if (text.isBlank()) return@forEach
                                val style = StringBuilder()
                                if (run.isBold) style.append("font-weight:bold;")
                                if (run.isItalic) style.append("font-style:italic;")
                                if (run.underline != UnderlinePatterns.NONE) style.append("text-decoration:underline;")
                                val fontSize = run.fontSize
                                if (fontSize > 0) style.append("font-size:").append(fontSize).append("pt;")
                                if (style.isNotEmpty()) {
                                    sb.append("<span style=\"").append(style).append("\">").append(text).append("</span>")
                                } else {
                                    sb.append(text)
                                }
                            }
                        }
                        sb.append("</p>")
                    }
                }
            }
            sb.append("</body></html>")
            sb.toString()
        }.getOrElse {
            "<html><body style=\"padding:16px;color:#666;\">解析失败</body></html>"
        }
    }

    private fun extractPresentationPreviewText(file: File): String {
        return when (file.extension.lowercase()) {
            "pptx" -> extractPptxPreviewText(file)
            "ppt" -> extractPptPreviewText(file)
            else -> ""
        }
    }

    private fun extractPptxPreviewText(file: File): String {
        return runCatching {
            FileInputStream(file).use { input ->
                XMLSlideShow(input).use { show ->
                    val slides = show.slides
                    if (slides.isEmpty()) return ""
                    val out = StringBuilder()
                    slides.forEachIndexed { index, slide ->
                        if (out.isNotEmpty()) out.append("\n\n")
                        out.append("—— 第${index + 1}页 ——\n\n")
                        slide.shapes.forEach { shape ->
                            if (shape is XSLFTextShape) {
                                var paragraphHasText = false
                                shape.textParagraphs.forEach { paragraph ->
                                    val line = paragraph.text?.trim().orEmpty()
                                    if (line.isNotEmpty()) {
                                        paragraphHasText = true
                                        out.append(line).append('\n')
                                    }
                                }
                                if (paragraphHasText) out.append('\n')
                            }
                        }
                    }
                    out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
                }
            }
        }.getOrDefault("")
    }

    private fun extractPptPreviewText(file: File): String {
        return runCatching {
            FileInputStream(file).use { input ->
                HSLFSlideShow(input).use { show ->
                    val slides = show.slides
                    if (slides.isEmpty()) return ""
                    val out = StringBuilder()
                    slides.forEachIndexed { index, slide ->
                        if (out.isNotEmpty()) out.append("\n\n")
                        out.append("—— 第${index + 1}页 ——\n\n")
                        slide.shapes.forEach { shape ->
                            if (shape is HSLFTextShape) {
                                val text = shape.text.orEmpty()
                                    .replace("\r\n", "\n")
                                    .replace('\r', '\n')
                                    .trim()
                                if (text.isNotBlank()) {
                                    out.append(text).append("\n\n")
                                }
                            }
                        }
                    }
                    out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
                }
            }
        }.getOrDefault("")
    }

    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            val rem = i % 26
            sb.append(('A'.code + rem).toChar())
            i = i / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>")
    }

    private fun setContentVisibility(
        svText: ScrollView,
        rvPdf: RecyclerView,
        layoutExcel: View,
        webView: WebView,
        showText: Boolean,
        showPdf: Boolean,
        showExcel: Boolean,
        showWeb: Boolean
    ) {
        svText.visibility = if (showText) View.VISIBLE else View.GONE
        rvPdf.visibility = if (showPdf) View.VISIBLE else View.GONE
        layoutExcel.visibility = if (showExcel) View.VISIBLE else View.GONE
        webView.visibility = if (showWeb) View.VISIBLE else View.GONE
    }

    private fun closePdf() {
        pdfAdapter = null
        currentPdfPath = null
        pdfRenderer?.close()
        pdfRenderer = null
        pdfDescriptor?.close()
        pdfDescriptor = null
    }

    private fun closeExcel() {
        excelGridAdapter = null
        excelRowHeaderAdapter = null
        excelColHeaderAdapter = null
        excelFilePath = null
        excelWorkbook?.close()
        excelWorkbook = null
    }

    private fun getDocumentFile(context: android.content.Context, doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi): File {
        if (doc.directoryId == REMOTE_PREVIEW_DIR) {
            return getRemoteDocumentFile()
        }
        if (doc.directoryId == "__favorite_preview__") {
            return KnowledgeBaseUiStore.resolveTransientDocumentFile(doc.id) ?: File("")
        }
        val root = File(context.filesDir, "knowledge_base")
        return File(File(root, doc.directoryId), doc.fileName)
    }

    private fun getRemoteDocumentFile(): File {
        val safeName = remoteTitle
            .ifBlank { documentId }
            .replace(Regex("[\\\\/\\u0000]"), "_")
        val root = File(requireContext().filesDir, "knowledge_base")
        val dir = File(root, REMOTE_PREVIEW_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${documentId}_$safeName")
    }

    private fun downloadRemoteFile() {
        if (!isAdded) return
        if (!AuthSessionStore.isLoggedIn(requireContext())) {
            remoteDownloadFailed = true
            remoteErrorMessage = "请先登录后查看云端文档"
            return
        }
        val accessToken = AuthSessionStore.accessToken(requireContext())
        val refreshToken = AuthSessionStore.refreshToken(requireContext())
        if (accessToken.isBlank()) {
            remoteDownloadFailed = true
            remoteErrorMessage = "登录态无效，请重新登录"
            return
        }
        remoteDownloading = true
        remoteDownloadFailed = false
        remoteErrorMessage = ""
        lifecycleScope.launch {
            val signResult = withContext(Dispatchers.IO) {
                AuthApiClient.createOssDownloadSign(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    objectKey = remoteObjectKey
                )
            }
            if (!signResult.newAccessToken.isNullOrBlank()) {
                AuthSessionStore.updateTokens(requireContext(), signResult.newAccessToken, signResult.newRefreshToken)
            }
            if (signResult.authInvalid) {
                AuthSessionStore.handleOemAuthInvalid(requireContext(), "知识库")
                remoteDownloading = false
                remoteDownloadFailed = true
                remoteErrorMessage = "登录态已失效"
                return@launch
            }
            if (!signResult.success || signResult.downloadUrl.isBlank()) {
                remoteDownloading = false
                remoteDownloadFailed = true
                remoteErrorMessage = signResult.message.ifBlank { "获取下载链接失败" }
                renderFromState()
                return@launch
            }
            val target = getRemoteDocumentFile()
            val downloadResult = withContext(Dispatchers.IO) {
                AuthApiClient.downloadFileByUrl(signResult.downloadUrl, target)
            }
            remoteDownloading = false
            if (!downloadResult.success) {
                remoteDownloadFailed = true
                remoteErrorMessage = downloadResult.message.ifBlank { "下载失败" }
            } else {
                remoteDownloadFailed = false
                remoteErrorMessage = ""
            }
            renderFromState()
        }
    }

    private fun renderFromState() {
        val root = view ?: return
        render(
            root.findViewById(R.id.tv_kb_doc_title_bar),
            root.findViewById(R.id.tv_kb_doc_body),
            root.findViewById(R.id.btn_kb_doc_toggle),
            root.findViewById(R.id.rv_kb_doc_pdf),
            root.findViewById(R.id.layout_kb_doc_excel),
            root.findViewById(R.id.rv_kb_doc_excel_row_header),
            root.findViewById(R.id.rv_kb_doc_excel_col_header),
            root.findViewById(R.id.rv_kb_doc_excel_grid),
            root.findViewById(R.id.wv_kb_doc_web),
            root.findViewById(R.id.sv_kb_doc_text)
        )
    }

    private fun readTextLimited(file: File, maxChars: Int): Pair<String, Boolean> {
        val sb = StringBuilder()
        var truncated = false
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (sb.length >= maxChars) {
                    truncated = true
                    break
                }
                val remaining = maxChars - sb.length
                if (line.length + 1 <= remaining) {
                    sb.append(line).append('\n')
                } else {
                    sb.append(line.take(remaining.coerceAtLeast(0)))
                    truncated = true
                    break
                }
            }
        }
        return sb.toString() to truncated
    }

    private fun buildMarkdownContent(parsed: WebParsedText, truncated: Boolean): String {
        val base = formatParsedTextToMarkdown(parsed.content).trim()
        if (base.isBlank()) return ""
        val sb = StringBuilder()
        sb.append(base)
        if (truncated) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("（内容过长，已截断）")
        }
        if (!parsed.url.isNullOrBlank() && !parsed.hasLinkLabel) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("[查看原文](").append(parsed.url).append(")")
        }
        return sb.toString()
    }

    private fun formatParsedTextToMarkdown(rawText: String): String {
        val headerRegex = Regex("^——[\\s]*(第\\d+页|Sheet\\s*\\d+)\\s*——")
        val sb = StringBuilder()
        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")
        normalized.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (headerRegex.matches(trimmed)) {
                val title = trimmed.replace("——", "").trim()
                sb.append("## ").append(title)
            } else {
                sb.append(line)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private data class WebParsedText(
        val content: String,
        val url: String?,
        val hasLinkLabel: Boolean
    )

    private fun normalizeWebParsedText(rawText: String): WebParsedText {
        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split('\n')
        val matcher = android.util.Patterns.WEB_URL.matcher(normalized)
        val url = if (matcher.find()) matcher.group().orEmpty().trim().ifBlank { null } else null
        val hasLinkLabel = normalized.contains("查看原文") || normalized.contains("原文链接")
        val urlLineIndex = if (url == null) -1 else lines.indexOfFirst { it.contains(url) }
        val trimmedLines = if (urlLineIndex in 0..2) {
            lines.drop(urlLineIndex + 1)
        } else {
            lines
        }.dropWhile { it.isBlank() }
        val content = trimmedLines.joinToString("\n")
        return WebParsedText(content, url, hasLinkLabel)
    }

    private fun renderMarkdownToTextView(
        tvBody: TextView,
        markdown: String,
        webView: WebView,
        svText: ScrollView,
        rvPdf: RecyclerView,
        layoutExcel: View
    ) {
        val html = markdownToHtml(markdown)
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val spannable = SpannableString(spanned)
        applyInAppUrlSpans(spannable, webView, svText, rvPdf, layoutExcel)
        tvBody.text = spannable
        tvBody.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun extractSourceUrl(doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi): String? {
        val path = doc.parsedTextPath?.trim().orEmpty()
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val (rawText, _) = readTextLimited(file, 40_000)
        return normalizeWebParsedText(rawText).url
    }

    private fun loadUrlMarkdown(
        doc: com.paopaoime.aikeyboard.ime.ui.knowledgebase.model.KbDocumentUi,
        tvBody: TextView,
        webView: WebView,
        svText: ScrollView,
        rvPdf: RecyclerView,
        layoutExcel: View
    ) {
        val sourceUrl = extractSourceUrl(doc) ?: run {
            tvBody.text = "未提取到正文。"
            return
        }
        val normalizedUrl = ensureUrlScheme(sourceUrl)
        if (!isHttpUrl(normalizedUrl)) {
            tvBody.text = "该链接无法在应用内打开。"
            return
        }
        val cached = urlMarkdownCache[normalizedUrl]
        if (!cached.isNullOrBlank()) {
            renderMarkdownToTextView(tvBody, cached, webView, svText, rvPdf, layoutExcel)
            return
        }
        tvBody.text = "加载中…"
        if (urlLoading.contains(normalizedUrl)) return
        urlLoading.add(normalizedUrl)
        originalExecutor.execute {
            val markdown = fetchUrlMarkdown(normalizedUrl)
            urlMarkdownCache[normalizedUrl] = markdown
            urlLoading.remove(normalizedUrl)
            tvBody.post {
                if (!isAdded) return@post
                if (markdown.isBlank()) {
                    tvBody.text = "未提取到正文。"
                } else {
                    renderMarkdownToTextView(tvBody, markdown, webView, svText, rvPdf, layoutExcel)
                }
            }
        }
    }

    private fun fetchUrlMarkdown(url: String): String {
        return runCatching {
            val html = fetchHtml(url)
            if (html.isBlank()) return ""
            val article = Readability4J(url, html).parse()
            var title = article.title?.trim().orEmpty()
            var text = article.textContent?.trim().orEmpty()
            if (text.isBlank()) {
                text = extractPlainText(html)
            }
            if (title.isBlank()) {
                title = extractTitleFromHtml(html)
            }
            buildMarkdownOutput(title, url, text)
        }.getOrElse { "" }
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .build()
        webClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            return response.body?.string().orEmpty()
        }
    }

    private fun buildMarkdownOutput(title: String, url: String, text: String): String {
        val normalizedText = text.trim()
        val sb = StringBuilder()
        if (title.isNotBlank()) {
            sb.append("# ").append(title.trim())
            if (normalizedText.isNotBlank()) sb.append("\n\n")
        }
        if (normalizedText.isNotBlank()) {
            sb.append(normalizedText)
        }
        if (sb.isNotEmpty()) sb.append("\n\n")
        sb.append("[查看原文](").append(url).append(")")
        return sb.toString()
    }

    private fun extractTitleFromHtml(html: String): String {
        val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html) ?: return ""
        return HtmlCompat.fromHtml(match.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
    }

    private fun extractPlainText(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        val text = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun markdownToHtml(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split('\n')
        val sb = StringBuilder()
        var inList = false
        var inCode = false
        for (line in lines) {
            val trimmedLine = line.trimEnd()
            val trimmed = trimmedLine.trim()
            if (trimmed.startsWith("```")) {
                if (inList) {
                    sb.append("</ul>")
                    inList = false
                }
                if (inCode) {
                    sb.append("</code></pre>")
                    inCode = false
                } else {
                    sb.append("<pre><code>")
                    inCode = true
                }
                continue
            }
            if (inCode) {
                sb.append(escapeHtml(trimmedLine)).append('\n')
                continue
            }
            if (trimmed.isBlank()) {
                if (inList) {
                    sb.append("</ul>")
                    inList = false
                }
                sb.append("<br/>")
                continue
            }
            val heading = when {
                trimmed.startsWith("###### ") -> 6
                trimmed.startsWith("##### ") -> 5
                trimmed.startsWith("#### ") -> 4
                trimmed.startsWith("### ") -> 3
                trimmed.startsWith("## ") -> 2
                trimmed.startsWith("# ") -> 1
                else -> 0
            }
            if (heading > 0) {
                if (inList) {
                    sb.append("</ul>")
                    inList = false
                }
                val content = trimmed.drop(heading + 1)
                sb.append("<h").append(heading).append(">")
                    .append(applyInlineMarkdown(content))
                    .append("</h").append(heading).append(">")
                continue
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    sb.append("<ul>")
                    inList = true
                }
                val content = trimmed.drop(2)
                sb.append("<li>").append(applyInlineMarkdown(content)).append("</li>")
                continue
            }
            if (inList) {
                sb.append("</ul>")
                inList = false
            }
            sb.append("<p>").append(applyInlineMarkdown(trimmedLine)).append("</p>")
        }
        if (inList) sb.append("</ul>")
        if (inCode) sb.append("</code></pre>")
        return "<html><head><meta charset=\"utf-8\"/></head><body style=\"padding:16px;color:#333;line-height:1.6;\">${sb}</body></html>"
    }

    private fun applyInlineMarkdown(text: String): String {
        var result = escapeHtml(text)
        result = result.replace(Regex("\\[(.+?)]\\(([^)]+)\\)")) {
            val label = it.groupValues[1]
            val url = ensureUrlScheme(it.groupValues[2])
            "<a href=\"$url\">$label</a>"
        }
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        result = result.replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
        return result
    }

    private fun applyInAppUrlSpans(
        spannable: SpannableString,
        webView: WebView,
        svText: ScrollView,
        rvPdf: RecyclerView,
        layoutExcel: View
    ) {
        val spans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val flags = spannable.getSpanFlags(span)
            val url = span.url
            spannable.removeSpan(span)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrlInWebView(url, webView, svText, rvPdf, layoutExcel)
                }
            }, start, end, flags)
        }
    }

    private fun openUrlInWebView(
        url: String,
        webView: WebView,
        svText: ScrollView,
        rvPdf: RecyclerView,
        layoutExcel: View
    ) {
        val normalizedUrl = ensureUrlScheme(url)
        if (!isHttpUrl(normalizedUrl)) {
            Toast.makeText(webView.context, "该链接无法在应用内打开", Toast.LENGTH_SHORT).show()
            return
        }
        setContentVisibility(svText, rvPdf, layoutExcel, webView, showText = false, showPdf = false, showExcel = false, showWeb = true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl(normalizedUrl)
    }

    private fun ensureUrlScheme(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.contains("://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun isHttpUrl(url: String): Boolean {
        val normalized = ensureUrlScheme(url)
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    private fun isParsablePreview(mimeType: String, fileName: String): Boolean {
        val mime = mimeType.lowercase()
        if (mime.startsWith("text/")) return true
        if (mime == "application/pdf" || mime.contains("pdf")) return true
        if (mime.contains("wordprocessingml") ||
            mime.contains("spreadsheetml") ||
            mime.contains("presentationml") ||
            mime.contains("msword") ||
            mime.contains("ms-excel") ||
            mime.contains("ms-powerpoint") ||
            mime.contains("rtf") ||
            mime.contains("html")
        ) {
            return true
        }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "htm", "txt", "csv", "rtf")
    }

    private fun buildPendingPreviewMessage(doc: KbDocumentUi): String {
        if (doc.directoryId != "__favorite_preview__") {
            return "未解析。"
        }
        val mime = doc.mimeType.lowercase()
        val ext = doc.fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.contains("presentationml") ||
                mime.contains("ms-powerpoint") ||
                ext == "ppt" ||
                ext == "pptx" -> "当前文件暂不支持预览，但仍可正常添加和发送。"
            mime.contains("msword") ||
                ext == "doc" -> "当前文件暂不支持该格式预览，但仍可正常添加和发送。"
            else -> "当前文件尚未生成可展示的预览，但仍可正常添加和发送。"
        }
    }

    private inner class PdfPageAdapter(
        private val renderer: PdfRenderer,
        private val targetWidth: Int
    ) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {
        private val bitmapCache = object : android.util.LruCache<Int, Bitmap>(cacheSize()) {
            override fun sizeOf(key: Int, value: Bitmap): Int = max(1, value.byteCount / 1024)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val image = ImageView(parent.context)
            image.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            image.adjustViewBounds = true
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setBackgroundColor(Color.WHITE)
            return PageViewHolder(image)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = renderer.pageCount

        inner class PageViewHolder(private val image: ImageView) : RecyclerView.ViewHolder(image) {
            fun bind(pageIndex: Int) {
                image.tag = pageIndex
                val cached = bitmapCache.get(pageIndex)
                if (cached != null) {
                    image.setImageBitmap(cached)
                    return
                }
                image.setImageDrawable(null)
                pdfExecutor.execute {
                    val bitmap = renderPdfPage(renderer, pageIndex, targetWidth)
                    if (bitmap != null) {
                        bitmapCache.put(pageIndex, bitmap)
                    }
                    image.post {
                        if (image.tag == pageIndex && bitmap != null) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }

        private fun cacheSize(): Int {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return max(1024, maxMemory / 24)
        }
    }

    private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int, targetWidth: Int): Bitmap? {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        return runCatching {
            renderer.openPage(pageIndex).use { page ->
                val width = page.width.coerceAtLeast(1)
                val height = page.height.coerceAtLeast(1)
                val targetHeight = max(1, (height.toFloat() / width.toFloat() * targetWidth).toInt())
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull()
    }

    private data class ExcelGridData(
        val workbook: Workbook,
        val sheetIndex: Int,
        val rowCount: Int,
        val colCount: Int
    )

    private fun buildExcelGridData(file: File): ExcelGridData? {
        return runCatching {
            FileInputStream(file).use { input ->
                val workbook = WorkbookFactory.create(input)
                if (workbook.numberOfSheets <= 0) {
                    workbook.close()
                    return@runCatching null
                }
                val sheetIndex = 0
                val sheet = workbook.getSheetAt(sheetIndex)
                val maxRows = 2000
                val maxCols = 80
                val lastRow = sheet.lastRowNum.coerceAtLeast(0)
                val rowCount = (lastRow + 1).coerceAtMost(maxRows)
                var colCount = 0
                for (rowIndex in 0 until rowCount) {
                    val row = sheet.getRow(rowIndex)
                    val lastCell = row?.lastCellNum?.toInt() ?: 0
                    if (lastCell > colCount) colCount = lastCell
                    if (colCount >= maxCols) {
                        colCount = maxCols
                        break
                    }
                }
                if (colCount <= 0) colCount = 1
                ExcelGridData(workbook, sheetIndex, rowCount, colCount)
            }
        }.getOrNull()
    }

    private inner class ExcelColumnHeaderAdapter(
        private val colCount: Int,
        private val cellWidth: Int,
        private val cellHeight: Int
    ) : RecyclerView.Adapter<ExcelColumnHeaderAdapter.ColHeaderViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColHeaderViewHolder {
            val tv = TextView(parent.context)
            tv.layoutParams = RecyclerView.LayoutParams(cellWidth, cellHeight)
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#555555"))
            tv.gravity = android.view.Gravity.CENTER
            tv.setBackgroundColor(Color.parseColor("#F5F5F5"))
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            return ColHeaderViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ColHeaderViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = colCount

        inner class ColHeaderViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(col: Int) {
                tv.text = columnName(col)
            }
        }
    }

    private inner class ExcelRowHeaderAdapter(
        private val rowCount: Int,
        private val rowHeaderWidth: Int,
        private val cellHeight: Int
    ) : RecyclerView.Adapter<ExcelRowHeaderAdapter.RowHeaderViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHeaderViewHolder {
            val tv = TextView(parent.context)
            tv.layoutParams = RecyclerView.LayoutParams(rowHeaderWidth, cellHeight)
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#555555"))
            tv.gravity = android.view.Gravity.CENTER
            tv.setBackgroundColor(Color.parseColor("#F5F5F5"))
            return RowHeaderViewHolder(tv)
        }

        override fun onBindViewHolder(holder: RowHeaderViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = rowCount

        inner class RowHeaderViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(row: Int) {
                tv.text = (row + 1).toString()
            }
        }
    }

    private inner class ExcelGridAdapter(
        private val workbook: Workbook,
        private val sheetIndex: Int,
        private val rowCount: Int,
        private val colCount: Int,
        private val cellWidth: Int,
        private val cellHeight: Int,
        private val formatter: DataFormatter
    ) : RecyclerView.Adapter<ExcelGridAdapter.CellViewHolder>() {
        private val sheet = workbook.getSheetAt(sheetIndex)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
            val tv = TextView(parent.context)
            tv.layoutParams = RecyclerView.LayoutParams(cellWidth, cellHeight)
            val pad = (parent.context.resources.displayMetrics.density * 6f).toInt()
            tv.setPadding(pad, 0, pad, 0)
            tv.textSize = 12f
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            return CellViewHolder(tv)
        }

        override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
            val row = position / colCount
            val col = position % colCount
            holder.bind(row, col)
        }

        override fun getItemCount(): Int = rowCount * colCount

        inner class CellViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(row: Int, col: Int) {
                val sheetRow = sheet.getRow(row)
                val cell = sheetRow?.getCell(col)
                val text = if (cell == null) "" else formatter.formatCellValue(cell)
                tv.text = text
                tv.setBackgroundColor(Color.WHITE)
                tv.setTypeface(tv.typeface, Typeface.NORMAL)
            }
        }
    }

    private inner class ExcelGridDecoration : RecyclerView.ItemDecoration() {
        private val paint = android.graphics.Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = resources.displayMetrics.density
        }

        override fun onDrawOver(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i)
                val left = child.left.toFloat()
                val right = child.right.toFloat()
                val top = child.top.toFloat()
                val bottom = child.bottom.toFloat()
                c.drawLine(left, bottom, right, bottom, paint)
                c.drawLine(right, top, right, bottom, paint)
            }
        }
    }
}
