# 泡泡输入法企业版 · AI 一键安装 SOP（v2 · 模块直链版）

> 生成时间：2026/10/12 15:52:56　|　SDK 版本：1.0.0.20261012　|　目录 schema：v1
> 本次纳入交付的模块依赖：core、voice、ai、material、automation、developer（仅代表需要集成的 SDK artifact，不代表功能默认启用）
> 【版本自检·必读】SDK 采用统一版本号（全部制品与抬头一致），执行前核对抬头 `SDK 版本` 与官网（https://www.paopaoime.com/oem/ 「AI 一键安装」）当前版本一致；若本文档是旧本地副本（SDK 版本低于 1.0.0.20260816，即旧 1.x-alpha 系列），必须重新下载后再执行——旧版制品存在应用名被资源污染覆盖、非 AppCompat 宿主主题下打开 SDK 页面黑屏崩溃等已修复缺陷，继续执行会复现旧问题。
> 本文档面向 AI 编程工具：目标是从 0 开始，把泡泡输入法 OEM SDK 集成到企业 Android 宿主工程（允许是空白目录），并构建出可安装的 release APK。
> 推荐使用 ECS Maven 仓库按模块集成；§3 提供仓库与各模块直链，完整交付归档另见官网 SDK 下载。

> 本版更新：新增自定义抽屉 entryHandler 和剪贴板 OemClipboardQuickReplyHandler，支持宿主完全接管入口：主线程回调、忽略返回值、异常静默，不收键盘或启动 SDK 页面。OEM 剪贴板去掉标题与叉号，按钮改“智能回复”，工具条加高 12 布局单位。未注册保持原 SDK 流程；八公开 SDK Release/R8，runtime app:1.13。 SDK `1.0.0.20261012`；保持 `targetSdk 36` 与 HTTPS 策略。

## 修改记录

| 日期 | SDK 版本 | 修改说明 |
| --- | --- | --- |
| 2026-10-12 | `1.0.0.20261012` | 新增自定义抽屉 entryHandler 和剪贴板 OemClipboardQuickReplyHandler，支持宿主完全接管入口：主线程回调、忽略返回值、异常静默，不收键盘或启动 SDK 页面。OEM 剪贴板去掉标题与叉号，按钮改“智能回复”，工具条加高 12 布局单位。未注册保持原 SDK 流程；八公开 SDK Release/R8，runtime app:1.13。 |
| 2026-10-11 | `1.0.0.202610111` | 同一宿主复制后仅展示一次临时剪贴板和高情商回复；关闭、打字或离开宿主后不重现。快捷回复直接打开结果抽屉，选中仅回填草稿。复用客户 AI 回复模型配置，支持客户自绘内容及整块抽屉、状态回调和原文重试。八公开 SDK Release/R8，运行时 app:1.12。 |
| 2026-10-11 | `1.0.0.20261011` | 复制文本不再自动替换工具栏，包含键盘显示中、重新唤起及旧设置为开启的场景；保留手动剪贴板、历史和粘贴。移除无效的工具栏剪贴板建议/超时设置。八个公开 SDK 全量重建并执行 Release/R8，输入法运行时升级至 app:1.11。 |
| 2026-10-10 | `1.0.0.20261010` | 日光瓷白与夜幕石墨统一键盘比例、候选高亮留白与文字、按键气泡、键盘类型图标和分页工具面板；工具栏与工具面板不提供皮肤入口。八个公开 SDK 重新制作并执行 Release/R8，输入法运行时升级至 app:1.10。 |
| 2026-10-08 | `1.0.0.20261008` | sherpa-onnx 1.13.8 三档离线语音、默认开启的宿主配置和下载镜像；工具栏图标与面板配色/布局对齐。 |
| 2026-09-24 | `1.0.0.20260923` | Android 17 兼容预适配：SDK 不再放开宿主全局明文流量，移除旧调试 HTTP 上报；输入法运行时升至 `app:1.8`，保持 `targetSdk 36`。 |


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

## 1. 输入参数

AI 工具开始执行前，必须先拿到以下输入：

```yaml
project_root: "企业 Android 宿主工程根目录绝对路径（可为空白目录）"
host_application_id: "宿主最终 applicationId"
ime_display_name: "系统输入法选择器展示的输入法名称（建议与 App 名称一致）"
sdk_repo_url: "默认 https://paopaoime.com/oem/sdk-repo"
api_base_url: "默认 https://paopaoime.com（演示联调）；仅当有模块选用 CLOUD_ONLY/DUAL_WRITE（域名通道）时才需要替换为企业自有域名"
ai_mode: "proxy | direct | dual"
customer_id: "后台交付的企业码（customerId），必填，不是企业名称"
app_key: "必填；当前与企业码同值，直接填写后台交付的同一企业码"
channel: "必填，默认 release；无特殊约定保持 release，必须显式写入 Manifest（旧版 SDK 缺失会启动崩溃）"
environment: "prod"
openai_base_url: ""
openai_api_key: ""
openai_model: ""
```

规则：
- `ai_mode=proxy` 可不填 `openai_*`；`direct` / `dual` 必须三项全填。
- SDK License 为 ACTIVE 仅说明功能授权成功，不代表云端模型已配置。proxy 模式的企业服务需实现 `/api/v1/assets/model-settings` 及所需鉴权；直接填写泡泡官方域名不会自动取得模型登录态。遇到 401「缺少登录态」应检查模型通道/企业鉴权，不应通过清缓存、关闭 R8 或伪造 License 处理。若本次不验收云端模型，应明确记录 AI 面板通过、真实生成未验证。
- `customer_id` / `app_key`：均填写后台交付的企业码；当前版本两者同值，直接填写同一企业码即可。
- `ime_display_name` 决定系统输入法选择器与 SDK 页面展示的名称，必须填写。
- 企业限制说明：仅当企业未开通、企业被管理员停用、或当前设备席位被管理员停用时，对应增值能力会显示受限；基础输入不受影响。
- 未明确交付自动化套餐时，不启用 automation 相关配置；即使已交付 automation，`enableAutomationSettingsEntry` 也默认保持 false，只有企业明确需要时才显示“自动化设置”入口。
- **硬规则（AI 工具必须遵守）**：上方“纳入交付的模块依赖”只代表这些 artifact 需要集成到宿主工程，**不代表对应功能默认启用**。除基础键盘、`enableUi=true` 与已勾选的语音 SDK（默认开启，可用 JSON 关闭）外，其余功能开关默认显式写入 `false`；仅对企业已开通且用户明确授权的功能改为 `true`，不得因为模块已集成就自动启用。
- **Manifest 落盘规则（强制）**：`com.paopaoime.oem.enableAi`、`com.paopaoime.oem.enableAiReply`、`com.paopaoime.oem.enableQaSession`、`com.paopaoime.oem.enableMaterialLibrary`、`com.paopaoime.oem.enableAutomation`、`com.paopaoime.oem.enableAutomationSettingsEntry`、`com.paopaoime.oem.enable_developer`、`com.paopaoime.oem.enable_developer_entry`、`com.paopaoime.oem.enableAiReplyCustomUi`、`com.paopaoime.oem.enableMaterialLibraryCustomUi`、`com.paopaoime.oem.enableCustomDrawers` 必须逐项显式写入；默认关闭态也不能省略。

## 2. 本次纳入交付的模块依赖闭包（不代表默认启用）

- **core**（基础 + 输入法核心）〔必选基座〕
  - SDK 初始化、License 授权与能力管理、输入法内核与宿主桥接。所有其他模块的运行基座，必选。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-core:1.0.0.20261012`
  - artifact：`com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20261012`
  - artifact：`org.fcitx.fcitx5.android:app:1.13`
  - artifact：`org.fcitx.fcitx5.android.lib:common:1.0`
  - artifact：`org.fcitx.fcitx5.android.lib:fcitx5:1.0`
  - artifact：`org.fcitx.fcitx5.android.lib:fcitx5-chinese-addons:1.0`
  - artifact：`org.fcitx.fcitx5.android.lib:fcitx5-lua:1.0`
  - artifact：`org.fcitx.fcitx5.android.lib:libime:1.0`
- **voice**（离线语音）　依赖：core
  - 可选独立语音 SDK，包含工具栏/面板、三档模型管理及 sherpa-onnx 框架。集成后默认开启；未勾选不加入语音框架和功能。模型权重按需下载。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261012`
  - artifact：`com.paopaoime.oem.internal:sherpa-onnx:1.13.8`
- **ai**（AI 回复）　依赖：core
  - 键盘内 AI 回复、企业质检会话与 AI 运行时，含 OCR 能力。未集成自动化（automation）模块时自动降级为手动截屏（MediaProjection）方案。默认关闭，企业开通后启用。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.20261012`
  - artifact：`com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.20261012`
  - artifact：`com.paopaoime.oem.internal:paddleocr4android:1.0.0`
- **material**（素材库）　依赖：core, ai
  - 键盘工具栏素材库入口与素材中心（素材库面板内嵌 AI 沟通能力，依赖 AI 回复模块）。默认关闭，企业开通后启用。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-material:1.0.0.20261012`
- **automation**（Stellar 自动化）　依赖：core
  - 基于 Stellar（Shizuku 深度定制）的高权限自动化执行链路，含 AIDL、客户端 API、Provider、UserService 与 Shizuku 兼容层，companion 为 stellar-manager。可选增强模块：AI 回复/素材库在未集成本模块时自动降级（手动截屏），不会报错。默认关闭，企业开通后启用。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.20261012`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-api:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-aidl:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-shared:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-provider:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-userservice:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-server:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-manager:1.1`
  - artifact：`com.paopaoime.oem.internal.stellar:stellar-shizuku-api:1.1`
- **developer**（开发者）　依赖：core
  - 开发者调试入口（AI 模型配置、会话存档覆盖、调试存储），独立 AAR 交付，未集成时相关调试入口自动隐藏。默认关闭；集成/联调期可临时开启，正式交付保持关闭。
  - artifact：`com.paopaoime.oem:oem-ime-sdk-developer:1.0.0.20261012`

## 3. SDK 获取（ECS 模块直链）

### 3.1 统一 Maven 仓库地址（推荐）

```text
https://paopaoime.com/oem/sdk-repo
```

该地址为标准 Maven 布局，宿主工程 settings.gradle 直接引用即可（见 §6），Gradle 会按需下载本次纳入交付模块所需的全部 artifact；**artifact 被下载并不代表功能默认启用**。

### 3.2 各模块直接下载地址

**core（基础 + 输入法核心）**

- `com.paopaoime.oem:oem-ime-sdk-core:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-core/1.0.0.20261012/oem-ime-sdk-core-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-core/1.0.0.20261012/oem-ime-sdk-core-1.0.0.20261012.pom
- `com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-host-bridge/1.0.0.20261012/oem-ime-host-bridge-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-host-bridge/1.0.0.20261012/oem-ime-host-bridge-1.0.0.20261012.pom
- `org.fcitx.fcitx5.android:app:1.13`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/app/1.13/app-1.13.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/app/1.13/app-1.13.pom
- `org.fcitx.fcitx5.android.lib:common:1.0`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/common/1.0/common-1.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/common/1.0/common-1.0.pom
- `org.fcitx.fcitx5.android.lib:fcitx5:1.0`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5/1.0/fcitx5-1.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5/1.0/fcitx5-1.0.pom
- `org.fcitx.fcitx5.android.lib:fcitx5-chinese-addons:1.0`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-chinese-addons/1.0/fcitx5-chinese-addons-1.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-chinese-addons/1.0/fcitx5-chinese-addons-1.0.pom
- `org.fcitx.fcitx5.android.lib:fcitx5-lua:1.0`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-lua/1.0/fcitx5-lua-1.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-lua/1.0/fcitx5-lua-1.0.pom
- `org.fcitx.fcitx5.android.lib:libime:1.0`
  - aar：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/libime/1.0/libime-1.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/libime/1.0/libime-1.0.pom

