# 外部版 API 参考

## 适用范围

本参考文档覆盖 OEM 版本当前保留的核心联网能力：

- 授权预留接口与 License 模型
- 输入质检会话上传
- 素材库标准对接协议
- AI 回复
- 可选 `ADB自动化`
- DrawerKit 抽屉开放层（L1 自定义UI / L2 自定义抽屉，R9 新增）

OEM 版已清理知识库、邀请、支付、主题上报等非核心业务联网能力，本页不再承诺这些历史接口。

## 推荐依赖关系

- `oem-ime-sdk-core`：统一初始化、License、能力开关、状态查询
- `oem-ime-sdk-ai`：本地模型、会话、模型下载和设置页跳转

## `oem-ime-sdk-core`

### `OemImeSdk`

职责：

- 统一宿主初始化入口
- 构建 License 请求上下文
- 收口能力开关和可用状态
- 提供基于 Manifest 的配置读取

主要方法：

```kotlin
object OemImeSdk {
    fun initialize(application: Application, config: OemSdkConfig): OemSdkStatus
    fun initializeFromManifest(application: Application, licenseProvider: OemLicenseProvider): OemSdkStatus
    fun isInitialized(): Boolean
    fun getStatus(): OemSdkStatus
    fun isCapabilityAvailable(capability: OemCapability): Boolean
    fun getEffectiveFeatureToggles(): OemFeatureToggles
    fun setFeatureToggles(toggles: OemFeatureToggles?)
    fun updateFeatureToggles(...)
    fun setAiReplyEnabled(enabled: Boolean?)
    fun setMaterialLibraryEnabled(enabled: Boolean?)
    fun setQaSessionEnabled(enabled: Boolean?)
    fun setSendTextMaxChars(maxChars: Int?)
    fun getSendTextMaxChars(): Int?
    fun setBuiltinNeonOverlayEnabled(enabled: Boolean)
    fun clearBuiltinNeonOverlayEnabledOverride()
    fun isBuiltinNeonOverlayEnabled(): Boolean
    fun setAiReplyLifecycleListener(listener: OemAiReplyLifecycleListener?)
    fun hasAiReplyLifecycleListener(): Boolean
    fun manifestConfig(context: Context): OemManifestConfig
    fun createSettingsIntent(context: Context): Intent
}
```

运行时配置规则：

- `initialize` / `initializeFromManifest` 负责建立初始配置快照
- 细粒度功能开关支持运行时热更新，优先级为：`运行时覆盖 > OemSdkConfig / Manifest > 默认值`
- 当前支持热更新的开关包括：`aiReplyEnabled`、`materialLibraryEnabled`、`qaSessionEnabled`、`aiReplyCustomUiEnabled`、`materialLibraryCustomUiEnabled`、`customDrawersEnabled`、`automationEnabled`、`developerEnabled`
- `setFeatureToggles(null)` 或 `clearFeatureToggleOverrides()` 可清除运行时覆盖，回退到初始化配置

运行时热更新示例：

```kotlin
OemImeSdk.updateFeatureToggles(
    qaSessionEnabled = false,
    materialLibraryEnabled = true,
)

OemImeSdk.setAiReplyEnabled(true)
OemImeSdk.setMaterialLibraryEnabled(false)
```

### `OemSdkConfig`

核心字段：

- `customerId`：填写我方交付的企业码，不是企业名称
- `appKey`：当前与企业码同值，直接填写同一企业码
- `channel`
- `environment`
- `licenseProvider`：授权连接器，按标准模板接入；如我方另行交付专用实现则替换
- `enableAi`
- `enableAutomation`
- `enableUi`
- `sendTextMaxChars`：AI 回复与素材库单次文本发送最大字符数，默认 `1000`；文本提交验证窗口会随该值联动，超过阈值时 SDK 明确失败提示，不会静默截断
- `builtinNeonOverlayEnabled`：SDK 内置 AI 回复霓虹边框/后台接管层总开关，默认 `false`；宿主需要继续使用 SDK 内置霓虹时才显式设为 `true`
- `aiReplyLifecycleListener`：AI 回复生成生命周期监听器，宿主可用它自管 loading、悬浮窗或生成中状态

### `OemLicenseProvider`

```kotlin
fun interface OemLicenseProvider {
    fun fetchLicense(context: Context, request: OemLicenseRequest): OemLicenseSnapshot
}
```

说明：

- `customerId` 与 `appKey` 为初始化必填项
- `channel` 默认使用 `release`
- 当企业未开通、企业被管理员停用、或当前设备席位被管理员停用时，AI 回复、素材库等增值能力会显示受限
- 基础输入不受影响

### `OemLog`（集成调试日志）

```kotlin
enum class OemLogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

fun interface OemLogListener {
    fun onLog(level: OemLogLevel, tag: String, message: String, throwable: Throwable?)
}

object OemLog {
    fun setEnabled(value: Boolean)
    fun isEnabled(): Boolean
    fun setLogcatEnabled(value: Boolean)
    fun isLogcatEnabled(): Boolean
    fun setMinLevel(level: OemLogLevel)
    fun getMinLevel(): OemLogLevel
    fun setListener(value: OemLogListener?)
    fun hasListener(): Boolean
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable?)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable?)
}
```

结构化诊断事件：

```kotlin
data class OemDiagnosticEvent(
    val category: String,
    val name: String,
    val level: OemLogLevel,
    val message: String,
    val attributes: Map<String, String>,
    val timestampMs: Long,
)

fun interface OemDiagnosticListener {
    fun onDiagnosticEvent(event: OemDiagnosticEvent): Boolean
}

object OemImeSdk {
    fun setDiagnosticListener(listener: OemDiagnosticListener?)
    fun hasDiagnosticListener(): Boolean
}
```

说明：

- 总开关**默认关闭**，关闭时不产生任何输出，开销近似为零；SDK 自身不存储任何日志
- 开启方式（任一即可）：Manifest meta-data `com.paopaoime.oem.enableDebugLog=true`（初始化入口自动读取）；`OemSdkConfig.enableDebugLog`；运行时 `OemLog.setEnabled(true)`
- 开启后日志同时发往 logcat 与宿主注册的 `OemLogListener`；`setLogcatEnabled(false)` 可只保留接收器；`setMinLevel` 按级别过滤
- 接收器回调在日志产生线程同步执行，宿主更新 UI 需自行切换线程；接收器内部抛出的异常会被 SDK 捕获
- 覆盖链路：初始化、授权刷新、配置解析、素材库（含企业远程素材）、AI 回复、自定义抽屉注册/打开/关闭、宿主回调派发（accepted/rejected）
- 结构化诊断：宿主可通过 `OemImeSdk.setDiagnosticListener { event -> ... }` 接收排障事件；素材库恢复宿主输入框失败时会回调 `category=material_restore`、`name=restore_failed` / `restore_failed_toast`，`attributes` 包含 `hostPackage`、`baselineStartCount`、`finalStartCount`、`restoreAttempts` 等字段

### AI 回复生成生命周期与内置霓虹开关

```kotlin
interface OemAiReplyLifecycleListener {
    fun onGenerationStarted(requestId: String, generateMode: String, fresh: Boolean)
    fun onGenerationFinished(requestId: String, generateMode: String, success: Boolean, reason: String)
}

object OemImeSdk {
    fun setAiReplyLifecycleListener(listener: OemAiReplyLifecycleListener?)
    fun hasAiReplyLifecycleListener(): Boolean
    fun setBuiltinNeonOverlayEnabled(enabled: Boolean)
    fun clearBuiltinNeonOverlayEnabledOverride()
    fun isBuiltinNeonOverlayEnabled(): Boolean
}
```

说明：

