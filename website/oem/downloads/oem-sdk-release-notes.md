# SDK 1.0.0.20261012

新增自定义抽屉 entryHandler 和剪贴板 OemClipboardQuickReplyHandler，支持宿主完全接管入口：主线程回调、忽略返回值、异常静默，不收键盘或启动 SDK 页面。OEM 剪贴板去掉标题与叉号，按钮改“智能回复”，工具条加高 12 布局单位。未注册保持原 SDK 流程；八公开 SDK Release/R8，runtime app:1.13。

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

## 历史记录

# 外部版版本说明

## 当前版本 1.0.0.202610111

### 2026-10-12 同版本更新

- 高情商快捷回复改为独立面板，匹配日光瓷白、夜幕石墨的颜色与材质；生成按钮采用星光图标，上下右等距。
- 修复皮肤固定尺寸覆盖键盘调节值的问题，支持直接拖动高度、宽度并保存；重置回到皮肤原始尺寸。
- 新用户默认标准按键声音、音量 50%、触感较强；保留用户已保存的设置。
- 本轮按项目方要求保持 SDK 和运行时版本不变。已接入相同坐标的客户须替换完整 Maven 仓库，并以 `--refresh-dependencies` 重新构建；以最新交付 manifest 的 SHA-256 确认制品，不能只比较版本号。
- 模型配置、客户配置文件和 DrawerKit 自绘接口沿用本版本既有能力。


- 当前宿主复制后展示一次临时剪贴板和高情商回复，关闭、打字或展示后收起即消费；跨宿主不重放。
- 快捷入口直接进入 AI 回复结果抽屉，选中仅回填原宿主草稿，不触发发送。
- 复用客户原有模型配置和配置文件；DrawerKit 内容层与整块自绘均支持快捷状态、原文重试和草稿回填。
- 八公开 SDK 全量 Release/R8；企业同时升级 SDK `1.0.0.202610111` 与运行时 `org.fcitx.fcitx5.android:app:1.12`。自定义抽屉需按新版接口渲染快捷状态，标准 UI 无需新增配置。

## 版本策略

- 自客户交付阶段起，OEM SDK 采用**统一版本号**：格式 `1.0.0.<构建号>`，构建号为日期戳（`yyyyMMdd`）；同一天多次发布时在末尾追加序号（如 `202608151`）
- 版本号唯一定义在 `OEM/android/gradle.properties`（`oemSdkVersion`）；`com.paopaoime.oem` 组下全部制品（`oem-ime-sdk-core` / `oem-ime-host-bridge` / `oem-ime-sdk-ai` / `oem-ime-sdk-ai-reply` / `oem-ime-sdk-material` / `oem-ime-sdk-automation` / `oem-ime-sdk-developer` / `oem-ime-sdk-voice`）均以该版本发布，每次发布仅递增末位编号，不再分模块维护独立版本
- fcitx 内核（`org.fcitx.fcitx5.android:app`）与 stellar 等内部组件使用独立坐标，不随 SDK 发布版本变化
- alpha 系列旧版本号（core `1.1.0-alpha06`、host-bridge `1.2.0-alpha17`、功能模块 `1.0.0-alpha0x` 等）自 2026-08-15 起冻结停用
- 对外每次发布都必须同步更新本页和 HTML 站点版本区

## 历史版本

### `1.0.0.20261011`

- 曾完全关闭自动剪贴板工具栏；本轮按新需求改为同宿主一次性快捷展示。运行时 app:1.11。

### `1.0.0.20261010`

- 双默认皮肤的比例、候选高亮、按键气泡、键盘类型图标和分页面板对齐；运行时 app:1.10。

### `1.0.0.20261008`

- 新增可选三档离线语音、自动标点、模型管理与宿主配置；运行时 app:1.9。

### `1.0.0.20260923`

构建版本日期：2026-09-23；官网发布日期以发布验收记录为准。

- Android 17 兼容预适配：演示宿主默认禁止明文网络请求；OEM SDK 不再向企业宿主的 Manifest 注入允许明文流量的设置。企业生产 API 请使用 HTTPS。
- 移除输入法运行时和演示宿主中旧的调试事件 HTTP 上报，不改变正式业务接口及输入流程。
- 七个公开 SDK 统一升级到 `1.0.0.20260923`，输入法运行时升级到 `org.fcitx.fcitx5.android:app:1.8`；企业需同时升级依赖并重新构建。
- 当前保持 `targetSdk 36`。计划升级到 37 的企业需要单独验证局域网访问、动态代码加载及大屏布局。
- 官网演示 APK 是第三方空白宿主集成 SDK 的 Debug 安装包；已安装旧演示包的设备若提示签名不一致，需先卸载旧包再安装，演示应用本地数据会被清除。客户正式应用的签名由客户自行管理。

