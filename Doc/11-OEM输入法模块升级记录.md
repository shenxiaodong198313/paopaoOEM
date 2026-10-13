# 11-OEM输入法模块升级记录

## 1.0.0.20261008

- 新增 sherpa-onnx 1.13.8 三档离线语音与自动标点，完整工具栏/面板交互和 App 模型下载设置。独立为可选语音 SDK；集成后默认开启，未集成不引入 sherpa，可配置模型调优参数与下载镜像。
- 日光瓷白/夜幕石墨对齐当前泡泡工具栏图标与面板文案配色，修正紧凑面板第二行裁切。
- 输入法运行时 app:1.9，公开 SDK 统一升级；详见离线语音配置与本版验收记录。


## 目的

本文档记录 `OEM/android` 下输入法模块的上游升级、主项目同步策略、构建验证和后续回归要求。OEM 输入法模块必须和主项目 `apps/android/ai-ime` 区分维护，避免误改外层企业 App、SDK Core、授权、加固和 AAR 发布链路。

## 2026-09-20 AI 回复生命周期回调与内置霓虹默认关闭

### 背景

- 客户要求宿主能感知 AI 回复生成 Started/Finished，用于自管 loading、悬浮窗或埋点，并要求 SDK 内置 AI 回复霓虹边框/后台接管层默认关闭，避免与宿主自有反馈重复。
- 旧链路只在 SDK 内部管理生成中 UI，宿主无法统一感知 fresh=true 的识屏/粘贴/传图生成和 fresh=false 的换语气/重新生成，也无法通过总开关关闭内置霓虹链路。

### 修改内容

- `OemImeSdk`：新增 `OemAiReplyLifecycleListener`、`setAiReplyLifecycleListener(...)`、`hasAiReplyLifecycleListener()`，以及 Started/Finished 派发 API；监听器异常被捕获并写入 `OemLog`，不影响 SDK 主流程。
- `OemImeSdk` / `OemEnterpriseConfig`：新增 `com.paopaoime.oem.builtinNeonOverlayEnabled` Manifest key、`OemSdkConfig.builtinNeonOverlayEnabled` 和运行时 `setBuiltinNeonOverlayEnabled(...)` / `clearBuiltinNeonOverlayEnabledOverride()`，优先级为运行时覆盖 > 初始化配置/Manifest > 默认 false。
- `MaterialHubActivity`：AI 回复识屏/极速生成、粘贴生成、传图回复、基于已有上下文换语气/重新生成均派发 Started/Finished；每个 request 使用本地 guard 保证 Finished 只回调一次，reason 覆盖 `success`、`failed`、`cancelled`、`timeout`。
- `MaterialHubActivity`：内置 AI 回复霓虹边框、后台 overlay、截图期 overlay 抑制等待均受 `builtinNeonOverlayEnabled` 统一控制；默认关闭时只保留宿主生命周期回调，不再启动 SDK 内置霓虹。
- 文档与交付：版本号升级为 `1.0.0.20260920`，同步外部集成手册、API 参考、版本说明、catalog、官网首页与 AI 一键安装 latest MD。

### 验证与发布

- 编译验证：`OEM/android` 已执行 `:oem-ime-sdk-core:compileReleaseKotlin :app:compileReleaseKotlin --no-daemon --console=plain`，BUILD SUCCESSFUL。
- SDK 发布：按三阶段 SOP 发布 fcitx runtime、内部运行时和七个公开 SDK；导出 `OEM/SDK/oem-sdk-delivery-1.0.0.20260920.zip`，交付 AAR 中已确认包含 `OemAiReplyLifecycleListener.class` 与新版 `OemImeSdk.class`。
- SOP 模拟 App：已清空并重建 `demo_sop_validation` 为“探客输入法 / tanke.keyboard.ime”，使用 `OEM/SDK/repo` 本地 Maven 镜像和最新 AI 一键安装文档集成全模块；Manifest 显式写入 `com.paopaoime.oem.builtinNeonOverlayEnabled=false`，Java 宿主注册 `OemAiReplyLifecycleListener` 并调用 `setBuiltinNeonOverlayEnabled(false)`。
- Release/R8 验证：`demo_sop_validation` 执行 `:app:assembleRelease --no-daemon --console=plain` 成功，`app-release.apk`、`mapping.txt` 与 `output-metadata.json` 已生成；合并 Manifest 确认 `package="tanke.keyboard.ime"` 且 `builtinNeonOverlayEnabled=false`。
- ECS 发布验收：已手工同步 `OEM/website/oem`、latest SDK zip、latest demo APK、`OEM/SDK/repo` 与 catalog 到 `/var/www/paopao/website/oem`；公网 HTTPS 抽检首页、latest SDK zip、latest demo APK、catalog、`1.0.0.20260920` core/material AAR 均返回 200，latest zip / demo APK content-length 分别为 `244077328` / `120396812`。

### 回归关注点

- 宿主注册生命周期监听后，任一 AI 回复生成入口都应成对收到 Started/Finished，取消和超时场景不得遗漏 Finished。
- 默认集成不应出现 SDK 内置霓虹边框/后台 overlay；只有 Manifest 或运行时 API 显式开启后才恢复 SDK 内置霓虹。

## 2026-09-14 长文本发送验证窗口联动 sendTextMaxChars

### 背景

- 客户反馈 `1.0.0.202609112` 虽已交付 `sendTextMaxChars` 配置，但微信发送 257~1000 字仍会失败，日志表现为 `runInputMutation ... verified=false` 和 `text commit failed at 0`。
- 代码与已发布 AAR 核实确认问题存在：`AiInputMethodService.snapshotInputState()` 仍硬编码 `getTextBeforeCursor(256, 0)` / `getTextAfterCursor(256, 0)`，`InputStateSnapshot.containsText()` 要求全文命中，导致长文本无法通过提交验证。
- `performFavoriteTextSend()` 发送前置校验也复用同一 `containsText()`，因此只修 commit 验证会留下第二道 256 字门。

### 修改内容

- `AiInputMethodService`：输入框快照窗口改为按 `OemEnterpriseConfig.resolveSendTextMaxChars(...) + 64` 动态取值，最大限制 2048，默认 1000 时验证窗口为 1064。
- `InputStateSnapshot.containsText(...)`：128 字以内继续全文匹配；128 字以上改为前 64 字与后 64 字均在快照中命中即认为写入成功。
- `performFavoriteTextSend()`、`tryPerformEditorActionAndVerify()`、`trySendEnterAndVerify()`、`runInputMutation()` 继续共用同一 `containsText()`，确保提交验证和素材发送前置校验口径一致。
- `MaterialHubActivity` / `OemDrawerCapabilities`：AI 回复、素材库和自定义抽屉发送超过 `sendTextMaxChars` 时明确提示“超出可发送字数上限（N字）”并失败，不再静默截断继续发送。

### 验证

- IDE 诊断：无新增诊断。
- 编译验证：`OEM/android` 执行 `./gradlew.bat :oem-ime-sdk-core:compileReleaseKotlin :oem-ime-host-bridge:compileReleaseKotlin :oem-ime-sdk-material:compileReleaseKotlin :app:compileReleaseKotlin --no-daemon --console=plain`，BUILD SUCCESSFUL；仅有既有 deprecated warning。
- 待发布验证：完成 `1.0.0.20260914` 三阶段 SDK 发布后，需反查 `oem-ime-host-bridge` AAR 不再包含 `snapshotInputState` 的 256 固定窗口，并用 SOP 演练 App release/R8 构建验证依赖闭包。

## 2026-09-11 AI 回复取消误弹错误页与文本发送上限配置

### 背景

- 客户反馈 `oem-ime-host-bridge 1.0.0.202609091` 在 AI 极速生成过程中，返回、再次进入 AI 回复入口、点击新一轮生成等主动取消操作会误弹「未生成」错误页，并可能把抽屉重新拉回前台。
- 代码核实确认问题存在：`requestAiCommunicateReplies` 取消旧任务后，`JobCancellationException` 会落入兜底 `catch (Throwable)`，被按真实生成失败渲染错误页；`finally` 还会无条件执行 `ensureAiCommunicateDrawerVisibleAfterCapture()`。
- 同步处理文本发送长度诉求：AI 回复和素材库发送链路需要默认支持 1000 字，并允许宿主配置单次可发送字符数。

### 修改内容

- `MaterialHubActivity`：`requestAiCommunicateReplies`、传图回复、粘贴改写分支补 `CancellationException` 分支并重新抛出；截图/传图 finally 仅在协程仍 active 时恢复抽屉，避免用户主动取消后误弹错误页或拉回抽屉。
- `OemImeSdk` / `OemEnterpriseConfig`：新增 `com.paopaoime.oem.sendTextMaxChars`、`OemSdkConfig.sendTextMaxChars`、`OemImeSdk.setSendTextMaxChars(...)` / `getSendTextMaxChars()`，默认 1000，有效范围收敛为 1..10000。
- `MaterialHubActivity` / `OemDrawerCapabilities`：AI 回复结果发送、素材库默认发送、自定义 AI 回复 `sendReplyToInput`、自定义素材库 `sendTextToInput`、素材条目兜底发送统一按配置裁剪文本，避免仍按旧短文本能力发送。
- 文档与示例：OEM app Manifest、外部 SDK 集成手册、API 参考、AI 一键安装 SOP 及官网 latest MD 均补充 `sendTextMaxChars` 配置说明。

### 验证

- IDE 诊断：`OemImeSdk.kt`、`OemEnterpriseConfig.kt`、`MaterialHubActivity.kt`、`OemDrawerCapabilities.kt` 无新增诊断。
- 编译验证：`OEM/android` 执行 `./gradlew :oem-ime-sdk-core:compileReleaseKotlin :app:compileReleaseKotlin --console=plain`，BUILD SUCCESSFUL；仅有既有 deprecated/unchecked warning。

### 发布与交付

- 坐标升版并重置发布：`OEM/android/gradle.properties` 统一版本号升级为 `1.0.0.202609111`，已清理旧 `OEM/android/build/oem-sdk-repo` 与旧交付 zip 后，按 SOP 分三阶段发布 fcitx runtime、内部运行时和七个公开 SDK；公开 SDK 发布阶段包含 `:aikeyboard-sdk-ai:minifyReleaseWithR8`、`:oem-ime-sdk-core:minifyReleaseWithR8` 等 release/R8 任务。
- 交付包：`OEM/SDK/export-sdk.ps1 -VersionTag 1.0.0.202609111` 导出 `OEM/SDK/oem-sdk-delivery-1.0.0.202609111.zip`，并将 `OEM/android/build/oem-sdk-repo` 同步为 `OEM/SDK/repo` 供官网模块直链发布。
- SOP 模拟 App：已按规则清空 `demo_sop_validation`，重新创建“探客输入法”（包名 `tanke.keyboard.ime`），从本次 SDK 交付 zip 解压出的 `local-sdk/repo` 集成 `1.0.0.202609111` 全套模块，Manifest 显式写入 `com.paopaoime.oem.sendTextMaxChars=1000`，release 构建通过，产物 `app-release.apk` 大小 `159,102,313` 字节。
- ECS 发布：已使用 `PAOPAOIME/scripts/deploy-oem-docs.ps1` 同步官网 docs、SDK zip、演示 APK、sdk-repo 与 catalog；远端 SSH 校验 `oem-sdk-delivery-latest.zip`、`oem-ime-demo-latest.apk`、`catalog/oem-sdk-catalog.json`、`sdk-repo/.../oem-ime-sdk-core-1.0.0.202609111.aar` 均存在。
- 线上抽检：`https://www.paopaoime.com/oem/`、`/downloads/oem-sdk-delivery-latest.zip`、`/downloads/oem-ime-demo-latest.apk`、`/sdk-repo/com/paopaoime/oem/oem-ime-sdk-core/1.0.0.202609111/oem-ime-sdk-core-1.0.0.202609111.aar`、`/catalog/oem-sdk-catalog.json` 均返回 HTTP 200。

### 回归关注点

- AI 极速生成中返回、再次进入、点击新一轮生成时，日志中不应再出现 `requestAiCommunicateReplies unexpected failure` + `JobCancellationException`，也不应误弹「未生成」错误页。
- AI 回复结果、素材库文本素材、自定义 UI 直接发送默认可发送 1000 字；宿主通过 Manifest 或运行时 API 调整后，发送长度应按新值生效。

## 2026-09-11 素材库发送恢复宿主输入框竞态修复

### 背景

- 客户日志 `2026-09-11.log` 显示微信素材发送时出现 `listener.onRequestCloseDrawer` 后 `host input rebound before restore, skip sdk restore`，随后 `text commit failed at 0`、`send failed: all parts failed`。
- 代码核实确认问题存在：`sendFavoriteRecord` 在 `onRequestClose` 后延时恢复时才采样 `startCount`，如果输入法重建落在关闭窗口内，新 `startCount` 会被采成基线，导致恢复判断失真。
- 微信 `com.tencent.mm` 当前使用默认 `restoreAttempts=1`，对“微信不主动 rebind”的情况恢复窗口过窄。

### 修改内容

- `MaterialHubFavoriteController`：发送点击瞬间先采样 `FavoriteHostInputRestoreBaseline`，并立即调用 `requestShowKeyboard()`；关闭后恢复逻辑使用这份前置基线判断，若关闭窗口内已经出现 `startCount` 增量则直接视为恢复成功。
- `MaterialHubFavoriteController`：关闭后的稳定等待从 380ms 收敛为 120ms，键盘唤起不再等 380ms 后才触发。
- `FavoriteHostAppRouter`：微信素材发送恢复策略从默认 1 次放宽为 3 次。
- `FavoriteBridge` / `AiInputMethodService` / `OemImeSdk`：新增结构化诊断事件桥接，宿主可通过 `OemImeSdk.setDiagnosticListener(...)` 接收 `material_restore` 事件；同时移除素材恢复链路对内网 `192.168.1.14` 取证上报的依赖。
- 外部文档：补充 `OemDiagnosticEvent`、`OemDiagnosticListener` 与 `setDiagnosticListener` 使用说明。

### 验证

- IDE 诊断：无新增诊断。
- 编译验证：`OEM/android` 执行 `./gradlew.bat :oem-ime-sdk-core:compileReleaseKotlin :ai-ime:app:compileReleaseKotlin :app:compileReleaseKotlin --no-daemon --console=plain`，BUILD SUCCESSFUL；仅有既有 Gradle/Deprecated warning。

## 2026-09-08 ColorOS 抽屉焦点 ANR 与极速生成后台化修复（1.0.0.202609081 / fcitx app 1.6）

### 背景

- 客户反馈 `1.0.0.20260907` 在 OPPO ColorOS / Android 15 上，聊天页打开 `MaterialHubActivity` 抽屉后无操作停留约 11 秒即被系统 ANR 杀死，日志链路显示 `mCurrentFocus=null`、`mFocusedApp=MaterialHubActivity`、窗口 `isFocusable=false` 且 flags 含 `FLAG_NOT_FOCUSABLE`。
- 代码核实确认问题可验证：旧抽屉普通态会在启动或键盘隐藏时调用 `setMaterialHubWindowFocusable(false)`，导致普通浏览/素材点选阶段也带 `FLAG_NOT_FOCUSABLE`；当输入法被隐藏后，ColorOS Theia NoFocusWindow 私有监控会把前台焦点悬空判为 ANR。
- 客户同时反馈极速生成链路中 `requestAiCommunicateReplies` 由 `lifecycleScope.launch` 进入主线程，部分截图后处理没有切换调度器，容易产生 skipped frames。

### 修改内容

