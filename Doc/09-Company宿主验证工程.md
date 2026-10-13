# Company 宿主验证工程

> 最新 `1.0.0.202610111` 验证工程为 `quick-reply-validation-202610111`、`quick-reply-no-voice-202610111` 和官网 ZIP 独立缓存工程 `quick-reply-online-202610111`。仅通过交付 Maven 仓接入；客户模型配置文件、自绘内容/整块抽屉及原文重试证据见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。测试辅助 Activity 不进入 SDK 或官网 Demo。

> 20261008 历史验证基线：SDK 1.0.0.20261008、输入法运行时 app:1.9。新增独立 voice SDK，三种空白 Release/R8 宿主分别验证集成、未集成、JSON 关闭。新工程见 `OEM/company/voice-validation-20261008`、`voice-absent-20261008`、`voice-disabled-20261008`，仅通过导出 Maven 仓集成，无源码 composite。语音测试源码在 `OEM/tests/voice`；发布后另建 `voice-online-20261008`，只解压公开 HTTPS 下载的 ZIP，使用独立 Gradle 缓存构建。实际结果与线上复验见 `17-20261008离线语音与工具面板对齐.md`。以下旧工程状态为历史记录。

## 2026-09-09 当前正式演练

当前发布基线为 `1.0.0.20260921`，验证工程为 `OEM/company/enterprise-ai-202609091`。它在官网部署后新建，从线上下载 AI 一键安装 MD 和 22 个制品的 AAR/POM，再按 MD 的本地 Maven 镜像方法集成，没有引用 SDK 源码或替换正式 AAR。中文名为「泡泡企业输入法」，包名 `com.enpaopaokeyboard.ai`；Release/R8、独立发布签名、arm64-v8a。

仅开启 enableUi、enableAi、enableAiReply、enableMaterialLibrary；其他功能开关为 false。使用真实 HTTP License 连接器，真机返回 ACTIVE。中文拼音候选上屏、AI 面板、素材库文本预览与插入本地输入区通过；用户明确要求不处理云端模型，因此真实 AI 生成不在本次验收范围。正式制品的独立截图回归 10 项通过。详细失败分类、证据及发布验收见《14-202609091正式发布与企业集成验收.md》。

以下保留历史 `OEM/company/android` 工程记录，其模拟 License、默认功能及旧版本描述不代表本次正式演示包。

## 目标

`OEM/company/android` 用于模拟“外部客户 App 接入 OEM SDK”的真实方式，不直接引用 `OEM/android` 源码模块，而是通过本地 Maven 仓库消费已发布的 SDK 制品。

## 使用原则

- `OEM/company` 是测试目录，不是正式交付目录
- 每次需要重新验证第三方接入时，可以清空该目录后重新创建空白宿主 App
- 重建后的宿主应从 `OEM/SDK` 复制交付文件，再按照对外文档中的“集成方式”接入 SDK
- 不允许为了省事直接引用 `OEM/android` 源码模块绕过对外集成流程

## 工程位置

- 宿主工程：`OEM/company/android`
- 源码构建仓库：`OEM/android/build/oem-sdk-repo`
- 标准交付目录：`OEM/SDK`
- 工程内复制目录：`OEM/company/android/local-sdk`

## 当前接入方式

- 主桥接 SDK：`com.paopaoime.oem:oem-ime-host-bridge:1.0.0.202609081`
- 当前完整依赖基线（以 `OEM/SDK/catalog/oem-sdk-catalog.json` 为准，`com.paopaoime.oem` 组全部统一版本号）：
  - `com.paopaoime.oem:oem-ime-host-bridge:1.0.0.202609081`
  - `org.fcitx.fcitx5.android:app:1.7`
  - `com.paopaoime.oem:oem-ime-sdk-core:1.0.0.202609081`
  - `com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.202609081`
  - `com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.202609081`
  - `com.paopaoime.oem:oem-ime-sdk-material:1.0.0.202609081`
  - `com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.202609081`
  - `com.paopaoime.oem.internal.stellar:stellar-manager:1.1`
  - `com.paopaoime.oem.internal:paddleocr4android:1.0.0`
