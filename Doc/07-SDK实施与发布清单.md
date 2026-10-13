# SDK 实施与发布清单

## 目标

本清单用于约束 OEM 企业分支源码每次更新后的标准出包流程，确保：

- 能重新输出客户可集成的一组 `AAR`
- 能重新生成统一的本地 Maven 仓库目录
- 能用 `OEM/company` 重新模拟第三方空白宿主接入
- 能同步更新对内 wiki 与对外集成文档

## 当前授权口径

- 正式企业按总席位管理，不再按批次时间管理
- 试用企业默认 10 席、30 天
- SDK 运行时仅在 `超席位`、`席位被手工停用`、`试用到期`、`企业停用` 时限制增值功能
- 弱网、超时、安装后短期检测不到默认放行，连续 7 天无法成功校验后才判定为异常席位

## 标准输出物

标准交付目录：

- `OEM/SDK`

源码构建中间目录：

- `OEM/android/build/oem-sdk-repo`

其中：

- `OEM/android/build/oem-sdk-repo` 用于 Gradle 发布中间产物
- `OEM/SDK` 用于标准交付归档、压缩包输出、`OEM/company` 复制测试

最终交付目录内部不是单个 `AAR`，而是一整套可交付的本地 Maven 仓库，内部包含：

- `*.aar`
- `*.pom`
- `*.module`
- `*-sources.jar`
- `maven-metadata.xml`
- 对客户交付时，推荐直接交付 `OEM/SDK` 导出的压缩包，而不是手工挑出零散 `AAR`。

## 当前标准交付模块

### 对外主 SDK

> 自 `1.0.0.20260816` 起采用统一版本号：`com.paopaoime.oem` 组下全部制品版本一致，版本唯一来源为 `OEM/android/gradle.properties` 的 `oemSdkVersion`，每次发布仅递增末位编号。当前构建基线：`1.0.0.20261012`；线上版本以发布验收记录为准。

- `com.paopaoime.oem:oem-ime-sdk-core:1.0.0.20261012`（必选：注册表/门禁/授权桥）
- `com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20261012`（必选：输入法内核与资源）
- `com.paopaoime.oem:oem-ime-sdk-ai:1.0.0.20261012`（AI 运行时）

### 对外可选功能模块（2026-08-04 新增）

- `com.paopaoime.oem:oem-ime-sdk-ai-reply:1.0.0.20261012`（AI回复，依赖 core；自动化截屏可选）
- `com.paopaoime.oem:oem-ime-sdk-material:1.0.0.20261012`（素材库，依赖 core + ai-reply）
- `com.paopaoime.oem:oem-ime-sdk-automation:1.0.0.20261012`（Stellar 自动化，可选增强）
- `com.paopaoime.oem:oem-ime-sdk-developer:1.0.0.20261012`（开发者工具，可选）

模块勾选口径见 `OEM/SDK/catalog/oem-sdk-catalog.json`，未集成自动化模块时 AI回复降级为手动截图并提示「当前未集成自动化模块」。功能默认策略：基础输入法必选；语音模块按需集成，集成后默认开启；其余功能默认关闭，企业按开通范围显式开启。

## 当前版本基线

- 2026-10-12 用户授权同版本重制 UI、修复键盘调节及按键默认值，回归测试按用户要求跳过；构建、发布与核对范围见 [同版本更新记录](21-20261012快捷回复皮肤面板与键盘调节.md)。本次同版本例外不改变后续默认升版 SOP。

