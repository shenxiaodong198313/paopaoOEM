# OEM 离线语音运行库

2026-10-08：由泡泡三档语音移植，发布坐标 `com.paopaoime.oem.internal:sherpa-onnx:1.13.8`。使用官方原始 AAR，源码工程的 publish 任务同时输出 POM/module；只有 oem-ime-sdk-voice 以 Maven 依赖传递给宿主，基础 host-bridge 不依赖 sherpa。模型权重不交付，配置和镜像见 `OEM/Doc/外部版-离线语音配置.md`。

- 来源：https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- 文件：https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar
- 上游 SHA-256：`b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471`
- 大小：38,691,998 字节；未修改上游 AAR。
- 选用官方静态链接 ONNX Runtime 变体，语音只携带自己的 JNI 库，避免与知识库使用的独立 `onnxruntime-android` 动态库混装。App 当前仅打包 ARM64；JNI 边界沿用 `oem-ime-sdk-voice/consumer-rules.pro` 的保留规则。