- `onGenerationStarted` 在 AI 回复生成任务真正进入生成链路时触发；`fresh=true` 表示重新取上下文/截图/粘贴/传图生成，`fresh=false` 表示基于已有上下文换语气或重新生成
- `onGenerationFinished` 对每个已 started 的 `requestId` 严格收口一次；`reason` 当前包含 `success`、`failed`、`cancelled`、`timeout`
- 监听器异常会被 SDK 捕获并写入日志，不影响 AI 回复主流程
- SDK 内置 AI 回复霓虹默认关闭，宿主可通过生命周期回调自行展示生成中 UI；如需沿用 SDK 内置霓虹，可设置 Manifest `com.paopaoime.oem.builtinNeonOverlayEnabled=true`，或运行时调用 `OemImeSdk.setBuiltinNeonOverlayEnabled(true)`

示例：

```kotlin
OemImeSdk.setAiReplyLifecycleListener(object : OemAiReplyLifecycleListener {
    override fun onGenerationStarted(requestId: String, generateMode: String, fresh: Boolean) {
        HostFloatingLoading.show(requestId, generateMode, fresh)
    }

    override fun onGenerationFinished(requestId: String, generateMode: String, success: Boolean, reason: String) {
        HostFloatingLoading.hide(requestId, success, reason)
    }
})
```

### Manifest key

- `com.paopaoime.oem.customerId`：填写我方交付的企业码
- `com.paopaoime.oem.appKey`：当前与企业码同值，直接填写同一企业码
- `com.paopaoime.oem.channel`：默认 `release`
- `com.paopaoime.oem.environment`
- `com.paopaoime.oem.apiBaseUrl`
- `com.paopaoime.oem.aiMode`
- `com.paopaoime.oem.aiOpenAiBaseUrl`
- `com.paopaoime.oem.aiOpenAiApiKey`
- `com.paopaoime.oem.aiOpenAiModel`
- `com.paopaoime.oem.enableAi`
- `com.paopaoime.oem.enableAutomation`
- `com.paopaoime.oem.enableAutomationSettingsEntry`
- `com.paopaoime.oem.enableUi`
- `com.paopaoime.oem.enableAiReplyCustomUi`（R9 新增）：AI 回复「自定义UI交互」开关，默认 `false`；开启后 AI 回复抽屉内容层由宿主自绘
- `com.paopaoime.oem.enableMaterialLibraryCustomUi`（R9 新增）：素材库「自定义UI交互」开关，默认 `false`；开启后素材库抽屉内容层由宿主自绘
- `com.paopaoime.oem.enableCustomDrawers`（R9 新增）：用户自定义抽屉开关，默认 `false`；开启后运行时注册的自定义抽屉入口才会暴露
- `com.paopaoime.oem.aiReplyGenerationTimeoutMs`：AI 回复整体生成超时（毫秒），默认 `30000`
- `com.paopaoime.oem.aiReplyNetworkTimeoutMs`：AI 回复域名通道网络请求（连接/读/写）超时（毫秒），默认 `30000`
- `com.paopaoime.oem.aiReplyErrorTextCustomEnabled`：AI 回复失败文案宿主自定义开关，默认 `false`（使用 SDK 默认文案）
- `com.paopaoime.oem.sendTextMaxChars`：AI 回复与素材库单次文本发送最大字符数，默认 `1000`；运行时可通过 `OemSdkConfig.sendTextMaxChars` 或 `OemImeSdk.setSendTextMaxChars(...)` 覆盖。该值同时作用于发送前长度校验与输入框提交验证；超过阈值时 SDK 会提示“超出可发送字数上限（N字）”并失败，不会静默截断
- `com.paopaoime.oem.builtinNeonOverlayEnabled`：SDK 内置 AI 回复霓虹边框/后台接管层总开关，默认 `false`；宿主自管生成中 UI 时保持关闭
- `com.paopaoime.oem.ocrEnabled`：SDK 内置 OCR 总开关，默认 `true`；设为 `false` 后 SDK 跳过 Paddle-Lite OCR 初始化与识别，适合低配机或宿主自管 OCR 场景
- `com.paopaoime.oem.enableDebugLog`：集成调试日志总开关，默认 `false`；详见「OemLog（集成调试日志）」章节

### 输入法显示名配置

OEM 输入法显示名不通过 `Manifest meta-data` 配置，而是通过宿主资源文件覆盖：

`app/src/main/res/values/oem_ime_config.xml`

```xml
<resources>
    <string name="oem_ime_display_name">XX企业输入法</string>
</resources>
```

说明：

- `oem_ime_display_name` 是 OEM 输入法统一显示名配置项
- 该字段会同步影响：
  - 系统“语言和输入法”中的输入法名称
  - 输入法启用/切换提示中的名称
  - 输入法设置页标题、启动页标题等统一品牌入口
- 企业可直接写成固定名称，例如：`XX企业输入法`
- 推荐直接把最终显示名写进 `oem_ime_display_name`，不要再额外引入 `company_app_name` / `app_name` 等别名链
- 如果确实使用 flavor `resValue` 或多语言资源注入品牌名，必须确保默认 `values` 与 `values-zh` 不再保留同名静态实体值；否则 release 阶段可能触发 `ExtraTranslation` 或重复资源冲突
- 该能力必须走资源覆盖，是因为系统输入法名称来自 `service android:label` 和 `input-method xml`，运行时 `meta-data` 无法覆盖这里

### 企业 API 域名

`com.paopaoime.oem.apiBaseUrl` 是 OEM 版统一的企业后端基础域名。当前阶段主要用于输入质检、AI 代理和自动化任务等联网能力；后续如果正式企业授权上线，也会统一从该域名体系扩展。

企业接入时必须显式配置企业自有域名，例如：

```xml
<meta-data
    android:name="com.paopaoime.oem.apiBaseUrl"
    android:value="https://api.enterprise.example.com" />
```

联调说明页：

- `https://www.paopaoime.com/oem/API/`

建议：

- 无论演示环境还是生产环境，都必须填写企业自己的 API 域名
- 不再提供 `paopaoime` 域名作为默认联调后端
- 如企业同时提供网关、鉴权和审计，推荐优先走企业代理模式

### AI 模式

`com.paopaoime.oem.aiMode` 支持以下取值：

- `proxy`：只走企业后端代理
- `direct`：只走 SDK 直连兼容 OpenAI 模型
- `dual`：优先代理，失败时回退到直连

直连模式额外字段：

```xml
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiBaseUrl"
    android:value="https://your-openai-compatible-host/v1" />
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiApiKey"
    android:value="your_api_key" />
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiModel"
    android:value="gpt-4o-mini" />
```

说明：

- `proxy` 适合企业统一做权限、风控、日志和额度控制
- `direct` 适合快速联调兼容 OpenAI 的模型平台
- `dual` 适合正式环境容灾，SDK 会优先请求企业代理，代理未配置或失败时回退到直连
- `aiOpenAiApiKey` 建议由宿主安全配置注入，不建议在正式包内长期明文固化

### 自动化接入补充要求

当交付包包含自动化套餐时，还需要满足以下条件：

1. `OemSdkConfig.enableAutomation = true`
2. `OemSdkConfig.enableAutomationSettingsEntry = false`（默认隐藏自动化设置入口，仅企业明确需要时再改为 `true`）
3. `OemSdkConfig.enableUi = true`
4. Manifest 中声明：

```xml
<meta-data
    android:name="com.paopaoime.oem.enableAutomation"
    android:value="true" />
<meta-data
    android:name="com.paopaoime.oem.enableAutomationSettingsEntry"
    android:value="false" />
```

4. `Application` 中补齐自动化引导：

```kotlin
OemHostRuntime.initialize(this)
```

5. 发布输入法自动化引擎制品时，将宿主包名写入：