**voice（离线语音）**

- `com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-voice/1.0.0.20261012/oem-ime-sdk-voice-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-voice/1.0.0.20261012/oem-ime-sdk-voice-1.0.0.20261012.pom
- `com.paopaoime.oem.internal:sherpa-onnx:1.13.8`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/sherpa-onnx/1.13.8/sherpa-onnx-1.13.8.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/sherpa-onnx/1.13.8/sherpa-onnx-1.13.8.pom

**ai（AI 回复）**

- `com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai/1.0.0.20261012/oem-ime-sdk-ai-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai/1.0.0.20261012/oem-ime-sdk-ai-1.0.0.20261012.pom
- `com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai-reply/1.0.0.20261012/oem-ime-sdk-ai-reply-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai-reply/1.0.0.20261012/oem-ime-sdk-ai-reply-1.0.0.20261012.pom
- `com.paopaoime.oem.internal:paddleocr4android:1.0.0`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/paddleocr4android/1.0.0/paddleocr4android-1.0.0.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/paddleocr4android/1.0.0/paddleocr4android-1.0.0.pom

**material（素材库）**

- `com.paopaoime.oem:oem-ime-sdk-material:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-material/1.0.0.20261012/oem-ime-sdk-material-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-material/1.0.0.20261012/oem-ime-sdk-material-1.0.0.20261012.pom

**automation（Stellar 自动化）**

- `com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-automation/1.0.0.20261012/oem-ime-sdk-automation-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-automation/1.0.0.20261012/oem-ime-sdk-automation-1.0.0.20261012.pom
- `com.paopaoime.oem.internal.stellar:stellar-api:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-api/1.1/stellar-api-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-api/1.1/stellar-api-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-aidl:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-aidl/1.1/stellar-aidl-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-aidl/1.1/stellar-aidl-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-shared:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shared/1.1/stellar-shared-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shared/1.1/stellar-shared-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-provider:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-provider/1.1/stellar-provider-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-provider/1.1/stellar-provider-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-userservice:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-userservice/1.1/stellar-userservice-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-userservice/1.1/stellar-userservice-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-server:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-server/1.1/stellar-server-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-server/1.1/stellar-server-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-manager:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-manager/1.1/stellar-manager-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-manager/1.1/stellar-manager-1.1.pom
- `com.paopaoime.oem.internal.stellar:stellar-shizuku-api:1.1`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shizuku-api/1.1/stellar-shizuku-api-1.1.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shizuku-api/1.1/stellar-shizuku-api-1.1.pom

**developer（开发者）**

- `com.paopaoime.oem:oem-ime-sdk-developer:1.0.0.20261012`
  - aar：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-developer/1.0.0.20261012/oem-ime-sdk-developer-1.0.0.20261012.aar
  - pom：https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-developer/1.0.0.20261012/oem-ime-sdk-developer-1.0.0.20261012.pom

### 3.3 离线镜像（构建环境无法访问公网时选用）

把下列 URL 逐个下载到 `<project_root>/oem-sdk/repo/` 下并保持 Maven 目录结构（URL 去掉 `https://paopaoime.com/oem/sdk-repo/` 前缀即为相对路径）：

```powershell
$repo = "https://paopaoime.com/oem/sdk-repo"
$urls = @(
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-core/1.0.0.20261012/oem-ime-sdk-core-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-core/1.0.0.20261012/oem-ime-sdk-core-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-host-bridge/1.0.0.20261012/oem-ime-host-bridge-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-host-bridge/1.0.0.20261012/oem-ime-host-bridge-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/app/1.13/app-1.13.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/app/1.13/app-1.13.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/common/1.0/common-1.0.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/common/1.0/common-1.0.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5/1.0/fcitx5-1.0.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5/1.0/fcitx5-1.0.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-chinese-addons/1.0/fcitx5-chinese-addons-1.0.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-chinese-addons/1.0/fcitx5-chinese-addons-1.0.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-lua/1.0/fcitx5-lua-1.0.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/fcitx5-lua/1.0/fcitx5-lua-1.0.pom",
    "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/libime/1.0/libime-1.0.aar", "https://paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/lib/libime/1.0/libime-1.0.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-voice/1.0.0.20261012/oem-ime-sdk-voice-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-voice/1.0.0.20261012/oem-ime-sdk-voice-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/sherpa-onnx/1.13.8/sherpa-onnx-1.13.8.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/sherpa-onnx/1.13.8/sherpa-onnx-1.13.8.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai/1.0.0.20261012/oem-ime-sdk-ai-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai/1.0.0.20261012/oem-ime-sdk-ai-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai-reply/1.0.0.20261012/oem-ime-sdk-ai-reply-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-ai-reply/1.0.0.20261012/oem-ime-sdk-ai-reply-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/paddleocr4android/1.0.0/paddleocr4android-1.0.0.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/paddleocr4android/1.0.0/paddleocr4android-1.0.0.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-material/1.0.0.20261012/oem-ime-sdk-material-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-material/1.0.0.20261012/oem-ime-sdk-material-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-automation/1.0.0.20261012/oem-ime-sdk-automation-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-automation/1.0.0.20261012/oem-ime-sdk-automation-1.0.0.20261012.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-api/1.1/stellar-api-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-api/1.1/stellar-api-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-aidl/1.1/stellar-aidl-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-aidl/1.1/stellar-aidl-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shared/1.1/stellar-shared-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shared/1.1/stellar-shared-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-provider/1.1/stellar-provider-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-provider/1.1/stellar-provider-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-userservice/1.1/stellar-userservice-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-userservice/1.1/stellar-userservice-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-server/1.1/stellar-server-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-server/1.1/stellar-server-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-manager/1.1/stellar-manager-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-manager/1.1/stellar-manager-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shizuku-api/1.1/stellar-shizuku-api-1.1.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/internal/stellar/stellar-shizuku-api/1.1/stellar-shizuku-api-1.1.pom",
    "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-developer/1.0.0.20261012/oem-ime-sdk-developer-1.0.0.20261012.aar", "https://paopaoime.com/oem/sdk-repo/com/paopaoime/oem/oem-ime-sdk-developer/1.0.0.20261012/oem-ime-sdk-developer-1.0.0.20261012.pom"
)
foreach ($u in $urls) {
    $rel = $u.Replace("$repo/", "").Replace("/", "\")
    $out = Join-Path "oem-sdk\repo" $rel
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    Invoke-WebRequest -UseBasicParsing $u -OutFile $out
}
```

使用离线镜像时，§6 的仓库地址改为 `maven { url = uri(rootDir.resolve("oem-sdk/repo")) }`。

## 4. 环境基线

```text
JDK 17 或 21 / Gradle 9.4.1 / AGP 9.2.0
minSdk 26 / targetSdk 36 / compileSdk 36
```

- 宿主工程低于此基线先升级再继续；使用 JDK 17 或 21，构建时显式设置 JAVA_HOME，Java/Kotlin 编译目标保持 17。
- AGP 9.x 已内置 Kotlin 支持：宿主 app 模块禁止再应用 `org.jetbrains.kotlin.android` 插件（会报 "Kotlin support is built into AGP" 硬错误）；`plugins {}` 只保留 `com.android.application`。
- 国内网络下载 Gradle 发行版请将 `gradle/wrapper/gradle-wrapper.properties` 的 distributionUrl 改为镜像（首选阿里云，腾讯备用）：`https\://mirrors.aliyun.com/macports/distfiles/gradle/gradle-9.4.1-bin.zip` 或 `https\://mirrors.cloud.tencent.com/gradle/gradle-9.4.1-bin.zip`（services.gradle.org 在国内常超时）。
- 生成 wrapper 的绕行法：本机 Gradle 与 AGP 不匹配时 `gradle wrapper` 会在配置阶段失败（`Unresolved reference 'android'`）——临时注释 `settings.gradle.kts` 的 `include(":app")` 再执行 `gradle wrapper`，成功后恢复注释并手工把 distributionUrl 改回镜像地址（wrapper 任务会把它重置为官网站点）。
- 默认直接产出 release 正式包，不要只验证 debug；空白工程无签名材料时先生成正式签名再绑定 release。`signingConfigs { storeFile = file("../xxx.keystore") }` 的相对路径以 **app 模块目录**为基准解析，不是工程根目录；推荐把 keystore 放工程根并用 `file(rootProject.file("xxx.keystore"))`，或直接用绝对路径，避免 `storeFile not found`。

### 4.1 首次构建与缓存处理

- 开始前执行 `java -version` 与 `.\gradlew.bat --version`，确认 Gradle 实际使用的 JVM；仅修改 PATH 不一定生效，还要检查 JAVA_HOME 与 org.gradle.java.home。
- 若工程显式要求 Java 17 toolchain，而 Gradle 使用 JDK 21，必须同时安装完整 JDK 17；可在本机 gradle.properties 中设置 `org.gradle.java.installations.paths=<JDK17绝对路径>,<JDK21绝对路径>`。不要把本机路径写进对外交付源码。
- 首次构建默认联网执行，禁止直接加 `--offline`。`No cached version ... available for offline mode` 表示依赖缓存未预热，先移除 --offline 完成一次联网构建；删除缓存后更不能继续离线重试。SDK 的本地 Maven 镜像不包含 AGP、AndroidX、Kotlin、lint 等全部外部依赖。
- Gradle wrapper 下载发生在 Gradle 启动前，不受 --offline 控制。发行版下载超时应切换 §4 的可用镜像、设置 `networkTimeout=60000` 后重新执行 --version；不要把未下载完成的 .part 文件伪装成完整发行包。
- 使用明确且固定的 GRADLE_USER_HOME。遇到缓存疑似陈旧时，先检查 dependencyInsight 与 AAR SHA-256；先尝试 `--refresh-dependencies`，必要时停止相关 Gradle daemon，仅清理已确认错误的坐标或在一个全新的专用 GRADLE_USER_HOME 中重建。不要清空整个用户目录或其他项目的缓存。
- `Could not find com.paopaoime...` 要核对官网对应 AAR/POM 直链。若直链 404，这是仓库交付缺失，必须等待 SDK 侧补齐并校验发布；清缓存、关闭 R8、改成源码 project 依赖均不能解决。官网返回 200 而本地解析失败时，再检查仓库范围、代理、Gradle 的负缓存与实际请求 URL。
- 每次失败保留完整日志及退出码，区分环境、依赖下载、SDK 制品缺失和源码编译错误。Windows PowerShell 5.1 将 native stderr 包装为 NativeCommandError 时，必须查看 Gradle 的 What went wrong 与进程退出码，不能仅凭 stderr 判定源码错误。

## 5. 空白目录建工程规则（全新宿主 App）

如果 `project_root` 下不存在可用 Android 工程，先创建最小宿主工程：

```text
<project_root>/
  settings.gradle.kts / build.gradle.kts / gradle.properties / gradlew(.bat) / gradle/wrapper/*
  app/build.gradle.kts / proguard-rules.pro
  app/src/main/AndroidManifest.xml
  app/src/main/java/<package>/HostApp.java
  app/src/main/java/<package>/MainActivity.java
```

宿主入口页规范（MainActivity = 透明转发页，把启动入口交给 SDK 自带完整主 UX）：
1. MainActivity.onCreate 立即以显式 Intent 启动 SDK 自带的 `com.paopaoime.aikeyboard.ime.ui.activity.SplashActivity`（同包内组件，其 exported=false 不影响宿主应用内启动），随后 finish()；不要自绘功能首页；
2. SplashActivity 自动路由：未 setup → SetupActivity（带 UI 的输入法启用/切换选择页）；已 setup → OemMainActivity（输入法设置页）。这就是标准版主 UX，宿主无需自实现；
3. App 名称：Manifest application label 用 ime_display_name，且必须在 strings.xml 覆盖 `oem_ime_display_name`（见 §9），否则 SDK 页面显示默认名称；
4. 诊断页（可选，仅排查用）：宿主可另建 SetupDiagActivity，展示四项状态（宿主初始化/输入法已启用/是否默认输入法/OEM 页面入口可用）与五按钮（启用输入法 → 切换到当前输入法 → 打开系统输入法设置 → 打开输入法页面 → 刷新状态），OEM 页面入口不可用时降级系统设置引导；入口由宿主自定（如设置内「接入诊断」），禁止占用 launcher。

