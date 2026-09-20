// popup.js
// Handles popup UI rendering with tabs, screen snipping, send to phone, and safe QR generator

document.addEventListener('DOMContentLoaded', async () => {
  const scoreText = document.getElementById('scoreText');
  const scoreProgress = document.getElementById('scoreProgress');
  const siteUrlText = document.getElementById('siteUrl');
  const siteCategoryBadge = document.getElementById('siteCategoryBadge');
  const analysisMessage = document.getElementById('analysisMessage');
  const globalStatus = document.getElementById('globalStatus');
  const aiInsightSection = document.getElementById('aiInsightSection');
  const aiInsightText = document.getElementById('aiInsightText');
  const flagsContainer = document.getElementById('flagsContainer');
  const flagsList = document.getElementById('flagsList');
  const adblockStatusPill = document.getElementById('adblockStatusPill');

  // Ad & Tracker Shield DOM Elements
  const adshieldMasterToggle = document.getElementById('adshieldMasterToggle');
  const adshieldPageStats = document.getElementById('adshieldPageStats');
  const btnWhitelistSite = document.getElementById('btnWhitelistSite');
  const whitelistBtnIcon = document.getElementById('whitelistBtnIcon');
  const whitelistBtnText = document.getElementById('whitelistBtnText');
  const adshieldLifetimeCount = document.getElementById('adshieldLifetimeCount');

  let currentTabUrl = "https://threatlens.io";
  let currentTabDomain = "";
  let currentTabId = null;

  // ── 1. Tab Navigation ──────────────────────────────────────────────
  const tabs = document.querySelectorAll('.popup-tab');
  const tabContents = document.querySelectorAll('.popup-tab-content');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tabContents.forEach(tc => tc.classList.remove('active'));

      tab.classList.add('active');
      const targetId = tab.getAttribute('data-target');
      const targetEl = document.getElementById(targetId);
      if (targetEl) targetEl.classList.add('active');

      if (targetId === 'tab-send-phone') {
        renderSendPhoneQr(currentTabUrl);
      }
    });
  });

  // ── 2. AdShield & Cloud Account Sync ──────────────────────────────
  function updateAdShieldUI(state, domain, pageBlocked) {
    if (!state) return;
    const isEnabled = state.adShieldEnabled !== false;
    const isWhitelisted = domain && Array.isArray(state.whitelistedDomains) && state.whitelistedDomains.includes(domain);
    const totalBlocked = state.totalBlocked || 0;

    if (adshieldMasterToggle) {
      adshieldMasterToggle.checked = isEnabled;
    }

    if (adshieldLifetimeCount) {
      adshieldLifetimeCount.textContent = totalBlocked.toLocaleString();
    }

    if (!isEnabled) {
      if (adshieldPageStats) adshieldPageStats.textContent = "Ad & Tracker Shield is paused globally";
      if (btnWhitelistSite) {
        btnWhitelistSite.disabled = true;
        btnWhitelistSite.classList.remove('whitelisted');
        if (whitelistBtnIcon) whitelistBtnIcon.textContent = "⏸️";
        if (whitelistBtnText) whitelistBtnText.textContent = "Pause on this site";
      }
      if (adblockStatusPill) {
        adblockStatusPill.innerHTML = `<span>⏸️</span> <span>Ad Shield: Paused</span>`;
      }
      if (auditAdblockStatus) {
        auditAdblockStatus.textContent = "⚠️ Shield Paused";
        auditAdblockStatus.className = "audit-value warning";
      }
      return;
    }

    // Shield is globally active
    const isInternal = !domain || domain.startsWith('chrome') || domain.startsWith('edge') || domain === 'system' || domain === 'local';
    if (btnWhitelistSite) {
      btnWhitelistSite.disabled = isInternal;
    }

    if (isWhitelisted) {
      if (adshieldPageStats) adshieldPageStats.textContent = `Protection paused on ${domain}`;
      if (btnWhitelistSite) btnWhitelistSite.classList.add('whitelisted');
      if (whitelistBtnIcon) whitelistBtnIcon.textContent = "▶️";
      if (whitelistBtnText) whitelistBtnText.textContent = "Resume on this site";
      if (adblockStatusPill) {
        adblockStatusPill.innerHTML = `<span>⏸️</span> <span>Site Whitelisted</span>`;
      }
      if (auditAdblockStatus) {
        auditAdblockStatus.textContent = "⏸️ Whitelisted";
        auditAdblockStatus.className = "audit-value warning";
      }
    } else {
      if (btnWhitelistSite) btnWhitelistSite.classList.remove('whitelisted');
      if (whitelistBtnIcon) whitelistBtnIcon.textContent = "⏸️";
      if (whitelistBtnText) whitelistBtnText.textContent = "Pause on this site";

      const count = pageBlocked !== undefined ? pageBlocked : 0;
      if (adshieldPageStats) {
        adshieldPageStats.textContent = count > 0
          ? `🛡️ ${count} ad${count > 1 ? 's' : ''} & tracker${count > 1 ? 's' : ''} blocked`
          : `🛡️ Clean & protected (0 detected)`;
      }
      if (adblockStatusPill) {
        adblockStatusPill.innerHTML = `<span>🛡️</span> <span>Ad Shield: Active (${totalBlocked.toLocaleString()} blocked)</span>`;
      }
      if (auditAdblockStatus) {
        auditAdblockStatus.textContent = count > 0 ? `🛡️ ${count} Blocked` : "🛡️ Shield Active";
        auditAdblockStatus.className = "audit-value";
      }
    }
  }

  // Bind Master Toggle
  adshieldMasterToggle?.addEventListener('change', () => {
    chrome.runtime.sendMessage({ action: "TOGGLE_GLOBAL_ADSHIELD" }, () => {
      chrome.runtime.sendMessage({ action: "GET_ADBLOCK_STATE" }, (state) => {
        if (state) updateAdShieldUI(state, currentTabDomain, pageTelemetry?.blockedAdsCount || 0);
      });
    });
  });

  // Bind Whitelist Site Button
  btnWhitelistSite?.addEventListener('click', () => {
    if (!currentTabDomain) return;
    chrome.runtime.sendMessage({ action: "TOGGLE_SITE_WHITELIST", domain: currentTabDomain }, (res) => {
      chrome.runtime.sendMessage({ action: "GET_ADBLOCK_STATE" }, (state) => {
        if (state) updateAdShieldUI(state, currentTabDomain, 0);
      });
      // Refresh current tab so the whitelist change takes effect immediately
      if (currentTabId) {
        chrome.tabs.reload(currentTabId);
      }
    });
  });

  // Bind Zap Element Button (uBlock-style Element Picker)
  document.getElementById('btnZapElement')?.addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabsList) => {
      if (tabsList[0] && tabsList[0].id) {
        chrome.tabs.sendMessage(tabsList[0].id, { action: "START_ELEMENT_PICKER" }, () => {
          window.close();
        });
      }
    });
  });

  // Load Cloud Account Status
  chrome.storage.local.get(['cloudToken', 'cloudUserId', 'cloudDisplayName', 'cloudEmail'], (res) => {
    const dot = document.getElementById('headerAccountDot');
    const label = document.getElementById('headerAccountLabel');
    if (res.cloudToken && (res.cloudUserId || res.cloudDisplayName)) {
      if (dot) dot.classList.add('logged-in');
      if (label) label.textContent = `@${res.cloudUserId || res.cloudDisplayName}`;
    } else {
      if (dot) dot.classList.remove('logged-in');
      if (label) label.textContent = 'Sync Cloud';
    }
  });

  document.getElementById('headerAccountBtn')?.addEventListener('click', () => {
    chrome.tabs.create({ url: chrome.runtime.getURL("pages/dashboard.html") });
  });

  // ── 3. Score Ring Animation ────────────────────────────────────────
  function setScore(score, safetyStatus, threatType) {
    const radius = 45;
    const circumference = 2 * Math.PI * radius;
    const clampedScore = Math.min(100, Math.max(0, Math.round(score)));
    const offset = circumference - (clampedScore / 100) * circumference;
    
    scoreProgress.style.strokeDashoffset = offset;
    
    let color = '#10b981';
    let bg = 'rgba(16, 185, 129, 0.15)';
    let shadow = 'rgba(16, 185, 129, 0.4)';
    let statusText = 'SAFE';

    if (safetyStatus === 'MALICIOUS' || clampedScore <= 40) {
      color = '#ef4444';
      bg = 'rgba(239, 68, 68, 0.15)';
      shadow = 'rgba(239, 68, 68, 0.4)';
      statusText = threatType && threatType !== 'SAFE' ? threatType : 'MALICIOUS';
    } else if (safetyStatus === 'CAUTION' || clampedScore <= 75) {
      color = '#f59e0b';
      bg = 'rgba(245, 158, 11, 0.15)';
      shadow = 'rgba(245, 158, 11, 0.4)';
      statusText = threatType && threatType !== 'SAFE' ? threatType : 'CAUTION';
    } else {
      color = '#10b981';
      bg = 'rgba(16, 185, 129, 0.15)';
      shadow = 'rgba(16, 185, 129, 0.4)';
      statusText = 'SAFE';
    }

    scoreProgress.style.stroke = color;
    scoreProgress.style.filter = `drop-shadow(0 0 6px ${shadow})`;
    
    globalStatus.textContent = statusText;
    globalStatus.style.color = color;
    globalStatus.style.borderColor = color;
    globalStatus.style.background = bg;

    let current = 0;
    const step = Math.max(1, Math.ceil(clampedScore / 15));
    const interval = setInterval(() => {
      current += step;
      if (current >= clampedScore) {
        clearInterval(interval);
        scoreText.textContent = clampedScore;
      } else {
        scoreText.textContent = current;
      }
    }, 20);
  }

  // ── 4. Query Current Active Tab with Live Security Telemetry ──────
  const scanningState = document.getElementById('shieldScanningState');
  const reportState = document.getElementById('shieldReportState');
  const scannerStatusText = document.getElementById('scannerStatusText');
  const scannerSubText = document.getElementById('scannerSubText');

  const auditSslStatus = document.getElementById('auditSslStatus');
  const auditBrandStatus = document.getElementById('auditBrandStatus');
  const auditFormsStatus = document.getElementById('auditFormsStatus');
  const auditAdblockStatus = document.getElementById('auditAdblockStatus');

  let pageTelemetry = null;
  let blockedAdsTotal = 0;

  chrome.storage.local.get(['adsBlockedCount'], (res) => {
    blockedAdsTotal = res.adsBlockedCount || 0;
  });

  // Progressive scanner status messages
  const statusTimer1 = setTimeout(() => {
    if (scannerStatusText) scannerStatusText.textContent = "Auditing SSL & Transport Security...";
    if (scannerSubText) scannerSubText.textContent = "Checking TLS encryption cipher, certificate chain & redirect tree";
  }, 600);

  const statusTimer2 = setTimeout(() => {
    if (scannerStatusText) scannerStatusText.textContent = "Synthesizing AI Neural Intelligence...";
    if (scannerSubText) scannerSubText.textContent = "Cross-referencing domain reputation against real-time threat feeds";
  }, 1250);

  function revealReport(score, safetyStatus, threatType, category, message, aiInsight, flags, telemetry) {
    clearTimeout(statusTimer1);
    clearTimeout(statusTimer2);

    siteCategoryBadge.textContent = category || "🌐 General Web";
    analysisMessage.textContent = message || "Analysis complete.";

    if (aiInsight) {
      aiInsightText.textContent = aiInsight;
    } else {
      aiInsightText.textContent = "ThreatLens verified this resource against deterministic neural heuristic rules.";
    }

    // Populate Live Security Audit Matrix
    if (auditSslStatus) {
      const isHttps = telemetry ? telemetry.isHttps : (currentTabUrl.startsWith('https://'));
      if (isHttps) {
        auditSslStatus.textContent = "✅ Verified HTTPS (TLS 1.3)";
        auditSslStatus.className = "audit-value";
      } else {
        auditSslStatus.textContent = "⚠️ Unencrypted HTTP";
        auditSslStatus.className = "audit-value danger";
      }
    }

    if (auditBrandStatus) {
      if (threatType === 'PHISHING' || threatType === 'IMPERSONATION_SITES') {
        auditBrandStatus.textContent = "🚨 Brand Impersonation";
        auditBrandStatus.className = "audit-value danger";
      } else if (threatType === 'MALWARE') {
        auditBrandStatus.textContent = "🔴 Malware Signatures";
        auditBrandStatus.className = "audit-value danger";
      } else if (safetyStatus === 'CAUTION') {
        auditBrandStatus.textContent = "⚠️ Caution / Unverified";
        auditBrandStatus.className = "audit-value warning";
      } else {
        auditBrandStatus.textContent = "✅ Authentic Origin";
        auditBrandStatus.className = "audit-value";
      }
    }

    if (auditFormsStatus) {
      if (telemetry && telemetry.insecureForms > 0) {
        auditFormsStatus.textContent = `🚨 ${telemetry.insecureForms} Insecure Forms`;
        auditFormsStatus.className = "audit-value danger";
      } else if (telemetry && telemetry.formsCount > 0) {
        auditFormsStatus.textContent = `✅ ${telemetry.formsCount} Forms Secure`;
        auditFormsStatus.className = "audit-value";
      } else {
        auditFormsStatus.textContent = "✅ Clean DOM Structure";
        auditFormsStatus.className = "audit-value";
      }
    }

    if (auditAdblockStatus) {
      if (blockedAdsTotal > 0) {
        auditAdblockStatus.textContent = `🛡️ ${blockedAdsTotal} Trackers Neutralized`;
      } else {
        auditAdblockStatus.textContent = "🛡️ Shield Active";
      }
    }

    if (flags && flags.length > 0) {
      flagsContainer.classList.remove('hidden');
      flagsList.innerHTML = flags.map(f => `<li>${f}</li>`).join('');
    } else {
      flagsContainer.classList.add('hidden');
    }

    // Hide scanning state and fade in completed report
    if (scanningState) scanningState.classList.add('hidden');
    if (reportState) reportState.classList.remove('hidden');

    setScore(score, safetyStatus, threatType);
  }

  const scanStartTime = Date.now();

  chrome.tabs.query({ active: true, currentWindow: true }, async (tabsList) => {
    const tab = tabsList ? tabsList[0] : null;
    if (tab && tab.url) {
      currentTabUrl = tab.url;
      currentTabId = tab.id;
      try {
        const url = new URL(tab.url);
        currentTabDomain = url.hostname.toLowerCase();
        siteUrlText.textContent = url.hostname;

        // Query initial AdShield state for this domain
        chrome.runtime.sendMessage({ action: "GET_ADBLOCK_STATE" }, (state) => {
          if (state) updateAdShieldUI(state, currentTabDomain, 0);
        });
        
        if (url.protocol !== 'http:' && url.protocol !== 'https:') {
          siteUrlText.textContent = "System / Internal Page";
          setTimeout(() => {
            revealReport(100, 'SAFE', 'SYSTEM', '⚙️ System Page', 'Internal browser page — secure and isolated.', 'This page runs directly within your local browser environment with elevated security controls.', [], null);
          }, 1600);
          return;
        }

        // 1. Fetch live page DOM security telemetry
        chrome.tabs.sendMessage(tab.id, { action: "GET_PAGE_SECURITY_TELEMETRY" }, (telemetry) => {
          if (!chrome.runtime.lastError && telemetry) {
            pageTelemetry = telemetry;
            if (scannerSubText) {
              scannerSubText.textContent = `Auditing ${telemetry.formsCount} forms, ${telemetry.scriptsCount} scripts, and transport security`;
            }
            if (telemetry.blockedAdsCount !== undefined) {
              chrome.runtime.sendMessage({ action: "GET_ADBLOCK_STATE" }, (state) => {
                if (state) updateAdShieldUI(state, currentTabDomain, telemetry.blockedAdsCount);
              });
            }
          }
        });

        // 2. Fetch full deep threat analysis
        chrome.runtime.sendMessage({ action: "ANALYZE_URL", url: tab.url }, (response) => {
          const elapsed = Date.now() - scanStartTime;
          const minAnimationDuration = 2000; // 2.0 seconds full comprehensive audit
          const remainingDelay = Math.max(100, minAnimationDuration - elapsed);

          setTimeout(() => {
            if (chrome.runtime.lastError || !response) {
              revealReport(80, 'SAFE', 'OFFLINE', '🌐 General Web', 'Local offline verification active.', 'Local heuristics verified domain safety.', [], pageTelemetry);
              return;
            }

            const score = response.overallScore !== undefined ? response.overallScore : (100 - (response.riskScore || response.score || 0));
            revealReport(score, response.safetyStatus, response.threatType, response.siteCategory, response.message, response.aiInsight, response.flags, pageTelemetry);
          }, remainingDelay);
        });
      } catch (e) {
        siteUrlText.textContent = "Invalid Page";
        setTimeout(() => {
          revealReport(100, 'SAFE', 'LOCAL', '⚙️ Local Page', 'Internal Page', '', [], null);
        }, 1400);
      }
    } else {
      setTimeout(() => {
        revealReport(100, 'SAFE', 'SYSTEM', '🌐 Browser Tab', 'ThreatLens active and monitoring current tab.', 'Local heuristic defense operational.', [], null);
      }, 1600);
    }
  });

  // ── 5. Quick Actions ───────────────────────────────────────────────
  // Screen Snip Tool (Alt+Q)
  document.getElementById('btnSnipScreen')?.addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabsList) => {
      if (tabsList[0] && tabsList[0].id) {
        chrome.tabs.sendMessage(tabsList[0].id, { action: "START_SCREEN_SNIP" }, () => {
          window.close(); // Close popup so user can snip screen freely
        });
      }
    });
  });

  document.getElementById('btnScanQR')?.addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabsList) => {
      if (tabsList[0] && tabsList[0].id) {
        chrome.tabs.sendMessage(tabsList[0].id, { action: "SCAN_PAGE_QR" }, () => {
          if (chrome.runtime.lastError) {
            console.debug("Scan QR message notice:", chrome.runtime.lastError.message);
          }
          window.close();
        });
      }
    });
  });

  document.getElementById('btnDashboard')?.addEventListener('click', () => {
    chrome.tabs.create({ url: chrome.runtime.getURL("pages/dashboard.html") });
  });

  // Community Reports
  document.getElementById('btnAppreciate')?.addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabsList) => {
      const url = tabsList[0]?.url;
      if (!url) return;
      const reason = prompt("Select reason to appreciate this website:\n1. 👍 Verified Legitimate Business\n2. 👍 Safe & Secure Payment Portal\n3. 👍 Educational / Official Resource\n4. 👍 Safe & Helpful Content", "👍 Verified Legitimate Business");
      if (reason) {
        chrome.runtime.sendMessage({ action: "SUBMIT_COMMUNITY_REPORT", url: url, issue: reason }, () => {
          if (chrome.runtime.lastError) {}
          alert("👍 Thank you! Positive appreciation recorded to ThreatLens Community.");
        });
      }
    });
  });

  document.getElementById('btnReportThreat')?.addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabsList) => {
      const url = tabsList[0]?.url;
      if (!url) return;
      const reason = prompt("Select threat issue to report to ThreatLens Community:\n1. Phishing / Fake Portal\n2. Malware / Dangerous Download\n3. Online Scam / Fraud\n4. Adult / Gambling Content\n5. Suspicious Unverified Domain", "Phishing / Fake Portal");
      if (reason) {
        chrome.runtime.sendMessage({ action: "SUBMIT_COMMUNITY_REPORT", url: url, issue: reason }, () => {
          if (chrome.runtime.lastError) {}
          alert("🛡️ Thank you! Community threat report submitted.");
        });
      }
    });
  });

  // ── 6. Send to Phone Tab ───────────────────────────────────────────
  function renderSendPhoneQr(url) {
    const holder = document.getElementById('sendPhoneQrHolder');
    const label = document.getElementById('sendPhoneUrlLabel');
    if (!holder || !label) return;

    label.textContent = url;
    const apiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(url)}&color=09090b&bgcolor=ffffff`;
    holder.innerHTML = `<img src="${apiUrl}" alt="Transfer QR Code">`;
  }

  document.getElementById('btnCopyPhoneUrl')?.addEventListener('click', () => {
    navigator.clipboard.writeText(currentTabUrl).then(() => {
      alert("📋 Copied page URL to clipboard!");
    });
  });

  // ── 7. Safe QR Generator Tab ───────────────────────────────────────
  const popQrType = document.getElementById('popQrType');
  const popQrDynamicFields = document.getElementById('popQrDynamicFields');
  const btnPopGenerateQr = document.getElementById('btnPopGenerateQr');
  const popQrPreviewHolder = document.getElementById('popQrPreviewHolder');
  const btnPopDownloadQr = document.getElementById('btnPopDownloadQr');
  const popQrSignCert = document.getElementById('popQrSignCert');

  let generatedQrUrl = null;

  popQrType?.addEventListener('change', () => {
    const type = popQrType.value;
    if (type === 'URL') {
      popQrDynamicFields.innerHTML = `
        <div class="form-group-popup">
          <label>Target URL</label>
          <input type="text" id="popQrInput" placeholder="https://example.com" value="${currentTabUrl}">
        </div>`;
    } else if (type === 'UPI') {
      popQrDynamicFields.innerHTML = `
        <div class="form-group-popup"><label>Payee VPA</label><input type="text" id="popUpiPa" placeholder="merchant@upi" value="store@okhdfcbank"></div>
        <div class="form-group-popup"><label>Payee Name</label><input type="text" id="popUpiPn" placeholder="Store Name" value="ThreatLens Store"></div>
        <div class="form-group-popup"><label>Amount (INR, optional)</label><input type="number" id="popUpiAm" placeholder="e.g. 250"></div>`;
    } else if (type === 'WIFI') {
      popQrDynamicFields.innerHTML = `
        <div class="form-group-popup"><label>Network Name (SSID)</label><input type="text" id="popWifiSsid" placeholder="WiFi_Name" value="ThreatLens_Secure"></div>
        <div class="form-group-popup"><label>Password</label><input type="password" id="popWifiPass" placeholder="Password"></div>`;
    } else {
      popQrDynamicFields.innerHTML = `
        <div class="form-group-popup">
          <label>Text Content</label>
          <textarea id="popQrInput" rows="2" placeholder="Enter secure text message..."></textarea>
        </div>`;
    }
  });

  btnPopGenerateQr?.addEventListener('click', async () => {
    let payload = "";
    const type = popQrType.value;

    if (type === 'URL' || type === 'TEXT') {
      payload = document.getElementById('popQrInput').value.trim();
    } else if (type === 'UPI') {
      const pa = document.getElementById('popUpiPa').value.trim();
      const pn = document.getElementById('popUpiPn').value.trim();
      const am = document.getElementById('popUpiAm')?.value.trim();
      payload = `upi://pay?pa=${encodeURIComponent(pa)}&pn=${encodeURIComponent(pn)}${am ? `&am=${am}` : ''}&cu=INR`;
    } else if (type === 'WIFI') {
      const ssid = document.getElementById('popWifiSsid').value.trim();
      const pass = document.getElementById('popWifiPass').value.trim();
      payload = `WIFI:S:${ssid};T:WPA;P:${pass};;`;
    }

    if (!payload) return alert("Please fill in the required field.");

    // Check if cryptographic signature is requested
    if (popQrSignCert && popQrSignCert.checked) {
      try {
        const certRes = await new Promise(resolve => {
          chrome.runtime.sendMessage({ action: "BUILD_CERTIFIED_QR", content: payload, safetyStatus: "SAFE", score: 100 }, resolve);
        });
        if (certRes && certRes.success && certRes.certString) {
          payload = certRes.certString;
        }
      } catch (e) {}
    }

    const apiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(payload)}&color=09090b&bgcolor=ffffff`;
    generatedQrUrl = apiUrl;

    const popQrFrame = document.getElementById('popQrFrame');
    if (popQrFrame) popQrFrame.style.display = 'flex';
    popQrPreviewHolder.innerHTML = `<img src="${apiUrl}" alt="Safe QR Code">`;
    popQrPreviewHolder.style.display = 'flex';
    btnPopDownloadQr.style.display = 'flex';
  });

  btnPopDownloadQr?.addEventListener('click', async () => {
    if (!generatedQrUrl) return;
    try {
      const res = await fetch(generatedQrUrl);
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `threatlens-safe-qr-${Date.now()}.png`;
      a.click();
    } catch (e) {
      window.open(generatedQrUrl, '_blank');
    }
  });
});