```properties
automationEngineApplicationId=com.example.host
automationEngineDefaultAllowedPackages=com.example.host,com.example.host.debug
```

如果没有同步覆盖宿主包名，常见现象是：

- 宿主页面停在等待 Binder
- 输入法自动化引擎服务进程已运行，但宿主显示未检测到可用服务
- 授权页面仍指向旧宿主包

## DrawerKit（抽屉开放层）

R9 新增。DrawerKit 开放层由 `oem-ime-sdk-core` 公开面提供：宿主可自定义内置抽屉（AI回复 / 素材库）的内容层（L1），也可注册自定义抽屉（L2）。三级能力模型：

| 层级 | 说明 | 启用方式 |
| --- | --- | --- |
| L0 | 默认UI（现状保留，零变化） | 功能勾选开关 |
| L1 | 内置抽屉自定义UI | 功能勾选 + `enableAiReplyCustomUi` / `enableMaterialLibraryCustomUi` + `setDrawerContentFactory` |
| L2 | 自定义抽屉 | `enableCustomDrawers=true` + 运行时 `registerCustomDrawer`，数量上限 4 个 |

边界约定：

- L1/L2 均不新增 license 鉴权；L1 能力字段调用仍受既有企业开关约束（事实状态）
- L2 默认关闭；未声明 `enableCustomDrawers=true` 时，注册表可保留，但自定义抽屉入口不会进入工具面板/工具条，主动拉起也不会生效
- 抽屉基座（容器、标准头部、开合动画、下拉手势、返回键分发）由 SDK 保留；基座头部完全复用现抽屉视觉（返回/关闭按钮位置不变），关闭按钮恒显
- 宿主自研内容属于宿主责任域，不纳入 SDK-R8 加密输入

### `OemDrawerKit` 常量

```kotlin
object OemDrawerKit {
    const val DRAWER_AI_REPLY = "ai_reply"
    const val DRAWER_MATERIAL_HUB = "material_hub"
    const val MAX_CUSTOM_DRAWERS = 4
}
```

- 内置抽屉 id 固定为 `ai_reply` / `material_hub`
- `MAX_CUSTOM_DRAWERS`：自定义抽屉数量上限（4 个），超出注册返回 `QUOTA_EXCEEDED`
- 自定义抽屉 id 由宿主定义，建议 `custom_<biz>` 格式，宿主内唯一

### `OemCustomDrawerSpec`

```kotlin
data class OemCustomDrawerSpec(
    val id: String,
    val title: String,
    val iconRes: Int = 0,
    val icon: Drawable? = null,
    val contentFactory: OemDrawerContentFactory,
    val entryHandler: OemCustomDrawerEntryHandler? = null
)
```

- `id`：自定义抽屉唯一标识
- `title`：工具面板（宫格页）与工具条显示名
- `iconRes` / `icon`：二选一，宿主侧 drawable 资源 id 或 Drawable/Bitmap；自定义图标默认原色渲染、不跟随内置 tint（宿主可自备 night 变体并经 `currentTheme()` 自适应）；未提供时宫格回退标题首字符、工具条使用占位图标
- `contentFactory`：内容层工厂

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

### `OemDrawerContentFactory`

```kotlin
interface OemDrawerContentFactory {
    fun onCreateContentView(context: Context, controller: OemDrawerController): View
    fun onCreateFullDrawerView(context: Context, controller: OemDrawerController): View? = null
    fun onDrawerStateChanged(state: OemDrawerState) {}
    fun onBackPressed(): Boolean = false
    fun onHostContentError(t: Throwable) {}
}
```

- `onCreateContentView`：基座创建内容层 View 时回调；抛出异常会被基座捕获（内置抽屉降级 L0 默认UI，自定义抽屉关闭抽屉），并经 `onHostContentError` 通知宿主。**该回调运行在主线程，只能做轻量根 View 创建与事件绑定；网络请求、读盘、位图解码、大列表构建必须异步执行，否则可能在自定义页首帧前触发 ANR**
- `onCreateFullDrawerView`：整块替换抽屉 View；返回非 `null` 时宿主单个 View 以 `MATCH_PARENT` 占满抽屉 root，SDK 不再创建默认顶栏、标题、关闭按钮和加载提示；返回 `null` 时保持旧版默认顶栏 + 内容槽行为
- `onBackPressed`：返回键分发时先询问内容层，返回 `true` 表示已消费，否则走基座返回/关闭
- `onDrawerStateChanged`：抽屉生命周期状态回调（opened / closed / back）

### `OemDrawerController`

```kotlin
interface OemDrawerController {
    fun closeDrawer()
    fun openDrawer(drawerId: String)
    fun setTitle(title: CharSequence?)
    fun setBackVisible(visible: Boolean)
    fun currentTheme(): OemDrawerTheme
}
```

- `closeDrawer` / `openDrawer`：主动关闭当前抽屉 / 打开指定 id 的抽屉
- `setTitle`：设置默认头部标题文本；当 `onCreateFullDrawerView` 返回非空 View 时默认头部不存在，该调用为兼容 no-op
- `setBackVisible`：配置默认返回按钮显隐（默认显示）；当 `onCreateFullDrawerView` 返回非空 View 时默认返回按钮不存在，该调用为兼容 no-op
- `currentTheme`：查询当前键盘深色/浅色主题；内容层深浅色由宿主自理

### `OemRegisterResult`

```kotlin
data class OemRegisterResult(
    val success: Boolean,
    val code: String,
    val message: String? = null
)
```

`code` 常见取值：

- `OK`：注册成功
- `QUOTA_EXCEEDED`：超出自定义抽屉数量上限（4 个）
- `DUPLICATED_ID`：id 已被占用
- `INVALID_SPEC`：参数非法（id/title 为空等）

### `OemImeSdk` DrawerKit 方法

```kotlin
object OemImeSdk {
    fun setDrawerContentFactory(drawerId: String, factory: OemDrawerContentFactory?)
    fun registerCustomDrawer(spec: OemCustomDrawerSpec): OemRegisterResult
    fun unregisterCustomDrawer(id: String)
    fun updateCustomDrawer(spec: OemCustomDrawerSpec)
    fun openDrawer(drawerId: String)
    fun closeDrawer(drawerId: String)
    fun aiReplyCapabilities(): OemAiReplyCapabilities
    fun materialCapabilities(): OemMaterialCapabilities
    fun isAiReplyCustomUiEnabled(): Boolean
    fun isMaterialLibraryCustomUiEnabled(): Boolean
}
```

- `setDrawerContentFactory`：为内置抽屉（`ai_reply` / `material_hub`）注册 L1 内容工厂；传 `null` 恢复 L0 默认UI。已注册工厂但对应自定义 UI 开关未开启时自动降级 L0，并输出包含 `drawerId` / 缺失开关名的 warn 日志
- `registerCustomDrawer`：运行时注册自定义抽屉（SDK 初始化之后、键盘首次展示之前）；仅当 `enableCustomDrawers=true` 时入口才会热刷新并追加到工具面板宫格与工具条「定制工具栏」体系（工具条维持 5 个上限，自定义项占配额，未满默认显示在末尾，已满进入隐藏池）
- `unregisterCustomDrawer`：注销自定义抽屉，入口即时移除并清理工具条持久化数据
- `updateCustomDrawer`：更新已注册抽屉的标题/图标/内容工厂
- `openDrawer` / `closeDrawer`：宿主主动开合指定抽屉
- `aiReplyCapabilities` / `materialCapabilities`：获取内置抽屉的能力字段 API（字段清单见下）
- `isAiReplyCustomUiEnabled` / `isMaterialLibraryCustomUiEnabled`：查询对应「自定义UI交互」开关的生效值（按「运行时覆盖 > OemSdkConfig > Manifest > 默认值」解析）；返回 `false` 时对应内置抽屉恒为 L0 默认UI

