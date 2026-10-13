# SDK 分发与加固方案

## 先说结论

- 对厂家分发，推荐主交付形态为 `AAR`
- 但 `AAR != 安全`
- 真正有效的方案必须是 `AAR + R8 混淆 + Native 验签 + License 验证 + 服务端签名令牌`

## 当前版本记录

- 当前记录时间：`2026-07-27`
- 当前交付阶段：`SDK 化 + OEM/company 宿主验证闭环 + 自动化授权 SOP + 核心 AAR 轻保护已落地`
- 当前状态结论：`核心对外 AAR 已实际开启 R8 混淆与压缩，但整套 OEM 依赖链尚未系统化完成`
- 当前未完成项：`依赖 AAR 的统一混淆、精细化 consumer rules、Native 级 License/验签保护`
- 下一阶段目标：`补齐依赖 AAR 的混淆与 keep 策略，然后再推进 Native 化关键校验`

## 当前版本已交付能力

- 已形成标准交付目录 `OEM/SDK`，可输出 `repo + docs + zip` 的完整交付包
- 已形成 `OEM/company` 模拟第三方宿主的 fresh 验证链路，不再依赖直接引用 `OEM/android` 源码模块
- 已完成输入法基础能力、候选词、设置页与宿主页的闭环验证
- 已完成 `ADB自动化` 套餐在同包宿主模式下的服务启动、默认授权与宿主页状态联动修复
- 已完成对外 HTML 文档、对内 Markdown wiki 与下载交付物的一致化维护
- 已完成企业授权主链收口：基础输入保留、仅限制 `AI回复`/`素材库`/企业质检等增值能力
- 已完成 `OEM/company` 正式包验证链路，当前宿主侧不再依赖 composite build，改为仅消费本地 Maven 制品仓

## 为什么推荐 AAR

- 便于厂家以普通 Android Library 方式集成
- 便于拆分模块和控制 API 暴露面
- 便于走私有 Maven 仓库或制品库分发
- 比直接交源码更适合商业授权

## 不推荐的分发方式

- 不建议直接交 OEM 源码工程
- 不建议只发 jar，因为 Android 资源、Manifest、so、consumer rules 都不完整
- 不建议把全部能力塞进单一巨型 AAR，后续升级和权限边界会很难维护

## 推荐分发形态

- `core.aar`：初始化、授权、配置、日志、公共接口
- `ai.aar`：模型加载、会话管理、下载器
- `automation.aar`：自动化桥接，可选
- `ui.aar`：需要嵌入 OEM 页面时再提供
- `bom` 或版本清单：统一约束多个 AAR 版本

## 第一层保护：R8 混淆与压缩

- 核心对外 SDK 模块已开启 `release + minifyEnabled true`
- 已为核心对外 SDK 模块接入 `consumer-rules.pro`
- 当前仍处于“核心主链已落地、整套依赖链未系统完成”的状态
- 对 JNI、License、策略相关类的进一步收口仍在后续计划中

## 当前已实际落地的 R8 范围

- `oem-ime-host-bridge`：`release` 已开启 `minifyEnabled true`
- `oem-ime-sdk-core`：`release` 已开启 `minifyEnabled true`
- `aikeyboard-sdk-ai`：`release` 已开启 `minifyEnabled true`

当前含义：

- 对外主交付 AAR 已经不是“只规划未实施”
- 现在已经有实际发布到 `OEM/SDK/repo` 的混淆产物
- 但当前 protect 级别仍属于“轻保护基线”，不是最终加固态

## DrawerKit 开放层 keep 清单（R9）

DrawerKit 开放层的公开 API 面位于 `oem-ime-sdk-core`，公开接口、Spec、能力字段协议常量与 Controller 必须整体 keep，保证宿主侧 R8/ProGuard 不误删：

- `oem-ime-sdk-core` 的 `proguard-rules.pro` 与 `consumer-rules.pro` 追加以下 keep，并随 AAR 下发：

```proguard
-keep class com.paopaoime.aikeyboard.sdk.core.drawer.** { *; }
-keep interface com.paopaoime.aikeyboard.sdk.core.drawer.** { *; }
```

- keep 范围：DrawerKit 公开 API（`OemDrawerKit` 常量、`OemCustomDrawerSpec`、`OemDrawerContentFactory`、`OemDrawerController`、`OemRegisterResult`，以及 `registerCustomDrawer` / `unregisterCustomDrawer` / `updateCustomDrawer` / `setDrawerContentFactory` / `openDrawer` / `closeDrawer` / `aiReplyCapabilities` / `materialCapabilities` / `isAiReplyCustomUiEnabled` / `isMaterialLibraryCustomUiEnabled` 等挂载点；`OemImeSdk` 挂载点沿用其既有公开面 keep）
- 宿主自研代码（L1 内容工厂、L2 自定义抽屉实现）位于宿主 APK，天然不纳入 SDK-R8 加密输入；我方义务仅为保证开放层公开 API 面完整
- L1/L2 均不新增 license capability：自定义UI开关仅为功能配置项，能力字段调用仍受既有企业开关约束（事实状态），不引入新的鉴权点
- 验收口径：宿主开启 R8 后 DrawerKit API 全部可用（consumer-rules 生效）；SDK 产物不含宿主类型