MainActivity 转发模板：

```java
public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent().setClassName(this, "com.paopaoime.aikeyboard.ime.ui.activity.SplashActivity"));
        finish();
    }
}
```

## 6. 仓库与 Gradle 基线配置

完整 `settings.gradle.kts` 模板（含 pluginManagement，可直接粘贴）：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.android.application") version "9.2.0" apply false
    // 注意：AGP 9.x 已内置 Kotlin，禁止再加 org.jetbrains.kotlin.android
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://paopaoime.com/oem/sdk-repo") } // OEM SDK ECS Maven 仓（离线镜像时改本地路径）
        maven("https://jitpack.io") // 传递依赖兜底
    }
}

rootProject.name = "<宿主工程名>"
include(":app")
```

根 `build.gradle.kts` 保持最简（插件版本已在 settings 声明）：

```kotlin
// 根 build.gradle.kts 可以为空文件
```

宿主 app 模块 `app/build.gradle.kts` 的 `plugins {}` 只写：

```kotlin
plugins {
    id("com.android.application")
}
```

## 7. 添加宿主关键依赖

修改宿主 app 模块 `build.gradle(.kts)`：

```kotlin
dependencies {
    // core · 基础 + 输入法核心
    implementation("com.paopaoime.oem:oem-ime-sdk-core:1.0.0.20261012")
    implementation("com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20261012")
    implementation("org.fcitx.fcitx5.android:app:1.13")
    implementation("org.fcitx.fcitx5.android.lib:common:1.0")
    implementation("org.fcitx.fcitx5.android.lib:fcitx5:1.0")
    implementation("org.fcitx.fcitx5.android.lib:fcitx5-chinese-addons:1.0")
    implementation("org.fcitx.fcitx5.android.lib:fcitx5-lua:1.0")
    implementation("org.fcitx.fcitx5.android.lib:libime:1.0")
    // voice · 离线语音
    implementation("com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261012")
    implementation("com.paopaoime.oem.internal:sherpa-onnx:1.13.8")
    // ai · AI 回复
    implementation("com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.20261012")
    implementation("com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.20261012")
    implementation("com.paopaoime.oem.internal:paddleocr4android:1.0.0")
    // material · 素材库
    implementation("com.paopaoime.oem:oem-ime-sdk-material:1.0.0.20261012")
    // automation · Stellar 自动化
    implementation("com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.20261012")
    implementation("com.paopaoime.oem.internal.stellar:stellar-api:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-aidl:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-shared:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-provider:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-userservice:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-server:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-manager:1.1")
    implementation("com.paopaoime.oem.internal.stellar:stellar-shizuku-api:1.1")
    // developer · 开发者
    implementation("com.paopaoime.oem:oem-ime-sdk-developer:1.0.0.20261012")
    // kotlin-stdlib 下限（SpillingKt 需 ≥2.1.0）；Gradle 自然取最高版本，严禁 force 降级
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    // SDK 页面基于 AppCompat；core POM 不传递 appcompat，宿主必须显式声明
    implementation("androidx.appcompat:appcompat:1.7.1")
}
```

必须理解：
- `oem-ime-host-bridge` 负责 OEM 宿主桥接；
- `org.fcitx.fcitx5.android:app:1.13` 必须显式加入（core 闭包已含），宿主 Application 需直接继承 `FcitxApplication`，缺它会在输入法服务启动时崩溃。
- `org.fcitx.fcitx5.android:app:1.13` 当前必须解析到官方仓 `https://paopaoime.com/oem/sdk-repo` 的最新制品，禁止混用历史本地 Maven 仓、私有镜像仓或旧缓存中的同坐标旧包；若命中旧二进制，输入法服务启动会报 `NoSuchMethodError`（典型符号：`FavoriteBridge.bindHostShare` / `bindHostInputRestorePolicy` / `FavoriteHostInputRestorePolicy`）。
- 上述 `NoSuchMethodError` 的临时止血方案：删除本地 Maven 仓与 Gradle 缓存中当前 `org/fcitx/fcitx5/android/app/1.7` 相关条目后重新构建，并用 `./gradlew app:dependencyInsight --dependency org.fcitx.fcitx5.android:app` 确认最终解析路径指向官方仓。
- `stellar-shizuku-aidl` 禁止显式加入：其 `moe.shizuku.server` 类由 fcitx 官方传递依赖 `dev.rikka.shizuku:aidl` 在运行时提供（与主工程一致）；显式加入 vendored 副本会产生 duplicate class（`IShizukuService` 等）导致构建失败。依赖块以本节模板为准，不得自行补充 stellar 其它构件。
- kotlin-stdlib 版本红线（强制）：禁止对 `org.jetbrains.kotlin:kotlin-stdlib` 使用 `resolutionStrategy.force`、BOM 钉版或任何降级手段；SDK 协程状态机引用 `kotlin.coroutines.jvm.internal.SpillingKt`（Kotlin 2.1.0 新增），stdlib 解析结果必须 ≥2.1.0。Gradle 默认取最高版本（fcitx POM 声明 2.3.21），无需任何干预；若出现 stdlib 版本冲突告警，靠自然最高版本胜出解决，严禁 force 到 2.1.0 以下（如 2.0.21），否则安装后 IME 服务与 App 启动运行时崩溃 `NoClassDefFoundError: SpillingKt`。

## 8. 宿主打包与 native 规则

在 `app/build.gradle(.kts)` 的 `android {}` 内加入（强制项）：

```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
        pickFirsts += "lib/**/libonnxruntime.so"
        pickFirsts += "lib/**/libc++_shared.so"
        pickFirsts += "lib/**/libFcitxJsi.so"
        pickFirsts += "lib/**/libfcitx5.so"
    }
    resources {
        excludes += "META-INF/INDEX.LIST"
        excludes += "META-INF/DEPENDENCIES"
        excludes += "META-INF/*.version"
        excludes += "META-INF/AL2.0"
        excludes += "META-INF/LGPL2.1"
    }
}
lint {
    abortOnError = false
    checkReleaseBuilds = false
}
```

- `useLegacyPackaging = true` 必须开启，否则 `extractNativeLibs=false` 会导致 Fcitx native 闪退；
- `pickFirsts` 只允许上述最小白名单，禁止写成 `lib/**/*.so`。
- `lint` 两项必须关闭：AGP 9.x 的 lintVital 存在与 Kotlin 运行时的兼容问题（如 `kotlin/concurrent/atomics/AtomicsKt` NoClassDefFound），会无端阻断 release 构建；lint 不是 release 门禁。
- release 必须保持 `isMinifyEnabled = true`。R8 报 Missing classes 时，先查看 `app/build/outputs/mapping/release/missing_rules.txt` 与引用来源；只有已确认属于不使用的可选平台类/库时才加入对应 -dontwarn。缺失 OEM、fcitx、Stellar 等实际运行类时必须补齐依赖，禁止用 blanket -dontwarn 或关闭 minify 掩盖问题。
- 当前版本全模块的标准 AI 回复/素材库集成，请下载 https://www.paopaoime.com/oem/downloads/oem-host-optional-r8.pro 到 app/oem-host-optional-r8.pro，并在 release 的 proguardFiles 中加入该文件。文件仅含已审查的 Android 隐藏 API、桌面 Java、可选 PDF/压缩/XML/日志扩展的精确 -dontwarn 规则，不屏蔽 OEM 核心类。企业若使用这些可选扩展，仍须自行补齐对应实现。

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro", "oem-host-optional-r8.pro")
    }
}
```

## 9. AndroidManifest meta 配置（含中文注释）

```xml
<!-- ===== 模块 core（基础 + 输入法核心）===== -->
<!-- 企业客户 ID（= 后台交付的企业码 customer_id，不是企业名称），license 按该值校验；无默认值，必填（请按字段说明填写）；必填 -->
<meta-data
    android:name="com.paopaoime.oem.customerId"
    android:value="your_customer_id" />

<!-- 企业 AppKey：当前与企业码同值（后台交付单一企业码，双字段同填），直接填企业码即可，无需单独索取；无默认值，必填（请按字段说明填写）；必填 -->
<meta-data
    android:name="com.paopaoime.oem.appKey"
    android:value="your_app_key" />

<!-- 渠道标识，用于区分发布渠道（宿主侧日志与授权快照标记），必须显式写入，默认值 release；旧版 SDK 未配置会直接启动崩溃，新版缺失时回落 release；默认值：release；必填 -->
<meta-data
    android:name="com.paopaoime.oem.channel"
    android:value="release" />

<!-- 环境标识，支持 prod / staging / dev；默认值：prod；选填 -->
<meta-data
    android:name="com.paopaoime.oem.environment"
    android:value="prod" />

<!-- 企业服务端基础域名；仅当任一模块选用 CLOUD_ONLY / DUAL_WRITE（域名通道）时必填；全 HOST_ONLY / LOCAL_ONLY 对接无需配置；无默认值，选填；选填 -->
<meta-data
    android:name="com.paopaoime.oem.apiBaseUrl"
    android:value="https://api.enterprise.example.com" />

<!-- 席位激活上报通道域名（选填，仅私有化部署覆盖用）；缺省走 core 内置加密官方通道，与 apiBaseUrl 无关，无需配置即有席位记录；无默认值，选填；选填 -->
<meta-data
    android:name="com.paopaoime.oem.analyticsBaseUrl"
    android:value="" />

<!-- UI 能力总开关（基础能力，默认开启）：true = SDK 承载键盘 UI 与设置页（工具栏 AI 回复/素材库入口、设置页可用）；false = 仅基础键盘，无 AI/素材入口。选购 ai/material 模块时必须为 true；默认值：true；必填 -->
<meta-data
    android:name="com.paopaoime.oem.enableUi"
    android:value="true" />

<!-- 输入质检会话分发模式：LOCAL_ONLY / CLOUD_ONLY / HOST_ONLY / DUAL_WRITE；默认值：CLOUD_ONLY；选填 -->
<meta-data
    android:name="com.paopaoime.oem.sessionArchiveMode"
    android:value="CLOUD_ONLY" />

<!-- AI 回复与素材库单次文本发送最大字符数；未配置默认 1000，可通过 OemSdkConfig 或 OemImeSdk.setSendTextMaxChars 运行时覆盖；默认值：1000；选填 -->
<meta-data
    android:name="com.paopaoime.oem.sendTextMaxChars"
    android:value="1000" />

<!-- ===== 模块 ai（AI 回复）===== -->
<!-- AI 能力总开关；默认关闭，企业开通 AI 能力后显式设为 true；默认值：false；必填 -->
<meta-data
    android:name="com.paopaoime.oem.enableAi"
    android:value="false" />

<!-- 是否启用 AI 回复功能；默认关闭，按开通范围启用；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableAiReply"
    android:value="false" />

<!-- 是否启用企业质检会话功能；默认关闭，按开通范围启用；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableQaSession"
    android:value="false" />

<!-- AI 运行模式，支持 proxy / direct / dual（CLOUD_ONLY 域名通道的子模式）；默认值：proxy；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiMode"
    android:value="proxy" />

<!-- 直连 OpenAI 兼容接口的基础地址（direct/dual 模式）；无默认值，选填；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiBaseUrl"
    android:value="https://api.openai.com/v1" />