- `MaterialHubActivity`：普通抽屉态默认 `clearFlags(FLAG_NOT_FOCUSABLE)` 并设置 `SOFT_INPUT_STATE_HIDDEN`，不再因普通浏览、AI 回复选择、素材库点选或自定义抽屉空闲态让窗口不可聚焦。
- `MaterialHubActivity`：`onKeyboardVisibilityChanged(false)` 与 `setFacadeKeyboardVisible(false)` 改为“隐藏软键盘但保持窗口可聚焦”，只在 `withAiCommunicateDrawerHidden` 的截图/自动化穿透窗口内临时切到 `FLAG_NOT_FOCUSABLE` / `FLAG_NOT_TOUCHABLE`，结束后按进入前状态恢复。
- `MaterialHubActivity`：`analyzeFastCaptureFrame` 迁移到 `Dispatchers.Default`，避免 `Bitmap.createScaledBitmap`、`getPixel` 采样和帧间位移指纹计算落在主线程。
- `MaterialHubActivity`：`inspectAcceptedFastCaptureContent` 的已接纳截图 OCR/结构化检查整体迁移到 `Dispatchers.Default`，文件解码继续使用 `Dispatchers.IO`。
- 坐标升版：`com.paopaoime.oem` 统一版本升至 `1.0.0.202609081`；本次未改 `ai-ime/app` 内核，`org.fcitx.fcitx5.android:app` 维持 `1.6`。

### 验证与发布

- 已完成代码层核实、IDE 诊断和 `:oem-ime-sdk-material:compileReleaseKotlin :app:compileReleaseKotlin` 编译验证。
- 已按 SOP 重发 fcitx/libime 与 `app:1.6` 到本地交付仓，重发 `com.paopaoime.oem:*:1.0.0.202609081` 全套制品，尤其是携带 `MaterialHubActivity` 切片的 `oem-ime-sdk-material`；未覆盖旧 `1.0.0.20260908` 坐标。
- 已导出 `OEM/SDK/oem-sdk-delivery-1.0.0.202609081.zip` 并部署官网 latest SDK、catalog、sdk-repo 与 demo APK。
- `demo_sop_validation` 已清空重建为全新宿主“探客输入法”（包名 `tanke.keyboard.ime`），从官网下载 latest MD/catalog/SDK 后完成 release 构建；宿主入口按官网 AI 一键安装 SOP 设置为透明转发页，`MainActivity` 立即启动 SDK 自带 `com.paopaoime.aikeyboard.ime.ui.activity.SplashActivity` 后 `finish()`，不自绘功能首页；真机 `adb install --no-streaming -r` 安装成功，`pm list packages` 可见 `package:tanke.keyboard.ime`，`monkey` 桌面启动成功并进入 SDK/系统输入法引导链路，日志无 `FATAL EXCEPTION`。

### 回归关注点

- OPPO ColorOS 打开 AI 回复/素材库抽屉后静置超过 15 秒，`dumpsys window` 不应出现前台 `MaterialHubActivity` 不可聚焦且 `mCurrentFocus=null` 的持续悬空状态。
- 极速生成连续 5 张截图时，JPEG 保存、位图采样、OCR 检查不应在主线程形成数百帧 skipped frames。
- 截图/自动化滑动阶段仍需保持可穿透，完成后抽屉应恢复可点击、可聚焦、软键盘不乱弹。

## 2026-09-08 MediaStore 分页兼容修复与传图上限配置（1.0.0.20260908 / fcitx app 1.6）

### 背景

- 客户验证 `1.0.0.20260907 / app:1.5` 时反馈：Android 13+ 设备打开 AI 回复「传图回复」后显示“当前没有可用的图片”。
- 代码核实确认问题可验证：1.5 新增分页时把 `LIMIT/OFFSET` 拼进 5 参数 `ContentResolver.query(..., sortOrder)`，即 `date_added DESC LIMIT 32 OFFSET 0`；Android 13+ MediaProvider 会对 `sortOrder` 做 SQL token 白名单校验，`LIMIT` 被拒绝并抛 `IllegalArgumentException: Invalid token LIMIT`。
- 由于 `FavoriteEmbeddedPickerStore.load()` 原先用 `runCatching` 静默吞掉异常，最终表现为选择器空态而非可见崩溃，增加了定位成本。

### 修改内容

- `FavoriteEmbeddedPickerStore`：分页查询改为 `ContentResolver.query(uri, projection, Bundle, null)`，使用 `QUERY_ARG_SORT_COLUMNS`、`QUERY_ARG_SORT_DIRECTION`、`QUERY_ARG_LIMIT`、`QUERY_ARG_OFFSET`，不再把 `LIMIT/OFFSET` 拼进 `sortOrder`。
- `FavoriteEmbeddedPickerStore.load()`：保留容错返回空列表，但新增 `Log.e` 记录异常、类型、limit、offset，后续 MediaStore 权限/查询异常可从日志定位。
- `AiCommunicatePanelController`：AI 回复「传图回复」新增最多可选图片数量限制，默认 5 张，超限 Toast 提示“最多选择 N 张图片”。
- 宿主配置：新增 `com.paopaoime.oem.aiReplyMaxImageSelection` Manifest meta，默认 5，有效范围 1..20；`OemSdkConfig.aiReplyMaxImageSelection` 与 `OemImeSdk.setAiReplyMaxImageSelection(...)` 支持运行时覆盖，并通过宿主 SharedPreferences 同步给 fcitx 面板读取，避免 ai-ime 反向依赖 SDK Core。
- 坐标升版：`org.fcitx.fcitx5.android:app` 从 `1.5` 升至 `1.6`，`com.paopaoime.oem` 统一版本升至 `1.0.0.20260908`，不覆盖旧版本坐标。

### 发布与交付

- `OEM/SDK/catalog/oem-sdk-catalog.json`、外部集成手册、AI 一键装 SOP、官网 latest MD 与官网生成器版本口径同步到新坐标。
- 演示宿主 Manifest 增加 `com.paopaoime.oem.aiReplyMaxImageSelection=5` 示例，便于验证默认上限和客户后续覆盖。
- 已按 SOP 重新发布 ai-ime `app:1.6`、`com.paopaoime.oem:*:1.0.0.20260908`、`paddleocr4android:1.0.0` 与 `stellar:*:1.1` 全套制品，导出 `OEM/SDK/oem-sdk-delivery-1.0.0.20260908.zip`，并部署官网 latest SDK、catalog、sdk-repo 与 demo APK。
- `demo_sop_validation` 已清空重建为全新宿主“探客输入法”（包名 `tanke.keyboard.ime`），从官网下载 latest MD/catalog/SDK 后完成 release 构建，官网 latest MD/catalog HTTP 200；设备安装阶段 `adb install --no-streaming` 与 `cmd package install` 长时间无返回，APK 已推送至 `/data/local/tmp/tanke-sop-validation.apk` 但包未注册，需后续在设备 PackageManager 恢复后补安装验收。

### 回归关注点

- Android 13+ 设备打开 AI 回复「传图回复」应能正常读取相册首批 32 张，不应因 `Invalid token LIMIT` 落入空态。
- 素材库图片/视频入口共用 `FavoriteEmbeddedPickerStore.loadPage()`，需一并回归首批加载、触底分页和空态。
- AI 回复传图最多默认选择 5 张；Manifest 或运行时配置为 1..20 内其他值时，超限提示数量应随配置变化。

## 2026-09-07 AI 回复传图选择器性能修复（1.0.0.20260907 / fcitx app 1.5）

### 背景

- 客户反馈 AI 回复的「传图回复」图片选择器在相册图片较多时首开慢、滚动掉帧、骨架屏反复闪烁，且越滚越卡。
- 核实确认根因是内嵌图片网格使用 `LinearLayout` 命令式逐行 `addView`，触底加载更多时 `AiCommunicatePanelController.render()` 会先清空容器再重建全部已可见卡片；同时缩略图缓存固定 16MB、解码线程池仅 2 线程、`MediaStore` 首开全量查询，进一步放大卡顿。

### 修改内容

- `FavoriteUi`：新增 `RecyclerView + GridLayoutManager` 内嵌媒体选择器，图片页只创建/绑定可见格子；View 回收时移除缩略图目标并尽量取消未完成解码任务。
- `AiCommunicatePanelController`：图片选择页改为 RecyclerView 直承载，触底加载更多只请求下一页并调用 Adapter 增量追加，不再执行整页 `removeAllViews()` 重建；首批与批次从 80 降为 32。
- `FavoriteEmbeddedPickerStore`：新增 `loadPage(type, limit, offset)`，图片/视频/文件查询支持分页读取，传图页首开不再全量读取相册。
- 缩略图策略：内存缓存由固定 16MB 调整为 `maxMemory / 8` 且至少 32MB，嵌入式预览缓存 key 去掉尺寸维度，解码线程池提升到 4~6 线程。
- 坐标升版：`org.fcitx.fcitx5.android:app` 从 `1.4` 升至 `1.5`，`com.paopaoime.oem` 统一版本升至 `1.0.0.20260907`，避免同坐标覆盖导致客户命中旧缓存。

### 发布与交付

- 按 SOP 从 0 重建 `OEM/android/build/oem-sdk-repo`，发布 fcitx/libime、`app:1.5` 与 `com.paopaoime.oem:*:1.0.0.20260907` 全套制品。
- `OEM/SDK/catalog/oem-sdk-catalog.json`、外部集成手册、Company 宿主验证文档、一键安装 MD 与官网页面版本口径同步到新坐标。
- 官网 `https://www.paopaoime.com/oem/`、`downloads/oem-ai-one-click-install-latest.md`、`downloads/oem-sdk-delivery-latest.zip` 已重新部署并通过远端校验；修正一键安装 MD 中 `org.fcitx.fcitx5.android:app:1.5` 直链残留旧 `app/1.4` 的问题。

### 全新企业集成验证

- 按 SOP 清空 `demo_sop_validation` 后，从官网下载最新 `oem-ai-one-click-install-latest.md` 与 `oem-sdk-delivery-latest.zip`，zip 大小 `224,549,233` 字节。
- 创建全新宿主 `星河输入法`，包名 `com.xinghe.keyboard.ime`，使用独立 `.gradle-home-clean` 与 `--refresh-dependencies` 冷缓存构建 release。
- `:app:assembleRelease` 通过，产物 `demo_sop_validation/xinghe-keyboard/app/build/outputs/apk/release/app-release.apk`，大小 `121,846,374` 字节。
- `dependencyInsight` 确认 `org.fcitx.fcitx5.android:app:1.5` 被宿主显式依赖，并由 `oem-ime-host-bridge`、`oem-ime-sdk-ai-reply`、`oem-ime-sdk-material` 传递引用同一版本。
- 线上关键 AAR 抽检通过：`https://www.paopaoime.com/oem/sdk-repo/org/fcitx/fcitx5/android/app/1.5/app-1.5.aar` 包含 `org/fcitx/fcitx5/android/input/favorite/FavoriteHostInputRestorePolicy.class`。
- 真机安装验证通过：`adb install --no-streaming -r app-release.apk` 成功，系统识别输入法服务 `com.xinghe.keyboard.ime/com.paopaoime.aikeyboard.ime.service.AiInputMethodService`，并已启用/切换为默认输入法。
- SDK 页面启动验证通过：显式启动 `SetupActivity` 后自动进入 `com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity`，宿主日志显示 `OEM SDK initialized`，版本 `1.0.0.20260907`。

### 回归关注点

- 相册图片较多时，打开传图回复应只展示首批 32 张并快速进入可交互状态。
- 滚动触底应按 32 张分页追加，滚动过程中不应出现整页骨架屏重闪或明显掉帧。
- 连续快速滑动后，已滚出屏幕的缩略图任务不应长期占用解码队列，回到可见区域时应优先展示内存缓存。
- 第三方全新 App 必须按 `OEM/SDK` 导出的交付仓集成，依赖 `org.fcitx.fcitx5.android:app:1.5` 与 `com.paopaoime.oem:*:1.0.0.20260907`。

## 2026-08-24 fcitx app 坐标升版 1.3 与不可变版本纪律（1.0.0.202608242）

### 背景

- 真机验证（探客输入法）切换为默认输入法后反复闪退：`NoClassDefFoundError: org.fcitx.fcitx5.android.input.favorite.FavoriteHostInputRestorePolicy`，崩溃于 `AiInputMethodService` 构造阶段。
- 根因：`org.fcitx.fcitx5.android:app:1.2` 属固定版本号，8/24 线上制品内容更新（新增 `FavoriteHostInputRestorePolicy` 等收藏相关类），但 Gradle 对 release 坐标有"不可变假设"——本机缓存中 8/15 下载的旧 app-1.2.aar（84,586,069 字节，无该类）不会被重新校验，构建直接打入旧二进制，运行期类缺失崩溃。MD §7 虽已有"禁止混用旧缓存同坐标旧包"警告，但固定版本内容变化本身即是缺陷源头。

### 修改内容

- `ai-ime/host-bridge/build.gradle.kts`：`version = "1.2"` → `"1.3"`（VERSION_NAME 同步），fcitx app 坐标升版，内容变化与版本号严格绑定。
- 依赖声明同步升 1.3：`oem-ime-host-bridge`（api）、`oem-ime-sdk-ai-reply`、`oem-ime-sdk-material`、主工程 `app`、`tankeyunke` 宿主。
- `catalog/oem-sdk-catalog.json`：app 坐标 → 1.3，notes 标注"自 1.3 起启用不可变版本纪律：内容变更必须升版本号"。
- `app.js`：一键安装 MD 全部 `app:1.2` 坐标引用升 1.3（§7 依赖说明、§16 验收清单、§17 排障），旧 1.2 缓存清理指引保留作为历史排障。
- 版本号：`oemSdkVersion` 升至 `1.0.0.202608242`（POM 依赖变化，制品内容变更必须换版本，避免重蹈同坐标覆辙）。

### 发布与交付

- ai-ime `:host-bridge:publishReleasePublicationToOemLocalRepository` 发布 app:1.3；OEM 两阶段发布 7 制品（202608242）；host-bridge POM 确认指向 app:1.3。
- export-sdk（22 制品、0 缺失）→ 重新生成一键安装 MD → deploy-oem-docs.ps1 部署并远程验证。

### 回归关注点

- 全新机器集成：app:1.3 首次下载，无缓存问题。
- 曾缓存 1.2 的环境升级：坐标不同，Gradle 必然重新下载，不命中旧缓存。
- 键盘服务启动、收藏输入恢复链路（FavoriteHostApp/FavoriteBridge）无 NoClassDefFoundError。

## 2026-08-24 channel meta 启动崩溃修复与文档口径纠正（1.0.0.202608241）

### 背景

- 模拟客户 0-1 集成（探客输入法）真机验证时发现：按官网一键安装 MD 集成后应用无法启动，一直停留启动页；logcat 显示 `HostApp` 创建阶段抛 `IllegalArgumentException: channel must not be blank`，进程反复崩溃，前台停留于 fcitx `LogActivity`。
- 根因：宿主 Manifest 未写 `com.paopaoime.oem.channel` meta 时，`OemManifestConfigParser` 读出 null，`OemManifestConfig.toSdkConfig` 转空串，`OemSdkConfig` init 强校验直接崩溃。而一键安装 MD 模板注释写"可不填，默认 release（选填）"，文档承诺的默认值在 SDK 内并不存在——文档口径与实现不符，属交付缺陷。

### 修改内容

- `OemImeSdk.kt`（oem-ime-sdk-core）：`OemManifestConfig` 新增 `companion object { const val DEFAULT_CHANNEL = "release" }`；`toSdkConfig` 的 channel 由 `channel?.trim().orEmpty()` 改为 `channel?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_CHANNEL`，缺失/空白时回落 `release` 不再崩溃。channel 仅作授权快照渠道标记与缓存一致性键，回落安全。
- `catalog/oem-sdk-catalog.json`：`com.paopaoime.oem.channel` 由 `required: false` 改为 `required: true`，描述更新为"必须显式写入，默认值 release"；MD 模板注释随之由"选填"变为"必填"。
- `app.js`：§1 输入参数与 §9 写入规则两处 channel 说明改为"必填，默认 release"，并写明旧版（<1.0.0.202608241）缺失会崩溃。
- 版本号：`oemSdkVersion` 升至 `1.0.0.202608241`（同日修订版）。

### 发布与交付

