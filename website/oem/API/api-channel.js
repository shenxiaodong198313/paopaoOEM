const escapeHtml = (value) =>
  String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

const renderJsonText = (node, value) => {
  if (!node) {
    return;
  }
  node.textContent = JSON.stringify(value ?? {}, null, 2);
};

const formatDateTime = (value) => {
  if (!value) {
    return "";
  }
  const date = typeof value === "number" ? new Date(value) : new Date(String(value));
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString("zh-CN", { hour12: false });
};

const formatFileSize = (value) => {
  const size = Number(value || 0);
  if (!Number.isFinite(size) || size <= 0) {
    return "-";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

const safeFetchJson = async (url, options) => {
  const response = await fetch(url, options);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.success === false) {
    throw new Error(payload?.error?.message || payload?.message || "请求失败");
  }
  return payload;
};

const parseTags = (value) =>
  String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

const hasSuspiciousGarbledText = (value) => {
  const text = String(value ?? "");
  return text.includes("�") || /\?{5,}/.test(text);
};

const buildTextExampleParts = () => [
  {
    partId: "part_text_demo",
    type: "text",
    text: "这是一条来自 OEM 官网 API 频道的素材演示文案。",
  },
];

const buildAttachmentExampleParts = () => [
  {
    partId: "part_image_demo",
    type: "attachment",
    assetId: "asset_demo_cover",
    displayName: "oem-cover.png",
    mimeType: "image/png",
    attachmentType: "image",
    fileSizeBytes: 182736,
    downloadUrl: "https://www.paopaoime.com/oem/assets/demo/oem-cover.png",
  },
];

const buildGroupExampleParts = () => [
  {
    partId: "part_text_intro",
    type: "text",
    text: "您好，这里是泡泡输入法 OEM 方案资料，请查收。",
  },
  {
    partId: "part_image_demo",
    type: "attachment",
    assetId: "asset_demo_cover",
    displayName: "oem-cover.png",
    mimeType: "image/png",
    attachmentType: "image",
    fileSizeBytes: 182736,
    downloadUrl: "https://www.paopaoime.com/oem/assets/demo/oem-cover.png",
  },
  {
    partId: "part_doc_demo",
    type: "attachment",
    assetId: "asset_demo_intro",
    displayName: "oem-intro.pdf",
    mimeType: "application/pdf",
    attachmentType: "document",
    fileSizeBytes: 548120,
    downloadUrl: "https://www.paopaoime.com/oem/downloads/oem-sdk-delivery-latest.zip",
  },
  {
    partId: "part_text_followup",
    type: "text",
    text: "如需正式对接，可在企业自己的 apiBaseUrl 下实现相同字段协议。",
  },
];

const initQaDemo = () => {
  const endpointBase = `${window.location.origin}/api/v1/oem/input-qa-demo/sessions`;
  const form = document.getElementById("qa-demo-form");
  if (!form) {
    return;
  }
  const statusNode = document.getElementById("submit-status");
  const tableStatus = document.getElementById("table-status");
  const sessionTableBody = document.getElementById("session-table-body");
  const sessionJson = document.getElementById("session-json");
  const refreshButton = document.getElementById("refresh-list");
  const refreshTableButton = document.getElementById("refresh-table");
  let selectedSessionId = "";

  const isDisplayableSession = (item) =>
    ![item?.qaContent, item?.appName, item?.host].some(hasSuspiciousGarbledText);

  const renderHost = (item) => {
    const hostName = item.host || item.appName || item.packageName || "未知宿主";
    const packageName = item.packageName || "";
    return packageName && packageName !== hostName ? `${hostName}\n${packageName}` : hostName;
  };

  const renderSessions = (items) => {
    sessionTableBody.innerHTML = "";
    if (!items.length) {
      sessionTableBody.innerHTML = '<tr><td colspan="3" class="qa-empty">当前还没有演示数据。</td></tr>';
      renderJsonText(sessionJson, {});
      selectedSessionId = "";
      return;
    }
    if (!selectedSessionId || !items.some((item) => item.sessionId === selectedSessionId)) {
      selectedSessionId = items[0].sessionId;
    }
    items.forEach((item) => {
      const row = document.createElement("tr");
      if (item.sessionId === selectedSessionId) {
        row.classList.add("is-active");
      }
      row.innerHTML = `
        <td class="qa-text-cell" title="${escapeHtml(item.qaContent || "(空)")}" >${escapeHtml(item.qaContent || "(空)")}</td>
        <td class="qa-host-cell">${escapeHtml(renderHost(item)).replace("\n", "<br>")}</td>
        <td class="qa-time-cell">${escapeHtml(formatDateTime(item.timestamp || item.submittedAt))}</td>
      `;
      row.addEventListener("click", () => {
        selectedSessionId = item.sessionId;
        renderSessions(items);
      });
      sessionTableBody.appendChild(row);
    });
    renderJsonText(
      sessionJson,
      items.find((item) => item.sessionId === selectedSessionId) || items[0]
    );
  };

  const loadSessions = async () => {
    tableStatus.textContent = "刷新中...";
    const payload = await safeFetchJson(`${endpointBase}?limit=10`);
    renderSessions((payload?.data?.items || []).filter(isDisplayableSession));
    tableStatus.textContent = `最近刷新：${formatDateTime(Date.now())}`;
  };

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(form);
    const body = Object.fromEntries(formData.entries());
    body.timestamp = Number(body.timestamp) || Date.now();
    statusNode.textContent = "提交中...";
    try {
      const payload = await safeFetchJson(endpointBase, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      });
      statusNode.textContent = "提交成功，已写入演示 JSON 存储";
      renderJsonText(sessionJson, payload?.data?.session || {});
      await loadSessions();
    } catch (error) {
      statusNode.textContent = `提交失败：${error.message}`;
    }
  });

  refreshButton?.addEventListener("click", () => {
    loadSessions().catch((error) => {
      statusNode.textContent = `刷新失败：${error.message}`;
      tableStatus.textContent = `刷新失败：${error.message}`;
    });
  });

  refreshTableButton?.addEventListener("click", () => {
    loadSessions().catch((error) => {
      tableStatus.textContent = `刷新失败：${error.message}`;
    });
  });

  loadSessions().catch((error) => {
    statusNode.textContent = `初始化失败：${error.message}`;
    tableStatus.textContent = `初始化失败：${error.message}`;
  });
};

