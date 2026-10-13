(function () {
  'use strict';
  var OEM = window.OEM;
  var esc = OEM.esc;

  function el(html) {
    var t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  }

  function footer() {
    return el('<div class="footer-note">泡泡输入法企业版 · OEM SDK 对外交付站。正式接入值（customerId / appKey / 交付仓库）由我方随交付包提供；本站仅面向企业集成方与 AI 编程工具。<br><a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">京ICP备2026012698号-1</a></div>');
  }

  function fieldTable(rows) {
    var body = rows.map(function (r) {
      return '<tr><td><code>' + esc(r[0]) + '</code></td><td><code>' + esc(r[1]) + '</code></td><td>' + (r[2] ? '<span class="badge-req">必填</span>' : '选填') + '</td><td>' + esc(r[3]) + '</td></tr>';
    }).join('');
    return el('<div class="tbl-wrap"><table class="tbl"><thead><tr><th>字段</th><th>类型</th><th>必填/选填</th><th>说明</th></tr></thead><tbody>' + body + '</tbody></table></div>');
  }

  function modeCards(modes) {
    var wrap = el('<div class="grid-2"></div>');
    modes.forEach(function (m) {
      wrap.appendChild(el('<div class="card mode-card"><span class="mode-name">' + m[0] + '</span><p>' + m[1] + '</p></div>'));
    });
    return wrap;
  }

  function capTable(rows) {
    var body = rows.map(function (r) {
      return '<tr><td><code>' + esc(r[0]) + '</code></td><td>' + r[1] + '</td><td><code>' + esc(r[2]) + '</code></td></tr>';
    }).join('');
    return el('<div class="tbl-wrap"><table class="tbl"><thead><tr><th>字段 id</th><th>作用</th><th>关键回调 / API</th></tr></thead><tbody>' + body + '</tbody></table></div>');
  }

  function drawerBaseTable() {
    return el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>基座职责</th><th>规范</th></tr></thead><tbody>' +
      '<tr><td>容器</td><td>底部对齐、<b>80% 屏高</b>；<b>开合动画</b>开 420ms / 关 320ms；<b>下拉手势</b>关闭（沿用现有阈值 / 速度判定）</td></tr>' +
      '<tr><td>标准头部</td><td>返回按钮 + 标题槽 + 关闭按钮。<b>关闭按钮恒显</b>（体验兜底，宿主不可移除）；返回按钮宿主可配置显隐（<code>setBackVisible(visible)</code>，单页内容可隐藏）；标题槽可配（<code>setTitle(title)</code>，可改文或隐藏）</td></tr>' +
      '<tr><td>返回键分发</td><td>基座先询问内容层 <code>onBackPressed(): Boolean</code>，未消费再走基座返回 / 关闭</td></tr>' +
      '<tr><td>生命周期回调</td><td><code>onDrawerOpened / onDrawerClosed / onDrawerBack</code></td></tr>' +
      '<tr><td>主动开合</td><td>宿主可调用 <code>openDrawer(id)</code> / <code>closeDrawer(id)</code>；id 取 <code>ai_reply</code> / <code>material_hub</code> / 自定义抽屉 id</td></tr>' +
      '<tr><td>主题适配</td><td>基座头部随键盘深色 / 浅色主题；内容层深浅色由宿主自理，SDK 提供 <code>currentTheme()</code> 查询</td></tr>' +
      '</tbody></table></div>'
    );
  }

  /* ---------------- 产品简介 ---------------- */

  function pageIntro(root) {
    root.appendChild(el(
      '<div class="hero">' +
      '<span class="tag">产品介绍</span>' +
      '<h1>泡泡输入法企业版</h1>' +
      '<p class="lead">面向企业定制的 Android 输入法 SDK：以成熟输入法内核为基座，叠加 AI 回复、素材库、会话存档（输入质检）与可选自动化增强能力，支持模块化按需集成、企业域名私有联调，帮助企业把「输入 + AI + 管理」整合进自有 App。</p>' +
      '<div class="hero-actions">' +
      '<a class="btn" href="./downloads/oem-ime-demo-latest.apk" download>下载泡泡企业输入法演示应用（Debug 安装包）</a>' +
      '<a class="btn ghost" href="#/modules">进入功能选择</a>' +
      '</div>' +
      '<p class="hero-meta">SDK 1.0.0.202610111 · com.enpaopaokeyboard.ai · 默认开启 AI 回复与素材库 · Android 8.0+ / arm64 · 安装后启用「泡泡企业输入法」。AI 生成需配置可用的云端模型。旧版演示应用若无法覆盖安装，请先卸载再安装；卸载会清除演示应用本地数据。</p>' +
      '</div>'
    ));

    root.appendChild(el('<div class="card"><h2>1.0.0.202610111 更新（10 月 12 日同版本优化）</h2><p>快捷回复采用与日光瓷白、夜幕石墨匹配的独立面板和星光生成按钮；修复键盘高度、宽度拖动调节。默认标准按键音、50% 音量、较强触感。相同坐标请替换完整仓库并刷新 Gradle 依赖。当前宿主复制后仅展示一次临时剪贴板与高情商回复；关闭、打字或展示后收起即消费，跨应用不重放。直接打开回复结果，选中只回填草稿。复用客户 AI 回复配置，支持客户自绘内容及整块抽屉、错误状态和原文重试。八公开 SDK Release/R8，运行时 app:1.12。</p><a href="./downloads/oem-sdk-release-notes.md">查看版本说明</a></div>'));
    root.appendChild(el('<h2 class="section">产品摘要</h2>'));
    var grid = el('<div class="grid-3"></div>');
    [
      ['产品定义', '泡泡输入法企业版', '企业自有 App 内的定制输入法与 AI 沟通解决方案：输入内核、AI 能力、企业管理功能以 SDK 形式整体交付，品牌与功能由企业自定义。'],
      ['交付形式', 'SDK 模块化交付', 'core 基座必选，AI 回复 / 素材库 / 自动化 / 开发者按需勾选；依赖闭包与 Manifest 配置自动生成，面向 AI 编程工具一键集成。'],
      ['接入对象', '企业自有 Android 应用', 'minSdk 26，JDK 17 / Gradle 9.4.1 / AGP 9.2.0 基线；宿主 Application 一次初始化即可承载全部已选能力。'],
      ['联调方式', '企业 API 域名可配置', 'apiBaseUrl 显式指向企业自有服务端；会话数据支持 HOST_ONLY 完全宿主自留，链路可全私有化。'],
      ['输入体验', '成熟内核 · 高效输入', '拼音 / 双拼 / 五笔 / 英文等输入方案，候选优化、剪贴板、辅助输入、键盘管理、布局与皮肤定制。'],
      ['安全合规', '无广告 · 可私有部署', '无广告无推送；License 授权与能力开关可控，正式生产可关闭开发者调试入口。']
    ].forEach(function (it) {
      grid.appendChild(el('<div class="summary-item"><div class="panel-label">' + it[0] + '</div><strong>' + it[1] + '</strong><span>' + it[2] + '</span></div>'));
    });
    root.appendChild(grid);

    root.appendChild(el('<h2 class="section">能力全景</h2>'));
    root.appendChild(el(
      '<div class="card"><h3>企业定制</h3><p>输入法品牌、工具栏编排、皮肤与布局、功能开关均按模块可控；定制工具栏支持入口排序与显隐，未集成模块的入口自动隐藏。</p></div>'
    ));
    root.appendChild(el(
      '<div class="grid-2">' +
      '<div class="card"><h3>AI 回复</h3><p>键盘内极速生成、读屏回复、图片回复；OCR 原生件内置。可选 Stellar 自动化增强，未集成时自动降级为手动截屏方案。</p></div>' +
      '<div class="card"><h3>素材库</h3><p>键盘工具栏素材库入口与素材中心，服务端标准协议增删改查、后台静默同步、分类管理，面板内嵌 AI 沟通能力。</p></div>' +
      '<div class="card"><h3>会话存档（输入质检）</h3><p>企业质检会话四种分发模式（CLOUD_ONLY / HOST_ONLY / DUAL_WRITE / LOCAL_ONLY），宿主可实现回调完全自留会话数据。</p></div>' +
      '<div class="card"><h3>开发者调试</h3><p>独立 AAR 交付的调试入口：AI 模型配置、会话存档覆盖、调试存储；未集成时入口自动隐藏，生产交付建议关闭。</p></div>' +
      '</div>'
    ));
    root.appendChild(footer());
  }

  /* ---------------- 功能介绍 ---------------- */

  function featureCard(title, desc, bullets, img, caption, stacked) {
    var shot = img
      ? '<figure class="feature-shot"><img src="' + img + '" alt="' + esc(title) + '"><figcaption>' + esc(caption || '') + '</figcaption></figure>'
      : '';
    var list = (bullets || []).map(function (b) { return '<li>' + b + '</li>'; }).join('');
    return el('<div class="card feature-card' + (stacked ? ' stacked' : '') + '"><div><h3>' + esc(title) + '</h3><p>' + esc(desc) + '</p><ul>' + list + '</ul></div>' + shot + '</div>');
  }

  function pageFeatures(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">产品介绍</span><h1>功能介绍</h1>' +
      '<p class="lead">以下截图取自最新演示包，覆盖首次启用、首页模块、AI 回复、素材库与各功能子页。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">启用流程</h2>'));
    root.appendChild(featureCard(
      '首次启动直达输入法设置页',
      '宿主打开 SDK 设置入口后直达系统输入法启用页，减少用户寻找路径。',
      ['第一步：在列表中启用「泡泡输入法」', '第二步：切换当前输入法为「泡泡输入法」', '两步完成后即可在任意输入框唤起键盘'],
      './assets/feature-demo/screen-oem-setup.png',
      '系统「语言和输入法」设置页：启用并切换泡泡输入法'
    ));

    root.appendChild(el('<h2 class="section">首页与核心模块</h2>'));
    root.appendChild(featureCard(
      'OEM 中心首页',
      '输入法 App 首页聚合核心能力入口：AI 回复、素材库、键盘设置与定制工具栏等，模块未集成时对应入口自动隐藏。',
      ['首页模块按已集成能力动态展示', '设置页同步隐藏未集成模块的配置项'],
      './assets/feature-demo/screen-oem-center.png',
      'OEM 中心首页：能力入口聚合'
    ));

    root.appendChild(featureCard('三档离线语音', '可选独立 SDK；集成后默认开启 sherpa-onnx 本机听写与标点。', ['性能约 102 MB / 均衡约 243 MB / 质量约 847 MB', '工具栏和面板直接进入；设置页下载、暂停与继续', '<a href="#/voice">查看开关、模型参数和下载地址配置</a>']));
    root.appendChild(el('<h2 class="section">AI 回复与素材库</h2>'));
    root.appendChild(featureCard(
      'AI 回复抽屉',
      '键盘内 AI 回复抽屉：极速生成、读屏回复、图片回复三类能力，支持会话续写。',
      ['未集成自动化模块时自动降级为手动截屏', '大模型支持企业代理 / 直连 / 双通道三种模式'],
      './assets/feature-demo/screen-ai-reply-drawer-home.png',
      'AI 回复抽屉首页'
    ));
    root.appendChild(featureCard(
      '素材库首页',
      '素材中心按分类展示企业下发素材，后台静默同步，面板内嵌 AI 沟通能力。',
      ['服务端标准协议增删改查', '素材组支持文本 / 图片内容块组合'],
      './assets/feature-demo/screen-material-library-home.png',
      '素材库首页'
    ));

    root.appendChild(el('<h2 class="section">输入与键盘子页</h2>'));
    [
      ['布局与显示', '键盘布局、候选栏、主题外观等显示项集中配置。', './assets/feature-demo/screen-feature-layout-display.png'],
      ['按键效果', '按键音效、振动、按压反馈等手感配置。', './assets/feature-demo/screen-feature-key-effect.png'],
      ['定制工具栏', '工具栏入口排序与显隐定制，AI 回复、素材库等常用能力可固定到工具栏。', './assets/feature-demo/screen-feature-customize-toolbar.png'],
      ['辅助输入', '联想、纠错、模糊音等辅助输入能力开关。', './assets/feature-demo/screen-feature-assist-input.png'],
      ['剪贴板', '剪贴板历史管理，敏感内容可配置保留策略。', './assets/feature-demo/screen-feature-clipboard.png'],
      ['键盘管理', '输入方案启用与键盘切换管理（拼音 / 双拼 / 五笔 / 英文等）。', './assets/feature-demo/screen-feature-keyboard-manage.png']
    ].forEach(function (it) {
      root.appendChild(featureCard(it[0], it[1], [], it[2], it[0]));
    });
    root.appendChild(footer());
  }

  function pageVoice(root) { root.appendChild(el("<div><div class=\"hero\"><span class=\"tag\">可选 SDK</span><h1>三档离线语音</h1><p class=\"lead\">按需集成，加入后默认开启。本机识别与自动标点，性能、均衡、质量三档模型按需下载。</p></div><div class=\"hero-actions\"><a class=\"btn\" href=\"./downloads/oem_voice_config.json\" download>下载默认配置</a><a class=\"btn\" href=\"./downloads/oem-voice-models.json\" download>模型地址与校验清单</a><a class=\"btn\" href=\"./downloads/oem-voice-guide.md\" download>完整配置说明</a></div><p>自 SDK `1.0.0.20261008` / 输入法运行时 `app:1.9` 起，语音独立为可选 `oem-ime-sdk-voice`，提供官方 sherpa-onnx 1.13.8 离线流式识别和自动标点；不依赖 AI 回复、自动化或云端大模型。</p><h2 class=\"section\">按需集成语音 SDK</h2><p>官网模块选择器勾选“离线语音”（`voice`），会加入语音 SDK 和 sherpa 框架；未勾选时基础输入法不依赖 sherpa，语音实现、JNI、设置页和麦克风权限不进入宿主，全部语音入口自动隐藏。</p><pre><code>// 在基础输入法 core + host-bridge 的依赖之外按需添加：\nimplementation &quot;com.paopaoime.oem:oem-ime-sdk-voice:1.0.0.20261008&quot;\n// sherpa-onnx:1.13.8 由语音 SDK 传递引入，无需手工添加。</code></pre><p>完整 SDK ZIP 包含所有模块以便离线交付，Gradle 仅打包宿主实际声明的依赖。不集成语音时不要添加 voice 或 sherpa 坐标。官网 full 演示包包含语音；lite / `-PdemoVoice=false` 对照构建不包含语音。模块集成后默认开启，无需注册初始化器或额外 Manifest 开关；可用下述 JSON 将功能关闭。关闭配置保留已集成的代码和框架，减小 APK 应移除 voice 依赖。</p><div class=\"tbl-wrap\"><table class=\"tbl\"><thead><tr><th>档位</th><th>配置 ID</th><th>首次下载含共用标点</th><th>说明</th></tr></thead><tbody><tr><td>性能</td><td>`performance`</td><td>101,874,904 字节，约 102 MB</td><td>默认；中文 Zipformer CTC INT8</td></tr><tr><td>均衡</td><td>`quality`</td><td>242,880,118 字节，约 243 MB</td><td>中文 Zipformer Transducer INT8</td></tr><tr><td>质量</td><td>`quality_xl`</td><td>846,750,000 字节，约 847 MB</td><td>中文 Zipformer XL Transducer INT8，高性能设备使用</td></tr></tbody></table></div><p>三档共用 75,519,198 字节标点模型；下载体积不是运行内存，不承诺大模型在所有场景更准。模型权重不进入 APK，首次在“输入法设置 → 语音输入”主动下载，支持暂停、继续、失败重试、Range 续传、大小和 SHA-256 校验。下载成功后识别及标点在设备本地执行，语音模块不上传录音或转写正文；宿主或其他已开启模块对最终输入文字的处理仍由企业自身配置决定。</p><p>工具栏麦克风、工具面板“语音输入”和可配置的空格长按入口使用同一引擎。未下载时进入语音设置；未授权时申请 `RECORD_AUDIO`；永久拒绝提供系统设置入口，拒绝不影响普通键盘。录音时整块键盘显示波带动画和实时文字，点击“点我完成”结束并恢复皮肤。密码框不录音，切换输入框、移动光标、隐藏键盘或低内存会结束会话，迟到结果不写入新宿主。默认 180 秒录音上限、30 秒无声结束、手动结束最多等待 4 秒离线收尾。标点始终开启。</p><h2 class=\"section\">宿主配置文件</h2><p>SDK 内置默认配置；企业需要调整时，将官网 `downloads/oem_voice_config.json` 复制为宿主 `app/src/main/res/raw/oem_voice_config.json`（不放 values-zh）。同名 Android 资源由宿主覆盖 SDK；配置在进程首次使用时读取，修改后须重新构建并重启宿主和输入法进程。也可只填写要覆盖的字段，省略字段使用以下默认值。</p><p>设置 `enabled: false` 将隐藏工具栏、工具面板、定制工具栏候选项及设置页入口，并阻止录音和模型下载；普通键盘保留，已下载模型不自动删除。开关不裁剪 AAR/JNI，也不自动移除 Manifest 权限。配置 JSON 无法解析、范围越界或不支持的解码方法会禁用语音并输出 `OemVoice` 配置错误日志，不影响基础输入。</p><pre><code>{\n  &quot;schemaVersion&quot;: 1,\n  &quot;enabled&quot;: true,\n  &quot;defaultMode&quot;: &quot;performance&quot;,\n  &quot;punctuationThreads&quot;: 1,\n  &quot;maxRecordingSeconds&quot;: 180,\n  &quot;silenceTimeoutSeconds&quot;: 30,\n  &quot;finishTimeoutMs&quot;: 4000,\n  &quot;speechRmsThreshold&quot;: 0.01,\n  &quot;models&quot;: {\n    &quot;performance&quot;: {\n      &quot;numThreads&quot;: 2,\n      &quot;decodingMethod&quot;: &quot;greedy_search&quot;,\n      &quot;maxActivePaths&quot;: 4,\n      &quot;blankPenalty&quot;: 0.0,\n      &quot;endpointSilenceSeconds&quot;: 2.4,\n      &quot;endpointSpeechSilenceSeconds&quot;: 0.7,\n      &quot;endpointMaxUtteranceSeconds&quot;: 15.0\n    },\n    &quot;quality&quot;: {\n      &quot;numThreads&quot;: 2,\n      &quot;decodingMethod&quot;: &quot;greedy_search&quot;,\n      &quot;maxActivePaths&quot;: 4,\n      &quot;blankPenalty&quot;: 0.0,\n      &quot;endpointSilenceSeconds&quot;: 2.4,\n      &quot;endpointSpeechSilenceSeconds&quot;: 0.7,\n      &quot;endpointMaxUtteranceSeconds&quot;: 15.0\n    },\n    &quot;quality_xl&quot;: {\n      &quot;numThreads&quot;: 2,\n      &quot;decodingMethod&quot;: &quot;greedy_search&quot;,\n      &quot;maxActivePaths&quot;: 4,\n      &quot;blankPenalty&quot;: 0.0,\n      &quot;endpointSilenceSeconds&quot;: 2.4,\n      &quot;endpointSpeechSilenceSeconds&quot;: 0.7,\n      &quot;endpointMaxUtteranceSeconds&quot;: 15.0\n    }\n  },\n  &quot;downloadUrlTemplates&quot;: [\n    &quot;https://hf-mirror.com/{repo}/resolve/{revision}/{name}&quot;,\n    &quot;https://huggingface.co/{repo}/resolve/{revision}/{name}&quot;\n  ],\n  &quot;fileUrls&quot;: {}\n}</code></pre><div class=\"tbl-wrap\"><table class=\"tbl\"><thead><tr><th>参数</th><th>默认值</th><th>有效范围 / 用途</th></tr></thead><tbody><tr><td>`enabled`</td><td>`true`</td><td>语音总开关</td></tr><tr><td>`defaultMode`</td><td>`performance`</td><td>performance / quality / quality_xl；仅影响尚未选档的新安装，用户选择优先</td></tr><tr><td>`models.&lt;id&gt;.numThreads`</td><td>`2`</td><td>1–8，CPU ASR 线程；三档可分别调优</td></tr><tr><td>`models.&lt;id&gt;.decodingMethod`</td><td>`greedy_search`</td><td>性能档仅 greedy_search；均衡/质量另支持 modified_beam_search</td></tr><tr><td>`models.&lt;id&gt;.maxActivePaths`</td><td>`4`</td><td>1–32；仅 modified_beam_search 时生效</td></tr><tr><td>`models.&lt;id&gt;.blankPenalty`</td><td>`0.0`</td><td>0–5，空白惩罚；非默认值需用业务语料验证</td></tr><tr><td>`models.&lt;id&gt;.endpointSilenceSeconds`</td><td>`2.4`</td><td>0.1–30 秒，未要求识别到文字的静音端点</td></tr><tr><td>`models.&lt;id&gt;.endpointSpeechSilenceSeconds`</td><td>`0.7`</td><td>0.1–10 秒，已有语音后的尾部静音</td></tr><tr><td>`models.&lt;id&gt;.endpointMaxUtteranceSeconds`</td><td>`15`</td><td>1–60 秒，最长短句；不是整段录音上限</td></tr><tr><td>`punctuationThreads`</td><td>`1`</td><td>1–4，标点 CPU 线程</td></tr><tr><td>`maxRecordingSeconds`</td><td>`180`</td><td>10–600 秒，整个录音会话上限</td></tr><tr><td>`silenceTimeoutSeconds`</td><td>`30`</td><td>5–120 秒，连续无声结束</td></tr><tr><td>`finishTimeoutMs`</td><td>`4000`</td><td>500–10000 毫秒，手动停止后的最大收尾等待</td></tr><tr><td>`speechRmsThreshold`</td><td>`0.01`</td><td>0.001–0.2，无声计时的 RMS 门限</td></tr><tr><td>`downloadUrlTemplates`</td><td>hf-mirror / Hugging Face</td><td>1–8 个 HTTPS 模板，按顺序失败回退；支持 {repo}、{revision}、{name}、{folder}</td></tr><tr><td>`fileUrls`</td><td>`{}`</td><td>按“目录/文件名”覆盖 1–8 个完整 HTTPS 下载 URL；设置后该文件仅使用指定列表</td></tr></tbody></table></div><h2 class=\"section\">更换下载地址</h2><p>保持默认文件清单的内容、大小和 SHA-256 不变，仅替换镜像地址。企业 CDN 若按目录存放，可将模板改成 `https://cdn.example.com/voice/{folder}/{name}`；默认镜像依次为 `https://hf-mirror.com/{repo}/resolve/{revision}/{name}` 和 `https://huggingface.co/{repo}/resolve/{revision}/{name}`。</p><p>也可逐文件覆盖，例如：</p><pre><code>{&quot;fileUrls&quot;:{&quot;quality_xl/encoder.int8.onnx&quot;:[&quot;https://cdn.example.com/voice/quality_xl/encoder.int8.onnx&quot;]}}</code></pre><p>模型清单及当前完整 URL 见 `downloads/oem-voice-models.json`。CDN 应支持 HTTPS、准确的 Content-Length 和 HTTP Range/Content-Range；不支持 Range 返回 200 时会从头下载，错误长度、HTML 或错误 SHA 均不会激活模型。禁止把任意其他模型或压缩包替换到这些地址；变更模型架构、权重或校验清单须升级 SDK。URL 可含查询参数，但不应在配置中放私钥或长期敏感凭据。</p><h2 class=\"section\">验收</h2><p>- 新装默认性能档；三档单选，共用一个下载按钮；三档首次下载和已下载切换正常。</p><p>- 暂停后继续可恢复进度；断网/错误校验显示重试；共用标点只下载一次。</p><p>- 完成下载后断网听写，实时上屏、自动标点及手动结束正常；拒绝权限/密码框不录音。</p><p>- 隐藏键盘、换宿主、移动光标后无迟到写入；无声和时限结束能恢复原皮肤。</p><p>- 独立 `Release/R8` 宿主能加载 JNI 并识别；不要删除随 AAR 提供的 sherpa consumer keep 规则。</p><p>- 未集成 voice 的宿主依赖树和 APK 无 sherpa AAR/JNI、无语音入口，普通键盘正常。</p><p>- `enabled: false` 的独立构建中所有语音入口消失，拼音与其他已授权 OEM 功能仍可用。</p></div>")); }

  /* ---------------- 功能选择 ---------------- */

  function pageModules(root) {
    var catalog = OEM.state.catalog;
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>功能选择</h1>' +
      '<p class="lead">勾选需要的模块，实时生成依赖闭包与集成所需的 Manifest meta 模板；「AI 一键安装」生成面向 AI 编程工具的集成 SOP 文档（Markdown），后续仅面向 AI 编程工具交付。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">勾选模块 <span class="hint">core 为必选基座</span></h2>'));
    var cards = el('<div class="cards"></div>');
    catalog.modules.forEach(function (m) {
      var color = OEM.modColor(m.id);
      var card = el(
        '<article class="mod-card" style="--mod-color:' + color + '">' +
        '<div class="mod-head">' +
        '<input type="checkbox" data-mod="' + m.id + '"' + (m.required ? ' checked disabled' : (OEM.state.selected.indexOf(m.id) >= 0 ? ' checked' : '')) + '>' +
        '<h3>' + esc(m.name) + '</h3>' +
        '<span class="mod-tag">' + m.id + '</span>' +
        (m.required ? '<span class="mod-tag required">必选</span>' : '') +
        '</div>' +
        '<p class="mod-desc">' + esc(m.description) + '</p>' +
        '<div class="panel-label">Artifacts（Maven 坐标）</div>' +
        (m.artifacts || []).map(function (a) { return '<div class="kv">' + esc(a) + '</div>'; }).join('') +
        '<div class="panel-label">Config Keys（开通所需 Manifest meta）</div>' +
        (m.configKeys || []).map(function (k) {
          return '<div class="kv"><b>' + esc(k.key) + '</b> · 默认 ' + esc(k.default === '' ? '未配置' : k.default) + '</div>';
        }).join('') +
        '<p class="dep-hint">依赖：' + ((m.dependsOn && m.dependsOn.length) ? m.dependsOn.map(function (d) { return '<span class="dep">' + d + '</span>'; }).join(', ') : '无') + '（勾选后自动纳入闭包）</p>' +
        (m.required ? '<p class="mod-foot">必选 · 所有模块的运行基座</p>' : '') +
        '</article>'
      );
      cards.appendChild(card);
    });
    root.appendChild(cards);

    var out = el(
      '<section class="out-panel">' +
      '<div class="out-head"><h2>已选模块及依赖闭包</h2>' +
      '<div class="out-actions">' +
      '<button class="btn ghost" id="copy-meta" type="button">复制 meta 模板</button>' +
      '<button class="btn ghost" id="copy-deps" type="button">复制 Gradle 依赖</button>' +
      '<button class="btn" id="dl-md" type="button">AI 一键安装（下载 MD）</button>' +
      '</div></div>' +
      '<div class="chips" id="closure-chips"></div>' +
      '<div class="panel-label">依赖闭包 Artifacts</div>' +
      '<div id="closure-artifacts"></div>' +
      '<div class="panel-label">集成所需 Manifest meta 模板（含中文注释）</div>' +
      '<pre class="code" id="meta-out"></pre>' +
      '</section>'
    );
    root.appendChild(out);

    function refreshOut() {
      var closure = OEM.computeClosure(catalog, OEM.state.selected);
      var chips = out.querySelector('#closure-chips');
      chips.innerHTML = '';
      closure.forEach(function (m) {
        chips.appendChild(el('<span class="chip on" style="border-color:' + OEM.modColor(m.id) + '">' + m.id + ' · ' + esc(m.name) + '</span>'));
      });
      var arts = out.querySelector('#closure-artifacts');
      arts.innerHTML = '';
      closure.forEach(function (m) {
        (m.artifacts || []).forEach(function (a) {
          arts.appendChild(el('<div class="kv">' + esc(a) + '　<span style="color:var(--text-faint)">← ' + m.id + '</span></div>'));
        });
      });
      out.querySelector('#meta-out').textContent = OEM.buildMetaTemplate(closure);
      out.querySelector('#copy-meta').onclick = function () { OEM.copyText(OEM.buildMetaTemplate(closure)); };
      out.querySelector('#copy-deps').onclick = function () { OEM.copyText(OEM.buildGradleDeps(closure)); };
      out.querySelector('#dl-md').onclick = function () {
        OEM.downloadText('paopao-oem-ai-one-click-install.md', OEM.buildOneClickMd(catalog, closure));
      };
    }

    cards.querySelectorAll('input[data-mod]').forEach(function (cb) {
      cb.addEventListener('change', function () {
        var id = cb.getAttribute('data-mod');
        if (cb.checked) {
          if (OEM.state.selected.indexOf(id) < 0) OEM.state.selected.push(id);
        } else {
          OEM.state.selected = OEM.state.selected.filter(function (x) { return x !== id; });
        }
        cb.closest('.mod-card').classList.toggle('selected', cb.checked);
        refreshOut();
      });
    });
    cards.querySelectorAll('.mod-card').forEach(function (cardEl) {
      var cb = cardEl.querySelector('input[data-mod]');
      cardEl.classList.toggle('selected', cb.checked);
    });

    refreshOut();
    root.appendChild(footer());
  }

  /* ---------------- 配置方式 ---------------- */

  function pageConfig(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>配置方式</h1>' +
      '<p class="lead">两种配置通道：Manifest meta（域名 / 开关 / 模式类）与宿主代码（初始化 / 回调类）。AI 回复、素材库、会话存档三大企业功能均已支持「域名配置 + 宿主回调」多模式分发，可按企业数据合规要求自由选择。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">域名配置（Manifest meta）</h2>'));
    root.appendChild(el(
      '<div class="card"><p>企业服务端基础域名通过 <code>com.paopaoime.oem.apiBaseUrl</code> 显式声明，AI 代理、素材同步、会话上报等全部走该域名。禁止留空，也不要回退到 <code>paopaoime</code> 域名。选择宿主回调（HOST_ONLY）模式的功能可不依赖该域名。</p></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml</h3></div>' +
      '<pre class="code">&lt;!-- 企业服务端基础域名：AI 代理 / 素材同步 / 会话上报的统一入口 --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.apiBaseUrl"\n    android:value="https://api.enterprise.example.com" /&gt;</pre></div>'
    ));

    root.appendChild(el('<h2 class="section">宿主配置（Application 初始化）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt</h3></div>' +
      '<pre class="code">class HostApp : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        // 推荐方式：Manifest meta 读取 + 宿主回调注入一次完成；分发模式用 Manifest meta 声明\n        OemImeSdk.initializeFromManifest(\n            this,\n            // 授权连接器（必填）：按标准模板接入；如我方另行交付专用实现则替换\n            licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() },\n            // 以下均选填，按需实现 / 注册：\n            sessionArchiveListener = null, // 会话存档宿主自留时实现 OemSessionArchiveListener\n            aiReplyHostDelegate = null, // AI 回复走宿主回调时实现 OemAiReplyHostDelegate\n            materialHostDelegate = null, // 企业素材走宿主回调时实现 OemMaterialHostDelegate\n            aiReplyTonePresets = null, // 宿主自定义 AI 回复语气预设列表\n            aiReplySystemPrompt = null, // 宿主自定义 AI 回复系统提示词\n            aiReplyPromptAssembler = null // 宿主完全接管 systemPrompt / userPrompt 组装\n        )\n    }\n}</pre></div>'
    ));

    root.appendChild(el('<h2 class="section">多模式分发总览</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>功能模块</th><th>分发模式 Manifest key</th><th>可选模式</th><th>宿主回调契约</th><th>详情</th></tr></thead><tbody>' +
      '<tr><td>AI 回复</td><td><code>com.paopaoime.oem.aiReplyDispatchMode</code></td><td>CLOUD_ONLY（默认）/ HOST_ONLY / DUAL_WRITE</td><td><code>OemAiReplyHostDelegate</code></td><td><a href="#/ai-reply">AI回复页</a></td></tr>' +
      '<tr><td>素材库（企业素材）</td><td><code>com.paopaoime.oem.materialDispatchMode</code></td><td>LOCAL_ONLY / CLOUD_ONLY（默认）/ HOST_ONLY / DUAL_WRITE</td><td><code>OemMaterialHostDelegate</code> + 推送接口</td><td><a href="#/material">素材库页</a></td></tr>' +
      '<tr><td>会话存档（输入质检）</td><td><code>com.paopaoime.oem.sessionArchiveMode</code></td><td>LOCAL_ONLY / CLOUD_ONLY（默认）/ HOST_ONLY / DUAL_WRITE</td><td><code>OemSessionArchiveListener</code></td><td><a href="#/session-archive">会话存档页</a></td></tr>' +
      '</tbody></table></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>配置解析优先级（高 → 低）</h3><ol>' +
      '<li>开发者调试覆盖（集成 developer 模块后可在调试入口临时切换，仅调试用）；</li>' +
      '<li><code>OemImeSdk.initialize</code> 传入的 <code>OemSdkConfig</code> 运行时参数（含宿主回调委托对象）；</li>' +
      '<li>AndroidManifest meta-data（模式字符串）；</li>' +
      '<li>默认值 CLOUD_ONLY。</li>' +
      '</ol><p>宿主回调委托对象（aiReplyHostDelegate / materialHostDelegate / sessionArchiveListener）只能通过宿主代码注入，也可在初始化后通过 <code>OemImeSdk.setAiReplyHostDelegate(...)</code> / <code>OemImeSdk.setMaterialHostDelegate(...)</code> 动态注册或注销。</p></div>'
    ));
    root.appendChild(footer());
  }

  /* ---------------- AI回复 ---------------- */

  function pageAiReply(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>AI回复</h1>' +
      '<p class="lead">键盘内 AI 回复能力：极速生成、读屏回复、图片回复。支持「域名配置」与「宿主回调」两种对接通道，按本页 SOP 依次完成：配置方式选择 → API 对接 / 宿主回调对接 → 调试台联调 → 验收。</p>' +
      '<div class="hero-actions"><button class="btn" id="open-ai-debug" type="button">打开 AI 回复调试台</button></div></div>'
    ));

    root.appendChild(el('<h2 class="section">① 配置方式选择</h2>'));
    root.appendChild(modeCards([
      ['CLOUD_ONLY（默认）· 域名配置', 'SDK 通过企业 apiBaseUrl 域名调用大模型网关（代理 / 直连 / 双通道，见 ②）。适合企业已有模型网关域名、希望统一服务端管控的场景。'],
      ['HOST_ONLY · 宿主回调', 'SDK 完成截屏后交给宿主，宿主调用自己的大模型，再通过回调把回复回传给 SDK 显示。Prompt 与语气可由宿主自定义或完全接管（见「Prompt 与语气预留」），全程不依赖 apiBaseUrl。'],
      ['DUAL_WRITE · 双通道容错', '优先宿主回调；宿主未注册回调、拒绝处理、超时或失败时自动回落域名配置通道，保证可用性。'],
      ['模式如何选择', '大模型调用必须留在宿主 App 内（数据不出宿主）→ HOST_ONLY；已有企业模型网关域名 → CLOUD_ONLY；既要宿主自控又要兜底 → DUAL_WRITE。']
    ]));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml（模式声明，选填，默认 CLOUD_ONLY）</h3></div>' +
      '<pre class="code">&lt;!-- AI 回复分发模式：CLOUD_ONLY / HOST_ONLY / DUAL_WRITE，默认 CLOUD_ONLY --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.aiReplyDispatchMode"\n    android:value="HOST_ONLY" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>配置解析优先级：<code>OemSdkConfig.aiReplyDispatchMode</code>（代码）＞ Manifest meta ＞ 默认 CLOUD_ONLY。选择 HOST_ONLY / DUAL_WRITE 时必须实现并注册 <code>OemAiReplyHostDelegate</code>（见 ③），否则 HOST_ONLY 将向用户提示失败、DUAL_WRITE 将自动走域名通道。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">② API 对接（域名配置 · CLOUD_ONLY / DUAL_WRITE 兜底通道）</h2>'));
    root.appendChild(el(
      '<div class="grid-3">' +
      '<div class="card"><h3>proxy（默认）</h3><p>请求经企业 <code>apiBaseUrl</code> 代理转发，密钥与模型由企业网关管理，推荐生产使用。</p></div>' +
      '<div class="card"><h3>direct</h3><p>端侧直连 OpenAI 兼容接口，需配置 baseUrl / apiKey / model 三项。</p></div>' +
      '<div class="card"><h3>dual</h3><p>直连优先、代理兜底：直连失败自动回退企业代理通道。</p></div>' +
      '</div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>direct / dual 模式所需 Manifest meta</h3></div>' +
      '<pre class="code">&lt;!-- AI 运行模式：proxy / direct / dual，默认 proxy --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.aiMode" android:value="direct" /&gt;\n&lt;!-- 直连 OpenAI 兼容接口的基础地址 --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.aiOpenAiBaseUrl" android:value="https://api.openai.com/v1" /&gt;\n&lt;!-- 直连接口密钥 --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.aiOpenAiApiKey" android:value="sk-xxxx" /&gt;\n&lt;!-- 直连接口模型名 --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.aiOpenAiModel" android:value="gpt-4o-mini" /&gt;</pre></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 宿主回调对接（HOST_ONLY / DUAL_WRITE）</h2>'));
    root.appendChild(el(
      '<div class="card"><h3>交互流程</h3><ol>' +
      '<li>用户在键盘内触发 AI 回复，SDK 完成截屏（自动化或手动 MediaProjection）与系统 / 用户 Prompt 组装；</li>' +
      '<li>SDK 调用宿主实现的 <code>OemAiReplyHostDelegate.onGenerateReplies(request, callback)</code>，把 <code>OemAiReplyRequest</code>（截屏 Base64 + Prompt + 采样参数）交给宿主；</li>' +
      '<li>宿主把截图还原为图片，连同 Prompt 调用企业自己的大模型；</li>' +
      '<li>生成完成后宿主必须调用一次 <code>callback.onResult(OemAiReplyResult)</code>（成功或失败都要回调，requestId 保持一致）；</li>' +
      '<li>SDK 收到回复列表后在 AI 回复面板展示；单次等待上限 90 秒，超时按宿主通道失败处理。</li>' +
      '</ol></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 初始化时注册（推荐）</h3></div>' +
      '<pre class="code">OemImeSdk.initialize(\n    this,\n    OemSdkConfig(\n        customerId = "your_customer_id", // 必填：填写我方交付的企业码\n        appKey = "your_customer_id", // 必填：当前与企业码同值，直接填写同一企业码\n        channel = "release", // 默认 release；无特殊约定保持默认值\n        licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() }, // 必填：按标准模板接入授权连接器\n        // AI 回复分发模式：HOST_ONLY 完全宿主回调；DUAL_WRITE 宿主优先、域名兜底\n        aiReplyDispatchMode = OemAiReplyDispatchMode.HOST_ONLY,\n        // AI 回复宿主回调委托（HOST_ONLY / DUAL_WRITE 必填）\n        aiReplyHostDelegate = OemAiReplyHostDelegate { request, callback -&gt;\n            // 1. 取出 SDK 组装好的上下文：截屏(Base64 JPEG) + 系统/用户 Prompt\n            // 2. 交给企业自己的大模型网关（宿主自控，SDK 不连接任何大模型）\n            yourLlmGateway.generateReplies(\n                images = request.screenshots.map { Base64.decode(it.imageBase64, Base64.DEFAULT) },\n                systemPrompt = request.systemPrompt,\n                userPrompt = request.userPrompt,\n                replyCount = request.replyCount\n            ) { replies, error -&gt;\n                // 3. 无论成功失败都必须回调一次；requestId 必须原样带回\n                callback.onResult(\n                    OemAiReplyResult(\n                        requestId = request.requestId,\n                        success = error == null,\n                        replies = replies,\n                        errorMessage = error?.message,\n                        modelName = "enterprise-gpt" // 选填：面板展示用\n                    )\n                )\n            }\n            true // 返回 true = 已接受请求，稍后异步回调；返回 false = 拒绝处理\n        }\n    )\n)</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>动态注册 / 切换（可选，运行期生效）</h3></div>' +
      '<pre class="code">// 初始化之后也可随时注册 / 注销委托、切换模式\nOemImeSdk.setAiReplyHostDelegate { request, callback -&gt; /* 同上 */ }\nOemImeSdk.setAiReplyDispatchMode(OemAiReplyDispatchMode.DUAL_WRITE)\n// 查询当前状态\nval registered = OemImeSdk.isAiReplyHostDelegateRegistered()\nval mode = OemImeSdk.getAiReplyDispatchMode()</pre></div>'
    ));

    root.appendChild(el('<h3 class="section">Prompt 与语气预留（宿主自定义）</h3>'));
    root.appendChild(el(
      '<div class="card"><p>SDK 内置的 Prompt 模板与语气仅为<b>默认兜底</b>，三个预留位均面向宿主 / 第三方开放，优先级：宿主预留 ＞ 开发者模块配置 ＞ SDK 内置。</p><ul>' +
      '<li><code>aiReplyTonePresets</code>：自定义语气列表（label + promptHint），AI 面板语气选项随之变化；</li>' +
      '<li><code>aiReplySystemPrompt</code>：自定义系统提示词（大模型角色与输出约束）；</li>' +
      '<li><code>aiReplyPromptAssembler</code>：完全接管 systemPrompt / userPrompt 组装，SDK 每次生成前把截屏 / OCR / 语气上下文交给宿主。</li>' +
      '</ul><p>以上配置对三种分发模式均生效：HOST_ONLY 时宿主回调收到的就是自定义后的 Prompt；CLOUD_ONLY 时走域名通道的也是自定义后的 Prompt。</p></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · Prompt / 语气预留示例</h3></div>' +
      '<pre class="code">OemSdkConfig(\n    customerId = "your_customer_id",\n    appKey = "your_app_key",\n    channel = "release",\n    licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() },\n    // 选填 1：自定义语气列表（面板选项 + tonePromptHint 一并生效）\n    aiReplyTonePresets = listOf(\n        OemDeveloperAiTonePreset(label = "客服话术", promptHint = "使用企业客服标准话术，先致意再答复。"),\n        OemDeveloperAiTonePreset(label = "技术严谨", promptHint = "术语准确、结论先行，不含寒暄。")\n    ),\n    // 选填 2：自定义系统提示词\n    aiReplySystemPrompt = "你是本企业 IM 的回复助手，只输出可直接发送的中文回复。",\n    // 选填 3：完全接管 Prompt 组装（注册后 SDK 不再使用内置模板）\n    aiReplyPromptAssembler = OemAiReplyPromptAssembler { ctx -&gt;\n        OemAiReplyPromptPair(\n            systemPrompt = "你是本企业 IM 的回复助手，语气要求：${ctx.toneLabel}。",\n            userPrompt = buildString {\n                append("屏幕内容：${ctx.ocrText}\\n")\n                append("截屏张数：${ctx.screenshotCount}\\n")\n                append("语气提示：${ctx.tonePromptHint}\\n")\n                append("请生成 ${ctx.replyCount} 条可直接发送的回复。")\n            }\n        )\n    }\n)</pre></div>'
    ));
    root.appendChild(el('<div class="panel-label">OemAiReplyPromptContext 字段说明（SDK → 宿主组装器）</div>'));
    root.appendChild(fieldTable([
      ['generateMode', 'String', true, '生成模式：FAST_LLM / OCR / IMAGE_REPLY / PASTE'],
      ['toneKey', 'String', true, '用户选择的语气 key（如 tone_0）'],
      ['toneLabel', 'String', true, '用户选择的语气展示名（宿主自定义语气时为宿主 label）'],
      ['tonePromptHint', 'String', true, '当前语气的 Prompt 提示语（宿主自定义语气时为宿主 promptHint）'],
      ['ocrText', 'String', true, '屏幕 OCR 文本（可能为空字符串）'],
      ['screenshotCount', 'Int', true, '本次可用截屏张数（可能为 0）'],
      ['replyCount', 'Int', true, '期望回复条数（建议值）'],
      ['hostApplicationId', 'String', true, '宿主应用包名']
    ]));

    root.appendChild(el('<div class="panel-label">OemAiReplyRequest 字段说明（SDK → 宿主）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '本次生成请求唯一 ID；回传结果时必须保持一致，用于幂等与日志串联'],
      ['sessionId', 'String', true, '本次 AI 回复面板会话 ID'],
      ['generateMode', 'String', true, '生成模式：FAST_LLM 极速回复 / OCR 读屏回复 / IMAGE_REPLY 图片回复 / PASTE 智能改写，可按模式差异化处理'],
      ['toneKey', 'String', true, '语气 key：friendly / concise / professional / formal'],
      ['toneLabel', 'String', true, '语气中文展示名'],
      ['systemPrompt', 'String', true, 'SDK 组装的系统提示词，可直接透传给大模型'],
      ['userPrompt', 'String', true, 'SDK 组装的用户提示词，可直接透传给大模型'],
      ['ocrText', 'String', true, '屏幕 OCR 文本（可能为空字符串；OCR 模式下为主要上下文）'],
      ['screenshots', 'List<OemAiReplyScreenshot>', true, '截屏列表（可能为空；FAST_LLM 模式可能含多张连拍帧）。每张含 imageBase64（JPEG Base64，不含 data: 前缀）/ mimeType（固定 image/jpeg）/ width / height'],
      ['replyCount', 'Int', true, '期望回复条数（建议值，宿主可多可少）'],
      ['temperature', 'Double', true, '采样参数 temperature 建议值'],
      ['topP', 'Double', true, '采样参数 topP 建议值'],
      ['maxTokens', 'Int', true, '最大 token 数建议值'],
      ['timestamp', 'Long', true, '请求发起时间（毫秒时间戳）'],
      ['hostApplicationId', 'String', true, '宿主应用包名']
    ]));
    root.appendChild(el('<div class="panel-label">OemAiReplyResult 字段说明（宿主 → SDK）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '必须与请求中的 requestId 保持一致'],
      ['success', 'Boolean', true, '是否生成成功；false 时 SDK 使用 errorMessage 向用户提示失败'],
      ['replies', 'List<String>', true, '大模型生成的回复文案列表（success=true 时必填，至少一条）'],
      ['errorMessage', 'String?', false, '失败原因（success=false 时返回）'],
      ['modelName', 'String?', false, '宿主侧模型名称，用于面板展示与统计']
    ]));

    root.appendChild(el('<h2 class="section">Stellar 自动化（可选增强 · 仅影响截屏方式）</h2>'));
    root.appendChild(el(
      '<div class="card"><h3>定位与降级</h3><p>自动化当前仅服务于 AI 回复（读屏回复链路）：集成 automation 模块后走 Stellar 高权限执行链路自动截屏；未集成时自动降级为手动截屏（MediaProjection）方案，不会报错。截屏方式与分发模式正交：无论 CLOUD_ONLY 还是 HOST_ONLY，拿到的都是截屏 + Prompt。</p></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>集成与授权步骤</h3><ol>' +
      '<li>功能选择中勾选 <code>automation</code>，依赖闭包自动包含 stellar 全系列 artifact。</li>' +
      '<li>Manifest 配置 <code>com.paopaoime.oem.enableAutomation=true</code> 与 <code>roro.stellar.permissions</code>（需 <code>tools:replace</code> 覆盖）。</li>' +
      '<li>宿主申请 <code>android.permission.WRITE_SECURE_SETTINGS</code>：adb shell pm grant &lt;包名&gt; android.permission.WRITE_SECURE_SETTINGS。</li>' +
      '<li>在 <code>OemImeSdk.initialize</code> 之后调用 <code>OemHostRuntime.initialize(context)</code>。</li>' +
      '<li>安装并启动 stellar-manager companion（随交付包提供）。</li>' +
      '<li>R8/混淆保留：<code>org.fcitx.**</code>、<code>com.paopaoime.**</code>、<code>roro.stellar.**</code>、AIDL 接口与 Kotlin metadata。</li>' +
      '</ol></div>'
    ));

    root.appendChild(el('<h2 class="section">其他自动化对接方式</h2>'));
    root.appendChild(el(
      '<div class="card"><h3>滑屏事件宿主回调（双写）</h3><p>客户方不通过 ADB 或 Stellar 执行自动化时，可注册滑屏宿主回调，由宿主使用自有自动化能力（无障碍等）执行「AI 回复-极速生成」的连续滑屏。SDK 将滑屏动作抽象为标准事件字段通知宿主，宿主执行完成后回写结果。</p></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>分发模式与回落规则</h3><p>Manifest key：<code>com.paopaoime.oem.swipeDispatchMode</code>，可选值：</p><ul>' +
      '<li><code>SDK_ONLY</code>：仅 SDK 自身执行（无线 ADB / Stellar 特权进程 <code>input swipe</code>），不通知宿主；</li>' +
      '<li><code>HOST_ONLY</code>：仅宿主回调执行，SDK 把滑屏事件抽象为 <code>OemSwipeRequest</code> 通知宿主；</li>' +
      '<li><code>DUAL_WRITE</code>（默认）：宿主优先接管；宿主未注册、拒绝或 5 秒超时未回写时，自动回落 SDK 自身 <code>input swipe</code> 通道，保证极速生成连续滑屏不中断。</li>' +
      '</ul><p>配置解析优先级（高→低）：<code>OemImeSdk.setSwipeDispatchMode</code> 运行时覆盖 ＞ <code>OemSdkConfig.swipeDispatchMode</code> ＞ Manifest meta ＞ 默认 <code>DUAL_WRITE</code>。</p></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>注册方式（二选一）</h3></div>' +
      '<pre class="code">// 方式一：动态注册（初始化之后也可随时注册 / 注销，运行期生效）\nOemImeSdk.setSwipeHostDelegate { request, callback -&gt;\n    // 返回 true = 宿主接管本次滑屏；返回 false = 不接管，SDK 按分发模式回落\n    hostAutomation.performSwipe(request) { success, error -&gt;\n        // 执行完成后必须回写一次；requestId 原样带回\n        callback.onResult(OemSwipeResult(request.requestId, success, error))\n    }\n    true\n}\n\n// 方式二：初始化时在 OemSdkConfig 中传入 swipeHostDelegate\nOemImeSdk.initialize(\n    this,\n    OemSdkConfig(\n        customerId = "your_customer_id",\n        appKey = "your_app_key",\n        channel = "release",\n        licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() },\n        swipeDispatchMode = OemSwipeDispatchMode.DUAL_WRITE,\n        swipeHostDelegate = OemSwipeHostDelegate { request, callback -&gt; /* 同上 */ true }\n    )\n)</pre></div>'
    ));
    root.appendChild(el('<div class="panel-label">OemSwipeRequest 字段说明（SDK → 宿主）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '本次滑屏唯一标识，回写结果时原样携带'],
      ['scene', 'String', true, '场景标识，极速生成为 AI_FAST_GENERATE'],
      ['direction', 'String', true, '手势方向，DOWNWARD_DRAG（向下拖动 = 内容上翻）'],
      ['startX', 'Int', true, '起点 X 坐标，语义同 adb shell input swipe'],
      ['startY', 'Int', true, '起点 Y 坐标'],
      ['endX', 'Int', true, '终点 X 坐标'],
      ['endY', 'Int', true, '终点 Y 坐标'],
      ['screenWidth', 'Int', true, '屏幕宽度，宿主可把绝对坐标换算为相对比例'],
      ['screenHeight', 'Int', true, '屏幕高度，宿主可把绝对坐标换算为相对比例'],
      ['durationMs', 'Int', true, '建议手势时长（毫秒）'],
      ['timestamp', 'Long', true, '事件时间戳（毫秒）']
    ]));
    root.appendChild(el('<div class="panel-label">OemSwipeResult 回写约定（宿主 → SDK）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '必须与请求中的 requestId 保持一致'],
      ['success', 'Boolean', true, '本次滑屏是否执行成功'],
      ['errorMessage', 'String?', false, '失败原因（success=false 时返回）']
    ]));
    root.appendChild(el(
      '<div class="card"><p>回写约定：宿主接管（返回 true）后，无论成功或失败都必须调用一次 <code>callback.onResult(OemSwipeResult)</code>；未回写时 SDK 将按超时（5 秒）回落自身执行通道。查询当前状态可用 <code>OemImeSdk.isSwipeHostDelegateRegistered()</code> 与 <code>OemImeSdk.getSwipeDispatchMode()</code>。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">④ 功能调试台</h2>'));
    root.appendChild(el(
      '<div class="card"><p>AI 回复调试台为纯前端模拟器：可生成 <code>OemAiReplyRequest</code> 示例 payload、模拟宿主回传 <code>OemAiReplyResult</code>，并演示三种分发模式下宿主通道失败时的 SDK 行为，帮助宿主开发联调前吃透回调字段。</p>' +
      '<p><button class="btn ghost small" id="open-ai-debug-2" type="button">打开 AI 回复调试台</button></p></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 验收 SOP</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>CLOUD_ONLY：配置 apiBaseUrl（及 direct/dual 参数）后，键盘内触发 AI 回复可正常出回复；</li>' +
      '<li>HOST_ONLY：宿主收到 OemAiReplyRequest（截图可解码、Prompt 非空），回调后面板展示宿主返回的回复；全程抓包确认无 apiBaseUrl 大模型请求；</li>' +
      '<li>DUAL_WRITE：宿主正常时走回调；模拟宿主返回 false / 超时后自动回落域名通道并出回复；</li>' +
      '<li>宿主返回 success=false 时，面板展示 errorMessage 文案且不崩溃；</li>' +
      '<li>使用调试台核对 request / result 字段与本页表格一致。</li>' +
      '</ul></div>'
    ));

    function openDebug() { window.OEM_DEBUG.openAiReplyConsole(); }
    root.querySelector('#open-ai-debug').addEventListener('click', openDebug);
    root.querySelector('#open-ai-debug-2').addEventListener('click', openDebug);
    root.appendChild(footer());
  }

  /* ---------------- 素材库 ---------------- */

  function pageMaterial(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>素材库</h1>' +
      '<p class="lead">键盘工具栏素材库入口与素材中心，面板内嵌 AI 沟通能力。企业集中管理素材（区别于用户本地添加的素材）支持「域名配置」与「宿主回调」两种对接通道，按本页 SOP 依次完成：配置方式选择 → API 对接 / 宿主回调对接 → 调试台联调 → 验收。</p>' +
      '<div class="hero-actions"><button class="btn" id="open-material-debug" type="button">打开素材库调试台</button></div></div>'
    ));

    root.appendChild(el('<h2 class="section">① 配置方式选择</h2>'));
    root.appendChild(modeCards([
      ['LOCAL_ONLY · 仅本地', '不拉取任何企业远程素材，素材库抽屉只显示用户本地自行添加的素材。适合暂不需要企业集中管理的场景。'],
      ['CLOUD_ONLY（默认）· 域名配置', 'SDK 周期性向企业 apiBaseUrl 域名拉取素材列表，企业在自己服务端完成素材增删改查（见 ②）。'],
      ['HOST_ONLY · 宿主回调', 'SDK 通过宿主回调拉取企业素材列表；宿主对素材的增删改通过 SDK 推送接口实时通知抽屉刷新（见 ③）。不依赖 apiBaseUrl。'],
      ['DUAL_WRITE · 双通道容错', '优先宿主回调；宿主未注册回调、拒绝处理、超时或失败时自动回落域名配置通道。']
    ]));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml（模式声明，选填，默认 CLOUD_ONLY）</h3></div>' +
      '<pre class="code">&lt;!-- 企业素材分发模式：LOCAL_ONLY / CLOUD_ONLY / HOST_ONLY / DUAL_WRITE，默认 CLOUD_ONLY --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.materialDispatchMode"\n    android:value="HOST_ONLY" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>配置解析优先级：<code>OemSdkConfig.materialDispatchMode</code>（代码）＞ Manifest meta ＞ 默认 CLOUD_ONLY。选择 HOST_ONLY / DUAL_WRITE 时必须实现并注册 <code>OemMaterialHostDelegate</code>（见 ③），否则 HOST_ONLY 拉不到企业素材、DUAL_WRITE 将自动走域名通道。</p></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml（导航模式声明，选填，默认 TYPE_ONLY）</h3></div>' +
      '<pre class="code">&lt;!-- 素材库导航模式：TYPE_ONLY / TYPE_PRIMARY_CATEGORY_SECONDARY / CATEGORY_PRIMARY_TYPE_SECONDARY / CATEGORY_ONLY --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.materialNavigationMode"\n    android:value="CATEGORY_ONLY" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p><code>materialNavigationMode</code> 只控制「素材类型」与「企业分类」的层级关系，不改变 UI 样式：第一行仍是主导航，第二行仍是较小的圆角分类条。素材类型始终由 SDK 内部控制；宿主只负责返回分类和标签。若宿主未返回任何分类，SDK 不论配置何种模式都会自动回退为 <code>TYPE_ONLY</code>。</p></div>'
    ));
    root.appendChild(el('<div class="panel-label">素材库导航模式说明</div>'));
    root.appendChild(fieldTable([
      ['TYPE_ONLY', '导航模式', true, '仅显示 SDK 内置素材类型；不展示企业分类'],
      ['TYPE_PRIMARY_CATEGORY_SECONDARY', '导航模式', true, '类型一级、分类二级；分类由宿主回调提供'],
      ['CATEGORY_PRIMARY_TYPE_SECONDARY', '导航模式', true, '分类一级、类型二级；分类为空时自动回退 TYPE_ONLY'],
      ['CATEGORY_ONLY', '导航模式', true, '仅显示企业分类，不显示类型；分类为空时自动回退 TYPE_ONLY']
    ]));

    root.appendChild(el('<h2 class="section">② API 对接（域名配置 · CLOUD_ONLY / DUAL_WRITE 兜底通道）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>标准协议（{api} = apiBaseUrl）</h3></div>' +
      '<pre class="code">GET    {api}/api/v1/material-library/attachments?customerId=&amp;channel=\nPOST   {api}/api/v1/material-library/attachments          （multipart 上传附件）\nGET    {api}/api/v1/material-library/materials?customerId=&amp;channel=\nPOST   {api}/api/v1/material-library/materials            （新建 / 更新素材组）\nDELETE {api}/api/v1/material-library/materials/{materialId}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>素材组示例（分组显示：文本块 + 图片块）</h3></div>' +
      '<pre class="code">{\n  "customerId": "demo-enterprise",\n  "channel": "release",\n  "materialId": "mat-demo-001",\n  "title": "企业欢迎素材",\n  "remark": "首启欢迎",\n  "parts": [\n    { "partType": "text", "title": "欢迎语", "content": "你好，欢迎使用企业输入法" },\n    { "partType": "image", "title": "活动图", "imageUrl": "https://static.example.com/banner.png" }\n  ]\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>同步策略</h3><ul>' +
      '<li>端侧后台静默同步，弱网自动重试，不阻塞键盘使用；</li>' +
      '<li>素材按 customerId + channel 隔离，支持多企业多渠道；</li>' +
      '<li>素材库依赖 AI 回复模块（面板内嵌 AI 沟通），勾选素材库时自动纳入闭包。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 宿主回调对接（HOST_ONLY / DUAL_WRITE）</h2>'));
    root.appendChild(el(
      '<div class="card"><h3>交互流程（拉取 + 推送双向）</h3><ol>' +
      '<li><b>拉取（查）</b>：素材抽屉打开、周期同步时，SDK 调用宿主实现的 <code>OemMaterialHostDelegate.fetchRemoteMaterials(request, callback)</code>，宿主查询最新企业素材列表后通过 <code>callback.onResult(OemMaterialFetchResult)</code> 回传（全量语义，SDK 整体替换本地缓存）；</li>' +
      '<li><b>分类 / 标签（查 + 管）</b>：当导航模式包含企业分类，或开发者页打开分类 / 标签管理时，SDK 调用 <code>OemMaterialTaxonomyHostDelegate.fetchMaterialTaxonomy(...)</code> 拉取分类 / 标签；用户在抽屉内新增、编辑、删除分类 / 标签时，SDK 再调用 <code>mutateMaterialTaxonomy(...)</code> 交给宿主落库；</li>' +
      '<li><b>推送（增 / 改 / 删）</b>：企业后台变更素材后，宿主调用 SDK 推送接口实时通知：<code>OemImeSdk.notifyRemoteMaterialUpserted(item)</code>（新增 / 更新）、<code>OemImeSdk.notifyRemoteMaterialRemoved(materialId)</code>（删除）、<code>OemImeSdk.invalidateRemoteMaterialCatalog()</code>（全量失效并重新拉取），抽屉即时刷新；</li>' +
      '<li>宿主通道单次拉取等待上限 30 秒，超时按失败处理（DUAL_WRITE 自动回落域名通道）。</li>' +
      '</ol></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>导航模式与宿主回调的对应关系</h3><ul>' +
      '<li><code>TYPE_ONLY</code>：不依赖分类 / 标签回调，未注册 <code>OemMaterialTaxonomyHostDelegate</code> 也能正常工作；</li>' +
      '<li><code>TYPE_PRIMARY_CATEGORY_SECONDARY</code> / <code>CATEGORY_PRIMARY_TYPE_SECONDARY</code> / <code>CATEGORY_ONLY</code>：要看到企业分类，宿主必须注册 <code>OemMaterialTaxonomyHostDelegate</code> 并在 <code>fetchMaterialTaxonomy</code> 中返回 <code>categories</code>；</li>' +
      '<li><code>tags</code> 只用于搜索命中和管理，不作为导航展示；</li>' +
      '<li>即使配置了分类优先或仅分类，只要 <code>categories</code> 为空，SDK 仍自动回退为 <code>TYPE_ONLY</code>，避免首页出现空导航。</li>' +
      '</ul></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 初始化时注册拉取委托（推荐）</h3></div>' +
      '<pre class="code">OemImeSdk.initialize(\n    this,\n    OemSdkConfig(\n        customerId = "your_customer_id", // 必填：正式值由我方交付\n        appKey = "your_app_key", // 必填：正式值由我方交付\n        channel = "release", // 必填：渠道标识\n        licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() }, // 必填：授权连接器（占位）\n        // 企业素材分发模式：HOST_ONLY 完全宿主回调；DUAL_WRITE 宿主优先、域名兜底\n        materialDispatchMode = OemMaterialDispatchMode.HOST_ONLY,\n        // 首页导航模式：仅控制类型 / 分类层级，不改变样式\n        materialNavigationMode = OemMaterialNavigationMode.CATEGORY_ONLY,\n        // 素材库宿主回调委托（HOST_ONLY / DUAL_WRITE 必填）\n        materialHostDelegate = OemMaterialHostDelegate { request, callback -&gt;\n            yourMaterialRepo.queryLatest(limit = request.limit) { items, error -&gt;\n                callback.onResult(\n                    OemMaterialFetchResult(\n                        requestId = request.requestId,\n                        success = error == null,\n                        materials = items.map { it.toOemRemoteMaterial() },\n                        errorMessage = error?.message\n                    )\n                )\n            }\n            true\n        },\n        // 分类 / 标签宿主回调委托：控制首页分类导航、搜索标签命中、开发者页管理\n        materialTaxonomyHostDelegate = object : OemMaterialTaxonomyHostDelegate {\n            override fun fetchMaterialTaxonomy(request: OemMaterialTaxonomyFetchRequest, callback: OemMaterialTaxonomyFetchCallback): Boolean {\n                yourTaxonomyRepo.queryAll { categories, tags, error -&gt;\n                    callback.onResult(\n                        OemMaterialTaxonomyFetchResult(\n                            requestId = request.requestId,\n                            success = error == null,\n                            categories = categories,\n                            tags = tags,\n                            errorMessage = error?.message\n                        )\n                    )\n                }\n                return true\n            }\n\n            override fun mutateMaterialTaxonomy(request: OemMaterialTaxonomyMutationRequest, callback: OemMaterialTaxonomyMutationCallback): Boolean {\n                yourTaxonomyRepo.mutate(request) { savedItem, error -&gt;\n                    callback.onResult(\n                        OemMaterialTaxonomyMutationResult(\n                            requestId = request.requestId,\n                            success = error == null,\n                            item = savedItem,\n                            errorMessage = error?.message\n                        )\n                    )\n                }\n                return true\n            }\n        }\n    )\n)</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>运行时切换（可选）</h3></div>' +
      '<pre class="code">// 初始化之后也可运行时切换导航模式或替换分类 / 标签委托\nOemImeSdk.setMaterialNavigationMode(OemMaterialNavigationMode.TYPE_PRIMARY_CATEGORY_SECONDARY)\nOemImeSdk.setMaterialTaxonomyHostDelegate(yourTaxonomyDelegate)\n\n// 查询当前状态\nval mode = OemImeSdk.getMaterialNavigationMode()\nval taxonomyRegistered = OemImeSdk.isMaterialTaxonomyHostDelegateRegistered()</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>企业后台变更后推送给 SDK（增 / 改 / 删）</h3></div>' +
      '<pre class="code">// 新增或更新一条企业素材（增 / 改）：同 materialId 覆盖，抽屉按 updatedAt 倒序展示\nOemImeSdk.notifyRemoteMaterialUpserted(\n    OemRemoteMaterialItem(\n        materialId = "mat-001",              // 必填：素材唯一 ID\n        sourceLabel = "企业下发",             // 选填：来源标签\n        parts = listOf(\n            OemRemoteMaterialPart.Text(partId = "p1", text = "你好，欢迎使用企业输入法"),\n            OemRemoteMaterialPart.Attachment(\n                partId = "p2",\n                displayName = "活动图.png",\n                mimeType = "image/png",\n                attachmentType = "IMAGE",     // IMAGE / VIDEO / DOCUMENT\n                downloadUrl = "https://static.example.com/banner.png"\n            )\n        )\n    )\n)\n// 删除一条企业素材（删）\nOemImeSdk.notifyRemoteMaterialRemoved("mat-001")\n// 批量变更不想逐条推送时：全量失效，SDK 将重新调用 fetchRemoteMaterials 拉取（查）\nOemImeSdk.invalidateRemoteMaterialCatalog()</pre></div>'
    ));
    root.appendChild(el('<div class="panel-label">OemMaterialTaxonomyFetchResult 字段说明（宿主 → SDK）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '必须与请求中的 requestId 保持一致'],
      ['success', 'Boolean', true, '是否拉取成功；false 时 SDK 保留现有分类状态'],
      ['categories', 'List<OemMaterialTaxonomyItem>', true, '企业分类列表；驱动首页分类导航与分类过滤'],
      ['tags', 'List<OemMaterialTaxonomyItem>', true, '企业标签列表；仅用于搜索命中与开发者页管理'],
      ['errorMessage', 'String?', false, '失败原因（success=false 时返回）']
    ]));
    root.appendChild(el('<div class="panel-label">OemMaterialTaxonomyMutationRequest 字段说明（SDK → 宿主）</div>'));
    root.appendChild(fieldTable([
      ['action', 'OemMaterialTaxonomyMutationAction', true, 'CREATE / UPDATE / DELETE'],
      ['target', 'OemMaterialTaxonomyTarget', true, 'CATEGORY / TAG'],
      ['item', 'OemMaterialTaxonomyItem?', false, 'CREATE / UPDATE 时传入分类或标签条目'],
      ['taxonomyId', 'String?', false, 'DELETE 时传入被删除条目的 ID'],
      ['hostApplicationId', 'String', true, '宿主应用包名']
    ]));
    root.appendChild(el('<div class="panel-label">OemMaterialTaxonomyItem 字段说明（分类 / 标签实体）</div>'));
    root.appendChild(fieldTable([
      ['taxonomyId', 'String', true, '分类或标签唯一 ID'],
      ['name', 'String', true, '展示名称；分类显示在导航条中，标签仅参与搜索命中'],
      ['sort', 'Int', false, '排序值，数值越小越靠前，默认 0'],
      ['enabled', 'Boolean', false, '是否启用；默认 true']
    ]));
    root.appendChild(el('<div class="panel-label">OemMaterialFetchRequest 字段说明（SDK → 宿主）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '本次拉取请求唯一 ID；回传结果时必须保持一致'],
      ['limit', 'Int', true, '期望拉取的素材条数上限（建议值，宿主可多可少）'],
      ['hostApplicationId', 'String', true, '宿主应用包名'],
      ['timestamp', 'Long', true, '请求发起时间（毫秒时间戳）']
    ]));
    root.appendChild(el('<div class="panel-label">OemMaterialFetchResult 字段说明（宿主 → SDK）</div>'));
    root.appendChild(fieldTable([
      ['requestId', 'String', true, '必须与请求中的 requestId 保持一致'],
      ['success', 'Boolean', true, '是否拉取成功；false 时 SDK 使用 errorMessage 记录失败'],
      ['materials', 'List<OemRemoteMaterialItem>', true, '最新企业素材列表（全量语义，SDK 用该列表整体替换本地缓存）'],
      ['errorMessage', 'String?', false, '失败原因（success=false 时返回）']
    ]));
    root.appendChild(el('<div class="panel-label">OemRemoteMaterialItem 字段说明（素材实体）</div>'));
    root.appendChild(fieldTable([
      ['materialId', 'String', true, '素材唯一 ID，增删改匹配依据'],
      ['sourceLabel', 'String', false, '来源标签，默认「企业下发」'],
      ['createdAt', 'Long', false, '创建时间（毫秒时间戳），默认当前时间'],
      ['updatedAt', 'Long', false, '最近更新时间（毫秒时间戳），抽屉按该字段倒序展示'],
      ['parts', 'List<OemRemoteMaterialPart>', true, '素材内容块列表，建议至少一项']
    ]));
    root.appendChild(el('<div class="panel-label">OemRemoteMaterialPart 字段说明（内容块）</div>'));
    root.appendChild(fieldTable([
      ['Text.partId', 'String', true, '内容块 ID（素材内唯一）'],
      ['Text.text', 'String', true, '文本内容'],
      ['Attachment.partId', 'String', true, '内容块 ID（素材内唯一）'],
      ['Attachment.displayName', 'String', true, '附件展示名'],
      ['Attachment.mimeType', 'String', true, 'MIME 类型，如 image/png、video/mp4、application/pdf'],
      ['Attachment.attachmentType', 'String', true, '附件类别：IMAGE / VIDEO / DOCUMENT'],
      ['Attachment.downloadUrl', 'String', true, '附件下载地址（https URL 或宿主 FileProvider 的 content:// URI）'],
      ['Attachment.fileSizeBytes', 'Long', false, '文件大小（字节），默认 0']
    ]));

    root.appendChild(el('<h2 class="section">④ 功能调试台</h2>'));
    root.appendChild(el(
      '<div class="card"><p>素材库调试台面向域名配置通道：可配置后端 API 地址，在线完成附件上传、素材组增删改查与 JSON 查看，验证企业服务端协议实现是否正确。宿主回调通道请在宿主 App 内按 ③ 联调。</p>' +
      '<p><button class="btn ghost small" id="open-material-debug-2" type="button">打开素材库调试台</button></p></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 验收 SOP</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>LOCAL_ONLY：抽屉无企业素材分区，仅显示本地添加素材；</li>' +
      '<li>CLOUD_ONLY：使用调试台完成素材 CRUD，端侧抽屉静默同步后可见；</li>' +
      '<li>HOST_ONLY：抽屉打开触发 fetchRemoteMaterials 且宿主回传列表可见；调用 notifyRemoteMaterialUpserted / Removed 后抽屉即时增删；抓包确认无 apiBaseUrl 素材请求；</li>' +
      '<li>导航模式验收：配置 <code>CATEGORY_ONLY</code> 或分类优先模式并返回至少一条 <code>categories</code> 后，首页出现分类导航；若宿主返回空分类，则自动回退为 <code>TYPE_ONLY</code>；</li>' +
      '<li>标签验收：搜索未输入关键字时不展示素材；输入命中标签关键字后可返回对应素材，标签本身不作为导航展示；</li>' +
      '<li>DUAL_WRITE：宿主正常时走回调；模拟宿主返回 false / 超时后回落域名通道仍可见素材；</li>' +
      '<li>附件素材（图片 / 视频 / 文档）点击可正常预览或下载。</li>' +
      '</ul></div>'
    ));

    function openDebug() { window.OEM_DEBUG.openMaterialConsole(); }
    root.querySelector('#open-material-debug').addEventListener('click', openDebug);
    root.querySelector('#open-material-debug-2').addEventListener('click', openDebug);
    root.appendChild(footer());
  }

  /* ---------------- 会话存档 ---------------- */

  function pageSessionArchive(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>会话存档</h1>' +
      '<p class="lead">输入质检会话的归档与分发：支持「域名配置」与「宿主回调」两种对接通道。按本页 SOP 依次完成：配置方式选择 → API 对接 / 宿主回调对接 → 调试台联调 → 验收。</p>' +
      '<div class="hero-actions"><button class="btn" id="open-session-debug" type="button">打开会话存档调试台</button></div></div>'
    ));

    root.appendChild(el('<h2 class="section">① 配置方式选择</h2>'));
    root.appendChild(modeCards([
      ['LOCAL_ONLY · 仅本地', '会话仅 SDK 本地留存，不上报云端也不回调宿主。用于调试与离线场景。'],
      ['CLOUD_ONLY（默认）· 域名配置', '会话仅上报企业云端（apiBaseUrl，见 ②），宿主不留存。适合统一云端质检。'],
      ['HOST_ONLY · 宿主回调', '会话仅通过宿主回调自留（见 ③），不上报云端。适合数据完全私有化场景。'],
      ['DUAL_WRITE · 双写', '云端上报与宿主回调双写，两端各自留存；两端均成功才判定本次归档成功，否则自动重试。']
    ]));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml</h3></div>' +
      '<pre class="code">&lt;!-- 是否启用企业质检会话功能，默认 true --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.enableQaSession" android:value="true" /&gt;\n\n&lt;!-- 输入质检会话分发模式：LOCAL_ONLY / CLOUD_ONLY / HOST_ONLY / DUAL_WRITE，默认 CLOUD_ONLY --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.sessionArchiveMode" android:value="CLOUD_ONLY" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>配置解析优先级：<code>OemSdkConfig.sessionArchiveMode</code>（代码）＞ Manifest meta ＞ 默认 CLOUD_ONLY。HOST_ONLY / DUAL_WRITE 必须注册 <code>OemSessionArchiveListener</code>（见 ③）。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">② API 对接（域名配置 · CLOUD_ONLY / DUAL_WRITE）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>云端上报协议（{api} = apiBaseUrl）</h3></div>' +
      '<pre class="code">POST {api}/api/v1/oem/input-qa\nContent-Type: application/json\nAuthorization: Bearer &lt;授权 token&gt;（已授权时携带）\n\n{\n  "sessionId": "会话唯一 ID",\n  "packageName": "输入发生时的前台应用包名",\n  "appName": "前台应用名称",\n  "qaContent": "质检会话内容",\n  "inputSource": "输入来源标识",\n  "timestamp": 1730000000000,\n  "policyState": "当前质检策略状态"\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>服务端实现要求</h3><ul>' +
      '<li>返回 HTTP 2xx 即视为上报成功；非 2xx 或网络异常时 SDK 端 WorkManager 自动重试，不会丢会话；</li>' +
      '<li>会话按 sessionId 幂等去重即可，重复上报可能因重试发生；</li>' +
      '<li>企业服务端可自行对 qaContent 做质检分析与留存策略。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 宿主回调对接（HOST_ONLY / DUAL_WRITE）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 注册会话接收器</h3></div>' +
      '<pre class="code">OemSdkConfig(\n    customerId = "your_customer_id",\n    appKey = "your_app_key",\n    sessionArchiveMode = OemSessionArchiveMode.HOST_ONLY, // 或 DUAL_WRITE\n    sessionArchiveListener = OemSessionArchiveListener { event -&gt;\n        // HOST_ONLY 与 DUAL_WRITE 模式下该回调必达\n        // 返回 true = 宿主已成功持久化；返回 false = 本次归档失败（DUAL_WRITE 将触发重试）\n        yourSessionStore.save(event)\n        true\n    }\n)</pre></div>'
    ));
    root.appendChild(el('<div class="panel-label">OemSessionArchiveEvent 字段说明（SDK → 宿主）</div>'));
    root.appendChild(fieldTable([
      ['eventId', 'String', true, '事件唯一 ID，用于幂等去重'],
      ['sessionId', 'String', true, '质检会话 ID'],
      ['hostApplicationId', 'String', true, '宿主应用包名'],
      ['targetPackageName', 'String', true, '输入发生时的前台应用包名'],
      ['targetAppName', 'String', true, '前台应用名称'],
      ['maskedContent', 'String', true, '质检会话内容（脱敏后）'],
      ['inputSource', 'String', true, '输入来源标识'],
      ['timestamp', 'Long', true, '会话时间（毫秒时间戳）'],
      ['policyState', 'String', true, '当前质检策略状态']
    ]));

    root.appendChild(el('<h2 class="section">④ 功能调试台</h2>'));
    root.appendChild(el(
      '<div class="card"><p>会话存档调试台面向域名配置通道：可配置后端 API 地址，在线提交质检会话、查看最近 10 条记录与完整 JSON，验证企业服务端 <code>/api/v1/oem/input-qa</code> 实现是否正确。宿主回调通道请在宿主 App 内按 ③ 联调。</p>' +
      '<p><button class="btn ghost small" id="open-session-debug-2" type="button">打开会话存档调试台</button></p></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 验收 SOP</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>切换四种模式分别验证会话去向与回调到达；</li>' +
      '<li>HOST_ONLY 下确认无任何云端请求；</li>' +
      '<li>DUAL_WRITE 下宿主返回 false 时确认 SDK 自动重试；</li>' +
      '<li>使用调试台查看最近 10 条会话与完整 JSON 结构。</li>' +
      '</ul></div>'
    ));

    function openDebug() { window.OEM_DEBUG.openSessionConsole(); }
    root.querySelector('#open-session-debug').addEventListener('click', openDebug);
    root.querySelector('#open-session-debug-2').addEventListener('click', openDebug);
    root.appendChild(footer());
  }

  /* ---------------- 日志管理 ---------------- */

  function pageLogManagement(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>日志管理</h1>' +
      '<p class="lead">OEM SDK 提供集成调试日志桥接能力 OemLog：总开关默认关闭、零开销；开启后 SDK 关键链路日志同时输出到 logcat 与宿主注册的接收器，便于集成联调期快速定位问题。SDK 自身不存储任何日志。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">① 概述</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>OemLog 为集成调试日志桥接能力，<b>总开关默认关闭，关闭时零开销</b>；</li>' +
      '<li>开启后，SDK 关键链路——<b>初始化、授权、配置解析、素材库、AI 回复、自定义抽屉、宿主回调</b>——的日志同时输出到 <code>logcat</code> 与宿主注册的接收器；</li>' +
      '<li><b>SDK 自身不存储任何日志</b>：日志仅实时分发，不落盘、不上报，留存策略完全由宿主侧决定。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">② 开启方式（任一即可）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>方式一 · AndroidManifest meta-data</h3></div>' +
      '<pre class="code">&lt;meta-data\n    android:name="com.paopaoime.oem.enableDebugLog"\n    android:value="true" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>方式二 · 构建配置 OemSdkConfig</h3></div>' +
      '<pre class="code">OemSdkConfig(\n    customerId = "your_customer_id",\n    appKey = "your_app_key",\n    enableDebugLog = true\n)</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>方式三 · 运行时开关</h3></div>' +
      '<pre class="code">OemLog.setEnabled(true)</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>生效优先级：<code>OemLog.setEnabled(...)</code>（运行时）＞ <code>OemSdkConfig.enableDebugLog</code>（构建配置）＞ Manifest meta ＞ 默认关闭。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 宿主接收日志</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 注册日志接收器（Kotlin）</h3></div>' +
      '<pre class="code">OemLog.setListener { level, tag, message, throwable -&gt;\n    // 回调在日志产生线程同步执行，更新 UI 请自行切线程\n    android.util.Log.println(level.priority, tag, message)\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>接收器回调在日志产生的线程<b>同步执行</b>：回调内应只做轻量转发（写日志、入队），耗时处理与 UI 更新请宿主自行切换线程，避免阻塞 SDK 主链路。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">④ 其它控制</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>API</th><th>作用</th></tr></thead><tbody>' +
      '<tr><td><code>OemLog.setLogcatEnabled(false)</code></td><td>关闭 logcat 输出，只保留宿主接收器通道</td></tr>' +
      '<tr><td><code>OemLog.setMinLevel(OemLogLevel.INFO)</code></td><td>按级别过滤，低于该级别的日志不分发</td></tr>' +
      '<tr><td><code>OemLog.setListener(null)</code></td><td>解除宿主接收器，仅保留 logcat 输出</td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 排障用法示例</h2>'));
    root.appendChild(el(
      '<div class="card"><p>客户对接「自定义素材库 / 自定义抽屉」遇到问题时，开启日志后可在输出中看到 <code>setDrawerContentFactory</code> / <code>registerCustomDrawer</code> / <code>dispatchRemoteMaterialFetch</code> / <code>sendTextToInput</code> 等关键调用的 <b>accepted / rejected</b> 记录，用于快速定位问题属于哪一类：</p><ul>' +
      '<li><b>宿主未注册</b>：关键调用出现 rejected（无对应工厂 / 委托注册记录）；</li>' +
      '<li><b>开关未开</b>：链路日志提示对应 Manifest 开关为 false，调用未进入分发；</li>' +
      '<li><b>宿主回调抛异常</b>：accepted 之后紧跟宿主侧异常堆栈，可定位到具体回调与异常类型。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">⑥ 注意事项</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>日志总开关<b>默认关闭</b>，未显式开启时无任何日志开销；</li>' +
      '<li><b>发布包建议关闭</b>：release 交付基线保持 <code>enableDebugLog=false</code>；</li>' +
      '<li><b>不要在生产环境长期开启</b>：日志可能包含链路细节，仅限集成联调期临时启用。</li>' +
      '</ul></div>'
    ));

    root.appendChild(footer());
  }

  /* ---------------- 产品架构 ---------------- */

  function pageArchitecture(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>产品架构</h1>' +
      '<p class="lead">泡泡输入法企业版 SDK 模块化目标架构：core 基座、五个可选功能模块、底层依赖层与 DrawerKit 抽屉开放层的 AAR 制品划分与依赖关系。</p></div>'
    ));
    root.appendChild(el('<h2 class="section">目标架构图（AAR 制品与依赖关系）</h2>'));
    root.appendChild(el(
      '<div class="arch-svg">' +
      '<svg viewBox="0 0 1080 640" xmlns="http://www.w3.org/2000/svg" font-family="Segoe UI, Microsoft YaHei, sans-serif">' +
      '<defs><marker id="ar" markerWidth="9" markerHeight="7" refX="8" refY="3.5" orient="auto"><polygon points="0 0,9 3.5,0 7" fill="#5b7396"/></marker></defs>' +
      '<rect x="330" y="14" width="420" height="52" rx="10" fill="#1c2537" stroke="#2b3a55"/>' +
      '<text x="540" y="38" fill="#e8edf7" font-size="15" text-anchor="middle">客户宿主 App（Company 式集成壳）</text>' +
      '<text x="540" y="56" fill="#9fb0c9" font-size="11" text-anchor="middle">Manifest meta 按模块开通 · OemImeSdk.initialize(...)</text>' +
      '<rect x="20" y="120" width="200" height="86" rx="10" fill="#12233b" stroke="#4da3ff" stroke-width="2"/>' +
      '<text x="120" y="146" fill="#4da3ff" font-size="14" text-anchor="middle" font-weight="bold">M1 oem-ime-sdk-core</text>' +
      '<text x="120" y="166" fill="#e8edf7" font-size="11" text-anchor="middle">基础+输入法（必选）</text>' +
      '<text x="120" y="184" fill="#9fb0c9" font-size="10" text-anchor="middle">core.aar + ime-kernel.aar + host-bridge.aar</text>' +
      '<rect x="240" y="120" width="200" height="86" rx="10" fill="#1d1830" stroke="#b78bff" stroke-width="2"/>' +
      '<text x="340" y="146" fill="#b78bff" font-size="14" text-anchor="middle" font-weight="bold">M2 oem-ime-sdk-ai</text>' +
      '<text x="340" y="166" fill="#e8edf7" font-size="11" text-anchor="middle">AI回复（可选）</text>' +
      '<text x="340" y="184" fill="#9fb0c9" font-size="10" text-anchor="middle">ai.aar + ocr 原生件</text>' +
      '<rect x="460" y="120" width="200" height="86" rx="10" fill="#2b2010" stroke="#ffb454" stroke-width="2"/>' +
      '<text x="560" y="146" fill="#ffb454" font-size="14" text-anchor="middle" font-weight="bold">M3 oem-ime-sdk-material</text>' +
      '<text x="560" y="166" fill="#e8edf7" font-size="11" text-anchor="middle">素材库（可选·新增）</text>' +
      '<text x="560" y="184" fill="#9fb0c9" font-size="10" text-anchor="middle">material.aar</text>' +
      '<rect x="680" y="120" width="200" height="86" rx="10" fill="#0f2a26" stroke="#35e0c2" stroke-width="2"/>' +
      '<text x="780" y="146" fill="#35e0c2" font-size="14" text-anchor="middle" font-weight="bold">M4 oem-ime-sdk-automation</text>' +
      '<text x="780" y="166" fill="#e8edf7" font-size="11" text-anchor="middle">Stellar自动化（可选·新增）</text>' +
      '<text x="780" y="184" fill="#9fb0c9" font-size="10" text-anchor="middle">automation.aar + stellar api 件</text>' +
      '<rect x="900" y="120" width="160" height="86" rx="10" fill="#12291a" stroke="#7ee787" stroke-width="2"/>' +
      '<text x="980" y="146" fill="#7ee787" font-size="14" text-anchor="middle" font-weight="bold">M5 oem-ime-sdk-dev</text>' +
      '<text x="980" y="166" fill="#e8edf7" font-size="11" text-anchor="middle">开发者（可选·新增）</text>' +
      '<text x="980" y="184" fill="#9fb0c9" font-size="10" text-anchor="middle">developer.aar</text>' +
      '<line x1="540" y1="66" x2="120" y2="118" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="540" y1="66" x2="340" y2="118" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="540" y1="66" x2="560" y2="118" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="540" y1="66" x2="780" y2="118" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="540" y1="66" x2="980" y2="118" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="340" y1="206" x2="150" y2="252" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="560" y1="206" x2="170" y2="252" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="780" y1="206" x2="190" y2="252" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<line x1="980" y1="206" x2="200" y2="252" stroke="#5b7396" marker-end="url(#ar)"/>' +
      '<rect x="330" y="256" width="420" height="56" rx="10" fill="#171e2e" stroke="#2b3a55"/>' +
      '<text x="540" y="280" fill="#9fd0ff" font-size="13" text-anchor="middle">M6 oem-ime-sdk-voice（可选离线语音）</text>' +
      '<text x="540" y="298" fill="#9fb0c9" font-size="11" text-anchor="middle">集成后默认开启；单独引入 sherpa-onnx，三档模型按需下载</text>' +
      '<rect x="60" y="352" width="280" height="70" rx="10" fill="#171e2e" stroke="#2b3a55"/>' +
      '<text x="200" y="380" fill="#e8edf7" font-size="12" text-anchor="middle">原生/三方依赖层</text>' +
      '<text x="200" y="398" fill="#9fb0c9" font-size="10.5" text-anchor="middle">paddleocr4android · sherpa-onnx（仅 voice）</text>' +
      '<rect x="400" y="352" width="280" height="70" rx="10" fill="#171e2e" stroke="#2b3a55"/>' +
      '<text x="540" y="380" fill="#e8edf7" font-size="12" text-anchor="middle">fcitx5 内核层（ai-ime composite）</text>' +
      '<text x="540" y="398" fill="#9fb0c9" font-size="10.5" text-anchor="middle">lib/* · plugin/* · host-bridge</text>' +
      '<rect x="740" y="352" width="280" height="70" rx="10" fill="#171e2e" stroke="#2b3a55"/>' +
      '<text x="880" y="380" fill="#e8edf7" font-size="12" text-anchor="middle">Stellar 高权限层</text>' +
      '<text x="880" y="398" fill="#9fb0c9" font-size="10.5" text-anchor="middle">stellar-manager（companion，可选）</text>' +
      '<line x1="340" y1="206" x2="220" y2="350" stroke="#5b7396" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<line x1="120" y1="206" x2="520" y2="350" stroke="#5b7396" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<line x1="780" y1="206" x2="880" y2="350" stroke="#5b7396" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<rect x="60" y="452" width="960" height="64" rx="10" fill="#101d2b" stroke="#4da3ff" stroke-dasharray="5 4"/>' +
      '<text x="540" y="476" fill="#4da3ff" font-size="13" text-anchor="middle">抽屉开放层 DrawerKit（R9 新增）：L0 默认UI / L1 内置抽屉自定义UI（AI回复·素材库）/ L2 自定义抽屉（≤4 个）</text>' +
      '<text x="540" y="498" fill="#9fb0c9" font-size="11" text-anchor="middle">公开 API 收口 oem-ime-sdk-core（keep 不混淆）· 宿主自研内容不纳入 SDK-R8 · 不做新增鉴权 · 入口并入工具面板与定制工具栏</text>' +
      '<line x1="120" y1="206" x2="300" y2="450" stroke="#4da3ff" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<line x1="340" y1="206" x2="480" y2="450" stroke="#4da3ff" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<line x1="560" y1="206" x2="620" y2="450" stroke="#4da3ff" stroke-dasharray="4 4" marker-end="url(#ar)"/>' +
      '<rect x="60" y="540" width="960" height="76" rx="10" fill="#101827" stroke="#2b3a55"/>' +
      '<text x="540" y="566" fill="#35e0c2" font-size="13" text-anchor="middle">交付层（模块化）：oem-sdk-catalog.json 模块目录 → OEM/website/oem 勾选页 → export-sdk.ps1 -Modules 组合导出</text>' +
      '<text x="540" y="588" fill="#9fb0c9" font-size="11" text-anchor="middle">每个 zip 内含：所选模块 AAR（R8 加固）+ oem-sdk-manifest.json（版本/MD5/所需 meta 配置）+ 模块文档</text>' +
      '<text x="540" y="606" fill="#9fb0c9" font-size="11" text-anchor="middle">AI/素材/自动化按授权开通；voice 为独立可选模块，以 JSON 配置开关</text>' +
      '</svg></div>'
    ));
    root.appendChild(el(
      '<div class="arch-legend">' +
      '<span><i style="background:var(--core)"></i>M1 必选</span>' +
      '<span><i style="background:var(--ai)"></i>M2 AI回复</span>' +
      '<span><i style="background:var(--material)"></i>M3 素材库</span>' +
      '<span><i style="background:var(--auto)"></i>M4 自动化</span>' +
      '<span><i style="background:var(--dev)"></i>M5 开发者</span>' +
      '<span><i style="background:#52d6d0"></i>M6 离线语音</span>' +
      '<span>实线=集成依赖，虚线=底层依赖</span>' +
      '</div>'
    ));
    root.appendChild(footer());
  }

  /* ---------------- AI回复自定义（DrawerKit L1） ---------------- */

  function pageAiReplyCustom(root) {
    root.appendChild(el("<div class=\"card\"><h3>剪贴板智能回复接管（1.0.0.20261012）</h3><p>OemImeSdk.setClipboardQuickReplyHandler(OemClipboardQuickReplyHandler { context, clipboardText, hostPackageName -&gt; false })。null 反注册并恢复 SDK 内置流程；接口位于 sdk.core 包。</p><p>注册后只在主线程回调，返回值不消费，异常静默；不打开 Activity、不收键盘、不生成、不弹 Toast、不输出 SDK 点击日志、不清除建议条。纯接管不依赖 AI/material 切片及内置生成的授权校验。普通 AI 回复和粘贴不变。</p><p>本版 OEM 移除剪贴板标题及叉号，按钮改为“智能回复”；工具条比皮肤原值增加 12 布局单位。Context 为 IME Service，来源包名可空。回调内反注册也不会使当前点击清条。完整示例见 AI 一键安装 MD。</p></div>"));
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>AI回复自定义</h1>' +
      '<p class="lead">DrawerKit 抽屉开放层 · L1 内置抽屉「自定义UI交互」：开启后 AI 回复抽屉的内容层由宿主自绘，SDK 仅保留抽屉基座（容器 + 标准头部 + 手势 + 主动开合）；原面板功能字段抽离为「能力字段协议」，宿主可自由排版并调用底层能力。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">① 功能说明（L0 默认 / L1 自定义UI）</h2>'));
    root.appendChild(modeCards([
      ['L0 默认UI（开关默认关闭）', 'AI 回复抽屉内容层为 SDK 默认 UI：页面、字段、交互、企业开关与受限态逻辑与现状完全一致；未配置自定义UI开关即维持该体验，零变化。'],
      ['L1 自定义UI（开关开启 + 注册内容工厂）', 'SDK 仅保留抽屉基座，内容层完全由宿主自绘；宿主按「能力字段协议」（见 ⑤）自由排版字段并调用底层能力，也可穿插自有交互（倒计时 / 滑屏 / 自有模型）。'],
      ['不新增鉴权', 'L1 不新增 license capability；能力字段调用仍受既有企业开关约束（如企业关闭 AI 回复则能力不可用，属事实状态而非新鉴权）。'],
      ['开关生效矩阵', '功能勾选关 → 入口不出现；功能开 + 自定义关 → L0；功能开 + 自定义开 + 未注册工厂 → 容错降级 L0；功能开 + 自定义开 + 已注册 → L1；已注册但抛异常 → 捕获并降级 L0。']
    ]));

    root.appendChild(el('<h2 class="section">② Manifest 开关配置</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml（自定义UI开关默认关闭）</h3></div>' +
      '<pre class="code">&lt;!-- AI 回复功能勾选开关（既有，自定义UI 生效的前提） --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.enableAiReply" android:value="true" /&gt;\n\n&lt;!-- AI 回复自定义UI交互开关（DrawerKit L1，默认关闭；只有明确需要宿主自绘时才改 true） --&gt;\n&lt;meta-data android:name="com.paopaoime.oem.enableAiReplyCustomUi" android:value="false" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>命名沿用现有 <code>com.paopaoime.oem.enable*</code> 风格；解析进入 <code>OemManifestMetaKeys</code> 与 <code>OemManifestConfig</code>，优先级沿用「运行时覆盖 ＞ OemSdkConfig ＞ Manifest ＞ 默认值」。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 宿主对接（注册内容工厂）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 注册 AI 回复内容工厂（drawerId = ai_reply）</h3></div>' +
      '<pre class="code">// SDK 初始化之后注册；内置 drawerId 取 ai_reply / material_hub\n// 传入 null 工厂即恢复 L0 默认 UI\nOemImeSdk.setDrawerContentFactory("ai_reply", object : OemDrawerContentFactory {\n    override fun onCreateContentView(context: Context, controller: OemDrawerController): View {\n        controller.setTitle("AI 回复")        // 标题槽可配\n        controller.setBackVisible(false)      // 单页内容可隐藏返回按钮\n        return MyAiReplyView(context)         // 宿主自绘内容层（自由排版）\n    }\n\n    override fun onBackPressed(): Boolean = false // 未消费则基座处理返回 / 关闭\n\n    override fun onHostContentError(t: Throwable) {\n        // 基座捕获宿主内容异常并降级 L0 时通知宿主\n    }\n})\n\n// 能力字段接口：与 ⑤ 能力字段表一一映射\nval caps = OemImeSdk.aiReplyCapabilities()\ncaps.startFastGenerate(replyListener)       // 极速生成</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>DrawerKit 接口定义（Kotlin）</h3></div>' +
      '<pre class="code">interface OemDrawerContentFactory {\n    fun onCreateContentView(context: Context, controller: OemDrawerController): View\n    fun onDrawerStateChanged(state: OemDrawerState) {}\n    fun onBackPressed(): Boolean = false\n    fun onHostContentError(t: Throwable) {}\n}\n\ninterface OemDrawerController {\n    fun closeDrawer()\n    fun openDrawer(drawerId: String)\n    fun setTitle(title: CharSequence?)\n    fun setBackVisible(visible: Boolean)\n    fun currentTheme(): OemDrawerTheme\n}</pre></div>'
    ));

    root.appendChild(el('<h2 class="section">④ 抽屉基座规范（SDK 保留部分）</h2>'));
    root.appendChild(drawerBaseTable());

    root.appendChild(el('<h2 class="section">⑤ AI 回复能力字段协议（12 个字段）</h2>'));
    root.appendChild(el(
      '<div class="card"><p>功能字段与 UI 解耦后交付宿主：宿主可任意排版这些字段，并通过 <code>OemImeSdk.aiReplyCapabilities()</code> 的对应 API 触发底层能力。</p></div>'
    ));
    root.appendChild(capTable([
      ['ai_reply.entry.paste', '点击复制生成回复：以「用户复制的会话文本」为上下文生成回复（粘贴会话模式）', 'startPasteSession(l) → 生成结果回调'],
      ['ai_reply.entry.fast', '极速生成：自动多屏识别当前聊天界面并生成回复，结果更精准', 'startFastGenerate(l) → 进度回调 + 结果回调'],
      ['ai_reply.entry.ocr', '识屏回复：OCR 理解屏幕内容生成回复', 'startScreenReply(l) → 进度回调 + 结果回调'],
      ['ai_reply.entry.image', '传图回复：上传多图理解会话生成回复（入参为图片 uri 列表）', 'startImageReply(uris, l) → 进度回调 + 结果回调'],
      ['ai_reply.permission.screen', '截屏权限申请：请求截屏 / 录屏权限（识屏类字段前置）', 'requestScreenPermission(l) → 授权结果回调'],
      ['ai_reply.picker.album', '相册选图：相册权限申请 + 多图选择 + 分页加载', 'openAlbumPicker(l) → 选中 uri 列表'],
      ['ai_reply.progress.timeline', '生成进度时间轴：检查运行环境 / 识别屏幕信息 / 生成回复建议 三步骤', 'observeProgress(l) → 步骤状态流（TODO / ACTIVE / DONE）'],
      ['ai_reply.result.list', '回复建议列表：多条回复候选，点选后填入宿主输入框', '结果回调 → onReplySelected(text)'],
      ['ai_reply.result.tone', '语气切换：礼貌回复 / 自然沟通 / 推动成交（预设可由宿主配置）', 'setTone(toneId) → 触发重新生成'],
      ['ai_reply.result.regenerate', '重新生成：以相同模式重跑生成', 'regenerate(l) → 进度 + 结果回调'],
      ['ai_reply.meta.footer', '模型声明：「回复内容由当前宿主配置的 AI 模型生成」文案', 'footerText() → 文案字符串'],
      ['ai_reply.state.restricted', '受限态：企业侧限制时的事实状态（仅状态告知，非鉴权）', 'isRestricted() → Boolean']
    ]));

    root.appendChild(el('<h2 class="section">⑥ 客户示例：倒计时 + 滑屏自定义流程</h2>'));
    root.appendChild(el(
      '<div class="card"><p>客户设想：点键盘 AI 回复按钮 → 自动弹起窗口显示倒计时 → 关闭抽屉去滑屏 → 截图发大模型 → 再拉起抽屉显示结果。映射到 DrawerKit 如下：基座只需预留「空白内容页 + 主动开合 + 能力字段」即可完整支撑该交互。</p></div>'
    ));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>步骤</th><th>承担方</th><th>使用能力</th></tr></thead><tbody>' +
      '<tr><td>1. 点击 AI 回复按钮</td><td>SDK 入口</td><td>现状入口不变，打开基座抽屉</td></tr>' +
      '<tr><td>2. 显示倒计时</td><td>宿主内容层</td><td>自绘 UI（宿主自绘倒计时）</td></tr>' +
      '<tr><td>3. 关闭抽屉去滑屏</td><td>宿主</td><td><code>controller.closeDrawer()</code> + 宿主自有自动化（滑屏）</td></tr>' +
      '<tr><td>4. 截图发大模型</td><td>宿主</td><td>调 <code>ai_reply.entry.ocr / entry.fast</code> 能力（SDK 截屏 + 模型），或宿主完全自有模型</td></tr>' +
      '<tr><td>5. 再拉起抽屉显示结果</td><td>宿主</td><td><code>openDrawer("ai_reply")</code> + 结果回调渲染 / 宿主自绘结果</td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑦ 降级与容错规则</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>功能勾选开关关闭 → 功能入口不出现（现状）；</li>' +
      '<li>功能开 + 自定义UI开关关 → L0 默认UI（现状，零变化）；</li>' +
      '<li>开关开启但未注册内容工厂 → <b>容错降级为 L0 默认UI</b>，输出 warn 日志；</li>' +
      '<li>宿主 <code>onCreateContentView</code> 抛异常 → 基座捕获，降级 L0 默认UI，经 <code>onHostContentError</code> 回调通知宿主；</li>' +
      '<li>旧宿主 + 新 SDK → 无开关无注册，完全 L0，零影响。</li>' +
      '</ul></div>'
    ));
    root.appendChild(footer());
  }

  /* ---------------- 素材库自定义（DrawerKit L1） ---------------- */

  function pageMaterialCustom(root) {
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>素材库自定义</h1>' +
      '<p class="lead">当前版本已经按真机闭环验证通过：客户只需要打开素材库自定义开关，在宿主侧注册 <code>material_hub</code> 的内容工厂，并把自己的 View 与数据流接到 <code>OemImeSdk.materialCapabilities()</code>，就可以在 SDK 抽屉基座上实现完全自定义的分类、列表、详情、编辑、追加、删除与发送。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">① 客户接入只看这 3 步</h2>'));
    root.appendChild(el(
      '<div class="step-grid">' +
      '<div class="step-card"><div class="step-no">01</div><h3>开启自定义素材库</h3><p>先打开素材库总开关，再打开 <code>enableMaterialLibraryCustomUi</code>。不开启时，SDK 继续显示默认素材库。</p></div>' +
      '<div class="step-card"><div class="step-no">02</div><h3>建立宿主与抽屉通信</h3><p>宿主通过 <code>setDrawerContentFactory(material_hub, ...)</code> 接管内容层；SDK 仍负责抽屉容器、头部、返回键、关闭与手势。</p></div>' +
      '<div class="step-card"><div class="step-no">03</div><h3>在宿主 View 上实现业务</h3><p>分类、列表、详情、编辑、追加、删除、发送都由宿主 View 自己排版；真正的数据与发送能力由 <code>materialCapabilities()</code> 提供。</p></div>' +
      '</div>'
    ));

    root.appendChild(el('<h2 class="section">② 开关配置</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>AndroidManifest.xml · 先打开总开关，再打开自定义开关</h3></div>' +
      '<pre class="code">&lt;!-- 既有素材库功能总开关：不打开则素材库入口不会出现 --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.enableMaterialLibrary"\n    android:value="true" /&gt;\n\n&lt;!-- 自定义素材库开关：打开后才会进入宿主自绘内容层 --&gt;\n&lt;meta-data\n    android:name="com.paopaoime.oem.enableMaterialLibraryCustomUi"\n    android:value="true" /&gt;</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><ul class="point-list">' +
      '<li>只开 <code>enableMaterialLibrary</code>：继续使用 SDK 默认素材库。</li>' +
      '<li>两个开关都开：进入宿主自定义素材库内容层。</li>' +
      '<li>开关开了但没注册内容工厂：SDK 自动容错回落到默认素材库，避免白屏。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 如何显示自定义素材库抽屉</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>Application / 初始化阶段 · 注册 material_hub 内容工厂</h3></div>' +
      '<pre class="code">object CompanyDrawerKitSetup {\n    fun install(context: Context) {\n        OemImeSdk.setDrawerContentFactory(\n            OemDrawerKit.DRAWER_MATERIAL_HUB,\n            MaterialCustomUiFactory()\n        )\n    }\n}\n\nprivate class MaterialCustomUiFactory : OemDrawerContentFactory {\n    private var controller: OemDrawerController? = null\n    private var rootContainer: FrameLayout? = null\n\n    override fun onCreateContentView(\n        context: Context,\n        controller: OemDrawerController,\n    ): View {\n        this.controller = controller\n        this.rootContainer = FrameLayout(context)\n        controller.setTitle("素材库")\n        controller.setBackVisible(false)\n        render(context)\n        return rootContainer!!\n    }\n\n    override fun onBackPressed(): Boolean {\n        // 宿主优先处理详情页 / 编辑页返回；未消费时交给 SDK 基座关闭抽屉\n        return false\n    }\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>这一层的关键边界是：<b>SDK 只保留抽屉基座</b>，宿主只渲染内容层。你不需要自己处理抽屉容器、顶部关闭按钮、下拉关闭手势，但你的 View 需要自己决定首页、详情页、编辑页之间如何切换。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">④ 如何在抽屉上显示分类组件与素材列表</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>宿主首页 View · 分类 + 文件夹 + 素材列表</h3></div>' +
      '<pre class="code">private fun refreshHomeData(context: Context) {\n    val caps = OemImeSdk.materialCapabilities() ?: return\n\n    caps.observeFolders(object : OemFolderListener {\n        override fun onFolders(folders: List&lt;OemMaterialFolder&gt;) {\n            this@MaterialCustomUiFactory.folders = folders\n            render(context)\n        }\n    })\n\n    caps.queryMaterials(\n        OemMaterialQuery(\n            category = selectedCategory,\n            folderId = selectedFolderId,\n            page = 0,\n        ),\n        object : OemMaterialListener {\n            override fun onPage(page: OemMaterialPage) {\n                materials = page.items\n                render(context)\n            }\n        }\n    )\n}\n\n// 你在宿主 View 里自己画分类 Tab / 文件夹 Chip / 列表卡片\n// 点击分类 Tab 后，只需要改 selectedCategory 再次调用 refreshHomeData()</pre></div>'
    ));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>宿主界面区域</th><th>建议显示内容</th><th>对应字段 / API</th></tr></thead><tbody>' +
      '<tr><td>顶部标题</td><td>“素材库”或企业自定义标题</td><td><code>controller.setTitle(...)</code></td></tr>' +
      '<tr><td>分类 Tab</td><td>最近 / 图片 / 文档 / 视频</td><td><code>OemMaterialCategory</code> + <code>queryMaterials(...)</code></td></tr>' +
      '<tr><td>文件夹行</td><td>个人 / 工作 / 学术等宿主自定义排布</td><td><code>observeFolders(...)</code></td></tr>' +
      '<tr><td>列表卡片标题</td><td>素材名、摘要、来源</td><td><code>OemMaterialItem.title</code> / <code>source</code></td></tr>' +
      '<tr><td>列表卡片副信息</td><td>大小、时间、类型</td><td><code>sizeText</code> / <code>timeText</code> / <code>kind</code></td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 如何通过素材列表进入素材详情</h2>'));
    root.appendChild(el(
      '<div class="shot-grid">' +
      '<div class="shot-card"><img src="./assets/feature-demo/material-custom-current-home.png" alt="当前版本宿主自绘素材详情页截图" /><h3>当前真机截图：宿主自绘素材详情页</h3><p>这张图对应的是“素材列表点击进入详情”后的页面。上半部分是宿主自定义操作区，下半部分是 <code>OemMaterialDetail.parts</code> 渲染出来的多组内容。</p></div>' +
      '<div class="shot-card"><h3>这张图里每一块对应什么</h3><ul class="point-list">' +
      '<li>顶部标题“素材详情（宿主自绘）”：宿主用 <code>controller.setTitle</code> 或页面标题自行控制。</li>' +
      '<li>“编辑当前素材 / 追加文本分组 / 追加图片分组...”按钮：宿主按钮，背后调用 <code>updateDetail</code> 或 <code>pickAndSave</code>。</li>' +
      '<li>“分组1 · 图片”：来自 <code>OemMaterialDetail.parts</code>，宿主决定按卡片、瀑布流还是业务表单展示。</li>' +
      '<li>关闭按钮和抽屉容器：依旧由 SDK 基座负责，不需要宿主自己造抽屉。</li>' +
      '</ul></div>' +
      '</div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>列表点击详情 · 详情数据由 SDK 提供，页面结构由宿主决定</h3></div>' +
      '<pre class="code">private fun openDetail(context: Context, item: OemMaterialItem) {\n    val caps = OemImeSdk.materialCapabilities() ?: return\n    caps.loadDetail(item.id, object : OemMaterialDetailListener {\n        override fun onDetail(detail: OemMaterialDetail) {\n            selectedItem = item\n            selectedDetail = detail\n            currentMode = ScreenMode.DETAIL\n            render(context)\n        }\n    })\n}\n\nprivate fun buildDetailPartCard(part: OemMaterialPart): View {\n    return when (part.type) {\n        OemMaterialPartType.TEXT -> buildTextCard(part.text.orEmpty())\n        OemMaterialPartType.IMAGE,\n        OemMaterialPartType.FILE,\n        OemMaterialPartType.VIDEO -> buildAttachmentCard(\n            title = part.title ?: "未命名附件",\n            filePath = part.filePath,\n            mimeType = part.mimeType,\n            sizeText = part.sizeText,\n        )\n    }\n}</pre></div>'
    ));

    root.appendChild(el('<h2 class="section">⑥ 如何对素材详情做发送、编辑、删除、追加</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>发送素材 · 始终走 SDK 默认发送链路</h3></div>' +
      '<pre class="code">private fun sendCurrentItem(context: Context, item: OemMaterialItem) {\n    val sent = OemImeSdk.materialCapabilities()?.sendToInput(item) == true\n    if (!sent) {\n        Toast.makeText(context, "发送失败", Toast.LENGTH_SHORT).show()\n    }\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>编辑详情 · 宿主只改 parts，再交给 updateDetail</h3></div>' +
      '<pre class="code">private fun saveEditedDetail(\n    itemId: String,\n    editingParts: List&lt;OemMaterialPart&gt;,\n) {\n    OemImeSdk.materialCapabilities()?.updateDetail(\n        itemId,\n        editingParts,\n        object : OemMaterialMutationListener {\n            override fun onSuccess(item: OemMaterialItem) {\n                // 重新加载详情即可\n            }\n        }\n    )\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>删除素材 · 宿主决定按钮样式，SDK 负责真正删除</h3></div>' +
      '<pre class="code">private fun deleteCurrentItem(itemId: String) {\n    OemImeSdk.materialCapabilities()?.delete(\n        itemId,\n        object : OemMaterialDeleteListener {\n            override fun onSuccess() {\n                // 回到首页并刷新列表\n            }\n        }\n    )\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>追加文本 / 图片 / 文件 / 视频分组</h3></div>' +
      '<pre class="code">private fun appendText(currentItem: OemMaterialItem, detail: OemMaterialDetail, text: String) {\n    val newPart = OemMaterialPart(\n        id = "text_" + System.currentTimeMillis(),\n        type = OemMaterialPartType.TEXT,\n        text = text,\n        title = text.take(40),\n    )\n    OemImeSdk.materialCapabilities()?.updateDetail(\n        currentItem.id,\n        detail.parts + newPart,\n        object : OemMaterialMutationListener {\n            override fun onSuccess(item: OemMaterialItem) {}\n        }\n    )\n}\n\nprivate fun appendAttachment(type: OemPickerType) {\n    OemImeSdk.materialCapabilities()?.pickAndSave(\n        type,\n        object : OemMaterialMutationListener {\n            override fun onSuccess(tempItem: OemMaterialItem) {\n                // 读取 tempItem 详情后，把 tempItem.parts 合并进当前 detail.parts\n                // 再次调用 updateDetail(currentItem.id, mergedParts, ...)\n                // 最后 delete(tempItem.id, ...) 清理临时素材\n            }\n        }\n    )\n}</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><p>如果你要做“完全不像默认素材库”的业务 UI，核心原则只有一个：<b>宿主决定页面怎么长，SDK 只提供数据和动作</b>。也就是说，客户完全可以把详情页改成企业卡片、时间轴、审批流表单，唯一不建议重写的是发送链路，发送仍应统一走 <code>sendToInput(item)</code>。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">⑦ 关键能力字段与界面落位</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>能力</th><th>宿主通常怎么用</th><th>在界面上一般显示在哪里</th></tr></thead><tbody>' +
      '<tr><td><code>queryMaterials(query, listener)</code></td><td>加载首页素材列表</td><td>首页内容区、搜索结果区、分类切换后的列表区</td></tr>' +
      '<tr><td><code>observeFolders(listener)</code></td><td>读取文件夹或业务分组</td><td>顶部文件夹筛选条、侧边过滤器、宫格入口</td></tr>' +
      '<tr><td><code>loadDetail(itemId, listener)</code></td><td>打开某个素材详情</td><td>详情页主体、多分组内容区</td></tr>' +
      '<tr><td><code>saveText(text, listener)</code></td><td>新建纯文本素材</td><td>文本编辑页“保存”按钮</td></tr>' +
      '<tr><td><code>pickAndSave(type, listener)</code></td><td>添加图片 / 文件 / 视频</td><td>“新增素材”或“追加分组”按钮</td></tr>' +
      '<tr><td><code>updateDetail(itemId, parts, listener)</code></td><td>编辑顺序、删除分组、追加内容后提交</td><td>详情页保存、编辑页确认、追加完成后的提交动作</td></tr>' +
      '<tr><td><code>delete(itemId, listener)</code></td><td>删除当前素材</td><td>详情页右上角菜单、编辑页危险操作区</td></tr>' +
      '<tr><td><code>sendToInput(item)</code></td><td>把当前素材送入宿主输入链路</td><td>详情页底部主按钮、首页快捷发送按钮</td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑧ 降级与容错</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>总开关关闭：素材库入口不出现。</li>' +
      '<li>总开关开、自定义开关关：继续走默认素材库。</li>' +
      '<li>两个开关都开但未注册工厂：自动降级到默认素材库。</li>' +
      '<li>宿主 View 创建异常：基座捕获异常并退出宿主自绘，避免拖垮输入主链路。</li>' +
      '<li>建议所有发送动作统一走 <code>sendToInput(item)</code>，不要自己重写发送链路。</li>' +
      '</ul></div>'
    ));
    root.appendChild(footer());
  }

  /* ---------------- 自定义抽屉（DrawerKit L2） ---------------- */

  function pageCustomDrawer(root) {
    root.appendChild(el("<div class=\"card\"><h3>剪贴板智能回复接管（1.0.0.20261012）</h3><p>OemImeSdk.setClipboardQuickReplyHandler(OemClipboardQuickReplyHandler { context, clipboardText, hostPackageName -&gt; false })。null 反注册并恢复 SDK 内置流程；接口位于 sdk.core 包。</p><p>注册后只在主线程回调，返回值不消费，异常静默；不打开 Activity、不收键盘、不生成、不弹 Toast、不输出 SDK 点击日志、不清除建议条。纯接管不依赖 AI/material 切片及内置生成的授权校验。普通 AI 回复和粘贴不变。</p><p>本版 OEM 移除剪贴板标题及叉号，按钮改为“智能回复”；工具条比皮肤原值增加 12 布局单位。Context 为 IME Service，来源包名可空。回调内反注册也不会使当前点击清条。完整示例见 AI 一键安装 MD。</p></div>"));
    root.appendChild(el(
      '<div class="hero"><span class="tag">集成交付</span><h1>自定义抽屉</h1>' +
      '<p class="lead">DrawerKit 抽屉开放层 · L2 自定义抽屉：宿主通过运行时注册 API 新建任意业务抽屉（图标、标题、内容均为宿主动态资源），入口自动进入键盘工具面板与顶部工具条，无需我方鉴权，宿主自研部分不纳入 SDK-R8 加密范围。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">① 功能说明</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>宿主运行时注册任意业务抽屉（L2），图标、标题、内容均为动态资源，<b>不走 Manifest 静态声明</b>；</li>' +
      '<li>自定义抽屉<b>不经过任何我方鉴权 / 企业开关</b>，仅要求 SDK 初始化成功，作为宿主自研的方便入口；</li>' +
      '<li>宿主自研代码位于宿主 APK，<b>天然不纳入 SDK-R8 加密范围</b>；DrawerKit 公开 API（接口 / Spec / Controller / 字段常量）经 consumer-rules keep，宿主侧 R8 不误删；</li>' +
      '<li>自定义抽屉内容属宿主自研责任域：其崩溃、合规、隐私由宿主负责。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el('<h2 class="section">② 注册 API</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>DrawerKit 自定义抽屉 API（Kotlin）</h3></div>' +
      '<pre class="code">data class OemCustomDrawerSpec(\n    val id: String,                 // 宿主内唯一，建议 "custom_&lt;biz&gt;"\n    val title: String,              // 工具面板 / 工具条显示名\n    val iconRes: Int = 0,           // 宿主侧 drawable 资源 id（与 icon 二选一）\n    val icon: Drawable? = null,     // 宿主侧 Drawable / Bitmap\n    val contentFactory: OemDrawerContentFactory,\n    val entryHandler: OemCustomDrawerEntryHandler? = null\n)\n\n// 注册结果\nenum class OemRegisterResult { OK, DUPLICATE_ID, QUOTA_EXCEEDED, INVALID_ID }\n\n// 注册 / 注销 / 更新：运行中注册 / 注销触发入口热刷新\nOemImeSdk.registerCustomDrawer(spec: OemCustomDrawerSpec): OemRegisterResult\nOemImeSdk.unregisterCustomDrawer(id: String)\nOemImeSdk.updateCustomDrawer(spec: OemCustomDrawerSpec)</pre></div>'
    ));
    root.appendChild(el(
      '<div class="card"><h3>注册结果语义</h3><ul>' +
      '<li><code>OK</code>：注册成功，入口即时出现在工具面板与工具条；</li>' +
      '<li><code>DUPLICATE_ID</code>：同 id 已注册（更新请用 <code>updateCustomDrawer</code>）；</li>' +
      '<li><code>QUOTA_EXCEEDED</code>：超出数量上限（见 ③）；</li>' +
      '<li><code>INVALID_ID</code>：id 非法（为空或格式不符，建议 <code>custom_&lt;biz&gt;</code>）。</li>' +
      '</ul><p>注册时机：SDK 初始化之后、键盘首次展示之前（Application 或首屏）。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">③ 数量上限</h2>'));
    root.appendChild(el(
      '<div class="card"><p>自定义抽屉最多注册 <b>4 个</b>；超出时 <code>registerCustomDrawer</code> 返回 <code>QUOTA_EXCEEDED</code>，需先 <code>unregisterCustomDrawer</code> 已有抽屉再注册新的。</p></div>'
    ));

    root.appendChild(el('<h2 class="section">④ 入口落位规则</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>入口</th><th>规则</th></tr></thead><tbody>' +
      '<tr><td>工具面板（宫格页）</td><td>自定义项<b>按注册顺序追加到内置项之后</b>（内置顺序保持现状），分页自动翻页</td></tr>' +
      '<tr><td>顶部工具条</td><td>纳入「定制工具栏」体系：出现在定制界面可选宫格中，受<b>排序 / 显示 / 隐藏</b>管理</td></tr>' +
      '<tr><td>工具条默认显隐</td><td>注册时若工具条未满 5 个（上限维持 5，含自定义），默认追加显示在末尾；<b>若已满，进入隐藏池</b>，用户可在定制工具栏中手动换入</td></tr>' +
      '<tr><td>持久化</td><td>自定义项 prefValue 取 <code>custom_&lt;id&gt;</code>（沿用 bridge_toolbar_visible_items / bridge_toolbar_order）；旧版本对未知值按「未知即过滤」处理</td></tr>' +
      '<tr><td>注销清理</td><td><code>unregisterCustomDrawer</code> 同步清除两项 SP 中对应 prefValue，入口即时移除，避免脏数据</td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑤ 入口图标规范</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>项</th><th>规范</th></tr></thead><tbody>' +
      '<tr><td>来源</td><td><code>iconRes</code>（宿主包内 drawable 资源）或 <code>icon</code>（Drawable / Bitmap）<b>二选一</b></td></tr>' +
      '<tr><td>尺寸</td><td>工具条按 24dp 渲染，宫格按现有宫格图标尺寸渲染；建议宿主提供矢量图或 ≥96px 位图</td></tr>' +
      '<tr><td>着色</td><td>自定义图标<b>默认原色渲染、不跟随内置 tint</b>（内置项保持现状 tint）；如需跟随主题，宿主可提供 night 变体并经 <code>currentTheme()</code> 自适应</td></tr>' +
      '<tr><td>兜底</td><td>未提供图标时，宫格沿用现有「标题首字符」textIcon 兜底；工具条显示默认占位图标</td></tr>' +
      '</tbody></table></div>'
    ));

    root.appendChild(el('<h2 class="section">⑥ 承载与交互</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>未配置 entryHandler：点击入口 → 基座创建抽屉 → 调宿主 <code>contentFactory</code> 渲染内容层；</li>' +
      '<li>基座标准头部、下拉手势、返回键分发、生命周期回调与 L1 基座规范完全一致（见「AI回复自定义」页 ④）；</li>' +
      '<li>宿主内容异常：基座捕获，关闭抽屉并回调宿主，不影响键盘主链路。</li>' +
      '</ul></div>'
    ));

    root.appendChild(el("<div class=\"card\"><h3>入口点击接管（1.0.0.20261012）</h3><p>可选 entryHandler: OemCustomDrawerEntryHandler? = null。接口 onEntryClick(context: Context, drawerId: String): Boolean。配置后两个入口只在主线程回调，返回值不消费，异常静默捕获；不收键盘、不创建 Activity/抽屉、不调用 contentFactory、不输出 SDK 点击日志或 Toast。null 保留原流程。</p><p>回调在编辑器和 material 切片校验前执行；纯接管无需 material。注册开关、宫格分页、工具条排序/显隐/隐藏池、custom_&lt;id&gt; 持久化与注销清理保持。Context 为 IME Service；自行启动 Activity 需 NEW_TASK。请勿在回调中递归 openDrawer 同 id。</p><pre class=\"code\">OemImeSdk.updateCustomDrawer(spec.copy(\n    entryHandler = OemCustomDrawerEntryHandler { context, drawerId -&gt;\n        // 宿主自行处理\n        true // false 也不回退 SDK 默认抽屉\n    }\n))</pre><p>新字段追加于 contentFactory 之后；请同步升级 SDK/runtime 并重新编译使用该 data class 的宿主和预编译库。</p></div>"));

    root.appendChild(el('<h2 class="section">⑦ 完整 Kotlin 示例（Application 注册 + 简单内容 View）</h2>'));
    root.appendChild(el(
      '<div class="code-card"><div class="code-head"><h3>HostApp.kt · 注册自定义抽屉</h3></div>' +
      '<pre class="code">class HostApp : FcitxApplication() {\n    override fun onCreate() {\n        super.onCreate()\n        OemHostRuntime.initialize(this, true)\n        OemImeSdk.initializeFromManifest(\n            this,\n            licenseProvider = OemLicenseProvider { _, _ -&gt; OemLicenseSnapshot.active() }\n        )\n\n        // SDK 初始化之后、键盘首次展示之前注册\n        val result = OemImeSdk.registerCustomDrawer(\n            OemCustomDrawerSpec(\n                id = "custom_coupon",                    // 宿主内唯一\n                title = "福利中心",                       // 工具面板 / 工具条显示名\n                iconRes = R.drawable.ic_drawer_coupon,   // 入口图标（iconRes / icon 二选一）\n                contentFactory = object : OemDrawerContentFactory {\n                    override fun onCreateContentView(\n                        context: Context,\n                        controller: OemDrawerController\n                    ): View {\n                        controller.setTitle("福利中心")\n                        return TextView(context).apply { // 简单内容 View（可替换为任意自绘视图）\n                            text = "宿主自研的自定义抽屉内容"\n                            textSize = 16f\n                            setPadding(48, 48, 48, 48)\n                        }\n                    }\n                }\n            )\n        )\n        if (result != OemRegisterResult.OK) {\n            Log.w("HostApp", "自定义抽屉注册失败: $result")\n        }\n\n        // 后续维护：\n        // OemImeSdk.updateCustomDrawer(newSpec)        // 更新标题 / 图标 / 内容工厂\n        // OemImeSdk.unregisterCustomDrawer("custom_coupon") // 注销：入口即时移除 + SP 清理\n    }\n}</pre></div>'
    ));
    root.appendChild(footer());
  }

  function tcHero(title, lead) {
    return el('<div class="hero"><span class="tag">测试用例</span><h1>' + esc(title) + '</h1><p class="lead">' + esc(lead) + '</p></div>');
  }

  function tcTable(rows) {
    var body = rows.map(function (r) {
      return '<tr><td><code>' + esc(r[0]) + '</code></td><td><b>' + esc(r[1]) + '</b></td><td>' + esc(r[2]) + '</td><td>' + esc(r[3]) + '</td><td>' + esc(r[4]) + '</td><td><span class="prio prio-' + r[5] + '">' + r[5] + '</span></td></tr>';
    }).join('');
    return el('<div class="tbl-wrap"><table class="tbl"><thead><tr><th>编号</th><th>用例标题</th><th>前置条件</th><th>执行步骤</th><th>预期结果</th><th>优先级</th></tr></thead><tbody>' + body + '</tbody></table></div>');
  }

  function pageTcOverview(root) {
    root.appendChild(tcHero('测试总览', '面向客户测试团队的验收用例集：覆盖安装激活、基础输入、AI 回复、素材库、会话存档与兼容异常，与 SDK ' + (OEM.state && OEM.state.catalog ? OEM.state.catalog.sdkVersion : '') + ' 及《OEM SDK 集成说明文档》PDF 同源对齐。'));
    root.appendChild(el('<h2 class="section">优先级定义</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>等级</th><th>定义</th><th>处理约定</th></tr></thead><tbody>' +
      '<tr><td><span class="prio prio-P0">P0</span></td><td>阻断性：主链路不可用（安装、启用、输入、授权、数据合规）</td><td>任一失败即阻断验收，必须修复后回归</td></tr>' +
      '<tr><td><span class="prio prio-P1">P1</span></td><td>核心体验：功能可用但体验受损（降级、超时、开关显隐）</td><td>验收前修复或经双方确认遗留</td></tr>' +
      '<tr><td><span class="prio prio-P2">P2</span></td><td>边缘场景：低频组合与外观适配</td><td>记录跟踪，不阻断验收</td></tr>' +
      '</tbody></table></div>'
    ));
    root.appendChild(el('<h2 class="section">用例域与入口</h2>'));
    root.appendChild(el(
      '<div class="tbl-wrap"><table class="tbl"><thead><tr><th>用例域</th><th>覆盖重点</th><th>入口</th></tr></thead><tbody>' +
      '<tr><td>环境与准备</td><td>交付物获取、集成构建、设备与授权服务准备</td><td><a href="#/tc-env">环境与准备</a></td></tr>' +
      '<tr><td>安装与激活</td><td>安装、启用、切换、License 状态、验收标准</td><td><a href="#/tc-install">安装与激活</a></td></tr>' +
      '<tr><td>基础输入</td><td>输入方案、候选、剪贴板、工具栏显隐、键盘管理</td><td><a href="#/tc-input">基础输入</a></td></tr>' +
      '<tr><td>AI 回复</td><td>四种触发场景、HOST_ONLY 回调、90s 超时降级</td><td><a href="#/tc-ai">AI 回复</a></td></tr>' +
      '<tr><td>素材库</td><td>入口、拉取分页、同步、30s 超时、HOST_ONLY</td><td><a href="#/tc-material">素材库</a></td></tr>' +
      '<tr><td>会话存档</td><td>四种分发模式数据流向与回调字段断言</td><td><a href="#/tc-archive">会话存档</a></td></tr>' +
      '<tr><td>兼容与异常</td><td>Android 厂商 ROM、Android 16 降级、断网恢复、多输入法共存</td><td><a href="#/tc-compat">兼容与异常</a></td></tr>' +
      '</tbody></table></div>'
    ));
    root.appendChild(el('<h2 class="section">执行约定</h2>'));
    root.appendChild(el(
      '<div class="card"><ul>' +
      '<li>基线环境：Android 10–16 真机各一台 + Android 厂商 ROM（小米 / OPPO / vivo / 三星）至少两台；详见「环境与准备」。</li>' +
      '<li>每条用例按「前置条件 → 执行步骤 → 预期结果」执行；预期不满足即记缺陷，附日志与截图。</li>' +
      '<li>HOST_ONLY / LOCAL_ONLY 类用例需宿主测试工程实现对应回调并打印数据，用于断言数据不出端。</li>' +
      '<li>用例编号规则：TC-ENV / TC-INS / TC-INP / TC-AI / TC-MAT / TC-ARC / TC-CMP，与左侧菜单域一一对应。</li>' +
      '</ul></div>'
    ));
    root.appendChild(footer());
  }

  function pageTcEnv(root) {
    root.appendChild(tcHero('环境与准备', '验收前的环境基线核对：交付物、构建链路、设备矩阵与企业侧服务准备。'));
    root.appendChild(el('<h2 class="section">准备用例</h2>'));
    root.appendChild(tcTable([
      ['TC-ENV-01', '交付物获取完整', '已获交付账号', '下载 SDK 交付仓（aar/pom 直链）、catalog、《集成说明文档》PDF 与演示包', '制品清单与 catalog 模块闭包一致，PDF 版本号 = sdkVersion', 'P0'],
      ['TC-ENV-02', '宿主集成构建通过', 'JDK 17 / Gradle 9.4.1 / AGP 9.2.0', '按集成章节配置 settings.gradle 与依赖，执行 assembleRelease', '构建成功，APK 含 extractNativeLibs=true', 'P0'],
      ['TC-ENV-03', '设备矩阵就绪', 'adb 可用', '准备 Android 10/12/14/16 真机与国产 ROM 机型，adb devices 可见', '全部设备 online，IME 命令可用', 'P0'],
      ['TC-ENV-04', 'License 服务可模拟', '企业授权服务或 mock', '分别返回 ACTIVE / GRACE / EXPIRED / DISABLED / NOT_OPENED', '五种状态可切换，供安装激活域用例使用', 'P1'],
      ['TC-ENV-05', 'apiBaseUrl 联通性', '企业服务端或 mock', '配置 apiBaseUrl 后抓包验证 SDK 请求域名', '请求仅发往企业域名；HOST_ONLY 场景无云端请求', 'P1'],
      ['TC-ENV-06', '能力开关基线', '宿主 Manifest 可改', '按开通范围组合 enableAi / enableMaterialLibrary / enableAutomation 开关', '开关组合可复现，用于显隐类用例', 'P1']
    ]));
    root.appendChild(footer());
  }

  function pageTcInstall(root) {
    root.appendChild(tcHero('安装与激活', '从安装包到键盘首次拉起的完整链路验收，含 License 状态与 Android 厂商 ROM 行为。'));
    root.appendChild(el('<h2 class="section">安装激活用例</h2>'));
    root.appendChild(tcTable([
      ['TC-INS-01', '正式包安装', 'release APK 已构建', 'adb install -r 安装宿主包', '安装成功，无解析错误', 'P0'],
      ['TC-INS-02', '命令行启用切换', '已安装', '执行 ime enable 与 ime set（组件名 com.paopaoime.aikeyboard.ime.service.AiInputMethodService）', '系统输入法列表可见并切换成功', 'P0'],
      ['TC-INS-03', '手动启用路径', '已安装', '系统设置 → 语言和输入法 → 启用并切换', '引导页直达启用页，两步完成', 'P1'],
      ['TC-INS-04', 'License ACTIVE 首启', '授权服务返回 ACTIVE', '切换后唤起键盘', '键盘正常拉起，无授权提示', 'P0'],
      ['TC-INS-05', 'License 异常态提示', 'mock 返回 EXPIRED / DISABLED / NOT_OPENED', '分别唤起键盘', '键盘侧展示对应状态提示，拒绝运行且不崩溃', 'P1'],
      ['TC-INS-06', 'GRACE 宽限运行', 'mock 返回 GRACE', '唤起键盘并使用核心功能', '功能可用且展示到期提醒', 'P1'],
      ['TC-INS-07', '重启后默认输入法', '已设为默认', '重启设备', '默认输入法保持或按 ROM 行为重置后可手动切回', 'P2'],
      ['TC-INS-08', '原生库解压校验', '已安装', 'aapt / apk 工具检查 extractNativeLibs', '值为 true，fcitx / OCR so 正常加载', 'P1']
    ]));
    root.appendChild(footer());
  }

  function pageTcInput(root) {
    root.appendChild(tcHero('基础输入', '输入法核心体验验收：输入方案、候选、剪贴板、工具栏显隐与键盘管理。'));
    root.appendChild(el('<h2 class="section">基础输入用例</h2>'));
    root.appendChild(tcTable([
      ['TC-INP-01', '拼音输入与候选', '键盘已启用', '输入 nihao，观察候选并上屏', '候选正确，「你好」可上屏', 'P0'],
      ['TC-INP-02', '方案切换', '键盘已启用', '切换英文 / 数字 / 符号键盘并输入', '各布局响应正确', 'P0'],
      ['TC-INP-03', '长句连续输入', '键盘已启用', '连续输入多音节短语', '整句候选合理，可回退删除', 'P1'],
      ['TC-INP-04', '剪贴板复制粘贴', '任意输入框', '复制文本后从键盘剪贴板粘贴', '内容一致，历史条目可用', 'P1'],
      ['TC-INP-05', '工具栏入口显隐', '分别构建未集成 / 已集成 ai、material 的包', '对比工具栏入口', '未集成模块入口不可见，已集成且开通的入口可见', 'P0'],
      ['TC-INP-06', '键盘管理页', '键盘已启用', '进入键盘管理 / 设置页', '页面可达，启用与切换引导可用', 'P1'],
      ['TC-INP-07', '皮肤与布局', '键盘已启用', '切换皮肤 / 布局后输入', '即时生效且输入不受影响', 'P2']
    ]));
    root.appendChild(footer());
  }

  function pageTcAi(root) {
    root.appendChild(tcHero('AI 回复', '键盘内 AI 沟通链路验收：四种触发场景、宿主回调、超时降级与开关显隐。'));
    root.appendChild(el('<h2 class="section">AI 回复用例</h2>'));
    root.appendChild(tcTable([
      ['TC-AI-01', '极速生成', 'ai 模块开通，宿主回调已注入', '输入框输入上下文，触发极速生成', '90s 内返回建议并可选择上屏', 'P0'],
      ['TC-AI-02', '读屏回复（含 automation）', '集成 automation 且授权完成', '触发读屏回复', 'OCR 读取当前界面并生成回复', 'P0'],
      ['TC-AI-03', '读屏降级（未集成 automation）', '未集成 automation', '触发读屏回复', '自动降级为手动截屏方案，流程可用', 'P0'],
      ['TC-AI-04', '图片回复', '设备含测试图片', '选择图片触发图片回复', 'OCR 解析图片内容并生成回复', 'P1'],
      ['TC-AI-05', '剪贴板会话理解', '剪贴板含会话文本', '触发剪贴板理解', '基于剪贴板内容生成回复', 'P1'],
      ['TC-AI-06', 'HOST_ONLY 数据自留', 'aiReplyDispatchMode=HOST_ONLY', '触发一次 AI 回复并抓包', '请求仅到宿主回调，无云端域名请求', 'P0'],
      ['TC-AI-07', '90s 超时降级', '宿主回调延迟 >90s', '触发极速生成', '键盘展示降级提示，不阻塞输入', 'P1'],
      ['TC-AI-08', '开关关闭入口隐藏', 'enableAiReply=false', '查看工具栏', 'AI 入口隐藏，其余功能正常', 'P1']
    ]));
    root.appendChild(footer());
  }

  function pageTcMaterial(root) {
    root.appendChild(tcHero('素材库', '素材中心链路验收：入口、拉取分页、使用插入、同步与超时空态。'));
    root.appendChild(el('<h2 class="section">素材库用例</h2>'));
    root.appendChild(tcTable([
      ['TC-MAT-01', '工具栏入口', 'material 模块开通', '点击工具栏素材库入口', '素材面板正常打开', 'P0'],
      ['TC-MAT-02', '拉取分页与分类', '宿主素材服务含 ≥2 页数据', '打开面板并翻页、切换分类', 'fetchRemoteMaterials 分页与过滤正确', 'P0'],
      ['TC-MAT-03', '素材使用插入', '面板有素材', '选择素材插入输入框', '内容正确上屏', 'P0'],
      ['TC-MAT-04', '后台静默同步', '素材服务端有更新', '等待同步周期或触发同步', '面板增量更新，无 UI 阻塞', 'P1'],
      ['TC-MAT-05', '30s 超时空态', '宿主回调延迟 >30s', '打开面板', '展示空态与重试入口，重试可恢复', 'P1'],
      ['TC-MAT-06', 'HOST_ONLY 素材链路', 'materialDispatchMode=HOST_ONLY', '拉取并使用素材', '数据仅来自宿主回调，抓包无云端请求', 'P1'],
      ['TC-MAT-07', '开关关闭入口隐藏', 'enableMaterialLibrary=false', '查看工具栏', '素材库入口隐藏', 'P1']
    ]));
    root.appendChild(footer());
  }

  function pageTcArchive(root) {
    root.appendChild(tcHero('会话存档', '输入质检会话四种分发模式的数据流向与回调字段断言，合规验收核心。'));
    root.appendChild(el('<h2 class="section">分发模式用例</h2>'));
    root.appendChild(tcTable([
      ['TC-ARC-01', 'CLOUD_ONLY 上传', 'sessionArchiveMode=CLOUD_ONLY', '完成一次会话并结束', '会话上传企业云端，宿主无回调', 'P0'],
      ['TC-ARC-02', 'HOST_ONLY 宿主自留', 'sessionArchiveMode=HOST_ONLY，宿主实现回调', '完成一次会话并结束', '回调收到完整会话，抓包无云端请求', 'P0'],
      ['TC-ARC-03', 'DUAL_WRITE 双写', 'sessionArchiveMode=DUAL_WRITE', '完成一次会话并结束', '云端与宿主回调均收到数据', 'P1'],
      ['TC-ARC-04', 'LOCAL_ONLY 不出端', 'sessionArchiveMode=LOCAL_ONLY', '完成一次会话并结束', '无回调、无网络请求，仅本地存储', 'P0'],
      ['TC-ARC-05', '回调字段完整性', 'HOST_ONLY', '断言回调字段', 'sessionId / content / scene / createdAt 齐全且类型正确', 'P1'],
      ['TC-ARC-06', '宿主留存失败处理', 'HOST_ONLY 且回调抛异常', '结束会话', 'SDK 记录审计日志，不崩溃，不影响输入', 'P2']
    ]));
    root.appendChild(footer());
  }

  function pageTcCompat(root) {
    root.appendChild(tcHero('兼容与异常', 'Android 厂商 ROM、系统版本、网络与进程异常下的行为验收。'));
    root.appendChild(el('<h2 class="section">兼容与异常用例</h2>'));
    root.appendChild(tcTable([
      ['TC-CMP-01', 'Android 厂商 ROM 安装', '小米 / OPPO / vivo / 三星 真机', '安装并启用', '按 FAQ 提示处理后均可用', 'P0'],
      ['TC-CMP-02', 'Android 16 权限降级', 'Android 16 设备', '集成 automation 并尝试授予 WRITE_SECURE_SETTINGS', '无法授予时自动降级，输入法主体正常', 'P0'],
      ['TC-CMP-03', '断网恢复', '键盘可用', '断网使用输入与 AI，再恢复网络', '断网时离线降级，恢复后自动重连', 'P1'],
      ['TC-CMP-04', '进程被杀恢复', '键盘前台', 'adb 强杀宿主与输入法进程后重新唤起', '键盘可重新拉起，状态自愈', 'P1'],
      ['TC-CMP-05', '多输入法共存', '系统含其他输入法', '来回切换默认输入法', '切换稳定，无崩溃', 'P1'],
      ['TC-CMP-06', '低内存回收', '开发者选项模拟低内存', '触发回收后唤起键盘', '键盘重建可用', 'P2'],
      ['TC-CMP-07', '深浅色主题', '系统深浅色切换', '切换后使用键盘与抽屉', '基座头部随主题，内容无错位', 'P2'],
      ['TC-CMP-08', '横屏与分屏', '支持横屏的设备', '横屏 / 分屏下输入', '键盘布局自适应，输入正常', 'P2']
    ]));
    root.appendChild(footer());
  }

  OEM.registerPages({
    'intro': pageIntro,
    'features': pageFeatures,
    'voice': pageVoice,
    'architecture': pageArchitecture,
    'modules': pageModules,
    'config': pageConfig,
    'ai-reply': pageAiReply,
    'ai-reply-custom': pageAiReplyCustom,
    'material': pageMaterial,
    'material-custom': pageMaterialCustom,
    'custom-drawer': pageCustomDrawer,
    'session-archive': pageSessionArchive,
    'log-management': pageLogManagement,
    'tc-overview': pageTcOverview,
    'tc-env': pageTcEnv,
    'tc-install': pageTcInstall,
    'tc-input': pageTcInput,
    'tc-ai': pageTcAi,
    'tc-material': pageTcMaterial,
    'tc-archive': pageTcArchive,
    'tc-compat': pageTcCompat
  });
})();