- 两阶段重新发布 7 个 `com.paopaoime.oem` 制品；`export-sdk.ps1 -VersionTag 1.0.0.202608241` 导出，manifest 22 制品、`missingArtifacts` 为空；deploy-oem-docs.ps1 全量部署并通过远程验证。
- 文档同步：07 清单、09-Company、集成手册、自动化授权配置坐标升至 202608241；`gen-full-md.js` 重新生成一键安装 MD。

### 回归关注点

- 宿主未写 `channel` meta：应用可正常启动，授权快照渠道标记为 `release`。
- 宿主显式写 `channel`（如 company 的 `marketing-enterprise-release`）：行为不变，快照标记沿用宿主值。
- 升级版本号可顺带规避 CDN 缓存：客户下载的一键安装 MD 与 catalog 均为新版本号，执行前版本自检能识别旧副本。

## 2026-08-24 宿主截图回调接口与滑屏宿主回调超时可配置（1.0.0.20260824）

### 背景

- 客户（SDK改进需求.md）提出两项改进：
  1. 宿主已具备自有截屏能力（无障碍全局截图等），要求 SDK 提供宿主宰图回调接口，由宿主直接供图，替代 SDK 内置截屏与手动授权链路。
  2. 滑屏宿主回调 `onSwipeRequested` 原固定 5 秒超时，但宿主无障碍手势经 ContentObserver 跨进程回传实测需 ~10 秒，导致宿主结果频繁被丢弃回落自身通道。
- 排查确认：`AiCommunicateScreenCaptureManager.captureBitmap` 的 OCR 路径外层包裹 6 秒、FAST_LLM 路径 30 秒 `withTimeoutOrNull`，若直接插入宿主同步截图会被误判超时；`MaterialHubActivity.dispatchSwipeToHostAndAwait` 使用硬编码 `SWIPE_HOST_RESULT_TIMEOUT_MS = 5_000L`。

### 修改内容

- `OemImeSdk`（oem-ime-sdk-core）：
  - 新增 `fun interface OemScreenshotProvider { fun captureScreen(context: Context): Bitmap? }`
  - 新增 `setScreenshotProvider(provider?)` / `getScreenshotProvider()` / `isScreenshotProviderRegistered()`
  - 新增 `DEFAULT_SWIPE_HOST_RESULT_TIMEOUT_MS = 15_000L`、`MIN_SWIPE_HOST_RESULT_TIMEOUT_MS = 1_000L`，`setSwipeHostResultTimeoutMillis(ms)`（低于最小值钳制）/ `getSwipeHostResultTimeoutMillis()`
- `AiCommunicateScreenCaptureManager`：`hasGrantHint` / `hasUsableGrant` / `hasAnyCaptureCapability` 在已注册 provider 时直接返回 `true`；`captureBitmap` 优先走 provider 同步取图——不设超时、返回 `null` 直接抛 `UserVisibleFailure(CAPTURE_FAILED)`，不降级到自动化截屏/手动授权/缓存。
- `MaterialHubActivity`：FAST_LLM 批量截图与 OCR 单帧截图路径在已注册 provider 时旁路外层 `withTimeoutOrNull` 包裹；删除 5 秒常量，`dispatchSwipeToHostAndAwait` 改读 `OemImeSdk.getSwipeHostResultTimeoutMillis()`；预热协程补 `runCatching` 防护宿主抛错导致崩溃。
- 版本号：`gradle.properties` `oemSdkVersion` 与 `build.gradle` fallback 统一升至 `1.0.0.20260824`。
- **Stellar 坐标漂移修复**：排查发现 2026-08-22 起 9 个 stellar 模块发布版本已随上游升级为 `1.1`，但 catalog/官网/文档仍写 `1.0.0 / 1.0.20`，按旧坐标交付必然导致客户侧传递依赖解析失败。本次借整版重发布将 `com.paopaoime.oem.internal.stellar:*` 全量切到 `1.1`，catalog、官网 index.html、07 清单、09-Company、集成手册、自动化授权配置、company/tankeyunke 宿主 build.gradle 一并刷新。

### 发布与交付

- 从 0 删除 `build/oem-sdk-repo` 后两阶段发布：阶段一发叶子制品（paddleocr / core / ai / fcitx app / 9 stellar），阶段二发上层模块（host-bridge / ai-reply / material / automation / developer），另在 ai-ime 嵌套工程发布 5 个 fcitx lib。
- `export-sdk.ps1 -VersionTag 1.0.0.20260824` 导出，manifest 22 制品、`missingArtifacts` 为空。
- 文档同步：版本说明、API参考（新增宿主截图回调章节+滑屏超时配置表）、集成手册（新增两节集成指引）、07 清单、09-Company、自动化授权配置；`gen-full-md.js` 重新生成一键安装 MD。

### 回归关注点

- 注册 `setScreenshotProvider` 后：识屏回复/极速生成取图必须走宿主回调且不受任何超时限制；返回 `null` 时应直接进失败态（`CAPTURE_FAILED`），不得出现自动化截屏、授权引导或缓存回退。
- 未注册 provider 时，全部链路与 1.0.0.20260821 行为一致。
- 滑屏宿主回调在 15 秒内回写均应被采纳；`setSwipeHostResultTimeoutMillis(800)` 应被钳制为 1_000。
- 客户按新坐标 `1.1` 集成 stellar 套餐，`oem-ime-sdk-automation` POM 传递依赖应可完整解析。

## 2026-08-17 运行时开关热更新补齐与 MaterialHub 首屏卡顿修复

### 背景

- 客户在集成 OEM SDK 时反馈两个问题：`featureToggles` 细粒度开关初始化后无法动态切换，以及 `MaterialHubActivity` 首次打开存在 ANR 风险。
- 核实后确认：
  1. SDK 对外已支持运行时替换委托、分发模式和 Prompt，但缺少 `qaSessionEnabled` / `aiReplyEnabled` / `materialLibraryEnabled` 等细粒度功能开关的统一热更新入口。
  2. `MaterialHubFavoriteController` 在 `show()` 阶段同步调用 `FavoriteStorage.listFavorites()` 读取本地索引，存在主线程读盘导致首屏卡顿甚至 ANR 的风险。

### 修复内容

- `OemImeSdk` 新增运行时功能开关覆盖层：`getEffectiveFeatureToggles()`、`setFeatureToggles()`、`updateFeatureToggles()`，并补充 `setAiReplyEnabled()`、`setMaterialLibraryEnabled()`、`setQaSessionEnabled()` 便捷接口。
- AI 回复、素材库、质检会话与 DrawerKit L1/L2 的开关判定统一改为读取“运行时覆盖 > 初始化配置”的生效值，宿主无需重启进程即可动态切换。
- `MaterialHubFavoriteController` 的本地收藏读取改为 `Dispatchers.IO` 后台线程执行；首次无缓存时先展示加载态，避免在主线程同步读盘阻塞页面首帧。
- 对外文档 `外部版-API参考.md`、`外部版-SDK集成手册.md` 已同步补充运行时开关热更新说明和调用示例。

### 回归关注点

- 宿主在初始化前后调用 `setFeatureToggles` / `updateFeatureToggles` 时，AI 回复、素材库、质检会话入口均应即时按新配置生效。
- `MaterialHub` 首次打开时不应再因本地素材索引读盘阻塞主线程；弱网情况下远程素材刷新允许后台慢，但列表首屏必须可交互。

## 2026-08-15 官网旧版本残留根因修复与「发布后清零收尾」SOP 规范化

### 背景

- 首个 release 上线后，官网徽标仍显示旧坐标「SDK 版本 1.1.0-alpha03」，暴露出「看似发布、实则遗留」的残留问题。
- 根因定位：
  1. `website/oem/app.js` 的 `loadCatalog()` 拉取相对路径 `../../SDK/catalog/oem-sdk-catalog.json`，在 ECS 上命中 nginx root（`/var/www/paopao/website`）下历史遗留的旧 `SDK/` 目录旧 catalog。
  2. 存在两份分歧的官网源：根目录 `website/oem`（deploy 用）与 `OEM/website/oem`（export-sdk / 一键 MD 生成用），后者未随发布同步，产出的 MD/catalog 口径滞后。
  3. 一键安装 MD 与 `OEM/Doc` 09/07/自动化授权配置等文档仍写旧 alpha 坐标。

### 修复内容

- `app.js`：catalog 拉取改为 `catalog/oem-sdk-catalog.json`（官网托管位 `/oem/catalog/`），失败才回退内联 catalog。
- `scripts/deploy-oem-docs.ps1`：新增上传 catalog 到 `/oem/catalog/`、每次强制删除历史遗留 `/var/www/paopao/website/SDK`、并以 catalog 动态版本号做线上校验。
- `OEM/SDK/export-sdk.ps1`：siteRoot 改指根目录 `website/oem`；`Drill/gen-full-md.js` 同样改读根目录 `website/oem/app.js` 并把 MD 写回根目录官网（downloads 与根两份拷贝）。
- 删除 `OEM/website` 整个镜像（29 文件），官网自此只有根目录 `website/oem` 一个源；`08-外部Wiki与ECS部署.md` 写入单一源规则。
- 文档回正：`09-Company宿主验证工程.md` 依赖基线、`07-SDK实施与发布清单.md` 交付条目、`外部版-自动化授权配置.md` 示例命令全部改为 `1.0.0.20260815`；一键 MD 重新生成（线上复检无 alpha 坐标）。
- 重新导出交付包并重新部署，线上复检全绿：`/oem/catalog/oem-sdk-catalog.json` 服务 `1.0.0.20260815`、页面徽标与 MD 一致。

### SOP 规范化（发布后清零收尾）

- `docs/Android/OEM发布标准SOP.md` 新增「十一、发布后清零收尾（强制执行）」：官网抽检通过后立即清理全部制品与缓存，仓库只维护 OEM 最新源码，下一次发布一律从 0 构建。
- 清理清单按实际目录名校准：`OEM/android/.gradle-local`、`.gradle-home`、`.kotlin`；`ai-ime/.gradle`、`.gradle-home`、`.kotlin`、`.tmp`；`OEM/company/android/.gradle-home`、`.kotlin`；`OEM/android/oem-*.log`、`export-sdk-run.log`、`hs_err_pid*.log`/`replay_pid*.log`；清理后 `OEM/SDK` 只允许剩 `catalog/`、`export-sdk.ps1`、`README.md`。
- 守护进程纪律：清理前在 `OEM/android`、`ai-ime`、`OEM/company/android` 三处各执行一次 `gradlew --stop`；不启用 build cache；同天重发递增末位编号，禁止相同坐标覆盖发布。
- Checklist 新增「7. 发布后清零收尾」勾检项；「禁止的错误流程」新增制品留到下一次会话、服务器手工放 catalog、重建 OEM/website 镜像三条。

### 本轮清零执行结果

- 已删除：`oem-sdk-repo`、全部模块 `build/` 目录、上述全部 Gradle/Kotlin 缓存目录、20 个历史构建日志、JVM 崩溃日志、`OEM/SDK` 导出产物（repo/docs/zip/manifest/README）、company `local-sdk`/`app/build`/缓存、临时脚本。
- 复检：`OEM/SDK` 仅剩 `catalog/`、`export-sdk.ps1`、`README.md`；company 工程仅剩源码与脚本；无守护进程残留。

### 后续回归清单

- 每次发布完成（官网抽检通过）后必须立即执行 SOP 第十一章清零收尾，不得把制品留到下一次会话。
- 官网只允许根目录 `website/oem` 一个源；一切上载以 `scripts/deploy-oem-docs.ps1` 为准，禁止服务器手工放 catalog/AAR 副本。
- 每次构建 SDK 前按 SOP 预检确认旧制品/缓存已不存在，从 0 构建。

## 2026-08-15 首个正式 release（1.0.0.20260815）发布到官网

### 背景

- OEM 进入客户交付阶段，版本号统一为 `1.0.0.<日期戳>` 单一来源（`OEM/android/gradle.properties` 的 `oemSdkVersion`），结束 SDK 与 host-bridge 双轨版本（原 `1.1.0-alpha03` / `1.2.0-alpha13`）。
- 功能默认策略统一：除「基础 + 输入法核心」（enableUi）外全部默认关闭，企业集成后按需开通；catalog / 官网模板 / SDK 代码回退 / 宿主解析四套口径已对齐。
- 本轮按 `docs/Android/OEM发布标准SOP.md` 完成首个正式 release 的完整发布链路并部署官网。

### 发布内容

- 两阶段发布本地 AAR 到 `OEM/android/build/oem-sdk-repo`：阶段 1 内部运行时（paddleocr / 9 个 stellar 模块，历史本地 TTS 运行时已于后续版本移除），阶段 2 SDK 模块（ai / core / material / ai-reply / automation / developer / host-bridge）；另在 `ai-ime` 工程发布 `lib` 系列与 fcitx `app:1.2`。
- `com.paopaoime.oem` 组全部制品统一为 `1.0.0.20260815`；host-bridge `VERSION_CODE` 由版本号末段自动推导（20260815）。
- 已下线 `sherpa-onnx`（不在 settings.gradle，禁止再发布）；`stellar-shizuku-aidl` 随交付仓整体提供但宿主禁止显式依赖（duplicate class 风险），catalog/官网已以 `excludedArtifacts` 明示。

### 工具链修复

- `export-sdk.ps1`：all 模式原先不生成 manifest，导致 SDK 根目录残留旧模块化导出的 `1.1.0-alpha01` manifest；现 all 模式同样从 catalog 生成全量 manifest（含 23 项制品 MD5/SHA256）并打入 zip。
- `scripts/deploy-oem-docs.ps1`：版本校验由硬编码 `1.1.0-alpha06` 改为从 catalog 动态读取 sdkVersion。
- SOP 修正：移除 sherpa-onnx 发布项、补两阶段发布命令与原因、资产路径基线改为 `assets/descriptor.json` 与 `META-INF/com/android/build/gradle/aar-metadata.properties`、`-VersionTag` 正式 release 必须传版本号。

### 校验结果

- 完整性：24 AAR、无历史版本目录残留；app AAR `assets/descriptor.json` 含 androidfrontend.conf / pinyin.conf / sc.dict / extb.dict；host-bridge aar-metadata 与三个核心模块 proguard.txt 齐全。
- 演示正式包：`OEM/company/android` `sync-sdk.ps1 -Clean` 后 `:app:assembleRelease` 构建成功（153.7MB），APK 内 descriptor 与关键输入法资产核验通过。
- 官网部署：website/oem 页面、`oem-sdk-delivery-latest.zip`（226MB）、`oem-ime-demo-latest.apk`（154MB）、sdk-repo 模块直链目录全部上线，远程校验通过。
- 线上抽检：`https://www.paopaoime.com/oem/` 页面版本显示 1.0.0.20260815；sdk-core / host-bridge AAR 与交付 zip、演示 APK 直链均 200；线上 core AAR 解包含 30 个 `sdk/core/drawer` 类，SHA256 与本地完全一致。

### 后续回归清单

- 每次发布 release 仅递增 `oemSdkVersion` 末位编号并全套重发，禁止单模块单独发版。
- 第三方按官网一键 MD 集成时，确认 sdk-repo 直链拉到的是当前版本 AAR（防止只传页面/zip 不传 sdk-repo 的历史事故）。
- 宿主集成不得显式依赖 `stellar-shizuku-aidl`；依赖块以官网模板为准。

## 2026-08-06 同步主项目隐藏更多面板“皮肤”入口

### 背景

- 产品约束：键盘 `更多` 工具面板与 `输入法设置 → 定制工具栏` 均不得展示 `皮肤` 入口。
- OEM 的 `定制工具栏` 候选原本已由 `oemDisabledToolbarItems = setOf(ToolbarItem.Skin)` 排除皮肤；但 `更多` 面板 `StatusAreaWindow.buildEntries` 固定列表仍无条件添加皮肤条目，0.1.3 升级后入口重新暴露。
- 本轮与主项目同构修复，只优化代码，不构建或发布 SDK AAR。

### 同步范围

- `OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/status/StatusAreaWindow.kt`：`buildEntries` 固定列表移除 `StatusAreaEntry.Android(..., Skin)` 条目；fcitx 动态 action 追加、AI回复/素材库门禁逻辑不变。
- 皮肤通道桥接与点击分发代码保留（OEM 宿主绑定为托管账号提示），仅移除面板入口。