### `OemAiReplyCapabilities`

```kotlin
interface OemAiReplyCapabilities {
    fun startPasteSession(l: OemAiReplyListener)
    fun startFastGenerate(l: OemAiReplyListener)
    fun startScreenReply(l: OemAiReplyListener)
    fun requestScreenPermission(l: OemPermissionListener)
    fun openAlbumPicker(l: OemAlbumPickerListener)
    fun startImageReply(uris: List<Uri>, l: OemAiReplyListener)
    fun observeProgress(l: OemProgressListener)
    fun setTone(toneId: String)
    fun regenerate(l: OemAiReplyListener)
    fun footerText(): String? = null
    fun onFooterClick(): Boolean = false
    fun isRestricted(): Boolean
    fun sendReplyToInput(text: String): Boolean
}
```

- `footerText`：AI 回复内置面板底部文案；不配置时显示默认文案，返回 `null` / 空串时隐藏该文案区域
- `onFooterClick`：底部文案点击回调；返回 `true` 表示宿主已消费，默认 `false` 表示点击无行为
- `sendReplyToInput`：将一条 AI 回复文本直接发送到当前聊天输入框（关闭抽屉并走素材发送通道）；返回 `true` 表示已进入发送流程

### `OemMaterialCapabilities`

```kotlin
interface OemMaterialRefreshListener {
    fun onSuccess(count: Int)
    fun onError(message: String) {}
}

interface OemMaterialCapabilities {
    fun query(q: OemMaterialQuery, l: OemMaterialListener)
    fun queryMaterials(q: OemMaterialQuery, l: OemMaterialListener)
    fun observeFolders(l: OemFolderListener)
    fun sendToInput(item: OemMaterialItem): Boolean
    fun previewView(item: OemMaterialItem): View?
    fun openComposer(type: OemPickerType)
    fun stateFlags(): OemMaterialStateFlags
    fun queryRemoteMaterials(q: OemMaterialQuery, l: OemMaterialListener)
    fun refreshRemoteMaterials(l: OemMaterialRefreshListener)
    fun sendTextToInput(text: String): Boolean
}
```

- `queryRemoteMaterials`：查询企业远程素材目录（宿主回调下发 / 企业域名通道的本地缓存），与本地收藏相互独立；自定义抽屉可借此实现企业素材流
- `refreshRemoteMaterials`：主动刷新企业远程素材目录（触发宿主回调 / 域名通道同步），`onSuccess(count)` 回传刷新后目录条数
- `sendTextToInput`：将一段文本直接发送到当前聊天输入框（关闭抽屉并走素材发送通道），供「自定义功能 → 直接发送」闭环
- `sendToInput`：同时支持本地收藏项与企业远程素材项

```kotlin
data class OemMaterialFolder(
    val id: String,
    val title: String,
    val count: Int,
)

interface OemFolderListener {
    fun onFolders(folders: List<OemMaterialFolder>)
    fun onError(message: String) {}
}
```

- `query`：兼容旧调用；新接入优先使用 `queryMaterials`
- `queryMaterials`：按分类 / 文件子类 / 关键词 / 文件夹 / 页码分页查询素材
- `observeFolders`：读取素材库预设文件夹（宿主自定义 UI 可直接渲染分类/分组条）
- `previewView`：返回 SDK 预置素材详情 View，宿主可直接挂入自己的详情区；若返回 `null`，宿主需自行绘制详情页
- `openComposer`：按 `TEXT / IMAGE / FILE / VIDEO` 打开对应“添加素材”页
- `sendToInput`：执行“点击发送素材”的标准提交逻辑；宿主自定义按钮/列表项只负责调用它

### AI回复能力字段清单（12）

| 字段 id | 名称 |
| --- | --- |
| `ai_reply.entry.paste` | 点击复制生成回复 |
| `ai_reply.entry.fast` | 极速生成 |
| `ai_reply.entry.ocr` | 识屏回复 |
| `ai_reply.entry.image` | 传图回复 |
| `ai_reply.permission.screen` | 截屏权限申请 |
| `ai_reply.picker.album` | 相册选图 |
| `ai_reply.progress.timeline` | 生成进度时间轴 |
| `ai_reply.result.list` | 回复建议列表 |
| `ai_reply.result.tone` | 语气切换 |
| `ai_reply.result.regenerate` | 重新生成 |
| `ai_reply.meta.footer` | 模型声明文案 |
| `ai_reply.state.restricted` | 受限态 |

### 素材库能力字段清单（11）

| 字段 id | 名称 |
| --- | --- |
| `material.tabs.category` | 分类页签 |
| `material.tabs.file_sub` | 文档子分类 |
| `material.search` | 搜索 |
| `material.list` | 素材列表 |
| `material.folders` | 预设文件夹 |
| `material.action.send` | 发送素材 |
| `material.action.preview` | 附件预览 |
| `material.fab.add` | 悬浮添加 |
| `material.composer` | 创作器 |
| `material.remote.sync` | 远程素材同步 |
| `material.state.empty/login/restricted` | 状态位（空态/需登录/受限） |

## 包名

```kotlin
com.paopaoime.aikeyboard.sdk.ai
```

## 主要对象

### `AiKeyboardHostSdk`

职责：

- 初始化宿主桥接
- 初始化日志采集
- 生成扩散图输出路径
- 读取当前模型和已下载模型
- 拉起 SDK 设置页

主要方法：

```kotlin
object AiKeyboardHostSdk {
    fun initialize(application: Application)
    fun initializeLogCollector()
    fun generateDiffusionOutputPath(context: Context, fileNamePrefix: String): String
    fun getBackend(): AiKeyboardAiBackend
    fun createSettingsIntent(context: Context): Intent
    fun getCurrentModelId(): String?
    fun setCurrentModelId(modelId: String?)
    suspend fun loadDownloadedModels(context: Context): List<AiKeyboardDownloadedModel>
}
```

### `AiKeyboardAiSdk`

职责：

- 创建和释放 AI 会话
- 加载本地模型
- 查询当前全局会话
- 查询云模型清单并请求云回复流

主要方法：

```kotlin
object AiKeyboardAiSdk {
    fun createSession(
        modelId: String,
        displayName: String,
        sessionId: String?,
        configPath: String,
        supportOmni: Boolean = true
    ): AiKeyboardManagedSession

    fun release()
    fun getSession(): AiKeyboardChatSession?
    fun getCurrentModelId(): String?
    suspend fun loadModel(context: Context, modelId: String, displayName: String, configPath: String)
    fun loadCloudModels(context: Context): List<AiKeyboardCloudModel>
    suspend fun requestCloudReplyStream(
        model: AiKeyboardCloudModel,
        history: List<AiKeyboardCloudMessage>,
        onProgress: (String) -> Unit
    )
}
```

## 数据结构

### `AiKeyboardDownloadedModel`

- `modelId`：模型唯一标识
- `displayName`：显示名称
- `localPath`：本地模型目录
- `configPath`：模型配置文件路径
- `isDiffusion`：是否为扩散模型
- `isVisual`：是否支持视觉能力
- `tags`：主标签列表
- `extraTags`：扩展标签列表

### `AiKeyboardCloudModel`

- `modelId`：云模型 ID
- `displayName`：展示名称
- `isDecision`：是否为决策模型

### `AiKeyboardCloudMessage`

- `role`：消息角色
- `content`：消息文本

## 企业接口约定

### 输入质检上传

默认路径：

- `POST {apiBaseUrl}/api/v1/input-qa`

请求体示例：