const initMaterialDemo = () => {
  const materialForm = document.getElementById("material-form");
  const assetUploadForm = document.getElementById("asset-upload-form");
  const materialModal = document.getElementById("material-modal");
  if (!materialForm || !assetUploadForm || !materialModal) {
    return;
  }

  const materialEndpointBase = `${window.location.origin}/api/v1/oem/material-library-demo/materials`;
  const assetEndpointBase = `${window.location.origin}/api/v1/oem/material-library-demo/assets`;

  const materialStatus = document.getElementById("material-form-status");
  const materialTableStatus = document.getElementById("material-table-status");
  const materialTableBody = document.getElementById("material-table-body");
  const materialJson = document.getElementById("material-json");
  const materialGroupSummary = document.getElementById("material-group-summary");
  const partsEditor = document.getElementById("material-parts-json");
  const partList = document.getElementById("material-part-list");
  const materialEntryCount = document.getElementById("material-entry-count");
  const openMaterialModalButton = document.getElementById("open-material-modal");
  const refreshMaterialsButton = document.getElementById("refresh-materials");
  const createMaterialButton = document.getElementById("create-material");
  const updateMaterialButton = document.getElementById("update-material");
  const loadSelectedMaterialButton = document.getElementById("load-selected-material");
  const deleteSelectedMaterialButton = document.getElementById("delete-selected-material");
  const fillTextExampleButton = document.getElementById("fill-text-example");
  const fillGroupExampleButton = document.getElementById("fill-group-example");
  const appendMaterialEntryButton = document.getElementById("append-material-entry");
  const clearMaterialEntriesButton = document.getElementById("clear-material-entries");
  const materialEntryText = document.getElementById("material-entry-text");
  const materialEntryAssetSelect = document.getElementById("material-entry-asset-select");
  const materialEntryAssetPreview = document.getElementById("material-entry-asset-preview");
  const textEntryField = document.getElementById("text-entry-field");
  const assetEntryFields = document.getElementById("asset-entry-fields");
  const partTypeInputs = Array.from(materialForm.querySelectorAll('input[name="partType"]'));
  const modalCloseTriggers = Array.from(
    materialModal.querySelectorAll("[data-close-material-modal]")
  );

  const assetUploadStatus = document.getElementById("asset-upload-status");
  const assetTableStatus = document.getElementById("asset-table-status");
  const assetTableBody = document.getElementById("asset-table-body");
  const assetJson = document.getElementById("asset-json");
  const refreshAssetsButton = document.getElementById("refresh-assets");
  const refreshAssetTableButton = document.getElementById("refresh-asset-table");
  const assetFileInput = document.getElementById("asset-file");
  const assetTypeInput = document.getElementById("asset-attachment-type");

  const materialIdInput = materialForm.elements.namedItem("materialId");
  const titleInput = materialForm.elements.namedItem("title");
  const descriptionInput = materialForm.elements.namedItem("description");
  const customerIdInput = materialForm.elements.namedItem("customerId");
  const channelInput = materialForm.elements.namedItem("channel");
  const tagsInput = materialForm.elements.namedItem("tags");
  const assetCustomerIdInput = assetUploadForm.elements.namedItem("assetCustomerId");
  const assetChannelInput = assetUploadForm.elements.namedItem("assetChannel");

  let selectedMaterialId = "";
  let selectedAssetId = "";
  let cachedMaterialList = [];
  let recentAssets = [];
  let partsDraft = [];

  const createId = (prefix) =>
    `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

  const getSelectedEntryType = () =>
    partTypeInputs.find((item) => item.checked)?.value || "text";

  const isAttachmentEntryType = (type) => type === "image" || type === "video" || type === "document";

  const buildAttachmentPartFromAsset = (asset = {}) => ({
    partId: createId("part_attachment"),
    type: "attachment",
    assetId: asset.assetId || "",
    displayName: asset.displayName || "",
    mimeType: asset.mimeType || "",
    attachmentType: asset.attachmentType || "document",
    fileSizeBytes: Number(asset.fileSizeBytes || 0),
    downloadUrl: asset.downloadUrl || "",
  });

  const normalizePartsToDraft = (parts = []) =>
    parts.map((part, index) => {
      if (part?.type === "attachment") {
        return {
          partId: part.partId || createId(`part_attachment_${index}`),
          type: "attachment",
          assetId: part.assetId || "",
          displayName: part.displayName || "",
          mimeType: part.mimeType || "",
          attachmentType: part.attachmentType || "document",
          fileSizeBytes: Number(part.fileSizeBytes || 0),
          downloadUrl: part.downloadUrl || "",
        };
      }
      return {
        partId: part?.partId || createId(`part_text_${index}`),
        type: "text",
        text: part?.text || "",
      };
    });

  const openMaterialModal = () => {
    materialModal.hidden = false;
    document.body.classList.add("has-modal-open");
  };

  const closeMaterialModal = () => {
    materialModal.hidden = true;
    document.body.classList.remove("has-modal-open");
  };

  const syncPartsEditor = () => {
    partsEditor.value = JSON.stringify(partsDraft, null, 2);
  };

  const updateMaterialEntryCount = () => {
    materialEntryCount.textContent = `已添加 ${partsDraft.length} 条`;
  };

  const updateMaterialGroupSummary = (material) => {
    if (!material) {
      materialGroupSummary.textContent = "当前未选中素材，等待刷新。";
      return;
    }
    const parts = Array.isArray(material.parts) ? material.parts : [];
    const typeSummary =
      parts
        .map((part) => (part.type === "attachment" ? part.attachmentType || "attachment" : "text"))
        .filter(Boolean)
        .join(" / ") || "暂无条目";
    materialGroupSummary.textContent = `${material.title || material.materialId || "未命名素材"}，共 ${parts.length} 条，类型：${typeSummary}`;
  };

  const getFilteredAssets = () => {
    const currentType = getSelectedEntryType();
    if (!isAttachmentEntryType(currentType)) {
      return recentAssets;
    }
    const matched = recentAssets.filter((asset) => asset.attachmentType === currentType);
    return matched.length ? matched : recentAssets;
  };

  const renderSelectedAssetPreview = (assetId) => {
    const asset = recentAssets.find((item) => item.assetId === assetId);
    if (!asset) {
      materialEntryAssetPreview.textContent = "当前未选择附件。";
      return;
    }
    materialEntryAssetPreview.textContent = `${asset.displayName || asset.assetId} | ${asset.attachmentType || asset.mimeType || "-"} | ${formatFileSize(asset.fileSizeBytes)}`;
  };

  const updateMaterialEntryAssetOptions = (preferredAssetId = "") => {
    const assets = getFilteredAssets();
    const options = ['<option value="">请选择已上传附件</option>']
      .concat(
        assets.map(
          (asset) =>
            `<option value="${escapeHtml(asset.assetId)}" ${
              asset.assetId === preferredAssetId ? "selected" : ""
            }>${escapeHtml(asset.displayName || asset.assetId)}</option>`
        )
      )
      .join("");
    materialEntryAssetSelect.innerHTML = options;
    if (preferredAssetId) {
      materialEntryAssetSelect.value = preferredAssetId;
    }
    renderSelectedAssetPreview(materialEntryAssetSelect.value);
  };

  const updateEntryEditorVisibility = () => {
    const currentType = getSelectedEntryType();
    const isAttachment = isAttachmentEntryType(currentType);
    textEntryField.hidden = isAttachment;
    assetEntryFields.hidden = !isAttachment;
    if (isAttachment) {
      updateMaterialEntryAssetOptions(materialEntryAssetSelect.value);
    } else {
      renderSelectedAssetPreview("");
    }
  };

  const clearEntryEditor = ({ keepType = true } = {}) => {
    materialEntryText.value = "";
    materialEntryAssetSelect.value = "";
    if (!keepType) {
      partTypeInputs.forEach((item) => {
        item.checked = item.value === "text";
      });
    }
    updateEntryEditorVisibility();
  };

  const renderPartList = () => {
    syncPartsEditor();
    updateMaterialEntryCount();
    partList.innerHTML = "";
    if (!partsDraft.length) {
      partList.innerHTML = '<p class="part-empty">当前还没有素材条目，请先在上方选择媒体类型并添加。</p>';
      return;
    }

    partsDraft.forEach((part, index) => {
      const card = document.createElement("article");
      card.className = "part-card part-card-simple";
      const isAttachment = part.type === "attachment";
      const label = isAttachment ? part.attachmentType || "attachment" : "text";
      const preview = isAttachment
        ? `${part.displayName || part.assetId || "未选择附件"} | ${formatFileSize(part.fileSizeBytes)}`
        : part.text || "(空文案)";
      card.innerHTML = `
        <div class="part-card-head">
          <div class="part-card-title">
            <strong>条目 ${index + 1}</strong>
            <span class="field-badge ${isAttachment ? "optional" : "required"}">${escapeHtml(label)}</span>
          </div>
          <div class="part-card-actions">
            <button class="button secondary" type="button" data-action="up">上移</button>
            <button class="button secondary" type="button" data-action="down">下移</button>
            <button class="button secondary" type="button" data-action="delete">删除</button>
          </div>
        </div>
        <div class="part-card-summary">
          <span class="part-card-key">${isAttachment ? "附件" : "文案"}</span>
          <p>${escapeHtml(preview)}</p>
        </div>
      `;

      card.querySelectorAll("[data-action]").forEach((button) => {
        button.addEventListener("click", () => {
          const action = button.getAttribute("data-action");
          if (action === "delete") {
            partsDraft.splice(index, 1);
          } else if (action === "up" && index > 0) {
            [partsDraft[index - 1], partsDraft[index]] = [partsDraft[index], partsDraft[index - 1]];
          } else if (action === "down" && index < partsDraft.length - 1) {
            [partsDraft[index + 1], partsDraft[index]] = [partsDraft[index], partsDraft[index + 1]];
          }
          renderPartList();
        });
      });

      partList.appendChild(card);
    });
  };

  const setParts = (parts) => {
    partsDraft = normalizePartsToDraft(parts);
    renderPartList();
  };

  const fillMaterialForm = (material) => {
    if (!material) {
      return;
    }
    materialIdInput.value = material.materialId || "";
    titleInput.value = material.title || "";
    descriptionInput.value = material.description || "";
    customerIdInput.value = material.customerId || "";
    channelInput.value = material.channel || "";
    tagsInput.value = Array.isArray(material.tags) ? material.tags.join(",") : "";
    setParts(material.parts || []);
  };

  const getMaterialPayload = () => {
    const title = titleInput.value.trim();
    if (!title) {
      throw new Error("title 为必填项");
    }
    if (!partsDraft.length) {
      throw new Error("请至少添加 1 条素材条目");
    }
    const parts = partsDraft.map((part, index) => {
      const partId = String(part.partId || "").trim() || createId(`part_${index}`);
      if (part.type === "attachment") {
        if (!part.assetId || !part.displayName || !part.mimeType || !part.downloadUrl) {
          throw new Error(`第 ${index + 1} 条附件素材缺少必要字段`);
        }
        return {
          partId,
          type: "attachment",
          assetId: String(part.assetId || "").trim(),
          displayName: String(part.displayName || "").trim(),
          mimeType: String(part.mimeType || "").trim(),
          attachmentType: String(part.attachmentType || "document").trim(),
          fileSizeBytes: Number(part.fileSizeBytes || 0),
          downloadUrl: String(part.downloadUrl || "").trim(),
        };
      }
      const text = String(part.text || "").trim();
      if (!text) {
        throw new Error(`第 ${index + 1} 条文案内容不能为空`);
      }
      return {
        partId,
        type: "text",
        text,
      };
    });
    setParts(parts);
    return {
      materialId: materialIdInput.value.trim(),
      title,
      description: descriptionInput.value.trim(),
      customerId: customerIdInput.value.trim(),
      channel: channelInput.value.trim(),
      tags: parseTags(tagsInput.value),
      parts,
    };
  };

  const appendMaterialEntry = () => {
    const currentType = getSelectedEntryType();
    if (currentType === "text") {
      const text = materialEntryText.value.trim();
      if (!text) {
        throw new Error("请输入文案内容后再添加");
      }
      partsDraft.push({
        partId: createId("part_text"),
        type: "text",
        text,
      });
    } else {
      const assetId = materialEntryAssetSelect.value;
      const asset = recentAssets.find((item) => item.assetId === assetId);
      if (!asset) {
        throw new Error("请选择已上传的附件后再添加");
      }
      partsDraft.push({
        ...buildAttachmentPartFromAsset(asset),
        attachmentType: currentType,
      });
    }
    renderPartList();
    clearEntryEditor();
    materialStatus.textContent = `已添加 1 条${currentType === "text" ? "文案" : currentType}素材，可继续添加下一条`;
  };

  const renderAssetRows = (items) => {
    recentAssets = items;
    assetTableBody.innerHTML = "";
    if (!items.length) {
      assetTableBody.innerHTML =
        '<tr class="is-passive"><td colspan="4" class="qa-empty">当前还没有上传附件。</td></tr>';
      renderJsonText(assetJson, {});
      selectedAssetId = "";
      updateMaterialEntryAssetOptions();
      return;
    }
    if (!selectedAssetId || !items.some((item) => item.assetId === selectedAssetId)) {
      selectedAssetId = items[0].assetId;
    }
    items.forEach((asset) => {
      const row = document.createElement("tr");
      if (asset.assetId === selectedAssetId) {
        row.classList.add("is-active");
      }
      row.innerHTML = `
        <td class="qa-text-cell">${escapeHtml(asset.displayName || asset.assetId)}</td>
        <td class="qa-host-cell">${escapeHtml(asset.attachmentType || asset.mimeType || "-")}</td>
        <td class="qa-time-cell">${escapeHtml(formatFileSize(asset.fileSizeBytes))}</td>
        <td class="asset-action-cell">
          <div class="inline-button-group">
            <button class="button secondary" type="button" data-action="use">用于当前类型</button>
          </div>
        </td>
      `;
      row.addEventListener("click", () => {
        selectedAssetId = asset.assetId;
        renderAssetRows(items);
        renderJsonText(assetJson, asset);
      });
      row.querySelector('[data-action="use"]')?.addEventListener("click", (event) => {
        event.stopPropagation();
        selectedAssetId = asset.assetId;
        renderJsonText(assetJson, asset);
        openMaterialModal();
        if (asset.attachmentType && isAttachmentEntryType(asset.attachmentType)) {
          partTypeInputs.forEach((item) => {
            item.checked = item.value === asset.attachmentType;
          });
        }
        updateMaterialEntryAssetOptions(asset.assetId);
        materialStatus.textContent = `已选择附件 ${asset.displayName || asset.assetId}，可直接添加到素材组`;
      });
      assetTableBody.appendChild(row);
    });
    renderJsonText(assetJson, items.find((item) => item.assetId === selectedAssetId) || items[0]);
    updateMaterialEntryAssetOptions(materialEntryAssetSelect.value);
  };

  const loadAssets = async () => {
    assetTableStatus.textContent = "刷新中...";
    const payload = await safeFetchJson(`${assetEndpointBase}?limit=10`);
    renderAssetRows(payload?.data?.items || []);
    assetTableStatus.textContent = `最近刷新：${formatDateTime(Date.now())}`;
  };

  const uploadAsset = async () => {
    const file = assetFileInput?.files?.[0];
    if (!file) {
      throw new Error("请先选择要上传的文件");
    }
    const params = new URLSearchParams();
    params.set("fileName", file.name);
    if (assetTypeInput?.value) {
      params.set("attachmentType", assetTypeInput.value);
    }
    if (assetCustomerIdInput?.value?.trim()) {
      params.set("customerId", assetCustomerIdInput.value.trim());
    }
    if (assetChannelInput?.value?.trim()) {
      params.set("channel", assetChannelInput.value.trim());
    }
    assetUploadStatus.textContent = "上传中...";
    const response = await fetch(`${assetEndpointBase}/upload?${params.toString()}`, {
      method: "POST",
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      body: file,
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload?.success === false) {
      throw new Error(payload?.error?.message || payload?.message || "附件上传失败");
    }
    const asset = payload?.data?.asset || {};
    selectedAssetId = asset.assetId || "";
    renderJsonText(assetJson, asset);
    assetUploadStatus.textContent = `上传成功：${asset.displayName || asset.assetId}`;
    assetUploadForm.reset();
    assetCustomerIdInput.value = customerIdInput.value.trim() || "customer_demo";
    assetChannelInput.value = channelInput.value.trim() || "release";
    await loadAssets();
    if (asset.assetId) {
      openMaterialModal();
      if (asset.attachmentType && isAttachmentEntryType(asset.attachmentType)) {
        partTypeInputs.forEach((item) => {
          item.checked = item.value === asset.attachmentType;
        });
      }
      updateMaterialEntryAssetOptions(asset.assetId);
      materialStatus.textContent = `附件已上传，可继续将 ${asset.displayName || asset.assetId} 添加到素材组`;
    }
  };

  const loadMaterialDetail = async (materialId, { fillEditor = false } = {}) => {
    if (!materialId) {
      renderJsonText(materialJson, {});
      updateMaterialGroupSummary(null);
      return null;
    }
    const payload = await safeFetchJson(`${materialEndpointBase}/${encodeURIComponent(materialId)}`);
    const material = payload?.data || {};
    renderJsonText(materialJson, material);
    updateMaterialGroupSummary(material);
    if (fillEditor) {
      fillMaterialForm(material);
    }
    return material;
  };

  const deleteMaterial = async (materialId) => {
    if (!materialId) {
      materialStatus.textContent = "请先选中要删除的素材";
      return;
    }
    if (!window.confirm(`确认删除素材 ${materialId} 吗？`)) {
      return;
    }
    materialStatus.textContent = "删除中...";
    await safeFetchJson(`${materialEndpointBase}/${encodeURIComponent(materialId)}`, {
      method: "DELETE",
    });
    if (selectedMaterialId === materialId) {
      selectedMaterialId = "";
      renderJsonText(materialJson, {});
      updateMaterialGroupSummary(null);
    }
    materialStatus.textContent = "删除成功";
    await loadMaterials();
  };

  const renderMaterialRows = (items) => {
    cachedMaterialList = items;
    materialTableBody.innerHTML = "";
    if (!items.length) {
      materialTableBody.innerHTML =
        '<tr class="is-passive"><td colspan="5" class="qa-empty">当前还没有演示素材。</td></tr>';
      selectedMaterialId = "";
      renderJsonText(materialJson, {});
      updateMaterialGroupSummary(null);
      return;
    }
    if (!selectedMaterialId || !items.some((item) => item.materialId === selectedMaterialId)) {
      selectedMaterialId = items[0].materialId;
    }
    items.forEach((item) => {
      const row = document.createElement("tr");
      if (item.materialId === selectedMaterialId) {
        row.classList.add("is-active");
      }
      const partCount = Number(item.partCount || item.partsCount || item.partTypes?.length || 0);
      row.innerHTML = `
        <td class="qa-text-cell" title="${escapeHtml(item.title || "(空)")}" >${escapeHtml(item.title || "(空)")}</td>
        <td class="qa-host-cell">${escapeHtml(
          item.previewText || item.previewAttachmentName || (item.partTypes || []).join(", ") || item.mode || "-"
        )}</td>
        <td class="qa-time-cell">${escapeHtml(String(partCount || "-"))}</td>
        <td class="qa-time-cell">${escapeHtml(formatDateTime(item.updatedAt))}</td>
        <td class="material-action-cell">
          <div class="inline-button-group">
            <button class="button secondary" type="button" data-action="edit">回填</button>
            <button class="button secondary" type="button" data-action="delete">删除</button>
          </div>
        </td>
      `;
      row.addEventListener("click", () => {
        selectedMaterialId = item.materialId;
        renderMaterialRows(items);
        loadMaterialDetail(item.materialId).catch((error) => {
          materialTableStatus.textContent = `加载素材失败：${error.message}`;
        });
      });
      row.querySelector('[data-action="edit"]')?.addEventListener("click", (event) => {
        event.stopPropagation();
        selectedMaterialId = item.materialId;
        loadMaterialDetail(item.materialId, { fillEditor: true })
          .then((material) => {
            if (material) {
              renderMaterialRows(cachedMaterialList);
              openMaterialModal();
              materialStatus.textContent = "已回填选中素材";
            }
          })
          .catch((error) => {
            materialStatus.textContent = `回填失败：${error.message}`;
          });
      });
      row.querySelector('[data-action="delete"]')?.addEventListener("click", (event) => {
        event.stopPropagation();
        deleteMaterial(item.materialId).catch((error) => {
          materialStatus.textContent = `删除失败：${error.message}`;
        });
      });
      materialTableBody.appendChild(row);
    });
  };

  const loadMaterials = async () => {
    materialTableStatus.textContent = "刷新中...";
    const payload = await safeFetchJson(`${materialEndpointBase}?limit=10`);
    const items = payload?.data?.items || [];
    renderMaterialRows(items);
    materialTableStatus.textContent = `最近刷新：${formatDateTime(Date.now())}`;
    if (items.length > 0) {
      await loadMaterialDetail(selectedMaterialId || items[0].materialId);
    }
  };

  const createOrUpdateMaterial = async (method) => {
    const payload = getMaterialPayload();
    materialStatus.textContent = method === "POST" ? "保存中..." : "验证更新接口中...";
    const targetMaterialId = payload.materialId || selectedMaterialId;
    if (method === "PUT" && !targetMaterialId) {
      throw new Error("更新前请先选中素材，或填写 materialId");
    }
    const url =
      method === "POST"
        ? materialEndpointBase
        : `${materialEndpointBase}/${encodeURIComponent(targetMaterialId)}`;
    const response = await safeFetchJson(url, {
      method,
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    const material = response?.data?.material || {};
    selectedMaterialId = material.materialId || targetMaterialId;
    renderJsonText(materialJson, material);
    updateMaterialGroupSummary(material);
    fillMaterialForm(material);
    materialStatus.textContent = method === "POST" ? "素材组保存成功" : "更新接口调用成功";
    closeMaterialModal();
    await loadMaterials();
  };

  assetUploadForm.addEventListener("submit", (event) => {
    event.preventDefault();
    uploadAsset().catch((error) => {
      assetUploadStatus.textContent = `上传失败：${error.message}`;
    });
  });

  refreshAssetsButton?.addEventListener("click", () => {
    loadAssets().catch((error) => {
      assetUploadStatus.textContent = `刷新附件失败：${error.message}`;
      assetTableStatus.textContent = `刷新附件失败：${error.message}`;
    });
  });

  refreshAssetTableButton?.addEventListener("click", () => {
    loadAssets().catch((error) => {
      assetTableStatus.textContent = `刷新附件失败：${error.message}`;
    });
  });

  openMaterialModalButton?.addEventListener("click", () => {
    openMaterialModal();
  });

  modalCloseTriggers.forEach((node) => {
    node.addEventListener("click", () => {
      closeMaterialModal();
    });
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !materialModal.hidden) {
      closeMaterialModal();
    }
  });

  partTypeInputs.forEach((input) => {
    input.addEventListener("change", () => {
      updateEntryEditorVisibility();
    });
  });

  materialEntryAssetSelect?.addEventListener("change", () => {
    renderSelectedAssetPreview(materialEntryAssetSelect.value);
  });

  appendMaterialEntryButton?.addEventListener("click", () => {
    try {
      appendMaterialEntry();
    } catch (error) {
      materialStatus.textContent = `添加失败：${error.message}`;
    }
  });

  clearMaterialEntriesButton?.addEventListener("click", () => {
    partsDraft = [];
    renderPartList();
    materialStatus.textContent = "已清空当前素材组条目";
  });

  createMaterialButton?.addEventListener("click", () => {
    createOrUpdateMaterial("POST").catch((error) => {
      materialStatus.textContent = `保存失败：${error.message}`;
    });
  });

  updateMaterialButton?.addEventListener("click", () => {
    createOrUpdateMaterial("PUT").catch((error) => {
      materialStatus.textContent = `更新失败：${error.message}`;
    });
  });

  loadSelectedMaterialButton?.addEventListener("click", () => {
    const targetMaterialId = materialIdInput.value.trim() || selectedMaterialId;
    loadMaterialDetail(targetMaterialId, { fillEditor: true })
      .then((material) => {
        if (material) {
          selectedMaterialId = material.materialId;
          renderMaterialRows(cachedMaterialList);
          openMaterialModal();
          materialStatus.textContent = "已回填选中素材";
        }
      })
      .catch((error) => {
        materialStatus.textContent = `回填失败：${error.message}`;
      });
  });

  deleteSelectedMaterialButton?.addEventListener("click", () => {
    deleteMaterial(materialIdInput.value.trim() || selectedMaterialId).catch((error) => {
      materialStatus.textContent = `删除失败：${error.message}`;
    });
  });

  fillTextExampleButton?.addEventListener("click", () => {
    setParts(buildTextExampleParts());
    materialStatus.textContent = "已填充文案示例";
  });

  fillGroupExampleButton?.addEventListener("click", () => {
    const recentAttachmentParts = recentAssets.slice(0, 2).map((asset) => buildAttachmentPartFromAsset(asset));
    const exampleParts = recentAttachmentParts.length
      ? [
          {
            partId: createId("part_text_intro"),
            type: "text",
            text: "您好，这里是泡泡输入法 OEM 方案资料，请查收。",
          },
          ...recentAttachmentParts,
        ]
      : buildGroupExampleParts();
    setParts(exampleParts);
    materialStatus.textContent = "已填充编组示例";
  });

  const refreshMaterials = () =>
    loadMaterials().catch((error) => {
      materialStatus.textContent = `刷新失败：${error.message}`;
      materialTableStatus.textContent = `刷新失败：${error.message}`;
    });

  refreshMaterialsButton?.addEventListener("click", refreshMaterials);

  setParts(buildTextExampleParts());
  updateEntryEditorVisibility();
  loadAssets().catch((error) => {
    assetUploadStatus.textContent = `初始化失败：${error.message}`;
    assetTableStatus.textContent = `初始化失败：${error.message}`;
  });
  loadMaterials().catch((error) => {
    materialStatus.textContent = `初始化失败：${error.message}`;
    materialTableStatus.textContent = `初始化失败：${error.message}`;
  });
};

initMaterialDemo();
initQaDemo();