### 防回归约定

- 后续升级、回灌、重构 `StatusAreaWindow` / `buildEntries` / 工具栏相关代码时，必须保持 `皮肤` 入口隐藏；回归清单包含“更多面板无皮肤、定制工具栏候选无皮肤”。

### 构建验证

```powershell
cd C:\Users\15811\Desktop\paopao\OEM\android
$env:GRADLE_USER_HOME='C:\g'
.\gradlew.bat :app:compileReleaseKotlin --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`（含 `:ai-ime:host-bridge`、`:oem-ime-host-bridge`、`:app`）。

### 后续回归清单

- 更多面板第一页不再出现 `皮肤` 格子，分页与其余入口顺序正常。
- 定制工具栏候选无 `皮肤`；工具栏、AI 回复/素材库门禁、键盘调节、企业质检留存不受影响。

## 2026-08-06 同步主项目剪贴板布局方式桥接修复

### 背景

- 主项目 `apps/android` 已修复“输入法设置 → 剪贴板 → 布局方式（单列/双列/紧凑）不生效”问题（见 `docs/安卓客户端/输入法模块.md` 1.36 节），并通过正式整包构建与真机回归。
- 根因为主项目与 OEM 共有的问题：设置只写宿主侧 AppPrefs（key `clipboard_layout_mode`），ai-ime 侧 `ClipboardWindow` 硬编码 `StaggeredGridLayoutManager(2, …)`，fcitx 侧无对应设置项接收。
- 本轮按主项目已验证的轻量桥接方案同构同步到 OEM，只优化代码，不构建或发布 SDK AAR。

### 同步范围

- 新增 `OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/clipboard/ClipboardLayoutMode.kt`（`ListView / GridView / FlexboxView`，实现 `ManagedPreferenceEnum`，枚举名与宿主侧编码值一致）。
- `OEM/android/ai-ime` `AppPrefs.Clipboard` 以 `enumList` 新增 `clipboardLayoutMode`，复用与宿主相同的 key `clipboard_layout_mode`，默认 `ListView`。
- `ClipboardWindow` 按设置生成 LayoutManager：`ListView` → 1 列；`GridView` / `FlexboxView` → 2 列；`FlexboxView`（紧凑）条目行数 4 → 2。
- `ClipboardAdapter` / `ClipboardEntryUi` 新增默认参数行数值，不影响其他调用点。
- `OEM/android/ai-ime` `values / values-zh-rCN / values-zh-rTW` 补充 `clipboard_layout_mode*` 字符串资源。
- `OEM/android/app` `InputMethodSettingsBridge.setClipboardLayoutMode` 改为 `hostPrefs` + `fcitxPrefs` 双写，与剪贴板其他设置桥接口径一致；“恢复默认”走同一桥接同时生效。

### 构建验证

执行目录：

```powershell
cd C:\Users\15811\Desktop\paopao\OEM\android
```

已通过 Kotlin 编译验证（含 org.fcitx 源码模块 `:ai-ime:host-bridge` 与宿主桥接模块 `:oem-ime-host-bridge`、`:app`，仅编译不打包、不构建 SDK）：

```powershell
$env:GRADLE_USER_HOME='C:\g'
.\gradlew.bat :app:compileReleaseKotlin --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL in 5m 19s`。

### 后续回归清单

- OEM 包构建后，设置中切换单列/双列/紧凑，宿主界面剪贴板面板列数应随之变化（1 列 / 2 列 / 2 列紧凑）。
- 剪贴板监听、上限、提示、停留时长、清空全部等既有设置不受影响。
- 下次构建 OEM debug 包时按本记录回归上述点位。

## 2026-08-03 同步主项目 fcitx5-android 0.1.3 升级

### 背景

- 主项目 `apps/android/ai-ime` 已完成官方 `fcitx5-android 0.1.3` 升级，并通过正式整包构建、安装和桌面入口启动验证。
- OEM 输入法模块来源于主项目输入法移植，功能几乎一致，但 `OEM/android` 额外包含企业壳 App、`oem-ime-sdk-core`、`aikeyboard-sdk-ai`、`oem-ime-host-bridge`、授权、素材库、质检、Stellar、OCR 和 SDK 发布配置。
- 本轮只升级 OEM 输入法内核并构建 debug 包测试，不做 R8 加固，不构建或发布 SDK AAR。

### 升级策略

- 不覆盖整个 `OEM/android` 工程。
- 不改 `OEM/android/app` 的真实系统输入法入口 `com.paopaoime.aikeyboard.ime.service.AiInputMethodService`。
- 不改 `OEM/android/oem-ime-sdk-core`、`OEM/android/aikeyboard-sdk-ai`、`OEM/android/oem-ime-host-bridge`。
- 不改 `OEM/android/ai-ime/host-bridge/build.gradle.kts` 的 Maven 发布配置。
- 仅将主项目本轮已验证的 `ai-ime` 内核源码、JNI、候选 UI、Picker、Popup、资源和版本元数据同步到 `OEM/android/ai-ime`。

### 同步范围

- 候选链路：`CandidateWord`、`FcitxEvent`、`FcitxAPI`、`Fcitx.kt`、`native-lib.cpp`、`jni-utils.h`、`object-conversion.h`、`helper-types.h`、`androidfrontend/*`。
- 候选 UI：横向候选、展开候选、浮动候选、T9 分页候选和顶部读音展示。
- Picker/Popup：`PickerPolicy`、`PickerWindow`、`PickerPagesAdapter`、`PickerPageUi`、`PickerWindowPreset`、`EmojiModifier`、`PopupComponent`、`PopupAction`、`PopupPunctuationUi`、`BaseKeyboard`。
- 键盘和状态入口：`KeyDef`、`KeyboardHeightPercentBase`、`StatusAreaWindow`。
- 资源：`ic_baseline_content_copy_24.xml`、键盘高度基准字符串。
- 版本：`baseVersionCode = 11`、`baseVersionName = 0.1.3`。

### OEM 专属保留点

- `InputView` 中的运行时输入拦截遮罩仍保留，依赖 `FcitxInputMethodService.RuntimeInputBlockState`。
- 升级主项目 `FcitxInputMethodService` 后，必须补回 OEM 扩展点：`RuntimeInputBlockState`、`getRuntimeInputBlockState()` 和 `onStartInputView()` 中的 `inputView?.syncRuntimeBlockState(...)`。
- OEM 外层 `AiInputMethodService` 仍负责素材库、AI 回复、企业质检、附件发送、T9 侧栏和授权刷新。
- OEM Manifest 仍只暴露 `AiInputMethodService` 为系统输入法入口，原 `FcitxInputMethodService` 不得重新暴露为第二个输入法。

### 构建验证

执行目录：

```powershell
cd C:\Users\15811\Desktop\paopao\OEM\android
```

已通过输入法桥接 Kotlin 编译：

```powershell
$env:GRADLE_USER_HOME='C:\g'
.\gradlew.bat :ai-ime:host-bridge:compileDebugKotlin --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL in 2m 10s`。

已通过 OEM App debug 构建：

```powershell
$env:GRADLE_USER_HOME='C:\g'
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL in 12m 42s`。

产物：

```text
OEM/android/app/build/outputs/apk/debug/tankeIme_debug_nomodel.apk
```

大小：`195020929 bytes`，约 `185.99 MB`，提交 GitHub 时必须排除。

### 后续回归清单

- 桌面入口启动不闪退。
- 系统输入法列表只出现 OEM 的 `AiInputMethodService`，不出现第二个官方 fcitx 服务入口。
- 拼音候选、展开候选、候选 tab、长按候选菜单正常。
- T9 中文、英文、数字键盘切换、删除回退、侧栏候选正常。
- 工具栏、更多面板、AI 回复、素材库、键盘调节、企业质检留存正常；`皮肤` 入口保持隐藏（更多面板与定制工具栏均不展示）。
- 输入法设置页、授权刷新、SDK 能力门禁仍走 OEM 外层逻辑。

### 提交注意

- 不提交 `OEM/android/app/build/outputs/apk/debug/tankeIme_debug_nomodel.apk`。
- 不提交 `OEM/android/**/build`、`OEM/android/**/.cxx`、`OEM/android/**/.gradle*`、Gradle 缓存、SDK 发布仓库产物或其他大于 100MB 的文件。

## 2026-08-04 SDK 模块化拆分与可选模块降级

### 背景

- 按友盟 SDK「官网勾选下载」模式，将 AAR 加固产物按功能拆分为 5 大模块：①基础功能+输入法（必选）②AI回复 ③素材库 ④Stellar自动化 ⑤开发者，客户按模块勾选集成。
- AI回复对 Stellar 自动化是可选依赖：无自动化模块时必须降级为手动截图并提示「当前未集成自动化模块」，不得崩溃。

### 拆分方案（零文件移动）

- 新增 4 个 feature 模块：`oem-ime-sdk-ai-reply`、`oem-ime-sdk-material`、`oem-ime-sdk-automation`、`oem-ime-sdk-developer`，坐标统一 `com.paopaoime.oem:*:1.0.0-alpha01`。
- feature 模块通过 `syncOemSlice`（Sync 任务）从 `app/src/main/java` 切片源码到 `build/sliced-src`，`sourceSets` 指向切片目录；所有 Kotlin/KSP 任务用 `tasks.configureEach` 显式 `dependsOn`，避免 Gradle 隐式依赖校验失败。
- `oem-ime-host-bridge` 源码集排除所有 feature 切片文件、`sdk/**`、`midscene/bridge/model/**`；`OemHostRuntime.kt`（依赖内核类）通过 `syncHostBridgeSlice` 单独编入 host-bridge。
- `oem-ime-sdk-core` 排除 `OemHostRuntime.kt`，保留 `midscene/bridge/model/**` 共享序列化模型。
- 跨模块调用全部走 `OemAddons` 可空注册表（ContentProvider 自动注册）：未集成模块时 addon 为 null，调用方安全降级。`OemAutomationAddon` 新增 overlay 显示/隐藏/neon/shell 命令与可用性查询接口，镜像类型 `OemAutomationOverlayState`/`OemAutomationShellResult`。
- `AiCommunicateScreenCaptureManager`、`MaterialHubActivity` 已全面改为 `OemAddons.automation?` / `OemAddons.developer?` 调用；`ai-reply` 移除对 automation 的依赖，`material` 移除对 automation/developer 的依赖，tonePresets 走 `OemDeveloperAiConfigDefaults.DEFAULT_TONE_PRESETS` 兜底。

### R8 加固要点（library minify）

- host-bridge：`-keep class com.paopaoime.aikeyboard.** { *; }`，feature AAR 按原名跨模块引用内核类，不得 shrink/混淆。
- core：`-dontobfuscate` + `midscene.bridge.model.**` keep（跨模块共享模型不得 shrink）。
- 4 个 feature 模块：`-dontobfuscate` 避免多模块独立混淆产生同名类冲突；切片顶层类逐一 keep（含 `AiCommunicateOcrSnapshot`、`OemRemoteMaterialCatalogWorker`）；`paddleocr/okhttp/okio/POI 系` dontwarn。
- material 模块内置 `material_hub_title` 字符串与 `Theme.MaterialHubOverlay` 默认资源，通过 `verifyReleaseResources`。

### 构建与交付验证

- `:app:assembleDebug` 通过；6 模块（core/host-bridge/ai-reply/material/automation/developer）`assembleRelease` 通过。
- AAR 抽查：host-bridge 1536 内核类完整、切片无泄漏；feature jar 类完整。
- `publishReleasePublicationToOemLocalRepository` 全量发布；catalog 25 个 artifact 全部在仓库。
- `export-sdk.ps1` 三模式验证：`all`（271.2MB）、`ai,material,automation,developer`（269.8MB）、`core`（204.5MB），manifest 0 missing。
- 新增演示工程 `OEM/demo/android`（克隆 company，profile 切换）：`-PdemoProfile=full` 234.7MB，`-PdemoProfile=lite` 202.2MB，四个可选模块增量约 32.5MB。
- 新增 `third_party/stellar/shizuku/aidl` 的 `publish-local.gradle` 接入，补齐 `stellar-shizuku-aidl:1.0.0` 产物。

### 提交注意

- 不提交 `OEM/demo/android/app/build/outputs/apk/debug/demo-full.apk`、`demo-lite.apk`（均大于 100MB）。
- 不提交 `OEM/demo/android/.gradle-home`、`local-sdk`、`build` 及 SDK 仓库产物。
- 设计文档：`OEM/DesignWithTRAE/sdk-modular-architecture/index.html`。

## 2026-08-04 模块入口显隐修复与 sherpa-onnx 剥离

### 背景

- 模块化拆分包此前仅做到「未选模块时入口点击无反应」，入口本身仍显示；客户要求按已选模块真正显/隐入口。
- OEM 版本未使用 sherpa-onnx 离线语音识别框架（主项目曾开发语音输入但不满意），需从 OEM 全线剥离语音输入功能代码；fcitx 内核自带的「切换系统语音输入法」入口与 sherpa 无关，经确认保留。

### 入口显隐修复

- 工具栏 / 定制工具栏（含排序）：`InputMethodSettingsBridge.unavailableToolbarItems(context)` 与 `ImeToolbarCustomizer` 按 `OemAddons.hasAiReply/hasMaterialHub` + `OemEnterpriseConfig.isAiReplyEntryEnabled/isMaterialLibraryEntryEnabled` 决定 AI回复、素材库入口显隐。
- app 设置页：自动化设置卡片按 `OemAddons.hasAutomation` 显隐；开发者入口按 `OemAddons.hasDeveloper` 显隐（已确认生效）。
- 键盘工具面板（fcitx 内核 `StatusAreaWindow.buildEntries`）：新增 `MaterialHubHostBridge.bindAvailability/unbindAvailability/isAvailable` 可用性桥接，`AiInputMethodService` 在 `MaterialHubHostBridge.bindLaunch` 之后绑定（`MATERIAL_HUB -> hasMaterialHub && isMaterialLibraryEntryEnabled`、`AI_REPLY -> hasAiReply && isAiReplyEntryEnabled`），`onDestroy` 解绑；面板中 AI回复/素材库入口与工具栏保持同一显隐策略。

### sherpa-onnx 剥离

- 源码层：移除语音输入相关代码与 `AppPrefs.voice` 分类（含 `syncToDeviceEncryptedStorage` 残留引用）；Gradle 仅保留 `sherpa_model/**` 资产排除规则。
- SDK 仓库三层清理：`build/oem-sdk-repo`、`OEM/SDK/repo`、`demo/android/local-sdk/repo` 中的 `sherpa-onnx` 产物全部移除，各级 POM 校验无 sherpa 依赖；`export-sdk.ps1` 重新导出交付 zip。
- demo 工程：`demo/android/app/build.gradle` 移除 sherpa-onnx 依赖与 3 条 `pickFirsts`（libsherpa-onnx-c-api/cxx-api/jni.so）。
- `Doc/07-SDK实施与发布清单.md` 同步移除 sherpa-onnx 运行时产物描述。

### 构建与交付验证

- `:app:assembleDebug` 通过；`:ai-ime:host-bridge`（org.fcitx.fcitx5.android:app:1.0）与 `:oem-ime-host-bridge`（1.2.0-alpha01）重新发布。
- demo 重建：full / lite 两 profile 构建成功并安装到真机；APK dex 抽查 `bindAvailability` 符号存在，lite 面板不再显示 AI回复/素材库入口。

### 提交注意

- 不提交 `OEM/demo/android/apk-archive/` 下大于 100MB 的归档 APK 及各级 `build`、`local-sdk`、仓库产物。

## 2026-08-04 AI回复与素材库多模式分发升级（域名配置 + 宿主回调）

### 背景

