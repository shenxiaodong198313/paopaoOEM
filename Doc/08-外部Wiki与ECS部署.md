# 外部 Wiki 与 ECS 部署

## 目标

将 OEM 对外 wiki 统一为静态 HTML 站点，并部署到主站子路径：

- 目标地址：`https://www.paopaoime.com/oem`
- SDK 下载地址：`https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip`
- Demo 下载地址：`https://www.paopaoime.com/oem/downloads/oem-ime-demo-latest.apk`
- PDF 下载地址：`https://www.paopaoime.com/oem/downloads/paopao-oem-sdk-doc-latest.pdf`
- 本地源码目录：`OEM/website/oem`
- 线上目录：`/var/www/paopao/website/oem`

## 演示 APK 的定位与签名

官网演示 APK 用于模拟第三方企业新建空白 Android 应用，再从交付 ZIP 集成 OEM SDK 的过程。该演示安装包使用 `OEM/demo/android` 的 `Debug` 构建，不要求生产发布签名。独立工程仍须额外通过 `Release/R8` 构建，以验证客户正式构建时的压缩兼容性；不要把 Release/R8 验证产物误当作官网演示包。

APK 即使是 Debug 构建也带有调试证书。若新包和线上旧演示包的证书不同，同包名无法覆盖安装；下载页应提示测试设备先卸载旧演示包再安装，且卸载会清除该演示应用的本地数据。调试 keystore 与应用授权值均不能提交 Git。

## 方案选择

本次不单独新建域名，也不新起独立前端工程，原因如下：

- 当前主站已由 Nginx 映射到 `/var/www/paopao/website`
- `/oem` 子路径天然适合承载静态文档
- 使用纯静态 HTML/CSS/JS 可以降低部署与运维复杂度
- 对外仍通过主站 `/oem` 子路径访问，但源码独立收敛到 `OEM` 项目内更利于维护

## 目录结构

```text
OEM/
  website/
    oem/
      index.html
      automation.html
      styles.css
      app.js
      md-copy.js
```

## 部署路径

- 本地目录：`c:\Users\15811\Desktop\paopao\OEM\website\oem`
- ECS 目录：`/var/www/paopao/website/oem`
- 服务器：`root@60.205.197.0`
- SSH key：`c:\Users\15811\.ssh\paopao_aliyun`

## 发布前必须同步的源文件

- `OEM/website/oem/index.html`
- `OEM/website/oem/styles.css`
- `OEM/website/oem/app.js`
- `OEM/website/oem/pages.js`
- 如 AI 一键装文档有修改：`OEM/website/oem/downloads/oem-ai-one-click-install-latest.md`
- 如对外 PDF 有修改：`OEM/website/oem/downloads/paopao-oem-sdk-doc-latest.pdf`

硬规则：

- 右上角 PDF 下载徽章的日期由 `app.js` 从 `catalog.sdkVersion` 末尾日期戳动态推导，不允许再手工写死。
- 对外 PDF 必须与官网页面和 AI 一键安装 MD 保持同源：概述、集成、功能、安装、权限、API 结构一致，只允许展示形式不同。
- 仅当 SDK 版本号递增且正式上线时，才在集成说明 PDF 与 Markdown 的“修改记录”新增该版本一行并保留历史行；普通开发或未发版的文档修改不新增版本记录。上线前检查 PDF 首行是否写本次发布内容，重新导出包含新版文档的 SDK ZIP。
- `scripts/deploy-oem-docs.ps1` 负责上传整个 `OEM/website/oem`，因此 PDF、测试用例页面与右上角下载入口会一并发布。

## Nginx 基线

主站已存在以下规则：

```nginx
location / {
    root /var/www/paopao/website;
    index index.html;
    try_files $uri $uri/ /index.html;
}
```

在该基线下，只要 `OEM/website/oem/index.html` 已同步到 ECS，`/oem` 路径即可被主站静态目录直接承载。

## 发布步骤

### 1. 本地预览