- 当前基线时间：`2026-10-12`
- 历史累计已交付（不是本次变更清单）：`标准 SDK 目录导出`、`外部HTML + Markdown 文档`、`demo_sop_validation fresh 宿主验证`、`OCR 低内存防护与 ocrEnabled 总开关（20260920）`、`AI 回复生成 Started/Finished 生命周期回调`、`SDK 内置 AI 回复霓虹边框/后台接管层默认关闭与运行时开关`、`抽屉整块替换宿主 View`、`AI 回复底部文案与点击回调可配`、`自定义抽屉开关缺失 warn 日志`、`输入法候选词修复`、`ADB自动化同包授权修复`、`Stellar v1.0.19 自动化套餐升级`、`SDK 模块化拆分（5 大模块）`、`feature 模块 R8 加固`、`OEM/demo 双包大小比对（full 234.7MB / lite 202.2MB）`、`滑屏事件宿主回调双写（swipeDispatchMode，core 1.1.0-alpha04）`、`媒体预览页下载与添加按钮屏蔽`、`抽屉下滑关闭手势移除（host-bridge 1.2.0-alpha14）`、`素材库关键词搜索与文本分类（搜索页防抖过滤/直发/状态恢复，core 1.1.0-alpha05 / host-bridge 1.2.0-alpha15）`、`AI回复失败态交互重构：错误状态码与宿主自定义文案双写、超时可配、重试从头重走链路、粘贴模式重试loading、结果/错误/权限容器拉伸、素材库编辑态保留添加菜单（core 1.1.0-alpha06 / host-bridge 1.2.0-alpha16）`、`制品完整性重发布：fcitx app 1.2 / host-bridge 1.2.0-alpha17 / material·ai-reply 1.0.0-alpha02（升版绕过 Gradle 旧缓存，全部近期功能随新坐标生效）`、`首个正式 release 1.0.0.20260815：版本号单一来源统一、功能默认关闭、两阶段发布 23 AAR、官网 catalog 实时加载与发布后清零收尾 SOP 落地`
- 当前已落地保护：`MaterialHub` 抽屉统一入口、一次性 IME 令牌校验、`MaterialHubActivity` 直拉拦截、`host-bridge/feature AAR` R8 minify（跨模块类名保真）
- 当前未落地保护：`关键 AAR 加密`
- 下一阶段动作：按 catalog 勾选口径在官网侧提供模块化下载，并用 `OEM/demo` 持续回归 lite/full 集成差异

### 历史离线语音与皮肤工具栏：1.0.0.20261008

- 可选 oem-ime-sdk-voice（三档离线语音及宿主配置），集成后默认开启；输入法运行时 app:1.9，内部 sherpa-onnx:1.13.8 纳入交付。
- 完整从零重建、八个公开模块 Release/R8、官网 Debug 演示包、独立 R8 企业宿主与上线后重拉验证。状态以 17-20261008 离线语音验收记录为准。

### 历史 Android 17 兼容预适配：1.0.0.20260923

- 演示宿主默认拒绝明文流量；SDK Manifest 不再强制放开宿主明文策略；移除 15 处旧调试 HTTP 上报。
- 七个公开 SDK 升至 `1.0.0.20260923`，输入法运行时升至 `app:1.8`，保持 targetSdk 36。三阶段发布已构建，线上状态见单独验收记录。

### 上次发布：1.0.0.20260921

- 默认浅深色皮肤替换为离线内置的日光瓷白与夜幕石墨；运行时升到 `app:1.7`。
- 保留 OEM 授权门禁、企业抽屉与自定义皮肤；补齐皮肤切片、字形与候选工具栏渲染。
- 按三阶段从零构建、Release/R8、正式部署和发布后空白企业宿主集成执行。
- 本轮状态与验证证据见 15-20260921本地默认皮肤发布与集成验收.md，仅已记录通过的项目视为验收完成。

### 对外交付时会随仓库一起携带的运行时

- `说明：已移除历史本地大模型与 TTS 运行时；本版恢复独立 sherpa-onnx 离线 ASR 运行时，模型权重按需下载。`
- `com.paopaoime.oem.internal:sherpa-onnx:1.13.8`
- `com.paopaoime.oem.internal:paddleocr4android:1.0.0`
- `org.fcitx.fcitx5.android:app:1.13`
- `org.fcitx.fcitx5.android.lib:{common,fcitx5,fcitx5-lua,libime,fcitx5-chinese-addons}:1.0`

### 如果本次交付包含自动化套餐，还要一起输出

> 自 `1.0.0.20260824` 起，stellar 套餐坐标由 `1.0.0 / 1.0.20` 统一升级为 `1.1`（对齐上游 v1.1(101000)），`oem-ime-sdk-automation` POM 传递依赖随之指向 `1.1`。

- `com.paopaoime.oem.internal.stellar:stellar-aidl:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-shared:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-api:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-provider:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-server:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-shizuku-api:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-shizuku-aidl:1.1`（随仓交付，宿主禁止显式依赖）
- `com.paopaoime.oem.internal.stellar:stellar-userservice:1.1`
- `com.paopaoime.oem.internal.stellar:stellar-manager:1.1`

