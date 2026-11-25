package org.fcitx.fcitx5.android.input.status

import java.io.File

/** Add an app-owned layer to verified, compiled schemas; never change downloaded package bytes. */
object RimePredictionSchema {
    private const val marker = "paopao_prediction_version: 1"
    fun supports(id: String) = Regex("paopao_(frost|ice)_(pinyin|pinyin14|t9(_enhanced)?|double_pinyin(_(flypy|abc|mspy|sogou|ziguang))?)").matches(id)

    fun patch(id: String, original: String): String {
        if (!supports(id)) return original
        if (original.lineSequence().any { it == marker }) return original
        // All installed schemas are resolved by the pinned build compiler. Match
        // only its engine lists, refusing unknown formats instead of damaging YAML.
        val lines = original.lines().toMutableList()
        val engine = lines.indexOf("engine:")
        check(engine >= 0) { "联想方案缺少输入引擎配置" }
        fun insert(list: String, item: String, before: String? = null) {
            val end = (engine + 1 until lines.size).firstOrNull { lines[it].isNotEmpty() && !lines[it].startsWith(' ') } ?: lines.size
            val start = (engine + 1 until end).firstOrNull { lines[it] == "  $list:" }
                ?: error("联想方案列表格式不支持")
            val listEnd = (start + 1 until end).firstOrNull { !lines[it].startsWith("    - ") } ?: end
            check(listEnd > start + 1) { "联想方案列表为空" }
            val position = if (before == null) start + 1 else
                (start + 1 until listEnd).firstOrNull { lines[it] == "    - $before" }
                    ?: error("联想方案缺少按键处理器")
            if ((start + 1 until listEnd).none { lines[it] == "    - $item" }) lines.add(position, "    - $item")
        }
        insert("processors", "predictor", "key_binder")
        insert("translators", "predict_translator")
        check(lines.none { it == "predictor:" }) { "联想方案已有不同配置" }
        return lines.joinToString("\n").trimEnd() + "\n$marker\npredictor:\n  db: paopao-common-predict.db\n  max_candidates: 5\n  max_iterations: 1\n"
    }

    /** Startup worker, or dictionary installation before schema activation. No key-path I/O. */
    fun prepare(build: File) {
        for (file in build.listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".schema.yaml")) continue
            val id = file.name.removeSuffix(".schema.yaml")
            if (!supports(id)) continue
            val original = file.readText()
            val updated = patch(id, original)
            if (updated == original) continue
            val temp = File(build, file.name + ".prediction-tmp")
            temp.outputStream().use { it.write(updated.toByteArray()); it.fd.sync() }
            check(temp.renameTo(file)) { "联想方案写入失败" }
        }
    }
}