- 此前仅会话存档（输入质检）支持「第三方域名配置 + 宿主回调」多模式分发；客户要求 AI回复、素材库两个功能也具备同等能力，实现完全依赖宿主回调的对接方式（数据不出宿主）。
- AI回复（截屏 + Prompt → 大模型生成回复）此前只能走企业 `apiBaseUrl` 域名通道；素材库（企业集中管理素材的增删改查）此前只能走域名拉取。
- 本轮对两个功能新增宿主回调模式，并把 wiki 官网三个模块的配置文档统一为标准 SOP 结构。

### SDK Core 契约扩展（oem-ime-sdk-core）

- 新增分发模式枚举（与会话存档 `OemSessionArchiveMode` 同风格，全部带中文 KDoc 与必填/选填说明）：
  - `OemAiReplyDispatchMode`：`CLOUD_ONLY`（默认）/ `HOST_ONLY` / `DUAL_WRITE`（宿主优先、失败回落域名）。
  - `OemMaterialDispatchMode`：`LOCAL_ONLY` / `CLOUD_ONLY`（默认）/ `HOST_ONLY` / `DUAL_WRITE`。
- AI回复宿主契约：`OemAiReplyRequest`（requestId/sessionId/generateMode/toneKey/toneLabel/systemPrompt/userPrompt/ocrText/screenshots/replyCount/temperature/topP/maxTokens/timestamp/hostApplicationId）、`OemAiReplyScreenshot`（JPEG Base64，宽缩放至 1280 内）、`OemAiReplyResult`、`OemAiReplyResultCallback`、`fun interface OemAiReplyHostDelegate`。
- 素材库宿主契约：`OemMaterialFetchRequest`、`OemMaterialFetchResult`、`fun interface OemMaterialHostDelegate`（拉取）；`OemRemoteMaterialItem` + `OemRemoteMaterialPart.Text/Attachment`、`OemRemoteMaterialCatalogEvent.Upsert/Remove/Invalidate`、`OemRemoteMaterialCatalogListener`（推送）。
- `OemSdkConfig` 新增 `aiReplyDispatchMode` / `materialDispatchMode` / `aiReplyHostDelegate` / `materialHostDelegate` 四字段；`OemManifestConfig` 与 `OemManifestMetaKeys` 新增 `com.paopaoime.oem.aiReplyDispatchMode` / `com.paopaoime.oem.materialDispatchMode`。
- `OemImeSdk` 对象新增：`getAiReplyDispatchMode/setAiReplyDispatchMode`、`getMaterialDispatchMode/setMaterialDispatchMode`（null 回退 config 或 Manifest，支持运行时切换）、`setAiReplyHostDelegate/isAiReplyHostDelegateRegistered`、`setMaterialHostDelegate/isMaterialHostDelegateRegistered`、`dispatchAiReplyGeneration`、`dispatchRemoteMaterialFetch`、`notifyRemoteMaterialUpserted/notifyRemoteMaterialRemoved/invalidateRemoteMaterialCatalog`、`installRemoteMaterialCatalogListener`；`initializeFromManifest` 增加两个委托参数。

### AI回复宿主回调链路

- `AiCommunicateReplyService.generateReplies` 重构为「校验 → 宿主通道 → 域名通道」：`resolveAiReplyDispatchMode` 决定通道；非 CLOUD_ONLY 时先走 `generateRepliesViaHost`（编码截屏 → 组装 `OemAiReplyRequest` → suspend 包装异步回调，单次等待上限 90 秒）；HOST_ONLY 失败直接对用户报错，DUAL_WRITE 失败回落原域名链路。
- 回调返回 `provider = "host_callback"`，宿主模式下不再产生云端 token 上报；`AiReplyTaskUploadWorker` 在 HOST_ONLY 且无授权 token 时直接 `Result.success()` 免重试。
- `MaterialHubActivity` 生成等待超时按模式自适应：域名通道 15 秒，宿主/DUAL 通道 150 秒（`resolveAiGenerationTimeoutMs`），覆盖 PASTE / IMAGE_REPLY / 请求 / 刷新四处入口。

### 素材库宿主回调链路（拉取 + 推送双向）

- `OemRemoteMaterialService.refreshRemoteCatalog` 按 `resolveMaterialDispatchMode` 分支：LOCAL_ONLY 写空目录；HOST_ONLY 走 `refreshRemoteCatalogFromHost`（30 秒超时）；DUAL_WRITE 宿主优先、失败回落域名；CLOUD_ONLY 保持原逻辑。
- `OemRemoteMaterialHostBridge`（internal object，构造 init 自动安装，幂等）：把宿主推送的 `Upsert/Remove/Invalidate` 事件实时合并进本地缓存抽屉；Invalidate 触发 `OemRemoteMaterialAutoSync.warmUp(force=true)` 重新拉取。
- 映射函数 `mapAttachmentType/mapPickerType/defaultMimeType/buildMaterialSignature/mapHostMaterial` 提升为文件级 private，域名与宿主两条链路共用同一套校验与签名逻辑。

### 配置解析优先级（三模块统一）

- 开发者调试覆盖（developer 模块）→ `OemSdkConfig` 运行时参数 → AndroidManifest meta-data → 默认值 CLOUD_ONLY；宿主回调委托对象只能由宿主代码注入（初始化时传入或 `setXxxHostDelegate` 动态注册）。

### wiki 官网改造（OEM/website/oem）

- AI回复 / 素材库 / 会话存档三个模块页统一为「①配置方式选择 → ②API对接（域名配置）→ ③宿主回调对接（字段说明表 + 代码示例）→ ④功能调试台 → ⑤验收SOP」结构，企业用户可按 SOP 逐节集成。
- 新增 AI回复调试台（纯前端模拟）：生成 `OemAiReplyRequest` 示例 payload、模拟宿主回传 `OemAiReplyResult`、预演三种分发模式下宿主通道失败的 SDK 行为。
- 修正会话存档 `OemSessionArchiveEvent` 字段说明为契约真实字段（eventId/sessionId/hostApplicationId/targetPackageName/targetAppName/maskedContent/inputSource/timestamp/policyState），补充云端上报协议 `POST {apiBaseUrl}/api/v1/oem/input-qa` 说明。
- 「配置方式」页改为多模式分发总览（三模块模式矩阵 + 配置解析优先级）；catalog（内联 + `OEM/SDK/catalog/oem-sdk-catalog.json`）新增 `aiReplyDispatchMode` / `materialDispatchMode` 两个 configKey；一键安装 SOP 的初始化示例同步新增字段。

### 构建验证

- 四个模块编译通过：`:oem-ime-sdk-core` / `:oem-ime-sdk-ai-reply` / `:oem-ime-sdk-material` / `:oem-ime-host-bridge` 的 `compileDebugKotlin` 全部 BUILD SUCCESSFUL。
- 修复两处编译问题：`OemEnterpriseConfig.kt` 补齐 `OemAiReplyDispatchMode/OemMaterialDispatchMode` 显式 import；`AiCommunicateReplyService.kt` 补齐 `kotlin.math.roundToInt` import。
- `:app:assembleDebug` 整包构建通过：`BUILD SUCCESSFUL in 4m 27s`（OEM 项目按规只打 debug 包）。
- 产物：`OEM/android/app/build/outputs/apk/debug/tankeIme_debug_nomodel.apk`，大小 `160408124 bytes`（约 153MB），超过 100MB，提交 GitHub 时必须排除。

### 后续回归清单

- HOST_ONLY：AI回复面板触发后宿主收到请求（截屏 Base64 可解码、Prompt 非空），回调后面板展示宿主返回回复，全程无 apiBaseUrl 大模型请求。
- DUAL_WRITE：宿主正常走回调；宿主返回 false / 超时后自动回落域名通道出回复。
- 素材库 HOST_ONLY：抽屉打开展示宿主回传素材；调用 `notifyRemoteMaterialUpserted/Removed` 后抽屉即时增删。
- CLOUD_ONLY（默认）回归：AI回复、素材库、会话存档原有域名链路行为不变。
- 未注册委托时 HOST_ONLY 正确提示失败、DUAL_WRITE 正确回落，不崩溃。

### 提交注意

- 不提交 `OEM/android/**/build`、`OEM/website/oem/.edge-tmp*` 缓存目录、大于 100MB 的 APK 产物与 SDK 仓库产物。

## 2026-08-05 AI回复 Prompt/语气宿主预留 + 一键装 MD v2 + ECS 全量发布

### 背景

- 用户指出：AI回复 HOST_ONLY 模式下「SDK 拼装 Prompt 后交给宿主」的设计把 Prompt 与语气写死在 SDK 内，必须把 prompt、回复语气字段预留给宿主/第三方。
- 同时要求：参考旧版线上 AI 一键装 MD，重构官网「AI 一键安装」产物（SDK 分模块发布 ECS、MD 内给出模块直链、支持大模型按 SOP 从 0 集成全新宿主）；并把最新 OEM 按标准 SOP 从 0 构建 R8 加固 SDK，随最新网站发布 ECS 替换旧站。

### Prompt 与语气宿主预留（AI回复）

- `OemImeSdk.kt` 新增三个契约类型：`OemAiReplyPromptContext`（generateMode/toneKey/toneLabel/tonePromptHint/ocrText/screenshotCount/replyCount/hostApplicationId）、`OemAiReplyPromptPair`（systemPrompt/userPrompt）、`fun interface OemAiReplyPromptAssembler`。
- `OemSdkConfig` 新增三个选填字段：`aiReplyTonePresets`（宿主自定义语气列表）、`aiReplySystemPrompt`（宿主系统提示词）、`aiReplyPromptAssembler`（完全接管 Prompt 组装）；`OemImeSdk` 提供对应 getter/setter 与 `isAiReplyPromptAssemblerRegistered`。
- 解析优先级统一为：宿主预留 ＞ 开发者模块（developer addon）＞ SDK 内置默认。
  - `OemEnterpriseConfig.resolveDeveloperAiReplyConfig` 以 `base.copy()` 应用宿主 systemPrompt / tonePresets 覆盖。
  - 新增 `resolveAiReplyTonePresets(context)` / `resolveAiReplyPromptAssembler(context)`。
- `AiCommunicateReplyService.generateReplies`：存在宿主 assembler 时用 `OemAiReplyPromptContext` 组装 `OemAiReplyPromptPair`，双非空白才接管（systemPrompt 与 userPrompt 均替换），否则 Log.w 回退 SDK 内置；三种分发模式均生效。
- `MaterialHubActivity.resolveAiCommunicateToneOptions` 改走 `OemEnterpriseConfig.resolveAiReplyTonePresets`，面板语气选项随宿主预留实时生效。
- wiki（pages.js）：AI回复页新增「Prompt 与语气预留（宿主自定义）」小节（优先级说明 + HostApp 示例 + 上下文字段表）；HOST_ONLY 卡片文案更新为「Prompt 与语气可由宿主自定义或完全接管」；全部初始化示例补齐必填参数 `channel` 与 `licenseProvider`（`OemLicenseProvider { _, _ -> OemLicenseSnapshot.active() }` 占位）。

### 一键装 MD v2（网站 buildOneClickMd 重写）

- 方案决策：统一 MD 生成器 + 按已选模块动态裁剪（不再下载多份 MD）；网站「AI 一键安装」按钮按当前模块闭包实时生成；另发布全模块静态 latest 版到 `downloads/oem-ai-one-click-install-latest.md`（保持旧 URL 可用）。
- 不再提供整包 SDK zip：全部 SDK 模块统一发布 ECS Maven 仓库 `https://paopaoime.com/oem/sdk-repo`（标准 Maven 布局）。
- MD 结构 15 节：输入参数 yaml → 已选模块闭包 → §3 SDK 获取（3.1 统一 Maven 地址、3.2 逐模块 aar/pom 直链、3.3 离线镜像 PowerShell）→ 环境基线（JDK17/Gradle 9.4.1/AGP 9.2.0）→ 空白建工程 + 引导页规范 → 仓库配置 → 依赖 → packaging native 规则 → Manifest → HostApp 初始化（继承 FcitxApplication、`OemHostRuntime.initialize` → `OemImeSdk.initializeFromManifest`）→ 自动化 → 构建 → 验收 → 必查项 → 常见失败。
- app.js 新增 `SDK_REPO_URL` 常量与 `artifactUrls(coord, repoUrl)` 直链生成器。

### 从 0 构建 R8 加固 SDK 与 ECS 发布（2026-08-05 02:0x）

- 构建：`$env:GRADLE_USER_HOME='C:\g'; .\gradlew.bat` 六模块 `assembleRelease`（`minifyEnabled true`，R8 生效）+ `publishReleasePublicationToOemLocalRepository`，BUILD SUCCESSFUL in 6m 1s；产物刷新于 `OEM/android/build/oem-sdk-repo`（25 aar + 25 pom，约 198MB）。
- 发布坐标：`com.paopaoime.oem:oem-ime-sdk-core:1.1.0-alpha01`、`oem-ime-host-bridge:1.2.0-alpha01`、`oem-ime-sdk-ai/ai-reply/material/automation/developer:1.0.0-alpha01`、`internal:paddleocr4android:1.0.0`、`internal.stellar:*`、`org.fcitx.fcitx5.android:*:1.0`；历史本地 TTS 运行时后续已从 OEM 交付面移除。
- ECS（`/var/www/paopao/website/oem/`）：
  - 新增 `sdk-repo/`（com + org 全量 Maven 布局，201M）。
  - 网站替换：index.html / app.js / pages.js / debug-tools.js / styles.css / assets/ 全部更新；删除旧 `automation.html`。
  - downloads：旧 `oem-sdk-delivery-latest.zip`（321M）、旧 `oem-ime-demo-latest.apk`（183M）删除；`oem-ai-one-click-install-latest.md` 替换为 v2 全模块版（32754 字节）；新增 `paopao-ime-enterprise-demo-latest.apk`（175.9MB，`-PWITHOUT_EMBEDDED_MODEL_ASSETS=false` 含模型 debug 整包，2026-08-05 构建）。
  - 旧站整体备份：`/var/www/paopao/oem-site-backup-20260805.tar.gz`（713M）。
  - `API/` 渠道文档目录保留不动。
- 健康检查（全通过）：`/oem/`、`/oem/app.js`、`/oem/pages.js`、latest.md、sdk-repo aar/pom 直链均 200；`/health`、`/admin`、`/admin/user/login` 200；两 auth 接口 401（未带 token 正常）。

### 构建验证

- 四模块增量编译通过（Prompt 预留改造后）：BUILD SUCCESSFUL in 1m 49s。
- 六模块 release + R8 + publish：BUILD SUCCESSFUL in 6m 1s。
- 演示包：`:app:assembleDebug`（含模型）BUILD SUCCESSFUL in 2m 56s，产物 `tankeIme_debug.apk` 175.9MB。

### 提交注意

- 不提交 `OEM/android/**/build`（含 oem-sdk-repo 198MB）、`OEM/website/oem/.edge-tmp*` 缓存目录、大于 100MB 的 APK 产物。

## 2026-08-05 第三方集成演练回修：host-bridge alpha05 + 官网 MD 模板修正 + nginx 404 + ECS 重发布

### 背景

- 模拟两家第三方企业（探客输入法 `com.tankeapp.latest` 全功能、云客输入法 `com.yunkeapp.latest` 仅 AI回复+素材库）按官网流程从 0 集成，全程宿主回调模式；演练发现 20 项问题（详见 `Drill/三方集成演练记录-2026-08-05.md`），本轮完成 R1-R5 回修并做 alpha05 回归演练。

### SDK 回修（oem-ime-host-bridge 1.2.0-alpha02 → alpha05）