### `1.0.0.20260921`

发布时间：2026-09-22（版本号沿用本轮 9 月 21 日分配的构建号）

- 两款默认皮肤的工具栏移除常驻圆形底色，保留原点击区域和按压反馈；消除重复内边距，图标按皮肤原定 23dp 显示；设置页预览同步。
- 默认浅色皮肤替换为「日光瓷白」，默认深色皮肤替换为「夜幕石墨」；两款随输入法运行时内置，离线可用，无需下载、登录或购买皮肤。
- 同步按键切片、文字定位、工具栏与侧栏渲染，覆盖全键盘、九宫格和数字键盘。
- 升级时迁移旧默认浅深色皮肤；保留用户自定义皮肤和跟随系统设置，内置皮肤不可删除或覆盖。
- 七个公开 SDK 统一升级到 `1.0.0.20260921`，输入法运行时升级到 `org.fcitx.fcitx5.android:app:1.7`。企业需同步升级显式运行时依赖并重新构建 App；无新增初始化 API 或服务端配置。

### `1.0.0.20260920`

发布时间：2026-09-20

- 修复低配机 OCR 原生层偶发 `SIGABRT` 崩溃风险：OCR 前增加系统内存水位检查，输入位图进入 Paddle-Lite 前按长边降采样，避免内存紧张时继续进入 native 推理。
- OCR 引擎保留默认 Paddle-Lite，不引入 NCNN/ONNX 重依赖；运行时按设备档位动态选择低内存优先或性能优先策略。
- 新增 SDK 内置 OCR 总开关 `ocrEnabled`，Manifest key 为 `com.paopaoime.oem.ocrEnabled`，默认 `true`；低配机或宿主自管 OCR 时可设为 `false`，SDK 将跳过 Paddle-Lite OCR 初始化与识别。
- 七个公开 SDK 制品统一升级到 `1.0.0.20260920`；内部运行时坐标不变，`org.fcitx.fcitx5.android:app` 仍为 `1.6`。

### `1.0.0.20260915`

发布时间：2026-09-15

- `OemDrawerContentFactory` 新增 `onCreateFullDrawerView(context, controller): View?`，宿主可用单个 View 整块替换抽屉内容（含默认顶栏区域）；返回 `null` 或未实现时保持旧版默认顶栏 + 内容槽行为。
- AI 回复内置面板接入 `OemAiReplyCapabilities.footerText()`，支持默认文案、宿主自定义文案、返回 `null` / 空串隐藏；新增 `onFooterClick()`，宿主可消费底部文案点击跳转协议或说明页。
- 自定义抽屉/内置抽屉自定义 UI 因 `ENABLE_AI_REPLY_CUSTOM_UI`、`ENABLE_MATERIAL_LIBRARY_CUSTOM_UI`、`ENABLE_CUSTOM_DRAWERS` 未开启而回退时，输出包含 `drawerId` 与缺失开关名的 warn 日志，降低排障成本。
- 七个公开 SDK 制品统一升级到 `1.0.0.20260915`；内部运行时坐标不变，`org.fcitx.fcitx5.android:app` 仍为 `1.6`。

### `1.0.0.20260914`

发布时间：2026-09-14