- 测试时优先将 `OEM/SDK` 中导出的交付物复制到宿主工程
- `OEM/company/android` 当前通过 `.\sync-sdk.ps1` 将最新交付 zip（`repo` 与 `docs`）同步到工程内 `local-sdk/`
- `OEM/company/android` 已改为直接消费 `oem-ime-host-bridge` AAR，不再以“点击按钮显式拉起桌面已安装 OEM 包”作为主验证链
- 2026-07-19 当前演练宿主中文名称为“成长星输入法”，并收敛为空白 Debug App，只保留 SDK 初始化、状态说明和输入法入口
- `ai-ime` 相关运行时已补齐本地 Maven 制品：`org.fcitx.fcitx5.android:app:1.7`、`org.fcitx.fcitx5.android.lib:{common,fcitx5,fcitx5-lua,libime,fcitx5-chinese-addons}:1.0`
- `paddleocr4android` 已补齐本地 Maven 制品：`com.paopaoime.oem.internal:paddleocr4android:1.0.0`
- `stellar` 相关运行时已补齐本地 Maven 制品（2026-08-24 起统一为 `1.1`）：`com.paopaoime.oem.internal.stellar:{stellar-aidl,stellar-shared,stellar-api,stellar-provider,stellar-shizuku-api,stellar-server}:1.1`、`com.paopaoime.oem.internal.stellar:stellar-manager:1.1`
- 抽屉/功能切片模块已纳入交付：`oem-ime-sdk-material`（素材库+AI回复抽屉基座与 DrawerKit 能力实现）、`oem-ime-sdk-ai-reply`（AI 回复生成管线）、`oem-ime-sdk-automation`、`oem-ime-sdk-developer`；宿主缺失 material/ai-reply 切片时，AI 回复、素材库与自定义抽屉整条链路不可用
- `OEM/company/android` 已移除全部 `includeBuild`，当前仅通过本地 Maven 仓库消费 OEM SDK 与内部运行时制品

## 运行时发布校验

- 只要改动了 `OEM/android/ai-ime/app` 下任何运行时 UI、抽屉、`MaterialHubFavoriteController`、`AiCommunicatePanelController` 或 `InputView/FcitxInputMethodService`，都不能只重发 `oem-ime-sdk-core / oem-ime-host-bridge`
- 上述场景必须同时在 `OEM/android` 执行 `:ai-ime:host-bridge:publishReleasePublicationToOemLocalRepository`，因为 `OEM/company/android` 运行时实际消费的是 `org.fcitx.fcitx5.android:app:1.7`
- 改动抽屉基座（`MaterialHubActivity`、`CustomDrawerContentHost`、`OemDrawerCapabilities`）或 AI 回复管线（`AiCommunicateReplyService` 等）时，必须重发 `:oem-ime-sdk-material` / `:oem-ime-sdk-ai-reply` 切片模块，否则宿主运行时出现 `NoSuchMethodError` 或抽屉整链路不可用
- 如果漏掉上述步骤，最典型的真机现象就是：编译通过，但抽屉启动时出现 `NoSuchMethodError`，原因是调用方已是新代码，而运行时 AAR 仍是旧版本
- 发布完成后必须先核对当前基线 `OEM/android/build/oem-sdk-repo/org/fcitx/fcitx5/android/app/1.7/app-1.7.aar` 已更新，再同步到 `OEM/company/android/local-sdk/repo`
- 增量发布后必须清理 `build/oem-sdk-repo` 中的历史版本目录，确保每个坐标只保留当前基线版本，再执行 `export-sdk.ps1 -Clean`
- 同步完成后必须再次核对当前基线 `OEM/company/android/local-sdk/repo/org/fcitx/fcitx5/android/app/1.7/app-1.7.aar` 的更新时间或文件哈希，确认宿主工程已吃到最新运行时，之后才允许构建和安装 `company` APK
- 2026-07-27 重新从零清理 SDK 后，额外确认 `org.fcitx.fcitx5.android.lib:{common,fcitx5,fcitx5-lua,libime,fcitx5-chinese-addons}:1.0` 必须从 `OEM/android/ai-ime` 嵌套工程补发，否则 company 会因 `org.fcitx.fcitx5.android:app` 的传递依赖缺失而构建失败

## 宿主行为

- 使用 `CompanyApplication` 调用 `OemImeSdk.initializeFromManifest(...)`
- 使用本地 `DemoLicenseProvider` 返回模拟 License
- `CompanyApplication` 会同时注入 `sessionArchiveListener`，保留宿主接收输入质检会话的日志链路
- `MainActivity` 现在只作为无界面启动中转页，桌面图标点击后会直接跳到 OEM 输入法设置页
- 演示包不再保留单独宿主页，首页体验统一收口到输入法设置页
- 宿主会读取 Manifest 中配置的目标 OEM 输入法设置页，并直接拉起该页面

## 当前限制

- `OEM/company/android` 当前已经可以完成“宿主包内集成输入法桥接层”的 release 构建，但还没有完成真机安装与启用/切换输入法链路复测
- 当前剩余工作已从“构建闭环”转向“真机安装与启用/切换输入法链路复测”
- 后续重点转为整理最终交付 SOP 与回归验证清单

## 宿主接收会话验证

- 术语约定：
  - `宿主 App`：集成 OEM SDK 的企业应用，当前示例为 `OEM/company/android`
  - `外部第三方 App`：真实发生输入行为的目标应用，例如微信、企业微信、浏览器等
- 当外部第三方 App 中发生真实输入后，宿主接收到的记录里：
  - `宿主应用` 应显示宿主包名
  - `目标包名 / 目标应用` 应显示真实发生输入的外部第三方 App
- 宿主 Manifest 当前默认配置：
  - `com.paopaoime.oem.enableQaSession=true`
  - `com.paopaoime.oem.sessionArchiveMode=DUAL_WRITE`
