# 外部版 SDK 集成手册

> 2026-10-12：新增自定义抽屉 entryHandler 和剪贴板 OemClipboardQuickReplyHandler，支持宿主完全接管入口：主线程回调、忽略返回值、异常静默，不收键盘或启动 SDK 页面。OEM 剪贴板去掉标题与叉号，按钮改“智能回复”，工具条加高 12 布局单位。未注册保持原 SDK 流程；八公开 SDK Release/R8，runtime app:1.13。 SDK `1.0.0.20261012`。

本文档只说明第三方宿主如何集成 SDK，不展开介绍内部架构。

## 修改记录

| 日期 | SDK 版本 | 修改说明 |
| --- | --- | --- |
| 2026-10-12 | `1.0.0.20261012` | 新增自定义抽屉 entryHandler 和剪贴板 OemClipboardQuickReplyHandler，支持宿主完全接管入口：主线程回调、忽略返回值、异常静默，不收键盘或启动 SDK 页面。OEM 剪贴板去掉标题与叉号，按钮改“智能回复”，工具条加高 12 布局单位。未注册保持原 SDK 流程；八公开 SDK Release/R8，runtime app:1.13。 |
| 2026-10-11 | `1.0.0.202610111` | 同一宿主复制后仅展示一次临时剪贴板和高情商回复；关闭、打字或离开宿主后不重现。快捷回复直接打开结果抽屉，选中仅回填草稿。复用客户 AI 回复模型配置，支持客户自绘内容及整块抽屉、状态回调和原文重试。八公开 SDK Release/R8，运行时 app:1.12。 |
| 2026-10-11 | `1.0.0.20261011` | 复制文本不再自动替换工具栏，包含键盘显示中、重新唤起及旧设置为开启的场景；保留手动剪贴板、历史和粘贴。移除无效的工具栏剪贴板建议/超时设置。八个公开 SDK 全量重建并执行 Release/R8，输入法运行时升级至 app:1.11。 |
| 2026-10-10 | `1.0.0.20261010` | 日光瓷白与夜幕石墨统一键盘比例、候选高亮留白与文字、按键气泡、键盘类型图标和分页工具面板；工具栏与工具面板不提供皮肤入口。八个公开 SDK 重新制作并执行 Release/R8，输入法运行时升级至 app:1.10。 |
| 2026-10-08 | `1.0.0.20261008` | sherpa-onnx 1.13.8 三档离线语音、默认开启的宿主配置和下载镜像；工具栏图标与面板配色/布局对齐。 |
| 2026-09-24 | `1.0.0.20260923` | Android 17 兼容预适配：SDK 不再放开宿主全局明文流量，移除旧调试 HTTP 上报；输入法运行时升至 `app:1.8`，保持 `targetSdk 36`。 |

## 当前版本说明

- 当前记录时间：`2026-10-12`
- 当前交付包已经包含：输入法宿主桥接、AI 相关运行时、输入法资源运行时，以及可选的 `ADB自动化` 运行时
- 当前版本已经完成：标准 SDK 压缩包导出、宿主接入步骤、自动化授权步骤、输入法候选词修复、自动化同包默认授权修复、宿主截图回调（OemScreenshotProvider）、滑屏宿主回调超时可配置
- 当前构建要求：八个公开 `AAR` 执行 Release/R8；JNI 与公开兼容接口按既有规则保留，不将 R8 等同于所有代码均被重命名

## 步骤一：获取交付包

- [下载最新SDK压缩包](https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip)

交付目录：

```text
oem-sdk-delivery/
  repo/
    com/paopaoime/oem/oem-ime-sdk-ai/1.0.0.20261012/
      oem-ime-sdk-ai-1.0.0.20261012.aar
      oem-ime-sdk-ai-1.0.0.20261012.pom
      oem-ime-sdk-ai-1.0.0.20261012.module
    com/paopaoime/oem/oem-ime-sdk-core/1.0.0.20261012/
      oem-ime-sdk-core-1.0.0.20261012.aar
      oem-ime-sdk-core-1.0.0.20261012.pom
      oem-ime-sdk-core-1.0.0.20261012.module
    com/paopaoime/oem/oem-ime-host-bridge/1.0.0.20261012/
      oem-ime-host-bridge-1.0.0.20261012.aar
      oem-ime-host-bridge-1.0.0.20261012.pom
      oem-ime-host-bridge-1.0.0.20261012.module
    internal/
      自动化引擎相关内部制品（已随包提供）
    org/fcitx/fcitx5/android/app/1.13/
      app-1.13.aar
      app-1.13.pom
  docs/
    index.html
    automation.html
    外部版-SDK集成手册.md
  DELIVERY_README.txt
```

> 版本说明：自 `1.0.0.20260816` 起 SDK 采用统一版本号，`com.paopaoime.oem` 组下全部制品（core / host-bridge / ai / ai-reply / material / automation / developer）版本一致；fcitx 内核（`org.fcitx.fcitx5.android:app`）与 stellar 等内部组件使用独立坐标，不随 SDK 版本变化。

## 步骤二：接入仓库和依赖

1. 下载 SDK 并解压到企业 Android 项目工程根目录下的 `oem-sdk/` 目录
2. 宿主 App 按下面示例接入仓库、依赖、企业 API 域名和 AI 模式相关配置
3. 当前接入基线：`JDK 17`、`Gradle 9.4.1`、`Android Gradle Plugin 9.2.0`、`minSdk 26`、`compileSdk 36`

