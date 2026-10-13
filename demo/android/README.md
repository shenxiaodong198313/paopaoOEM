# 泡泡企业输入法演示宿主工程

该目录用于验证 `OEM SDK` 的外部集成方式。

## 当前演练目标

- 以空白 Android App 方式接入 `oem-ime-host-bridge`
- 不直接引用 `OEM/android` 的源码模块
- 通过 `OEM/SDK` 中导出的标准交付文件模拟客户集成
- full 演示包名为 `com.enpaopaokeyboard.ai`，中文名称为“泡泡企业输入法”；lite 使用独立测试包名
- 默认 targetSdk 36，演示宿主拒绝明文网络请求，企业 API 使用 HTTPS
- 本轮以 `Release/R8` 构建验证 SDK 集成，并以 `Debug` 包作为官网演示安装包

## 使用原则

- `OEM/company` 是测试目录，可以按需清空后重建
- 每次重建时，应先把 `OEM/SDK` 中导出的交付文件复制到当前工程，再进行接入验证
- 再严格按照对外《集成方式》文档接入，不允许直接依赖 `OEM/android` 源码模块

## 当前接入方式

- 标准交付目录：`../../SDK`
- 工程内复制仓库：`./local-sdk/repo`
- 当前主依赖：`com.paopaoime.oem:oem-ime-host-bridge:1.0.0.20260923` 与输入法运行时 `org.fcitx.fcitx5.android:app:1.8`

## 同步交付物

先在 `OEM/SDK` 完成导出，再在当前目录执行：

```powershell
.\sync-sdk.ps1
```

脚本会把 `OEM/SDK/repo` 和 `OEM/SDK/docs` 复制到当前工程的 `local-sdk/`，用于模拟客户把 SDK 解压后导入自己工程的场景。macOS 可直接复制两目录后用本机 Gradle 9.4.1 构建。

## 构建

```powershell
$env:GRADLE_USER_HOME='c:\Users\15811\Desktop\paopao\OEM\company\android\.gradle-home'
.\sync-sdk.ps1
.\gradlew.bat clean :app:assembleRelease -PdemoProfile=full
```

演示授权值从 Git 忽略的 `local.properties` 中 `oemDemoAppKey` 读取，不在源码中写入。官网演示安装包使用 `:app:assembleDebug -PdemoProfile=full` 构建；`Release/R8` 仍须单独构建，验证客户正式集成时不会被代码压缩破坏。

旧线上演示 APK 的证书 SHA-256 为 `88ca5df0d7dc410a9be2466f45d8b80c0bbefc35415da19dd9061fed83254b6f`。如果新 Debug 包的签名不同，同包名不能覆盖安装；已装旧版的测试设备需先卸载旧包再安装新版，卸载会清除演示应用本地数据。Debug keystore 不得提交 Git。
