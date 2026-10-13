(function () {
  'use strict';

  var BTN_STYLE = '' +
    '.md-copy-btn{position:fixed;top:96px;right:36px;z-index:80;display:inline-flex;align-items:center;gap:7px;' +
    'padding:7px 14px;font-size:12.5px;letter-spacing:.5px;color:#9fb0c7;background:rgba(13,19,32,.92);' +
    'border:1px solid #1d2a3f;border-radius:8px;cursor:pointer;backdrop-filter:blur(6px);font-family:inherit;' +
    'transition:color .15s,border-color .15s;}' +
    '.md-copy-btn.in-doc{top:16px;}' +
    '.md-copy-btn:hover{color:#e6edf7;border-color:#64748b;}' +
    '.md-copy-btn svg{flex:none;}' +
    '.md-copy-btn.ok{color:#7ee787;border-color:#7ee787;}' +
    '.md-copy-btn.err{color:#ff8f8f;border-color:#ff8f8f;}' +
    '@media (max-width:720px){.md-copy-btn{top:64px;padding:6px 10px;font-size:12px;}}';

  var COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>';

  var INLINE_TAGS = { A: 1, B: 1, STRONG: 1, EM: 1, I: 1, CODE: 1, SPAN: 1, SMALL: 1, SUB: 1, SUP: 1, BR: 1, IMG: 1, TIME: 1, KBD: 1, MARK: 1 };
  var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, BUTTON: 1, INPUT: 1, SELECT: 1, TEXTAREA: 1, SVG: 1, CANVAS: 1, IFRAME: 1, VIDEO: 1, AUDIO: 1 };

  function cleanText(v) {
    return String(v || '').replace(/\s+/g, ' ');
  }

  function mdInline(node) {
    var out = '';
    node.childNodes.forEach(function (child) {
      if (child.nodeType === 3) {
        out += cleanText(child.nodeValue);
        return;
      }
      if (child.nodeType !== 1) return;
      var tag = child.tagName;
      if (SKIP_TAGS[tag]) return;
      if (tag === 'BR') { out += '\n'; return; }
      if (tag === 'STRONG' || tag === 'B') {
        var b = mdInline(child).trim();
        if (b) out += '**' + b + '**';
        return;
      }
      if (tag === 'EM' || tag === 'I') {
        var e = mdInline(child).trim();
        if (e) out += '*' + e + '*';
        return;
      }
      if (tag === 'CODE') {
        var c = child.textContent.replace(/\s+/g, ' ').trim();
        if (c) out += '`' + c + '`';
        return;
      }
      if (tag === 'A') {
        var txt = mdInline(child).trim();
        var href = child.getAttribute('href') || '';
        if (!txt) return;
        if (!href || href.indexOf('javascript:') === 0 || href.charAt(0) === '#') { out += txt; return; }
        out += '[' + txt + '](' + href + ')';
        return;
      }
      if (tag === 'IMG') {
        var src = child.getAttribute('src') || '';
        if (src) out += '![' + (child.getAttribute('alt') || '') + '](' + src + ')';
        return;
      }
      out += mdInline(child);
    });
    return out;
  }

  function mdSelf(node) {
    var wrap = node.ownerDocument.createElement('div');
    wrap.appendChild(node.cloneNode(true));
    return mdInline(wrap);
  }

  function isInlineOnly(el) {
    var hasElement = false;
    for (var i = 0; i < el.childNodes.length; i++) {
      var child = el.childNodes[i];
      if (child.nodeType === 1) {
        hasElement = true;
        if (!INLINE_TAGS[child.tagName]) return false;
      }
    }
    return hasElement;
  }

  function fenceLang(pre) {
    var card = pre.closest ? pre.closest('.code-card') : null;
    var head = card && card.querySelector('.code-head h3, .code-head h4');
    var title = head ? head.textContent : '';
    if (/powershell|ps1|shell|bash/i.test(title)) return 'powershell';
    if (/\.kt|kotlin|gradle/i.test(title)) return 'kotlin';
    if (/\.java|java/i.test(title)) return 'java';
    if (/xml|manifest/i.test(title)) return 'xml';
    if (/json/i.test(title)) return 'json';
    if (/yaml|yml/i.test(title)) return 'yaml';
    return '';
  }

  function mdCodeBlock(pre) {
    var lang = fenceLang(pre);
    var body = pre.textContent.replace(/\n+$/, '');
    return '```' + lang + '\n' + body + '\n```';
  }

  function mdList(list, ordered, indent) {
    var lines = [];
    var idx = 1;
    list.childNodes.forEach(function (li) {
      if (li.nodeType !== 1 || li.tagName !== 'LI') return;
      var bullet = ordered ? (idx++) + '. ' : '- ';
      var inlinePart = '';
      var nested = [];
      li.childNodes.forEach(function (c) {
        if (c.nodeType === 3) { inlinePart += cleanText(c.nodeValue); return; }
        if (c.nodeType !== 1) return;
        if (c.tagName === 'UL' || c.tagName === 'OL') { nested.push(c); return; }
        if (c.tagName === 'BR') { inlinePart += ' '; return; }
        inlinePart += mdInline(c);
      });
      var first = cleanText(inlinePart).trim();
      var rest = indent + '  ';
      lines.push(indent + bullet + first.replace(/\n/g, '\n' + rest));
      nested.forEach(function (n) {
        mdList(n, n.tagName === 'OL', indent + '  ').forEach(function (l) { lines.push(l); });
      });
    });
    return lines;
  }

  function mdTableCell(cell) {
    return mdInline(cell).trim().replace(/\|/g, '\\|').replace(/\n/g, ' ') || ' ';
  }

  function mdTable(table) {
    var rows = table.querySelectorAll('tr');
    if (!rows.length) return '';
    var out = [];
    var first = rows[0];
    var hasHead = first.querySelectorAll('th').length > 0;
    var startAt = 0;
    if (hasHead) {
      var headCells = first.querySelectorAll('th,td');
      out.push('| ' + Array.prototype.map.call(headCells, mdTableCell).join(' | ') + ' |');
      out.push('| ' + Array.prototype.map.call(headCells, function () { return '---'; }).join(' | ') + ' |');
      startAt = 1;
    }
    for (var i = startAt; i < rows.length; i++) {
      var cells = rows[i].querySelectorAll('th,td');
      if (!cells.length) continue;
      if (!hasHead && i === 0) {
        out.push('| ' + Array.prototype.map.call(cells, mdTableCell).join(' | ') + ' |');
        out.push('| ' + Array.prototype.map.call(cells, function () { return '---'; }).join(' | ') + ' |');
        continue;
      }
      out.push('| ' + Array.prototype.map.call(cells, mdTableCell).join(' | ') + ' |');
    }
    return out.join('\n');
  }

  function mdFigure(fig) {
    var parts = [];
    var img = fig.querySelector('img');
    if (img && img.getAttribute('src')) parts.push('![' + (img.getAttribute('alt') || '') + '](' + img.getAttribute('src') + ')');
    var cap = fig.querySelector('figcaption');
    if (cap) {
      var t = mdInline(cap).trim();
      if (t) parts.push('*' + t + '*');
    }
    return parts.join('\n\n');
  }

  function mdBlocks(el) {
    var blocks = [];
    el.childNodes.forEach(function (child) {
      if (child.nodeType === 3) {
        var t = cleanText(child.nodeValue).trim();
        if (t) blocks.push(t);
        return;
      }
      if (child.nodeType !== 1) return;
      var node = child;
      var tag = node.tagName;
      if (SKIP_TAGS[tag]) return;
      if (node.classList.contains('mdcopy-ignore')) return;
      var m = /^H([1-6])$/.exec(tag);
      if (m) {
        var ht = mdInline(node).trim();
        if (ht) blocks.push(Array(parseInt(m[1], 10) + 1).join('#') + ' ' + ht);
        return;
      }
      switch (tag) {
        case 'P': {
          var pt = mdInline(node).trim();
          if (pt) blocks.push(pt);
          return;
        }
        case 'PRE':
          blocks.push(mdCodeBlock(node));
          return;
        case 'UL':
          blocks.push(mdList(node, false, '').join('\n'));
          return;
        case 'OL':
          blocks.push(mdList(node, true, '').join('\n'));
          return;
        case 'TABLE':
          blocks.push(mdTable(node));
          return;
        case 'FIGURE':
          blocks.push(mdFigure(node));
          return;
        case 'BLOCKQUOTE': {
          var inner = mdBlocks(node);
          if (inner) blocks.push(inner.split('\n').map(function (l) { return '> ' + l; }).join('\n'));
          return;
        }
        case 'HR':
          blocks.push('---');
          return;
        default:
          break;
      }
      if (INLINE_TAGS[tag]) {
        var at = mdSelf(node).trim();
        if (at) blocks.push(at);
        return;
      }
      if (node.classList.contains('panel-label') || node.classList.contains('eyebrow')) {
        var lt = mdInline(node).trim();
        if (lt) blocks.push('**' + lt + '**');
        return;
      }
      if (node.classList.contains('kv')) {
        var kt = mdInline(node).trim();
        if (kt) blocks.push('- ' + kt);
        return;
      }
      if (isInlineOnly(node)) {
        var it = mdInline(node).trim();
        if (it) blocks.push(it);
        return;
      }
      var sub = mdBlocks(node);
      if (sub) blocks.push(sub);
    });
    return blocks.join('\n\n');
  }

  function findRoot() {
    return document.getElementById('page-root') ||
      document.querySelector('main.doc-content') ||
      document.querySelector('.wrap') ||
      document.querySelector('main') ||
      document.body;
  }

  function buildMarkdown(root) {
    var body = mdBlocks(root).replace(/\n{3,}/g, '\n\n').trim();
    var head = '> 来源：' + location.href.split('#')[0] + (location.hash || '') + '　|　复制时间：' + new Date().toLocaleString('zh-CN');
    return head + '\n\n' + body + '\n';
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      var ok = false;
      try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
      ta.remove();
      if (ok) resolve(); else reject(new Error('execCommand copy failed'));
    });
  }

  function setFeedback(btn, ok) {
    var label = btn.querySelector('span');
    btn.classList.remove('ok', 'err');
    if (ok === true) {
      btn.classList.add('ok');
      label.textContent = '已复制';
    } else if (ok === false) {
      btn.classList.add('err');
      label.textContent = '复制失败';
    } else {
      label.textContent = '复制 MD 格式';
    }
  }

  function inject() {
    var style = document.createElement('style');
    style.textContent = BTN_STYLE;
    document.head.appendChild(style);
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'md-copy-btn';
    btn.className = 'md-copy-btn';
    btn.title = '一键复制当前页面的 Markdown 格式内容';
    btn.innerHTML = COPY_ICON + '<span>复制 MD 格式</span>';
    document.body.appendChild(btn);
    var alignTimer = null;
    function align() {
      var root = findRoot();
      if (root.id !== 'page-root') btn.classList.add('in-doc'); else btn.classList.remove('in-doc');
      var rect = root.getBoundingClientRect();
      var pad = parseFloat(getComputedStyle(root).paddingRight) || 0;
      var right = Math.round(window.innerWidth - rect.right + pad);
      btn.style.right = Math.max(8, right) + 'px';
    }
    function alignSoon() {
      if (alignTimer) clearTimeout(alignTimer);
      alignTimer = setTimeout(align, 0);
    }
    window.addEventListener('resize', alignSoon);
    window.addEventListener('hashchange', alignSoon);
    document.addEventListener('click', alignSoon);
    align();
    var timer = null;
    btn.addEventListener('click', function () {
      var md = buildMarkdown(findRoot());
      copyText(md).then(function () {
        setFeedback(btn, true);
      }).catch(function () {
        setFeedback(btn, false);
      }).then(function () {
        if (timer) clearTimeout(timer);
        timer = setTimeout(function () { setFeedback(btn, null); }, 1600);
      });
    });
  }

  window.MdCopy = { buildMarkdown: buildMarkdown, findRoot: findRoot };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', inject);
  } else {
    inject();
  }
})();