- 因此当前 `OEM/company` 会同时保留云端上传与宿主回调两条链路
- 真机验证时可直接观察两处结果：
- `adb logcat -s CompanyApplication`：若出现 `收到宿主会话回调` 日志，说明 SDK 已把会话成功回调给宿主
- 官网/API 演示页：若最近会话列表出现当前输入，说明云端链路正常
- 当你在宿主自身或其他目标 App 中完成真实输入后，可直接看日志和官网/API 页，不再依赖宿主页列表刷新

四种模式的对内验证口径：

- `CLOUD_ONLY`：只看官网/API 演示页是否出现最近会话
- `HOST_ONLY`：只看 `CompanyApplication` 日志；官网/API 演示页不应新增真实记录
- `DUAL_WRITE`：官网/API 演示页与 `CompanyApplication` 日志都应出现同一时段的新记录
- `LOCAL_ONLY`：官网/API 演示页不应新增真实记录，只允许本地排障使用

## 正确接入模型

- 第三方企业 App 不需要把 OEM 输入法宿主做成桌面主入口，而是应在自身任意业务页面放置一个入口，例如“输入法”
- 用户点击该入口后，应进入 OEM SDK 提供的统一网关：先检查输入法是否已启用、是否已切换为目标输入法，再决定后续跳转
- 若未启用或未切换：直接打开 OEM 输入法自己的 `SetupActivity`，由 OEM 引导页接管启用/切换流程
- 若已经切换完成：直接进入 OEM 输入法自己的 `OemMainActivity`
- 若企业宿主希望强门禁，可在 App 启动或进入特定业务页时先执行上述检查，未切换到目标输入法前不放行业务功能

## 当前实现

- `OEM/company/android` 已改造成“企业宿主示例”，首页包含：
  - `输入法` 入口按钮
  - 业务功能按钮（仅在目标输入法已切换时解锁）
  - 当前输入法安装/启用/切换状态摘要
- 宿主通过 Manifest 元数据配置目标 OEM 输入法：
  - `com.paopaoime.company.targetImeService`
  - `com.paopaoime.company.targetImeSetupActivity`
  - `com.paopaoime.company.targetImeCenterActivity`
- 宿主侧 `ImeHostGateway` 默认以当前 `context.packageName` 作为目标包名，开始面向“宿主自身就是输入法承载包”的集成方式
- 宿主 Manifest 已将 `com.paopaoime.oem.enableUi` 调整为 `true`，允许 `OemImeSdk.initializeFromManifest(...)` 正常申请 UI/IME 能力
- `CompanyApplication` 当前通过公开入口 `OemHostRuntime.initialize(...)` 补齐宿主初始化链
- 宿主 Manifest 已补齐自动化接入最小配置：
  - `com.paopaoime.oem.enableAutomation=true`
  - `com.paopaoime.oem.enableAutomationSettingsEntry=false`
  - `roro.stellar.permissions=stellar,follow_stellar_startup`
- `stellar` 发布链已支持宿主包名配置化，发布 SDK 前可在 `OEM/android/gradle.properties` 或 Gradle 命令行覆盖：

```properties
automationEngineApplicationId=com.yingxiaoime.enterprise
automationEngineDefaultAllowedPackages=com.yingxiaoime.enterprise,com.yingxiaoime.enterprise.debug
```

- 若以命令行临时覆盖发布，则可直接执行：

```powershell
.\gradlew.bat :stellar-aidl:publishReleasePublicationToOemLocalRepository :stellar-shared:publishReleasePublicationToOemLocalRepository :stellar-api:publishReleasePublicationToOemLocalRepository :stellar-provider:publishReleasePublicationToOemLocalRepository :stellar-shizuku-api:publishReleasePublicationToOemLocalRepository :stellar-server:publishReleasePublicationToOemLocalRepository :stellar-manager:publishReleasePublicationToOemLocalRepository -PautomationEngineApplicationId=com.yingxiaoime.enterprise -PautomationEngineDefaultAllowedPackages=com.yingxiaoime.enterprise,com.yingxiaoime.enterprise.debug --no-daemon
```

- 第三方宿主若切换为自己的 `applicationId`，必须同步替换上述两个值；否则 `stellar` 服务端仍会把旧包名当作 manager/provider 目标，出现“服务已启动但等待 Binder 响应超时”的假启动问题
- 2026-07-19 继续复测结果：
  - 以下设置页入口与开关联调结论基于临时开启 `com.paopaoime.oem.enableAutomationSettingsEntry=true`；当前正式交付基线已改为默认 `false`
  - 首页自动化设置开关优先走快速链路，必要时自动拉起 `Stellar Starter` 正式向导
  - `OEM/company` 宿主页“自动化设置”区域已直接显示“已检测到可用的 Stellar 自动化服务，探客输入法已获得授权”
  - 自动化开关已验证可从开启态切回关闭态，并恢复“未检测到可用的 Stellar 自动化服务”文案
  - 自动化开关已验证可再次从关闭态恢复到开启态，`stellar_server` 进程保持可见
  - 再次通过“自动化设置”10 连击隐藏入口进入内嵌 `Stellar`，确认第三方宿主的真实排障路径可用
  - 设备日志已捕获真实自动化链路：`MaterialHubActivity` 进入识屏回复流程后，`OCRPredictorNative` 完成 OCR，`requestAiCommunicateReplies ocr capture finished snapshot=true previewPath=true textLength=267`
  - 当前剩余阻塞已从“自动化服务不可用”转移为“宿主未下发可用 AI 模型配置”，日志明确提示：`当前未获取到可用 AI 模型配置，请检查宿主下发的模型设置`