`settings.gradle`：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri(rootDir.resolve("oem-sdk/repo")) }
    }
}
```

`app/build.gradle`：

```gradle
dependencies {
    implementation "com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20261012"
}
```

### 可选功能模块（2026-08-04 起）

输入法基础能力（必选）只需 `oem-ime-host-bridge`（自动传递 `oem-ime-sdk-core`）。以下模块可按需勾选追加（全部与 SDK 统一版本号一致）：

```gradle
dependencies {
    // AI 回复（依赖 core；未集成自动化模块时自动降级为手动截图）
    implementation "com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.20261012"
    implementation "com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.20261012"
    implementation "com.paopaoime.oem.internal:paddleocr4android:1.0.0"

    // 素材库（依赖 core + ai-reply）
    implementation "com.paopaoime.oem:oem-ime-sdk-material:1.0.0.20261012"

    // Stellar 自动化（可选增强；AI回复检测到该模块后自动启用自动化截屏）
    implementation "com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.20261012"
    implementation "com.paopaoime.oem.internal.stellar:stellar-manager:1.1"

    // 开发者工具（可选）
    implementation "com.paopaoime.oem:oem-ime-sdk-developer:1.0.0.20261012"
}
```

降级行为约定：

- 未集成 `oem-ime-sdk-automation` 时，AI回复仍可工作：自动截图链路降级为手动截图会话，界面提示「当前未集成自动化模块，需要安装后使用」，不会崩溃。
- 未集成 `oem-ime-sdk-developer` 时，AI 语气预设使用内置默认集。
- 完整勾选口径以交付包内 `oem-sdk-manifest.json` 与 `OEM/SDK/catalog/oem-sdk-catalog.json` 为准。

`app/src/main/res/values/oem_ime_config.xml`：

```xml
<resources>
    <string name="oem_ime_display_name">XX企业输入法</string>
</resources>
```

输入法名称配置说明：

- `oem_ime_display_name` 是 OEM 输入法统一显示名配置项
- 该字段**不走 Manifest meta-data**，因为系统输入法名称来自静态资源和 `input-method xml`
- 该字段会同时影响：
  - 系统“语言和输入法”中的输入法名称
  - 输入法启用/切换引导提示中的名称
  - 输入法设置首页标题、启动页标题等统一品牌入口
- 推荐直接把该字段写成最终显示名称，例如 `XX企业输入法`，不要再额外引入 `company_app_name` / `app_name` 这类中间别名
- 默认情况下只在 `res/values/oem_ime_config.xml` 保留这一份定义即可；如果没有明确的多语言差异，不要额外创建 `values-zh/oem_ime_config.xml`
- 如果企业确实使用 `productFlavor.resValue`、`buildConfig` 注入或宿主自己的别名字符串来驱动品牌名，必须保证默认资源与 `values-zh` 中不存在同名静态实体值；否则 release 阶段容易触发 `ExtraTranslation` / 重复资源类问题
- AI 一键安装与正式交付都必须至少跑一次 `assembleRelease`，不能只验证 debug；品牌资源、Manifest merge、lintVital 冲突通常只会在 release 暴露

`AndroidManifest.xml`：

> 功能默认策略：除「基础 + 输入法核心」外，其余功能（AI 回复 / 素材库 / 自动化 / 开发者）默认全部关闭。以下模板为全功能开通示例，实际集成时只保留企业已开通功能对应的开关为 `true`，未开通的保持 `false` 或直接不声明。

```xml
<application>
    <!-- 企业后端基础域名：AI 代理、素材库同步、输入质检云端上传统一走这里；禁止回退到 paopaoime 域名 -->
    <meta-data
        android:name="com.paopaoime.oem.apiBaseUrl"
        android:value="https://api.enterprise.example.com" />
    <!-- AI 运行模式：proxy=企业代理；direct=SDK 直连；dual=双模式兜底 -->
    <meta-data
        android:name="com.paopaoime.oem.aiMode"
        android:value="dual" />
    <!-- UI 总开关（基础能力，默认开启）：关闭后输入法设置页、素材库等界面能力不会对宿主开放 -->
    <meta-data
        android:name="com.paopaoime.oem.enableUi"
        android:value="true" />
    <!-- AI 能力总开关：默认关闭，开通 AI 套餐后设为 true -->
    <meta-data
        android:name="com.paopaoime.oem.enableAi"
        android:value="true" />
    <!-- 细粒度功能开关：是否启用 AI 回复（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableAiReply"
        android:value="true" />
    <!-- SDK 内置 OCR 总开关：默认 true；低配机或宿主自管 OCR 时可设为 false，SDK 将跳过 OCR 初始化与识别 -->
    <meta-data
        android:name="com.paopaoime.oem.ocrEnabled"
        android:value="true" />
    <!-- AI 回复「传图回复」最多可选图片数；未配置默认 5，有效范围 1..20 -->
    <meta-data
        android:name="com.paopaoime.oem.aiReplyMaxImageSelection"
        android:value="5" />
    <!-- AI 回复 / 素材库单次文本发送最大字符数；未配置默认 1000，可运行时覆盖 -->
    <meta-data
        android:name="com.paopaoime.oem.sendTextMaxChars"
        android:value="1000" />
    <!-- SDK 内置 AI 回复霓虹边框/后台接管层总开关：默认关闭；宿主自管生成中 UI 时保持 false -->
    <meta-data
        android:name="com.paopaoime.oem.builtinNeonOverlayEnabled"
        android:value="false" />
    <!-- 细粒度功能开关：是否启用素材库（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableMaterialLibrary"
        android:value="true" />
    <!-- 细粒度功能开关：是否启用企业质检会话（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableQaSession"
        android:value="true" />
    <!-- 输入质检会话分发模式：
         CLOUD_ONLY=仅上传企业服务端
         HOST_ONLY=仅回调宿主 App
         DUAL_WRITE=同时上传服务端并回调宿主
         LOCAL_ONLY=仅本地留存 -->
    <meta-data
        android:name="com.paopaoime.oem.sessionArchiveMode"
        android:value="DUAL_WRITE" />
    <!-- 自动化总开关：默认关闭，只有交付自动化套餐时才打开 -->
    <meta-data
        android:name="com.paopaoime.oem.enableAutomation"
        android:value="true" />