```powershell
cd c:\Users\15811\Desktop\paopao\website
python -m http.server 8016
```

本地访问：

- `http://127.0.0.1:8016/oem/`

### 2. 上传到 ECS

使用脚本：

- `scripts/deploy-oem-docs.ps1`

该脚本当前同时负责：

- 上传 `OEM/website/oem`
- 清理 ECS 上旧的 SDK 与 Demo 安装包
- 上传最新 `OEM/SDK/oem-sdk-delivery-*.zip`
- 上传最新 Demo 安装包
- 在 ECS 上生成固定下载地址：
  - `https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip`
  - `https://www.paopaoime.com/oem/downloads/oem-ime-demo-latest.apk`
  - `https://www.paopaoime.com/oem/downloads/paopao-oem-sdk-doc-latest.pdf`

注意：

- 执行脚本前先确认本地 `OEM/SDK/oem-sdk-delivery-current.zip` 与 `OEM/company/android/app/build/outputs/apk/ai/release/app-ai-release.apk` 已更新为本次最新产物。
- 执行脚本后只代表文件已上传，不代表官网展示一定已经通过最终验收。

或直接手工执行：

```powershell
scp -i C:\Users\15811\.ssh\paopao_aliyun -r `
  c:\Users\15811\Desktop\paopao\OEM\website\oem `
  root@60.205.197.0:/var/www/paopao/website/
```

### 3. 线上验收

```powershell
curl.exe -ksSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/ `
  | Select-String -Pattern "更新于\s+\d{4}-\d{2}-\d{2}|oem-sdk-delivery-latest.zip|oem-ime-demo-latest.apk"

curl.exe -ksSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/automation.html `
  | Select-String -Pattern "更新于\s+\d{4}-\d{2}-\d{2}|oem-sdk-delivery-latest.zip|oem-ime-demo-latest.apk"

curl.exe -ksSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/API/ `
  | Select-String -Pattern "更新于\s+\d{4}-\d{2}-\d{2}|oem-sdk-delivery-latest.zip|oem-ime-demo-latest.apk"

curl.exe -kI -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip

curl.exe -kI -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/downloads/oem-ime-demo-latest.apk

curl.exe -kI -H "Cache-Control: no-cache" -H "Pragma: no-cache" `
  https://www.paopaoime.com/oem/downloads/paopao-oem-sdk-doc-latest.pdf
```

通过标准：

- 首页右上角能看到 PDF 下载徽章，文案含 `V1.0` 与本次发布日期
- 左侧导航能看到 `测试用例` 分组及其二级菜单
- `/oem/downloads/oem-sdk-delivery-latest.zip` 返回 `200`
- `/oem/downloads/oem-ime-demo-latest.apk` 返回 `200`
- `/oem/downloads/paopao-oem-sdk-doc-latest.pdf` 返回 `200`
- 两个下载文件的 `content-length` 与本地制品字节数一致
- PDF 书签、页眉页脚、版本号与官网 catalog 一致

## 页面约束

- 所有资源必须使用相对路径
- 不依赖打包器和 Node 构建链
- 不暴露内部模块名、私钥策略和风控实现
- 页面中的能力说明必须与 `OEM/Doc` 的外部版 Markdown 一致
- 测试用例是官网的一部分，必须保持与左侧导航、翻页顺序一致；新增/删除页面时同步更新 `pages.js` 注册表和 `index.html` 侧栏结构
- 移动端兼容（2026-08-07 起）：`max-width: 900px` 时侧栏默认收起并改为悬浮抽屉（展开不挤压正文），点击菜单项后自动收起；正文页脚上方有「上一页 / 下一页」翻页按钮，按侧栏导航顺序切换路由
- 官网唯一源为 `OEM/website/oem`；`deploy-oem-docs.ps1`、`export-sdk.ps1`、`Drill/gen-full-md.js` 均读写此处，禁止再回退到 `PAOPAOIME/website/oem` 或再建第二份镜像

## 常见误判

