# OEM 官网发布说明

## 1. 目录定位

- 本目录：`OEM/website/oem`
- 对外访问地址：`https://www.paopaoime.com/oem/`
- ECS 线上目录：`/var/www/paopao/website/oem`
- 发布脚本：`PAOPAOIME/scripts/deploy-oem-docs.ps1`

说明：

- 从现在开始，`OEM 官网` 的唯一源码目录就是 `OEM/website/oem`
- `PAOPAOIME/website/oem` 已迁出，不再作为有效源目录
- `export-sdk.ps1`、`deploy-oem-docs.ps1`、`Drill/gen-full-md.js` 都已改为读写本目录

## 2. 发布前确认

发布前至少确认：

1. 本目录页面改动已完成：`index.html`、`automation.html`、`architecture.html`、`API/index.html`、`app.js`、`pages.js`
2. 如 AI 一键装内容有变化，重新生成：
   - `oem-ai-one-click-install-latest.md`
   - `downloads/oem-ai-one-click-install-latest.md`
3. `OEM/SDK/catalog/oem-sdk-catalog.json` 已是本次最新版本
4. 如需对外发 SDK / demo，先准备：
   - `OEM/SDK/oem-sdk-delivery-*.zip`
   - `OEM/company/android/app/build/outputs/apk/ai/release/app-ai-release.apk`

## 3. 一键发布

```powershell
cd C:\Users\15811\Desktop\paopao\PAOPAOIME
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-oem-docs.ps1
```

默认行为：

- 上传 `OEM/website/oem` 到 ECS `/var/www/paopao/website/oem`
- 上传最新 SDK zip 到 `downloads/oem-sdk-delivery-latest.zip`
- 上传 demo apk 到 `downloads/oem-ime-demo-latest.apk`
- 同步 `OEM/SDK/repo` 到线上 `sdk-repo`
- 同步 `OEM/SDK/catalog/oem-sdk-catalog.json` 到线上 `catalog`

## 4. 发布后验收

```bash
curl -I https://www.paopaoime.com/oem/
curl -I https://www.paopaoime.com/oem/automation.html
curl -I https://www.paopaoime.com/oem/API/
curl -I https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip
curl -I https://www.paopaoime.com/oem/downloads/oem-ime-demo-latest.apk
```

## 5. 口径要求

- `OEM 官网` 是独立项目，不再归入 `PAOPAOIME/website` 主站源码目录
- `PC 官网维护` 与 `Web 官网发布 SOP` 不再负责 OEM 站点发布
- 后续只要 OEM 官网有结构、页面、发布方式变化，必须同步更新：
  - `OEM/Doc/08-外部Wiki与ECS部署.md`
  - `PAOPAOIME/wiki/content/android-oem.md`
  - 本 README

## 20261008 语音页面与发布附件

- 官网新增 #/voice 路由、默认配置 downloads/oem_voice_config.json、固定模型清单 downloads/oem-voice-models.json 及 downloads/oem-voice-guide.md。必须与 PDF 和 AI 一键装 MD 一并导出、发布。
- macOS 发布按 staging 完整校验后目录切换；上线前将当前 OEM 目录备份并复制到本机，禁止修改主站、Android 服务和管理后台。
- 当前官网 APK 来源为 OEM/demo/android 的 Debug 构建；独立 Release/R8 用于企业集成验证。旧段落中的 company Release 路径是历史记录。

- voice 为独立可选模块：catalog 未勾选时不生成 sherpa/voice 依赖；勾选后默认开启，并提供 JSON 配置。演示 full 含语音，lite 或 demoVoice=false 排除。
