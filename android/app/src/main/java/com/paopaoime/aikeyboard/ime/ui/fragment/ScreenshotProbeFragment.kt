package com.paopaoime.aikeyboard.ime.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R
import com.paopaoime.aikeyboard.sdk.core.OemAddons
import com.paopaoime.aikeyboard.sdk.core.OemAutomationRuntimeSnapshot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotProbeFragment : Fragment() {
    companion object {
        private const val TAG = "ScreenshotProbe"
    }

    private var statusView: TextView? = null
    private var pathView: TextView? = null
    private var previewView: ImageView? = null
    private var captureButton: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_screenshot_probe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)

        val header = view.findViewById<View>(R.id.layout_screenshot_probe_header)
        val initialHeaderPaddingTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }

        statusView = view.findViewById(R.id.tv_screenshot_probe_status)
        pathView = view.findViewById(R.id.tv_screenshot_probe_path)
        previewView = view.findViewById(R.id.iv_screenshot_probe_preview)
        captureButton = view.findViewById(R.id.btn_screenshot_probe_capture)

        view.findViewById<View>(R.id.btn_screenshot_probe_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        captureButton?.setOnClickListener {
            Log.d(TAG, "capture button clicked")
            captureAndOpenPreview()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)
        updateStatus()
    }

    override fun onDestroyView() {
        statusView = null
        pathView = null
        previewView = null
        captureButton = null
        super.onDestroyView()
    }

    private fun updateStatus(extraMessage: String? = null) {
        val context = context ?: return
        val shizukuStatus = OemAddons.automation?.getAutomationRuntimeSnapshot(context)
            ?: OemAutomationRuntimeSnapshot(detailMessage = "自动化引擎功能包未加载")
        Log.d(
            TAG,
            "updateStatus ready=${shizukuStatus.ready} extra=${extraMessage.orEmpty()}"
        )
        statusView?.text = buildString {
            append("状态：自动化服务")
            append(if (shizukuStatus.ready) "已就绪" else "未就绪")
            if (!extraMessage.isNullOrBlank()) {
                append("；")
                append(extraMessage)
            }
        }
    }

    private fun captureAndOpenPreview() {
        val context = context ?: return
        val status = OemAddons.automation?.getAutomationRuntimeSnapshot(context)
            ?: OemAutomationRuntimeSnapshot(detailMessage = "自动化引擎功能包未加载")
        Log.d(TAG, "captureAndOpenPreview ready=${status.ready} detail=${status.detailMessage}")
        if (!status.ready) {
            Toast.makeText(context, "自动化服务尚未就绪，无法截屏", Toast.LENGTH_SHORT).show()
            updateStatus("请先完成输入法自动化引擎启动与授权")
            return
        }
        setButtonsEnabled(false)
        pathView?.text = "正在执行 screencap..."
        previewView?.isVisible = false
        updateStatus("正在执行 screencap")
        Toast.makeText(context, "开始执行截屏验证", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "capture coroutine started")
                val result = withContext(Dispatchers.IO) { saveCurrentScreenshot(context) }
                if (!isAdded) return@launch
                result.onSuccess { file ->
                    Log.d(TAG, "capture succeeded path=${file.absolutePath} size=${file.length()}")
                    pathView?.text = file.absolutePath
                    previewView?.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    previewView?.isVisible = true
                    updateStatus("截图成功，已尝试打开预览")
                    openImagePreview(file)
                }.onFailure { error ->
                    Log.w(TAG, "capture failed", error)
                    previewView?.isVisible = false
                    pathView?.text = "截屏失败：${error.message ?: error.javaClass.simpleName}"
                    updateStatus(error.message ?: error.javaClass.simpleName)
                    Toast.makeText(requireContext(), "截屏失败：${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "capture coroutine crashed", throwable)
                if (isAdded) {
                    pathView?.text = "截屏协程异常：${throwable.message ?: throwable.javaClass.simpleName}"
                    updateStatus(throwable.message ?: throwable.javaClass.simpleName)
                    Toast.makeText(requireContext(), "截屏异常：${throwable.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                setButtonsEnabled(true)
            }
        }
    }

    private fun saveCurrentScreenshot(context: android.content.Context): Result<File> {
        return runCatching {
            Log.d(TAG, "saveCurrentScreenshot started")
            val automation = OemAddons.automation ?: error("自动化引擎功能包未加载")
            val dataUrl = automation.captureAutomationScreenshotBase64(context)
                ?: error(automation.getLastAutomationScreenshotError().ifBlank { "截图失败" })
            val base64 = dataUrl.substringAfter(',', dataUrl)
            val pngBytes = Base64.decode(base64, Base64.DEFAULT)
            val targetDir = File(context.filesDir, "screenshot-probe").apply {
                if (!exists()) mkdirs()
            }
            val file = File(targetDir, "probe_${System.currentTimeMillis()}.png")
            file.writeBytes(pngBytes)
            Log.d(TAG, "saveCurrentScreenshot wrote file=${file.absolutePath} bytes=${pngBytes.size}")
            file
        }
    }

    private fun openImagePreview(file: File) {
        val context = context ?: return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.cropper.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可打开图片的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        captureButton?.isEnabled = enabled
    }
}