```json
{
  "sessionId": "qa_demo_001",
  "packageName": "com.example.host",
  "appName": "企业输入法宿主",
  "qaContent": "用户输入的候选词与会话内容",
  "inputSource": "type",
  "policyState": "CLOUD_UPLOAD",
  "timestamp": 1784448000000
}
```

字段说明：

- `sessionId`：会话唯一标识
- `packageName`：宿主包名
- `appName`：宿主应用名
- `qaContent`：需留存的输入内容
- `inputSource`：输入来源，常用值为 `type`
- `policyState`：策略状态，常用值为 `CLOUD_UPLOAD`
- `timestamp`：客户端时间戳

说明补充：

- `channel` 目前是 SDK 初始化与 Manifest 配置维度，用于区分客户渠道/环境，不是当前云端输入质检接口的必填请求字段
- `CLOUD_ONLY / HOST_ONLY / DUAL_WRITE / LOCAL_ONLY` 是分发策略，不会改变云端协议字段结构；真正变化的是“是否上传云端”和“是否回调宿主”

### 素材库分类/标签宿主回调

素材库若开启 `HOST_ONLY / DUAL_WRITE`，并且企业希望在宿主后台管理“分类、标签”，可额外注册 `materialTaxonomyHostDelegate`。

第一期规则：

- 分类：支持抽屉内部的增删改查
- 标签：不作为导航，不直接显示，只作为搜索命中条件和后台管理对象
- 素材类型：始终由 SDK 内部固定控制，第三方企业不参与配置
- `materialNavigationMode` 支持 `TYPE_ONLY / TYPE_PRIMARY_CATEGORY_SECONDARY / CATEGORY_PRIMARY_TYPE_SECONDARY / CATEGORY_ONLY`
- 若宿主未返回任何分类，即使 `materialNavigationMode` 设为双层模式或仅分类模式，SDK 也自动回退为单行类型导航

注册方式：

```kotlin
OemImeSdk.initializeFromManifest(
    application = this,
    licenseProvider = YourLicenseProvider(),
    materialTaxonomyHostDelegate = EnterpriseMaterialTaxonomyDelegate(),
)
```

或初始化后运行时注册：

```kotlin
OemImeSdk.setMaterialTaxonomyHostDelegate(EnterpriseMaterialTaxonomyDelegate())
```

拉取协议：

```kotlin
override fun fetchMaterialTaxonomy(
    request: OemMaterialTaxonomyFetchRequest,
    callback: OemMaterialTaxonomyFetchCallback,
): Boolean {
    callback.onResult(
        OemMaterialTaxonomyFetchResult(
            requestId = request.requestId,
            success = true,
            categories = listOf(
                OemMaterialTaxonomyItem("cat_product", "商品详情", sort = 10),
                OemMaterialTaxonomyItem("cat_copy", "文案素材", sort = 20),
            ),
            tags = listOf(
                OemMaterialTaxonomyItem("tag_hot", "爆款", sort = 10),
                OemMaterialTaxonomyItem("tag_new", "上新", sort = 20),
            ),
        )
    )
    return true
}
```

变更协议：

```kotlin
override fun mutateMaterialTaxonomy(
    request: OemMaterialTaxonomyMutationRequest,
    callback: OemMaterialTaxonomyMutationCallback,
): Boolean {
    // 宿主按 action + target 自行转发到企业后台
    callback.onResult(
        OemMaterialTaxonomyMutationResult(
            requestId = request.requestId,
            success = true,
            item = request.item,
        )
    )
    return true
}
```

素材字段扩展：

- `categoryId`：素材所属分类 ID
- `categoryName`：素材所属分类名称
- `tags`：素材标签列表，SDK 搜索时会把标签作为命中条件

### 宿主回调事件对象

当客户把 `sessionArchiveMode` 配置为 `HOST_ONLY` 或 `DUAL_WRITE` 时，宿主在 `sessionArchiveListener` 中收到的不是上面的云端请求体，而是标准宿主事件对象 `OemSessionArchiveEvent`。

字段示例：

```kotlin
OemSessionArchiveEvent(
    eventId = "qa_demo_001",
    sessionId = "qa_demo_001",
    hostApplicationId = "com.example.host",
    targetPackageName = "com.tencent.mm",
    targetAppName = "微信",
    maskedContent = "用户输入的候选词与会话内容",
    inputSource = "type",
    policyState = "DUAL_WRITE",
    timestamp = 1784448000000
)
```

字段说明：

- `eventId`：宿主回调事件唯一标识，建议宿主拿它做幂等去重
- `sessionId`：输入质检会话 ID
- `hostApplicationId`：当前集成 SDK 的宿主包名
- `targetPackageName / targetAppName`：真实发生输入的外部第三方 App，而不是宿主 App 本身
- `maskedContent`：已脱敏的输入内容，宿主可按自己的合规要求继续落库或转发
- `inputSource`：输入来源
- `policyState`：当前分发策略，常见值为 `HOST_ONLY` 或 `DUAL_WRITE`
- `timestamp`：事件时间戳

### 自动化滑屏宿主回调（极速生成双写）

客户方不通过 ADB 执行自动化时，可注册滑屏宿主回调，由宿主使用自有自动化能力（无障碍等）执行"AI 回复-极速生成"的连续滑屏。SDK 将滑屏动作抽象为标准事件字段通知宿主，宿主执行完成后回写结果。

注册方式（二选一）：

```kotlin
OemImeSdk.setSwipeHostDelegate { request, callback ->
    // 返回 true 表示宿主接管本次滑屏
    hostAutomation.performSwipe(request) { success, error ->
        callback.onResult(OemSwipeResult(request.requestId, success, error))
    }
    true
}
```

或在 `OemSdkConfig` 中传入 `swipeHostDelegate`。

分发模式 Manifest key：`com.paopaoime.oem.swipeDispatchMode`，可选值 `SDK_ONLY` / `HOST_ONLY` / `DUAL_WRITE`（默认 `DUAL_WRITE`：宿主优先接管，宿主未注册、拒绝或超时未回写时回落 SDK 自身 `input swipe` 通道）。

等待宿主回写结果的超时默认 15 秒（`OemImeSdk.DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS = 15_000L`）。宿主无障碍手势链路（含 ContentObserver 跨进程回传）通常耗时 8-12 秒，如宿主侧链路更慢，可在初始化时调大：

```kotlin
// 单位毫秒，最小值 1_000（MIN_SWIPE_HOST_RESULT_TIMEOUT_MS），小于最小值会被钳制
OemImeSdk.setSwipeHostResultTimeoutMillis(20_000L)
// 读取当前生效值
val timeoutMs = OemImeSdk.getSwipeHostResultTimeoutMillis()
```

`OemSwipeRequest` 字段：

| 字段 | 说明 |
|---|---|
| `requestId` | 本次滑屏唯一标识，回写结果时原样携带 |
| `scene` | 场景标识，极速生成为 `AI_FAST_GENERATE` |
| `direction` | 手势方向，`DOWNWARD_DRAG`（向下拖动=内容上翻） |
| `startX/startY/endX/endY` | 起止坐标，语义同 `adb shell input swipe` |
| `screenWidth/screenHeight` | 屏幕尺寸，宿主可换算相对比例 |
| `durationMs` | 建议手势时长 |
| `timestamp` | 事件时间戳 |

`OemSwipeResult` 字段：`requestId` / `success` / `errorMessage`。宿主接管后必须调用一次 `callback.onResult`，否则 SDK 在超时（默认 15 秒，可通过 `setSwipeHostResultTimeoutMillis` 调整）后回落自身通道。

### 宿主截图回调（OemScreenshotProvider）

宿主已具备自有截屏能力（无障碍全局截图、系统级截屏服务等）时，可注册截图回调，由 SDK 在"AI 回复-识屏回复 / 极速生成"需要屏幕画面时同步调用宿主取图，替代 SDK 内置的自动化截屏与手动截屏授权链路。

