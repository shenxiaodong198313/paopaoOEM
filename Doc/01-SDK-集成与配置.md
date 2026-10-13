# SDK 集成与配置

> 当前发布为 SDK `1.0.0.202610111` / runtime `app:1.12`，已包含完整输入法及八个公开 SDK。临时剪贴板快捷回复沿用现有客户 AI 配置，客户结果展示通过 DrawerKit 扩展；详见 [本轮记录](20-202610111临时剪贴板快捷回复.md) 和外部集成手册。下方早期阶段描述保留为历史。

## 当前状态

- 当前可见的 SDK 雏形模块是 `OEM/android/aikeyboard-sdk-ai`
- 该模块当前以 Android Library 形态存在，命名空间是 `com.paopaoime.aikeyboard.sdk.ai`
- 它目前承载的是 OEM AI 能力接入与宿主桥接能力，不等于完整输入法 SDK
- 现阶段更适合作为“AI 核心能力 SDK”第一阶段输出
- 第二阶段新增了 `OEM/android/oem-ime-sdk-core`
- `oem-ime-sdk-core` 负责统一初始化、License 请求模型、能力开关和状态查询
- 当前已补充 `oem-ime-host-bridge`，用于把输入法 UI、服务与宿主打成同包交付
- 自动化能力已在 `OEM/company` 宿主验证壳中完成一轮闭环验证，但仍属于可选套餐，不建议默认对所有客户开放

## 当前对外能力

- `AiKeyboardHostSdk.initialize(application)`：初始化宿主桥接与 AI 运行时上下文
- `AiKeyboardHostSdk.initializeLogCollector()`：初始化日志采集
- `AiKeyboardHostSdk.generateDiffusionOutputPath(context, prefix)`：生成扩散图输出路径
- `AiKeyboardHostSdk.createSettingsIntent(context)`：拉起 OEM 输入法设置页
- `AiKeyboardHostSdk.loadDownloadedModels(context)`：读取已下载模型列表
- `AiKeyboardAiSdk.createSession(...)`：创建受管会话
- `AiKeyboardAiSdk.loadModel(...)`：加载本地模型
- `AiKeyboardAiSdk.getSession()`：获取当前全局会话
- `AiKeyboardAiSdk.release()`：释放全局会话

## 当前边界

- 当前不具备完整输入法宿主集成接口
- 当前云端流式回复能力是占位态，默认抛出 `UnsupportedOperationException`
- 当前完整 License 服务端验签和租户隔离仍未完全落地
- 当前 `oem-ime-sdk-core` 已具备独立 Maven 发布链、consumer rules 和 SDK 版本策略
- 当前 `oem-ime-sdk-core` 仍是第二阶段最小实现，尚未承接完整 UI/IME 宿主桥

## 推荐的 SDK 交付结构

- `oem-ime-sdk-core`：输入法核心接口、初始化、License 验签、配置下发
- `oem-ime-sdk-ai`：AI 会话、运行时桥接、下载器与相关能力
- `oem-ime-sdk-automation`：自动化桥接，可选依赖
- `oem-ime-sdk-ui`：宿主若需要嵌入页面时使用，可选依赖
- `oem-ime-demo-app`：内部联调壳，不对客户直接交付

## 推荐集成方式

1. 厂家宿主在 `Application` 中初始化 SDK
2. 宿主传入 `customerId`、`appKey`、`channel`、`licenseSource`
3. SDK 完成 License 校验后再开放能力
4. 宿主按需启用 AI、本地模型、自动化等功能开关
5. 宿主通过统一入口读取当前授权状态和能力状态

## 推荐初始化接口

```kotlin
data class OemSdkConfig(
    val customerId: String,
    val appKey: String,
    val channel: String,
    val environment: String,
    val licenseProvider: LicenseProvider,
    val enableAi: Boolean,
    val enableAutomation: Boolean,
)

object OemImeSdk {
    fun initialize(application: Application, config: OemSdkConfig)
    fun getStatus(): OemSdkStatus
}
```

## 第二阶段已落地接口

```kotlin
enum class OemCapability {
    AI,
    AUTOMATION,
    UI,
}

enum class OemLicenseStatus {
    ACTIVE,
    GRACE,
    EXPIRED,
    DISABLED,
    MISSING,
    INVALID,
}

data class OemLicenseRequest(
    val customerId: String,
    val appKey: String,
    val channel: String,
    val environment: OemEnvironment,
    val hostIdentity: OemHostIdentity,
    val sdkVersion: String,
    val requestedCapabilities: Set<OemCapability>,
    val hostUserIdHash: String?,
)

fun interface OemLicenseProvider {
    fun fetchLicense(context: Context, request: OemLicenseRequest): OemLicenseSnapshot
}

object OemImeSdk {
    fun initialize(application: Application, config: OemSdkConfig): OemSdkStatus
    fun initializeFromManifest(application: Application, licenseProvider: OemLicenseProvider): OemSdkStatus
    fun getStatus(): OemSdkStatus
    fun isCapabilityAvailable(capability: OemCapability): Boolean
}
```