- R1 依赖可见性：`oem-ime-host-bridge/build.gradle` 中 `org.fcitx.fcitx5.android:app` 由 `implementation` 改 `api`，宿主编译期可见 `FcitxApplication`，不再需要猜传递依赖。
- R3 重复入口：`SplashActivity` 移除 MAIN/LAUNCHER 且 `exported=false`；`FcitxInputMethodService` 由合并改 `tools:node="remove"` 整体移除（避免输入法选择器出现重复服务项）。
- R2 资源污染根除：`app_name` 多语言污染（旧 AAR 携带 8 个 locale 的 "Stellar"）通过改名根除——fcitx 侧 `resValue("string","app_name",…)` 改 `fcitx_app_name`（`ai-ime/app` 与 `ai-ime/host-bridge` 各 1 处），`input_method.xml` subtype label、Manifest application/service label、6 个 Kotlin 的 `R.string.app_name` 引用全部同步改名；`org.fcitx.fcitx5.android:app:1.0` 重新构建发布（ai-ime `:host-bridge:publishReleasePublicationToOemLocalRepository`，BUILD SUCCESSFUL in 4m 44s）。
- 品牌单点覆盖：OEM 主工程 `values`/`values-zh` 移除 `app_name` 项，`activity_splash.xml` 引用改 `paopao_app_name`；品牌统一收敛到 `oem_ime_display_name`（宿主覆盖一个字符串即可定制全部显示名：IME 选择器/设置页/素材页/闪屏）；默认值中性化为「泡泡输入法」，并移除 `values-zh` 的实体项（alpha04 回归发现 zh 限定符实体值会压过宿主默认覆盖，alpha05 修复）。
- 发布：`.\gradlew.bat :oem-ime-host-bridge:publishReleasePublicationToOemLocalRepository`（OEM 复合构建自动携带 ai-ime 源码变更），alpha03/alpha04/alpha05 依次发布，BUILD SUCCESSFUL in 5m 47s / 5m 22s / 5m 56s。
- AAR 验证：解压 alpha05 确认无 `app_name` 条目、无 LAUNCHER、`values` 与 `values-zh` 的 `oem_ime_display_name` 均为引用项（宿主可覆盖）。

### 官网 MD 模板与 catalog 修正（R4）

- `app.js` `buildOneClickMd`：§6 补全 `pluginManagement`/`plugins` 完整 settings 模板（含 AGP 9.x 内置 Kotlin 禁加 `org.jetbrains.kotlin.android` 说明、腾讯 Gradle 镜像分发地址）；§8 pickFirsts 补 `libFcitxJsi.so`/`libfcitx5.so`；§9 新增 `oem_ime_display_name` 宿主覆盖步骤与 meta 四疑问写入规则（customerId=企业码、channel 选填默认 release、apiBaseUrl 条件必填、enableUi 语义说明且 ai/material 必须 true）；§12/13 安装验收补 IME 服务全名 `com.paopaoime.aikeyboard.ime.service.AiInputMethodService` 与国产 ROM 安装 FAQ 6 条（-99/深度扫描引导/熄屏假错误/更新免引导/IME 被切回/WRITE_SECURE_SETTINGS）；必查项与常见失败各扩充 4 条。
- catalog（`OEM/SDK/catalog/oem-sdk-catalog.json` 与 index.html 内联兜底双写）：core 的 bridge 坐标升 `1.2.0-alpha05`；meta 六字段 description/default/required 按实测语义改写；ai、material 的 `enableUi` 默认值改 `true`。
- 静态 `downloads/oem-ai-one-click-install-latest.md` 用 `Drill/gen-full-md.js`（node 载入 app.js 纯函数部分）重新生成（30792 字符，全 5 模块，alpha05 直链）。

### nginx R5：sdk-repo/downloads 缺失文件真 404

- `/etc/nginx/conf.d/paopao.conf` 两个 server 块（80/443）的 `location /` 前各插入 `location ^~ /oem/sdk-repo/` 与 `location ^~ /oem/downloads/`（`try_files $uri =404`）；备份 `paopao.conf.bak-drill-20260805`；`nginx -t && systemctl reload nginx` 通过。
- 验证：alpha05 pom/aar/module、metadata、latest.md、catalog 均 200；alpha04 路径与虚构路径均 404（不再返回首页 HTML 假 200）。

### ECS 重发布

- sdk-repo：删除远端 alpha01-alpha04 bridge 版本；上传 alpha05 全量（aar/pom/module/sources + 5 个 checksum）与 `org.fcitx.fcitx5.android:app:1.0` 新 AAR（MD5 与本地一致）；metadata 重写为仅含 alpha05。
- 站点：`index.html`（内联 catalog 同步）、`SDK/catalog/oem-sdk-catalog.json`、`downloads/oem-ai-one-click-install-latest.md` 替换上传。
- 权限修复：Windows scp 上传目录默认 700 导致 nginx worker 403/404，统一 `chmod -R a+rX` 后恢复（此后每次 scp 上传均需补此步）。

### alpha05 回归演练（云客，真实 ECS 仓库）

- 宿主侧品牌定制收敛为一行：`<string name="oem_ime_display_name">云客输入法</string>`（删除演练期 14 个 locale 应急覆盖后仍然全 locale 标签正确）。
- `:app:assembleDebug --refresh-dependencies` BUILD SUCCESSFUL in 2m 12s（远程仓库解析 alpha05 + 新 fcitx app AAR）。
- 同包名更新安装免 OPPO 引导直接 Success；`ime enable/set` 成功；`MainActivity` 打开成功（mCurrentFocus 确认）；设置页搜索框唤起键盘 `mInputShown=true`（期间再次出现默认 IME 被 OPPO 切回百度的 F17 现象，重设即恢复）。
- `ime list -a` 确认服务 labelRes 解析到宿主覆盖的 `oem_ime_display_name`（default 与 zh 配置均解析为引用项，品牌泄漏闭环）。
- 证据：`Drill/evidence-yunke-r3-keyboard.png`、`Drill/evidence-yunke-r2-main.png`。

### 提交注意

- 不提交 `OEM/android/**/build`（oem-sdk-repo 含 AAR 约 200MB）、`Drill/**/build` 与 Drill APK 产物（均大于 100MB）、`OEM/website/oem/.edge-tmp*` 缓存目录。

## 2026-08-05（午）用户真机复验反馈回修：MD 版本自检横幅 + 启动页转交 SDK 主 UX

### 背景

- 用户用 01:13 生成的旧版 MD（SDK 1.1.0-alpha01）在空白目录构建宿主，复现两个已修复缺陷：① 应用名显示 Stellar（alpha01 AAR 的 `app_name` 多语言资源污染）；② 启动页为调试式「输入法接入引导页」，而非标准版的启用/切换选择页与设置页。
- 根因：① 旧 MD 无版本自检提示，用户无法识别本地副本过期；② MD §5 把宿主 MainActivity 规格写成调试引导页，未告知 SDK 自带完整主 UX（SplashActivity 路由：未 setup → SetupActivity 选择页；已 setup → OemMainActivity 设置页）。

### 官网 MD 模板修正（app.js，已发布 ECS）

- 抬头横幅增加 `host-bridge` 版本位与【版本自检·必读】行：旧本地副本（bridge 低于 1.2.0-alpha05）必须重新下载再执行。
- §5 宿主入口页规范重写：MainActivity = 透明转发页（显式 Intent 启动 SDK `com.paopaoime.aikeyboard.ime.ui.activity.SplashActivity` 后 finish，附 Java 模板）；调试引导页降级为可选 SetupDiagActivity（禁止占用 launcher）。
- 验收标准/必查清单/常见失败同步：启动后须进入 SDK 接入向导或设置页；新增失败项 11（自绘调试页）/12（应用名 Stellar=旧 MD）。
- 静态 `downloads/oem-ai-one-click-install-latest.md` 重新生成（31816 字符）并上传；`app.js` 上传；`chmod a+rX` 后线上验证含「版本自检」与 SplashActivity 转发规格。

## 2026-08-05（午后）MD 增补「二次开发对接 SOP」章节（字段级回调参考）

### 背景

- 用户反馈：构建验收后想自建页面测试素材库宿主回调等二次开发时，MD 缺少字段级 API 信息（此前仅指向官网模块页）。要求 MD 自足：含 API 模式/宿主回调两种对接 SOP、字段表、问答式信息收集指引。

### 改动（app.js buildOneClickMd，已发布 ECS）

- 新增 §13「二次开发对接 SOP（API 模式与宿主回调 · 字段级参考）」，安装验收顺延为 §14；按所选模块条件生成小节：
  - 通用规则：回调包名/入口、分发模式解析优先级、CLOUD_ONLY 不触发回调的硬规则、delegate 返回值与 callback 必调语义；
  - AI 回复：模式矩阵 + `OemAiReplyHostDelegate`/`OemAiReplyRequest`（14 字段含 screenshots Base64、systemPrompt/userPrompt 透传）/`OemAiReplyResult` 接口原文 + Prompt 组装器/语气预设自定义 + 对接问答 4 问 + 测试页 SOP；
  - 会话存档：四模式矩阵 + `OemSessionArchiveListener`/`OemSessionArchiveEvent`（9 字段）+ developer 模块运行时切模式测试入口；
  - 素材库：四模式矩阵 + `OemMaterialHostDelegate`/请求/结果/`OemRemoteMaterialItem`/`OemRemoteMaterialPart`(Text/Attachment) 全字段 + 宿主推送三接口（upsert/remove/invalidate）+ 对接问答 4 问 + 「素材回调测试页」SOP（三按钮+调用日志区+触发链路+成功标准）；
  - 授权通用：`OemLicenseProvider`/`OemLicenseSnapshot` 工厂与状态枚举 + 运行时查询/刷新 API。
- API 面来源：`oem-ime-sdk-core/.../OemImeSdk.kt`（接口与数据类原文逐行核对），修复生成脚本缺 `hasMaterial` 变量。
- 静态 MD 重新生成（38975 字符）+ app.js 上传 ECS，线上验证含新章节与字段表。

## 2026-08-05（演练中）appKey 语义收敛：与企业码同值

### 背景

- 用户演练时执行 AI 因 MD 要求必填 `appKey` 而后台企业表只有「企业码」列反向询问；核查后台源码快照（ECS_paopaoime/snapshots）确认 `appKey|app_key` 零引用——后台不消费 appKey，SDK 仅 `require(appKey.isNotBlank())` 并透传授权回调，真正校验按 `customerId`。

### 改动（已发布 ECS）

- 约定收敛：**当前版本 appKey 与企业码同值（双字段同填）**；MD §1 输入参数/规则、§9 写入规则、catalog（JSON+内联）appKey description 全部改为「直接填企业码，无需单独索取」，并明确「不要因缺少独立 AppKey 而中断或询问用户」。
- meta 注释模板 `buildMetaTemplate` 的必填提示由「请填写后台交付的值」改「请按字段说明填写」，消除二次误导。
- 静态 MD 重新生成（39094 字符）+ app.js/index.html/catalog 上传验证。
- 后续项（R6 一并）：后台是否为 AppKey 增加独立字段作安全增强，演练后决策；当前同值约定已在文档显式声明。

## 2026-08-05（午间）黑屏与 Stellar 双缺陷根因修复（alpha07 / fcitx 1.1 / automation alpha02 / stellar-manager 1.0.20）

### 背景

- 用户按 alpha05 版 MD 重建云客工程后仍出现两问题：桌面图标显示 Stellar、打开 App 黑屏（logcat：`OemMainActivity` 抛 `You need to use a Theme.AppCompat theme`）。

### 根因（三处，逐一实锤）

1. **黑屏=主题崩溃**：宿主 application 主题为 framework 主题（`@android:style/Theme.DeviceDefault`），SDK 内未声明主题的 AppCompatActivity 继承后崩溃；此前 bridge Manifest 用 `tools:replace` 强改宿主 application 主题属侵入式做法且实际未生效。
2. **Stellar=三方伴生件资源污染**（真凶，非 fcitx 残留）：automation 模块 POM 传递依赖 `stellar-manager` AAR，其 10 个 locale 用裸公开键 `app_name="Stellar"`（无引用点的死键），资源合并后以 locale 限定符优先级覆盖宿主标签。此前归因"旧 fcitx AAR 缓存"仅解释旧副本现象，本轮 tanke 回归在全新 fcitx 1.1 下仍复现才定位到 stellar-manager。
3. **同版本号重发布无效**：Gradle 对 release 版本元数据永久缓存，同版本内容替换构建机永不重下——fcitx `app` 与 bridge 各 bump 一次（1.0→1.1、alpha05→alpha06），且 alpha06 因 inter-AAR 主题冲突缺陷发布后再次 bump 至 alpha07（教训：发版前必须用真实宿主工程回归）。

### 改动

- **Manifest 主题钉死**：bridge Manifest 移除 application 级 `android:theme` 与 theme 的 `tools:replace`（不再侵入宿主主题）；bridge 内全部 Activity 显式声明主题（自研页 `Theme.AppTheme`、org.fcitx 页与 fcitx AAR 声明一致的 `Theme.FcitxAppTheme`，避免 inter-AAR 合并冲突）；fcitx app Manifest 5 个无主题 Activity 补 `Theme.FcitxAppTheme`；OEM 主工程 app Manifest 9 处补齐（等价行为零风险）。
- **stellar-manager 去污染**：删除 10 个 locale 的裸 `app_name` 实体（grep 确认全模块零引用）；版本 1.0.19→1.0.20（VERSION_CODE 100200）。
- **automation 1.0.0-alpha01→alpha02**：依赖 stellar-manager:1.0.20。
- **fcitx app 1.0→1.1**（VERSION_NAME 同步）；**bridge alpha05→alpha07**，api 坐标指向 app:1.1。
- **MD 增补**（app.js，已发布 ECS）：§9 写入规则新增「宿主主题约束（强制）」——application 主题必须 AppCompat 系谱（`Theme.AppCompat.*`/`MaterialComponents`/`Material3`），禁 `@android:style/*`；必查清单移除 theme 的 tools:replace 要求；常见失败新增第 11 条（黑屏+Theme.AppCompat 报错）；版本自检门槛升至 alpha07；坐标全部更新（fcitx 1.1 / bridge alpha07 / automation alpha02 / stellar-manager 1.0.20）。
- **catalog 双写 + 静态 MD 重新生成**（39683 字符）并发布 ECS（含 metadata 合并与校验和重建、chmod -R a+rX、curl 全 200）。

### 回归（tanke 演练宿主：framework 主题 + R8 minify + 全模块）

- Manifest 合并通过；aapt2 badging 全 locale 标签为宿主角名（Stellar 计数 0）；R8 missing_rules 为纯 dontwarn 项，已并入 tanke proguard（MD 层面提示宿主启用 R8 时按 missing_rules.txt 追加）。
- 真机验证由用户侧执行（云客工程需按新 MD 重建或升级坐标）。

## 2026-08-05（下午）stellar-shizuku-aidl 重复类冲突修复（bridge alpha08）

### 背景

- 用户按 alpha07 版 MD 模拟构建 automation 全模块时构建失败：`stellar-shizuku-aidl`（vendored，包名 `moe.shizuku.server`）与 fcitx app POM 传递依赖的官方 `dev.rikka.shizuku:aidl:13.1.5` 含重复类 `moe.shizuku.server.IShizukuService`，checkDuplicateClasses 失败。

### 根因

- 上游 fcitx5-android 官方 POM 本就依赖 `dev.rikka.shizuku:aidl`（Shizuku 集成）；stellar 套件自带 vendored 副本（`shizuku/aidl` 模块，namespace 同为 `moe.shizuku.server`）。MD 依赖块把 vendored 副本列为显式依赖 → 与 fcitx 传递的官方件同包同类冲突。
- 主工程行为核对：主工程内 vendored aidl 仅 `compileOnly` 参与编译，运行时由 fcitx 传递的官方件提供类——即正确形态是「vendored 副本不进宿主运行时类路径」。此前 tanke 回归未含完整 stellar 显式清单，故未暴露。

### 改动（已发布 ECS）

- catalog（JSON+内联）automation 模块 artifacts 移除 `stellar-shizuku-aidl:1.0.0`（保留 `stellar-shizuku-api`，其包名 `roro.stellar.shizuku` 无冲突）。
- MD §7 必理解新增：`stellar-shizuku-aidl` 禁止显式加入（类由 fcitx 官方传递依赖运行时提供；显式加入产生 duplicate class），依赖块以模板为准不得自行补充 stellar 其它构件。
- bridge bump 1.2.0-alpha08（内容不变，仅版本门槛，使 alpha07 旧副本被版本自检拦截）；metadata 合并、web 文件上传、chmod、curl 全 200。
- 回归：tanke 依赖块对齐新 MD 全 automation/stellar 清单（含 shizuku-api、不含 shizuku-aidl）+ bridge alpha08 → checkReleaseDuplicateClasses 通过、BUILD SUCCESSFUL（5m34s）。