接口定义：

```kotlin
fun interface OemScreenshotProvider {
    // 返回当前屏幕位图；无法取图时返回 null
    fun captureScreen(context: Context): Bitmap?
}
```

注册与注销：

```kotlin
// 注册（传 null 注销，恢复内置截屏链路）
OemImeSdk.setScreenshotProvider { context ->
    hostScreenshotService.capture(context) // 宿主自有截屏实现
}
// 读取当前注册的 provider（未注册返回 null）
val provider = OemImeSdk.getScreenshotProvider()
// 快捷判断是否已注册
if (OemImeSdk.isScreenshotProviderRegistered()) { /* 宿主取图模式 */ }
```

语义约定（与内置链路的差异）：

- **同步调用、不设超时**：`captureScreen` 在 SDK 截屏协程中同步执行，SDK 不包裹任何超时限制，宿主侧耗时多久都会等待，避免无障碍慢速截屏被误判超时
- **注册后直接视为已授权**：截屏权限校验方法（`isScreenCapturePermissionGranted` 等）在检测到已注册 provider 时直接返回 `true`，宿主无需再引导用户做手动截屏授权
- **不降级**：`captureScreen` 返回 `null` 时，SDK 直接按截屏失败处理（失败态状态码 `CAPTURE_FAILED`），**不会**回退到自动化截屏、手动截屏授权或历史缓存截图
- **全链路接管**：识屏回复单帧截图、极速生成连续滑屏取图、预热截屏均走该 provider

建议宿主在 `captureScreen` 内部做好自身异常兜底（捕获后返回 `null` 或抛出），避免崩溃传播到输入法进程。

### AI 回复失败状态码与宿主自定义

AI 回复生成失败时，SDK 不再向用户展示底层错误原文，而是统一展示失败卡片：

- 默认标题：`这次生成没有成功`
- 默认描述：`请点击下方按钮重新生成`
- 失败态下点击语气或「重新生成」会丢弃上一轮上下文，从截屏开始完整重走一遍生成流程
- 「识屏回复 / 极速生成」检测到自动化截图不可用且没有手动截屏授权时，会自动引导用户进入手动截屏授权页，不再因自动化权限问题直接进入失败态

#### 状态码清单（`OemAiReplyErrorCode`）

| 状态码 | 含义 |
|---|---|
| `NETWORK` | 断网 / DNS 解析失败 / 连接失败等网络异常 |
| `TIMEOUT` | 生成整体超时或宿主回调超时 |
| `MODEL_BUSY` | 大模型返回失败（限流、服务繁忙等） |
| `CONTENT_BLOCKED` | 命中大模型内容质检 / 审核拦截 |
| `RESULT_INVALID` | 模型返回为空或解析不出有效回复 |
| `CONFIG_MISSING` | 企业配置缺失、SDK 未初始化或宿主回调未注册 |
| `CAPTURE_FAILED` | 截屏 / 图片读取失败 |
| `UNKNOWN` | 其他未分类异常 |

#### 宿主回调模式回传状态码

`OemAiReplyResult` 新增可选字段 `errorCode`。宿主在 `success = false` 时可携带上表任一状态码，SDK 会按该状态码选择失败文案；未携带时 SDK 根据 `errorMessage` 自动分类。

```kotlin
callback.onResult(
    OemAiReplyResult(
        requestId = request.requestId,
        success = false,
        errorMessage = "命中内容审核",
        errorCode = OemAiReplyErrorCode.CONTENT_BLOCKED,
    )
)
```

#### 失败文案宿主自定义（双写）

- 开关：Manifest key `com.paopaoime.oem.aiReplyErrorTextCustomEnabled`，或运行时 `OemImeSdk.setAiReplyErrorTextCustomEnabled(true)`；默认 `false`（使用 SDK 默认文案）
- 文案表：`OemSdkConfig.aiReplyErrorTexts` 或运行时 `OemImeSdk.setAiReplyErrorTexts(map)`，key 为状态码，value 为 `OemAiReplyErrorText(title, description)`
- `title` 必填，`description` 可选填；留空项回落到 SDK 默认文案

```kotlin
OemImeSdk.setAiReplyErrorTextCustomEnabled(true)
OemImeSdk.setAiReplyErrorTexts(
    mapOf(
        OemAiReplyErrorCode.TIMEOUT to OemAiReplyErrorText(
            title = "本次生成超时",
            description = "请检查网络后重新生成",
        ),
        OemAiReplyErrorCode.CONTENT_BLOCKED to OemAiReplyErrorText(
            title = "内容暂不支持生成",
        ),
    )
)
```

#### 超时配置（默认 30 秒）

| 配置项 | Manifest key | 运行时接口 | 默认值 |
|---|---|---|---|
| 生成整体超时 | `com.paopaoime.oem.aiReplyGenerationTimeoutMs` | `OemImeSdk.setAiReplyGenerationTimeoutMs(ms)` | 30000ms |
| 域名通道网络超时 | `com.paopaoime.oem.aiReplyNetworkTimeoutMs` | `OemImeSdk.setAiReplyNetworkTimeoutMs(ms)` | 30000ms |
| 滑屏宿主回写超时 | 无（仅运行时配置） | `OemImeSdk.setSwipeHostResultTimeoutMillis(ms)` | 15000ms |

解析优先级：SDK 运行时设置 > Manifest meta-data > 默认值。宿主回调模式下，生成整体超时同时约束 SDK 等待宿主回写的最长时间。

### 输入质检演示接口

官网演示页面：

- `https://www.paopaoime.com/oem/API/`

OEM 真机联调上传接口：

- `POST https://www.paopaoime.com/api/v1/oem/input-qa`

演示写入接口：

- `POST https://www.paopaoime.com/api/v1/oem/input-qa-demo/sessions`

演示列表接口：

- `GET https://www.paopaoime.com/api/v1/oem/input-qa-demo/sessions`

演示单条详情接口：

- `GET https://www.paopaoime.com/api/v1/oem/input-qa-demo/sessions/{sessionId}`

说明：

- `POST /api/v1/oem/input-qa` 与官网演示存储已打通，OEM 真机输入上屏后会按同一字段结构写入该列表
- 官网 API 页面点击“刷新会话”默认读取最近 `10` 条输入会话，无需整页刷新
- 列表默认展示 `qaContent`、宿主（`appName` 优先，否则回退 `packageName`）和 `timestamp`
- 当前对外演示宿主默认固定使用 `https://www.paopaoime.com` 作为联调 `apiBaseUrl`
- 演示接口会将请求体写入独立 JSON 存储，便于在线查看结构
- 这组接口仅用于联调示例，不会写入正式业务质检表
- 企业正式环境只需要复用 `POST {apiBaseUrl}/api/v1/input-qa` 的字段协议

模式联调建议：

- `CLOUD_ONLY`：使用 `POST {apiBaseUrl}/api/v1/input-qa` 或真机真实输入后，只需在官网 API 页确认最近 `10` 条会话列表有新增记录
- `HOST_ONLY`：官网 API 页不作为验收依据，应转到宿主自身的会话接收页、日志或落库表确认回调结果
- `DUAL_WRITE`：官网 API 页与宿主接收页都应能看到同一时段的新记录，最适合作为首次联调模式
- `LOCAL_ONLY`：官网 API 页不会出现新记录，此模式仅用于本地排障或关闭外部流转

### 素材库标准对接协议

说明：

