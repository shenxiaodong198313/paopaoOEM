# OEM 离线语音接入与配置

## 离线语音输入（可选 SDK，集成后默认开启）

自 SDK `1.0.0.20261008` / 输入法运行时 `app:1.9` 起，语音独立为可选 `oem-ime-sdk-voice`，提供官方 sherpa-onnx 1.13.8 离线流式识别和自动标点；不依赖 AI 回复、自动化或云端大模型。


### 按需集成语音 SDK

官网模块选择器勾选“离线语音”（`voice`），会加入语音 SDK 和 sherpa 框架；未勾选时基础输入法不依赖 sherpa，语音实现、JNI、设置页和麦克风权限不进入宿主，全部语音入口自动隐藏。

```groovy
// 在基础输入法 core + host-bridge 的依赖之外按需添加：
implementation "com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261008"
// sherpa-onnx:1.13.8 由语音 SDK 传递引入，无需手工添加。
```

完整 SDK ZIP 包含所有模块以便离线交付，Gradle 仅打包宿主实际声明的依赖。不集成语音时不要添加 voice 或 sherpa 坐标。官网 full 演示包包含语音；lite / `-PdemoVoice=false` 对照构建不包含语音。模块集成后默认开启，无需注册初始化器或额外 Manifest 开关；可用下述 JSON 将功能关闭。关闭配置保留已集成的代码和框架，减小 APK 应移除 voice 依赖。

| 档位 | 配置 ID | 首次下载含共用标点 | 说明 |
| --- | --- | --- | --- |
| 性能 | `performance` | 101,874,904 字节，约 102 MB | 默认；中文 Zipformer CTC INT8 |
| 均衡 | `quality` | 242,880,118 字节，约 243 MB | 中文 Zipformer Transducer INT8 |
| 质量 | `quality_xl` | 846,750,000 字节，约 847 MB | 中文 Zipformer XL Transducer INT8，高性能设备使用 |

三档共用 75,519,198 字节标点模型；下载体积不是运行内存，不承诺大模型在所有场景更准。模型权重不进入 APK，首次在“输入法设置 → 语音输入”主动下载，支持暂停、继续、失败重试、Range 续传、大小和 SHA-256 校验。下载成功后识别及标点在设备本地执行，语音模块不上传录音或转写正文；宿主或其他已开启模块对最终输入文字的处理仍由企业自身配置决定。

工具栏麦克风、工具面板“语音输入”和可配置的空格长按入口使用同一引擎。未下载时进入语音设置；未授权时申请 `RECORD_AUDIO`；永久拒绝提供系统设置入口，拒绝不影响普通键盘。录音时整块键盘显示波带动画和实时文字，点击“点我完成”结束并恢复皮肤。密码框不录音，切换输入框、移动光标、隐藏键盘或低内存会结束会话，迟到结果不写入新宿主。默认 180 秒录音上限、30 秒无声结束、手动结束最多等待 4 秒离线收尾。标点始终开启。

### 宿主配置文件

SDK 内置默认配置；企业需要调整时，将官网 `downloads/oem_voice_config.json` 复制为宿主 `app/src/main/res/raw/oem_voice_config.json`（不放 values-zh）。同名 Android 资源由宿主覆盖 SDK；配置在进程首次使用时读取，修改后须重新构建并重启宿主和输入法进程。也可只填写要覆盖的字段，省略字段使用以下默认值。

设置 `enabled: false` 将隐藏工具栏、工具面板、定制工具栏候选项及设置页入口，并阻止录音和模型下载；普通键盘保留，已下载模型不自动删除。开关不裁剪 AAR/JNI，也不自动移除 Manifest 权限。配置 JSON 无法解析、范围越界或不支持的解码方法会禁用语音并输出 `OemVoice` 配置错误日志，不影响基础输入。

```json
{
  "schemaVersion": 1,
  "enabled": true,
  "defaultMode": "performance",
  "punctuationThreads": 1,
  "maxRecordingSeconds": 180,
  "silenceTimeoutSeconds": 30,
  "finishTimeoutMs": 4000,
  "speechRmsThreshold": 0.01,
  "models": {
    "performance": {
      "numThreads": 2,
      "decodingMethod": "greedy_search",
      "maxActivePaths": 4,
      "blankPenalty": 0.0,
      "endpointSilenceSeconds": 2.4,
      "endpointSpeechSilenceSeconds": 0.7,
      "endpointMaxUtteranceSeconds": 15.0
    },
    "quality": {
      "numThreads": 2,
      "decodingMethod": "greedy_search",
      "maxActivePaths": 4,
      "blankPenalty": 0.0,
      "endpointSilenceSeconds": 2.4,
      "endpointSpeechSilenceSeconds": 0.7,
      "endpointMaxUtteranceSeconds": 15.0
    },
    "quality_xl": {
      "numThreads": 2,
      "decodingMethod": "greedy_search",
      "maxActivePaths": 4,
      "blankPenalty": 0.0,
      "endpointSilenceSeconds": 2.4,
      "endpointSpeechSilenceSeconds": 0.7,
      "endpointMaxUtteranceSeconds": 15.0
    }
  },
  "downloadUrlTemplates": [
    "https://hf-mirror.com/{repo}/resolve/{revision}/{name}",
    "https://huggingface.co/{repo}/resolve/{revision}/{name}"
  ],
  "fileUrls": {}
}
```

