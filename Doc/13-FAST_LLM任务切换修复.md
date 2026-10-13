# FAST_LLM ColorOS 任务恢复修复

日期：2026-09-09

## 问题与最终方案

客户在 OPPO RMX3843 上反馈：极速生成截图循环结束后，聊天 App 被系统动画切走并露出桌面。客户提供的源码定位和 AMS 日志指向 `moveTaskToBack(true)` 与 `AppTask.moveToFront()` 这一对任务切换，尤其是后者的 `startActivityFromRecents` 路径。

本轮先验证删除整对切换。PLQ110 / Android 16 / ColorOS V16.1.0 真机发现：截图正常，但系统 `ActivityRecordInputSink` 拦截注入滑动，日志为 `Not sending touch gesture ... ActivityRecordInputSink ... NO_INPUT_CHANNEL`。AOSP Android 16 的 ActivityRecordInputSink 实现确认：独立任务不能只靠窗口 `FLAG_NOT_TOUCHABLE` 穿透到另一个任务。因此未采用该方案。

最终使用工单提出的备选方案：截图期间仍临时退到聊天页；回抽屉使用 `NEW_TASK | REORDER_TO_FRONT | SINGLE_TOP | NO_ANIMATION`，删除 `AppTask.moveToFront()`、`CLEAR_TOP` 以及任务前台轮询。保留退后台后的 450ms 等待，删除第二次退后台的 280ms 和回前台的 350ms 固定等待，返回改为等待 `onNewIntent` 确认（最多 1 秒）。

返回 Intent 使用新的内部启动令牌和一次性返回标记；匹配后只确认返回，不重放抽屉入口请求、不重置正在进行的 AI 会话。显式关闭或销毁 Activity 后不再把面板拉回来。

霓虹隐藏方式：在任务退后台之前直接把 Activity 本地边框设为 `INVISIBLE`，等待两次绘制回调再退后台；返回后直接恢复 `VISIBLE`。边框不再跟随 Activity 向屏幕右侧滑出。独立悬浮边框保持固定在屏幕上，实际截屏时隐藏。

窗口的 180ms 准备等待纳入 `try/finally`，恢复使用 `NonCancellable`，覆盖准备期间取消、退后台后取消、截图异常和超时。恢复原窗口 flag、点击状态和抽屉位置。实际截图时暂时隐藏本地与自动化霓虹边框，返回后恢复。

## 验证方式

`OEM/tests/fast-capture` 提供可复现的独立消费者模板，复制交付 Maven 仓库并仅在测试副本覆盖 material/automation 新 Release AAR，禁止引用 SDK 源码模块。实际运行目录为 `OEM/company/capture-regression`。

测试使用合成聊天页、宿主截图回调和真实系统 `input swipe`，不发送聊天消息、不调用云端模型。第一轮空截图用例对 SDK 预期异常的断言已校正；第二轮保留了输入拦截失败日志，作为未采用删除整对任务切换方案的证据。

## 已完成验证

- material、automation 最终 Release/R8 构建成功；独立消费者 Debug APK 与 instrumentation APK 构建、安装成功。
- PLQ110 / Android 16 / ColorOS V16.1.0：最终 10 项测试全部通过，包含实际三张截图、两次滑动、任务返回、取消/错误恢复、霓虹退后台前隐藏。
- 返回采用 `0x30030000` 启动 flags，系统 transition 显示 `TRANSLUCENT | NO_ANIMATION | MOVE_TO_TOP`。备选方案测试中四次返回均 `acknowledged=true` 且为同一 task；图像验证两次 `moved=true`。对应记录未出现 `startActivityFromRecents`。
- 字节码检查确认 material AAR 不再引用 `android/app/ActivityManager`，含新的返回与霓虹帧等待方法。
- 最终 APK 已安装至测试设备，原默认输入法未变更。源码 `git diff --check` 通过。

证据目录：`OEM/tests/fast-capture/evidence/20260909`（设备日志和二进制证据留在本地，不纳入 Git）。其中 `instrumentation-final.txt` / `result-final.txt` 为最终 10 项结果，`capture-final.png` 为实际截图，`artifacts.json` 为 AAR/APK SHA-256；失败的无任务切换验证也保留在该目录。录屏尝试受设备限制未生成有效视频，不能将其作为视觉验收证据；霓虹检查通过实际生命周期时点断言确认退出前已隐藏，并等待绘制帧。

## 交付边界

已统一升版并按 SOP 从零构建整套正式 SDK `1.0.0.202609091`（七个公开模块 Release/R8），发布至 ECS OEM 官网。官网部署后下载正式制品构建 `OEM/company/capture-regression-release-202609091`，不覆盖任何 AAR，10 项真机回归再次全部通过。另从官网 AI 一键安装 MD 构建正式企业演示包 `com.enpaopaokeyboard.ai`，记录见《14-202609091正式发布与企业集成验收.md》。本轮未在客户 RMX3843 或真实微信中完成验收；当前截图证据来自 PLQ110 的合成聊天宿主。
