# OEM 离线语音 SDK 回归

测试源码复制到独立企业宿主的 `app/src/androidTest/java/`，PCM 音频来自泡泡 Android 的 `androidTest/assets/voice_sample.pcm`。测试只向本地 InputCheckActivity 输入，关闭 enableQaSession，不向会话服务上传。

- `VoiceAbsenceTest`：未引入语音 SDK / 引入但 JSON 关闭的两种宿主；基础键盘可用，工具栏无语音入口。后一种传 `-e voiceInstalled true`。
- `VoiceIntegrationTest#configContracts`：参数边界、档位解码方法、URL 覆盖与非法配置。
- `VoiceIntegrationTest#downloadThreeModelsThroughSettings`：真实设置页依次下载三档，执行暂停/继续和全部 SHA-256 校验，不注入权重。
- `VoiceRuntimeDeviceTest`：三档固定中文 PCM、自动标点、二次调用常驻复用、纯静音结果。
- `VoiceTextSessionTest`：中间词修订、仅替换本会话内容、密码/光标/连接隔离相关文本边界。
- `VoiceIntegrationTest#microphonePanelStopCursorAndHide`：真机扬声器播放固定测试 WAV，由麦克风实际采集，验证语音面板、手动结束、光标取消与隐藏键盘，测试后恢复媒体音量。测试 WAV 从同一 PCM 转换为 16 kHz / mono / PCM16。
- `passwordGuardRejectsAllAndroidPasswordTypes`：用四类 Android 密码 EditorInfo 验证真实 controller 在录音前拒绝。OnePlus 安全键盘会截获密码框，无法用该机的系统输入框切换单独覆盖 OEM 密码分支；此项为 controller 边界测试。
- `permanentlyDeniedMicrophoneGuidesSystemSettings`：OnePlus 系统权限设为不允许后验证二次拒绝、去设置说明与取消返回；已授权时跳过。
- `openVoiceFromInputMethodSettings`：从 OEM 输入法设置实际进入三档语音页，并返回设置首页。
- 复用 `OEM/tests/system-skins/SkinInstrumentation.java` 验证浅深色皮肤、QWERTY/T9/数字输入及图标几何。

宿主保持 release 的 minifyEnabled/shrinkResources=true。AndroidJUnitRunner 测试 APK 与目标 App 分开编译，对照工程额外 keep `InputCheckActivity`、`androidx.tracing.**` 和 `kotlin.**`，防止目标 App 的 R8 删除测试运行器才用到的门面类。这些规则仅用于测试宿主，不进入公开 SDK 或官网演示包。