- 修复 `1.0.0.202609112` 中长文本发送验证未联动 `sendTextMaxChars` 的遗留问题：微信等宿主发送 257~1000 字时，输入框快照验证不再被 256 字窗口误判失败。
- `AiInputMethodService` 的输入框快照窗口改为按 `sendTextMaxChars + 64` 动态取值并限制最大 2048；`InputStateSnapshot.containsText(...)` 对 128 字以上文本使用前 64 字 + 后 64 字抽样验证。
- AI 回复、素材库默认发送、自定义 AI 回复 `sendReplyToInput` 与自定义素材库 `sendTextToInput` 均保留同一 `sendTextMaxChars` 口径；超过阈值时明确提示“超出可发送字数上限（N字）”并失败，不再静默截断丢字。
- 继承 `1.0.0.202609112` 的取消异常透传、素材库恢复宿主输入框竞态修复、微信恢复 3 次与结构化诊断回调。
- 修复微信等宿主素材库发送前恢复聊天输入框的基线后置竞态：SDK 在关闭素材界面前采样 `startCount` 并立即请求显示键盘，关闭窗口内已发生的 rebound 不再被误判为失败。
- 微信素材发送恢复窗口从 1 次放宽到 3 次，降低宿主不主动 rebind 时的偶发失败率。
- 新增结构化诊断回调 `OemDiagnosticEvent` / `OemDiagnosticListener` / `OemImeSdk.setDiagnosticListener(...)`，素材恢复失败会以 `category=material_restore` 上报给宿主，便于线上取证。
- 七个公开 SDK 制品统一升级到 `1.0.0.20260914`；内部运行时坐标不变，`org.fcitx.fcitx5.android:app` 仍为 `1.6`。
- 已按 SOP 重置 SDK 仓库、执行 release/R8 发布、导出交付包，并用“探客输入法 / tanke.keyboard.ime”全新模拟 App 完成 release 构建；官网 latest SDK 与演示 APK 已替换并通过 HTTPS 抽检。

### `1.0.0.202609081`

发布时间：

- 2026-09-08

包含内容：

- 修复 OPPO ColorOS / Android 15 上 `MaterialHubActivity` 抽屉普通浏览态因 `FLAG_NOT_FOCUSABLE` 导致前台焦点悬空、约 11 秒后被 Theia NoFocusWindow 监控判 ANR 的风险。
- 抽屉普通浏览、AI 回复选择、素材库点选、自定义抽屉空闲态统一保持可聚焦，并使用 `SOFT_INPUT_STATE_HIDDEN` 避免软键盘误弹；仅在极速生成截图/自动化滑动穿透阶段临时切为不可聚焦/不可触摸，结束后恢复。
- 极速生成多屏截图链路中，截图 JPEG 保存继续运行在 `Dispatchers.IO`；位图差异指纹、垂直采样和接纳截图 OCR 内容检查迁移到 `Dispatchers.Default/IO`，避免继承 `lifecycleScope.launch` 的主线程执行。
- `com.paopaoime.oem` 全套制品统一升级到 `1.0.0.202609081`；`org.fcitx.fcitx5.android:app` 维持 `1.6`。

已知限制：

- 本版本未改动 fcitx 输入法内核坐标，客户仍需显式依赖 `org.fcitx.fcitx5.android:app:1.6`。

### `1.0.0-alpha01`

发布时间：

- 规划中

包含内容：

- 第一阶段 `AI Runtime SDK` AAR
- 本地模型能力
- 模型下载能力
- SDK 设置页跳转能力
- 对外集成手册、API 参考和 FAQ

已知限制：

- 当前不等于完整输入法 OEM SDK
- `Core SDK`、自动化和 UI 仍在后续阶段推进
- 部分能力依赖本地模型和设备资源，不同机型体验可能有差异

### `1.1.0-alpha01`

发布时间：

- 规划中

包含内容：

- 新增 `oem-ime-sdk-core`
- 新增统一初始化入口 `OemImeSdk.initialize(...)`
- 新增 `OemLicenseProvider` / `OemLicenseRequest` / `OemLicenseSnapshot`
- 新增 Manifest 配置读取与宿主签名 SHA-256 识别

已知限制：

- 当前仍未包含完整 UI/IME 宿主桥抽离
- 自动化能力仍按后续阶段评估是否独立开放

### `1.2.0-alpha01`

发布时间：

- 规划中

包含内容（对应 OEM 内部里程碑：R9）：

- 新增 DrawerKit 抽屉开放层（`oem-ime-sdk-core` 公开面）
- L1 内置抽屉「自定义UI交互」：新增 `com.paopaoime.oem.enableAiReplyCustomUi` / `com.paopaoime.oem.enableMaterialLibraryCustomUi` Manifest 开关（默认 `false`），配套 `setDrawerContentFactory` 注册宿主内容工厂
- L2 自定义抽屉：新增 `com.paopaoime.oem.enableCustomDrawers` Manifest 开关（默认 `false`）以及 `registerCustomDrawer` / `unregisterCustomDrawer` / `updateCustomDrawer` 运行时注册；仅在显式开启后入口才进入工具面板宫格与工具条「定制工具栏」体系
- 能力字段协议：AI回复 12 字段 / 素材库 11 字段抽离，提供 `aiReplyCapabilities()` / `materialCapabilities()`
- 官网新增页面：产品架构、AI回复自定义、素材库自定义、自定义抽屉