### 1. 服务器目录已更新，但页面日期还是旧的

根因：

- 通常不是上传失败，而是源 HTML 中的按钮日期没有提前改掉。

处理：

- 回到 `OEM/website/oem/index.html`、`OEM/website/oem/automation.html`、`OEM/website/oem/API/index.html` 直接修改日期。
- 重新执行 `scripts/deploy-oem-docs.ps1`，再做直连验收。

### 2. 某些抓取结果还是旧页面，但直连访问已经是新页面

根因：

- 中间缓存或抓取链路缓存导致读取到旧内容。

处理：

- 以 `curl.exe` 直连并加 `Cache-Control: no-cache` / `Pragma: no-cache` 的结果为准。
- 下载文件进一步用 `content-length` 和 `last-modified` 对照本地制品确认。

## 后续维护规则

- 外部站改版后，必须同步更新本页
- 如果 `OEM/website/oem` 新增资源目录，部署脚本也要一起更新
- 如果未来把 `/oem` 独立成二级域名，再补 Nginx 新配置，不在本阶段提前过度设计

## 20261008 语音页面与发布附件

- 官网新增 #/voice 路由、默认配置 downloads/oem_voice_config.json、固定模型清单 downloads/oem-voice-models.json 及 downloads/oem-voice-guide.md。必须与 PDF 和 AI 一键装 MD 一并导出、发布。
- macOS 发布按 staging 完整校验后目录切换；上线前将当前 OEM 目录备份并复制到本机，禁止修改主站、Android 服务和管理后台。
- 当前官网 APK 来源为 OEM/demo/android 的 Debug 构建；独立 Release/R8 用于企业集成验证。旧段落中的 company Release 路径是历史记录。

`1.0.0.20261010` 已于 2026-10-10 发布：双默认皮肤标准对齐、runtime app:1.10、八个公开 SDK Release/R8、官网 Debug APK、88 项 HTTPS 文件与公开 ZIP 校验、空缓存独立宿主及不含语音宿主 R8 通过。本轮按用户要求不操作手机测试。备份、哈希和逐项范围见 [18-20261010双默认皮肤交互对齐](18-20261010双默认皮肤交互对齐.md)。


## 20261011 剪贴板工具栏修复发布

SDK `1.0.0.20261011` / runtime `app:1.11` 已按 macOS staging 流程发布，652 文件上线前校验通过，仅切换 OEM 目录。旧目录与完整归档在 ECS、本机均可回退；主站与管理后台健康。构建、六项实机回归、公开下载与空缓存验证的完整状态见 [19-20261011剪贴板不替换工具栏](19-20261011剪贴板不替换工具栏.md)。

## 202610111 临时剪贴板快捷回复发布

官网 SDK、Demo、32 页 PDF、AI 一键安装 MD、catalog 及 sdk-repo 已同步为 SDK `1.0.0.202610111` / runtime `app:1.12`。653 文件暂存校验后原子切换；90 项公开文件和官网 ZIP 校验通过，空缓存独立宿主 R8 与 11 项真机回归通过。回滚、私密备份和最终产物哈希见 [本轮记录](20-202610111临时剪贴板快捷回复.md)。


## 20261012 自定义入口与剪贴板回复接管发布

官网 SDK `1.0.0.20261012` / runtime `app:1.13`，SDK ZIP、Demo、32 页 PDF、AI 一键安装 MD、catalog 和 Maven 仓同步。654 项暂存 SHA-256 校验后仅切换 OEM 静态目录；92 项公开 HTTPS 校验及官网 ZIP 空依赖缓存 Release/R8、15 项发布后真机回归通过。core/admin active、/health healthy 且 dbHealthy=true、/admin/ HTTP 200。当前目录可由 `/var/www/paopao/deploy_backups/oem-before-entry-20261012` 回退，完整私密归档在 ECS 与本机均保留。哈希、构建、验证范围和清理见 [本轮记录](22-20261012自定义抽屉入口回调.md)。