</application>
```

企业联网配置说明：

- `com.paopaoime.oem.apiBaseUrl`：企业后端基础域名。OEM 版核心联网能力统一只走这个域名，必须显式填写企业自有 API 域名，例如 `https://api.enterprise.example.com`
- `apiBaseUrl` 不再允许漏配后静默回退到 `paopaoime` 域名；若未配置，SDK 会直接报错，提示宿主补齐企业域名
- 当前 OEM 联调包已允许采集宿主自身输入框的上屏内容，便于直接在 `OEM/company` 宿主内完成输入会话留存验证
- `com.paopaoime.oem.aiMode`：AI 接入模式，支持 `proxy`、`direct`、`dual`
- `com.paopaoime.oem.enableAiReply`：AI 回复入口开关，可与 AI 总开关配合控制
- `com.paopaoime.oem.aiReplyMaxImageSelection`：AI 回复「传图回复」最多可选图片数量，默认 5，有效范围 1..20；也可通过 `OemSdkConfig.aiReplyMaxImageSelection` 或 `OemImeSdk.setAiReplyMaxImageSelection(...)` 运行时覆盖
- `com.paopaoime.oem.sendTextMaxChars`：AI 回复与素材库单次文本发送最大字符数，默认 1000；也可通过 `OemSdkConfig.sendTextMaxChars` 或 `OemImeSdk.setSendTextMaxChars(...)` 运行时覆盖。该值会同步作用于微信等宿主的输入框提交验证窗口；超过阈值时 SDK 会提示“超出可发送字数上限（N字）”并失败，不会静默截断
- `com.paopaoime.oem.builtinNeonOverlayEnabled`：SDK 内置 AI 回复霓虹边框/后台接管层总开关，默认 `false`；宿主需要沿用 SDK 内置霓虹时才改为 `true`，否则建议结合 `OemAiReplyLifecycleListener` 自行展示 loading/悬浮窗
- `com.paopaoime.oem.enableMaterialLibrary`：素材库入口开关，适合只保留某一项功能的客户
- `com.paopaoime.oem.enableQaSession`：企业质检会话采集开关
- `com.paopaoime.oem.sessionArchiveMode`：企业质检会话分发模式，支持 `CLOUD_ONLY`、`HOST_ONLY`、`DUAL_WRITE`、`LOCAL_ONLY`
- 当分发模式为 `HOST_ONLY` 或 `DUAL_WRITE` 时，宿主必须实现 `sessionArchiveListener`；否则 SDK 虽然会尝试回调宿主，但宿主自身不会留下任何会话处理记录

质检会话四种模式建议：

- `CLOUD_ONLY`：只上传企业服务端，适合当前只要求企业云端联调、暂不需要宿主侧查看会话列表的客户
- `HOST_ONLY`：只回调宿主 App，适合客户希望完全在自己 App 内做会话留存、审核或二次分发，不希望上云演示环境
- `DUAL_WRITE`：同时上传企业服务端并回调宿主，适合联调阶段同时核对“企业云端列表 + 宿主接收列表”两边结果
- `LOCAL_ONLY`：仅在输入法本地留存，不上传云端也不回调宿主，适合只做本地问题排查或临时关闭外部流转时使用

客户要让宿主 App 留存质检会话，该怎么配置：

1. 先开启企业质检能力：确保 `enableQaSession` 或 `qaSessionEnabled` 为 `true`，否则输入会话不会进入分发链路
2. 再选择分发模式：只想宿主自己留存时用 `HOST_ONLY`；既要官网/API 演示页可见、又要宿主自己留存时用 `DUAL_WRITE`
3. 宿主必须在 `Application` 初始化 SDK 时实现 `sessionArchiveListener`；这一项不能只靠 `Manifest`，因为真正接收会话事件的是代码里的监听器
4. `sessionArchiveListener` 收到 `event` 后，建议宿主立即做本地落库、列表展示或转发到客户自己的业务系统，处理成功后返回 `true`
5. 联调时建议直接去微信、企业微信、浏览器等外部第三方 App 输入一句固定文案，再回到宿主自己的会话页核对最新记录；若用了 `DUAL_WRITE`，官网 API 页也应看到同一时段的新记录

推荐联调顺序：

1. 第一次联调先用 `DUAL_WRITE`
2. 官网演示页确认云端列表能看到最近会话
3. 宿主自己的本地落库页、日志系统或调试页确认 `sessionArchiveListener` 已收到同一时段的新记录
4. 双边都通过后，再按客户正式方案切到 `CLOUD_ONLY` 或 `HOST_ONLY`
- 当 `aiMode=direct` 或 `aiMode=dual` 时，再补下面 3 个字段：

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

建议：