已知限制：

- L1/L2 均不新增鉴权；L1 能力字段调用仍受既有企业开关约束；L2 额外受宿主显式开关控制，默认不暴露
- DrawerKit 首期仅覆盖 Activity 基座路径，Dialog 备用路径后续评估
- 宿主自研内容属于宿主责任域，不纳入 SDK-R8 加密输入

### `1.1.0-alpha06 / 1.2.0-alpha16`

发布时间：

- 2026-08-14

包含内容：

- AI回复失败态交互重构：统一错误状态码（NETWORK/TIMEOUT/MODEL_BUSY/CONTENT_BLOCKED/RESULT_INVALID/CONFIG_MISSING/CAPTURE_FAILED/UNKNOWN）与宿主自定义错误文案双写
- 断网/大模型请求超时可由宿主配置，默认 30s；失败后点击语气或重新生成从头重走截屏与生成链路
- 自动化不可用时自动回退手动截屏授权页，不再因自动化权限出现生成失败
- 粘贴模式失败态重试增加 1.5s loading 动效；结果/错误/权限页容器拉伸铺满、文本垂直居中
- 素材库编辑态保留右下角添加菜单，支持编辑时继续添加内容
- 官网与 AI 一键装文档同步刷新至最新版本

已知限制：

- 无

### `1.2.0-alpha17`（配套制品：fcitx app `1.2` / material·ai-reply `1.0.0-alpha02` / core `1.1.0-alpha06`）

发布时间：

- 2026-08-15

包含内容：

- 制品完整性修复：fcitx app（1.1→1.2）、host-bridge（alpha16→alpha17）、素材库与 AI回复（alpha01→alpha02）升版重发布，根治构建端命中 Gradle 旧缓存导致新功能不生效的问题
- AI回复失败态：统一错误卡标题/描述文案与容器高度、统一错误状态码宿主回调、断网/超时可由宿主配置、重试从头重走截屏与生成链路
- 极速生成：截屏过程中霓虹边框始终固定在屏幕四周，不再随截屏动画位移出屏
- 素材库：相册/文件授权通过后直接进入选择器，不再回退抽屉首页
- 素材库详情页：右上角提供下载按钮，移除右下角浮动添加按钮；素材编辑页保留右下角添加菜单

已知限制：

- 无（本版本为 alpha 多版本线的最后一版，此后切换统一版本号）

### `1.0.0.20260815`（统一版本号基线，取代 alpha 系列）

发布时间：

- 2026-08-15（首个正式 release，官网 https://www.paopaoime.com/oem/ 可下载）

包含内容：

- 版本号统一：全部 `com.paopaoime.oem` 制品统一以 `1.0.0.20260815` 发布，版本唯一来源为 `OEM/android/gradle.properties` 的 `oemSdkVersion`；后续每次发布 release 仅递增末位编号，客户侧只看到一个 SDK 版本
- host-bridge `VERSION_CODE` 改为从统一版本号末位自动推导（原硬编码 `1200001`）
- 功能默认策略调整：除「基础 + 输入法核心」（core 模块，`enableUi` 默认开）外，其余功能（AI 回复 / 素材库 / 自动化 / 开发者）默认全部关闭；企业集成后按开通范围在宿主 Manifest 中显式开启。catalog、官网模板、SDK 代码回退值、host-bridge AAR Manifest 已同步该口径
- 宿主参考工程（company / demo / tankeyunke）依赖坐标统一对齐新版本

已知限制：

- 无

### `1.0.0.202608242`

发布时间：

- 2026-08-24（同日修订版，官网 https://www.paopaoime.com/oem/ 可下载）

包含内容（变更类型：`patch`）：

