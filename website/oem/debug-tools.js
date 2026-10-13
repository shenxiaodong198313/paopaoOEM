(function () {
  'use strict';
  var OEM = window.OEM;
  var esc = OEM.esc;

  var LS_KEY = 'oem-debug-api-base';

  function apiBase() {
    var v = localStorage.getItem(LS_KEY);
    if (v) return v.replace(/\/+$/, '');
    return window.location.origin;
  }

  function setApiBase(v) {
    localStorage.setItem(LS_KEY, v.replace(/\/+$/, ''));
  }

  function openModal(title, subtitle, builder) {
    var shell = document.getElementById('debug-modal');
    shell.innerHTML = '';
    var backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    var card = document.createElement('div');
    card.className = 'modal-card';
    var head = document.createElement('div');
    head.className = 'modal-head';
    head.innerHTML = '<div><h3>' + esc(title) + '</h3><p>' + esc(subtitle) + '</p></div>';
    var close = document.createElement('button');
    close.className = 'icon-btn';
    close.textContent = '✕';
    close.addEventListener('click', closeModal);
    head.appendChild(close);
    card.appendChild(head);
    var body = document.createElement('div');
    card.appendChild(body);
    shell.appendChild(backdrop);
    shell.appendChild(card);
    backdrop.addEventListener('click', closeModal);
    shell.hidden = false;
    builder(body);
  }

  function closeModal() {
    var shell = document.getElementById('debug-modal');
    shell.hidden = true;
    shell.innerHTML = '';
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeModal();
  });

  function apiBaseBar(body, onSaved) {
    var bar = document.createElement('div');
    bar.className = 'api-base-bar';
    bar.innerHTML =
      '<input type="text" value="' + esc(apiBase()) + '" placeholder="后端 API 地址">' +
      '<button class="btn ghost small" type="button">保存地址</button>' +
      '<span class="tip">默认同源部署（站点与后端同域名）。本地联调可改为如 http://localhost:8080，保存后自动记住。</span>';
    var input = bar.querySelector('input');
    bar.querySelector('button').addEventListener('click', function () {
      setApiBase(input.value.trim() || window.location.origin);
      input.value = apiBase();
      if (onSaved) onSaved();
    });
    body.appendChild(bar);
  }

  function section(body, title) {
    var h = document.createElement('h2');
    h.className = 'section';
    h.innerHTML = esc(title);
    body.appendChild(h);
  }

  function statusLine(body) {
    var p = document.createElement('p');
    p.className = 'status-line';
    body.appendChild(p);
    return p;
  }

  function fieldHTML(label, required, inputHtml) {
    return '<label class="field"><span class="lbl">' + esc(label) + (required ? ' <span class="badge-req">必填</span>' : '') + '</span>' + inputHtml + '</label>';
  }

  function val(form, name) {
    var el = form.querySelector('[name="' + name + '"]');
    return el ? el.value.trim() : '';
  }

  function requireFields(form, names) {
    for (var i = 0; i < names.length; i++) {
      if (!val(form, names[i])) return names[i] + ' 为必填项';
    }
    return null;
  }

  function fetchJson(url, options) {
    return fetch(url, options).then(function (r) {
      return r.json().then(function (data) {
        if (!r.ok || data.code !== 0) {
          throw new Error((data && data.message) || ('HTTP ' + r.status));
        }
        return data.data;
      });
    });
  }

  function postJson(url, payload) {
    return fetchJson(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
  }

  function tableWrap(body) {
    var wrap = document.createElement('div');
    wrap.className = 'tbl-wrap';
    body.appendChild(wrap);
    return wrap;
  }

  function jsonBox(body, label) {
    var p = document.createElement('div');
    p.className = 'panel-label';
    p.textContent = label;
    var ta = document.createElement('textarea');
    ta.className = 'mono';
    ta.readOnly = true;
    ta.rows = 8;
    body.appendChild(p);
    body.appendChild(ta);
    return ta;
  }

  /* ---------------- 素材库调试台 ---------------- */

  function openMaterialConsole() {
    openModal('素材库调试台', '服务端素材增删改查联调：附件上传、素材组 CRUD、JSON 查看。', buildMaterialConsole);
  }

  function buildMaterialConsole(body) {
    var refreshAll = null;
    apiBaseBar(body, function () { if (refreshAll) refreshAll(); });

    section(body, '附件上传');
    var upForm = document.createElement('form');
    upForm.className = 'form-grid';
    upForm.innerHTML =
      fieldHTML('customerId', true, '<input name="customerId" value="demo-enterprise">') +
      fieldHTML('channel', true, '<input name="channel" value="release">') +
      fieldHTML('materialId', true, '<input name="materialId" value="mat-demo-001">') +
      fieldHTML('附件文件', true, '<input name="file" type="file">');
    body.appendChild(upForm);
    var upBtn = document.createElement('button');
    upBtn.className = 'btn';
    upBtn.type = 'button';
    upBtn.textContent = '上传附件';
    body.appendChild(upBtn);
    var upStatus = statusLine(body);

    section(body, '附件列表');
    var attWrap = tableWrap(body);
    var attJson = jsonBox(body, '附件 JSON');

    section(body, '素材组列表');
    var matWrap = tableWrap(body);
    var matJson = jsonBox(body, '素材组 JSON');

    section(body, '素材组编辑');
    var matForm = document.createElement('form');
    matForm.innerHTML =
      '<div class="form-grid">' +
      '<label class="field"><span class="lbl">模式</span><select name="mode"><option value="create">新建素材组</option><option value="update">更新已有素材组</option></select></label>' +
      fieldHTML('customerId', true, '<input name="customerId" value="demo-enterprise">') +
      fieldHTML('channel', true, '<input name="channel" value="release">') +
      fieldHTML('materialId', true, '<input name="materialId" value="mat-demo-001">') +
      fieldHTML('标题', true, '<input name="title" value="企业欢迎素材">') +
      fieldHTML('备注', false, '<input name="remark" value="网站调试台创建">') +
      '</div>' +
      '<div class="panel-label">内容 parts（JSON 数组）</div>' +
      '<textarea name="parts" class="mono" rows="6"></textarea>';
    body.appendChild(matForm);

    var partBar = document.createElement('div');
    partBar.className = 'form-grid';
    partBar.innerHTML =
      '<label class="field"><span class="lbl">partType</span><select id="part-type"><option value="text">text</option><option value="image">image</option></select></label>' +
      fieldHTML('标题', false, '<input id="part-title" value="欢迎语">') +
      fieldHTML('文本内容', false, '<input id="part-content" value="你好，欢迎使用企业输入法">') +
      fieldHTML('图片地址', false, '<input id="part-image" placeholder="https://...">');
    body.appendChild(partBar);
    var partBtns = document.createElement('div');
    partBtns.style.display = 'flex';
    partBtns.style.gap = '8px';
    partBtns.style.marginBottom = '12px';
    partBtns.innerHTML = '<button class="btn ghost small" type="button" id="part-add">追加 part</button><button class="btn ghost small" type="button" id="part-seed">填入示例 parts</button>';
    body.appendChild(partBtns);

    var saveBtn = document.createElement('button');
    saveBtn.className = 'btn';
    saveBtn.type = 'button';
    saveBtn.textContent = '保存素材组';
    body.appendChild(saveBtn);
    var matStatus = statusLine(body);

    function currentParts() {
      try {
        var v = JSON.parse(matForm.querySelector('[name="parts"]').value || '[]');
        return Array.isArray(v) ? v : [];
      } catch (e) { return []; }
    }

    partBar.ownerDocument.getElementById('part-add').addEventListener('click', function () {
      var parts = currentParts();
      var type = partBar.querySelector('#part-type').value;
      var part = { partType: type, title: partBar.querySelector('#part-title').value.trim() };
      if (type === 'text') part.content = partBar.querySelector('#part-content').value.trim();
      if (type === 'image') part.imageUrl = partBar.querySelector('#part-image').value.trim();
      parts.push(part);
      matForm.querySelector('[name="parts"]').value = JSON.stringify(parts, null, 2);
    });
    partBar.ownerDocument.getElementById('part-seed').addEventListener('click', function () {
      matForm.querySelector('[name="parts"]').value = JSON.stringify([
        { partType: 'text', title: '欢迎语', content: '你好，欢迎使用企业输入法' },
        { partType: 'image', title: '活动图', imageUrl: 'https://static.example.com/banner.png' }
      ], null, 2);
    });

    function renderAttachments(items) {
      var rows = (items || []).map(function (it) {
        return '<tr><td>' + esc(it.attachmentId) + '</td><td>' + esc(it.materialId) + '</td><td>' + esc(it.fileName) + '</td><td>' + esc(OEM.formatFileSize(it.fileSize)) + '</td><td>' + esc(it.contentType || '-') + '</td><td>' + esc(OEM.formatDateTime(it.updatedAt)) + '</td></tr>';
      }).join('');
      attWrap.innerHTML = '<table class="tbl"><thead><tr><th>attachmentId</th><th>materialId</th><th>文件名</th><th>大小</th><th>类型</th><th>更新时间</th></tr></thead><tbody>' + (rows || '<tr><td colspan="6">暂无附件</td></tr>') + '</tbody></table>';
      attJson.value = JSON.stringify(items || [], null, 2);
    }

    function renderMaterials(items) {
      var rows = (items || []).map(function (it) {
        return '<tr data-id="' + esc(it.materialId) + '"><td>' + esc(it.materialId) + '</td><td>' + esc(it.title) + '</td><td>' + (it.parts || []).length + '</td><td>' + esc(OEM.formatDateTime(it.updatedAt)) + '</td><td><button class="btn ghost small" type="button" data-act="fill">回填表单</button> <button class="btn danger small" type="button" data-act="del">删除</button></td></tr>';
      }).join('');
      matWrap.innerHTML = '<table class="tbl"><thead><tr><th>materialId</th><th>标题</th><th>parts</th><th>更新时间</th><th>操作</th></tr></thead><tbody>' + (rows || '<tr><td colspan="5">暂无素材组</td></tr>') + '</tbody></table>';
      matJson.value = JSON.stringify(items || [], null, 2);
      matWrap.querySelectorAll('button[data-act]').forEach(function (btn) {
        btn.addEventListener('click', function (e) {
          e.stopPropagation();
          var id = btn.closest('tr').getAttribute('data-id');
          var item = (items || []).find(function (x) { return x.materialId === id; });
          if (!item) return;
          if (btn.getAttribute('data-act') === 'fill') {
            matForm.querySelector('[name="mode"]').value = 'update';
            matForm.querySelector('[name="customerId"]').value = item.customerId || '';
            matForm.querySelector('[name="channel"]').value = item.channel || '';
            matForm.querySelector('[name="materialId"]').value = item.materialId || '';
            matForm.querySelector('[name="title"]').value = item.title || '';
            matForm.querySelector('[name="remark"]').value = item.remark || '';
            matForm.querySelector('[name="parts"]').value = JSON.stringify(item.parts || [], null, 2);
            matStatus.textContent = '已回填素材组 ' + id + '，可直接修改后保存。';
            matStatus.className = 'status-line';
          } else {
            if (!window.confirm('删除素材组 ' + id + '？')) return;
            fetchJson(apiBase() + '/api/v1/oem/material-library-demo/materials/' + encodeURIComponent(id), { method: 'DELETE' })
              .then(function () { matStatus.textContent = '已删除 ' + id; matStatus.className = 'status-line'; refreshAll(); })
              .catch(function (err) { matStatus.textContent = '删除失败：' + err.message; matStatus.className = 'status-line err'; });
          }
        });
      });
    }

    function loadAttachments() {
      return fetchJson(apiBase() + '/api/v1/oem/material-library-demo/attachments?customerId=demo-enterprise&channel=release')
        .then(renderAttachments)
        .catch(function (err) { attWrap.innerHTML = ''; attJson.value = ''; upStatus.textContent = '附件列表加载失败：' + err.message + '（请确认后端地址与 demo 接口已部署）'; upStatus.className = 'status-line err'; });
    }

    function loadMaterials() {
      return fetchJson(apiBase() + '/api/v1/oem/material-library-demo/materials?customerId=demo-enterprise&channel=release')
        .then(renderMaterials)
        .catch(function (err) { matWrap.innerHTML = ''; matJson.value = ''; matStatus.textContent = '素材列表加载失败：' + err.message + '（请确认后端地址与 demo 接口已部署）'; matStatus.className = 'status-line err'; });
    }

    refreshAll = function () { loadAttachments(); loadMaterials(); };

    upBtn.addEventListener('click', function () {
      var err = requireFields(upForm, ['customerId', 'channel', 'materialId']);
      var fileInput = upForm.querySelector('[name="file"]');
      if (!err && (!fileInput.files || !fileInput.files.length)) err = '请选择附件文件';
      if (err) { upStatus.textContent = err; upStatus.className = 'status-line err'; return; }
      var fd = new FormData();
      fd.append('customerId', val(upForm, 'customerId'));
      fd.append('channel', val(upForm, 'channel'));
      fd.append('materialId', val(upForm, 'materialId'));
      fd.append('file', fileInput.files[0]);
      upStatus.textContent = '上传中…';
      upStatus.className = 'status-line';
      fetchJson(apiBase() + '/api/v1/oem/material-library-demo/attachments', { method: 'POST', body: fd })
        .then(function (data) { upStatus.textContent = '上传成功：' + (data.attachmentId || ''); upStatus.className = 'status-line'; loadAttachments(); })
        .catch(function (e2) { upStatus.textContent = '上传失败：' + e2.message; upStatus.className = 'status-line err'; });
    });

    saveBtn.addEventListener('click', function () {
      var err = requireFields(matForm, ['customerId', 'channel', 'materialId', 'title']);
      var parts = currentParts();
      if (!err && !parts.length) err = 'parts 至少需要一个内容块';
      if (err) { matStatus.textContent = err; matStatus.className = 'status-line err'; return; }
      var payload = {
        customerId: val(matForm, 'customerId'),
        channel: val(matForm, 'channel'),
        materialId: val(matForm, 'materialId'),
        title: val(matForm, 'title'),
        remark: val(matForm, 'remark'),
        parts: parts
      };
      matStatus.textContent = '保存中…';
      matStatus.className = 'status-line';
      postJson(apiBase() + '/api/v1/oem/material-library-demo/materials', payload)
        .then(function () { matStatus.textContent = '保存成功'; matStatus.className = 'status-line'; loadMaterials(); })
        .catch(function (e2) { matStatus.textContent = '保存失败：' + e2.message; matStatus.className = 'status-line err'; });
    });

    matForm.querySelector('[name="parts"]').value = JSON.stringify([
      { partType: 'text', title: '欢迎语', content: '你好，欢迎使用企业输入法' }
    ], null, 2);

    refreshAll();
  }

  /* ---------------- 会话存档调试台 ---------------- */

  function openSessionConsole() {
    openModal('会话存档调试台', '输入质检会话联调：提交质检会话、查看最近 10 条记录与完整 JSON。', buildSessionConsole);
  }

  function buildSessionConsole(body) {
    var refresh = null;
    apiBaseBar(body, function () { if (refresh) refresh(); });

    section(body, '提交质检会话');
    var form = document.createElement('form');
    form.className = 'form-grid';
    form.innerHTML =
      fieldHTML('customerId', true, '<input name="customerId" value="demo-enterprise">') +
      fieldHTML('channel', true, '<input name="channel" value="release">') +
      fieldHTML('sessionId', true, '<input name="sessionId" value="qa-demo-' + Date.now() + '">') +
      fieldHTML('输入文本', true, '<input name="inputText" value="今天安排客户回访，记得同步质检结果">') +
      fieldHTML('质检结果', false, '<input name="qaResult" value="网站调试台提交">');
    body.appendChild(form);
    var submitBtn = document.createElement('button');
    submitBtn.className = 'btn';
    submitBtn.type = 'button';
    submitBtn.textContent = '提交会话';
    body.appendChild(submitBtn);
    var status = statusLine(body);

    section(body, '最近 10 条会话（点击查看 JSON）');
    var wrap = tableWrap(body);
    var jsonTa = jsonBox(body, '会话 JSON');

    function render(items) {
      var rows = (items || []).map(function (it, idx) {
        return '<tr data-idx="' + idx + '"><td>' + esc(it.sessionId) + '</td><td>' + esc(it.customerId) + '</td><td>' + esc(it.channel) + '</td><td>' + esc(it.inputText || '') + '</td><td>' + esc(it.qaResult || '-') + '</td><td>' + esc(OEM.formatDateTime(it.createdAt)) + '</td></tr>';
      }).join('');
      wrap.innerHTML = '<table class="tbl"><thead><tr><th>sessionId</th><th>customerId</th><th>channel</th><th>inputText</th><th>qaResult</th><th>createdAt</th></tr></thead><tbody>' + (rows || '<tr><td colspan="6">暂无会话</td></tr>') + '</tbody></table>';
      wrap.querySelectorAll('tbody tr[data-idx]').forEach(function (tr) {
        tr.addEventListener('click', function () {
          wrap.querySelectorAll('tr').forEach(function (x) { x.classList.remove('is-active'); });
          tr.classList.add('is-active');
          jsonTa.value = JSON.stringify(items[Number(tr.getAttribute('data-idx'))], null, 2);
        });
      });
    }

    refresh = function () {
      fetchJson(apiBase() + '/api/v1/oem/input-qa-demo/sessions?limit=10')
        .then(render)
        .catch(function (err) { wrap.innerHTML = ''; status.textContent = '会话列表加载失败：' + err.message + '（请确认后端地址与 demo 接口已部署）'; status.className = 'status-line err'; });
    };

    submitBtn.addEventListener('click', function () {
      var err = requireFields(form, ['customerId', 'channel', 'sessionId', 'inputText']);
      if (err) { status.textContent = err; status.className = 'status-line err'; return; }
      status.textContent = '提交中…';
      status.className = 'status-line';
      postJson(apiBase() + '/api/v1/oem/input-qa-demo/sessions', {
        customerId: val(form, 'customerId'),
        channel: val(form, 'channel'),
        sessionId: val(form, 'sessionId'),
        inputText: val(form, 'inputText'),
        qaResult: val(form, 'qaResult')
      }).then(function () {
        status.textContent = '提交成功';
        status.className = 'status-line';
        form.querySelector('[name="sessionId"]').value = 'qa-demo-' + Date.now();
        refresh();
      }).catch(function (e2) {
        status.textContent = '提交失败：' + e2.message;
        status.className = 'status-line err';
      });
    });

    refresh();
  }

  /* ---------------- AI 回复调试台（宿主回调 payload 模拟器） ---------------- */

  var TONE_LABELS = { friendly: '友好', concise: '简洁', professional: '专业', formal: '正式' };
  var MODE_USER_PROMPTS = {
    FAST_LLM: '请根据当前屏幕聊天上下文，生成可直接发送的简短回复。',
    OCR: '请根据屏幕 OCR 识别出的聊天内容，生成得体的回复。',
    IMAGE_REPLY: '请根据用户选择的图片内容，生成得体的回复。',
    PASTE: '请对即将粘贴的内容进行智能改写润色。'
  };

  function openAiReplyConsole() {
    openModal('AI 回复调试台', '宿主回调 payload 模拟：生成 OemAiReplyRequest、模拟宿主回传 OemAiReplyResult、预演三种分发模式行为。', buildAiReplyConsole);
  }

  function buildAiReplyConsole(body) {
    var currentRequest = null;

    var note = document.createElement('div');
    note.className = 'card';
    note.innerHTML = '<p>本调试台为<b>纯前端模拟</b>，不连接真实后端与 SDK，用于帮助宿主开发者直观理解 AI 回复宿主回调的请求 / 结果 payload 结构与分发模式行为。真实集成请见 wiki「AI回复 → ③ 宿主回调对接」。</p>';
    body.appendChild(note);

    section(body, '时间线');
    var logList = document.createElement('ul');
    logList.className = 'debug-log';
    body.appendChild(logList);
    function log(line) {
      var li = document.createElement('li');
      li.textContent = '[' + new Date().toLocaleTimeString('zh-CN') + '] ' + line;
      logList.appendChild(li);
      logList.scrollTop = logList.scrollHeight;
    }
    log('调试台就绪：请先在 ① 生成请求 payload，再在 ② 模拟宿主回传结果。');

    section(body, '① 模拟 SDK 派发请求（SDK → 宿主）');
    var reqForm = document.createElement('form');
    reqForm.className = 'form-grid';
    reqForm.innerHTML =
      fieldHTML('generateMode', true, '<select name="generateMode"><option value="FAST_LLM">FAST_LLM（极速回复）</option><option value="OCR">OCR（读屏回复）</option><option value="IMAGE_REPLY">IMAGE_REPLY（图片回复）</option><option value="PASTE">PASTE（智能改写）</option></select>') +
      fieldHTML('语气 toneKey', true, '<select name="tone"><option value="friendly">friendly（友好）</option><option value="concise">concise（简洁）</option><option value="professional">professional（专业）</option><option value="formal">formal（正式）</option></select>') +
      fieldHTML('replyCount', true, '<input name="replyCount" type="number" value="3" min="1" max="10">') +
      fieldHTML('ocrText', false, '<input name="ocrText" value="客户：你们的产品支持企业私有化部署吗？">') +
      fieldHTML('截屏张数', false, '<select name="shots"><option value="1">1 张</option><option value="2">2 张（连拍）</option><option value="0">无截屏</option></select>');
    body.appendChild(reqForm);
    var reqBtn = document.createElement('button');
    reqBtn.className = 'btn';
    reqBtn.type = 'button';
    reqBtn.textContent = '生成请求 payload';
    body.appendChild(reqBtn);
    var reqStatus = statusLine(body);
    var reqJson = jsonBox(body, 'OemAiReplyRequest JSON');

    section(body, '② 模拟宿主回传结果（宿主 → SDK）');
    var resForm = document.createElement('form');
    resForm.className = 'form-grid';
    resForm.innerHTML =
      '<label class="field" style="grid-column:1/-1"><span class="lbl">replies <span class="badge-req">必填</span>（一行一条）</span><textarea name="replies" rows="4" class="mono">您好，我们产品支持完全私有化部署\n您好，支持企业私有化部署，欢迎随时咨询</textarea></label>' +
      fieldHTML('success', true, '<select name="success"><option value="true">true（生成成功）</option><option value="false">false（生成失败）</option></select>') +
      fieldHTML('errorMessage', false, '<input name="errorMessage" placeholder="失败原因（success=false 时返回）">') +
      fieldHTML('modelName', false, '<input name="modelName" value="enterprise-gpt">');
    body.appendChild(resForm);
    var resBtn = document.createElement('button');
    resBtn.className = 'btn';
    resBtn.type = 'button';
    resBtn.textContent = '回传结果给 SDK';
    body.appendChild(resBtn);
    var resStatus = statusLine(body);
    var resJson = jsonBox(body, 'OemAiReplyResult JSON');

    section(body, '③ 分发模式行为预演');
    var modeForm = document.createElement('form');
    modeForm.className = 'form-grid';
    modeForm.innerHTML = fieldHTML('分发模式', true, '<select name="mode"><option value="CLOUD_ONLY">CLOUD_ONLY（域名配置）</option><option value="HOST_ONLY">HOST_ONLY（宿主回调）</option><option value="DUAL_WRITE">DUAL_WRITE（双通道容错）</option></select>');
    body.appendChild(modeForm);
    var failBtn = document.createElement('button');
    failBtn.className = 'btn ghost';
    failBtn.type = 'button';
    failBtn.textContent = '模拟宿主通道失败（返回 false / 未注册 / 超时）';
    body.appendChild(failBtn);
    var modeStatus = statusLine(body);

    reqBtn.addEventListener('click', function () {
      var generateMode = reqForm.querySelector('[name="generateMode"]').value;
      var toneKey = reqForm.querySelector('[name="tone"]').value;
      var replyCount = Math.max(1, Number(reqForm.querySelector('[name="replyCount"]').value) || 3);
      var shots = Number(reqForm.querySelector('[name="shots"]').value) || 0;
      var screenshots = [];
      for (var i = 0; i < shots; i++) {
        screenshots.push({
          imageBase64: 'iVBORw0KGgoAAAANSUhEUg…（JPEG Base64 占位；真实回调为完整截屏数据，宽缩放至 1280 内）',
          mimeType: 'image/jpeg',
          width: 1280,
          height: 720
        });
      }
      currentRequest = {
        requestId: 'ai-demo-' + Date.now(),
        sessionId: 'panel-demo-1',
        generateMode: generateMode,
        toneKey: toneKey,
        toneLabel: TONE_LABELS[toneKey],
        systemPrompt: '你是企业输入法 AI 回复助手，请基于截屏与上下文，以「' + TONE_LABELS[toneKey] + '」语气生成 ' + replyCount + ' 条可直接发送的回复。',
        userPrompt: MODE_USER_PROMPTS[generateMode],
        ocrText: reqForm.querySelector('[name="ocrText"]').value.trim(),
        screenshots: screenshots,
        replyCount: replyCount,
        temperature: 0.7,
        topP: 0.9,
        maxTokens: 512,
        timestamp: Date.now(),
        hostApplicationId: 'com.example.host'
      };
      reqJson.value = JSON.stringify(currentRequest, null, 2);
      reqStatus.textContent = '已生成 OemAiReplyRequest（requestId=' + currentRequest.requestId + '），请在 ② 模拟宿主回传。';
      reqStatus.className = 'status-line';
      log('SDK → 宿主：派发 OemAiReplyRequest（requestId=' + currentRequest.requestId + '，generateMode=' + generateMode + '，截屏 ' + shots + ' 张）');
    });

    resBtn.addEventListener('click', function () {
      if (!currentRequest) {
        resStatus.textContent = '请先在 ① 生成请求 payload，回传结果需要携带一致的 requestId';
        resStatus.className = 'status-line err';
        return;
      }
      var success = resForm.querySelector('[name="success"]').value === 'true';
      var replies = resForm.querySelector('[name="replies"]').value.split('\n').map(function (s) { return s.trim(); }).filter(Boolean);
      if (success && !replies.length) {
        resStatus.textContent = 'success=true 时 replies 至少需要一条';
        resStatus.className = 'status-line err';
        return;
      }
      var result = {
        requestId: currentRequest.requestId,
        success: success,
        replies: success ? replies : [],
        errorMessage: success ? null : (resForm.querySelector('[name="errorMessage"]').value.trim() || '宿主大模型生成失败'),
        modelName: resForm.querySelector('[name="modelName"]').value.trim() || null
      };
      resJson.value = JSON.stringify(result, null, 2);
      resStatus.className = 'status-line';
      if (success) {
        resStatus.textContent = '模拟 SDK 已接收：AI 回复面板展示 ' + replies.length + ' 条回复（provider=host_callback）';
        log('宿主 → SDK：回传 OemAiReplyResult（success=true，' + replies.length + ' 条回复）→ 面板展示');
      } else {
        resStatus.textContent = '模拟 SDK 已接收：向用户提示失败「' + result.errorMessage + '」';
        log('宿主 → SDK：回传 OemAiReplyResult（success=false，' + result.errorMessage + '）→ 面板提示失败');
      }
    });

    failBtn.addEventListener('click', function () {
      var mode = modeForm.querySelector('[name="mode"]').value;
      var behavior = {
        CLOUD_ONLY: '不调用宿主回调，SDK 直接走域名通道（apiBaseUrl 代理 / 直连），宿主回调失败与否均不影响。',
        HOST_ONLY: 'SDK 判定宿主通道不可用 → 本次 AI 回复失败，面板向用户提示「生成失败」，不回落任何域名通道。',
        DUAL_WRITE: 'SDK 自动回落域名通道（apiBaseUrl 代理 / 直连）继续生成，用户无感知。'
      }[mode];
      modeStatus.textContent = mode + '：' + behavior;
      modeStatus.className = 'status-line';
      log('模式预演：' + mode + ' 下宿主通道失败 → ' + behavior);
    });
  }

  window.OEM_DEBUG = {
    openMaterialConsole: openMaterialConsole,
    openSessionConsole: openSessionConsole,
    openAiReplyConsole: openAiReplyConsole
  };
})();