- 素材库本地添加能力继续保留，企业服务端管理能力通过标准 HTTP 协议补充
- 我方输出的是“标准版对接能力”，客户可以在自己的 `apiBaseUrl` 下按相同字段协议实现，也可以在网关层做路径映射
- 素材既支持单个 `text / image / document / video`，也支持以上类型按顺序编组；顺序以 `parts` 数组为准
- Android OEM 客户端当前已改为“无感后台同步”：用户每次唤起输入法时，都会在后台静默检查并下载最近 `10` 条远端素材
- 素材库抽屉首页不再展示独立 `下载` 按钮；只有已经后台下载完成的素材才会进入可发送列表
- 后台低频任务默认每小时检查一次远端素材元数据，同时在输入法保活周期内按需补齐附件落盘

推荐标准路径：

- `POST {apiBaseUrl}/api/v1/material-library/assets:upload`
- `GET {apiBaseUrl}/api/v1/material-library/assets`
- `GET {apiBaseUrl}/api/v1/material-library/assets/{assetId}`
- `POST {apiBaseUrl}/api/v1/material-library/materials`
- `GET {apiBaseUrl}/api/v1/material-library/materials`
- `GET {apiBaseUrl}/api/v1/material-library/materials/{materialId}`
- `PUT {apiBaseUrl}/api/v1/material-library/materials/{materialId}`
- `DELETE {apiBaseUrl}/api/v1/material-library/materials/{materialId}`

字段建议：

- `title`：素材标题
- `description`：素材说明
- `customerId`：企业标识，可选
- `channel`：渠道标识，可选
- `tags`：标签数组，可选
- `parts`：有序素材片段数组，至少 1 个元素

`parts` 结构：

```json
[
  {
    "partId": "part_text_001",
    "type": "text",
    "text": "单个文案素材"
  },
  {
    "partId": "part_attachment_001",
    "type": "attachment",
    "assetId": "asset_demo_001",
    "displayName": "产品手册.pdf",
    "mimeType": "application/pdf",
    "attachmentType": "document",
    "fileSizeBytes": 24576,
    "downloadUrl": "https://api.example.com/files/asset_demo_001"
  }
]
```

说明：

- `type=text` 时使用 `text`
- `type=attachment` 时使用 `assetId / displayName / mimeType / attachmentType / fileSizeBytes / downloadUrl`
- `attachmentType` 推荐固定为 `document / image / video`
- 单个图片、单个文档、单个视频，本质上都属于只包含 1 个 attachment part 的素材
- 多类型编组素材则直接在 `parts` 中按展示顺序拼装多个 text/attachment part

创建素材示例：

```json
{
  "materialId": "material_group_001",
  "title": "售前资料编组",
  "description": "先发欢迎话术，再发产品图和产品手册",
  "customerId": "customer_demo",
  "channel": "release",
  "tags": ["销售", "首触达"],
  "parts": [
    {
      "partId": "part_text_001",
      "type": "text",
      "text": "您好，这里是泡泡输入法 OEM 方案资料。"
    },
    {
      "partId": "part_image_001",
      "type": "attachment",
      "assetId": "asset_image_001",
      "displayName": "oem-cover.png",
      "mimeType": "image/png",
      "attachmentType": "image",
      "fileSizeBytes": 182736,
      "downloadUrl": "https://api.example.com/files/asset_image_001"
    },
    {
      "partId": "part_doc_001",
      "type": "attachment",
      "assetId": "asset_doc_001",
      "displayName": "oem-intro.pdf",
      "mimeType": "application/pdf",
      "attachmentType": "document",
      "fileSizeBytes": 548120,
      "downloadUrl": "https://api.example.com/files/asset_doc_001"
    }
  ]
}
```

响应建议：

- 创建/更新成功时返回完整素材对象
- 列表接口返回 `items / total / limit`
- 删除接口返回 `deleted=true`
- 响应包络建议继续沿用 `{ success, requestId, data, error }`

官网演示接口：

- `POST https://www.paopaoime.com/api/v1/oem/material-library-demo/materials`
- `GET https://www.paopaoime.com/api/v1/oem/material-library-demo/materials`
- `GET https://www.paopaoime.com/api/v1/oem/material-library-demo/materials/{materialId}`
- `PUT https://www.paopaoime.com/api/v1/oem/material-library-demo/materials/{materialId}`
- `DELETE https://www.paopaoime.com/api/v1/oem/material-library-demo/materials/{materialId}`

说明：

- `https://www.paopaoime.com` 这一组演示接口可以视为“模拟第三方企业服务端”，用于官网联调展示
- 官网 API 频道只保留一个轻量 CRUD 演示区，默认查看并维护最近 `10` 条素材
- 演示后端当前已经切到 ECS MySQL 持久化，素材主记录、片段顺序和附件元数据都会写入数据库
- 附件二进制文件当前仍落在 `core-service/data/oem-material-library-demo/assets`，数据库中保存附件元数据与下载路径
- 官网 API 频道现已提供附件上传入口，可先上传图片 / 视频 / 文档，再把返回的附件元数据插入当前素材并直接发布
- Android OEM 客户端会先消费这组接口返回的元数据，再在输入法激活后的后台任务里根据 `downloadUrl` 静默补齐附件；只有下载完成的素材才进入抽屉可发送列表
- 客户正式环境只需要在自己的 `apiBaseUrl` 下复用相同字段协议即可
  

### AI 回复代理接口

代理模式下，SDK 当前使用的核心路径如下：

- `GET {apiBaseUrl}/api/v1/assets/model-settings`
- `POST {apiBaseUrl}/api/v1/ai-communicate/records`
- `POST {apiBaseUrl}/api/v1/ai-communicate/tasks/report`
- `POST {apiBaseUrl}/api/v1/ai-communicate/records/{sessionId}/selection`

这组接口用于 AI 模型运行时配置、AI 回复任务日志、结果上报与用户选择回传。企业可按相同路径协议自行实现，或在网关层做转发。

推荐职责划分：

- `model-settings`：返回当前企业允许 SDK 使用的模型运行时配置
- `records`：记录一次 AI 回复任务请求及原始上下文
- `tasks/report`：回传阶段进度、耗时和结果摘要
- `selection`：记录用户最终选中了哪一条回复

#### 模型配置字段与配置位置

手机开发者页调试入口：

- `输入法设置 -> 开发者 -> AI模型配置`
- `输入法设置 -> 开发者 -> AI回复模块`

官网只说明字段与配置位置，不在官网页面录入密钥。正式环境建议由宿主配置中心、`Manifest` 或服务端下发体系管理。

##### AI模型配置

| 字段 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `apiBaseUrl` | 选填；`proxy / dual` 推荐填写 | 宿主 `Manifest` 或内置默认域名 | 企业代理域名 |
| `aiOpenAiBaseUrl` | `direct / dual` 下必填 | 无 | 直连 OpenAI 兼容接口基础地址 |
| `aiOpenAiApiKey` | `direct / dual` 下必填 | 无 | 直连 OpenAI 兼容接口密钥 |
| `aiOpenAiModel` | `direct / dual` 下必填 | 无 | 直连模型名称 |
| `temperature` | 选填 | `0.0` | AI 回复采样温度 |
| `topP` | 选填 | `0.85` | AI 回复 nucleus sampling 参数 |
| `maxTokens` | 选填 | `1100` | AI 回复最大输出 token 数 |

##### AI回复模块

| 字段 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `systemPrompt` | 选填 | 自动按默认业务角色生成 | 留空时使用系统默认提示词 |
| `tonePresets` | 选填 | 内置默认语气列表 | 支持增删改查；未配置时继续使用默认语气 |
| `replyCount` | 选填 | `10` | 取值范围 `1-20` |
| `replyLengthPolicy` | 选填 | `short_reply` | 回复长度策略标识 |
| `businessRole` | 选填 | `IM 沟通回复助手` | 当前 AI 回复业务角色 |
| `minChars` | 选填 | `6` | 单条回复最小字符数 |
| `maxChars` | 选填 | `22` | 单条回复最大字符数 |
| `allowEmoji` | 选填 | `true` | 是否允许少量自然表情 |
| `avoidNickname` | 选填 | `true` | 是否尽量避免直接称呼客户昵称 |
| `language` | 选填 | `zh-CN` | 回复输出语言标识 |