<!-- 直连 OpenAI 兼容接口的密钥（direct/dual 模式）；无默认值，选填；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiApiKey"
    android:value="sk-xxxx" />

<!-- 直连 OpenAI 兼容接口的模型名（direct/dual 模式）；无默认值，选填；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiOpenAiModel"
    android:value="gpt-4o-mini" />

<!-- AI 回复分发模式：CLOUD_ONLY（域名配置）/ HOST_ONLY（宿主回调）/ DUAL_WRITE（宿主优先、域名兜底）；HOST_ONLY 需宿主实现 OemAiReplyHostDelegate；默认值：CLOUD_ONLY；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiReplyDispatchMode"
    android:value="CLOUD_ONLY" />

<!-- AI 回复「传图回复」最多可选图片数量；未配置默认 5，有效范围 1..20，可通过 OemSdkConfig 或 OemImeSdk.setAiReplyMaxImageSelection 运行时覆盖；默认值：5；选填 -->
<meta-data
    android:name="com.paopaoime.oem.aiReplyMaxImageSelection"
    android:value="5" />

<!-- SDK 内置 AI 回复霓虹边框/后台接管层总开关；默认关闭，宿主可通过 OemAiReplyLifecycleListener 自行展示 loading/悬浮窗；如需恢复 SDK 内置霓虹再显式设为 true；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.builtinNeonOverlayEnabled"
    android:value="false" />

<!-- SDK 内置 OCR 总开关；默认开启。低配机或宿主自管 OCR 时可设为 false，SDK 将跳过 Paddle-Lite OCR 初始化与识别；默认值：true；选填 -->
<meta-data
    android:name="com.paopaoime.oem.ocrEnabled"
    android:value="true" />

<!-- AI回复自定义UI交互开关（DrawerKit L1，默认关闭）：开启后 AI 回复抽屉由宿主注册的内容工厂渲染；默认走内容槽，若 factory.onCreateFullDrawerView 返回非空 View 则整块替换含顶栏区域；已注册工厂但开关关闭时回退 L0 并输出 missingToggle=ENABLE_AI_REPLY_CUSTOM_UI warn；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableAiReplyCustomUi"
    android:value="false" />

<!-- 用户自定义抽屉开关（DrawerKit L2，默认关闭）：开启后宿主运行时注册的自定义抽屉（OemImeSdk.registerCustomDrawer(spec)）才会进入工具面板与定制工具栏；关闭时注册表可保留，但入口不暴露、拉起请求不生效并输出 missingToggle=ENABLE_CUSTOM_DRAWERS warn；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableCustomDrawers"
    android:value="false" />

<!-- ===== 模块 material（素材库）===== -->
<!-- 是否启用素材库功能；默认关闭，按开通范围启用；默认值：false；必填 -->
<meta-data
    android:name="com.paopaoime.oem.enableMaterialLibrary"
    android:value="false" />

<!-- 企业素材分发模式：LOCAL_ONLY（仅本地）/ CLOUD_ONLY（域名配置）/ HOST_ONLY（宿主回调）/ DUAL_WRITE（宿主优先、域名兜底）；HOST_ONLY 需宿主实现 OemMaterialHostDelegate；默认值：CLOUD_ONLY；选填 -->
<meta-data
    android:name="com.paopaoime.oem.materialDispatchMode"
    android:value="CLOUD_ONLY" />

<!-- 素材库导航模式：TYPE_ONLY（仅显示素材类型）/ TYPE_PRIMARY_CATEGORY_SECONDARY（类型一级、分类二级）/ CATEGORY_PRIMARY_TYPE_SECONDARY（分类一级、类型二级）/ CATEGORY_ONLY（仅显示分类，不显示类型）。若宿主未返回任何分类，SDK 自动回退为 TYPE_ONLY；第一期仅第三方企业配置分类/标签，素材类型仍由 SDK 内部控制；默认值：TYPE_ONLY；选填 -->
<meta-data
    android:name="com.paopaoime.oem.materialNavigationMode"
    android:value="TYPE_ONLY" />

<!-- 素材库自定义UI交互开关（DrawerKit L1，默认关闭）：开启后素材库抽屉由宿主注册的内容工厂渲染；默认走内容槽，若 factory.onCreateFullDrawerView 返回非空 View 则整块替换含顶栏区域；已注册工厂但开关关闭时回退 L0 并输出 missingToggle=ENABLE_MATERIAL_LIBRARY_CUSTOM_UI warn；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi"
    android:value="false" />

<!-- ===== 模块 automation（Stellar 自动化）===== -->
<!-- 自动化能力总开关；默认关闭，选购 automation 模块并开通后必须 true（companion 安装与授权前置步骤见 §11）；默认值：false；必填 -->
<meta-data
    android:name="com.paopaoime.oem.enableAutomation"
    android:value="false" />

<!-- 自动化设置入口显示开关；默认关闭。即使已集成 automation 模块并启用自动化能力，也只有显式设为 true 时，输入法设置页中的“自动化设置”卡片/入口才会显示；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enableAutomationSettingsEntry"
    android:value="false" />

<!-- Stellar 授权声明，SDK 构件已声明同名 meta，宿主必须显式声明并以 tools:replace 覆盖；默认值：stellar,follow_stellar_startup；必填 -->
<meta-data
    android:name="roro.stellar.permissions"
    android:value="stellar,follow_stellar_startup"
    tools:replace="android:value" />

<!-- ===== 模块 developer（开发者）===== -->
<!-- 是否启用开发者模块；默认关闭（正式交付基线），集成/联调期可临时设为 true；默认值：false；必填 -->
<meta-data
    android:name="com.paopaoime.oem.enable_developer"
    android:value="false" />

<!-- 开发者入口开关；SDK 构件已声明同名 meta，宿主声明时必须带 tools:replace；默认关闭，正式交付不暴露调试入口；默认值：false；选填 -->
<meta-data
    android:name="com.paopaoime.oem.enable_developer_entry"
    android:value="false"
    tools:replace="android:value" />