## 推荐配置来源

- 默认配置：随 AAR 内置
- 宿主覆盖配置：Manifest meta-data 或初始化参数
- 远端配置：授权成功后从 License Server 拉取
- 私有化配置：由本地 License 文件或局域网 License Server 提供

当前已实现的 Manifest key：

- `com.paopaoime.oem.customerId`
- `com.paopaoime.oem.appKey`
- `com.paopaoime.oem.channel`
- `com.paopaoime.oem.environment`
- `com.paopaoime.oem.enableAi`
- `com.paopaoime.oem.enableAutomation`
- `com.paopaoime.oem.enableUi`
- `com.paopaoime.oem.enableAiReplyCustomUi`（R9 新增）：AI回复「自定义UI交互」开关，默认 `false`
- `com.paopaoime.oem.enableMaterialLibraryCustomUi`（R9 新增）：素材库「自定义UI交互」开关，默认 `false`
- `com.paopaoime.oem.enableCustomDrawers`（R9 新增）：用户自定义抽屉开关，默认 `false`
- `com.paopaoime.oem.aiReplyGenerationTimeoutMs`：AI 回复生成整体超时（毫秒），默认 `30000`
- `com.paopaoime.oem.aiReplyNetworkTimeoutMs`：AI 回复域名通道网络超时（毫秒），默认 `30000`
- `com.paopaoime.oem.aiReplyErrorTextCustomEnabled`：AI 回复失败文案宿主自定义开关，默认 `false`

## 自动化可选模块接入

当客户交付包包含 `oem-ime-host-bridge` 与输入法自动化引擎运行时时，宿主除 `OemImeSdk` 初始化外，还需要调用 SDK 提供的公开宿主引导入口，不要直接依赖内部实现类。

推荐的 `Application` 初始化链：

```kotlin
class HostApp : FcitxApplication() {
    override fun onCreate() {
        super.onCreate()
        OemHostRuntime.initialize(this)
        OemImeSdk.initializeFromManifest(
            application = this,
            licenseProvider = YourLicenseProvider(),
        )
    }
}
```

宿主 Manifest 还必须补齐：

```xml
<meta-data
    android:name="com.paopaoime.oem.enableAutomation"
    android:value="true" />
<meta-data
    android:name="com.paopaoime.oem.enableAutomationSettingsEntry"
    android:value="false" />
<meta-data
    android:name="com.paopaoime.oem.enableUi"
    android:value="true" />
```

自动化权限元数据已随 SDK 默认合并，无需额外暴露底层引擎键名。
`enableAutomationSettingsEntry=false` 为默认基线：即使已交付自动化模块，也不默认显示“自动化设置”入口，只有企业明确需要时才改为 `true`。

发布输入法自动化引擎制品时，必须把宿主包名带进发布参数：

```properties
automationEngineApplicationId=com.example.host
automationEngineDefaultAllowedPackages=com.example.host,com.example.host.debug
```

如果不覆盖这两个值，`manager/provider/binder` 仍可能继续指向旧宿主包名，表面现象通常是：

- 宿主侧显示正在等待 Binder 响应
- 输入法自动化引擎服务进程已启动，但宿主拿不到可用服务
- 授权页或 Provider authority 仍落在旧 `applicationId`

设备侧最小验收路径：

1. 若联调阶段需要宿主页暴露入口，临时将 `enableAutomationSettingsEntry=true`
2. 进入宿主设置页的“自动化设置”
3. 连续点击该区域 10 次，进入内嵌“输入法自动化引擎”
4. 在“输入法自动化引擎”首页启动无线 ADB / Root 服务
5. 返回输入法设置页，确认“自动化设置”入口可以正常进入，并且不会再跳回额外宿主页
6. 再次关闭并开启自动化开关，确认状态能往返切换

## 宿主必须提供的最小信息

- `customerId`
- `applicationId`
- 宿主签名证书摘要 `certSha256`
- 发布渠道 `channel`
- 当前 SDK 版本

## 版本管理建议

- SDK 使用独立语义化版本：`major.minor.patch`
- 对外文档同时标记“最低宿主版本要求”
- 每个版本都给出迁移说明和废弃 API 列表
- 严禁让厂家直接依赖 OEM demo 工程源码

## 当前建议

- 第一阶段先把 `aikeyboard-sdk-ai` 提纯成真正的 AAR 交付模块
- 第二阶段已把初始化、授权请求模型、能力开关和状态查询抽成 `oem-ime-sdk-core`
- 第三阶段才考虑暴露更大范围的 UI 或自动化能力

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


## 20261010 双默认皮肤交互更新

日光瓷白与夜幕石墨统一键盘比例、候选高亮留白与文字、按键气泡、键盘类型图标和分页工具面板；工具栏与工具面板不提供皮肤入口。八个公开 SDK 重新制作并执行 Release/R8，输入法运行时升级至 app:1.10。
当前 SDK `1.0.0.20261010`，运行时 `app:1.10`。构建、部署及验收状态见 [本轮记录](18-20261010双默认皮肤交互对齐.md)。本轮不操作手机做界面测试，安装后由用户验收。