##### tonePresets 子字段

| 字段 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 必填 | 无 | 语气名称 |
| `promptHint` | 必填 | 无 | 当前语气的提示词描述 |

#### `GET /api/v1/assets/model-settings`

用途：

- 仅在 `aiMode=proxy` 或 `aiMode=dual` 时使用
- 用于返回企业当前允许 SDK 使用的可用模型配置

建议返回结构示例：

```json
{
  "success": true,
  "requestId": "req_demo_001",
  "data": {
    "draft": {
      "decision": {
        "modelName": "qwen3.6-flash",
        "baseUrl": "https://your-openai-compatible-host/v1",
        "apiKey": "your_api_key"
      },
      "decisionModels": [
        {
          "modelName": "qwen3.6-flash",
          "baseUrl": "https://your-openai-compatible-host/v1",
          "apiKey": "your_api_key"
        }
      ]
    }
  }
}
```

返回要求：

- 至少返回一组可用的 `modelName / baseUrl / apiKey`
- 若企业有多模型策略，可在 `decisionModels` 中返回候选列表
- 包络建议继续沿用 `{ success, requestId, data, error }`

### 自动化接口

当交付自动化套餐时，OEM 当前保留以下代理接口：

- `POST {apiBaseUrl}/api/v1/midscene/run`
- `POST {apiBaseUrl}/api/v1/midscene/tasks/report`

如果企业不需要自动化能力，不打开 `enableAutomation` 即可。

## 初始化示例

初始化示例中的 `customerId`、`appKey`、`channel` 请按我方交付值填写；`licenseProvider` 按标准模板接入，如我方另行交付专用实现则替换。

```kotlin
class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OemImeSdk.initialize(
            application = this,
            config = OemSdkConfig(
                // 必填：填写我方交付的企业码
                customerId = "your_customer_id",
                // 必填：当前与企业码同值，直接填写同一企业码
                appKey = "your_customer_id",
                channel = "release",
                environment = OemEnvironment.PROD,
                // 预留接口，当前示例返回演示 License
                licenseProvider = OemLicenseProvider { _, request ->
                    OemLicenseSnapshot.active(
                        licenseId = "demo",
                        grantedCapabilities = request.requestedCapabilities
                    )
                },
                enableAi = true,
                enableAutomation = true,
                enableAutomationSettingsEntry = false,
                enableUi = true,
            )
        )
    }
}
```

## 模型列表示例

```kotlin
lifecycleScope.launch {
    val models = AiKeyboardHostSdk.loadDownloadedModels(this@MainActivity)
    models.forEach { model ->
        Log.d("OEM", "model=${model.modelId}, path=${model.localPath}")
    }
}
```

## 兼容性说明

- 最低 `minSdk`：26
- 推荐只在 `arm64-v8a` 设备上集成和验证
- 当前 OEM 版已支持代理 AI 与直连 OpenAI 兼容模型双模式并存
- 自动化套餐在当前版本下已验证可通过内嵌“输入法自动化引擎”实现宿主授权与状态回传

## 当前限制

- 当前文档默认覆盖 `AI Runtime SDK + Core SDK`，自动化要求只在明确交付对应套餐时生效
- 当前完整输入法壳、设置容器和自动化管理端页面本身不属于稳定公开 API
- 具体授权范围以交付合同和版本说明为准

## 离线语音输入（可选 SDK，集成后默认开启）

自 SDK `1.0.0.20261008` / 输入法运行时 `app:1.9` 起，语音独立为可选 `oem-ime-sdk-voice`，提供官方 sherpa-onnx 1.13.8 离线流式识别和自动标点；不依赖 AI 回复、自动化或云端大模型。


### 按需集成语音 SDK

官网模块选择器勾选“离线语音”（`voice`），会加入语音 SDK 和 sherpa 框架；未勾选时基础输入法不依赖 sherpa，语音实现、JNI、设置页和麦克风权限不进入宿主，全部语音入口自动隐藏。

```groovy
// 在基础输入法 core + host-bridge 的依赖之外按需添加：
implementation "com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261010"
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

### 临时剪贴板高情商回复（1.0.0.20261012）

当前聊天宿主的新复制事件仅展示一次；关闭、开始打字或展示后收起键盘都会消费，切换宿主清除。快捷入口直接生成结果，使用标准 AI 回复的客户模型/域名/直连、分发回调、提示词、语气、超时和错误文案配置，无需新增模型配置文件。关闭 AI 回复或缺少 material/ai-reply 切片时不显示高情商回复入口。

客户已有的 `DRAWER_AI_REPLY` 工厂同样承载快捷回复，`onCreateContentView`（只替换内容）与 `onCreateFullDrawerView`（替换整个抽屉）均支持。普通 AI 回复入口的 `clipboardReplyState()` 返回 null；快捷入口会自动开始生成，客户只需渲染状态，不能在 View 创建时再次调用 `startPasteSession` 造成重复请求。

```kotlin
// package com.paopaoime.aikeyboard.sdk.core.drawer
// 注册前继续使用既有 enableAiReplyCustomUi=true 开关。
OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_AI_REPLY,
    object : OemDrawerContentFactory {
        private var controller: OemDrawerController? = null
        private var content: LinearLayout? = null
        override fun onCreateContentView(context: Context, controller: OemDrawerController): View {
            this.controller = controller
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                content = this
                if (controller.clipboardReplyState() == null) {
                    // 在这里显示客户已有的普通 AI 回复入口。
                }
            }
        }
        override fun onClipboardReplyStateChanged(state: OemClipboardReplyState) {
            val root = content ?: return
            root.removeAllViews()
            root.addView(TextView(root.context).apply {
                text = when {
                    state.loading -> "正在生成回复…"
                    state.errorTitle != null -> state.errorTitle + "\n" + state.errorDescription.orEmpty()
                    else -> "选择一条回复"
                }
            })
            state.suggestions.forEach { reply ->
                root.addView(Button(root.context).apply {
                    text = reply
                    setOnClickListener { controller?.insertClipboardReply(reply) }
                })
            }
            if (!state.loading) root.addView(Button(root.context).apply {
                text = "重新生成"
                setOnClickListener { controller?.regenerateClipboardReply() }
            })
        }
        override fun onDrawerStateChanged(state: OemDrawerState) {
            if (state == OemDrawerState.CLOSED) { content = null; controller = null }
        }
    })
```

以上为最小内容区示例，展示方式可完全由客户设计。整块替换时在 `onCreateFullDrawerView` 返回自有根 View，并绑定同样的状态回调；关闭按钮由客户调用 `controller.closeDrawer()`。所有 View 创建与状态回调均在主线程，禁止在其中同步执行网络请求。

`OemClipboardReplyState` 包含 `sourceText`、`loading`、`suggestions`、`modelName`、`errorCode`、`errorTitle` 和 `errorDescription`。工厂 View 创建完成后会收到当前状态，此后每次加载、成功或失败都会通知；错误标题/描述沿用客户既有配置。`regenerateClipboardReply()` 使用本次捕获的原文，不重新读取全局剪贴板。关闭抽屉取消该会话。

`insertClipboardReply(text)` 返回 true 表示已受理回填，不代表宿主已接收；恢复失败会提示用户。它只向原宿主输入框写入草稿，绝不点击发送。已有 `aiReplyCapabilities().sendReplyToInput(text)` 在快捷会话内也使用相同草稿回填语义；普通 AI 回复和素材发送仍保留其既有行为。

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