本轮 `2026-07-31` 自动化套餐基线：

- `Stellar` 上游已升级到 `v1.0.19(100190)`，并保留 v1.0.18 已并入的被跳转快速授权、隐藏后台、无线调试快捷开启和开机预热模式
- 已并入 v1.0.19 的一次性跟随启动命令能力，包括 `FOLLOW_SERVICE_ONCE`、执行次数、成功/失败统计和数据库字段迁移
- OEM 默认自动化宿主配置保持为 `automationEngineApplicationId=com.yingxiaoime.enterprise`，默认授权白名单为 `com.yingxiaoime.enterprise,com.yingxiaoime.enterprise.debug`
- 已重点校验 Binder SOP：`Starter`、native `libstellar.so`、`ServerBootstrap`、`StellarService` 均保留 `--manager-package` 透传、`stellar.manager.applicationId` 写入和宿主 `stellar=FLAG_GRANTED` 内部自授权
- 已按 OEM SOP 重新发布本地 Maven 自动化引擎制品，`stellar-manager-1.0.19.aar` 已进入 `OEM/android/build/oem-sdk-repo`
- 已执行 `OEM/SDK/export-sdk.ps1 -Clean:$false -VersionTag current` 导出 `oem-sdk-delivery-current.zip`

本轮 `2026-07-27` 自动化套餐基线：

- `Stellar` 上游已升级到 `v1.0.18(100180)`
- 已保留官方新增的被跳转快速授权、隐藏后台、无线调试快捷开启、开机预热模式和命令保存桌面图标底层能力
- `我的 -> 自动化设置` 已修正为“条目点击只计数隐藏入口，开关只响应 Switch 组件本身”，避免点击条目时误开/误关自动化
- `OEM/company/android` 已从最新 `OEM/SDK` 重新同步、清缓存、构建 Release，并通过 `adb install --no-streaming -r` 安装到设备
- `stellar-server` AAR 已携带 consumer rules，必须保留 `roro.stellar.server.**` 并对 Android hidden API 补齐 `-dontwarn`，防止外部宿主 Release/R8 后 `app_process` 找不到 `ServerBootstrap`

## 标准出包流程

### 步骤一：确认版本与文档

- 确认 `aikeyboard-sdk-ai / oem-ime-sdk-core / oem-ime-host-bridge` 的版本号是否已更新
- 确认本次是否包含自动化套餐
- 确认对外集成文档是否需要同步改版
- 确认宿主是否已提供 `app/src/main/res/values/oem_ime_config.xml`，并显式配置 `oem_ime_display_name`
- 如果目标环境包含中文，必须同时提供 `app/src/main/res/values-zh/oem_ime_config.xml`，避免中文资源回退到 OEM 默认名称

### 步骤二：清理旧产物

在 `OEM/android` 下清理旧的输出仓库与临时缓存，确保本次产物可追溯：

```powershell
Remove-Item -Recurse -Force .\build\oem-sdk-repo -ErrorAction SilentlyContinue
```

同时确认 OEM 版本未残留以下已移除目录：

```powershell
Remove-Item -Recurse -Force .\mnn-master-core -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\third_party\google-ai-edge -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\model-downloader-wrapper -ErrorAction SilentlyContinue
```

同时清理标准交付目录中的旧内容：

```powershell
Remove-Item -Recurse -Force ..\SDK\repo -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force ..\SDK\docs -ErrorAction SilentlyContinue
Remove-Item -Force ..\SDK\oem-sdk-delivery-*.zip -ErrorAction SilentlyContinue
```

### 步骤三：先重建并发布内部运行时

从零发布分为三个独立 Gradle 调用阶段：先发布 fcitx host-bridge 和五个 lib；再发布 sherpa-onnx、paddleocr4android 和全部九个 stellar 模块；最后发布八个公开 SDK。先 clean 各阶段的模块输出，再执行以下命令，逐次核对退出码。首次构建必须联网；只有确认全部依赖已缓存后才可加 `--offline`。禁止把内部发布与依赖它们的公开模块放在同一次 Gradle 调用中，配置阶段就可能解析尚未存在的 AAR。