- `proxy`：由企业服务端代理大模型请求，适合统一鉴权、审计和额度控制
- `direct`：由 SDK 直接请求兼容 OpenAI 的模型服务，适合快速验证
- `dual`：优先走企业代理，代理未配置或不可用时再回退到直连
- `aiOpenAiApiKey` 不建议长期硬编码在正式包中，建议由宿主自己的安全配置或下发体系注入

### 开发者页联调配置位置

当前 OEM 演示包已在手机端提供开发者联调页，入口路径：

- `输入法设置 -> 开发者 -> AI模型配置`
- `输入法设置 -> 开发者 -> AI回复模块`

说明：

- 这里的配置属于**本机开发联调覆盖项**，优先级高于宿主 `Manifest` 默认值
- 官网文档只需要说明“字段含义 + 配置位置”，正式环境仍建议由宿主自己的配置中心、下发系统或 `Manifest` 统一管理
- `AI模型配置` 主要影响模型连通与采样参数
- `AI回复模块` 主要影响 prompt、语气和输出规则

#### AI模型配置字段

| 字段 | 手机开发者页位置 | 是否必填 | 默认值 | 生效条件 |
| --- | --- | --- | --- | --- |
| `apiBaseUrl` | `开发者 -> AI模型配置` | 选填；`proxy / dual` 推荐填写 | 宿主 `Manifest` 或内置默认域名 | 企业代理、质检、素材库、自动化共用 |
| `aiOpenAiBaseUrl` | `开发者 -> AI模型配置` | `direct / dual` 下必填 | 无 | 直连 OpenAI 兼容模型 |
| `aiOpenAiApiKey` | `开发者 -> AI模型配置` | `direct / dual` 下必填 | 无 | 直连 OpenAI 兼容模型 |
| `aiOpenAiModel` | `开发者 -> AI模型配置` | `direct / dual` 下必填 | 无 | 直连 OpenAI 兼容模型 |
| `temperature` | `开发者 -> AI模型配置` | 选填 | `0.0` | AI 回复请求参数 |
| `topP` | `开发者 -> AI模型配置` | 选填 | `0.85` | AI 回复请求参数 |
| `maxTokens` | `开发者 -> AI模型配置` | 选填 | `1100` | AI 回复请求参数 |

#### AI回复模块字段

| 字段 | 手机开发者页位置 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `systemPrompt` | `开发者 -> AI回复模块` | 选填 | 自动按默认业务角色生成 | 留空时使用内置系统提示词 |
| `tonePresets` | `开发者 -> AI回复模块` | 选填 | 内置默认语气列表 | 支持增删改查；未配置时不生效，继续用默认语气 |
| `replyCount` | `开发者 -> AI回复模块` | 选填 | `10` | 取值范围 `1-20` |
| `replyLengthPolicy` | `开发者 -> AI回复模块` | 选填 | `short_reply` | 用于补充回复长度策略标识 |
| `businessRole` | `开发者 -> AI回复模块` | 选填 | `IM 沟通回复助手` | 用于描述当前 AI 回复角色 |
| `minChars` | `开发者 -> AI回复模块` | 选填 | `6` | 单条回复最小字符数 |
| `maxChars` | `开发者 -> AI回复模块` | 选填 | `22` | 单条回复最大字符数 |
| `allowEmoji` | `开发者 -> AI回复模块` | 选填 | `true` | 是否允许输出少量自然表情 |
| `avoidNickname` | `开发者 -> AI回复模块` | 选填 | `true` | 是否尽量避免直接称呼客户昵称 |
| `language` | `开发者 -> AI回复模块` | 选填 | `zh-CN` | 输出语言标识 |

#### tonePresets 子字段

当企业在手机端新增一条自定义语气时，当前每条语气包含以下字段：

| 字段 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 必填 | 无 | 语气名称，如“礼貌回复” |
| `promptHint` | 必填 | 无 | 对当前语气的提示词描述 |

如果本次交付包含自动化套餐，设备侧还需要执行：

```bash
adb shell pm grant <宿主包名> android.permission.WRITE_SECURE_SETTINGS
```

## 步骤三：在 Application 初始化 SDK

`customerId`、`appKey`、`channel` 为宿主初始化必填项，填写规则如下：

- `customerId`：填写我方交付的企业码，不是企业名称
- `appKey`：当前与企业码同值，直接填写同一企业码
- `channel`：必填，默认 `release`；必须显式写入 Manifest（旧版本缺失会在 Application 创建阶段崩溃）

企业限制说明：