- 2026-07-31 最新回归结果：
  - `OEM/android/third_party/stellar` 已同步到 `Stellar v1.0.19(100190)`，并保留 OEM 宿主包名配置化和默认授权包白名单
  - `oem-ime-host-bridge` 已同步依赖 `com.paopaoime.oem.internal.stellar:stellar-manager:1.0.19`
  - 已按 `automationEngineApplicationId=com.yingxiaoime.enterprise` 和 `automationEngineDefaultAllowedPackages=com.yingxiaoime.enterprise,com.yingxiaoime.enterprise.debug` 重新发布 Stellar 本地 Maven 制品
  - 已重点校验 Binder 相关 SOP：`--manager-package` 透传、`stellar.manager.applicationId` 写入、`StellarService` 内部 `stellar=FLAG_GRANTED` 自授权均保留
  - `OEM/android` 已执行 `:app:assembleDebug` 成功，Debug 产物为 `OEM/android/app/build/outputs/apk/debug/tankeIme_debug_nomodel.apk`
  - `OEM/SDK` 已导出 `oem-sdk-delivery-current.zip`，其中 `repo` 已包含 `stellar-manager/1.0.19`
- 2026-07-27 回归结果：
  - `OEM/android/third_party/stellar` 已同步到 `Stellar v1.0.18(100180)`，并保留 OEM 宿主包名配置化和默认授权包白名单
  - `stellar-server` 已补齐 consumer rules，Release/R8 后保留 `roro.stellar.server.bootstrap.ServerBootstrap`，并对 Android hidden API 缺失类补齐 `-dontwarn`
  - `OEM/company/android/app/build.gradle` 已同步依赖 `com.paopaoime.oem.internal.stellar:stellar-manager:1.0.18`
  - `OEM/company/android` 已执行 `sync-sdk.ps1`，消费最新 `OEM/SDK/repo`
  - `OEM/company/android` 已执行 `:app:assembleRelease` 成功，R8 阶段通过
  - Release 产物：`OEM/company/android/app/build/outputs/apk/release/app-release.apk`
  - 真机安装：`adb shell pm clear`、`adb shell pm uninstall --user 0` 后，`adb install --no-streaming -r app-release.apk` 成功，设备包路径已确认为 `com.yingxiaoime.enterprise`
  - 已执行 `adb shell pm grant com.yingxiaoime.enterprise android.permission.WRITE_SECURE_SETTINGS`
  - 已点击自动化设置 Switch，真机确认 `stellar_server` 进程存在
  - `logcat` 已确认 `Calling main entry roro.stellar.server.bootstrap.ServerBootstrap`，未再出现 `ClassNotFoundException: roro.stellar.server.bootstrap.ServerBootstrap`
  - 输入法设置页自动化摘要已显示“已检测到可用的输入法自动化引擎服务，演示输入法已获得授权”
- 2026-07-18 最新构建结果：
  - `:oem-ime-host-bridge:bundleDebugAar` 成功
  - `:oem-ime-host-bridge:publishReleasePublicationToOemLocalRepository` 成功
  - `:sherpa-onnx:publishReleasePublicationToOemLocalRepository` 成功
  - `:paddleocr4android:publishReleasePublicationToOemLocalRepository` 成功
  - `stellar` 关键模块发布成功：
    - `:stellar-aidl:publishReleasePublicationToOemLocalRepository`
    - `:stellar-shared:publishReleasePublicationToOemLocalRepository`
    - `:stellar-api:publishReleasePublicationToOemLocalRepository`
    - `:stellar-provider:publishReleasePublicationToOemLocalRepository`
    - `:stellar-shizuku-api:publishReleasePublicationToOemLocalRepository`
    - `:stellar-server:publishReleasePublicationToOemLocalRepository`
    - `:stellar-manager:publishReleasePublicationToOemLocalRepository`
  - `ai-ime` 关键模块发布成功：
    - `:lib:common:publishReleasePublicationToOemLocalRepository`
    - `:lib:fcitx5:publishReleasePublicationToOemLocalRepository`
    - `:lib:fcitx5-lua:publishReleasePublicationToOemLocalRepository`
    - `:lib:libime:publishReleasePublicationToOemLocalRepository`
    - `:lib:fcitx5-chinese-addons:publishReleasePublicationToOemLocalRepository`
    - `:host-bridge:publishReleasePublicationToOemLocalRepository`
  - `OEM/company/android` 已移除 `includeBuild('../../android/ai-ime')`
  - `OEM/company/android` 已移除 `includeBuild('../../android')`
  - `OEM/company/android` 执行 `:app:mergeReleaseNativeLibs` 成功
  - `OEM/company/android` 执行 `:app:packageRelease` 成功
  - Release 产物：`OEM/company/android/app/build/outputs/apk/release/app-release.apk`
  - 最新产物时间戳：`2026-07-18 22:41:22`
  - 最新包体大小：`236,499,058` 字节
  - 真机安装与切换验证：
    - `adb uninstall com.yingxiaoime.enterprise` 后重新安装 `app-release.apk` 成功
    - 系统已识别输入法服务：`com.yingxiaoime.enterprise/com.paopaoime.aikeyboard.ime.service.AiInputMethodService`
    - `adb shell ime enable ...` 成功，输入法已启用
    - `adb shell ime set ...` 成功，当前默认输入法已切换到宿主包内服务
    - `dumpsys input_method` 已确认 `mSelectedMethodId` 与 `mCurId` 均为 `com.yingxiaoime.enterprise/com.paopaoime.aikeyboard.ime.service.AiInputMethodService`
    - `adb shell am start -n com.yingxiaoime.enterprise/com.paopaoime.aikeyboard.ime.ui.setup.SetupActivity` 成功
    - `adb shell am start -n com.yingxiaoime.enterprise/com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity` 成功
    - `dumpsys activity` 已确认前台 `ResumedActivity` 为 `com.yingxiaoime.enterprise/com.paopaoime.aikeyboard.ime.ui.activity.OemMainActivity`
