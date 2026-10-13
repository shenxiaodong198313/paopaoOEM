package com.paopaoime.aikeyboard.ime.voice

// Performance/balanced/quality use the same pinned weights on Android and Harmony.
data class ModelFile(val folder: String, val name: String, val repo: String, val revision: String, val bytes: Long, val sha256: String) {
    fun url(host: String) = "https://$host/$repo/resolve/$revision/$name"
}
enum class VoiceMode(val id: String, val title: String, val description: String) {
    PERFORMANCE("performance", "性能模式", "更轻快，适合日常短句与低配设备"),
    // Preserve the original ID and downloaded folder when renaming the 243 MB tier.
    BALANCED("quality", "均衡模式", "兼顾速度与准确度，适合日常使用"),
    QUALITY("quality_xl", "质量模式", "更大中文模型，适合高性能设备");
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: PERFORMANCE }
}
object VoiceModels {
    val files = listOf(
        ModelFile("performance", "model.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01", "a5f60fe00dcfbaf68fcc1c6b5cf53061e144d6da", 26342340L, "68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a"),
        ModelFile("performance", "tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01", "a5f60fe00dcfbaf68fcc1c6b5cf53061e144d6da", 13366L, "6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd"),
        ModelFile("quality", "decoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30", "ad658fa0201659a09ea3c176129a191c77ecae8f", 5165083L, "06522ad63cec0fdf6809f4e1db9bb4f7d710c34582e3b35db62ac60eccafac7e"),
        ModelFile("quality", "encoder.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30", "ad658fa0201659a09ea3c176129a191c77ecae8f", 161141793L, "5ac51e27981bb4dab01bb9be4958453ba50c3b61c063ddda0eab23fd3671aa4f"),
        ModelFile("quality", "joiner.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30", "ad658fa0201659a09ea3c176129a191c77ecae8f", 1033416L, "b34584dc6f561089e1d747fedebb3765f2caa72c927ef54d7ca55e5ae40a814b"),
        ModelFile("quality", "tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30", "ad658fa0201659a09ea3c176129a191c77ecae8f", 20628L, "6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652"),
        ModelFile("quality_xl", "decoder.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30", "18f31ce4644ac3bcf544eea692d7242b798d508f", 8533022L, "8f9c903da2818f207304a3f30b9eeb30028e30398f333c1e95e12c97704173e6"),
        ModelFile("quality_xl", "encoder.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30", "18f31ce4644ac3bcf544eea692d7242b798d508f", 761133737L, "f2c543a0330e1ed0bd09c82e4ae7d3f1cbee10a15feca638fcc4f88083a36b8a"),
        ModelFile("quality_xl", "joiner.int8.onnx", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30", "18f31ce4644ac3bcf544eea692d7242b798d508f", 1545417L, "f76ffce14b6ef80098cfdbce8846896ff68133970abc314eafab632f910df0d7"),
        ModelFile("quality_xl", "tokens.txt", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30", "18f31ce4644ac3bcf544eea692d7242b798d508f", 18626L, "6722bd1585f46f84456b29c3550a343a3cc375b971645773c02ed8e0b4e2405c"),
        ModelFile("punctuation", "model.int8.onnx", "ranger810/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8", "5cccf43af83e4fc50d1d55b8410312e87709be70", 75519198L, "65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1")
    )
    fun files(mode: VoiceMode) = files.filter { it.folder == mode.id || it.folder == "punctuation" }
    fun bytes(mode: VoiceMode) = files(mode).sumOf { it.bytes }
}