- fcitx 内核坐标升版：`org.fcitx.fcitx5.android:app` 由 `1.2` 升为 `1.3`。修复曾缓存过 8/15 旧版 `app:1.2` 的环境构建后切换输入法闪退（`NoClassDefFoundError: FavoriteHostInputRestorePolicy`）的问题；自 1.3 起该坐标启用不可变版本纪律（内容变更必须升版本号），宿主无需再手工清理 Gradle 缓存
- 一键安装 MD 同步更新：§7 依赖、§16 验收清单、§17 排障中全部 `app:1.2` 坐标升为 `app:1.3`；旧 1.2 缓存清理指引保留供历史环境排障

升级指引：

- 依赖清单中 `org.fcitx.fcitx5.android:app:1.2` 改为 `app:1.3`，OEM 制品坐标升至 `1.0.0.202608242`；重新构建即可，无需清理缓存

已知限制：

- 无

### `1.0.0.202608241`

发布时间：

- 2026-08-24（同日修订版，官网 https://www.paopaoime.com/oem/ 可下载）

包含内容（变更类型：`patch`）：

- 修复宿主未配置 `com.paopaoime.oem.channel` meta 时 Application 创建阶段崩溃（`channel must not be blank`）、应用卡在启动页无法进入的问题：channel 缺失或空白时自动回落 `release`
- 一键安装 MD 与 catalog 口径纠正：`channel` 由"选填"改为"必填，默认 release"，必须显式写入；已按 1.0.0.20260824 文档集成且写入 channel 的宿主不受影响，可直接沿用；未写入的宿主升级本版后即可正常启动（也建议补写）

已知限制：

- 无

### `1.0.0.20260824`

发布时间：

- 2026-08-24（官网 https://www.paopaoime.com/oem/ 可下载）

包含内容：

- 新增宿主宰图回调：`OemScreenshotProvider`（`captureScreen(context): Bitmap?`）与 `OemImeSdk.setScreenshotProvider(...)` / `getScreenshotProvider()` / `isScreenshotProviderRegistered()`。面向具备系统级截屏能力的宿主（如 MDM 管控应用）：注册后 SDK 跳过全部截图权限校验（能力预检与授权检查直接视为可用），截图时只调用宿主回调，不再走 Automation / MediaProjection / 缓存降级；宿主返回 `null` 时本次截图直接判定失败（`CAPTURE_FAILED`）
- 宿主宰图回调为同步方法，SDK 在后台线程等待宿主返回，期间不设超时（允许宿主跨进程取图）；OCR 快照与多屏截图链路在注册宰图回调后同步取消外层超时包裹
- 新增宿主滑屏结果回写超时可配置：`OemImeSdk.setSwipeHostResultTimeoutMillis(...)` / `getSwipeHostResultTimeoutMillis()`，默认由 5s 提升到 15s（`DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS`），最小 1s（`MIN_SWIPE_HOST_RESULT_TIMEOUT_MS`）。宿主无障碍手势结果经跨进程通道回传延迟较大时，应把超时上调到覆盖自身链路最大延迟
- Stellar 自动化套餐制品坐标升级：`com.paopaoime.oem.internal.stellar:*` 由 `1.0.0 / 1.0.20` 统一升级为 `1.1`（并入上游 v1.1(101000)：无线调试 mDNS 真实 host+port 透传、启动向导本地网络权限适配、server 侧 PackageManagerCompat 兼容层）；`oem-ime-sdk-automation` POM 传递依赖随之指向 `1.1`，宿主集成时必须使用本版本交付仓，旧交付仓的 `1.0.0/1.0.20` 坐标无法解析新 POM
- 本版为整版重发布（全部制品从 0 构建），旧版本本地缓存请清理后重新下载

已知限制：

- 宿主宰图回调返回 `null` 或抛异常时不做任何降级，宿主需保证截屏实现可用

## 升级原则

- 不允许跳过版本说明直接替换 AAR
- 版本升级前先核对 `minSdk`、宿主包名、签名和授权参数
- 破坏性变更必须在版本说明中明确标注

## 变更类型约定

- `patch`：修复问题，不改公开接口语义
- `minor`：新增兼容能力，不要求宿主大改
- `major`：存在不兼容调整，必须提供迁移说明

## 后续计划

- 沿统一版本线 `1.0.0.<日期戳>` 持续演进：有客户交付需求时递增末位编号发布新版，不再区分 SDK / host-bridge / 功能模块各自版本
- DrawerKit 抽屉开放层（L1 自定义UI开关 / L2 自定义抽屉 / 能力字段协议）与自动化可选模块能力继续在 `1.0.0.202608242` 统一版本线内交付
