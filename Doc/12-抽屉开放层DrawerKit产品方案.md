# 12-抽屉开放层 DrawerKit 产品方案（自定义UI交互 & 自定义抽屉）

| 项目 | 内容 |
|---|---|
| 文档状态 | 产品规划（待评审，未进入开发） |
| 适用工程 | 仅 OEM 企业工程（`OEM/android`），不复用泡泡输入法主项目 |
| 关联文档 | [01-SDK-集成与配置.md](./01-SDK-集成与配置.md)、[02-SDK-分发与加固方案.md](./02-SDK-分发与加固方案.md)、[外部版-SDK集成手册.md](./外部版-SDK集成手册.md)、[外部版-API参考.md](./外部版-API参考.md)、[11-OEM输入法模块升级记录.md](./11-OEM输入法模块升级记录.md) |
| 设计图 | `OEM/DesignWithTRAE/drawer-open-kit/index.html`（本地浏览器打开） |
| 规划版本 | R9（建议） |

---

## 1. 背景与问题分析

### 1.1 现状

OEM 输入法当前有两个从键盘唤起的抽屉：**AI回复** 与 **素材库（快捷发送）**。二者共用抽屉基座 [MaterialHubActivity](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/materialhub/MaterialHubActivity.kt)（透明 Activity + 底部 80% 屏高抽屉容器，含开合动画、下拉手势关闭、返回键分发），内容层分别为：

- AI回复：[AiCommunicatePanelController](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/materialhub/AiCommunicatePanelController.kt)（纯 Kotlin 代码构建 View，含 START/PERMISSION/PREPARING/GENERATING/RESULT/ERROR/RESTRICTED/IMAGE_PICKER 八个页面状态）；
- 素材库：[MaterialHubFavoriteController](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/materialhub/MaterialHubFavoriteController.kt) + [FavoriteUi](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/favorite/FavoriteUi.kt) + [FavoriteComposerOverlay](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/favorite/FavoriteComposerOverlay.kt)。

### 1.2 问题分析（为什么现在做不到）

