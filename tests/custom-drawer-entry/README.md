# OEM 宿主入口回调回归

仅复制到从正式 ZIP 接入的 `OEM/company` 独立客户工程；不进入 SDK/官网演示包。`EntryValidationActivity.kt` 和 `JavaCompatibility.java` 验证旧五位置 Kotlin/Java 构造、默认抽屉、true/false/异常接管、主线程、两个入口、注册配额、更新及工具条排序/隐藏池和注销清理。工具条上限从 SDK 常量读取，当前为 5。

宿主沿用 demo 的真实企业初始化；Manifest 额外注册 `.EntryValidationActivity`、`enableCustomDrawers=true` 和 `enableDebugLog=true`。测试 host 开 Release/R8，签名后卸载重装；先启动宿主等待 SDK 初始化，再启用/选择测试输入法。

`action=registry` 检查注册与工具条；`device-regression.py <package> <evidence-dir>` 覆盖两个桥接入口与后台调用的 true/false/异常共九例，检查每次恰好一次主线程回调、零工厂、输入法仍可见且 Activity 不变。另需真实点击工具条与工具面板第二页，不能以直接桥接调用替代 UI 验收。

追加剪贴板需求：`action=quick_setup --es mode true|false|throws|unregister|default` 配置宿主回调，回调模式刻意关闭 SDK AI/material 开关。`action=copy` 仅供直接桥接参数测试；输入法自身包内复制按原有过滤规则不出建议。真实剪贴板 UI 必须用独立外部 `CopyActivity.java`（包名 `com.paopaoime.clipboardregression`），可复制到 `OEM/tests/clipboard/fixture` 的独立副本，设置 Activity singleTop，Debug 构建安装。该应用不依赖 SDK、无网络权限。`action=quick_bridge [--ez nullHost true]` 验证可空来源包名，`action=theme --es theme light|dark` 设置测试皮肤。

真实点击“智能回复”后检查回调参数、无新 Activity/Toast/日志、键盘和建议条保持；回调内反注册也不能清条。点复制文本继续粘贴；默认模式用 HOST_ONLY 合成模型输出验证恢复内置生成、选中仅回填草稿。精简宿主没有 material/ai-reply/voice，验证两类入口接管不依赖这些切片。截图检查标题及叉号消失、按钮文案和浅深色对比度；工具条与候选区同步增加 12 布局单位。

测试完成后卸载临时宿主、恢复原输入法。不读取联系人或发送消息。测试过程中 ROM 拒绝 `WRITE_SECURE_SETTINGS` 授权要如实记录，不计为授权/自动化通过。

`quick-reply-regression.py <sdk-package> <evidence-dir> <button-x> <button-y>` 自动切换两个测试应用、复制原文并真实点按钮。坐标必须先看当前设备截图确定；PLQ110 本轮为 `(1080,1750)`。测试驱动不得把输入法应用本身当成外部复制宿主，也不得为测试移除现有复制来源过滤。

`default-quick-regression.py <sdk-package> <evidence-dir> <button-x> <button-y> <result-x> <result-y>` 撤销接管后检查原 SDK 抽屉、一次合成模型调用、草稿回填和零发送；坐标同样先通过截图确定。
