package com.paopaoime.aikeyboard.ime.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.paopaoime.aikeyboard.ime.R
import android.view.KeyEvent

open class ImeService : InputMethodService() {

    private var isShiftOn: Boolean = false

    override fun onCreateInputView(): View {
        return createKeyboardView()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isShiftOn = false
    }

    private fun createKeyboardView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        titleRow.addView(
            TextView(this).apply {
                text = getString(R.string.paopao_app_name)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        titleRow.addView(
            Button(this).apply {
                text = "切换"
                minHeight = 0
                minimumHeight = 0
                setOnClickListener { showInputMethodPicker() }
            }
        )
        container.addView(titleRow)

        val letterButtons = mutableListOf<Button>()

        fun addRow(keys: List<KeySpec>) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            keys.forEach { spec ->
                val btn = Button(this).apply {
                    text = spec.label
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, spec.weight)
                    minHeight = 0
                    minimumHeight = 0
                    setOnClickListener { spec.onClick() }
                }
                spec.onCreated?.invoke(btn)
                row.addView(btn)
            }
            container.addView(row)
        }

        fun commitText(text: String) {
            val ic = currentInputConnection ?: return
            ic.commitText(text, 1)
        }

        fun sendDownUpKeyEvent(keyCode: Int) {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }

        fun deleteOne() {
            val ic = currentInputConnection ?: return
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
                return
            }
            ic.deleteSurroundingText(1, 0)
        }

        fun toggleShift() {
            isShiftOn = !isShiftOn
            letterButtons.forEach { btn ->
                val t = btn.text?.toString().orEmpty()
                btn.text = if (isShiftOn) t.uppercase() else t.lowercase()
            }
        }

        addRow(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { ch ->
                KeySpec(
                    label = ch,
                    onClick = { commitText(if (isShiftOn) ch.uppercase() else ch) },
                    onCreated = { letterButtons.add(it) }
                )
            }
        )
        addRow(
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { ch ->
                KeySpec(
                    label = ch,
                    onClick = { commitText(if (isShiftOn) ch.uppercase() else ch) },
                    onCreated = { letterButtons.add(it) }
                )
            }
        )
        addRow(
            listOf(
                KeySpec(label = "↑", weight = 1.2f, onClick = { toggleShift() }),
                *listOf("z", "x", "c", "v", "b", "n", "m").map { ch ->
                    KeySpec(
                        label = ch,
                        onClick = { commitText(if (isShiftOn) ch.uppercase() else ch) },
                        onCreated = { letterButtons.add(it) }
                    )
                }.toTypedArray(),
                KeySpec(label = "⌫", weight = 1.6f, onClick = { deleteOne() })
            )
        )
        addRow(
            listOf(
                KeySpec(label = "空格", weight = 5f, onClick = { commitText(" ") }),
                KeySpec(label = "回车", weight = 2f, onClick = { sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER) })
            )
        )

        return container
    }

    private fun showInputMethodPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showInputMethodPicker()
    }

    private fun openSystemImeSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun openFcitx5Settings() {
        val pm = packageManager
        val intents = listOf(
            Intent("android.intent.action.APPLICATION_PREFERENCES").apply {
                setPackage("org.fcitx.fcitx5.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent().apply {
                setClassName(
                    "org.fcitx.fcitx5.android",
                    "org.fcitx.fcitx5.android.ui.main.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            val canResolve = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) != null
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0) != null
            }
            if (!canResolve) continue
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }

        Toast.makeText(this, "未找到 fcitx5 的设置页面（可能未安装）", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    private data class KeySpec(
        val label: String,
        val weight: Float = 1f,
        val onClick: () -> Unit,
        val onCreated: ((Button) -> Unit)? = null
    )
}