| 参数 | 默认值 | 有效范围 / 用途 |
| --- | --- | --- |
| `enabled` | `true` | 语音总开关 |
| `defaultMode` | `performance` | performance / quality / quality_xl；仅影响尚未选档的新安装，用户选择优先 |
| `models.<id>.numThreads` | `2` | 1–8，CPU ASR 线程；三档可分别调优 |
| `models.<id>.decodingMethod` | `greedy_search` | 性能档仅 greedy_search；均衡/质量另支持 modified_beam_search |
| `models.<id>.maxActivePaths` | `4` | 1–32；仅 modified_beam_search 时生效 |
| `models.<id>.blankPenalty` | `0.0` | 0–5，空白惩罚；非默认值需用业务语料验证 |
| `models.<id>.endpointSilenceSeconds` | `2.4` | 0.1–30 秒，未要求识别到文字的静音端点 |
| `models.<id>.endpointSpeechSilenceSeconds` | `0.7` | 0.1–10 秒，已有语音后的尾部静音 |
| `models.<id>.endpointMaxUtteranceSeconds` | `15` | 1–60 秒，最长短句；不是整段录音上限 |
| `punctuationThreads` | `1` | 1–4，标点 CPU 线程 |
| `maxRecordingSeconds` | `180` | 10–600 秒，整个录音会话上限 |
| `silenceTimeoutSeconds` | `30` | 5–120 秒，连续无声结束 |
| `finishTimeoutMs` | `4000` | 500–10000 毫秒，手动停止后的最大收尾等待 |
| `speechRmsThreshold` | `0.01` | 0.001–0.2，无声计时的 RMS 门限 |
| `downloadUrlTemplates` | hf-mirror / Hugging Face | 1–8 个 HTTPS 模板，按顺序失败回退；支持 {repo}、{revision}、{name}、{folder} |
| `fileUrls` | `{}` | 按“目录/文件名”覆盖 1–8 个完整 HTTPS 下载 URL；设置后该文件仅使用指定列表 |

### 更换下载地址

保持默认文件清单的内容、大小和 SHA-256 不变，仅替换镜像地址。企业 CDN 若按目录存放，可将模板改成 `https://cdn.example.com/voice/{folder}/{name}`；默认镜像依次为 `https://hf-mirror.com/{repo}/resolve/{revision}/{name}` 和 `https://huggingface.co/{repo}/resolve/{revision}/{name}`。

也可逐文件覆盖，例如：

```json
{"fileUrls":{"quality_xl/encoder.int8.onnx":["https://cdn.example.com/voice/quality_xl/encoder.int8.onnx"]}}
```

模型清单及当前完整 URL 见 `downloads/oem-voice-models.json`。CDN 应支持 HTTPS、准确的 Content-Length 和 HTTP Range/Content-Range；不支持 Range 返回 200 时会从头下载，错误长度、HTML 或错误 SHA 均不会激活模型。禁止把任意其他模型或压缩包替换到这些地址；变更模型架构、权重或校验清单须升级 SDK。URL 可含查询参数，但不应在配置中放私钥或长期敏感凭据。

### 验收

- 新装默认性能档；三档单选，共用一个下载按钮；三档首次下载和已下载切换正常。
- 暂停后继续可恢复进度；断网/错误校验显示重试；共用标点只下载一次。
- 完成下载后断网听写，实时上屏、自动标点及手动结束正常；拒绝权限/密码框不录音。
- 隐藏键盘、换宿主、移动光标后无迟到写入；无声和时限结束能恢复原皮肤。
- 独立 `Release/R8` 宿主能加载 JNI 并识别；不要删除随 AAR 提供的 sherpa consumer keep 规则。
- 未集成 voice 的宿主依赖树和 APK 无 sherpa AAR/JNI、无语音入口，普通键盘正常。
- `enabled: false` 的独立构建中所有语音入口消失，拼音与其他已授权 OEM 功能仍可用。
