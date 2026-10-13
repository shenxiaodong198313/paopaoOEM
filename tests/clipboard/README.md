# 剪贴板工具栏实机回归

`fixture` 是与被测输入法独立、无网络权限的本地宿主，不引用任何输入法源码或 SDK。测试读取当前输入法窗口的实际无障碍内容，比较复制前后工具栏与键盘内容，保留截图和结果。

使用现有 Android Gradle wrapper 在 fixture 下构建 `:app:assembleDebug :app:assembleDebugAndroidTest`，安装两个 APK，并将被测正式输入法设置为默认。然后执行：

```sh
adb shell am instrument -w -e label main-after com.paopaoime.clipboardregression.test/com.paopaoime.clipboardregression.ClipboardInstrumentation
adb pull /sdcard/Android/data/com.paopaoime.clipboardregression/files/clipboard-main-after
```

OEM 使用独立 Release/R8 宿主安装后的真实输入法，label 改为 `oem-after`。测试仅在本地输入框操作，不发送消息。覆盖显示中复制、连续复制、隐藏时复制后唤起、敏感文本复制、再次唤起与宿主手动粘贴；同时断言复制不会自动写入输入框。失败截图不得当作通过证据。

2026-10-11 修复前主项目安装包在第一项明确失败：工具栏原按钮消失，窗口文本变为复制标记。修复后结果见 OEM Doc 19 和 `tmp/oem-clipboard-20261011/`。