- 当企业未开通、企业被管理员停用、或当前设备席位被管理员停用时，AI 回复、素材库等增值能力会显示受限
- 基础输入不受影响

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
                // 预留接口，当前可先返回固定放行结果
                licenseProvider = YourLicenseProvider(),
                // AI 总开关：关闭后 AI 运行时不会初始化
                enableAi = true,
                // 自动化总开关：只有交付自动化套餐时才打开
                enableAutomation = false,
                // UI 总开关：素材库、设置页等界面能力依赖这里
                enableUi = true,
                // 细粒度功能开关：按客户需求单独启用/关闭
                featureToggles = OemFeatureToggles(
                    aiReplyEnabled = true,
                    materialLibraryEnabled = true,
                    qaSessionEnabled = true,
                ),
                // 输入质检会话分发模式：可选仅云端、仅宿主、双写或仅本地
                sessionArchiveMode = OemSessionArchiveMode.DUAL_WRITE,
                // 当分发模式包含 HOST_ONLY / DUAL_WRITE 时，在这里接收回调给宿主的会话事件
                sessionArchiveListener = YourSessionArchiveListener(),
            )
        )
    }
}
```

宿主接收会话的最小实现示例：

```kotlin
class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OemImeSdk.initializeFromManifest(
            application = this,
            licenseProvider = YourLicenseProvider(),
            sessionArchiveListener = OemSessionArchiveListener { event ->
                // 建议宿主先做本地落库或上报，再返回 true
                HostSessionStore.save(event)
                true
            },
            materialTaxonomyHostDelegate = EnterpriseMaterialTaxonomyDelegate(),
        )
    }
}
```

如果企业选择宿主回调模式管理素材库，建议同时注册两类委托：

- `materialHostDelegate`：负责企业素材列表的拉取，以及素材增删改后的 `upsert/remove/invalidate`
- `materialTaxonomyHostDelegate`：负责企业分类、标签的查询与抽屉内管理；第一期仅企业自定义分类、标签，素材类型仍由 SDK 内部控制

如需在初始化后根据企业后台配置或宿主设置页即时切换细粒度能力，可直接调用运行时开关接口，无需重新初始化 SDK：

```kotlin
OemImeSdk.updateFeatureToggles(
    qaSessionEnabled = false,
    materialLibraryEnabled = true,
)

OemImeSdk.setAiReplyEnabled(true)
```

说明：

- 该能力只作用于 `featureToggles` 细粒度开关，不替代 `enableAi` / `enableUi` / `enableAutomation` 这类总开关
- 生效优先级为：`运行时覆盖 > OemSdkConfig / Manifest > 默认值`
- 若要回退到初始化配置，可调用 `OemImeSdk.setFeatureToggles(null)` 或 `OemImeSdk.clearFeatureToggleOverrides()`

### AI 回复生成生命周期回调（可选，2026-09-20 起）

宿主需要自行展示生成中 loading、悬浮窗或埋点时，可注册 AI 回复生成生命周期监听器。SDK 内置霓虹默认关闭，推荐宿主按自身 UI 体系接管生成中反馈：

```kotlin
OemImeSdk.setAiReplyLifecycleListener(object : OemAiReplyLifecycleListener {
    override fun onGenerationStarted(requestId: String, generateMode: String, fresh: Boolean) {
        HostAiReplyLoading.show(requestId, generateMode, fresh)
    }

    override fun onGenerationFinished(requestId: String, generateMode: String, success: Boolean, reason: String) {
        HostAiReplyLoading.hide(requestId, success, reason)
    }
})
```

集成要点：

- `fresh=true` 表示本次会重新取上下文/截图/粘贴/传图生成；`fresh=false` 表示基于已有上下文换语气或重新生成
- 每个已开始的 `requestId` 都会回调一次 `Finished`，`reason` 当前为 `success`、`failed`、`cancelled`、`timeout`
- 如果企业仍希望使用 SDK 内置霓虹，可在 Manifest 写入 `com.paopaoime.oem.builtinNeonOverlayEnabled=true`，或初始化后调用 `OemImeSdk.setBuiltinNeonOverlayEnabled(true)`

### 宿主截图回调（可选，2026-08-24 起）

宿主已具备自有截屏能力（无障碍全局截图、系统级截屏服务等）时，可注册截图回调替代 SDK 内置截屏链路：

```kotlin
OemImeSdk.setScreenshotProvider { context ->
    // 返回当前屏幕位图；无法取图时返回 null
    HostScreenshotService.capture(context)
}
```

集成要点：

- `captureScreen` 为同步调用，SDK 不设超时，宿主侧耗时多久都会等待
- 注册后截屏权限校验直接视为已授权，无需引导用户做手动截屏授权
- 返回 `null` 时 SDK 按截屏失败处理（`CAPTURE_FAILED`），不会降级到自动化截屏或缓存截图
- 传 `null` 给 `setScreenshotProvider` 即注销，恢复内置截屏链路

### 滑屏宿主回调超时（可选，2026-08-24 起）

已注册滑屏宿主回调（`swipeHostDelegate`）且宿主走无障碍手势链路时，等待宿主回写结果的超时默认 15 秒。如宿主链路更慢（例如 ContentObserver 跨进程回传超过 15 秒），可在初始化后调大：

```kotlin
// 单位毫秒，最小 1_000，小于最小值会被钳制
OemImeSdk.setSwipeHostResultTimeoutMillis(20_000L)
```

建议联调方式：

- 若 `sessionArchiveMode=DUAL_WRITE`，可同时验证两条结果：
  - 企业自己的云端会话列表能看到最近会话，说明云端上传链路正常
  - 宿主自己的接收页、日志或本地落库能看到同一时段的新记录，说明宿主回调链路正常
- 若只验证宿主回调，可把 `sessionArchiveMode` 改为 `HOST_ONLY`
- 若只验证云端上传，可把 `sessionArchiveMode` 改为 `CLOUD_ONLY`
- 若希望对比不同外部第三方 App 的输入效果，建议直接在微信、企业微信、浏览器输入框等真实场景输入一句固定文案，再回到官网或宿主接收页核对最新记录
- 宿主接收验证时，记录中的 `targetPackageName / targetAppName` 应该显示真实输入发生的外部第三方 App，而不是宿主 App 本身

## 步骤四：在 Manifest 写入配置

`com.paopaoime.oem.customerId` 和 `com.paopaoime.oem.appKey` 必须填写正式交付值：

- `com.paopaoime.oem.customerId`：填写我方交付的企业码
- `com.paopaoime.oem.appKey`：当前与企业码同值，直接填写同一企业码
- `com.paopaoime.oem.channel`：必填，默认 `release`；必须显式写入 Manifest

```xml
<application
    android:name=".HostApp"
    tools:replace="android:name">

    <meta-data
        android:name="com.paopaoime.oem.customerId"
        android:value="your_customer_id" />
    <meta-data
        android:name="com.paopaoime.oem.appKey"
        android:value="your_customer_id" />
    <meta-data
        android:name="com.paopaoime.oem.channel"
        android:value="release" />
    <meta-data
        android:name="com.paopaoime.oem.environment"
        android:value="prod" />
    <!-- 企业后端基础域名：AI 代理、素材库同步、输入质检云端上传统一走这里；禁止回退到 paopaoime 域名 -->
    <meta-data
        android:name="com.paopaoime.oem.apiBaseUrl"
        android:value="https://api.enterprise.example.com" />
    <!-- AI 运行模式：proxy=企业代理；direct=SDK 直连；dual=双模式兜底 -->
    <meta-data
        android:name="com.paopaoime.oem.aiMode"
        android:value="dual" />
    <!-- AI 总开关：默认关闭；开通 AI 套餐后设为 true，关闭后 AI 回复功能不会再对宿主开放 -->
    <meta-data
        android:name="com.paopaoime.oem.enableAi"
        android:value="true" />
    <!-- UI 总开关（基础能力，默认开启）：关闭后素材库和设置页等 UI 能力不会再对宿主开放 -->
    <meta-data
        android:name="com.paopaoime.oem.enableUi"
        android:value="true" />
    <!-- 细粒度功能开关：是否启用 AI 回复（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableAiReply"
        android:value="true" />
    <!-- AI 回复「传图回复」最多可选图片数；未配置默认 5，有效范围 1..20 -->
    <meta-data
        android:name="com.paopaoime.oem.aiReplyMaxImageSelection"
        android:value="5" />
    <!-- 细粒度功能开关：是否启用素材库（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableMaterialLibrary"
        android:value="true" />
    <!-- 素材库导航模式：默认 TYPE_ONLY；支持仅类型、类型+分类、分类+类型、仅分类 -->
    <meta-data
        android:name="com.paopaoime.oem.materialNavigationMode"
        android:value="TYPE_ONLY" />
    <!-- DrawerKit（R9）：AI回复「自定义UI交互」开关，默认 false；开启后 AI回复抽屉内容层由宿主自绘，需配套注册内容工厂 -->
    <meta-data
        android:name="com.paopaoime.oem.enableAiReplyCustomUi"
        android:value="false" />
    <!-- DrawerKit（R9）：素材库「自定义UI交互」开关，默认 false；开启后素材库抽屉内容层由宿主自绘，需配套注册内容工厂 -->
    <meta-data
        android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi"
        android:value="false" />
    <!-- DrawerKit（R9）：用户自定义抽屉开关，默认 false；开启后运行时注册的自定义抽屉才会进入工具面板/定制工具栏 -->
    <meta-data
        android:name="com.paopaoime.oem.enableCustomDrawers"
        android:value="false" />
    <!-- 细粒度功能开关：是否启用企业质检会话（默认关闭，开通后启用） -->
    <meta-data
        android:name="com.paopaoime.oem.enableQaSession"
        android:value="true" />
    <!-- 输入质检会话分发模式：按客户要求改为 CLOUD_ONLY / HOST_ONLY / DUAL_WRITE / LOCAL_ONLY -->
    <meta-data
        android:name="com.paopaoime.oem.sessionArchiveMode"
        android:value="DUAL_WRITE" />
    <meta-data
        android:name="com.paopaoime.oem.aiOpenAiBaseUrl"
        android:value="https://your-openai-compatible-host/v1" />
    <meta-data
        android:name="com.paopaoime.oem.aiOpenAiApiKey"
        android:value="your_api_key" />
    <meta-data
        android:name="com.paopaoime.oem.aiOpenAiModel"
        android:value="gpt-4o-mini" />