在 `OEM/android/ai-ime` 下执行第一阶段：

```powershell
$env:GRADLE_USER_HOME='c:\Users\15811\Desktop\paopao\OEM\android\.gradle-user'
$env:JAVA_HOME='C:\Java\Java21\jdk-21' # 替换为本机已安装路径
$runtime = @('host-bridge','lib:common','lib:fcitx5','lib:fcitx5-lua','lib:libime','lib:fcitx5-chinese-addons')
& ..\gradlew.bat @($runtime | ForEach-Object { ':' + $_ + ':publishReleasePublicationToOemLocalRepository' }) --no-daemon --no-build-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Fcitx 发布失败，停止后续阶段' }
```

强制规则：

- 只要改动了 `OEM/android/ai-ime/app` 下任何运行时 UI、抽屉、`MaterialHubFavoriteController`、`AiCommunicatePanelController` 或 `InputView/FcitxInputMethodService`，必须同时发布 `:ai-ime:host-bridge:publishReleasePublicationToOemLocalRepository`
- 只发布 `oem-ime-sdk-core / oem-ime-host-bridge` 不足以让 `OEM/company/android` 吃到 `org.fcitx.fcitx5.android:app:1.13` 的最新实现
- 发布完成后必须核对 `OEM/android/build/oem-sdk-repo/org/fcitx/fcitx5/android/app/1.13/app-1.13.aar` 的更新时间，再同步到独立宿主的 `local-sdk/repo`
- OEM 当前标准保留运行时为：`paddleocr4android / stellar-*`
- `sherpa-onnx` 从本版恢复为必要内部依赖：固定官方 1.13.8 静态 ONNX Runtime AAR，第二阶段同时发布 `:sherpa-onnx:publishReleasePublicationToOemLocalRepository`；必须保留 JNI consumer rules，不打包语音模型权重
- `mnn-master-core / model-downloader-wrapper / third_party/google-ai-edge` 不再属于 OEM 标准交付面

### 步骤四：依次发布其他内部运行时和公开 SDK

```powershell
Set-Location .. # 回到 OEM/android
$internal = @('sherpa-onnx','paddleocr4android','stellar-aidl','stellar-shared','stellar-api','stellar-provider','stellar-shizuku-api','stellar-shizuku-aidl','stellar-userservice','stellar-server','stellar-manager')
& .\gradlew.bat @($internal | ForEach-Object { ':' + $_ + ':publishReleasePublicationToOemLocalRepository' }) --no-daemon --no-build-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw '内部运行时发布失败，停止后续阶段' }
$public = @('oem-ime-sdk-core','aikeyboard-sdk-ai','oem-ime-host-bridge','oem-ime-sdk-ai-reply','oem-ime-sdk-material','oem-ime-sdk-automation','oem-ime-sdk-developer','oem-ime-sdk-voice')
& .\gradlew.bat @($public | ForEach-Object { ':' + $_ + ':publishReleasePublicationToOemLocalRepository' }) --no-daemon --no-build-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw '公开 SDK 发布失败' }
```

注意：第一阶段已发布以下五个 Fcitx lib。核对制品存在；仅在缺失时回到嵌套工程补发，随后重做下游验证：

```powershell
cd OEM\android\ai-ime
$env:GRADLE_USER_HOME='c:\Users\15811\Desktop\paopao\OEM\android\.gradle-user'
..\gradlew.bat `
  :lib:common:publishReleasePublicationToOemLocalRepository `
  :lib:fcitx5:publishReleasePublicationToOemLocalRepository `
  :lib:fcitx5-lua:publishReleasePublicationToOemLocalRepository `
  :lib:libime:publishReleasePublicationToOemLocalRepository `
  :lib:fcitx5-chinese-addons:publishReleasePublicationToOemLocalRepository `
  --no-daemon
```

如果自动化套餐需要绑定新的宿主包名，则必须同时覆盖：

