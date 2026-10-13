# OEM SDK 标准交付目录

该目录只保留 OEM SDK 的最新正确交付结果，不再在工作目录中累积历史版本包。

目录职责：

- `OEM/android/build/oem-sdk-repo`：源码工程的中间构建仓库
- `OEM/SDK/repo`：当前最新对外交付使用的本地 Maven 仓
- `OEM/SDK/docs`：当前最新对外交付文档副本
- `OEM/SDK/oem-sdk-delivery-<VersionTag>.zip`：当前最新标准交付压缩包

推荐结构：

```text
OEM/SDK/
  repo/
    com/paopaoime/oem/...
    com/paopaoime/oem/internal/...
    org/fcitx/fcitx5/android/...
  docs/
    index.html
    automation.html
    styles.css
    app.js
    *.md
  DELIVERY_README.txt
  export-sdk.ps1
  oem-sdk-delivery-current.zip
```

使用规则：

1. 每次 `OEM/android` 源码或 OEM 文档更新后，先重新发布 `AAR` 到 `OEM/android/build/oem-sdk-repo`
2. 再执行 `OEM/SDK/export-sdk.ps1 -Clean -VersionTag current`，覆盖 `repo/`、`docs/` 并删除旧交付 zip
3. `OEM/SDK` 根目录始终只保留 1 份最新正确 zip，不在此目录长期存放历史版本包
4. `OEM/Doc` 与 `OEM/website/oem` 是源文档；`OEM/SDK/docs` 仅允许通过导出脚本覆盖生成，禁止手工直改
5. `OEM/company` 每次验证都应从 `OEM/SDK` 复制最新结果，模拟第三方宿主接入
