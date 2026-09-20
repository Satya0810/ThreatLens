// sandbox.js
// Logic for ThreatLens Full-Screen Isolated Sandbox Browser

document.addEventListener('DOMContentLoaded', () => {
  function unwrapUrl(rawUrl) {
    if (!rawUrl) return rawUrl;
    try {
      const url = new URL(rawUrl);
      const host = url.hostname.toLowerCase();
      // Bing
      if (host.includes('bing.com') && url.pathname.includes('/ck/')) {
        const u = url.searchParams.get('u');
        if (u) {
          let b64 = u.startsWith('a1') ? u.slice(2) : u;
          while (b64.length % 4 !== 0) b64 += '=';
          const decoded = atob(b64.replace(/-/g, '+').replace(/_/g, '/'));
          if (decoded.startsWith('http://') || decoded.startsWith('https://')) return decoded;
        }
      }
      // Google
      if (host.includes('google.') && url.pathname.includes('/url')) {
        const target = url.searchParams.get('url') || url.searchParams.get('q');
        if (target && (target.startsWith('http://') || target.startsWith('https://'))) return target;
      }
    } catch (e) {}
    return rawUrl;
  }

  const rawParamUrl = urlParams.get('url');
  const initialUrl = unwrapUrl(rawParamUrl) || 'https://duckduckgo.com';
  const initialScore = parseInt(urlParams.get('score'), 10) || 75;
  const initialThreatType = urlParams.get('type') || 'SUSPICIOUS';

  // DOM Elements
  const headerThreatScore = document.getElementById('headerThreatScore');
  const headerThreatFill = document.getElementById('headerThreatFill');
  const headerScriptPill = document.getElementById('headerScriptPill');
  const headerScriptText = document.getElementById('headerScriptText');
  const btnReturnSafety = document.getElementById('btnReturnSafety');
  const btnProceedLive = document.getElementById('btnProceedLive');

  const btnNavBack = document.getElementById('btnNavBack');
  const btnNavForward = document.getElementById('btnNavForward');
  const btnNavReload = document.getElementById('btnNavReload');
  const protocolBadge = document.getElementById('protocolBadge');
  const urlInput = document.getElementById('urlInput');
  const btnClearUrl = document.getElementById('btnClearUrl');
  const btnNavGo = document.getElementById('btnNavGo');

  const tabsTrack = document.getElementById('tabsTrack');
  const btnNewTab = document.getElementById('btnNewTab');

  const btnToggleScripts = document.getElementById('btnToggleScripts');
  const toggleScriptLabel = document.getElementById('toggleScriptLabel');
  const btnToggleViewport = document.getElementById('btnToggleViewport');
  const btnViewSource = document.getElementById('btnViewSource');
  const btnAuditLogs = document.getElementById('btnAuditLogs');
  const auditLogCountBadge = document.getElementById('auditLogCountBadge');
  const btnCaptureSnapshot = document.getElementById('btnCaptureSnapshot');

  const sandboxProgressBar = document.getElementById('sandboxProgressBar');
  const viewportFrame = document.getElementById('viewportFrame');
  const sandboxIframe = document.getElementById('sandboxIframe');

  const errorViewportCard = document.getElementById('errorViewportCard');
  const errorTitle = document.getElementById('errorTitle');
  const errorDesc = document.getElementById('errorDesc');
  const errorUrlBox = document.getElementById('errorUrlBox');
  const btnRetryFetch = document.getElementById('btnRetryFetch');
  const btnErrorProceedAnyway = document.getElementById('btnErrorProceedAnyway');

  // Modals
  const sourceCodeModal = document.getElementById('sourceCodeModal');
  const sourceCodeContent = document.getElementById('sourceCodeContent');
  const btnCopySource = document.getElementById('btnCopySource');
  const btnCloseSourceModal = document.getElementById('btnCloseSourceModal');

  const auditLogModal = document.getElementById('auditLogModal');
  const btnCloseAuditModal = document.getElementById('btnCloseAuditModal');
  const auditScriptsCount = document.getElementById('auditScriptsCount');
  const auditFormsCount = document.getElementById('auditFormsCount');
  const auditTrackersCount = document.getElementById('auditTrackersCount');
  const auditIframesCount = document.getElementById('auditIframesCount');
  const auditLogList = document.getElementById('auditLogList');

  const scriptWarningModal = document.getElementById('scriptWarningModal');
  const btnCloseScriptWarning = document.getElementById('btnCloseScriptWarning');
  const btnCancelScriptEnable = document.getElementById('btnCancelScriptEnable');
  const btnConfirmScriptEnable = document.getElementById('btnConfirmScriptEnable');

  // State
  let allowScripts = false;
  let isMobileViewport = false;
  let currentThreatScore = initialScore;

  // Initialize Threat Score Badge
  if (headerThreatScore && headerThreatFill) {
    headerThreatScore.textContent = `${currentThreatScore}%`;
    headerThreatFill.style.width = `${Math.min(100, Math.max(10, currentThreatScore))}%`;
  }

  // Multi-Tab Management
  let tabs = [
    {
      id: 'tab-1',
      url: initialUrl,
      title: 'Initial Target',
      history: [initialUrl],
      historyIndex: 0,
      rawHtml: '',
      sanitizedHtml: '',
      telemetry: null
    }
  ];
  let activeTabId = 'tab-1';

  function getActiveTab() {
    return tabs.find(t => t.id === activeTabId) || tabs[0];
  }

  function updateNavButtons() {
    const tab = getActiveTab();
    if (!tab) return;
    btnNavBack.disabled = tab.historyIndex <= 0;
    btnNavForward.disabled = tab.historyIndex >= tab.history.length - 1;
  }

  function renderTabs() {
    const tabElements = tabsTrack.querySelectorAll('.tab-item');
    tabElements.forEach(el => el.remove());

    tabs.forEach(tab => {
      const item = document.createElement('div');
      item.className = `tab-item ${tab.id === activeTabId ? 'active' : ''}`;
      item.dataset.tabId = tab.id;
      item.innerHTML = `
        <span>${tab.url.startsWith('https') ? '🔒' : '🌐'}</span>
        <span class="tab-title">${escapeHtml(tab.title || tab.url)}</span>
        ${tabs.length > 1 ? '<span class="tab-close-btn" title="Close Tab">×</span>' : ''}
      `;

      item.addEventListener('click', (e) => {
        if (e.target.classList.contains('tab-close-btn')) {
          e.stopPropagation();
          closeTab(tab.id);
          return;
        }
        switchTab(tab.id);
      });

      tabsTrack.insertBefore(item, btnNewTab);
    });
  }

  function switchTab(tabId) {
    activeTabId = tabId;
    renderTabs();
    const tab = getActiveTab();
    if (tab) {
      urlInput.value = tab.url;
      updateProtocolBadge(tab.url);
      updateNavButtons();
      if (tab.sanitizedHtml) {
        renderHtmlInViewport(tab.sanitizedHtml);
        updateTelemetryDisplay(tab.telemetry);
      } else {
        fetchAndRender(tab.url);
      }
    }
  }

  function closeTab(tabId) {
    if (tabs.length <= 1) return;
    const idx = tabs.findIndex(t => t.id === tabId);
    tabs = tabs.filter(t => t.id !== tabId);
    if (activeTabId === tabId) {
      activeTabId = tabs[Math.max(0, idx - 1)].id;
    }
    switchTab(activeTabId);
  }

  function addNewTab(url = 'https://duckduckgo.com') {
    const newId = `tab-${Date.now()}`;
    tabs.push({
      id: newId,
      url: url,
      title: 'New Tab',
      history: [url],
      historyIndex: 0,
      rawHtml: '',
      sanitizedHtml: '',
      telemetry: null
    });
    switchTab(newId);
  }

  btnNewTab.addEventListener('click', () => {
    addNewTab();
  });

  function updateProtocolBadge(url) {
    if (url.startsWith('https://')) {
      protocolBadge.innerHTML = `<span>🔒</span> HTTPS`;
      protocolBadge.classList.remove('insecure');
    } else {
      protocolBadge.innerHTML = `<span>⚠️</span> HTTP`;
      protocolBadge.classList.add('insecure');
    }
    btnClearUrl.style.display = urlInput.value ? 'block' : 'none';
  }

  urlInput.addEventListener('input', () => {
    btnClearUrl.style.display = urlInput.value ? 'block' : 'none';
  });

  btnClearUrl.addEventListener('click', () => {
    urlInput.value = '';
    urlInput.focus();
    btnClearUrl.style.display = 'none';
  });

  function navigateTo(url) {
    let finalUrl = url.trim();
    if (!finalUrl) return;

    if (!finalUrl.startsWith('http://') && !finalUrl.startsWith('https://')) {
      if (finalUrl.includes('.') && !finalUrl.includes(' ')) {
        finalUrl = `https://${finalUrl}`;
      } else {
        finalUrl = `https://duckduckgo.com/?q=${encodeURIComponent(finalUrl)}`;
      }
    }

    const tab = getActiveTab();
    if (tab) {
      tab.url = finalUrl;
      tab.history = tab.history.slice(0, tab.historyIndex + 1);
      tab.history.push(finalUrl);
      tab.historyIndex = tab.history.length - 1;
      updateNavButtons();
    }
    fetchAndRender(finalUrl);
  }

  btnNavGo.addEventListener('click', () => {
    navigateTo(urlInput.value);
  });

  urlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      navigateTo(urlInput.value);
    }
  });

  btnNavBack.addEventListener('click', () => {
    const tab = getActiveTab();
    if (tab && tab.historyIndex > 0) {
      tab.historyIndex--;
      tab.url = tab.history[tab.historyIndex];
      updateNavButtons();
      fetchAndRender(tab.url);
    }
  });

  btnNavForward.addEventListener('click', () => {
    const tab = getActiveTab();
    if (tab && tab.historyIndex < tab.history.length - 1) {
      tab.historyIndex++;
      tab.url = tab.history[tab.historyIndex];
      updateNavButtons();
      fetchAndRender(tab.url);
    }
  });

  btnNavReload.addEventListener('click', () => {
    const tab = getActiveTab();
    if (tab) {
      fetchAndRender(tab.url);
    }
  });

  function renderHtmlInViewport(sanitizedHtml) {
    errorViewportCard.style.display = 'none';
    sandboxIframe.style.display = 'block';

    if (allowScripts) {
      sandboxIframe.setAttribute('sandbox', 'allow-same-origin allow-forms allow-scripts');
    } else {
      sandboxIframe.setAttribute('sandbox', 'allow-same-origin allow-forms');
    }

    sandboxIframe.removeAttribute('src');
    sandboxIframe.srcdoc = sanitizedHtml;
  }

  function updateTelemetryDisplay(telemetry) {
    if (!telemetry) return;
    const totalThreats = (telemetry.scriptsStripped || 0) + (telemetry.blockedTrackers ? telemetry.blockedTrackers.length : 0);
    auditLogCountBadge.textContent = `Threat Audit (${totalThreats})`;

    auditScriptsCount.textContent = telemetry.scriptsStripped || 0;
    auditFormsCount.textContent = telemetry.formsDisarmed || 0;
    auditTrackersCount.textContent = telemetry.blockedTrackers ? telemetry.blockedTrackers.length : 0;
    auditIframesCount.textContent = telemetry.iframesFound || 0;

    let logHtml = `
      <div class="audit-log-item">
        <div class="audit-dot info"></div>
        <div style="color: #94a3b8;">Container active: isolated cookies and zero persistent storage.</div>
      </div>
    `;

    if (telemetry.scriptsStripped > 0) {
      logHtml += `
        <div class="audit-log-item">
          <div class="audit-dot"></div>
          <div><strong style="color:#ef4444;">${telemetry.scriptsStripped} script tags</strong> stripped to prevent drive-by execution.</div>
        </div>
      `;
    }

    if (telemetry.formsDisarmed > 0) {
      logHtml += `
        <div class="audit-log-item">
          <div class="audit-dot"></div>
          <div><strong style="color:#f59e0b;">${telemetry.formsDisarmed} forms</strong> disarmed to block credential harvesting.</div>
        </div>
      `;
    }

    if (telemetry.passwordInputs > 0) {
      logHtml += `
        <div class="audit-log-item">
          <div class="audit-dot"></div>
          <div><strong style="color:#ef4444;">Password input field detected</strong> inside untrusted page.</div>
        </div>
      `;
    }

    if (telemetry.blockedTrackers && telemetry.blockedTrackers.length > 0) {
      telemetry.blockedTrackers.forEach(tracker => {
        logHtml += `
          <div class="audit-log-item">
            <div class="audit-dot"></div>
            <div>${escapeHtml(tracker)}</div>
          </div>
        `;
      });
    }

    auditLogList.innerHTML = logHtml;
  }

  async function fetchAndRender(url) {
    urlInput.value = url;
    updateProtocolBadge(url);
    sandboxProgressBar.classList.add('loading');
    errorViewportCard.style.display = 'none';

    const tab = getActiveTab();
    try {
      let response = null;

      // Request background service worker to fetch and sanitize
      try {
        response = await new Promise((resolve, reject) => {
          chrome.runtime.sendMessage({
            action: "FETCH_SANDBOX_HTML",
            url: url,
            isDesktop: !isMobileViewport,
            allowScripts: allowScripts
          }, (res) => {
            if (chrome.runtime.lastError) {
              reject(new Error(chrome.runtime.lastError.message));
            } else {
              resolve(res);
            }
          });
        });
      } catch (workerErr) {
        console.warn("Worker fetch failed, falling back to direct fetch:", workerErr);
      }

      if (response && response.success) {
        tab.rawHtml = response.rawHtml;
        tab.sanitizedHtml = response.sanitizedHtml;
        tab.telemetry = response.telemetry;

        // Extract title from HTML
        const titleMatch = response.rawHtml.match(/<title[^>]*>([^<]+)<\/title>/i);
        if (titleMatch && titleMatch[1]) {
          tab.title = titleMatch[1].trim();
        } else {
          try { tab.title = new URL(url).hostname; } catch (e) { tab.title = url; }
        }

        renderTabs();
        renderHtmlInViewport(tab.sanitizedHtml);
        updateTelemetryDisplay(tab.telemetry);
        sourceCodeContent.textContent = tab.rawHtml || tab.sanitizedHtml;
      } else {
        // Direct fetch fallback in case worker wasn't ready
        const res = await fetch(url, {
          headers: {
            'User-Agent': isMobileViewport 
              ? 'Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
              : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
          }
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const rawText = await res.text();

        let sanitized = rawText;
        if (!allowScripts) {
          sanitized = sanitized
            .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
            .replace(/\bon\w+\s*=\s*(["'])[\s\S]*?\1/gi, "")
            .replace(/<form\b[^>]*>/gi, '<form onsubmit="return false;" style="pointer-events:none;">');
        }

        if (/<head[^>]*>/i.test(sanitized)) {
          sanitized = sanitized.replace(/(<head[^>]*>)/i, `$1<base href="${url}">`);
        } else {
          sanitized = `<base href="${url}">${sanitized}`;
        }

        tab.rawHtml = rawText;
        tab.sanitizedHtml = sanitized;
        tab.telemetry = {
          scriptsStripped: (rawText.match(/<script\b/gi) || []).length,
          formsDisarmed: (rawText.match(/<form\b/gi) || []).length,
          blockedTrackers: []
        };

        try { tab.title = new URL(url).hostname; } catch (e) { tab.title = url; }
        renderTabs();
        renderHtmlInViewport(sanitized);
        updateTelemetryDisplay(tab.telemetry);
        sourceCodeContent.textContent = rawText;
      }
    } catch (err) {
      console.error("Sandbox loading error:", err);
      sandboxIframe.style.display = 'none';
      errorViewportCard.style.display = 'flex';
      errorUrlBox.textContent = url;
      errorDesc.textContent = `Could not safely fetch destination: ${err.message}. The target server may be offline or blocking external requests.`;
    } finally {
      sandboxProgressBar.classList.remove('loading');
    }
  }

  // ── Tools & Controls ──

  // 1. Toggle Scripts
  btnToggleScripts.addEventListener('click', () => {
    if (!allowScripts) {
      // Show warning modal
      scriptWarningModal.style.display = 'flex';
    } else {
      allowScripts = false;
      toggleScriptLabel.textContent = 'Scripts: Blocked (Safe)';
      btnToggleScripts.classList.remove('warning-state');
      headerScriptPill.className = 'security-pill safe';
      headerScriptText.textContent = 'Scripts Blocked (Safe)';
      const tab = getActiveTab();
      if (tab) fetchAndRender(tab.url);
    }
  });

  btnConfirmScriptEnable.addEventListener('click', () => {
    scriptWarningModal.style.display = 'none';
    allowScripts = true;
    toggleScriptLabel.textContent = 'Scripts: Active (Container)';
    btnToggleScripts.classList.add('warning-state');
    headerScriptPill.className = 'security-pill danger';
    headerScriptText.textContent = 'Scripts Active';
    const tab = getActiveTab();
    if (tab) fetchAndRender(tab.url);
  });

  btnCancelScriptEnable.addEventListener('click', () => {
    scriptWarningModal.style.display = 'none';
  });

  btnCloseScriptWarning.addEventListener('click', () => {
    scriptWarningModal.style.display = 'none';
  });

  // 2. Toggle Viewport Mode
  btnToggleViewport.addEventListener('click', () => {
    isMobileViewport = !isMobileViewport;
    if (isMobileViewport) {
      viewportFrame.classList.add('mobile-mode');
      btnToggleViewport.classList.add('active');
      btnToggleViewport.innerHTML = '<span>📱</span> <span>Mobile (412px)</span>';
    } else {
      viewportFrame.classList.remove('mobile-mode');
      btnToggleViewport.classList.remove('active');
      btnToggleViewport.innerHTML = '<span>🖥️</span> <span>Desktop View</span>';
    }
    const tab = getActiveTab();
    if (tab) fetchAndRender(tab.url);
  });

  // 3. View Source Modal
  btnViewSource.addEventListener('click', () => {
    const tab = getActiveTab();
    sourceCodeContent.textContent = tab.rawHtml || tab.sanitizedHtml || 'No DOM loaded yet.';
    sourceCodeModal.style.display = 'flex';
  });

  btnCloseSourceModal.addEventListener('click', () => {
    sourceCodeModal.style.display = 'none';
  });

  btnCopySource.addEventListener('click', () => {
    const code = sourceCodeContent.textContent;
    navigator.clipboard.writeText(code).then(() => {
      btnCopySource.textContent = '✓ Copied!';
      setTimeout(() => { btnCopySource.textContent = '📋 Copy HTML'; }, 2000);
    });
  });

  // 4. Threat Audit Modal
  btnAuditLogs.addEventListener('click', () => {
    auditLogModal.style.display = 'flex';
  });

  btnCloseAuditModal.addEventListener('click', () => {
    auditLogModal.style.display = 'none';
  });

  // 5. Capture Snapshot
  btnCaptureSnapshot.addEventListener('click', () => {
    const tab = getActiveTab();
    const htmlToSave = tab.sanitizedHtml || tab.rawHtml || '';
    if (!htmlToSave) {
      alert("No DOM available to snapshot.");
      return;
    }

    const blob = new Blob([htmlToSave], { type: 'text/html' });
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    let safeHost = 'unknown';
    try { safeHost = new URL(tab.url).hostname; } catch (e) {}
    a.download = `ThreatLens_Sandbox_${safeHost}_${Date.now()}.html`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(blobUrl);

    btnCaptureSnapshot.innerHTML = '<span>✓</span> <span>Saved!</span>';
    setTimeout(() => {
      btnCaptureSnapshot.innerHTML = '<span>📸</span> <span>Capture</span>';
    }, 2000);
  });

  // 6. Navigation Actions
  btnReturnSafety.addEventListener('click', () => {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      window.close();
    }
  });

  btnProceedLive.addEventListener('click', () => {
    const tab = getActiveTab();
    const confirmProceed = confirm(
      `⚠️ THREAT WARNING\n\nDestination: ${tab.url}\nThreat Score: ${currentThreatScore}%\n\nYou are about to leave the isolated container and open this URL in your main browser.\n\nProceed anyway?`
    );
    if (confirmProceed) {
      chrome.runtime.sendMessage({ action: "ALLOW_BYPASS", url: tab.url }, () => {
        window.open(tab.url, '_blank');
      });
    }
  });

  btnRetryFetch.addEventListener('click', () => {
    const tab = getActiveTab();
    if (tab) fetchAndRender(tab.url);
  });

  btnErrorProceedAnyway.addEventListener('click', () => {
    const tab = getActiveTab();
    if (tab) {
      chrome.runtime.sendMessage({ action: "ALLOW_BYPASS", url: tab.url }, () => {
        window.open(tab.url, '_blank');
      });
    }
  });

  // Close modals on backdrop click
  [sourceCodeModal, auditLogModal, scriptWarningModal].forEach(modal => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.style.display = 'none';
      }
    });
  });

  function escapeHtml(str) {
    return (str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Initial Load
  switchTab('tab-1');
});