```powershell
-PautomationEngineApplicationId=com.example.host `
-PautomationEngineDefaultAllowedPackages=com.example.host,com.example.host.debug
```

### 步骤五：导出到标准 SDK 目录

在 `OEM/SDK` 下执行：

```powershell
.\export-sdk.ps1 -VersionTag 1.0.0.20261008
```

macOS/Linux 使用同等的全模块导出脚本：`python3 OEM/SDK/export-sdk.py --version-tag current`，正式归档再以当前统一版本号作为 `--version-tag` 导出。导出脚本会检查 catalog、Gradle 版本及每个 AAR/POM/module，再复制 Maven 仓库和对外文档并生成清单与 ZIP。

导出后将生成：

- `OEM/SDK/repo`
- `OEM/SDK/docs`
- `OEM/SDK/DELIVERY_README.txt`
- `OEM/SDK/oem-sdk-delivery-current.zip`（当前交付版本 `1.0.0.20261008`；版本归档放官网 downloads，不在 SDK 根目录堆积）

### 步骤六：检查输出结果

至少核对以下路径已生成 `aar / pom / module`：

- `OEM/SDK/repo/com/paopaoime/oem/oem-ime-sdk-core`
- `OEM/SDK/repo/com/paopaoime/oem/oem-ime-host-bridge`
- `OEM/SDK/repo/com/paopaoime/oem/oem-ime-sdk-ai`

如果包含自动化套餐，再核对：

- `OEM/SDK/repo/com/paopaoime/oem/internal/stellar`
- `OEM/SDK/repo/com/paopaoime/oem/internal/stellar/stellar-server/1.1/stellar-server-1.1.aar` 内必须包含 `proguard.txt`
- `stellar-server-1.1.aar` 的 `proguard.txt` 必须包含 `-keep class roro.stellar.server.** { *; }` 和 Android hidden API `-dontwarn`

同时核对本轮交互保护基线没有被回退：

- `AI回复 / 素材库` 抽屉必须通过统一的 `MaterialHubLaunchRequest` 拉起
- `MaterialHubActivity` 必须校验 IME 下发的一次性启动令牌，禁止普通页面直接构造 Intent 进入
- `KawaiiBar / StatusArea` 不应再直接各自拼装抽屉页面 Intent
- 如果本次包含自动化套餐，必须额外确认宿主包名相关配置没有回退：
  - `automationEngineApplicationId`
  - `automationEngineDefaultAllowedPackages`
- 必须额外确认输入法显示名配置没有回退：
  - 宿主已提供 `oem_ime_display_name`
  - 中文环境下宿主已提供 `values-zh/oem_ime_config.xml`
  - `company` 演示包合并资源后，`paopao_app_name / ime_app_name / input_method_settings_page_title` 都已指向 `oem_ime_display_name`
  - `SetupActivity / SetupPage` 等启用引导页文案也统一读取 `oem_ime_display_name`

### 步骤七：用 OEM/company 重新模拟第三方空白宿主

`OEM/company` 是测试目录，不是正式交付目录。每次验证都允许清空后重建，用于模拟“客户新建空白 App 再按对外文档接入 SDK”的真实过程。

执行原则：

- 可以清空 `OEM/company` 旧内容
- 重新创建空白 Android App
- 从 `OEM/SDK` 复制交付文件到测试工程，再按对外文档中的“集成方式”接入 SDK
- `OEM/company/android` 可直接执行 `.\sync-sdk.ps1`，把 `OEM/SDK/repo` 与 `OEM/SDK/docs` 复制到工程内 `local-sdk/`
- 禁止直接引用 `OEM/android` 源码模块绕过验证
- `OEM/SDK` 根目录只允许保留 1 份最新正确交付 zip，不在工作目录长期堆积历史版本

自动化套餐的宿主最小检查表：

- `Application` 已调用公开入口 `OemHostRuntime.initialize(...)`
- `Manifest` 已补齐 `com.paopaoime.oem.enableAutomation=true`
- `Manifest` 已补齐 `roro.stellar.permissions=stellar,follow_stellar_startup`
- 最终安装包已按当前宿主 `applicationId` 重新发布 `stellar`
- 设备已向最终宿主包名授予 `WRITE_SECURE_SETTINGS`
- 点击自动化开关后必须确认 `stellar_server` 进程存在，不能只看安装和系统权限授予结果
- `adb logcat` 中必须出现 `Calling main entry roro.stellar.server.bootstrap.ServerBootstrap`，且不得出现 `ClassNotFoundException: roro.stellar.server.bootstrap.ServerBootstrap`
- 输入法设置页自动化摘要必须显示已检测到可用自动化引擎服务，并确认演示输入法已获得授权
- 若进入内嵌 Stellar 的“授权应用”页面，当前宿主包名必须为允许状态，不能停留在询问或服务未运行
- 宿主已提供 `app/src/main/res/values/oem_ime_config.xml`
- 宿主已提供 `app/src/main/res/values-zh/oem_ime_config.xml`
- 系统“语言和输入法”列表中显示的输入法名称已符合当前宿主品牌
- 系统“更改键盘”弹窗与宿主启用页按钮文案已符合当前宿主品牌

### 步骤八：更新文档

- **仅在 SDK 版本号递增且准备正式上线发布时**，才在《OEM SDK 集成说明文档》的“修改记录”新增该版本一行：填写发布日期、文档版本、实际 SDK 版本和本次变更要点；保留旧行，不得用旧版本（例如 OCR）的说明覆盖当前版本。同步更新 `PAOPAOIME/scripts/gen-oem-sdk-doc-pdf.py` 的 `build_revision()`、`OEM/Doc/外部版-SDK集成手册.md` 的修改记录，重新生成官网 latest 与版本号 PDF，再重新导出 SDK ZIP。日常开发、未发布的代码或文档修改不新增版本记录。
- 出包前检查 PDF“修改记录”第一页、压缩包内文档和官网最新 PDF：首行版本号须等于 catalog.sdkVersion，描述须对应本次实际改动，历史版本日期和内容不得被改写为当前版本。
- 对内更新：
  - `01-SDK-集成与配置`
  - `07-SDK实施与发布清单`
  - `09-Company宿主验证工程`
- 对外更新：
  - `OEM/Doc/外部版-SDK集成手册.md`
  - `OEM/Doc/外部版-自动化授权配置.md`
  - `website/oem/index.html`
  - `website/oem/automation.html`

## 推荐客户交付目录

```text
OEM/SDK/
  repo/
    com/paopaoime/oem/...
    com/paopaoime/oem/internal/...
    org/fcitx/fcitx5/android/...
  docs/
    index.html
    automation.html
    styles.css
    app.js
    外部版-SDK集成手册.md
    外部版-自动化授权配置.md
  DELIVERY_README.txt
  oem-sdk-delivery-<version>.zip