</application>
```

素材库标准配置说明：

- `com.paopaoime.oem.ocrEnabled=true`：默认启用 SDK 内置 OCR，用于 AI 识屏、微信发送按钮/附件确认等端侧识别链路
- `com.paopaoime.oem.ocrEnabled=false`：关闭 SDK 内置 OCR，SDK 不再初始化 Paddle-Lite OCR；适合低配机规避端侧 OCR native 崩溃，或宿主自行接管 OCR/截图语义的场景

- `com.paopaoime.oem.materialNavigationMode=TYPE_ONLY`
  - 只显示素材类型一行导航
  - 适合企业只想保留 SDK 默认的类型筛选
- `com.paopaoime.oem.materialNavigationMode=TYPE_PRIMARY_CATEGORY_SECONDARY`
  - 第一行显示素材类型，第二行显示企业分类
  - 适合“先按类型，再按分类”筛选的企业
- `com.paopaoime.oem.materialNavigationMode=CATEGORY_PRIMARY_TYPE_SECONDARY`
  - 第一行显示企业分类，第二行显示素材类型
  - 适合“先按分类，再按类型”筛选的企业
- `com.paopaoime.oem.materialNavigationMode=CATEGORY_ONLY`
  - 只显示企业分类，不显示素材类型
  - 适合企业完全按自定义分类组织素材的场景
- 宿主未返回任何分类时，无论配置哪种模式，SDK 都自动回退为单行类型导航
- 标签不作为导航显示，仅作为搜索命中条件；宿主通过素材字段中的 `tags` 回传标签即可

## 步骤五：如果包含自动化套餐，再补下面配置

`Application` 增加自动化初始化：

```kotlin
OemHostRuntime.initialize(this)
```

`Manifest` 增加：

```xml
<meta-data
    android:name="com.paopaoime.oem.enableAutomation"
    android:value="true" />