```

输入法显示名（必做）：在宿主 `app/src/main/res/values/strings.xml` 中加入一行，决定系统输入法选择器、SDK 设置页与素材页展示的名称：

```xml
<string name="oem_ime_display_name">填入 ime_display_name</string>
```

写入规则：
- `<application android:name=".HostApp" android:theme="@style/你的AppCompat系主题" tools:replace="android:name,android:label,android:theme">`，根节点声明 `xmlns:tools`；fcitx 官方 AAR 在 `<application>` 上声明了默认 `android:name`（FcitxApplication）与 `android:theme`（Theme.FcitxAppTheme），宿主必须用 `tools:replace` 三者全量覆盖，否则 manifest merger 报属性冲突；theme 覆盖为宿主自己的 AppCompat 系主题即可（SDK 页面自带显式主题，不受影响）；
- meta 值基线（强制）：本模板输出的 `android:value` 即集成期基线，执行方不得自行改值；尤其是所有默认关闭的 meta（如 `enableAi` / `enableAiReply` / `enableQaSession` / `enableMaterialLibrary` / `enableAutomation` / `enableAutomationSettingsEntry` / `enable_developer` / `enable_developer_entry` / `enableAiReplyCustomUi` / `enableMaterialLibraryCustomUi` / `enableCustomDrawers`）默认写入 `false`，不得省略；企业已开通且用户明确授权的功能可改为 `true`，不得因为模块已集成就擅自启用；带 `tools:replace="android:value"` 的项是因为 SDK 构件 Manifest 已声明同名 meta，宿主必须原样保留该属性覆盖；
- 宿主主题约束（强制）：`<application>` 的 `android:theme` 必须来自 AppCompat 系谱（`Theme.AppCompat.*` / `Theme.MaterialComponents.*` / `Theme.Material3.*`），禁止使用 `@android:style/Theme.*` 等 framework 主题；SDK 内页面基于 AppCompat，宿主主题系谱不符会导致打开 SDK 页面时黑屏/崩溃（`You need to use a Theme.AppCompat theme`）。SDK 自带页面已内置显式主题，但宿主自己的 Activity（含 §5 转发页）若继承 application 主题，同样受此约束；
- `customerId`/`appKey`：均填后台交付的企业码（不是企业名称）；当前版本两者同值；
- `channel`：必填，默认 release；必须显式写入 `com.paopaoime.oem.channel` meta。旧版 SDK（<1.0.0.202608241）未配置该项会在 Application 创建时直接崩溃（`channel must not be blank`）；新版缺失时回落 release，但仍要求显式写入，避免授权快照渠道标记为空。
- 输入法品牌名规则（强制）：默认只保留 `oem_ime_display_name` 这一份定义，直接写最终展示名；不要再额外创建 `company_app_name` / `app_name` / `company_ime_display_name` 等中间别名链；
- 如果企业必须通过 flavor `resValue`、多语言资源或构建脚本注入品牌名，必须同步删除 `values` 与 `values-zh` 中同名静态实体值；否则 release 阶段容易出现 `ExtraTranslation` 或重复资源冲突；
- 企业限制说明：仅当企业未开通、企业被管理员停用、或当前设备席位被管理员停用时，AI 回复、素材库等增值能力会显示受限；基础输入不受影响；
- `apiBaseUrl`：条件必填——仅当任一模块选用 CLOUD_ONLY / DUAL_WRITE（域名通道）时填企业自有域名；全 HOST_ONLY / LOCAL_ONLY 对接可留默认值；
- 席位激活上报（后台「席位记录数」）随 core 基础模块内置启用：官方上报通道加密内置于 core，与 `apiBaseUrl`（企业自有域名）完全无关；`analyticsBaseUrl` 仅私有化部署覆盖用，选填；执行方无需也不得为上报目的自行填写或改写任何域名；
- `enableUi`：true = SDK 承载键盘 UI 与设置页（键盘工具栏出现 AI 回复/素材库入口、设置页可用）；false = 仅基础键盘（无 AI/素材入口、OEM 页面入口降级系统设置）。选购 ai/material 模块时必须为 true；
- `ai_mode=direct/dual` 时追加 `aiOpenAiBaseUrl` / `aiOpenAiApiKey` / `aiOpenAiModel` 三项 meta；
- `aiReplyMaxImageSelection` 控制 AI 回复「传图回复」最多可选图片数量，默认 5，有效范围 1..20；运行时可通过 `OemSdkConfig.aiReplyMaxImageSelection` 或 `OemImeSdk.setAiReplyMaxImageSelection(...)` 覆盖；
- `builtinNeonOverlayEnabled` 控制 SDK 内置 AI 回复霓虹边框/后台接管层，默认 false；宿主自管生成中 UI 时保持关闭，若需要沿用 SDK 内置霓虹再改为 true；
- 宿主可注册 `OemAiReplyLifecycleListener` 接收 AI 回复生成 Started/Finished 回调，并根据 `requestId`、`generateMode`、`fresh`、`success`、`reason` 自行展示或关闭 loading/悬浮窗；
- 多模式分发（可选）：`com.paopaoime.oem.aiReplyDispatchMode`（CLOUD_ONLY/HOST_ONLY/DUAL_WRITE）、`com.paopaoime.oem.materialDispatchMode`（LOCAL_ONLY/CLOUD_ONLY/HOST_ONLY/DUAL_WRITE）、`com.paopaoime.oem.sessionArchiveMode`（四模式），HOST_ONLY/DUAL_WRITE 需宿主注册对应回调，详见官网各模块页「宿主回调对接」。

## 10. HostApp 模板与初始化顺序

公开入口类 → AAR 归属速查（import 与依赖排障用，勿用 javap 反推）：

| 类 | 包 | 所属 AAR |
|---|---|---|
| `OemImeSdk`（单例总入口）/ `OemLicenseProvider` / `OemLicenseSnapshot` | `com.paopaoime.aikeyboard.sdk.core` | oem-ime-sdk-core |
| `OemHostRuntime`（运行时初始化，必须先于 OemImeSdk 调用） | `com.paopaoime.aikeyboard.sdk.core` | **oem-ime-host-bridge**（不在 core！） |
| 宿主回调模式：`OemAiReplyHostDelegate` / `OemAiReplyRequest` / `OemAiReplyResult` / `OemMaterialHostDelegate` / `OemMaterialFetchResult` / `OemSessionArchiveListener` | `com.paopaoime.aikeyboard.sdk.core` | oem-ime-sdk-core |
| DrawerKit 全套：`OemDrawerKit` / `OemCustomDrawerSpec` / `OemDrawerContentFactory` / `OemDrawerController` / `OemAiReplyCapabilities` / `OemAiReplySuggestions` / `OemMaterialCapabilities` | `com.paopaoime.aikeyboard.sdk.core.drawer` | oem-ime-sdk-core |

注意：`sdk.core.OemAiReplyResult`（宿主回调结果，含 requestId/success/replies）与 `sdk.core.drawer.OemAiReplySuggestions`（自绘 UI 候选列表，含 suggestions/mode）语义不同、类名不同，按需 import，禁止通配符同时导入两个包。

公开演示联调请下载 https://www.paopaoime.com/oem/downloads/EnterpriseRemoteLicenseProvider.kt ，将 package 改为宿主包名。该连接器向配置的 apiBaseUrl 调用 /api/v1/oem/license/resolve 并读取真实授权结果；企业自有授权服务可替换同一 OemLicenseProvider 接口。禁止将 active() 占位结果作为授权验证通过的证据。网络授权初始化须在工作线程执行，并等待初始化完成后开放 SDK 入口。

宿主 Application 必须继承 `org.fcitx.fcitx5.android.FcitxApplication`：

```kotlin
class HostApp : FcitxApplication() {
    override fun onCreate() {
        super.onCreate()
        OemHostRuntime.initialize(this, true) // 必须在前，缺它会闪退（Launcher.instance.context 未初始化）
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
        OemImeSdk.initializeFromManifest(
            this,
            licenseProvider = EnterpriseRemoteLicenseProvider // 下载上方连接器并修改 package
            // 选填宿主回调：sessionArchiveListener / aiReplyHostDelegate / materialHostDelegate
            // 选填宿主预留：aiReplyTonePresets / aiReplySystemPrompt / aiReplyPromptAssembler
        )
        }
    }
}
```

Java 宿主等价模板（`initializeFromManifest` 共 11 参，Kotlin 默认参数在 Java 调用时必须全部显式传入；以下为当前 AAR 真实签名，逐参注释语义，直接可用，无需 javap 反推）：

```java
public class HostApp extends FcitxApplication {
    @Override public void onCreate() {
        super.onCreate();
        OemHostRuntime.INSTANCE.initialize(this, true); // 必须在前；Kotlin object 单例，Java 必须经 INSTANCE 调用
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
        OemImeSdk.INSTANCE.initializeFromManifest(
                this, // application
                EnterpriseRemoteLicenseProvider.INSTANCE, // 同上方 Kotlin 连接器
                null, // hostUserIdHash：宿主用户标识哈希，选填
                true, // autoInitializeAiRuntime：默认 true
                true, // initializeLogCollector：默认 true
                null, // sessionArchiveListener：会话存档宿主回调，选填
                null, // aiReplyHostDelegate：AI 回复宿主回调，选填
                null, // materialHostDelegate：素材库宿主回调，选填
                null, // aiReplyTonePresets：语气预设，选填
                null, // aiReplySystemPrompt：系统 prompt，选填
                null  // aiReplyPromptAssembler：prompt 组装器，选填
        );
        });
    }
}
```

- 初始化顺序固定：`super.onCreate()` → `OemHostRuntime.initialize(this, true)` → `OemImeSdk.initializeFromManifest(...)`；
- 必须以当前 AAR 真实公开 API 为准核对签名，不要凭旧文档猜测；
- AI 回复 Prompt 与语气已预留宿主自定义（aiReplyTonePresets / aiReplySystemPrompt / aiReplyPromptAssembler），需要时按官网「AI回复 → Prompt 与语气预留」接入。

## 11. 自动化套餐配置（已选 automation）

1. 仅在企业明确授权开启自动化时，Manifest 追加 `com.paopaoime.oem.enableAutomation=true`、`com.paopaoime.oem.enableAutomationSettingsEntry=false`（默认隐藏自动化设置入口；仅企业明确需要时改 true）与 `roro.stellar.permissions`（`tools:replace="android:value"`，值 `stellar,follow_stellar_startup`）；
2. 宿主申请 `android.permission.WRITE_SECURE_SETTINGS`：adb shell pm grant <包名> android.permission.WRITE_SECURE_SETTINGS；
3. 安装并启动 stellar-manager companion（直链见 §3.2 automation 模块）；
4. 宿主包名变更时自动化制品必须按新包名重新发布。

## 12. 构建 APK

```powershell
.\gradlew.bat --no-daemon :app:assembleRelease
```

宿主工程校验过严时可退一步：`--no-parallel --max-workers=1`。产物在 `app/build/outputs/apk/release/`。

## 13. 二次开发对接 SOP（API 模式与宿主回调 · 字段级参考）

> 本章面向「构建验收通过之后」的二次开发：例如宿主想自建页面测试素材库/AI 回复的宿主回调。全部契约以本章为准，无需查阅其他文档。
> 通用规则：
> - 回调接口与数据类均在包 `com.paopaoime.aikeyboard.sdk.core`，统一入口为单例 `OemImeSdk`；
> - 分发模式解析优先级（高→低）：`OemImeSdk` 运行时 setter ＞ Manifest meta-data ＞ 默认 `CLOUD_ONLY`；
> - **`CLOUD_ONLY` 模式下宿主回调完全不会被调用**；要让回调生效必须选 `HOST_ONLY`/`DUAL_WRITE` 并注册 delegate；
> - delegate 返回值语义统一：`true`=已接受、稍后经 callback 异步回传；`false` 或抛异常=拒绝（HOST_ONLY 向用户提示失败，DUAL_WRITE 自动回落域名模式）；callback 无论成败必须调用一次。

### 13.1 AI 回复（ai-reply）宿主回调

模式矩阵（Manifest key：`com.paopaoime.oem.aiReplyDispatchMode`）：

| 模式 | SDK 行为 | 宿主需提供 |
|---|---|---|
| CLOUD_ONLY（默认） | SDK 自调大模型：`aiMode=proxy` 向 apiBaseUrl 请求模型配置；`direct` 用 OpenAI 兼容三件套直连 | 无 |
| HOST_ONLY | SDK 只截屏+展示 UI，把截屏与组装好的 Prompt 交给宿主，宿主自调大模型并回传回复 | `OemAiReplyHostDelegate` |
| DUAL_WRITE | 优先宿主回调；未注册/拒绝/超时/失败自动回落域名模式 | 可选 |

注册入口（二选一或叠加）：初始化时 `OemImeSdk.initializeFromManifest(app, licenseProvider, ..., aiReplyHostDelegate = 你的实现)`；运行时 `OemImeSdk.setAiReplyHostDelegate(实现)`，`isAiReplyHostDelegateRegistered()` 查询。

接口原文：

```kotlin
fun interface OemAiReplyHostDelegate {
    fun onGenerateReplies(request: OemAiReplyRequest, callback: OemAiReplyResultCallback): Boolean
}
fun interface OemAiReplyResultCallback { fun onResult(result: OemAiReplyResult) }

data class OemAiReplyRequest(
    val requestId: String,                       // 本次请求唯一 ID（幂等/日志串联）
    val sessionId: String,                       // 面板会话 ID
    val generateMode: String,                    // FAST_LLM / OCR / IMAGE_REPLY / PASTE
    val toneKey: String,                         // 语气 key：friendly / concise / professional / formal
    val toneLabel: String,                       // 语气展示名
    val systemPrompt: String,                    // SDK 组装的系统提示词（可直接透传给大模型）
    val userPrompt: String,                      // SDK 组装的用户提示词（可直接透传）
    val ocrText: String,                         // 屏幕 OCR 文本（可能为空）
    val screenshots: List<OemAiReplyScreenshot>, // 截屏（JPEG Base64，不含 data: 前缀；FAST_LLM 可能多张连拍）
    val replyCount: Int,                         // 期望回复条数（建议值）
    val temperature: Double,                     // 采样建议值
    val topP: Double,                            // 采样建议值
    val maxTokens: Int,                          // 最大 token 建议值
    val timestamp: Long,
    val hostApplicationId: String,
)
data class OemAiReplyResult(
    val requestId: String,                       // 必须与请求一致
    val success: Boolean,
    val replies: List<String> = emptyList(),     // success=true 时必填
    val errorMessage: String? = null,
    val modelName: String? = null,               // 宿主模型名（面板展示）
)
```

Prompt/语气自定义（选填）：注册 `OemAiReplyPromptAssembler`（`assemble(context): OemAiReplyPromptPair`，返回 systemPrompt+userPrompt，空白时 SDK 回退内置模板）；`aiReplyTonePresets: List<OemDeveloperAiTonePreset(label, promptHint)>` 自定义语气 Tab；`aiReplySystemPrompt` 覆盖系统提示词。

对接问答（动手前 AI 应向用户确认）：1) 企业大模型网关地址/鉴权方式，还是先 mock？2) 是否消费 SDK 截屏（多模态）还是仅用文本 Prompt？3) 回复条数与语气 Tab 是否自定义？4) 失败策略选 HOST_ONLY（强宿主）还是 DUAL_WRITE（自动兜底）？

测试页 SOP：触发入口=键盘工具栏「AI 回复」按钮→面板点「极速生成/读屏回复」；观察=delegate 断点或 logcat（请求含 requestId）；成功标准=面板渲染宿主回传的 replies 列表并显示 modelName。若选购 developer 模块：设置页→开发者通道→AI 回复配置，可运行时切换分发模式复测。

### 13.2 会话存档（qa-session）宿主回调

模式矩阵（Manifest key：`com.paopaoime.oem.sessionArchiveMode`）：LOCAL_ONLY=仅 SDK 本地留存不外发；CLOUD_ONLY（默认）=仅上传企业服务端；HOST_ONLY=仅回调宿主；DUAL_WRITE=回调宿主并上传云端。

```kotlin
fun interface OemSessionArchiveListener {
    fun onSessionArchived(event: OemSessionArchiveEvent): Boolean  // true=已接收
}
data class OemSessionArchiveEvent(
    val eventId: String,            // 幂等去重
    val sessionId: String,
    val hostApplicationId: String,
    val targetPackageName: String,  // 被输入的目标应用包名
    val targetAppName: String,
    val maskedContent: String,      // 本地脱敏后内容
    val inputSource: String,
    val timestamp: Long,
    val policyState: String,
)
```

注册：`initializeFromManifest(..., sessionArchiveListener = 实现)` 或运行时 `setSessionArchiveListener`；触发时机=输入质检会话结束后由 SDK 后台任务按策略派发；主动派发可用 `dispatchSessionArchiveEvent(event)`。developer 模块的「会话存档」页可运行时切模式并查看本地留存记录。

### 13.3 素材库（material）宿主回调

模式矩阵（Manifest key：`com.paopaoime.oem.materialDispatchMode`）：

| 模式 | SDK 行为 | 宿主需提供 |
|---|---|---|
| LOCAL_ONLY | 不拉企业素材，抽屉只显示用户本地素材 | 无 |
| CLOUD_ONLY（默认） | 周期向 apiBaseUrl 拉企业素材列表 | 无 |
| HOST_ONLY | 经 delegate 向宿主拉取；宿主增删改经推送接口实时通知 SDK；无需 apiBaseUrl | `OemMaterialHostDelegate` |
| DUAL_WRITE | 优先宿主回调，失败回落域名模式 | 可选 |

导航模式（Manifest key：`com.paopaoime.oem.materialNavigationMode`）：`TYPE_ONLY`（默认，仅显示素材类型）/ `TYPE_PRIMARY_CATEGORY_SECONDARY`（类型一级、分类二级）/ `CATEGORY_PRIMARY_TYPE_SECONDARY`（分类一级、类型二级）/ `CATEGORY_ONLY`（仅显示分类，不显示类型）。

关键规则：

- `materialNavigationMode` 只控制「素材类型」与「企业分类」的层级关系，不改变 UI 样式；
- 要让分类导航真正显示，宿主必须注册 `OemMaterialTaxonomyHostDelegate` 并返回 `categories`；
- `tags` 只用于搜索命中和管理，不作为导航展示；
- 当宿主未返回任何分类时，不论配置哪种导航模式，SDK 都自动回退为 `TYPE_ONLY`。

```kotlin
fun interface OemMaterialHostDelegate {
    fun fetchRemoteMaterials(request: OemMaterialFetchRequest, callback: OemMaterialFetchCallback): Boolean
}
fun interface OemMaterialFetchCallback { fun onResult(result: OemMaterialFetchResult) }

