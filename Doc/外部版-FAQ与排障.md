# 外部版 FAQ 与排障

## 1. 这是完整输入法 SDK 吗？

不是。当前第一阶段交付的是 `AI Runtime SDK`，重点覆盖模型、会话、设置跳转和相关能力封装，不直接交付完整 OEM 工程源码。

## 2. 为什么还会同时收到 demo-app？

demo-app 用于联调和验收，不是正式集成方式。正式接入应以 AAR 和公开 API 为准。

## 3. 初始化后没有看到模型，怎么排查？

优先检查：

- 宿主是否已在 `Application` 中执行 `AiKeyboardHostSdk.initialize(...)`
- 当前设备上是否存在已下载模型
- 宿主是否有读写模型目录所需的运行环境

## 4. 为什么某些高级能力不可用？

可能原因：

- 当前授权套餐未开通
- 当前交付包未包含对应可选模块
- 当前版本仍处于第一阶段，不包含完整输入法壳或自动化能力

## 5. 设置页拉起失败怎么办？

优先检查：

- 宿主是否正确集成了我方交付的依赖
- 交付版本是否与当前文档匹配
- 当前设备是否存在被裁剪掉的上游页面依赖

## 6. 如何确认当前接入的是正式版本？

请检查：

- AAR 坐标与版本号是否与交付清单一致
- `customerId`：填写我方交付的企业码
- `appKey`：当前与企业码同值，直接填写同一企业码
- `channel`：默认 `release`，无特殊约定保持默认值

## 7. 离线环境怎么接入？

如果客户需要离线 License 或内网 License Server，需要按单独私有化方案交付，具体以合同和交付清单为准。

## 8. 页面或文档与实际交付不一致怎么办？

以当前交付包附带的版本说明和合同为准，并及时反馈版本号、宿主包名和问题截图，以便同步修正文档和交付清单。

## 9. 自动化页提示“等待 Binder 响应”或“未检测到可用服务”怎么办？

优先检查：

- 宿主 Manifest 是否已声明 `com.paopaoime.oem.enableAutomation=true`
- 如果希望宿主页显示“自动化设置”，宿主 Manifest 是否已显式声明 `com.paopaoime.oem.enableAutomationSettingsEntry=true`
- 交付的输入法自动化引擎制品是否已按宿主自己的 `applicationId` 重发，尤其是 `automationEngineApplicationId` 和 `automationEngineDefaultAllowedPackages`
- 宿主 `Application` 是否已执行 `OemHostRuntime.initialize(this)` 并完成自动化初始化
- 设备侧是否已通过隐藏入口进入“输入法自动化引擎”，并完成无线 ADB / Root 服务启动

如果以上都正确，且已显式开启 `enableAutomationSettingsEntry=true`，重新进入输入法设置页后应能正常看到并打开“自动化设置”相关入口；默认基线为隐藏入口，不显示属于正常行为。

## 10. 自动化已经启动，但“极速生成”仍失败怎么办？

当前已验证的链路是：

- 输入法自动化引擎服务进程可启动
- 宿主可识别服务并获得授权
- 自动化截图 / OCR 链路可进入 `MaterialHubActivity`

如果仍提示生成失败，请继续检查：

- 宿主是否已下发可用 AI 模型配置
- 当前 License 是否开放了对应 AI 能力
- 设备本地是否存在所需模型或云侧参数

## 11. 会话存档宿主回调收不到、开发者面板留存为空怎么办？

按以下顺序排查：

