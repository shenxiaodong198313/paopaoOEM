# SDK 化拆分蓝图

## 目标

本蓝图用于定义 OEM 工程从“独立输入法 App”走向“可对外交付 SDK”的模块边界、演进顺序和宿主职责。

## 当前判断

- 当前最成熟的可复用边界是 `OEM/android/aikeyboard-sdk-ai`
- 当前最重的耦合点仍在 `OEM/android/app`
- 输入法内核、自动化能力和设置 UI 还没有完全收敛成对外稳定接口
- 因此本次 SDK 化采用分阶段推进，不做一次性大拆

## 分层结果

### 第一层：宿主壳

职责：

- 承担品牌资源、设置入口、启用引导和内部验证能力
- 提供 `Application` 初始化入口
- 提供 demo-app 作为联调壳

当前落点：

- `OEM/android/app`

### 第二层：AI Runtime SDK

职责：

- 暴露模型下载、日志初始化和设置页跳转能力
- OEM 版本默认不再承载历史本地模型运行时，会话能力以云端/代理链路为主
- 对外形成第一阶段可交付 AAR

当前落点：

- `OEM/android/aikeyboard-sdk-ai`
- `OEM/android/model-downloader-wrapper`
- `OEM/android/paddleocr4android`

### 第三层：IME Core SDK

职责：

- 统一输入法初始化、宿主桥接、License 校验、能力开关和公共状态查询
- 屏蔽宿主对内部实现类的直接依赖

建议模块名：

- `oem-ime-sdk-core`

当前状态：

- 已完成第二阶段最小实现
- 当前模块目录为 `OEM/android/oem-ime-sdk-core`
- 已提供统一初始化、License 请求模型、能力开关和状态中心
- 完整 UI/IME 宿主桥仍留在后续阶段继续抽离

### 第四层：Automation SDK

职责：

- 暴露宿主可选的自动化桥接能力
- 将高权限管理端与普通宿主依赖分离

建议策略：

- 宿主只接 `stellar-api/provider/shared`
- `stellar-manager` 继续作为 companion app 或内部验证壳，不默认嵌入对外 SDK

### 第五层：UI SDK

职责：

- 当客户需要嵌入标准设置页或素材页时，提供可选 UI 容器
- 与纯能力型 SDK 解耦，避免所有客户被动引入大体积 UI

建议模块名：

- `oem-ime-sdk-ui`

## 第一阶段范围

- 产出 `oem-ime-sdk-ai`
- 保持 `app` 继续做 demo-app
- 补齐本地 Maven 发布骨架
- 补齐外部 API 文档、版本文档和 FAQ
- 交付 `website/oem` 静态 HTML 文档站

## 第二阶段范围

- 新建 `oem-ime-sdk-core`
- 抽离授权配置、宿主桥接和能力总开关
- 统一 `OemImeSdk.initialize(...)` 入口
- 输出对外能力矩阵和错误码定义

当前完成度：

- `OemImeSdk.initialize(...)` 已落地
- `OemLicenseProvider` / `OemLicenseRequest` / `OemLicenseSnapshot` 已落地
- `OemManifestMetaKeys` 和 Manifest 配置读取已落地
- 宿主签名 SHA-256 和版本信息读取已落地
- `oem-ime-sdk-core` 已可发布到本地 Maven 仓库

## 第三阶段范围

- 评估 `host-bridge` 与输入法核心的进一步抽离
- 把可复用设置页收敛成 `oem-ime-sdk-ui`
- 评估自动化能力是否独立成对外套餐

## 模块归属表

| 领域 | 当前模块 | 目标归属 | 是否对外 |
| --- | --- | --- | --- |
| AI 会话与模型 | `aikeyboard-sdk-ai` | `oem-ime-sdk-ai` | 是 |
| 历史本地模型运行时 | `mnn-master-core` | 已从 OEM 编译链移除，不再作为标准依赖 | 否 |
| 模型下载 | `model-downloader-wrapper` | `oem-ime-sdk-ai` 内部依赖 | 间接 |
| TTS | 已移出 OEM 编译链 | 不再作为 OEM 标准依赖 | 否 |
| OCR | `paddleocr4android` | `oem-ime-sdk-ai` 可选能力 | 间接 |
| 输入法壳 | `app` | `oem-ime-demo-app` | 否 |
| 输入法宿主桥 | `ai-ime/host-bridge` | `oem-ime-sdk-core` 候选 | 后续 |
| 初始化与 License 外壳 | `oem-ime-sdk-core` | `oem-ime-sdk-core` | 是 |
| Stellar 自动化 | `third_party/stellar/*` | `oem-ime-sdk-automation` 候选 | 可选 |

## 宿主职责

- 传入 `customerId`、`appKey`、环境和渠道信息
- 负责自身 `Application` 初始化和生命周期管理
- 不直接依赖 SDK 内部类和内部资源
- 按版本说明升级，不跳过迁移说明

## 里程碑

1. 第一阶段：`AI Runtime SDK` 可发布、可接入、可联调
2. 第二阶段：`Core SDK` 收口初始化与授权
3. 第三阶段：`UI/Automation` 变为可选组件

## 退出条件

满足以下条件后，才算第一阶段 SDK 化完成：

- `aikeyboard-sdk-ai` 可以产出稳定 AAR
- 对外 API 和版本说明可独立阅读
- demo-app 可以作为联调壳完成验证
- `website/oem` 可以独立部署到 ECS 并稳定访问