| # | 问题 | 现状锚点 |
|---|---|---|
| P1 | UI 与能力耦合：功能字段直接写死在固定布局里，宿主无法重新排版 | 面板为硬编码 View 树，无布局 XML/Compose 可替换 |
| P2 | 抽屉「返回/关闭」不在基座层，而在各面板头部，无法作为统一基座交付 | backButton/closeButton 定义于 AiCommunicatePanelController 与 FavoriteUi 内部 |
| P3 | 工具项体系封闭：宫格页条目硬编码、工具条 `ToolbarItem` 为封闭枚举、图标仅支持 `@DrawableRes` | [StatusAreaWindow.buildEntries()](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/status/StatusAreaWindow.kt#L359-L435)、[InputMethodSettingsBridge.ToolbarItem](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/ime/ui/settings/InputMethodSettingsBridge.kt#L110-L124) |
| P4 | Manifest 模板缺少「自定义UI交互」开关 | [OemManifestMetaKeys](file:///c:/Users/15811/Desktop/paopao/OEM/android/oem-ime-sdk-core/src/main/java/com/paopaoime/aikeyboard/sdk/core/OemImeSdk.kt#L649-L701) 仅有功能勾选开关 |
| P5 | 无「宿主加载自定义页面」的公开接口 | 现有宿主接口仅授权/回调类（OemLicenseProvider、OemAiReplyHostDelegate 等），无 UI 承载类 |

### 1.3 目标与非目标

**目标**
1. 内置抽屉（AI回复/素材库）支持「自定义UI交互」开关：开启后抽屉内容层由宿主自绘，SDK 仅保留抽屉基座；
2. 将两个内置抽屉的功能字段抽离为「能力字段协议」，宿主可自由排版并调用底层能力；
3. 支持宿主注册最多 4 个自定义抽屉；但默认不暴露，只有显式开启 `enableCustomDrawers` 后入口才会进入键盘工具面板（宫格页）与顶部工具条，并受「定制工具栏」排序/显示/隐藏管理；
4. 以上能力**不做我方鉴权开通**，仅作为宿主自研的方便入口；宿主自研部分**不纳入 SDK-R8 加密范围**。

**非目标**
1. 不改变开关关闭时的任何现有体验（L0 完全等价现状）；
2. 不提供跨进程 UI 方案（宿主内容与输入法同进程，走 View 级承载，不引入 WebView 强依赖）；
3. 不开放键盘主键区/候选区的自定义（本期仅抽屉层）；
4. 不复用到泡泡输入法主项目。

---

## 2. 术语与能力分层

| 术语 | 定义 |
|---|---|
| 抽屉基座（DrawerBase） | SDK 提供的承载层：抽屉容器（80% 屏高、开合动画、下拉关闭手势、返回键分发）+ 标准头部（返回、标题槽、关闭）+ 生命周期回调 + 主动开合 API |
| 内容层（DrawerContent） | 抽屉头部以下的区域。L0 为 SDK 默认 UI；L1/L2 为宿主 View |
| 能力字段（Capability Field） | 从内置抽屉抽离出的最小功能单元（如「识屏回复」「语气切换」），每个字段有 id、语义、入参、回调、触发 API，与 UI 解耦 |
| 自定义UI交互开关 | Manifest meta 开关，决定内置抽屉内容层由谁提供 |
| 自定义抽屉（Custom Drawer） | 宿主通过注册 API 新建的抽屉，入口进入工具面板与工具条 |

**三级能力模型（DrawerKit）**

| 层级 | 名称 | 内容层归属 | 配置方式 | 鉴权 |
|---|---|---|---|---|
| L0 | 默认UI | SDK | 功能勾选开关（现状） | 随现状（企业开关/license 交集） |
| L1 | 内置抽屉自定义UI | 宿主 | 功能勾选 + 自定义UI开关 + 注册内容工厂 | **不做新增鉴权**（能力调用仍受既有企业开关约束） |
| L2 | 自定义抽屉 | 宿主 | `enableCustomDrawers` + 运行时注册 API | **无鉴权** |

---

## 3. 总体架构

```
┌──────────────────────── 宿主 App ────────────────────────┐
│  宿主自研代码（不纳入 SDK-R8）                              │
│  · L1 内容工厂（AI回复/素材库自定义UI）                      │
│  · L2 自定义抽屉 Spec + 内容工厂                           │
│  · 自有交互逻辑（倒计时/滑屏/自有模型等）                    │
└───────────────┬──────────────────────────┬───────────────┘
                │ 注册 API / 能力字段 API     │ Manifest meta
┌───────────────▼──────────────────────────▼───────────────┐
│              DrawerKit 开放层（oem-ime-sdk-core 公开面）    │
│  OemDrawerKit：registerCustomDrawer / setDrawerContent    │
│  / openDrawer / closeDrawer / 能力字段协议                 │
│  （公开 API 全部 keep，随 consumer-rules 下发）             │
├──────────────────────────────────────────────────────────┤
│  抽屉基座 MaterialHubActivity（容器+标准头部+手势+生命周期） │
├──────────────────────────┬───────────────────────────────┤
│  默认内容层（L0，现状UI）    │  宿主内容层（L1/L2）            │
├──────────────────────────┴───────────────────────────────┤
│  入口层：StatusAreaWindow 宫格页 / KawaiiBar 工具条          │
│  （定制工具栏排序/显隐体系，含动态项通道）                    │
└──────────────────────────────────────────────────────────┘
```

模块归属建议：
- 开放层公开 API（接口、Spec、字段协议常量）→ `oem-ime-sdk-core`（随 `OemImeSdk` 公开面，consumer-rules keep）；
- 基座改造（标准头部上移、内容层插槽、主动开合）→ 基座实现所在模块（`oem-ime-sdk-material`，MaterialHubActivity 声明于其 Manifest）；
- AI回复能力字段实现 → `oem-ime-sdk-ai-reply`；素材库能力字段实现 → `oem-ime-sdk-material`；
- 入口动态项通道 → 引擎侧 `ai-ime`（StatusAreaWindow/KawaiiBar）+ 宿主层 `ImeToolbarCustomizer`/`InputMethodSettingsBridge`。

---

## 4. 功能设计

### 4.1 L0 默认UI（保持不变）

开关关闭时，AI回复/素材库的页面、字段、交互、企业开关与受限态逻辑与当前版本完全一致，作为默认抽屉交互保留。

### 4.2 L1 内置抽屉「自定义UI交互」

#### 4.2.1 Manifest 开关设计

在集成所需 Manifest meta 模板中，对两个功能各新增一个开关，**默认关闭**：

```xml
<meta-data android:name="com.paopaoime.oem.enableAiReplyCustomUi" android:value="false" />
<meta-data android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi" android:value="false" />
<meta-data android:name="com.paopaoime.oem.enableCustomDrawers" android:value="false" />
```

命名沿用现有 `com.paopaoime.oem.enable*` 风格；解析进入 [OemManifestMetaKeys](file:///c:/Users/15811/Desktop/paopao/OEM/android/oem-ime-sdk-core/src/main/java/com/paopaoime/aikeyboard/sdk/core/OemImeSdk.kt#L649-L701) 与 `OemManifestConfig`，优先级沿用「运行时覆盖 > OemSdkConfig > Manifest > 默认值」。

**开关生效矩阵**

| 功能勾选开关 | 自定义UI开关 | 宿主注册内容工厂 | 行为 |
|---|---|---|---|
| 关 | 任意 | 任意 | 功能入口不出现（现状） |
| 开 | 关 | 任意 | L0 默认UI（现状） |
| 开 | 开 | 未注册 | **容错降级为 L0 默认UI**，输出 warn 日志 |
| 开 | 开 | 已注册 | L1：基座 + 宿主内容层 |
| 开 | 开 | 已注册但抛异常 | 捕获异常，降级 L0 默认UI，经回调通知宿主 |

#### 4.2.2 抽屉基座规范（SDK 保留部分）

| 基座职责 | 规范 |
|---|---|
| 容器 | 底部对齐、80% 屏高（沿用 `DRAWER_HEIGHT_RATIO`）、开 420ms/关 320ms 动画、下拉手势关闭（沿用现有阈值/速度判定） |
| 标准头部 | 返回按钮 + 标题槽 + 关闭按钮。**关闭按钮恒显**（体验兜底，宿主不可移除）；返回按钮显隐由宿主经 `controller.setBackVisible` 配置（默认显示）；标题文本可由宿主设置或隐藏 |
| 返回键分发 | 基座先询问内容层 `onBackPressed(): Boolean`，未消费再走基座返回/关闭（沿用现有分发语义） |
| 生命周期回调 | `onDrawerOpened / onDrawerClosed / onDrawerBack` |
| 主动开合 | 宿主可调用 `openDrawer(id)` / `closeDrawer(id)`，id 取 `ai_reply` / `material_hub` / 自定义 id |
| 主题适配 | 基座头部随键盘深色/浅色主题；内容层深浅色由宿主自理，SDK 提供当前主题查询 |
| 状态栏安全区 | 基座处理（沿用 statusBarSpacer） |

> 改造点：将现面板头部的返回/关闭「上移」为基座标准头部；L0 模式下由默认内容层按现有视觉渲染，保证视觉不变。

#### 4.2.3 AI回复 能力字段协议

字段与 UI 解耦后交付宿主。宿主可任意排版这些字段，并通过对应 API 触发底层能力。

| 字段 id | 名称 | 作用说明 | 关键入参 | 输出/回调 | 现实现锚点 |
|---|---|---|---|---|---|
| `ai_reply.entry.paste` | 点击复制生成回复 | 以「用户复制的会话文本」为上下文生成回复（粘贴会话模式） | 无 | 生成结果回调 | `GenerateMode.PASTE` |
| `ai_reply.entry.fast` | 极速生成 | 自动多屏识别当前聊天界面并生成回复，结果更精准 | 无 | 进度回调 + 结果回调 | `GenerateMode.FAST_LLM` |
| `ai_reply.entry.ocr` | 识屏回复 | OCR 理解屏幕内容生成回复 | 无 | 进度回调 + 结果回调 | `GenerateMode.OCR` |
| `ai_reply.entry.image` | 传图回复 | 上传多图理解会话生成回复 | 图片 uri 列表 | 进度回调 + 结果回调 | `GenerateMode.IMAGE_REPLY` |
| `ai_reply.permission.screen` | 截屏权限申请 | 请求截屏/录屏权限（识屏类字段前置） | 无 | 授权结果回调 | renderPermissionPage |
| `ai_reply.picker.album` | 相册选图 | 相册权限申请 + 多图选择 + 分页加载 | 无 | 选中 uri 列表 | renderImagePickerPage |
| `ai_reply.progress.timeline` | 生成进度 | 三步骤时间轴：检查运行环境/识别屏幕信息/生成回复建议 | 无 | 步骤状态流（TODO/ACTIVE/DONE） | buildTimelineProgressCard |
| `ai_reply.result.list` | 回复建议列表 | 多条回复候选，点选后填入宿主输入框 | 无 | `onReplySelected(text)` | buildReplyCard |
| `ai_reply.result.tone` | 语气切换 | 礼貌回复/自然沟通/推动成交（预设可由宿主配置） | tone id | 触发重新生成 | buildToneRow |
| `ai_reply.result.regenerate` | 重新生成 | 以相同模式重跑生成 | 无 | 进度 + 结果回调 | onRegenerate |
| `ai_reply.meta.footer` | 模型声明 | 「回复内容由当前宿主配置的 AI 模型生成」文案 | 无 | 文案字符串 | buildFooterNote |
| `ai_reply.state.restricted` | 受限态 | 企业侧限制时的事实状态（仅状态告知，非鉴权） | 无 | 布尔 | renderRestrictedPage |

#### 4.2.4 素材库 能力字段协议

| 字段 id | 名称 | 作用说明 | 关键入参 | 输出/回调 | 现实现锚点 |
|---|---|---|---|---|---|
| `material.tabs.category` | 分类页签 | 最近/文档/图片/视频 四类切换 | category | 列表数据流 | FavoriteCategory |
| `material.tabs.file_sub` | 文档子分类 | 全部/文字/演示/表格/PDF | sub | 列表数据流 | FavoriteFileCategory |
| `material.search` | 搜索 | 全库关键词搜索 | keyword | 列表数据流 | listSearchActionHost |
| `material.list` | 素材列表 | 分页素材数据，点选发送/预览 | 分页游标 | 素材项流 | FavoriteAdapter |
| `material.folders` | 预设文件夹 | 个人/学术/工作/其他 四文件夹浏览 | folder id | 文件夹内列表 | folderItems |
| `material.action.send` | 发送素材 | 将文本/图片/文件/视频送入宿主输入框 | 素材项 | 发送结果 | 列表点击 |
| `material.action.preview` | 附件预览 | 文档/图片/视频内联预览 | 素材项 | 预览视图 | showInlineAttachmentPreview |
| `material.fab.add` | 悬浮添加 | 发文本/传图片/传文件/传视频 四入口 | picker type | 进入创作器 | floatingAddButton |
| `material.composer` | 创作器 | 新建文本编辑、添加附件、保存草稿 | 类型 + 内容 | 保存结果 | FavoriteComposerOverlay |
| `material.remote.sync` | 远程素材同步 | 宿主注入远程素材源（沿用现有注入接口） | 无 | 同步事件 | OemRemoteMaterialService |
| `material.state.empty/login/restricted` | 状态位 | 空态/需登录/受限 三种事实状态 | 无 | 布尔 | 各 state view |

#### 4.2.5 宿主对接接口草案（L1）

```kotlin
interface OemDrawerContentFactory {
    fun onCreateContentView(context: Context, controller: OemDrawerController): View
    fun onDrawerStateChanged(state: OemDrawerState) {}
    fun onBackPressed(): Boolean = false
}

interface OemDrawerController {
    fun closeDrawer()
    fun openDrawer(drawerId: String)
    fun setTitle(title: CharSequence?)
    fun setBackVisible(visible: Boolean)
    fun currentTheme(): OemDrawerTheme
}

OemImeSdk.setDrawerContentFactory(drawerId: String, factory: OemDrawerContentFactory?)
OemImeSdk.aiReplyCapabilities(): OemAiReplyCapabilities
OemImeSdk.materialCapabilities(): OemMaterialCapabilities
OemImeSdk.isAiReplyCustomUiEnabled(): Boolean
OemImeSdk.isMaterialLibraryCustomUiEnabled(): Boolean
```

- `drawerId` 内置取 `ai_reply` / `material_hub`；传 `null` 工厂即恢复 L0；
- 能力接口即 4.2.3/4.2.4 字段的一一映射（如 `startFastGenerate(listener)`、`startScreenReply(listener)`、`queryMaterials(query, listener)`、`sendToInput(item)`）。

#### 4.2.6 客户示例场景映射（可行性验证）

客户设想：点键盘 AI回复按钮 → 自动弹起窗口显示倒计时 → 关闭抽屉去滑屏 → 截图发大模型 → 再拉起抽屉等待结果。映射：

| 步骤 | 承担方 | 使用能力 |
|---|---|---|
| 点击 AI回复按钮 | SDK 入口 | 现状入口不变，打开基座抽屉 |
| 显示倒计时 | 宿主内容层 | 自绘 UI |
| 关闭抽屉去滑屏 | 宿主 | `controller.closeDrawer()` + 宿主自有自动化 |
| 截图发模型 | 宿主 | 调 `ai_reply.entry.ocr/fast` 能力（SDK 截屏+模型），或宿主完全自有模型 |
| 再拉起抽屉显示结果 | 宿主 | `openDrawer("ai_reply")` + 结果回调渲染 / 宿主自绘结果 |

结论：基座只需预留「空白内容页 + 主动开合 + 能力字段」即可完整支撑该交互。

### 4.3 L2 自定义抽屉

#### 4.3.1 注册 API 与 Spec

自定义抽屉的图标、数量、内容均为动态资源，但入口暴露受 Manifest 开关控制；整体仍不走静态抽屉声明，采用运行时注册：

```kotlin
data class OemCustomDrawerSpec(
    val id: String,                 // 宿主内唯一，建议 "custom_<biz>"
    val title: String,              // 工具面板/工具条显示名
    val iconRes: Int = 0,           // 宿主侧 drawable 资源 id（与 icon 二选一）
    val icon: Drawable? = null,     // 宿主侧 Drawable/Bitmap
    val contentFactory: OemDrawerContentFactory
)

OemImeSdk.registerCustomDrawer(spec: OemCustomDrawerSpec): OemRegisterResult
OemImeSdk.unregisterCustomDrawer(id: String)
OemImeSdk.updateCustomDrawer(spec: OemCustomDrawerSpec)
```

注册时机：SDK 初始化之后、键盘首次展示之前（Application 或首屏）；运行中注册/注销触发入口热刷新。

#### 4.3.2 入口落位规则

| 入口 | 规则 |
|---|---|
| 工具面板（宫格页） | 自定义项**按注册顺序追加到内置项之后**（内置顺序保持现状），分页沿用 `chunked(8)` 自动翻页 |
| 顶部工具条 | 自定义项进入「定制工具栏」体系：出现在定制界面可选宫格中，**受排序/显示/隐藏管理**；持久化沿用 `bridge_toolbar_visible_items` / `bridge_toolbar_order`，自定义项 prefValue 取 `custom_<id>` |
| 工具条默认显隐 | 注册时若工具条未满 5 个（`MAX_TOOLBAR_ITEMS`），默认追加显示在末尾；若已满，进入隐藏池，用户可在定制工具栏中手动换入 |
| 注销清理 | `unregisterCustomDrawer` 同步清除两项 SP 中对应 prefValue，避免脏数据 |

#### 4.3.3 入口图标规范

| 项 | 规范 |
|---|---|
| 来源 | 宿主提供 `iconRes`（宿主包内资源）或 `icon`（Drawable/Bitmap），二选一 |
| 尺寸 | 工具条按 24dp 渲染、宫格按现有宫格图标尺寸渲染；建议宿主提供矢量图或 ≥96px 位图 |
| 着色 | 自定义图标**默认原色渲染、不参与内置 tint**（内置项保持现状 tint）；如宿主希望跟随主题，可提供 night 变体并经 `currentTheme()` 自适应 |
| 兜底 | 未提供图标时，宫格沿用现有「label 首字符」textIcon 兜底；工具条显示默认占位图标 |
| 数量上限 | 自定义抽屉最多 4 个，超出注册返回 `QUOTA_EXCEEDED` |

#### 4.3.4 承载与交互

- 点击入口 → 基座创建抽屉 → 调宿主 `contentFactory` 渲染内容层；
- 头部、手势、返回键、生命周期与 4.2.2 基座规范一致；
- 自定义抽屉**不经过 license 鉴权**，但受宿主显式开关 `enableCustomDrawers` 控制；默认关闭，仅在宿主明确开启后暴露入口；
- 宿主内容异常：基座捕获，关闭抽屉并回调宿主，不影响键盘主链路。

### 4.4 鉴权与 R8 边界

| 边界 | 结论 |
|---|---|
| 鉴权 | L1/L2 均**不新增** license capability；L1 的「能力字段调用」仍受既有企业开关约束（如企业关闭 AI回复则能力不可用，属事实状态而非新鉴权）；L2 不走 license，但默认受 `enableCustomDrawers=false` 收口 |
| R8 | 宿主自研代码位于宿主 APK，**天然不纳入** SDK-R8 输入；我方义务：DrawerKit 公开 API（接口/Spec/字段常量/Controller）加入 `oem-ime-sdk-core` 的 proguard keep 与 consumer-rules，保证宿主侧 R8 不误删；同步更新 [02-SDK-分发与加固方案.md](./02-SDK-分发与加固方案.md) 的「开放层 keep 清单」 |
| 加固披露 | 交付文档明确：自定义 UI/自定义抽屉为宿主自研责任域，其崩溃、合规、隐私由宿主负责 |

---

## 5. 标准产品流程

### 5.1 集成配置流程（L1）

```
集成模板勾选 AI回复/素材库
        │
        ▼
（可选）勾选「自定义UI交互」开关 → 写入 Manifest（默认 false）
        │
        ▼
OemImeSdk.initializeFromManifest(...)
        │
        ▼
宿主调用 setDrawerContentFactory("ai_reply"/"material_hub", factory)
        │
        ▼
用户点击入口：开关开且工厂已注册 → 基座+宿主内容；否则 → L0 默认UI
```

### 5.2 自定义抽屉接入流程（L2）

```
SDK 初始化成功
        │
        ▼
宿主 registerCustomDrawer(spec)（提供入口图标+标题+内容工厂）
        │
        ├──► 工具面板：追加到宫格末尾（自动分页）
        └──► 工具条：未满6个默认显示末尾 / 已满进隐藏池
        │
        ▼
（可选）用户在「定制工具栏」中排序/显示/隐藏（含自定义项）
        │
        ▼
点击入口 → 基座抽屉 → 宿主内容层
```

### 5.3 运行时交互流程（通用）

```
点击入口 → 基座创建(容器+标准头部) → onCreateContentView
   → 用户交互(宿主可调用能力字段 API / 主动 open/close)
   → 返回键: 内容层 onBackPressed 未消费 → 基座返回/关闭
   → 关闭: 关闭按钮 / 下拉手势 / closeDrawer → onDrawerClosed → 回收
```

### 5.4 降级与容错流程

| 场景 | 行为 |
|---|---|
| 开关开 + 未注册工厂 | 降级 L0，warn 日志 |
| 宿主 onCreateContentView 抛异常 | 捕获，降级 L0（内置抽屉）/ 关闭抽屉（自定义），回调 `onHostContentError` |
| 旧宿主 + 新 SDK | 无开关无注册，完全 L0，零影响 |
| 注销自定义抽屉 | 入口即时移除 + SP 清理 |

### 5.5 版本与升级兼容

- 新 key 未配置 = false，老宿主升级 SDK 行为不变；
- 工具条持久化兼容：`custom_<id>` 值对旧版本为未知值，旧版解析时按「未知即过滤」处理（需在实现时保证 resolve 逻辑对未知 prefValue 忽略而非崩溃）。

---

## 6. UI/交互规范要点（对照设计图）

详见 `OEM/DesignWithTRAE/drawer-open-kit/index.html`，要点：

1. L0 视觉与现状逐像素一致（头部改造后由默认内容层还原现状视觉）；
2. L1/L2 基座头部：完全复用现抽屉视觉（返回/关闭按钮位置不变），左返回（显隐由宿主经 `setBackVisible` 配置，默认显示）、中标题（可隐藏/可改文）、右关闭（恒显）；
3. 内容层空白基座页提供「虚线占位 + 说明文案」的开发期态（仅 debug 包显示），正式包为纯空白容器；
4. 工具面板自定义项视觉与内置宫格一致（图标+文案），自定义项角标「自研」仅 debug 态显示；
5. 定制工具栏界面：自定义项出现在下方可选宫格，拖拽排序与显隐逻辑与内置项完全一致。

---

## 7. 接口协议汇总（Kotlin 草案）

```kotlin
object OemDrawerKit {
    const val DRAWER_AI_REPLY = "ai_reply"
    const val DRAWER_MATERIAL_HUB = "material_hub"
    const val MAX_CUSTOM_DRAWERS = 4
}

interface OemDrawerContentFactory {
    fun onCreateContentView(context: Context, controller: OemDrawerController): View
    fun onCreateFullDrawerView(context: Context, controller: OemDrawerController): View? = null
    fun onDrawerStateChanged(state: OemDrawerState) {}
    fun onBackPressed(): Boolean = false
    fun onHostContentError(t: Throwable) {}
}

interface OemDrawerController {
    fun closeDrawer()
    fun openDrawer(drawerId: String)
    fun setTitle(title: CharSequence?)
    fun setBackVisible(visible: Boolean)
    fun currentTheme(): OemDrawerTheme
}

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
}


补充约定：`onCreateFullDrawerView` 返回非空时，宿主 View 占满抽屉 root，SDK 不创建默认顶栏、关闭按钮和加载提示；返回 `null` 时继续走 `onCreateContentView` 的旧版内容槽。`footerText` 返回 `null` / 空串时隐藏底部说明，`onFooterClick` 返回 `true` 表示点击已由宿主消费。

interface OemMaterialCapabilities {
    fun queryMaterials(q: OemMaterialQuery, l: OemMaterialListener)
    fun observeFolders(l: OemFolderListener)
    fun sendToInput(item: OemMaterialItem): Boolean
    fun previewView(item: OemMaterialItem): View?
    fun openComposer(type: OemPickerType)
    fun stateFlags(): OemMaterialStateFlags
}

data class OemCustomDrawerSpec(
    val id: String,
    val title: String,
    val iconRes: Int = 0,
    val icon: Drawable? = null,
    val contentFactory: OemDrawerContentFactory
)
```

挂载点：`OemImeSdk.setDrawerContentFactory / registerCustomDrawer / unregisterCustomDrawer / updateCustomDrawer / openDrawer / closeDrawer / aiReplyCapabilities / materialCapabilities / isAiReplyCustomUiEnabled / isMaterialLibraryCustomUiEnabled`。

---

## 8. 文档与交付更新计划（开发阶段执行）

| 文档 | 更新内容 |
|---|---|
| [外部版-SDK集成手册.md](./外部版-SDK集成手册.md) | 步骤四模板新增两个自定义UI开关；新增「DrawerKit 自定义UI与自定义抽屉」章节 |
| [外部版-API参考.md](./外部版-API参考.md) | 新增 DrawerKit API 章节（接口、Spec、字段协议表、图标规范） |
| [外部版-版本说明.md](./外部版-版本说明.md) | R9 版本条目 |
| [01-SDK-集成与配置.md](./01-SDK-集成与配置.md) | Manifest key 清单补充 |
| [02-SDK-分发与加固方案.md](./02-SDK-分发与加固方案.md) | 开放层 keep 清单、R8 边界说明 |
| [11-OEM输入法模块升级记录.md](./11-OEM输入法模块升级记录.md) | R9 开发完成后补记录 |
| `OEM/SDK/docs/` 同步副本 | 随导出脚本同步 |
| Company/demo 验证工程 | 增加 L1/L2 示例实现（含客户倒计时示例） |

---

## 9. 验收标准与测试矩阵

| 维度 | 用例要点 |
|---|---|
| L0 回归 | 开关缺省/关闭时，AI回复全页面状态机、素材库全功能与现版本一致 |
| L1 开关 | 矩阵 5 行行为符合 4.2.1；manifest 优先级与运行时覆盖正确 |
| L1 基座 | 头部返回/关闭/标题槽、下拉手势、返回键分发、主动开合、主题适配 |
| L1 字段 | 12+11 个能力字段 API 独立可用、回调正确；客户倒计时示例端到端跑通 |
| L2 注册 | 注册/注销/更新热刷新；宫格追加顺序与分页；工具条默认显隐与 6 上限；定制工具栏排序/显隐/持久化/注销清理 |
| L2 图标 | iconRes/icon 二选一、原色渲染、textIcon 兜底、4 个上限 |
| 容错 | 未注册降级、抛异常降级、旧宿主兼容、未知 prefValue 忽略 |
| 加固 | 宿主开 R8 后 DrawerKit API 可用（consumer-rules 生效）；SDK 产物不含宿主类型 |
| 鉴权 | L1/L2 全链路无 license 请求产生 |

---

## 10. 风险、边界与待确认项

| # | 项 | 建议/待确认 |
|---|---|---|
| R1 | 工具条 6 上限是否放宽 | 已确认：维持 6 个上限（自定义项占配额，已满进隐藏池），保持工具条克制 |
| R2 | 自定义图标是否支持跟随主题 tint | 已确认：默认原色渲染、不跟随内置 tint；宿主可自备 night 变体并经 `currentTheme()` 自适应 |
| R3 | L1 头部视觉与现面板头部差异 | 已确认：基座头部完全复用现抽屉视觉（返回/关闭按钮位置不变），L0 无感；返回按钮显隐由宿主经 `setBackVisible` 配置（默认显示） |
| R4 | 自定义抽屉数量上限 | 已确认：4 个（超出注册返回 `QUOTA_EXCEEDED`） |
| R5 | 宿主内容层性能（View 树过大） | 文档提示宿主内容层视图数建议 ≤200，基座不做强制 |
| R6 | 闲鱼宿主 Dialog 备用路径 | L1/L2 首期仅覆盖 Activity 基座路径，Dialog 路径后续评估 |

---

## 11. 里程碑建议（R9）

| 阶段 | 内容 |
|---|---|
| R9-P0 | 基座标准头部上移 + L1 开关 + AI回复字段协议 + 客户示例验证 |
| R9-P1 | 素材库字段协议 + L2 自定义抽屉 + 入口落位（宫格/工具条/定制工具栏） |
| R9-P2 | 外部文档更新 + Company/demo 示例 + 加固 keep 清单 + 验收 |

---

## 附：现实现锚点速查（开发参考）

| 关注点 | 文件 |
|---|---|
| 抽屉基座 | [MaterialHubActivity.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/materialhub/MaterialHubActivity.kt) |
| AI回复内容层 | [AiCommunicatePanelController.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/materialhub/AiCommunicatePanelController.kt) |
| 素材库内容层 | [FavoriteUi.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/favorite/FavoriteUi.kt) / [MaterialHubFavoriteController.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/materialhub/MaterialHubFavoriteController.kt) |
| Manifest key | [OemImeSdk.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/oem-ime-sdk-core/src/main/java/com/paopaoime/aikeyboard/sdk/core/OemImeSdk.kt) |
| 宫格页 | [StatusAreaWindow.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/status/StatusAreaWindow.kt) / [StatusAreaEntry.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/status/StatusAreaEntry.kt) |
| 工具条定制 | [InputMethodSettingsBridge.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/ime/ui/settings/InputMethodSettingsBridge.kt) / [ImeToolbarCustomizer.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/ime/service/ImeToolbarCustomizer.kt) / [CustomizeToolbarSettingsFragment.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/ime/ui/fragment/CustomizeToolbarSettingsFragment.kt) |
| 入口触发链 | [ToolbarActionHostBridge.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/ai-ime/app/src/main/java/org/fcitx/fcitx5/android/input/bar/ToolbarActionHostBridge.kt) / [AiInputMethodService.kt](file:///c:/Users/15811/Desktop/paopao/OEM/android/app/src/main/java/com/paopaoime/aikeyboard/ime/service/AiInputMethodService.kt) |

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