- 分发模式是否为 `HOST_ONLY` 或 `DUAL_WRITE`：默认 `CLOUD_ONLY` 只上传云端，不回调宿主、也不写开发者留存。配置方式三选一：宿主 Manifest `com.paopaoime.oem.sessionArchiveMode`、`OemSdkConfig.sessionArchiveMode`、开发者面板模式切换（仅对切换后的新输入生效）。
- 历史版本中 `QaUploadWorker` 在 `apiBaseUrl` 未配置时会整批抛异常，导致 `HOST_ONLY`/`DUAL_WRITE` 也发不出去；当前版本已修复为懒解析：`HOST_ONLY` 不依赖域名，`DUAL_WRITE` 域名缺失时跳过云端、保留宿主回调与留存。请确认安装包为修复后版本。
- 宿主是否在 `OemImeSdk.initialize` 时传入非空 `sessionArchiveListener`（或事后 `setSessionArchiveListener`），且 `onSessionArchived` 返回 `true`；返回 false 视为投递失败，会话会保留重试。
- License 是否受限：当企业未开通、企业被管理员停用、或当前设备席位被管理员停用时，会强制降级 `LOCAL_ONLY`，既不分发也不留存。
- 采集有 1.5 秒防抖落库 + WorkManager 异步投递，非实时；输入后请等待数秒再查看。

## 12. 切换 sessionArchiveMode 后，之前的输入为什么没有留存？

会话的分发策略在采集落库时逐行冻结（`policyState`）。切换模式只对切换之后的新输入生效，库中已按 `CLOUD_ONLY`/`LOCAL_ONLY` 冻结的历史行不会补发宿主回调。验证时请"先切模式、再输入"。

## 13. 如何开启 SDK 集成调试日志排查自定义素材库 / 自定义抽屉等集成问题？

SDK 内置集成调试日志门面 `OemLog`，**默认关闭、零开销**。排查自定义 UI 不生效、抽屉唤起异常、素材发送失败、AI 回复自定义回调等问题时，可通过以下任一方式开启：

- 宿主 Manifest：`<meta-data android:name="com.paopaoime.oem.enableDebugLog" android:value="true" />`
- 初始化时 `OemSdkConfig.enableDebugLog = true`
- 运行时 `OemLog.setEnabled(true)` / `OemLog.setEnabled(false)`

开启后建议宿主通过 `OemLog.setListener(...)` 注册接收器，把回调接入宿主自己的日志系统或屏幕调试面板（回调线程即日志产生线程，需换线程请自行切换）；SDK 自身不落盘存储任何日志。开启后可看到抽屉唤起路由、自定义 UI 工厂挂载与回退、能力调用（`sendToInput` / `sendTextToInput` / `sendReplyToInput`）、企业远程素材同步等全链路日志，便于定位问题在宿主侧还是 SDK 侧。详见集成手册「集成调试日志管理（OemLog）」章节与 API 参考「OemLog」章节。

## 14. 企业微信等宿主上配置了自定义素材库模式，为什么仍显示内置素材库界面？

这是历史版本的行为差异：企业微信 / 闲鱼 / 抖音等部分宿主上，素材库默认呈现策略为「附着 IME 对话框」，该链路过去未读取自定义 UI 开关（`com.paopaoime.oem.enableMaterialLibraryCustomUi`），会直接弹出内置素材库对话框；而微信 / QQ / WhatsApp 等走「透明 overlay Activity」的宿主自定义 UI 正常生效。表现为"内置界面闪一下才出现自定义界面"或"自定义界面完全不出现"。

当前版本已修复为全链路尊重自定义开关：自定义 UI 开关生效、且已为素材库安装自定义内容工厂（`setDrawerContentFactory(OemDrawerKit.DRAWER_MATERIAL_HUB, factory)`）时，统一走 overlay Activity 链路，内置 UI 完全不挂载；若宿主自定义内容创建失败，则自动回退内置 UI 保证功能可用。排查时请依次确认：

- 安装包是否为当前修复后版本（对照交付清单版本号）
- 自定义 UI 开关是否生效（运行时覆盖 > Manifest > 默认 false），且内容工厂已安装
- 可开启集成调试日志（见上一条），查看路由日志 `material hub launch routed to overlay activity: custom UI factory installed` 确认链路走向；若出现 `custom drawer fallback to builtin: drawerId=material_hub missingToggle=ENABLE_MATERIAL_LIBRARY_CUSTOM_UI`，表示已注册工厂但未开启素材库自定义 UI 开关