interface OemMaterialTaxonomyHostDelegate {
    fun fetchMaterialTaxonomy(request: OemMaterialTaxonomyFetchRequest, callback: OemMaterialTaxonomyFetchCallback): Boolean
    fun mutateMaterialTaxonomy(request: OemMaterialTaxonomyMutationRequest, callback: OemMaterialTaxonomyMutationCallback): Boolean
}
fun interface OemMaterialTaxonomyFetchCallback { fun onResult(result: OemMaterialTaxonomyFetchResult) }
fun interface OemMaterialTaxonomyMutationCallback { fun onResult(result: OemMaterialTaxonomyMutationResult) }

data class OemMaterialFetchRequest(
    val requestId: String,          // 回传时必须一致
    val limit: Int,                 // 建议条数上限
    val hostApplicationId: String,
    val timestamp: Long,
)
data class OemMaterialFetchResult(
    val requestId: String,
    val success: Boolean,
    val materials: List<OemRemoteMaterialItem> = emptyList(), // 全量语义：SDK 整体替换本地缓存
    val errorMessage: String? = null,
)
data class OemRemoteMaterialItem(
    val materialId: String,                     // 必填，增删改匹配键
    val sourceLabel: String = "企业下发",
    val createdAt: Long, val updatedAt: Long,   // 抽屉按 updatedAt 倒序
    val parts: List<OemRemoteMaterialPart>,
)
sealed class OemRemoteMaterialPart {
    data class Text(val partId: String, val text: String) : OemRemoteMaterialPart()
    data class Attachment(
        val partId: String, val displayName: String,
        val mimeType: String,                   // image/png、video/mp4、application/pdf…
        val attachmentType: String,             // IMAGE / VIDEO / DOCUMENT
        val downloadUrl: String,                // https URL 或 FileProvider content:// URI
        val fileSizeBytes: Long = 0L,
    ) : OemRemoteMaterialPart()
}

data class OemMaterialTaxonomyFetchResult(
    val requestId: String,
    val success: Boolean,
    val categories: List<OemMaterialTaxonomyItem> = emptyList(), // 驱动首页分类导航
    val tags: List<OemMaterialTaxonomyItem> = emptyList(),       // 仅用于搜索命中
    val errorMessage: String? = null,
)
data class OemMaterialTaxonomyItem(
    val taxonomyId: String,
    val name: String,
    val sort: Int = 0,
    val enabled: Boolean = true,
)
data class OemMaterialTaxonomyMutationRequest(
    val requestId: String,
    val action: OemMaterialTaxonomyMutationAction, // CREATE / UPDATE / DELETE
    val target: OemMaterialTaxonomyTarget,         // CATEGORY / TAG
    val hostApplicationId: String,
    val timestamp: Long,
    val item: OemMaterialTaxonomyItem? = null,
    val taxonomyId: String? = null,
)
```

注册入口：`initializeFromManifest(..., materialHostDelegate = 实现, materialTaxonomyHostDelegate = 实现)`；或运行时 `setMaterialHostDelegate` / `setMaterialTaxonomyHostDelegate` / `setMaterialNavigationMode`。状态查询：`isMaterialHostDelegateRegistered()` / `isMaterialTaxonomyHostDelegateRegistered()` / `getMaterialNavigationMode()`。

宿主推送接口（增删改实时生效）：`OemImeSdk.notifyRemoteMaterialUpserted(item)` / `notifyRemoteMaterialRemoved(materialId)` / `invalidateRemoteMaterialCatalog()`（全量重拉，会再次触发 delegate）。

对接问答（动手前 AI 应向用户确认）：1) 素材数据源=企业后台 API 还是本地 mock？2) 附件存储方式与 downloadUrl 形态（https 还是 content://）？3) 首页导航想要哪种模式（类型优先 / 分类优先 / 仅分类）？4) 分类和标签是否都由宿主管理，是否需要抽屉内增删改查？

测试页 SOP（如「素材回调测试页」）：页面放三块能力——1) 推送一条 Upsert / Remove / Invalidate；2) 分类 / 标签全量拉取日志；3) 分类 / 标签增删改结果日志。触发链路=键盘工具栏「素材库」按钮打开抽屉→SDK 调 `fetchRemoteMaterials` 与 `fetchMaterialTaxonomy`→抽屉渲染素材与分类；成功标准=配置分类优先或仅分类模式时，返回的 `categories` 可驱动首页导航；搜索命中 `tags` 后可返回对应素材。

### 13.4 授权与状态查询（通用）

```kotlin
fun interface OemLicenseProvider {
    fun fetchLicense(context: Context, request: OemLicenseRequest): OemLicenseSnapshot
}
// OemLicenseSnapshot(status, licenseId, message, expiresAtMillis, grantedCapabilities)
//   工厂：OemLicenseSnapshot.active() / grace() / missing() / invalid(msg)…
// OemLicenseStatus：ACTIVE/GRACE 允许运行；EXPIRED/DISABLED/FULL/NOT_OPENED/MISSING/INVALID 拒绝

// 运行时查询：
OemImeSdk.getStatus()                    // OemSdkStatus：initialized/capabilities/license…
OemImeSdk.isCapabilityAvailable(cap)     // AI / AUTOMATION / UI / MATERIAL_LIBRARY / DEVELOPER
OemImeSdk.isAiReplyEnabled()             // featureToggles.aiReply && AI && UI
OemImeSdk.isMaterialLibraryEnabled()
OemImeSdk.refreshLicenseAsync(ctx, reason) / refreshLicenseNow(ctx, reason)
```

### 13.5 调试日志管理（OemLog，默认关闭）

SDK 内置统一日志门面 `OemLog`（包 `com.paopaoime.aikeyboard.sdk.core`），覆盖 SDK 初始化、授权、素材库拉取/同步、AI 回复、抽屉路由与自定义内容降级等关键链路。总开关**默认关闭且零开销**，仅在集成联调阶段按需开启：

开启方式（三选一，任一生效即可）：

1. Manifest meta-data：`<meta-data android:name="com.paopaoime.oem.enableDebugLog" android:value="true" />`（随包固定开启，适合内部联调版）；
2. 初始化前配置：`OemSdkConfig.enableDebugLog = true`（代码控制，需在 `OemImeSdk.initializeFromManifest` 之前）；
3. 运行时动态开关：`OemLog.setEnabled(true/false)`（可做宿主调试面板开关，即时生效）。

宿主接收日志（可选）：注册 `OemLog.setListener { level, tag, message, throwable -> ... }` 后，全部 SDK 日志会回调给宿主（回调在日志产生线程，宿主自行切线程/落盘）；不再需要时 `OemLog.setListener(null)`。SDK 自身不落盘、不上传任何日志。

注意：

- 正式发布版建议关闭总开关（默认即关闭）；日志内容可能含素材/会话链路标识，不建议在未脱敏情况下写入用户可见存储；
- 排障路径：自定义素材库 / 自定义抽屉 / AI 回复集成异常时，先开 `enableDebugLog` 复现一次，观察素材库路由（如 `material hub launch routed ...`）、抽屉打开/关闭/降级等关键行定位问题方（宿主内容异常时 SDK 会自动降级内置 UI 并打日志）。

## 14. DrawerKit 自定义 UI 开发（面向 AI 对话开发）
> 本章面向 AI 对话式开发：开发者向 AI 工具说「给键盘的 AI 回复做自定义界面」「做一个企业工具箱抽屉」时，AI 直接按本章片段执行。所有代码可直接复制；API 均在包 `com.paopaoime.aikeyboard.sdk.core.drawer`，统一入口为单例 `OemImeSdk`。
> 前置条件（缺一不可）：① core 模块 `enableUi=true`；② 已选 material 切片模块——抽屉基座（MaterialHubActivity）打包在 `oem-ime-sdk-material` 内，未选该模块时本章全部不可用；③ 自定义 UI 生效还要求对应 L1 开关打开（见下方三层模型小节）。

### 14.1 DrawerKit 三层模型与通用规则

| 层级 | 含义 | 宿主动作 |
|---|---|---|
| L0 默认 | 内置抽屉 UI（AI 回复/素材库官方界面） | 无 |
| L1 自定义UI交互 | 抽屉基座（头部返回/关闭、容器、手势、主题）由输入法保留，内容层完全交给宿主自绘；能力通过能力字段调用，UI 与能力解耦 | Manifest 开关 + `setDrawerContentFactory` |
| L2 自定义抽屉 | 宿主注册全新空白抽屉（自带标题/图标/内容）；默认不暴露，只有显式开启后入口才进入键盘工具面板宫格末尾与顶部工具条，并受定制工具栏排序/显隐管理（最多 4 个） | `enableCustomDrawers=true` + `registerCustomDrawer` |

L1 Manifest 开关（默认 false；运行时覆盖 > Manifest > 默认）：

```xml
<!-- AI 回复「自定义UI交互」：默认 false；只有明确需要宿主自绘 AI 回复抽屉时才改 true -->
<meta-data android:name="com.paopaoime.oem.enableAiReplyCustomUi" android:value="false" />
<!-- 素材库「自定义UI交互」：默认 false；只有明确需要宿主自绘素材库抽屉时才改 true -->
<meta-data android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi" android:value="false" />
<!-- 用户自定义抽屉：默认 false；只有明确需要暴露自定义抽屉入口时才改 true -->
<meta-data android:name="com.paopaoime.oem.enableCustomDrawers" android:value="false" />
```

基座契约（L1/L2 通用）：
- 抽屉基座保留标准头部（返回/关闭），头部按钮位置与内置抽屉一致；返回按钮显隐由宿主在内容工厂内 `controller.setBackVisible(visible)` 配置；
- 宿主内容抛异常或工厂未注册时，自动降级回内置 UI，不会崩溃；宿主可通过 `onHostContentError(error)` 回调感知降级；
- `onCreateContentView(...)` 运行在主线程，必须只做“轻量根 View 创建 + 事件绑定”；网络请求、数据库/文件扫描、大列表 diff、位图解码、接口首刷都必须异步执行。若在这里同步做重活，`MaterialHubActivity` 可能在自定义 View 首帧前直接 ANR（典型现象：还没进入自定义页就卡死/提示 `Application does not have a focused window`）；
- 生命周期回调：`onDrawerStateChanged(OemDrawerState.OPENED/CLOSED/BACK)`；宿主拦截返回键用 `onBackPressed(): Boolean`（true=已消费）；
- 主动开合（任意宿主页面可用，无需键盘弹出）：`OemImeSdk.openDrawer(drawerId)` / `OemImeSdk.closeDrawer(drawerId)`；
- 运行时查询开关生效值：`OemImeSdk.isAiReplyCustomUiEnabled()` / `isMaterialLibraryCustomUiEnabled()`。
- 版本纪律：只要本次发布改动了 SDK 代码、Manifest 契约、文档模板、AAR/POM 依赖关系或固定版本传递依赖内容，就必须同步升级发布版本号；禁止“同版本覆盖重发”。

```kotlin
interface OemDrawerContentFactory {
    fun onCreateContentView(context: Context, controller: OemDrawerController): View
    fun onDrawerStateChanged(state: OemDrawerState) {} // OPENED / CLOSED / BACK
    fun onBackPressed(): Boolean = false                 // true=宿主消费返回键
    fun onHostContentError(error: Throwable) {}          // 宿主内容异常→降级内置UI时回调
}
interface OemDrawerController {
    fun closeDrawer()                    // 关闭当前抽屉
    fun openDrawer(drawerId: String)     // 互跳内置/已注册抽屉
    fun setTitle(title: CharSequence?)   // 覆盖基座头部标题
    fun setBackVisible(visible: Boolean) // 配置头部返回按钮显隐
    fun currentTheme(): OemDrawerTheme   // LIGHT / DARK，自绘内容适配主题用
}
```

### 14.2 片段 · AI 回复自定义 UI（L1）

1. Manifest：默认保持 `com.paopaoime.oem.enableAiReplyCustomUi=false`；只有明确要接管 AI 回复抽屉内容层时，才改为 `true`；
2. Application 初始化后注册内容工厂（drawerId 固定 `OemDrawerKit.DRAWER_AI_REPLY`）：

```kotlin
OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_AI_REPLY, object : OemDrawerContentFactory {
    private var controller: OemDrawerController? = null

    override fun onCreateContentView(context: Context, controller: OemDrawerController): View {
        this.controller = controller
        controller.setTitle("AI 回复")     // 覆盖基座头部标题
        controller.setBackVisible(true)    // 头部返回按钮可配
        return buildMyAiReplyView(context) // 宿主自绘内容（能力调用见下表）
    }

    override fun onDrawerStateChanged(state: OemDrawerState) {
        if (state == OemDrawerState.CLOSED) controller = null // 释放引用
    }
});
// 恢复内置 UI：OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_AI_REPLY, null)
```

### 临时剪贴板高情商回复（1.0.0.202610111）

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
3. 能力字段（UI 与能力解耦，自绘界面全部经 `OemImeSdk.aiReplyCapabilities()` 调用；返回 null=能力未就绪，需提示并降级）：

```kotlin
interface OemAiReplyCapabilities {
    fun startPasteSession(listener: OemAiReplyListener)   // 剪贴板会话理解
    fun startFastGenerate(listener: OemAiReplyListener)   // 极速生成
    fun startScreenReply(listener: OemAiReplyListener)    // 屏幕理解回复（先过截屏授权）
    fun requestScreenPermission(listener: OemPermissionListener) // 截屏授权，onResult(granted)
    fun openAlbumPicker(listener: OemAlbumPickerListener) // 相册选择，onImagesSelected(uris)
    fun startImageReply(uris: List<Uri>, listener: OemAiReplyListener)
    fun setTone(toneId: String)                           // 切换语气
    fun tonePresets(): List<OemTonePreset>                // 语气列表 (id,label)
    fun regenerate(listener: OemAiReplyListener)          // 换一批
    fun footerText(): String                              // 合规底纹文案（自绘页必须展示）
    fun isRestricted(): Boolean                           // 配置关闭时为 true，应显示受限态
}
// 结果回调：OemAiReplyListener.onProgress(stepIndex/stepLabel/status) → onSuccess(OemAiReplySuggestions(suggestions,mode,modelName)) / onError(message)
```

验收标准：键盘工具栏点「AI 回复」打开的是宿主自绘界面；头部返回/关闭可用；四种生成方式（剪贴板/极速/读屏/相册图片）进度与结果正常渲染；`footerText()` 文案已展示；工厂未注册或开关关闭时回退内置 UI。

### 14.3 片段 · 素材库自定义 UI（L1）

当前版本已经按真机闭环验证通过。客户只需要完成 3 件事：1）开启素材库自定义开关；2）在宿主侧注册 `material_hub` 内容工厂；3）在宿主 View 上实现分类、列表、详情、编辑、追加、删除和发送。

#### 1. 开关配置

- 继续使用默认素材库：`com.paopaoime.oem.enableMaterialLibrary=true` 且 `com.paopaoime.oem.enableMaterialLibraryCustomUi=false`。
- 启用宿主自定义素材库：`com.paopaoime.oem.enableMaterialLibrary=true` 且 `com.paopaoime.oem.enableMaterialLibraryCustomUi=true`。
- 两个开关都开但未注册内容工厂：SDK 自动容错回落到默认素材库。

```xml
<!-- 既有素材库总开关 -->
<meta-data
    android:name="com.paopaoime.oem.enableMaterialLibrary"
    android:value="true" />

