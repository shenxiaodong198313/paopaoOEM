# OEM 临时剪贴板与客户自绘回归

共享外部宿主 fixture 位于 `PAOPAOIME/tests/android/clipboard-quick-reply`，仅使用 Android 平台 API，不依赖输入法源码。该目录 `QuickReplyValidationActivity.kt` 仅复制到从 SDK ZIP 接入的独立客户验证工程，不进入官网 Demo 或 SDK。

验证 Activity 给独立验证应用的私有 `oem_developer_ai_config.json` 写入合成配置（无 API key），注册 HOST_ONLY 客户模型回调，逐次记录 `exactSource/configFile/screenshotCount` 布尔及计数，验证既有客户配置文件、语气、提示词、采样参数和原始复制文本全部沿用。合成回复只写入 fixture 编辑框，不发送联系人消息。

- `layout=builtin`：使用客户模型回调 + SDK 默认回复抽屉。
- `layout=content`：客户内容区自绘。
- `layout=full`：客户整块抽屉自绘，SDK 默认头部不出现。
- `failFirst=true`：首次回传 NETWORK，再通过客户配置的错误标题/描述展示；期间替换系统剪贴板，点“原文重新生成”，下一次请求仍须 `exactSource=true`。

完整 fixture label 含 `ai` 时包含结果回填；`direct-customer-content-retry` / `direct-customer-full` 只跑自绘流程；`native` 覆盖原生可选 TextView 复制后返回编辑器。所有快捷结果断言仅填草稿且 `onEditorAction` 计数为零。

R8 宿主只从 `oem-sdk-delivery-1.0.0.202610111.zip` 解压接入。真机后保存结果、截图和 callback 日志，并清除临时测试应用；官网发布后另用公开 ZIP 和空 Gradle 依赖缓存重建复验。