## 当前已落地的轻保护基线

- 已把 `AI回复` 与 `素材库` 的拉起入口统一为 `MaterialHubLaunchRequest`
- 已把键盘栏与状态区的散落入口收口到 `ToolbarActionHostBridge -> MaterialHubHostBridge -> AiInputMethodService`
- 已给 `MaterialHubActivity` 增加一次性 IME 上下文令牌校验，阻止普通页面直接构造 Intent 拉起抽屉
- 已要求抽屉启动时必须带当前输入目标包名，避免离开输入法上下文后复用同一套页面入口
- 已修复 `ADB自动化` 同包宿主模式下的 manager 包名透传、服务启动与默认授权链
- 当前阶段暂不做 `stellar / ADB自动化` 的额外加壳或 Native 级保护，优先保护“基于输入法唤起抽屉”的交互方式
- 已把企业授权限制统一为“基础输入始终保留，增值能力按授权状态受限”
- 已把受限页收口为无标题单文案展示，避免 OEM 宿主侧继续混用旧口径

## 当前明确未完成的保护

- 依赖 AAR 仍未系统开启 `minifyEnabled true`，当前只覆盖了核心对外主链
- `consumer-rules.pro` 仍偏粗，尚未收敛到最小暴露面
- 尚未把 License 校验、宿主签名校验和功能开关判定整体下沉到 `so`
- 尚未引入专门的代码加密、壳保护或服务端风控联动

## 当前仍未完成混淆的重点依赖

- `paddleocr4android` 仍未开启系统化混淆
- `stellar-manager` 仍未开启系统化混淆
- 其他第三方/桥接依赖也还未做统一梳理与风险分级

结论：

- 如果问“R8 是否已经落地到核心 AAR”，答案是：`已落地`
- 如果问“整套 OEM SDK 是否已经完成系统化 AAR 保护”，答案是：`尚未完成`

## 自动化最易漏项

- `OEM/company` 只是模拟客户宿主，因此凡是客户侧必须声明的内容，都必须先在 `company` 里完整体现
- 宿主 `Application` 必须补齐 `AuthApiClient / Launcher / StellarApplication / MidsceneShizukuManager / AutomationAvailabilityMonitor` 初始化
- 宿主 `Manifest` 必须补齐 `com.paopaoime.oem.enableAutomation=true`
- 宿主 `Manifest` 必须补齐 `roro.stellar.permissions=stellar,follow_stellar_startup`
- 设备侧必须授予最终宿主包名 `WRITE_SECURE_SETTINGS`
- 宿主 `applicationId` 变化后，必须重新发布 `ADB自动化` 对应制品并重新导出 `OEM/SDK`

## 当前阶段不做的事情

- 不做整套交互逻辑 Native 化，避免主项目和 OEM 双线维护成本陡增
- 不引入商业加固壳，避免出包链复杂化和宿主兼容性回归
- 不为了保护而改动已有抽屉 UI 行为，当前只收口入口和直拉方式
- 不把“核心主链已启用 R8”误表述成“整套 OEM 依赖已经全部加固完成”

## 第二层保护：Native 化关键逻辑

- 把以下内容尽量收敛到 `so` 中：
- License 签名校验
- 宿主证书摘要校验
- 核心特性开关判定
- 反调试和环境完整性检测

## 第三层保护：License 令牌

- 由你方 License Server 使用私钥签发 License Token
- SDK 只内置公钥，不内置主密钥
- Token 至少包含：
- `customerId`
- `packageName`
- `certSha256`
- `expireAt`
- `featureFlags`
- `seatLimit`
- `licenseType`

## 第四层保护：运行时校验

- 启动时校验宿主包名
- 启动时校验宿主签名摘要
- 启动时校验 License 是否过期
- 关键能力执行前再次校验授权状态
- 检测 Root、Hook、模拟器、调试器时降低能力或拒绝执行

## 第五层保护：服务端联动

- 在线客户默认启用心跳校验
- 心跳上报建议只传脱敏后的安装标识和统计口径
- 服务端可动态下发：
- 授权状态
- 席位上限
- 功能开关
- 灰度名单

## 混淆策略建议

- 对外公开接口集中到单一包，例如 `com.tankeime.sdk.api`
- 其他实现全部下沉到 `internal` 包并混淆
- 避免把协议常量、错误码含义、授权字段名全部明文散落在 Java/Kotlin 层
- 对 License 解析逻辑避免直接使用简单 JSON 明文常量比对

## 重要现实判断

- Android 客户端没有“绝对防破解”
- 所以目标不是“无法破解”，而是把破解成本提高到高于商业收益
- 真正决定防护效果的是“离线校验 + 在线联动 + 合同约束 + 可追踪授权”四者组合

## 建议实施顺序

1. 先把 SDK 对外 API 收口
2. 再做 AAR 化和私有 Maven 分发
3. 再做 R8、consumer rules、资源裁剪
4. 再把 License 校验下沉到 JNI
5. 最后补在线 License Server 和风控策略