<meta-data
    android:name="com.paopaoime.oem.enableAutomationSettingsEntry"
    android:value="false" />
```

自动化权限元数据已随 SDK 默认合并，无需额外暴露底层引擎键名。

发布输入法自动化引擎时使用宿主自己的包名：

```properties
automationEngineApplicationId=com.example.host
automationEngineDefaultAllowedPackages=com.example.host,com.example.host.debug
```

说明：

- 只有交付自动化套餐时才打开 `enableAutomation`
- `com.paopaoime.oem.enableAutomationSettingsEntry` 默认 `false`，即使已集成自动化模块也不默认显示“自动化设置”入口；只有企业明确需要该入口时才改为 `true`
- 自动化权限元数据已由 SDK 内部合并，通常无需宿主手工声明底层引擎键名
- 宿主包名变化后，输入法自动化引擎制品必须按新的 `applicationId` 重新发布
- 如果客户需要完整自动化授权 SOP，请继续阅读：[外部版-自动化授权配置.md](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-%E8%87%AA%E5%8A%A8%E5%8C%96%E6%8E%88%E6%9D%83%E9%85%8D%E7%BD%AE.md)

自动化最易漏项：

- 设备侧需要向最终宿主包名授予 `WRITE_SECURE_SETTINGS`
- `Application` 初始化链不能只写 `OemImeSdk.initialize(...)`，还要补齐自动化初始化
- `Manifest` 里的 `com.paopaoime.oem.enableAutomation` 不能遗漏
- 宿主包名变化后，不能只改宿主工程，必须同时重新获取按新包名发布的输入法自动化引擎制品

## 步骤六：完成验收

基础验收：

1. 安装宿主 App
2. 确认宿主能正常打开输入法相关页面
3. 确认输入法服务已被系统识别
4. 确认宿主可以进入输入法设置页

## DrawerKit 自定义UI与自定义抽屉（R9）

R9 起提供 DrawerKit 抽屉开放层：宿主可以在 SDK 提供的抽屉基座上自定义两个内置抽屉（AI回复 / 素材库）的内容层，也可以注册全新的自定义抽屉。能力分三级：

| 层级 | 名称 | 配置方式 |
| --- | --- | --- |
| L0 | 默认UI | 功能勾选开关（现状，视觉与行为零变化） |
| L1 | 内置抽屉自定义UI | 功能勾选 + `enableAiReplyCustomUi` / `enableMaterialLibraryCustomUi` 开关 + `OemImeSdk.setDrawerContentFactory(drawerId, factory)` |
| L2 | 自定义抽屉 | `enableCustomDrawers=true` + 运行时 `OemImeSdk.registerCustomDrawer(spec)`，最多注册 4 个 |

要点说明：

- L1 默认模式下，抽屉基座（容器、标准头部、开合动画、下拉手势、返回键分发）仍由 SDK 保留；基座头部复用现抽屉视觉（返回/关闭按钮位置不变），返回按钮显隐由宿主经 `controller.setBackVisible` 配置（默认显示）
- 若宿主实现 `OemDrawerContentFactory.onCreateFullDrawerView(...)` 并返回非空 View，则该 View 以 `MATCH_PARENT` 占满抽屉 root，默认顶栏、标题、关闭按钮和加载提示均不创建，返回/关闭交互由宿主在同一 View 树内调用 `controller.closeDrawer()` 等能力完成
- 已注册内容工厂但对应自定义 UI 开关未开启时，自动容错降级为 L0 默认UI，并输出包含 `drawerId` / 缺失开关名的 warn 日志；开关开启但未注册工厂时也降级 L0，不会崩溃
- 内置抽屉的功能字段已抽离为「能力字段协议」（AI回复 12 字段、素材库 11 字段），宿主可自由排版并经 `OemImeSdk.aiReplyCapabilities()` / `OemImeSdk.materialCapabilities()` 调用底层能力
- **能力同样开放给 L2 自定义抽屉**：能力在进程启动时安装、与当前打开的抽屉无关，自定义抽屉的 View 内可随时调用全部能力实现编程闭环。本次新增的闭环能力：`aiReplyCapabilities().sendReplyToInput(text)`（AI 生成结果直接发送到输入框）、`materialCapabilities().sendTextToInput(text)`（任意文本直接发送）、`materialCapabilities().queryRemoteMaterials(...)` / `refreshRemoteMaterials(...)`（企业远程素材查询与刷新）；`sendToInput(item)` 亦支持发送企业远程素材项
- 素材库开启后必须二选一：默认内置 UI（`enableMaterialLibrary=true` + `enableMaterialLibraryCustomUi=false`）或宿主自定义 UI（`enableMaterialLibrary=true` + `enableMaterialLibraryCustomUi=true`）；宿主自定义模式下，**自定义 View 的传递点固定在 Application.onCreate，通过 `OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_MATERIAL_HUB, factory)` 返回宿主根 View**。需要连默认顶栏一起替换时，实现 `onCreateFullDrawerView`；只替换内容区时，实现 `onCreateContentView`
- `onCreateContentView(...)` 运行在主线程，只允许轻量创建根 View；网络、读盘、图片解码、大列表初始化必须异步，否则可能在“还没进入宿主自定义 View”前直接 ANR
- 素材库宿主自定义推荐能力组合：`queryMaterials`（列表）、`observeFolders`（分组）、`previewView`（详情）、`openComposer(TEXT/IMAGE/FILE/VIDEO)`（添加）、`sendToInput`（发送）
- L2 默认关闭；只有显式声明 `com.paopaoime.oem.enableCustomDrawers=true` 后，运行时注册的自定义抽屉入口才会进入工具面板宫格与顶部工具条
- L2 自定义抽屉入口自动追加到键盘工具面板（宫格页）末尾，并进入顶部工具条的「定制工具栏」排序/显示/隐藏体系；工具条维持 5 个上限（自定义项占配额，未满默认显示在末尾，已满进入隐藏池）；自定义图标默认原色渲染、不跟随内置 tint
- L1/L2 均不新增鉴权；L1 能力字段调用仍受既有企业开关约束；宿主自研内容属于宿主责任域，不纳入 SDK-R8 加密输入

完整接口签名与能力字段清单见 [外部版-API参考.md](./外部版-API参考.md) 的「DrawerKit（抽屉开放层）」章节。

## 集成调试日志管理（OemLog）

客户对接「自定义素材库 / 自定义抽屉 / 宿主回调」等功能遇到问题时，可开启 SDK 集成调试日志，SDK 关键链路日志会同步发往 logcat 与宿主注册的接收器，便于定位是宿主未注册、开关未开还是宿主回调抛异常。

总开关**默认关闭**，关闭时零开销；SDK 自身不存储任何日志，无积累增长问题。

开启方式（任一即可）：

1. Manifest：`<meta-data android:name="com.paopaoime.oem.enableDebugLog" android:value="true" />`
2. 构建配置：`OemSdkConfig` 的 `enableDebugLog` 字段
3. 运行时：`OemLog.setEnabled(true)`

宿主接收日志：

```kotlin
OemLog.setListener { level, tag, message, throwable ->
    // 回调在日志产生线程同步执行，更新 UI 请自行切线程
    android.util.Log.println(level.priority, tag, message)
}
```

宿主接收结构化诊断事件（可选）：

```kotlin
OemImeSdk.setDiagnosticListener { event ->
    // 示例：category=material_restore，name=restore_failed
    // attributes 内包含 hostPackage、baselineStartCount、finalStartCount、restoreAttempts 等字段
    uploadSdkDiagnostic(event.category, event.name, event.attributes)
    true
}
```

其它控制：`OemLog.setLogcatEnabled(false)` 关闭 logcat 只保留接收器；`OemLog.setMinLevel(OemLogLevel.INFO)` 按级别过滤；`OemLog.setListener(null)` 解除接收器。

排障示例：自定义素材库不生效时，开启日志后搜索 `OemImeSdk` / `OemDrawerCapabilities` / `MaterialHubActivity` 标签，可查看 `setDrawerContentFactory installed=...`、`custom drawer fallback to builtin: drawerId=... missingToggle=...`、`dispatchRemoteMaterialFetch accepted=...`、`sendTextToInput accepted=...` 等记录；出现 `missingToggle=ENABLE_MATERIAL_LIBRARY_CUSTOM_UI` / `ENABLE_AI_REPLY_CUSTOM_UI` / `ENABLE_CUSTOM_DRAWERS` 即表示宿主未开启对应开关，出现 `rejected: ... not registered` 即表示宿主未注册对应回调。

注意：发布包建议关闭；不要在生产环境长期开启。完整 API 见 [外部版-API参考.md](./外部版-API参考.md) 的「OemLog（集成调试日志）」章节。


## 本地默认皮肤（1.0.0.20260921）

浅色为「日光瓷白」，深色为「夜幕石墨」。两款资源随 `org.fcitx.fcitx5.android:app:1.13` 内置，离线可用。新装默认浅色；用户可在输入法设置的布局与显示中选择浅色、深色或跟随系统。

从旧版升级会迁移旧默认皮肤，保留自定义皮肤与跟随系统选择。企业只需更新七个公开 SDK 的统一版本以及显式 `app:1.9` 依赖并重新构建，不需要新增皮肤 API、服务器或下载权限。禁止混用 `app:1.6`。

### 第三方 Maven 镜像注意事项

若额外配置国内 Maven 镜像，请将 `com.github.Kyant0:Capsule` 使用 `exclusiveContent` 定向到 `https://jitpack.io`。部分镜像仅含 POM/module 而缺少 AAR，Gradle 不会自动转到下一个仓库，可能报 `Capsule-2.1.0.aar` 找不到。官网 SOP 默认的官方仓库配置不依赖该镜像。

## 离线语音输入（可选 SDK，集成后默认开启）

自 SDK `1.0.0.20261008` / 输入法运行时 `app:1.9` 起，语音独立为可选 `oem-ime-sdk-voice`，提供官方 sherpa-onnx 1.13.8 离线流式识别和自动标点；不依赖 AI 回复、自动化或云端大模型。


### 按需集成语音 SDK

官网模块选择器勾选“离线语音”（`voice`），会加入语音 SDK 和 sherpa 框架；未勾选时基础输入法不依赖 sherpa，语音实现、JNI、设置页和麦克风权限不进入宿主，全部语音入口自动隐藏。

```groovy
// 在基础输入法 core + host-bridge 的依赖之外按需添加：
implementation "com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261012"
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

客户开发者配置仍使用既有 `oem_developer_ai_config.json`（应用私有 files 目录）及原 Manifest/初始化配置解析；快捷回复不新增配置文件，也不绕过已有解析优先级。

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