<!-- 素材库自定义 UI 开关 -->
<meta-data
    android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi"
    android:value="true" />
```

#### 2. 如何把宿主 View 传给素材库抽屉

宿主自定义模式下，**自定义 View 的传递位置固定在 Application.onCreate**：SDK 初始化后调用 `OemImeSdk.setDrawerContentFactory(OemDrawerKit.DRAWER_MATERIAL_HUB, factory)`；factory 的 `onCreateContentView(context, controller)` 返回的 View，就是挂到素材库抽屉基座内容区的宿主 View。

```kotlin
object CompanyDrawerKitSetup {
    fun install(context: Context) {
        OemImeSdk.setDrawerContentFactory(
            OemDrawerKit.DRAWER_MATERIAL_HUB,
            MaterialCustomUiFactory()
        )
    }
}

private class MaterialCustomUiFactory : OemDrawerContentFactory {
    override fun onCreateContentView(context: Context, controller: OemDrawerController): View {
        controller.setTitle("素材库")
        controller.setBackVisible(false)
        return buildYourMaterialRootView(context, controller)
    }
}
```

说明：SDK 只保留抽屉基座（容器、头部、关闭、返回、手势）；首页、详情页、编辑页、空态、骨架屏都由宿主 View 自己决定。

#### 3. 如何在抽屉上显示分类组件与素材列表

```kotlin
private fun refreshHomeData(context: Context) {
    val caps = OemImeSdk.materialCapabilities() ?: return

    caps.observeFolders(object : OemFolderListener {
        override fun onFolders(folders: List<OemMaterialFolder>) {
            this@MaterialCustomUiFactory.folders = folders
            render(context)
        }
    })

    caps.queryMaterials(
        OemMaterialQuery(
            category = selectedCategory,
            folderId = selectedFolderId,
            page = 0,
        ),
        object : OemMaterialListener {
            override fun onPage(page: OemMaterialPage) {
                materials = page.items
                render(context)
            }
        }
    )
}
```

- 分类 Tab：通常使用 `OemMaterialCategory` 驱动。
- 文件夹条 / 业务分组条：通常使用 `observeFolders(...)` 返回的 `OemMaterialFolder` 渲染。
- 列表卡片：通常展示 `OemMaterialItem.title`、`kind`、`sizeText`、`timeText`、`source`。

#### 4. 如何通过素材列表进入素材详情

```kotlin
private fun openDetail(context: Context, item: OemMaterialItem) {
    val caps = OemImeSdk.materialCapabilities() ?: return
    caps.loadDetail(item.id, object : OemMaterialDetailListener {
        override fun onDetail(detail: OemMaterialDetail) {
            selectedItem = item
            selectedDetail = detail
            currentMode = ScreenMode.DETAIL
            render(context)
        }
    })
}

private fun buildDetailPartCard(part: OemMaterialPart): View {
    return when (part.type) {
        OemMaterialPartType.TEXT -> buildTextCard(part.text.orEmpty())
        OemMaterialPartType.IMAGE,
        OemMaterialPartType.FILE,
        OemMaterialPartType.VIDEO -> buildAttachmentCard(
            title = part.title ?: "未命名附件",
            filePath = part.filePath,
            mimeType = part.mimeType,
            sizeText = part.sizeText,
        )
    }
}
```

详情页的数据核心是 `OemMaterialDetail.parts`。每一组文本、图片、文件、视频内容都由宿主自己决定怎样展示；SDK 只负责把结构化数据提供出来。

#### 5. 如何对素材详情做发送、编辑、删除、追加

发送建议统一走 SDK 默认发送链路，不要自己重写：

```kotlin
private fun sendCurrentItem(item: OemMaterialItem) {
    val sent = OemImeSdk.materialCapabilities()?.sendToInput(item) == true
    if (!sent) {
        // 提示发送失败
    }
}
```

编辑 / 删除 / 追加分组的最小写法：

```kotlin
private fun saveEditedDetail(itemId: String, editingParts: List<OemMaterialPart>) {
    OemImeSdk.materialCapabilities()?.updateDetail(
        itemId,
        editingParts,
        object : OemMaterialMutationListener {
            override fun onSuccess(item: OemMaterialItem) {}
        }
    )
}

private fun deleteCurrentItem(itemId: String) {
    OemImeSdk.materialCapabilities()?.delete(
        itemId,
        object : OemMaterialDeleteListener {
            override fun onSuccess() {}
        }
    )
}

private fun appendText(currentItem: OemMaterialItem, detail: OemMaterialDetail, text: String) {
    val newPart = OemMaterialPart(
        id = "text_" + System.currentTimeMillis(),
        type = OemMaterialPartType.TEXT,
        text = text,
        title = text.take(40),
    )
    OemImeSdk.materialCapabilities()?.updateDetail(
        currentItem.id,
        detail.parts + newPart,
        object : OemMaterialMutationListener {
            override fun onSuccess(item: OemMaterialItem) {}
        }
    )
}