## 2026-08-05（傍晚）kotlin-stdlib 被 force 降级致 SpillingKt 运行时崩溃（bridge alpha09）

### 背景

- 用户按 alpha08 MD 构建的云客工程（com.yunkeime.keyboard）安装与切换输入法正常，但启动 App 闪退；adb crash buffer 实锤：`NoClassDefFoundError: kotlin.coroutines.jvm.internal.SpillingKt`，崩溃点 `FcitxInputMethodService.onCreate` 协程体与 App 启动同源。

### 根因

- 执行 AI 在宿主 `app/build.gradle.kts` 自行追加 `resolutionStrategy { force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21") }`（注释误写"优先保留高版本"，force 实为钉版降级）。`SpillingKt` 为 Kotlin 2.1.0 新增类，fcitx AAR（POM 声明 stdlib 2.3.21）协程状态机引用它；stdlib 被钉到 2.0.21 后运行时缺类。SDK 制品无缺陷。

### 改动（已发布 ECS）

- 用户演练工程 DEMO：删除 force 块 → 重建 → 真机安装验证：启动直达 `OemMainActivity`，无 FATAL（dex 含 SpillingKt）。
- MD（app.js）：§7 依赖模板内置 `implementation "org.jetbrains.kotlin:kotlin-stdlib:2.3.21"` 下限行（含注释）；「必须理解」新增 kotlin-stdlib 版本红线（禁 force/BOM 钉版降级，必须 ≥2.1.0）；常见失败新增第 12 条（SpillingKt 崩溃对照与处置）。
- bridge bump 1.2.0-alpha09 作版本门槛；catalog 双写；静态 MD 重新生成（39840 字符）；metadata 合并、web 上传、chmod、curl 全 200。

## 2026-08-05（夜）第三方执行日志复盘 → SOP 六项优化（bridge alpha10）

### 背景

- 用户提供另一 AI 按 alpha08 MD 从零集成的完整执行日志（DEMO 工程，最终成功安装）。复盘其全部绕行/猜测环节，转化为 MD 模板级修复，目标「执行 AI 零反推、零绕行」。

### 六项优化（均已入 MD 并发布 ECS，静态 MD 41847 字符）

1. **Java 版 HostApp 模板**（§10）：`initializeFromManifest` 11 参逐参注释语义（源码核对：hostUserIdHash/autoInitializeAiRuntime/initializeLogCollector/三个宿主回调/三个 prompt 预留），`OemLicenseSnapshot.Companion.active()` Java 四参显式调用——消除执行 AI 下载 AAR + javap 反推默认值的整段流程。
2. **依赖模板补 appcompat**（§7）：core POM 不传递 appcompat，执行 AI 首次构建必撞 `AppCompatActivity` 找不到；模板内置 `androidx.appcompat:appcompat:1.7.1`。
3. **lint 必配项**（§8）：`abortOnError=false` + `checkReleaseBuilds=false`，说明 AGP 9.x lintVital 的 `AtomicsKt` 兼容问题非代码缺陷。
4. **Manifest tools:replace 恢复 theme**（§9）：fcitx 官方 AAR 在 `<application>` 声明默认 name/theme，宿主必须 `tools:replace="android:name,android:label,android:theme"` 三者全量覆盖，否则 merger 冲突；并解释 theme 覆盖为宿主 AppCompat 系主题即可（SDK 页面自带显式主题）。此前 alpha09 文案漏 theme 会导致执行 AI 再次自行补。
5. **wrapper 镜像与绕行法**（§6）：distributionUrl 首选阿里云镜像（已验证 200）腾讯备用；本机 Gradle 与 AGP 不匹配时「临时注释 include(":app") 生成 wrapper 再恢复」的实测绕行法。
6. **版本门槛**：bridge bump 1.2.0-alpha10（内容不变），catalog 双写、metadata 合并、ECS 全量发布、curl 全 200、关键文案 grep 复核通过。

## 2026-08-05（夜二）第二轮演练复盘：开发者入口不显示 → SOP 模板级锁定（bridge alpha11）

### 背景

- 第二轮演练（DEV 工程，全模块）安装启动正常，但设置页右上角开发者入口不显示；执行日志显示执行 AI 把 `enable_developer`/`enable_developer_entry` 写成 false、`enableAutomation` 留模板默认 false，且为 Java 模板补 `.INSTANCE` 时再次 javap 反推。

### 根因

1. catalog developer 模块描述含「正式生产交付建议关闭」诱导语，执行 AI 据此把模板 true 改 false；
2. `enable_developer_entry` 在 bridge AAR Manifest 已声明（true），宿主重声明触发 merger 冲突，执行 AI 加 tools:replace 时顺手改值；
3. `enableAutomation` 模板默认 false 与 §11「选购 automation 必须 true」分离，执行 AI 只抄模板不读 §11；
4. §10 Java 模板漏 `.INSTANCE`（`OemImeSdk`/`OemHostRuntime` 为 Kotlin object 单例，Java 必须经 INSTANCE 调用）。

### 改动（已发布 ECS，静态 MD 42286 字符）

- buildMetaTemplate 支持 `toolsReplaceValue`：模板对 `roro.stellar.permissions`、`enable_developer_entry` 直接渲染 `tools:replace="android:value"`（合并冲突在模板层消解）；
- catalog 双写：`enableAutomation` 默认 false→true（选购 automation 必须 true）；developer 模块描述去诱导语改「集成/演练期必须启用，仅正式交付且企业明确要求时才关闭」；两个 developer meta 描述加「模板值为基线，执行方不得自行改 false」；
- §9 写入规则新增「meta 值基线（强制）」条款，点名典型违规场景；
- §10 Java 模板补 `OemHostRuntime.INSTANCE` / `OemImeSdk.INSTANCE`；
- bridge bump 1.2.0-alpha11；DEV 工程 meta 改 true 重建真机验证：启动直达 OemMainActivity。

## 2026-08-05（夜三）席位上报缺失修复：analytics 独立通道与官方默认回退（core 1.1.0-alpha02 / bridge alpha12）

### 背景

- 用户反馈：按 SOP 填入企业码打包的两个宿主（云客 4wBUsH3k / 学生手机 2X8cv95U）安装并操作后，后台企业列表「席位记录数」未记录新设备。

### 根因

- aapt2 实锤：两包 `customerId`/`appKey` 正确，但**未声明 `apiBaseUrl` meta**；`OemAnalyticsReporter`（core alpha01）在 apiBaseUrl 缺失时走 `skip analytics` 分支，激活/心跳事件从未发出。
- 诱因在 SOP 侧：alpha08 起 MD 把 apiBaseUrl 改为占位符 + 「条件必填」，执行 AI 按字面不填；而席位上报通道此前与 apiBaseUrl 强耦合（语义本应是企业素材/AI 域名通道），导致「不配企业域名 = 无席位记录」的错误耦合。

### 改动（已发布 ECS）

- SDK（core 1.1.0-alpha02）：新增 `analyticsBaseUrl` meta（选填）；reporter 通道解析顺序 `analyticsBaseUrl` → `apiBaseUrl` → 官方默认 `https://www.paopaoime.com`，三者皆缺也能上报；不再 skip。
- catalog 双写：core 坐标升 alpha02、bridge 门槛 alpha12、新增 analyticsBaseUrl key 说明；app.js §7 写入规则新增「席位激活上报默认启用、执行方不得为上报目的自填/改写域名」。
- 端到端验证：DEV（4wBUsH3k、无 apiBaseUrl）升坐标重建 → 真机 logcat `report success eventType=activate` → 后台 DB 出现 `4wBUsH3k + com.yunkeai.key` active 席位记录。
- 用户侧动作：两个旧包（core alpha01）需按新 MD 重建一次，首启即补报激活。

## 2026-08-06（凌晨）apiBaseUrl 作用范围厘清与上报通道加密内置（core 1.1.0-alpha03 / bridge alpha13）

### 背景

- 用户质询：apiBaseUrl 应仅服务企业自有服务器通道；席位/设备统计是我们自己的埋点，应随基础模块加密内置，与企业服务器无关。

### 结论（作用范围厘清）

- `apiBaseUrl` 真实消费点仅两处，均为企业自有域名通道：① AI 回复 CLOUD_ONLY 代理模式拉取企业模型配置；② 企业素材 CLOUD_ONLY 拉取素材清单。开发者插件读写它仅用于调试。
- alpha02 的「analyticsBaseUrl → apiBaseUrl → 默认」回退链存在反向缺陷：配了自有域名的企业会把席位上报发往企业服务器（404）而丢记录。

### 改动（已发布 ECS）

- reporter 去除 apiBaseUrl 回退：通道仅认 `analyticsBaseUrl`（私有化覆盖，选填）→ core 内置加密官方通道（XOR+Base64 混淆存储，运行时解码；坦诚说明：属防 casual 检查级混淆，完整性保护仍靠 HMAC 签名 + 后端校验）。
- catalog/app.js/MD 文案同步：「官方上报通道加密内置于 core，与 apiBaseUrl 完全无关」。
- 端到端解耦证明：DEV 注入假 apiBaseUrl（fake.enterprise.example.com）重建 → 卸载重装 → logcat `report success eventType=activate`（上报无视企业域名）；随后移除假 meta 复原 DEV。

## 2026-08-06 管理后台企业管理重构：记账化原则与统计口径一致（后端 + admin 前端）

### 背景

- 用户反馈：8 月 6 日新包（com.aiyunke.key，云客 4wBUsH3k）安装打开后，后台仍无该企业最新数据；且企业管理页顶部统计卡片与下方列表对不上数。
- 用户明确设计原则：**不做强制 license 下发**；只通过「创建不限制使用时间的企业 + 企业可用数量」记账；**只手动禁用**，不做到期自动禁用；简单的数据上报统计。

### 根因（四层）

1. license resolve 存在 `app_key` 硬校验：AI 构建宿主上报的 appKey = 企业码（如 4wBUsH3k），而企业表存的是平台签发的 `pp_oem_*`，resolve 必然 INVALID，席位快照不落库。
2. analytics ingest 不写 `enterprise_id`，且历史回填是懒触发 + JOIN 条件含 app_key（同样 mismatch），导致席位记录与企业脱钩。
3. summary 统计未过滤 `enterprise_id IS NOT NULL`，把未归属企业的 demo 记录计入，卡片与列表口径不一致（activated 10 vs 6）。
4. 残留 grace 时间窗与 trial expires_at 强制逻辑，违背「不限制使用时间、只手动禁用」原则；其中 `isSeatWithinGrace` 函数已删但调用未清，属运行期隐患。

### 改动（已部署 ECS）

- `oemEnterpriseAnalyticsService.js`：ingest 按 customer_id 归因并写入 `enterprise_id`（ON DUPLICATE COALESCE 补齐）。
- `oemEnterpriseLicenseService.js`：删除 app_key 硬校验与 `INVALID_APP_KEY` 分支；席位存活判定简化为「已激活（first_activated_at 非空）且未手动禁用」；backfill JOIN 仅按 customer_id；backfill 不再为 trial 企业写 expires_at；summary 增加 `enterprise_id IS NOT NULL` 过滤；清除 grace/overdue 全部残留。
- `apps/admin` EnterpriseUsers：移除「超过7天未校验」列、枚举与统计卡；重建 dist 部署 ECS（旧版备份 dist.bak）。

### 验证

- DB 回填：7 条企业归属记录全部挂对（com.aiyunke.key → 云客；4 条 NULL 为无对应企业的历史 demo 数据）。
- 口径一致：summary `{totalSeats:20110, activatedSeats:6, remainingSeats:20104}` 与列表逐行求和完全相等；云客 5 条记录（4 激活 + 1 手动禁用）正确呈现。

## 2026-08-17 企业开通策略再次收敛：取消自动到期 / 超席位限制，仅保留手动禁用（后端）

### 背景

- 用户复核管理后台时确认：当前创建企业并没有有效期录入入口，也不希望再增加有效期限制。
- 当前真实产品策略应为：企业创建后，只要 `customerId` 能匹配到企业且企业未被手动停用，即允许集成使用；`seat_limit` 仅用于统计和运营记账，不应因为 API 维护、上报抖动或席位满额导致正常企业被自动限制。

### 根因

1. `oemEnterpriseLicenseService.js` 仍残留旧的 `trial -> expires_at -> EXPIRED` 受限链路。
2. 授权解析仍残留 `seat_limit -> FULL / blocked_full` 自动限制逻辑，与“席位仅统计”原则冲突。
3. 新建企业默认状态仍是 `trial`，与当前管理后台实际交付方式不一致。
4. demo 素材库访问仍残留 `app_key` / 到期拦截，和“只手动禁用”原则不一致。

### 改动

- `oemEnterpriseLicenseService.js`
  - 新建企业默认状态改为 `active`，历史 `trial` 统一按 `active` 兼容处理。
  - 取消 `expires_at` 运行时生效，运行时返回不再下发到期时间。
  - 删除 `FULL / EXPIRED / INVALID_APP_KEY` 运行时拦截分支，只保留：
    - 企业不存在：`NOT_OPENED`
    - 企业整体停用：`DISABLED`
    - 单席位手动停用：`DISABLED`
    - 其他已开通企业：`ACTIVE`
  - `seat_limit` 仅保留统计字段，不再触发自动重排、自动封禁或 `blocked_full` 受限。
  - demo 素材库访问不再因 `app_key` 或到期被拒，只保留企业存在与企业停用校验。

### 交付口径

- 对外实施口径统一为：
  - 客户拿到企业码 `customerId` 后即可集成；
  - `appKey` 当前仅保留协议字段与兼容用途，不再作为服务端硬拦截条件；
  - `channel` 仅用于渠道标识、缓存键、统计维度；
  - 企业限制只通过后台“停用企业”或“停用席位”进行人工控制。

### 回归关注

1. 已存在的 `trial` 企业不应再因为历史 `expires_at` 进入受限态。
2. `seat_limit` 小于当前已激活记录数时，企业实例仍应正常使用，只变化后台统计。
3. 手动停用企业、手动停用席位后，增值能力仍应受限。

## 2026-08-14 AI回复失败态治理：统一文案、状态码宿主回调、超时可配、重试从头重走、自动化回退手动授权

### 背景

- 用户提出 5 项整改：①失败文案统一为「这次生成没有成功 / 请点击下方按钮重新生成」，并支持宿主按状态码双写自定义（描述可选）；②状态码支持宿主回调模式回传；③断网 / 大模型请求超时支持宿主配置，默认 30s；④失败态点击语气或重新生成必须从头重走截屏+生成，而不是弹 toast；⑤自动化权限未开启时不得出现「生成没有成功」，应回退手动截屏授权页后继续流程。

### 根因

1. 旧失败分支把底层错误原文（含英文 host 错误、自动化权限细节）直接渲染到错误卡片，且无状态分类。
2. `OemAiReplyResult` 只有 `success/errorMessage`，宿主无法表达失败类别；SDK 等待宿主回写硬编码 90s、生成超时按模式写死 15s/150s。
3. 失败态点击语气/重新生成仍走 `refreshAiCommunicateRepliesFromExistingContext`，失败时上下文为空只会 toast。
4. `shouldPreferAutomationCapture` 在「监控开启 / 曾经就绪」时也返回 true，入口放行后截图失败，`automationPreferred && !hasProjectionGrant` 分支直接 showError（图1 的「当前自动化截图权限不可用」）。

### 改动