- 2026-07-22 最新回归结果：
  - `OEM/SDK/repo` 与 `OEM/company/android/local-sdk/repo` 已确认不再包含 `mnn-master-core`
  - `OEM/company/android` 的 Debug / Release 构建均已在无 `mnn-master-core` 依赖的交付仓库下通过
  - `CompanyApplication` 已接入 `sessionArchiveListener`
  - 真机日志已确认出现：`CompanySessionArchive: 收到宿主会话回调 count=1 ... policy=HOST_SELF_CHECK`
  - 说明“通过宿主 App 接收会话”的 SDK 回调链路已经打通；后续只需再用真实输入覆盖一条非自检事件即可完成业务层验证
- 2026-08-07 外部 AI 集成对话复盘优化（`c__Users_15811_Desk.md` 对话日志）：
  - SDK 级：drawer 包结果类 `OemAiReplyResult` 与 core 包宿主回调结果类重名导致通配符 import 歧义 → drawer 包更名为 `OemAiReplySuggestions`（语义=候选回复列表）；同步更新 [OemDrawerKit.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/oem-ime-sdk-core/src/main/java/com/paopaoime/aikeyboard/sdk/core/drawer/OemDrawerKit.kt)、[OemDrawerCapabilities.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/materialhub/OemDrawerCapabilities.kt)、company 示例 [CompanyDrawerKitSetup.kt](file:///c:/Users/15811/Desktop/paopao/OEM/company/android/app/src/main/java/com/paopaoime/company/CompanyDrawerKitSetup.kt)；core+host-bridge 重发、company 构建安装通过
  - 验证过 kotlin.Metadata 在 R8 后保留（lambda 推断问题为宿主代码风格，非 SDK 缺陷）
  - MD 级（`buildOneClickMd`）：§10 新增「公开入口类 → AAR 归属速查表」（重点标注 `OemHostRuntime` 在 host-bridge 不在 core）；§4 明确 keystore `storeFile` 相对路径以 app 模块目录为基准；常见失败新增 15（Gradle 同坐标缓存陈旧需删 files-2.1/transforms）与 16（Kotlin 回调用 object 显式实现、禁止双包通配符 import）；14.2 片段类名同步更名
  - 静态 MD 重新生成（970 行）并随 deploy 发布；线上字节级验证通过（速查表/Suggestions/transforms 均在线）；线上 core AAR 含 `OemAiReplySuggestions` 且旧 `drawer/OemAiReplyResult` 已移除
  - 新增长期工具 [scripts/verify-online.ps1](file:///c:/Users/15811/Desktop/paopao/scripts/verify-online.ps1)（线上 MD/AAR 内容抽检，中文用字节级匹配规避 PS5.1 GBK 误报）
- 2026-08-07 线上 sdk-repo 陈旧制品事故修复（第三方 AI 按官网 MD 直链下载构建出旧 UI 包）：
  - 现象：外部 AI 工具按官网一键 MD 的 ECS 直链（`https://paopaoime.com/oem/sdk-repo/...`）下载 AAR 集成，构建出的包仍是旧界面（工具面板出现「皮肤」按钮、「布局和显示」为旧灰色占位插图），且报告 DrawerKit API（`OemCustomDrawerSpec`/`OemDrawerContentFactory` 等）不存在
  - 根因：`scripts/deploy-oem-docs.ps1` 历史上只同步官网页面与 `downloads/` 下的 zip/apk，**从未同步 `sdk-repo` 模块直链目录**；ECS 上的 core AAR 为旧版（0 个 drawer 类），而本地 `OEM/SDK/repo` 的 core AAR 含 30 个 drawer 类、host-bridge AAR 含新插图（`img_number_26` 等 16 个 `img_*`）
  - 源码层核对结论：OEM 与主项目 `apps/android` 在「布局和显示」「按键效果」两页已完全对齐（布局 XML 逐行一致、8 个 `img_*` 插图同名同内容、KeyEffect Fragment 相同）；皮肤按钮 OEM 以 `oemDisabledToolbarItems` 运行时禁用集隐藏（主项目为编译期白名单 `filterNot { it == Skin }`），两工程工具栏/宫格/自定义页均不显示皮肤
  - 修复：`deploy-oem-docs.ps1` 新增 sdk-repo 全量同步步骤（先 `rm -rf` 远端旧目录再 `scp -r` 本地 `OEM/SDK/repo`，约 197MB/570 文件），远端校验加入 core AAR 存在性断言；已执行发布
  - 线上复核：`oem-ime-sdk-core-1.1.0-alpha03.aar` 含 30 个 drawer 类（含 `OemCustomDrawerSpec`）；`oem-ime-host-bridge-1.2.0-alpha13.aar` 含 `img_number_26.png` 新插图
  - 结论：SDK 与 MD 文档均完整，问题仅为线上直链制品陈旧；外部 AI 重新按官网 MD 拉取即可得到新 UI 与完整 DrawerKit API
- 2026-08-07 崩溃修复回归结果（真机日志 `ClassCastException: FrameLayout cannot be cast to ImageView`）：
  - 根因：`LayoutDisplaySettingsFragment` 将布局中的 `preview_layout_display_height`（实际为带圆角背景的 FrameLayout）按 `findViewById<ImageView>` 绑定，R8 release 下点击「输入法布局和显示」即崩溃
  - 修复：改按 `View` 绑定并动态调整高度（圆角背景在 FrameLayout 自身，内部 ImageView 随父容器自适应）；已排查全部 `findViewById<ImageView>` 无其他类型不匹配
  - 重发：`:oem-ime-host-bridge:publishReleasePublicationToOemLocalRepository`（1.2.0-alpha13 覆盖重发）→ `export-sdk.ps1 -Clean` → `sync-sdk.ps1 -Clean` → company release 重建安装
  - 真机验证：设置频道点击「布局和显示」进入布局显示页与「键盘高度调节」子页均无崩溃（uiautomator 点击复现原路径 + 用户手动确认）
  - 官网同步：一键 MD 生成器（`OEM/website/oem/app.js` 的 `buildOneClickMd`）新增 §14「DrawerKit 自定义 UI 开发（面向 AI 对话开发）」章节，含 AI 回复自定义 UI / 素材库自定义 UI / 空白自定义抽屉三个可复制代码片段与验收标准；静态 `downloads/oem-ai-one-click-install-latest.md` 已重新生成并随 `deploy-oem-docs.ps1` 发布至 `https://www.paopaoime.com/oem/`（MD 全文 17 章连续编号，线上已验证含 DrawerKit 章节）
- 2026-08-07 拼音9键无候选词 + 崩溃修复（真机日志 `fcitx-main SIGSEGV`，OnePlus Ace）：
  - 现象：切到拼音9键时键盘闪烁收回再唤起，点击只出数字、无中文候选词；主项目 `apps/android` 同版本9键正常
  - 根因：OEM `ai-ime/host-bridge/build.gradle.kts` 的 CMake `targets(...)` 漏了 `"rime"`，导致 `librime.so` 未编入；且 OEM 缺 `t9.schema.yaml`/`rime_ice.*`/`melt_eng.*`/`cn_dicts`/`en_dicts`/`lua` 等 rime-data 资产。激活 rime 时 dlopen 失败 → engine 为 null → `helper-types.h` 的 `InputMethodStatus` 对空 engine 解引用（`engine->subMode(...)`）→ fcitx-main SIGSEGV
  - 修复：host-bridge `targets(...)` 补 `"rime"`、`generateDataDescriptor` 补 opencc 符号链接；从主项目镜像全套 rime-data（含 `t9.schema.yaml`/`t9_phone.schema.yaml`）；`helper-types.h` 对 engine 加判空保护
  - 重发：`:host-bridge:publishReleasePublicationToOemLocalRepository`（org.fcitx.fcitx5.android:app:1.7）→ `:oem-ime-host-bridge` 重发 → export/sync → company release 重建安装
  - 真机验证：微信内切到拼音9键无崩溃无闪烁，点击 6/4 键出现中文候选词（你/密/米/迷/秘），logcat 无 SIGSEGV
- 2026-08-07 OEM 工具栏按钮被压扁修复（仅 OEM）：
  - 现象：定制工具栏固定 6 个按钮时左侧圆形按钮被挤成椭圆、间距不均
  - 根因：FlexboxLayout 默认 `flexShrink=1f`，6 按钮总宽超出可用宽度时左侧按钮被压缩（OVAL 背景宽高不等即变椭圆）
  - 修复：`InputMethodSettingsBridge.MAX_TOOLBAR_ITEMS` 6→5；`ImeToolbarCustomizer` 在 addFlexbox 与 styleToolbarButton 两处 LayoutParams 补 `flexShrink=0f`；strings 提示文案「最多固定 6 个」→「5 个」
  - 真机验证：默认工具栏 5 个自定义按钮均为等大圆形、间距均匀
- 2026-08-06 DrawerKit（抽屉开放层 R9）回归结果：
  - `OEM/company/android` 已接入 DrawerKit 宿主示例 `CompanyDrawerKitSetup`（`CompanyApplication.onCreate` 调用 `install`）：
    - L1：为 `ai_reply` / `material_hub` 注册自绘内容工厂（`OemImeSdk.setDrawerContentFactory`），演示能力字段调用（剪贴板/快速生成/截屏授权→屏幕理解/相册图片回复；素材查询/发送输入框/打开编辑页）
    - L2：注册自定义抽屉 `company_tools`（「企业工具箱」，自带矢量图标），入口自动进入键盘工具面板宫格末尾与顶部工具条，并受定制工具栏排序/显隐管理；抽屉内可通过 `OemDrawerController.openDrawer` 互跳内置抽屉
  - 当前交付基线已改为默认关闭两个 L1 开关与 L2 自定义抽屉：`com.paopaoime.oem.enableAiReplyCustomUi=false`、`com.paopaoime.oem.enableMaterialLibraryCustomUi=false`、`com.paopaoime.oem.enableCustomDrawers=false`；只有企业宿主显式声明后才开放对应抽屉入口
  - 宿主 `app/build.gradle` 依赖已切换到统一正式基线 `1.0.0.20260816`（host-bridge / core / ai / ai-reply / material / automation / developer）
  - 宿主 `proguard-rules.pro` 已补 `-dontwarn com.gemalto.jp2.JP2Decoder`（pdfbox 可选依赖）
  - 交付仓已重建为 24 AAR 干净基线，`oem-sdk-delivery-current.zip` 已同步导出
  - `:app:assembleRelease -x checkReleaseDuplicateClasses` 构建成功（R8 通过），Release 产物 `app-release.apk` 已真机安装
  - 真机验证：`adb shell ime enable/set com.yingxiaoime.enterprise/...AiInputMethodService` 成功并设为默认输入法；进程存活、无 FATAL 异常
  - 待人工复测项：键盘弹出后工具面板宫格末尾出现「企业工具箱」；工具条/定制工具栏出现自定义项并可排序隐藏；AI 回复/素材库抽屉为宿主自绘界面且能力字段可用；`setBackVisible` 返回按钮可配生效

## 已解决的外部集成问题

- 修正 `oem-ime-sdk-core / oem-ime-sdk-ai` 的 POM，使其不再引用 `unspecified` 内部依赖
- 将 `model_downloader` 与历史本地模型残留从 OEM 发布链彻底剥离，并确认 OEM 版本已不再发布任何本地模型运行时
- 给宿主工程补齐 `jitpack.io` 仓库，解析上游 fork 依赖
- 移除失效 `AndroidDeviceNames` 三方依赖，并把 `DeviceName.kt` shim 正式并入 OEM 发布链
- 为宿主工程补齐 `META-INF` 资源排除、`libc++_shared.so` 冲突处理和 `Manifest tools:replace`
- 从 `oem-ime-host-bridge` 中移除 `app/libs/*.aar` 直依赖，避免 Android Library 二次打包 AAR 时被本地 `.aar` 阻塞
- 将未被引用的 `OneKeyLoginManager.kt` 从桥接层排除，切断阿里号码认证本地 `.aar` 对 bridge AAR 化的阻塞
- 为宿主工程补齐 `libsherpa-onnx-c-api.so / libsherpa-onnx-cxx-api.so / libsherpa-onnx-jni.so` 的 JNI `pickFirsts`，解决 `mergeReleaseNativeLibs` 阶段的重复 so 冲突
- 已将 `oem-ime-host-bridge` 对 `sherpa-onnx / paddleocr4android` 的依赖改为稳定 Maven 坐标，避免 POM 中继续写出 `TankeImeKeyboardOEM:*:unspecified`
- 已将 `oem-ime-host-bridge` 对 `stellar-api / stellar-manager` 的依赖改为稳定 Maven 坐标，避免 POM 中继续写出 `TankeImeKeyboardOEM:*:unspecified`
- 已补 `OEM/android/settings.gradle` 对本地 `build/oem-sdk-repo` 的依赖解析，允许 OEM 主桥在重新发布时消费内部已发布制品
- 已在真机上确认 `com.yingxiaoime.enterprise` 既是宿主页承载包，也是系统当前选中的输入法服务承载包，不再依赖外部已安装的 OEM 主包
- 已修复 `stellar-server` 在外部宿主 Release/R8 后入口类被裁剪的问题，避免 `stellar_server` 启动后因 `ServerBootstrap` 找不到而无法落盘宿主授权

## 构建命令

### Debug

```powershell
$env:GRADLE_USER_HOME='c:\Users\15811\Desktop\paopao\OEM\company\android\.gradle-home'
.\gradlew.bat --no-daemon :app:assembleDebug
```

### Release

```powershell
$env:GRADLE_USER_HOME='c:\Users\15811\Desktop\paopao\OEM\company\android\.gradle-home'
.\gradlew.bat --no-daemon :app:assembleRelease
```

Release 构建前强制校验：

- 已执行并成功完成 `:ai-ime:host-bridge:publishReleasePublicationToOemLocalRepository`
- 已将最新交付 zip 同步到 `OEM/company/android/local-sdk/repo`（`.\sync-sdk.ps1 -Clean`）
- 已确认 `OEM/company/android/local-sdk/repo/org/fcitx/fcitx5/android/app/1.7/app-1.7.aar` 为本轮最新产物
- 抽屉相关代码改动时，已确认 `oem-ime-sdk-material` / `oem-ime-sdk-ai-reply` 切片 AAR 为本轮最新产物
- 只有以上各项都完成，`company` 的 Release 包才允许用于真机回归

## 当前产物

本次本地皮肤发布使用独立工程 `OEM/company/enterprise-skins-20260921`，统一 SDK 为 `1.0.0.20260921`，运行时为 `app:1.7`。先验证导出 AAR，再在发布后从官网获取 SDK/MD 重建；具体结果见 `15-20260921本地默认皮肤发布与集成验收.md`。

- Debug：`OEM/company/android/app/build/outputs/apk/debug/app-debug.apk`
- Release：`OEM/company/android/app/build/outputs/apk/release/app-release.apk`

## 签名说明

- 当前 keystore：`OEM/company/android/keystore/company-release.jks`
- 当前配置：`OEM/company/android/keystore/signing.properties`
- 模板文件：`OEM/company/android/keystore/signing.properties.template`

## 后续建议

- 后续如果要交给真正企业客户，将 `DemoLicenseProvider` 替换为正式在线 License Provider
- 如果客户有自己的公司证书，应替换当前 `company-release.jks`
- 下一步可在真实业务输入框场景下继续验证键盘弹出、候选栏、自动化能力与 License 门禁等完整产品体验




本轮 `1.0.0.20260921` 已于 2026-09-22 完成正式发布、31 文件 HTTPS 校验和发布后空白工程 Release/R8 实机复验；新工程为 `company/enterprise-skins-online-20260921`，详见 [15-20260921本地默认皮肤发布与集成验收](15-20260921本地默认皮肤发布与集成验收.md)。


## 20261010 双默认皮肤交互更新

日光瓷白与夜幕石墨统一键盘比例、候选高亮留白与文字、按键气泡、键盘类型图标和分页工具面板；工具栏与工具面板不提供皮肤入口。八个公开 SDK 重新制作并执行 Release/R8，输入法运行时升级至 app:1.10。
当前 SDK `1.0.0.20261010`，运行时 `app:1.10`。构建、部署及验收状态见 [本轮记录](18-20261010双默认皮肤交互对齐.md)。本轮不操作手机做界面测试，安装后由用户验收。

`1.0.0.20261010` 已于 2026-10-10 发布：双默认皮肤标准对齐、runtime app:1.10、八个公开 SDK Release/R8、官网 Debug APK、88 项 HTTPS 文件与公开 ZIP 校验、空缓存独立宿主及不含语音宿主 R8 通过。本轮按用户要求不操作手机测试。备份、哈希和逐项范围见 [18-20261010双默认皮肤交互对齐](18-20261010双默认皮肤交互对齐.md)。


## 20261011 剪贴板工具栏修复

复制文本不再自动替换工具栏，包含键盘显示中、重新唤起及旧设置为开启的场景；保留手动剪贴板、历史和粘贴。移除无效的工具栏剪贴板建议/超时设置。八个公开 SDK 全量重建并执行 Release/R8，输入法运行时升级至 app:1.11。 状态见 [本轮记录](19-20261011剪贴板不替换工具栏.md)。


## 202610111 临时剪贴板快捷回复

已同步同宿主一次性剪贴板与高情商快捷回复，复用 AI 回复客户配置并支持 DrawerKit 客户自绘结果。SDK `1.0.0.202610111`、runtime `app:1.12`；构建与发布状态见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。

## 20261012 两类宿主入口接管回归

本轮使用 `OEM/company/entry-validation-20261012`（完整）、`entry-lite-20261012`（无 material/ai-reply/voice）、`entry-online-20261012`（官网 ZIP、独立空依赖缓存），均仅从交付 ZIP 接入。测试源码在 `OEM/tests/custom-drawer-entry/`；外部复制宿主 `entry-copy-20261012` 仅使用平台 API，无网络权限。真机覆盖抽屉两桥接入口与真实宫格/工具条、剪贴板四种接管及默认回填、浅深色显示和粘贴。构建、发布后复验、设备限制及清理的最终状态以 [本轮记录](22-20261012自定义抽屉入口回调.md) 为准。