## 20261011 剪贴板工具栏修复

复制文本不再自动替换工具栏，包含键盘显示中、重新唤起及旧设置为开启的场景；保留手动剪贴板、历史和粘贴。移除无效的工具栏剪贴板建议/超时设置。八个公开 SDK 全量重建并执行 Release/R8，输入法运行时升级至 app:1.11。 状态见 [本轮记录](19-20261011剪贴板不替换工具栏.md)。


## 202610111 临时剪贴板快捷回复

已同步同宿主一次性剪贴板与高情商快捷回复，复用 AI 回复客户配置并支持 DrawerKit 客户自绘结果。SDK `1.0.0.202610111`、runtime `app:1.12`；构建与发布状态见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。

### 入口点击由宿主接管（1.0.0.20261012）

```kotlin
fun interface OemCustomDrawerEntryHandler {
    fun onEntryClick(context: Context, drawerId: String): Boolean
}

// 既有注册项可用 copy 更新，或在首次构造时传 entryHandler。
OemImeSdk.updateCustomDrawer(spec.copy(
    entryHandler = OemCustomDrawerEntryHandler { context, drawerId ->
        // 宿主自行决定展示什么、何时展示；context 为 IME Service。
        // 如启动 Activity，请自行添加 FLAG_ACTIVITY_NEW_TASK。
        true // 返回值预留，true / false 均不触发 SDK 后续动作。
    }
))
```

- `entryHandler == null`：收键盘、创建抽屉并调用 `contentFactory`，沿用原流程。
- 非空：工具条和工具面板入口均只在主线程回调一次；SDK 不收键盘、不创建 Activity/抽屉、不调用内容工厂、不输出点击日志/Toast。返回 `false` 或抛异常也不会回退默认流程，异常由 SDK 静默捕获。
- 回调在注册项查找成功后、编辑器与 material 切片校验前执行；纯入口接管不依赖 material 切片。注册开关 `enableCustomDrawers=true` 仍须开启。
- `contentFactory` 仍为必填字段，接管时不调用。标题、图标、四个名额、宫格分页、工具条排序/显隐/隐藏池、`custom_<id>` 持久化及注销清理沿用原规则。
- 新字段位于 `contentFactory` 之后，保留旧位置参数与五参数 JVM 构造函数；请统一升级全部 SDK、runtime 并重新编译宿主及使用该 data class 的预编译库，不混用旧版二进制。
- `OemImeSdk.openDrawer(id)` 同样进入现有路由；设置回调后请勿在回调内递归调用同 id。需要恢复默认抽屉时先以 `copy(entryHandler = null)` 更新注册项。

### 剪贴板“智能回复”由宿主接管（1.0.0.20261012）

```kotlin
// package com.paopaoime.aikeyboard.sdk.core
fun interface OemClipboardQuickReplyHandler {
    fun onQuickReply(context: Context, clipboardText: String, hostPackageName: String?): Boolean
}

OemImeSdk.setClipboardQuickReplyHandler(
    OemClipboardQuickReplyHandler { context, clipboardText, hostPackageName ->
        // context 是 IME Service；文本与来源包名来自本次复制快照。
        // 宿主自行决定是否展示页面；启动 Activity 时自行添加 NEW_TASK。
        false // 返回值预留，false / true 均表示由宿主接管。
    }
)
// 恢复 SDK 内置生成、结果抽屉和草稿回填流程：
OemImeSdk.setClipboardQuickReplyHandler(null)
```

- 仅携带剪贴板原文的 AI_REPLY 请求走此回调；普通 AI 回复入口、素材库入口与粘贴行为不变。
- 注册后点击“智能回复”只在主线程回调宿主；不启动 Activity、不收键盘、不生成、不调用内容工厂、不弹 Toast、不打 SDK 点击日志、不清除当前建议条。宿主抛异常由 SDK 静默捕获，返回值被忽略。
- 回调在 SDK AI 功能与授权前置校验之前执行，纯接管无需 ai-reply/material 切片；注册时“智能回复”按钮仍可显示。SDK 内置生成继续遵守原有开关和授权。
- `hostPackageName` 为本次复制会话记录的来源宿主，可为 null；SDK 不读取其他应用的剪贴板历史。敏感文本和跨宿主过滤保持现状。
- 建议在 SDK 初始化之后、首次展示键盘前注册；`null` 反注册。回调内反注册也不会使本次点击转入内置流程或清除建议条。
- OEM UI 去掉整块标题及关闭叉号，按钮文案为“智能回复”；文本区单行省略，点击粘贴保持原行为。工具条在当前皮肤值基础上增加 12 布局单位（标准竖屏总高 60→72，横屏 56→68），候选行与测量同步。泡泡主版不变。
- 请同步升级全部 SDK 到 `1.0.0.20261012`、runtime 到 `app:1.13`，并重新编译宿主。