- `oem-ime-sdk-core/.../OemImeSdk.kt`：新增 `OemAiReplyErrorCode`（NETWORK/TIMEOUT/MODEL_BUSY/CONTENT_BLOCKED/RESULT_INVALID/CONFIG_MISSING/CAPTURE_FAILED/UNKNOWN）、`OemAiReplyErrorText(title, description)`；`OemAiReplyResult` 增加 `errorCode`；`OemSdkConfig` 与 Manifest key 新增 `aiReplyGenerationTimeoutMs` / `aiReplyNetworkTimeoutMs` / `aiReplyErrorTextCustomEnabled` / `aiReplyErrorTexts`；`OemImeSdk` 提供对应运行时 getter/setter（优先级：运行时 > Manifest > 默认）。
- `app/.../OemEnterpriseConfig.kt`：新增 `DEFAULT_AI_REPLY_TIMEOUT_MS = 30_000L` 与 `resolveAiReplyGenerationTimeoutMs / resolveAiReplyNetworkTimeoutMs / resolveAiReplyErrorTextCustomEnabled / resolveAiReplyErrorText(statusCode)`。
- `app/.../AiCommunicateReplyService.kt`：`UserVisibleFailure` 与宿主 `Failure` 携带 `errorCode`；各失败点分类落码（配置缺失/截图缺失/结果无效等）；新增 `classifyModelError` 将宿主或域名通道错误消息归类为 NETWORK/CONTENT_BLOCKED/MODEL_BUSY；宿主回调等待超时改用可配置的生成超时（默认 30s）；`ModelClient` 构造传入可配置网络超时。
- `app/.../ModelClient.kt`：连接/读/写超时改为构造参数 `networkTimeoutMs`（默认 60s，运行时取配置 30s）。
- `ai-ime/app/.../AiCommunicatePanelController.kt`（fcitx 二开模块）：`showError` 增加 `errorTitle/errorDescription` 参数，默认值常量 `这次生成没有成功 / 请点击下方按钮重新生成`；新增 `isShowingError` 与 `selectTone`；错误卡片标题/描述空值回落默认文案，原 message 仅留存不再渲染（避免泄漏内部信息）。
- `app/.../MaterialHubActivity.kt`：新增 `showAiReplyError(mode,tone,errorCode,rawMessage)` 统一失败展示（先查宿主自定义文案再回落默认）；`resolveAiReplyErrorCode(Throwable)` 异常分类；`restartAiCommunicateFromScratch` 失败态重走完整入口；语气/重新生成回调在失败态改为从头重走；生成超时解析改接 `resolveAiReplyGenerationTimeoutMs`（默认 30s）；入口与请求前的能力判断从 `hasAnyCaptureCapability`（含"曾经就绪"）收紧为 `hasAutomationCapture || hasUsableGrant`；删除「自动化权限不可用」showError 分支，无手动授权时统一回退 `showPermission` 授权页；粘贴/传图/主流程/刷新/截屏授权全部失败分支改为带状态码展示。

### 交互口径

- 失败卡片只展示标题+描述；宿主开启 `aiReplyErrorTextCustomEnabled` 后可按状态码下发 `title`（必填）与 `description`（可选），留空回落默认。
- 失败态点击语气/重新生成：丢弃上一轮上下文，重新截屏 → OCR/整理 → 大模型生成。
- 识屏回复：自动化就绪 → 直接截屏生成；自动化不可用 → 先手动截屏授权，授权后继续截屏与生成；任何情况下不再因自动化权限出现「生成没有成功」。

### 验证

- `OEM/android` 下 `gradlew :app:assembleDebug` 通过，`tankeIme_debug_nomodel.apk`（约 180MB）已 `adb install -r` 安装到真机（Success）。
- 设计图归档：`OEM/DesignWithTRAE/ai-reply-network-states/index.html`。
- 待真机回归：失败文案与重试重走、断网/超时（30s）表现、宿主自定义文案开关、自动化关闭时的手动授权回退。

## 2026-08-14（补充）失败态交互优化：粘贴模式 loading 反馈 + 结果/错误容器拉伸

### 背景

- 粘贴模式文本生成链路过短、返回过快，失败态点击语气或重新生成时错误卡无任何视觉变化，用户感知不到点击生效。要求：点击后文案区域先显示 loading 动效图标 1.5s，再恢复文本；仅粘贴模式需要（极速生成/识屏回复/传图回复有前置检查交互，不需要）。
- 四个模块的结果页/错误页容器高度只包裹文本，与设计原型不符。要求：容器拉伸至底部接近「重新生成」按钮，内容垂直居中。

### 改动

- `ai-ime/app/src/main/res/drawable/ic_ai_reply_loading.xml`：新增加载图标（设计稿 `_加载中-copy-copy.svg` 转换的 12 段青绿渐变环 VectorDrawable）。
- `ai-ime/app/.../AiCommunicatePanelController.kt`（fcitx 二开模块）：
  - `PanelState` 增加 `errorRetryLoading`；新增 `beginErrorRetryFeedback()`：错误卡切换为 loading 图标，`ERROR_RETRY_FEEDBACK_MS = 1500L` 后恢复文案，重复点击会重置计时。
  - `showError` 在已处于失败页时保留 loading 态，避免快返回打断反馈动效。
  - 错误卡 loading 态只渲染旋转图标：`ObjectAnimator` 0→360° 顺时针无限循环（LinearInterpolator，1s/圈）。
  - 结果页/错误页改拉伸布局：`updatePageLayoutMode` 对 RESULT/ERROR 开启 `fillViewport` 且 `contentContainer` MATCH_PARENT；回复列表卡与错误卡以 `weight=1` 占满剩余高度；内容不足时回复列表与错误文案垂直居中（gravity CENTER）。
- `app/.../MaterialHubActivity.kt`：`restartAiCommunicateFromScratch` 中粘贴模式先调用 `beginErrorRetryFeedback()` 再重走流程。

### 验证

- `:app:assembleDebug` 通过并 `adb install -r` 真机安装（Success）；待回归：粘贴失败态点击语气/重新生成的 1.5s 旋转反馈、四个模块结果/错误容器拉伸与垂直居中。

## 2026-08-14（补充2）截屏权限页容器拉伸 + 素材库编辑页保留添加菜单

### 背景

- 截屏权限说明页容器高度只包裹文本，与结果/错误页拉伸口径不一致；要求容器底部挨着「立即授权」按钮，且「仅用于本次读取聊天内容」提示条放到底部居中。
- 素材库编辑界面右下角缺少添加菜单，编辑时无法继续添加内容；要求保留右下角添加菜单。

### 改动

- `ai-ime/app/.../AiCommunicatePanelController.kt`（fcitx 二开模块）：
  - `updatePageLayoutMode` 拉伸白名单加入 `Page.PERMISSION`。
  - `renderPermissionPage` 信息卡以 `weight=1` 占满剩余高度，底部贴近「立即授权」。
  - `buildInfoCard` 新增 `tagAtBottom` 参数：正文与提示条之间插入弹性占位，提示条置底且文本水平居中（gravity CENTER）。
- `ai-ime/app/.../MaterialHubFavoriteController.kt`（fcitx 二开模块）：
  - `syncPrimaryActions` 去掉「编辑模式隐藏悬浮添加按钮」的限制：素材组预览/编辑页（非企业只读）右下角均保留添加菜单，点击仍走既有 quick-add 通道（文本编辑器/附件选择器），新增内容追加到当前编辑记录。
  - `persistQuickAddedParts` 快速添加后保持原编辑态（wasEditing），用户可连续添加、继续编辑，无需重新进入编辑。

### 验证

- `:app:assembleDebug` 通过并 `adb install -r` 真机安装（Success）；待回归：权限页容器拉伸与提示条置底居中、编辑页右下角添加菜单与连续添加体验。

## 2026-08-14（SDK 发布 1.1.0-alpha06 / host-bridge 1.2.0-alpha16）

- 发布范围：core 1.1.0-alpha06、host-bridge 1.2.0-alpha16（含本轮 AI 回复失败态交互重构与素材库编辑态添加菜单），其余模块按原版本全量重发（R8 release）
- 历史本地 TTS wrapper 当时只做了外部源码路径修正；该运行时已在后续版本中从 OEM 工程与交付链彻底移除
- 官网与 AI 一键装文档已同步刷新版本号；演示包暂缓重建，官网下载位维持 2026-08-05 构建

## 2026-08-15 制品完整性重发布（fcitx app 1.2 / host-bridge alpha17 / material·ai-reply alpha02）

### 背景

- 用户自测探客企业输入法（按官网 AI 一键装 MD 全新构建）反馈 5 个问题：AI回复错误卡样式/文案为旧版、极速生成截屏时霓虹边框被右移出屏幕、素材库首次授权后未直接进入相册选择器、素材详情页无下载按钮且仍有浮动添加按钮、素材编辑页无添加菜单——整体表现为「最近新优化的功能没有集成进来」。

### 根因（实锤）

- 五项功能在 OEM 源码中全部已实现，且已发布进 oem-sdk-repo 与 ECS；但制品发布时版本号从不变（app 恒 1.1、material/ai-reply 恒 alpha01），Gradle 视 release 制品为不可变、缓存后永不重下。
- 构建机全局 Gradle 缓存（GRADLE_USER_HOME 指向 scoop gradle 目录）存着 8/5~8/7 的旧 AAR（大小/md5 与 ECS 最新制品不一致），探客构建命中旧缓存，APK dex 缺少 8/14 新增文案标记。
- 对照证明：期间升过版本的 host-bridge alpha16 / core alpha06 在 8/15 被正确重新下载——升版本号是绕过缓存的唯一可靠手段（与 2026-08-05「同版本号重发布无效」结论一致，本轮是同一教训在 app/material/ai-reply 上的再次兑现）。

### 改动

- 版本升级：fcitx app 1.1→1.2（VERSION_NAME 同步）；host-bridge 1.2.0-alpha16→alpha17（api 坐标指向 app:1.2）；oem-ime-sdk-material、oem-ime-sdk-ai-reply 1.0.0-alpha01→alpha02（均依赖 app:1.2）；OEM 主工程 app 模块依赖同步 app:1.2。core 维持 1.1.0-alpha06 不变。
- 四模块重发布至 OEM/android/build/oem-sdk-repo；核验：material alpha02 与已验证含全部新标记的 alpha01 字节一致（md5 f65fcaffcd63e12ae518e279da2c99a7），app-1.2.aar 约 84.6MB。
- 文档坐标全量刷新：官网 index.html/app.js、AI 一键装 MD（根级与 downloads 副本，含抬头、版本自检门槛、斜杠下载 URL）、OEM/Doc 版本说明与发布清单、docs/Android OEM发布标准SOP、oem-sdk-catalog.json。
- 版本自检门槛升至 host-bridge 1.2.0-alpha17，拦截旧本地 MD 副本。

### 回归

- 探客工程依赖升级至新坐标并以 --refresh-dependencies 重建安装，dex 标记验证（新文案、截屏授权提示等）全部命中；真机复验由用户执行。
- 防回归约定：此后任何制品内容变更必须同步升版本号再发布，禁止同版本覆盖；发布后以「新坐标 + dex 标记」双重验证。

## 2026-08-15 统一版本号 1.0.0.20260815 与功能默认关闭基线（客户交付准备）

### 背景

- 进入客户交付阶段，此前 SDK / host-bridge / 各功能模块各自维护独立版本号（core 1.1.0-alpha06、host-bridge 1.2.0-alpha17、功能模块 1.0.0-alpha0x……），客户视角版本混乱；功能默认值也存在 catalog、SDK 代码回退、宿主解析三套口径不一致（有开有关）的问题。

### 改动

- 版本号统一（单一来源）：
  - `OEM/android/gradle.properties` 新增 `oemSdkVersion=1.0.0.20260815`，根 `build.gradle` 暴露 `ext.oemSdkVersion`；格式 `1.0.0.<构建号>`，构建号为日期戳（yyyyMMdd），每次发布 release 仅递增末位编号（同日多次发布追加序号，如 202608151）。
  - 统一版本读取覆盖全部对外交付模块；后续版本已进一步收口，历史本地 TTS 模块不再属于 OEM 标准发布链。
  - fcitx 内核（org.fcitx.fcitx5.android:app:1.2 及 lib 系 1.0）、stellar、paddleocr 等内部组件维持独立坐标，不随 SDK 发布版本变化。
  - 宿主工程坐标对齐：company、demo、tankeyunke 三个宿主 build.gradle 的 SDK 依赖统一为 `1.0.0.20260815`（company/demo 顺带修正 app:1.1→1.2、stellar-manager 1.0.19→1.0.20 的过期坐标）。
  - catalog（oem-sdk-catalog.json）、官网内联 catalog（website/oem/index.html，并同步 OEM/SDK/docs 与 OEM/website/oem 副本）、app.js 版本自检门槛（改为统一版本口径 1.0.0.20260815，SOP 抬头去掉 host-bridge 独立版本列）全部刷新。
- 功能默认策略（除「基础 + 输入法核心」外全部默认关闭）：
  - catalog 与官网模板：enableAi / enableAiReply / enableQaSession / enableMaterialLibrary / enableAutomation / enable_developer / enable_developer_entry 默认值 true→false；enableUi（基础能力）保持 true；新增 defaultPolicy 字段说明口径。
  - SDK 代码回退值（OemImeSdk.kt）：`OemFeatureToggles` 全部默认 false；`OemSdkConfig.enableAi` 默认 false、`enableUi` 默认 true；Manifest 解析回退同步；ENABLE_DEVELOPER 常量注释同步。
  - OEM 主工程宿主解析（OemEnterpriseConfig.kt）：enableAi / enableAiReply / enableMaterialLibrary / enableQaSession / enableDeveloperEntry 回退值 true→false，enableUi 保持 true。
  - host-bridge AAR Manifest 内嵌的 `enable_developer_entry` 默认值 true→false（避免污染未覆盖的宿主）。
  - OEM 主工程（企业全功能验证工程）Manifest 显式声明全量功能开启（enableUi/enableAi/enableAiReply/enableQaSession/enableMaterialLibrary/enableAutomation/enable_developer/enable_developer_entry），保证自验工程功能不受默认关闭影响。
- 文档同步：外部版-版本说明（版本策略重写 + 新增 1.0.0.20260815 条目）、外部版-SDK集成手册（交付目录树/依赖坐标/Manifest 模板注释 + 默认策略提示）、07-SDK实施与发布清单（坐标基线 + VersionTag 示例）、docs/Android/OEM发布标准SOP。

### 发布约定（后续执行）

- 每次发布 release：仅改 `gradle.properties` 的 `oemSdkVersion` 末位编号 → 全量重发布 8 个制品 → 执行 `export-sdk.ps1 -VersionTag <新版本>` → 同步官网/版本说明/清单文档。
- 企业集成默认只得到「基础 + 输入法核心」；开通某功能 = 宿主 Manifest 声明对应开关为 true + 集成对应模块制品。


## 2026-09-30 日光瓷白工具面板文字颜色（源码修复，不发布）

用户确认四端统一基线：工具面板图标下方文字与图标同色。修复 `StatusAreaEntryUi` 的标签颜色来源，正常态与图标同用 `keyTextColor`，激活态同用 `genericActiveForegroundColor`。不修改功能按键的白色文字、不改 SDK 版本、不构建或发布交付制品。源码核验通过：普通和激活状态的标签均复用图标内容色，不再引用 `altKeyTextColor`。主项目同位置 Kotlin 编译通过；OEM 未构建或发布制品。

## 2026-09-30 工具图标与面板入口收口（源码修复，不发布）

用户明确的设计基线：默认工具图标使用相同的细线风格；面板不显示重载配置、引擎方案菜单、货币标点切换、全半角及单字优先开关。保留专门的键盘选择、简繁切换及客户自定义入口。仅修复源码和默认资源，保持 SDK 版本与发布产物不变。

源码修复完成：面板移除重载配置；按稳定动作标识隐藏 Rime 方案菜单、全角模块、各方案的货币标点/全半角/单字优先开关。策略与主项目相同，不依赖翻译或开关状态；主项目新增过滤单测并通过编译。默认皮肤入口描边调整为 1.2dp，与已有表情等细线工具图标一致。OEM 仅做源码策略一致性核验，不构建、不改版本、不发布 SDK。

补充：Rime 表情候选开关的无图标动作映射为统一线性表情资源，避免用彩色 emoji 充当面板图标；保留原开关操作。主项目单测覆盖这一映射。
