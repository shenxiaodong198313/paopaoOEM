# OEM 本地默认皮肤集成回归

本测试针对客户可取得的发布 AAR，在独立企业 Release/R8 App 中运行，不引用 OEM 源码工程。

本轮工程：`OEM/company/enterprise-skins-20260921`。输入法运行时 `app:1.7`，统一 SDK `1.0.0.20260921`。

测试依赖补充 `androidTestImplementation("androidx.constraintlayout:constraintlayout:2.2.1")`，用于直接检查键盘 View 的布局继承关系；普通客户 App 无需为皮肤新增此依赖。

`SkinInstrumentation.java` 复制到企业宿主 `app/src/androidTest/java/com/enpaopaokeyboard/ai/`。宿主按照对外 SOP 初始化真实 `FcitxApplication`、`OemHostRuntime` 和 License；另提供包含 `editor: EditText` 的 `InputCheckActivity`，测试页面保留该字段。Gradle 使用 `testBuildType = "release"` 和 `testInstrumentationRunner = "com.enpaopaokeyboard.ai.SkinInstrumentation"`。测试包与正式宿主使用同一签名。

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleReleaseAndroidTest --no-daemon --no-build-cache
adb install --no-streaming -r app/build/outputs/apk/release/app-release.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell ime enable com.enpaopaokeyboard.ai/com.paopaoime.aikeyboard.ime.service.AiInputMethodService
adb shell ime set com.enpaopaokeyboard.ai/com.paopaoime.aikeyboard.ime.service.AiInputMethodService
adb shell am instrument -w com.enpaopaokeyboard.ai.test/com.enpaopaokeyboard.ai.SkinInstrumentation
adb pull /sdcard/Android/data/com.enpaopaokeyboard.ai/files/skin-verification
```

覆盖新装默认、四个旧默认主题迁移、显式/隐式跟随系统、自定义浅深色保留、迁移幂等性、设备加密存储资源、内置主题不可删改，以及两款皮肤的 QWERTY/T9/数字布局、工具栏/侧栏对齐、工具栏静止背景全透明和图标 23dp 无重复内边距、英文/中文候选词/数字真实输入。截图、布局数据与结果文件保存在宿主外部文件目录。测试只向专用本地输入框输入，不向外部联系人发送消息。

发布后另建空白工程，从官网下载 SDK ZIP/集成 MD，按相同 SOP 重新构建、安装及运行测试。证据见 `OEM/Doc/15-20260921本地默认皮肤发布与集成验收.md`。
