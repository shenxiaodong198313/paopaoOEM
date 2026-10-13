# OEM SDK Wiki

> 2026-10-12 同版本 UI 更新、键盘调节和按键效果默认值见 [本轮记录](21-20261012快捷回复皮肤面板与键盘调节.md)。

> 当前发布：SDK `1.0.0.202610111` / runtime `app:1.12`。2026-10-12 同版本优化已发布：皮肤匹配面板、键盘调节修复及默认按键效果，见 [最新发布记录](21-20261012快捷回复皮肤面板与键盘调节.md)。AI 客户配置与自绘抽屉接口沿用 [原版本记录](20-202610111临时剪贴板快捷回复.md)。

## 目标

该目录用于维护 OEM 输入法的 SDK 化事实基线，统一沉淀以下内容：

- 哪些能力已经具备 SDK 化条件
- 哪些模块仍属于宿主壳或内部验证工程
- 对内如何拆分、发布、授权和交付
- 对外如何让厂家快速接入、联调和验收

## 当前阶段

- 第一阶段以 `OEM/android/aikeyboard-sdk-ai` 为可发布 AAR 的起点
- 第一阶段目标不是一次性输出完整输入法 SDK，而是先稳定 `AI Runtime SDK`
- 输入法核心、授权、自动化和 UI 继续按模块化方案推进
- 对外站点统一部署到 `https://www.paopaoime.com/oem`

## 双视图结构

- 内部版：面向你自己和研发同学，保留真实模块边界、构建细节、授权策略和实施路线
- 外部版：面向集成厂家，保留接入、交付、授权、FAQ 和版本说明，不暴露内部安全实现

## 推荐阅读

### 内部版

1. [内部版-首页](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%86%85%E9%83%A8%E7%89%88-%E9%A6%96%E9%A1%B5.md)
2. [01-SDK-集成与配置](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/01-SDK-%E9%9B%86%E6%88%90%E4%B8%8E%E9%85%8D%E7%BD%AE.md)
3. [06-SDK化拆分蓝图](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/06-SDK%E5%8C%96%E6%8B%86%E5%88%86%E8%93%9D%E5%9B%BE.md)
4. [07-SDK实施与发布清单](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/07-SDK%E5%AE%9E%E6%96%BD%E4%B8%8E%E5%8F%91%E5%B8%83%E6%B8%85%E5%8D%95.md)
5. [08-外部Wiki与ECS部署](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/08-%E5%A4%96%E9%83%A8Wiki%E4%B8%8EECS%E9%83%A8%E7%BD%B2.md)
6. [09-Company宿主验证工程](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/09-Company%E5%AE%BF%E4%B8%BB%E9%AA%8C%E8%AF%81%E5%B7%A5%E7%A8%8B.md)
7. [02-SDK-分发与加固方案](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/02-SDK-%E5%88%86%E5%8F%91%E4%B8%8E%E5%8A%A0%E5%9B%BA%E6%96%B9%E6%A1%88.md)
8. [03-商业授权与年费方案](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/03-%E5%95%86%E4%B8%9A%E6%8E%88%E6%9D%83%E4%B8%8E%E5%B9%B4%E8%B4%B9%E6%96%B9%E6%A1%88.md)
9. [04-私有化授权方案](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/04-%E7%A7%81%E6%9C%89%E5%8C%96%E6%8E%88%E6%9D%83%E6%96%B9%E6%A1%88.md)
10. [05-实施路线图](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/05-%E5%AE%9E%E6%96%BD%E8%B7%AF%E7%BA%BF%E5%9B%BE.md)
11. [10-企业总席位与SDK运行控制方案](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/10-%E4%BC%81%E4%B8%9A%E6%80%BB%E5%B8%AD%E4%BD%8D%E4%B8%8ESDK%E8%BF%90%E8%A1%8C%E6%8E%A7%E5%88%B6%E6%96%B9%E6%A1%88.md)
12. [11-OEM输入法模块升级记录](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/11-OEM%E8%BE%93%E5%85%A5%E6%B3%95%E6%A8%A1%E5%9D%97%E5%8D%87%E7%BA%A7%E8%AE%B0%E5%BD%95.md)

### 外部版

1. [外部版-首页](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-%E9%A6%96%E9%A1%B5.md)
2. [外部版-SDK集成手册](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-SDK%E9%9B%86%E6%88%90%E6%89%8B%E5%86%8C.md)
3. [外部版-API参考](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-API%E5%8F%82%E8%80%83.md)
4. [外部版-授权与交付说明](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-%E6%8E%88%E6%9D%83%E4%B8%8E%E4%BA%A4%E4%BB%98%E8%AF%B4%E6%98%8E.md)
5. [外部版-版本说明](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-%E7%89%88%E6%9C%AC%E8%AF%B4%E6%98%8E.md)
6. [外部版-FAQ与排障](file:///c:/Users/15811/Desktop/paopao/OEM/Doc/%E5%A4%96%E9%83%A8%E7%89%88-FAQ%E4%B8%8E%E6%8E%92%E9%9A%9C.md)

## 目录与产物映射

- `OEM/android/aikeyboard-sdk-ai`：第一阶段对外 AAR 模块
- `OEM/android/oem-ime-sdk-core`：第二阶段对外 Core AAR 模块
- `OEM/android/app`：内部验证壳与 demo-app 基线
- `OEM/company/android`：独立外部宿主验证工程
- `OEM/website/oem`：对外 HTML wiki 源码
- `scripts/deploy-oem-docs.ps1`：对外文档部署脚本

## 维护规则

- 每次调整 `OEM/android/aikeyboard-sdk-ai` 的对外接口，必须同步更新“集成与配置”和“外部版-API参考”
- 每次调整模块拆分、发布坐标或构建动作，必须同步更新“06-SDK化拆分蓝图”和“07-SDK实施与发布清单”
- 每次调整外部页面、导航、访问路径或 ECS 上传方式，必须同步更新“08-外部Wiki与ECS部署”
- 每次调整授权粒度、License 字段、交付口径，必须同步更新“商业授权与年费方案”和“外部版-授权与交付说明”
- 所有已经确定的设计基线，先写 Wiki，再继续代码改造

- [20261010 双默认皮肤交互对齐](18-20261010双默认皮肤交互对齐.md)

- [20261011 剪贴板不替换工具栏](19-20261011剪贴板不替换工具栏.md)


## 202610111 临时剪贴板快捷回复

已同步同宿主一次性剪贴板与高情商快捷回复，复用 AI 回复客户配置并支持 DrawerKit 客户自绘结果。SDK `1.0.0.202610111`、runtime `app:1.12`；构建与发布状态见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。