```

## 验收清单

- `publishReleasePublicationToOemLocalRepository` 执行成功
- `OEM/SDK/repo` 存在完整 `aar / pom / module / sources`
- `OEM/SDK/oem-sdk-delivery-<version>.zip` 已生成
- `OEM/company` 已按“空白宿主 + 复制 OEM/SDK + 对外文档”方式重新接入
- `OEM/company/android` 能独立构建 `debug / release` APK
- 自动化套餐真机验收必须覆盖系统权限、Stellar 内部授权、Binder ready 和 R8 后 `ServerBootstrap` 可加载
- 对外文档只保留客户可执行的集成步骤，不出现内部架构和无关信息
- 本次若递增 SDK 版本并正式上线，集成说明 PDF 与 Markdown 的“修改记录”各新增该版本一行，且最新 PDF、交付 ZIP 和官网文件一致
- `AI回复 / 素材库` 抽屉只能从输入法上下文进入，普通 Activity 直拉应被拦截



本轮 `1.0.0.20260921` 已于 2026-09-22 完成正式发布、31 文件 HTTPS 校验和发布后空白工程 Release/R8 实机复验；详见 [15-20260921本地默认皮肤发布与集成验收](15-20260921本地默认皮肤发布与集成验收.md)。

`1.0.0.20260923` Android 17 兼容预适配已于 2026-09-24 发布：官网演示包按空白宿主用途采用 Debug 构建，另以 Release/R8 校验 SDK 集成；公开 HTTPS 32 项文件校验和发布后独立工程实机验证通过。Android 17 设备测试仍待补齐；详见 [16-20260923-Android17预适配与发布验收](16-20260923-Android17预适配与发布验收.md)。

## 20261008 语音发布检查补充

- 语音总开关在宿主 res/raw/oem_voice_config.json，默认 true；三个档位均使用固定权重清单。
- 导出 catalog 必须含 com.paopaoime.oem.internal:sherpa-onnx:1.13.8；全仓预期 25 AAR（catalog 24 项，另含内部传递依赖）。
- 只有 voice 的 POM 和 .module 传递 sherpa；基础宿主不得引入 sherpa；APK 只有一份当前 ABI 的 libsherpa-onnx-jni.so，不含 ASR/标点权重。
- 官网同时更新语音页面、默认 JSON、模型 URL/哈希清单、MD、PDF、SDK ZIP 与递增版本的 Demo APK。
- 逐项核对开关隐藏、模型下载/暂停/续传/校验、三档原生识别、自动标点、手动停止及深浅皮肤面板。

- 语音独立发布坐标 `com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261008`。同时验证包含语音与不含语音的 Release/R8 宿主；后者必须无 sherpa JNI 和语音入口。

`1.0.0.20261008` 已于 2026-10-08 发布：八个公开 SDK R8、官网 Debug 演示包、88 项公开文件校验、公开 ZIP 独立缓存构建与发布后真机 10 项回归通过；包括可选语音 SDK 与新版 AI 一键安装 MD。详见 [17-20261008离线语音与工具面板对齐](17-20261008离线语音与工具面板对齐.md)。


## 20261010 双默认皮肤交互更新

日光瓷白与夜幕石墨统一键盘比例、候选高亮留白与文字、按键气泡、键盘类型图标和分页工具面板；工具栏与工具面板不提供皮肤入口。八个公开 SDK 重新制作并执行 Release/R8，输入法运行时升级至 app:1.10。
当前 SDK `1.0.0.20261010`，运行时 `app:1.10`。构建、部署及验收状态见 [本轮记录](18-20261010双默认皮肤交互对齐.md)。本轮不操作手机做界面测试，安装后由用户验收。

`1.0.0.20261010` 已于 2026-10-10 发布：双默认皮肤标准对齐、runtime app:1.10、八个公开 SDK Release/R8、官网 Debug APK、88 项 HTTPS 文件与公开 ZIP 校验、空缓存独立宿主及不含语音宿主 R8 通过。本轮按用户要求不操作手机测试。备份、哈希和逐项范围见 [18-20261010双默认皮肤交互对齐](18-20261010双默认皮肤交互对齐.md)。


## 20261011 剪贴板工具栏修复

复制文本不再自动替换工具栏，包含键盘显示中、重新唤起及旧设置为开启的场景；保留手动剪贴板、历史和粘贴。移除无效的工具栏剪贴板建议/超时设置。八个公开 SDK 全量重建并执行 Release/R8，输入法运行时升级至 app:1.11。 状态见 [本轮记录](19-20261011剪贴板不替换工具栏.md)。

`1.0.0.20261011` 已于 2026-10-11 发布：25 AAR / 八公开 SDK R8，90 项公开文件校验、公开 ZIP 空缓存 Release/R8、无语音宿主、主项目与 OEM 及公开包六项实机回归通过。设备安全设置授权限制单列于 [本轮发布记录](19-20261011剪贴板不替换工具栏.md)。


## 202610111 临时剪贴板快捷回复

已同步同宿主一次性剪贴板与高情商快捷回复，复用 AI 回复客户配置并支持 DrawerKit 客户自绘结果。SDK `1.0.0.202610111`、runtime `app:1.12`；构建与发布状态见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。

`1.0.0.202610111` 已正式发布：八公开 SDK R8、25 AAR、90 项 HTTPS 校验、官网 ZIP 空依赖缓存 Release/R8 和 11 项发布后真机回归通过；客户配置与客户自绘扩展仅限 OEM。备份、哈希与清理详见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。

## 20261012 自定义抽屉入口接管

新回调、状态栏提前收键盘修复、主线程与异常隔离；SDK `1.0.0.20261012`，runtime `app:1.13`。本轮验证与发布状态见 [22-20261012自定义抽屉入口回调](22-20261012自定义抽屉入口回调.md)。

`1.0.0.20261012` 已发布：合并自定义抽屉入口与剪贴板智能回复接管；25 AAR、八公开 SDK R8、654 文件暂存校验、92 项公开文件校验、完整/精简宿主和官网 ZIP 空依赖缓存 Release/R8、发布后 15 项真机回归通过。范围、兼容性及设备权限限制见 [本轮记录](22-20261012自定义抽屉入口回调.md)。