private fun appendAttachment(type: OemPickerType) {
    OemImeSdk.materialCapabilities()?.pickAndSave(
        type,
        object : OemMaterialMutationListener {
            override fun onSuccess(tempItem: OemMaterialItem) {
                // 读取 tempItem 详情后，把 tempItem.parts 合并进当前 detail.parts
                // 再次调用 updateDetail(currentItem.id, mergedParts, ...)
                // 最后 delete(tempItem.id, ...) 清理临时素材
            }
        }
    )
}
```

#### 6. 关键能力字段与界面落位

- `queryMaterials(query, listener)`：首页列表、分类切换后的列表区、搜索结果区。
- `observeFolders(listener)`：顶部文件夹条、业务分组筛选条、侧边过滤器。
- `loadDetail(itemId, listener)`：素材详情页主体、多分组内容区。
- `saveText(text, listener)`：新增纯文本素材。
- `pickAndSave(type, listener)`：新增图片 / 文件 / 视频，或在详情页追加媒体分组。
- `updateDetail(itemId, parts, listener)`：编辑排序、删除某个分组后的保存、追加分组后的提交。
- `delete(itemId, listener)`：详情页删除按钮。
- `sendToInput(item)`：详情页主发送按钮、首页快捷发送按钮。

#### 7. 验收标准

- 键盘工具栏点「素材库」后，打开的是宿主自绘界面，不再跳回默认素材库页面。
- 分类、文件夹、素材列表、详情页、编辑页、追加分组页都由宿主 View 承载。
- 文本编辑页可以正常弹起键盘并保存。
- 图片 / 文件 / 视频追加后仍停留在宿主详情页，新增分组可见且可发送。
- `sendToInput(item)` 成功把内容送入当前输入链路；发送失败时宿主可提示，但不要自造发送链路。
- 工厂未注册、开关关闭或宿主内容异常时，SDK 能自动降级回默认素材库。

### 14.4 片段 · 空白自定义抽屉（L2）

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



注册即自动获得入口：键盘工具面板宫格末尾 + 顶部工具条（占工具条配额），并纳入定制工具栏的排序/显隐管理；图标按原色渲染。上限 4 个，id 不可为 `ai_reply`/`material_hub`。

```kotlin
val spec = OemCustomDrawerSpec(
    id = "my_tools",                      // 唯一 id（小写下划线，不含内置 id）
    title = "企业工具箱",                  // 宫格/工具条/抽屉头部展示名
    icon = ContextCompat.getDrawable(context, R.drawable.ic_my_tools), // 或 iconRes = R.drawable.ic_my_tools
    contentFactory = object : OemDrawerContentFactory {
        private var controller: OemDrawerController? = null
        override fun onCreateContentView(context: Context, controller: OemDrawerController): View {
            this.controller = controller
            controller.setTitle("企业工具箱")
            controller.setBackVisible(false) // 自定义抽屉通常隐藏返回，只留关闭
            return buildMyDrawerView(context)
        }
        override fun onDrawerStateChanged(state: OemDrawerState) {
            if (state == OemDrawerState.CLOSED) controller = null
        }
    },
)
when (val result = OemImeSdk.registerCustomDrawer(spec)) {
    OemRegisterResult.OK -> {}               // 成功，入口即时出现
    OemRegisterResult.DUPLICATE_ID -> {}     // 已存在，改调 updateCustomDrawer(spec)
    OemRegisterResult.QUOTA_EXCEEDED -> {}   // 已达 4 个上限
    OemRegisterResult.INVALID_ID -> {}       // id 非法（空或占用内置 id）
}
// 维护：OemImeSdk.updateCustomDrawer(spec) / unregisterCustomDrawer(id) / customDrawers() / findCustomDrawer(id)
// 主动开合：OemImeSdk.openDrawer("my_tools") / OemImeSdk.closeDrawer("my_tools")
```

验收标准：注册后下次弹出键盘，工具面板宫格末尾与工具条出现该入口；点击打开宿主自绘抽屉；定制工具栏设置页可对其排序/隐藏；`unregisterCustomDrawer` 后入口同步消失。

### 14.5 集成提问与验收

AI 执行本章前，建议先向开发者确认（缺省值可直接继续）：

1. 自定义哪个抽屉？（AI 回复 / 素材库 / 全新空白抽屉，可多选）
2. 抽屉头部是否需要返回按钮？（`setBackVisible`）
3. 抽屉标题文案？（默认取入口标题）
4. 空白抽屉的入口图标资源名？（缺省由 AI 生成矢量图）

完成后按下一章「安装与验收」执行真机验收；DrawerKit 专项验收以各片段的验收标准为准。

## 15. 安装与验收

SDK 输入法服务全名固定为 `com.paopaoime.aikeyboard.ime.service.AiInputMethodService`：

```powershell
adb uninstall <host_application_id>
adb install -r "<最新 release apk 绝对路径>"
adb shell ime enable <host_application_id>/com.paopaoime.aikeyboard.ime.service.AiInputMethodService
adb shell ime set <host_application_id>/com.paopaoime.aikeyboard.ime.service.AiInputMethodService
```

国产 ROM 安装 FAQ（OPPO/ColorOS、vivo 等，实测）：
1. `adb install` 返回 `Failure [-99]`：改用 `adb push <apk> /data/local/tmp/x.apk` + `adb shell pm install -r -t /data/local/tmp/x.apk`；
2. `pm install` 长时间无输出：设备前台可能在「安装增强防护」引导页等待确认，按「更多 → 开始深度扫描 → 继续安装」走完扫描即可；
3. 提示"解析程序包时出现问题"：多为安装期间熄屏导致会话中断（文件本身完好），保持亮屏（`adb shell svc power stayon true`）后重试；
4. 同包名二次安装（更新）不会触发引导，可直接 `pm install -r`；
5. `adb shell ime set` 后偶发被系统切回原输入法：重新执行 ime set 即可（部分 ROM 会冻结后台 IME 进程）；
6. `WRITE_SECURE_SETTINGS`（宿主程序化启用/切换 IME 用）在 Android 16 上无法通过 `pm grant` 授予：属正常现象，键盘功能不受影响，引导页应降级为跳转系统设置。

验收标准：
1. APK 安装成功，`dumpsys package` 中 `extractNativeLibs=true`；
2. 宿主 App 启动不闪退，且启动后进入 SDK 接入向导或设置页（SplashActivity 路由），不是自绘调试页；
3. 输入法可启用、可切换、可进入首页；切为默认输入法后至少真实弹起一次键盘；`adb logcat -b crash -d` 无新崩溃；
4. 已选功能入口可见可用，未选功能入口隐藏；AI 回复 / 素材库按所选分发模式工作；
5. `api_base_url` 为企业域名时，输入会话可在企业服务端查询。

## 16. 必须检查项

- [ ] settings.gradle 已加 ECS Maven 仓（或离线镜像）与 jitpack，plugins 块未包含 org.jetbrains.kotlin.android
- [ ] app 依赖已含 `oem-ime-host-bridge` 与 `org.fcitx.fcitx5.android:app:1.13`
- [ ] `org.fcitx.fcitx5.android:app:1.13` 的最终解析结果来自 `https://paopaoime.com/oem/sdk-repo`，不是历史本地仓 / 私有镜像 / 旧缓存
- [ ] `packaging.jniLibs.useLegacyPackaging = true` 已写入，白名单未扩大
- [ ] Manifest 必填 meta 已写入，`tools:replace` 时根节点已声明 xmlns:tools；选购 ai/material 时 `enableUi=true`
- [ ] 宿主 strings.xml 已覆盖 `oem_ime_display_name`（输入法显示名）
- [ ] `oem_ime_display_name` 仅保留一份默认定义，`values-zh` 与别名字符串未重复定义同一品牌值
- [ ] HostApp 继承 FcitxApplication，初始化顺序正确
- [ ] 已创建「输入法接入引导页」且含降级逻辑
- [ ] 本次发布版本号已升级；若变更了固定版本依赖内容（如 fcitx app / 传递依赖 POM），对应依赖坐标也已同步升级，未做同版本覆盖重发
- [ ] assembleRelease 成功且 release APK 已生成
- [ ] 安装后包验证 extractNativeLibs=true
- [ ] 真机已完成三段验收：App 冷启动、切默认输入法、真实弹键盘；不能只验 assemble 成功或只验 App 能打开
- [ ] 若使用 DrawerKit 自定义 UI：manifest 已开对应 L1 开关，宿主在 Application.onCreate 通过 `setDrawerContentFactory(...)` 传入 View 工厂，能力字段全部经 `OemImeSdk.*Capabilities()` 调用（未直连内部实现）
- [ ] 若使用 DrawerKit 自定义 UI：`onCreateContentView(...)` 只做轻量 View 创建，重数据加载已异步化；真机验证“打开抽屉能进宿主页、不在首帧前 ANR”

## 17. 常见失败与处理

1. 依赖找不到：核对 §3 直链是否 200；确认 settings.gradle 仓库地址拼写；jitpack 是否已加。
1.1. 首次联网、Java toolchain、wrapper 镜像或 Gradle 缓存失败：按 §4.1 分类处理。每次修复后重跑相同失败任务，再继续完整 assembleRelease；文档若缺步骤，先更新文档再用最新官网下载副本复验。
2. 启动即闪退：检查 HostApp 是否继承 FcitxApplication、是否先 `OemHostRuntime.initialize(this, true)`。
3. 切换输入法后闪退：检查是否显式依赖 `org.fcitx.fcitx5.android:app:1.13`（报 `FcitxApplication has not been created!` 即此因）。
3.1. 启动即崩溃并报 `NoSuchMethodError: FavoriteBridge.bindHostShare` / `bindHostInputRestorePolicy`：当前解析到的是旧的 `org.fcitx.fcitx5.android:app:1.13` 之前的历史二进制。处理顺序：① 删除本地 Maven 仓与 Gradle 缓存中旧版 `org/fcitx/fcitx5/android/app` 目录；② 删除对应 transforms 缓存；③ 重新构建；④ 用 `./gradlew app:dependencyInsight --dependency org.fcitx.fcitx5.android:app` 确认实际解析来源是 `https://paopaoime.com/oem/sdk-repo`。
4. 进首页后 native 闪退：检查 `useLegacyPackaging = true` 与 extractNativeLibs。
5. direct/dual 无回复：补全 `aiOpenAiBaseUrl/aiOpenAiApiKey/aiOpenAiModel` 三项。
6. 无法访问 sdk_repo_url：改用 §3.3 离线镜像方案。
7. 键盘工具栏没有 AI 回复/素材库入口：检查 `enableUi` 是否为 true（false 时仅基础键盘）。
8. 输入法选择器显示的名称不对：检查宿主 strings.xml 是否覆盖了 `oem_ime_display_name`。
9. `adb install` 报 `Failure [-99]`：见安装与验收节的国产 ROM 安装 FAQ。
10. 报 "Kotlin support is built into AGP"：移除 app 模块的 `org.jetbrains.kotlin.android` 插件（AGP 9.x 已内置 Kotlin）。
11. 打开 App 黑屏且 logcat 出现 `You need to use a Theme.AppCompat theme (or descendant) with this activity`：宿主 `<application android:theme>` 使用了 framework 主题（如 `@android:style/Theme.DeviceDefault`），按 §9 主题约束改为 AppCompat 系谱主题后重建。
12. 安装后切换输入法或打开 App 即崩溃，logcat 出现 `NoClassDefFoundError: ...SpillingKt`：kotlin-stdlib 被降级到 2.1.0 以下（典型为构建脚本里 `resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.0.x")`），删除该 force/钉版配置让 Gradle 自然解析到最高版本后重建。
13. 打开素材库自定义页还没进入宿主 View 就 ANR：宿主在 `onCreateContentView(...)` 里同步做了网络/读盘/大布局初始化。处理顺序：① 工厂里只返回轻量根 View；② 首屏先展示 loading/骨架；③ 数据拉取、列表构建、图片解码改为协程/异步；④ 再次真机验证“开抽屉 → 看到宿主页 → 再加载数据”。
14. 自定义抽屉/AI 回复自绘界面不生效：依次核对 ① manifest 的 `enableAiReplyCustomUi` / `enableMaterialLibraryCustomUi` 已开；② `setDrawerContentFactory` / `registerCustomDrawer` 在 `OemHostRuntime.initialize` 之后调用；③ 注册返回值不是 QUOTA_EXCEEDED（上限 4 个）/ DUPLICATE_ID（改用 updateCustomDrawer）；④ 已选 material 切片模块（抽屉基座缺失则默认展示不可用，entryHandler 纯接管不受此限制）。宿主内容崩溃会自动降级内置 UI，可经 `onHostContentError` 感知；仍无法定位时按本章「调试日志管理（OemLog）」小节开启日志复现一次，观察路由与降级关键行。
15. 点击键盘设置子页崩溃（ClassCastException）：升级 SDK 至统一版本 1.0.0.20260816 及以上（对应旧 host-bridge 1.2.0-alpha17 的修复，旧版布局预览控件类型不匹配已修复）。
16. 下载的 AAR 与官网内容不一致（如缺少 DrawerKit 类）：Gradle 按坐标缓存制品，官网同版本覆盖重发后本地缓存不会自动更新，`--refresh-dependencies` 也可能因 transforms 缓存失效不了。处理：删除 `~/.gradle/caches/modules-2/files-2.1/com.paopaoime.oem/` 与 `~/.gradle/caches/<gradle版本>/transforms/` 下对应条目（或整个 transforms 目录）后重新构建；必要时对 AAR 做 unzip 抽查 classes.jar 确认类存在。
17. Kotlin 实现 SDK 回调接口时 lambda 参数推断失败：不要依赖 SAM/lambda 推断，直接用 `object : OemXxxListener { override fun onXxx(...) }` 显式实现并写全参数类型；同时避免对 `sdk.core` 与 `sdk.core.drawer` 两个包同时通配符 import（历史存在同名类，现 drawer 包结果类已更名 `OemAiReplySuggestions`）。


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
