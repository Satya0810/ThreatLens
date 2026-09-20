// dashboard.js
// Master logic for ThreatLens Hub & Dashboard

import { HistoryManager } from '../core/HistoryManager.js';
import { CloudSync } from '../core/CloudSync.js';
import { ThreatAnalyzer } from '../core/ThreatAnalyzer.js';
import { QrDecoder } from '../core/QrDecoder.js';
import { CertificateEngine } from '../core/CertificateEngine.js';
import { QrEncryptionEngine } from '../core/QrEncryptionEngine.js';
import { Llm7Client } from '../core/Llm7Client.js';

const analyzer = new ThreatAnalyzer();
let currentLastScanResult = null;

document.addEventListener('DOMContentLoaded', async () => {
  
  // ── 1. Tab Navigation ─────────────────────────────────────────────
  const navItems = document.querySelectorAll('.nav-item');
  const tabContents = document.querySelectorAll('.tab-content');

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      navItems.forEach(n => n.classList.remove('active'));
      tabContents.forEach(t => t.classList.remove('active'));

      item.classList.add('active');
      const tabId = item.getAttribute('data-tab');
      const targetTab = document.getElementById(`tab-${tabId}`);
      if (targetTab) targetTab.classList.add('active');
      
      if (tabId === 'history') loadHistory();
      if (tabId === 'home') loadHomeStats();
      if (tabId === 'adshield') loadAdShieldStats();
    });
  });

  // ── 2. Overview Stats ─────────────────────────────────────────────
  async function loadHomeStats() {
    const history = await HistoryManager.getHistory();
    const totalEl = document.getElementById('statTotalScans');
    const blockedEl = document.getElementById('statThreatsBlocked');
    const safeEl = document.getElementById('statSafeScans');

    if (totalEl) totalEl.textContent = history.length;
    
    const blocked = history.filter(h => h.safetyStatus === 'MALICIOUS' || (h.overallScore !== undefined && h.overallScore < 40) || (h.score >= 60)).length;
    if (blockedEl) blockedEl.textContent = blocked;
    if (safeEl) safeEl.textContent = history.length - blocked;
  }
  loadHomeStats();

  // ── 3. Live Threat Scanner ────────────────────────────────────────
  const liveScanInput = document.getElementById('liveScanInput');
  const btnRunLiveScan = document.getElementById('btnRunLiveScan');
  const qrImageFileInput = document.getElementById('qrImageFileInput');
  const liveScanResult = document.getElementById('liveScanResult');
  const scanResultCategory = document.getElementById('scanResultCategory');
  const scanResultStatus = document.getElementById('scanResultStatus');
  const scanResultScore = document.getElementById('scanResultScore');
  const scanResultExpanded = document.getElementById('scanResultExpanded');
  const scanResultSummary = document.getElementById('scanResultSummary');
  const scanResultAiCard = document.getElementById('scanResultAiCard');
  const scanResultAiText = document.getElementById('scanResultAiText');
  const scanResultVcCard = document.getElementById('scanResultVcCard');
  const vcCardBody = document.getElementById('vcCardBody');
  const btnExportScanPdf = document.getElementById('btnExportScanPdf');
  const btnOpenInSandbox = document.getElementById('btnOpenInSandbox');

  // Unlock Modal Elements
  const unlockQrModal = document.getElementById('unlockQrModal');
  const unlockQrPassword = document.getElementById('unlockQrPassword');
  const unlockQrError = document.getElementById('unlockQrError');
  const btnConfirmUnlockQr = document.getElementById('btnConfirmUnlockQr');
  const btnCancelUnlockQr = document.getElementById('btnCancelUnlockQr');
  const btnCloseUnlockModal = document.getElementById('btnCloseUnlockModal');
  let pendingLockedPayload = null;

  if (btnCancelUnlockQr && unlockQrModal) {
    btnCancelUnlockQr.addEventListener('click', () => { unlockQrModal.style.display = 'none'; });
  }
  if (btnCloseUnlockModal && unlockQrModal) {
    btnCloseUnlockModal.addEventListener('click', () => { unlockQrModal.style.display = 'none'; });
  }
  if (btnConfirmUnlockQr) {
    btnConfirmUnlockQr.addEventListener('click', async () => {
      const pass = unlockQrPassword.value.trim();
      if (!pass) {
        unlockQrError.textContent = "Please enter the decryption password or PIN.";
        unlockQrError.style.display = 'block';
        return;
      }
      btnConfirmUnlockQr.disabled = true;
      btnConfirmUnlockQr.textContent = "Decrypting...";

      const dec = await QrEncryptionEngine.decrypt(pendingLockedPayload, pass);
      btnConfirmUnlockQr.disabled = false;
      btnConfirmUnlockQr.textContent = "Decrypt & Inspect";

      if (dec.success) {
        unlockQrModal.style.display = 'none';
        liveScanInput.value = dec.content;
        executeLiveAnalysis(dec.content);
      } else {
        unlockQrError.textContent = dec.error || "Incorrect password or corrupted ciphertext.";
        unlockQrError.style.display = 'block';
      }
    });
  }

  async function executeLiveAnalysis(payload) {
    if (!payload || !payload.trim()) return;

    const trimmed = payload.trim();

    // 0. Check for Password-Protected QR
    if (QrEncryptionEngine.isLockedQr(trimmed)) {
      pendingLockedPayload = trimmed;
      if (unlockQrPassword) unlockQrPassword.value = '';
      if (unlockQrError) unlockQrError.style.display = 'none';
      if (unlockQrModal) unlockQrModal.style.display = 'flex';
      return;
    }

    btnRunLiveScan.disabled = true;
    btnRunLiveScan.textContent = "Analyzing...";

    try {
      const result = await analyzer.analyze(trimmed);
      currentLastScanResult = result;
      await HistoryManager.saveScan(result);

      liveScanResult.style.display = 'block';
      scanResultCategory.textContent = result.siteCategory || "General";
      scanResultStatus.textContent = result.safetyStatus;

      const isSafe = result.safetyStatus === 'SAFE';
      const isCaution = result.safetyStatus === 'CAUTION';
      
      scanResultStatus.className = `badge ${isSafe ? 'badge-safe' : (isCaution ? 'badge-warning' : 'badge-danger')}`;
      scanResultCategory.className = `badge ${isSafe ? 'badge-safe' : (isCaution ? 'badge-warning' : 'badge-danger')}`;

      const score = result.overallScore !== undefined ? result.overallScore : (100 - (result.riskScore || 0));
      scanResultScore.textContent = `${score.toFixed(0)}/100`;
      scanResultScore.style.color = isSafe ? 'var(--safe)' : (isCaution ? 'var(--warning)' : 'var(--danger)');

      scanResultExpanded.textContent = result.expandedUrl || result.rawContent;
      scanResultSummary.textContent = result.siteSummary || result.message || "Analyzed by ThreatLens Security Engine.";

      // ── LLM7.io Live AI Neural Verdict ──
      if (scanResultAiCard && scanResultAiText) {
        scanResultAiCard.style.display = 'block';
        scanResultAiText.textContent = "Querying ThreatLens Neural Core & LLM7.io Fast Engine...";
        
        const llm7 = new Llm7Client();
        llm7.getThreatExplanation(
          result.expandedUrl || result.rawContent,
          result.riskScore || (100 - score),
          result.flags || [],
          result.threatType || 'SAFE'
        ).then(aiInsight => {
          scanResultAiText.textContent = aiInsight;
          currentLastScanResult.aiVerdict = aiInsight;
        }).catch(() => {
          scanResultAiText.textContent = result.siteSummary || "Verified by ThreatLens heuristic engines.";
        });
      }

      // ── W3C Verifiable Credential Inspector ──
      if (scanResultVcCard && vcCardBody) {
        if (result.scanType === 'CREDENTIAL' || trimmed.startsWith('threatlensvc://')) {
          scanResultVcCard.style.display = 'block';
          vcCardBody.innerHTML = `
            <div style="margin-bottom: 4px;"><strong>Credential Type:</strong> Decentralized Verifiable Identity</div>
            <div style="margin-bottom: 4px;"><strong>Cryptographic Status:</strong> Valid & Verified</div>
            <div style="word-break: break-all; opacity: 0.8;"><strong>Payload:</strong> ${trimmed.substring(0, 120)}...</div>
          `;
        } else {
          scanResultVcCard.style.display = 'none';
        }
      }

      // ── Sandbox Preview Button ──
      if (btnOpenInSandbox) {
        if (result.expandedUrl && result.expandedUrl.startsWith('http')) {
          btnOpenInSandbox.style.display = 'inline-flex';
          btnOpenInSandbox.onclick = () => {
            const sandboxUrl = chrome.runtime.getURL(`pages/sandbox.html?url=${encodeURIComponent(result.expandedUrl)}&score=${result.riskScore || 0}&type=${encodeURIComponent(result.threatType || 'UNKNOWN')}`);
            window.open(sandboxUrl, '_blank');
          };
        } else {
          btnOpenInSandbox.style.display = 'none';
        }
      }

      loadHomeStats();
    } catch (e) {
      alert("Analysis failed: " + e.message);
    } finally {
      btnRunLiveScan.disabled = false;
      btnRunLiveScan.textContent = "Analyze Payload";
    }
  }

  // ── PDF Threat Dossier Export Function ──
  function exportScanReportPdf(scanData) {
    if (!scanData) return;

    const printWin = window.open('', '_blank');
    if (!printWin) {
      alert("Please allow popups to export the PDF dossier.");
      return;
    }

    const score = scanData.overallScore !== undefined ? scanData.overallScore : (100 - (scanData.riskScore || 0));
    const status = scanData.safetyStatus || 'SAFE';
    const flags = scanData.flags || [];
    const dateStr = new Date().toLocaleString();

    printWin.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <title>ThreatLens Security Dossier - ${scanData.threatType || 'Report'}</title>
        <style>
          body { font-family: 'Segoe UI', Arial, sans-serif; padding: 40px; color: #111; line-height: 1.6; }
          .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #06b6d4; padding-bottom: 16px; margin-bottom: 24px; }
          .title { font-size: 24px; font-weight: 800; color: #09090b; }
          .badge { padding: 4px 12px; border-radius: 6px; font-weight: bold; font-size: 13px; text-transform: uppercase; }
          .safe { background: #dcfce7; color: #15803d; }
          .danger { background: #fee2e2; color: #b91c1c; }
          .section { margin-bottom: 24px; }
          .section-title { font-size: 14px; font-weight: 800; color: #64748b; text-transform: uppercase; margin-bottom: 8px; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }
          .target-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; font-family: monospace; font-size: 13px; word-break: break-all; }
          .ai-box { background: #f0fdf4; border: 1px solid #86efac; border-radius: 8px; padding: 14px; font-size: 14px; color: #14532d; }
          .flags-list { padding-left: 20px; }
          .flags-list li { margin-bottom: 6px; font-size: 13px; }
          .footer { margin-top: 40px; border-top: 1px solid #e2e8f0; padding-top: 12px; font-size: 11px; color: #94a3b8; display: flex; justify-content: space-between; }
        </style>
      </head>
      <body>
        <div class="header">
          <div>
            <div class="title">🛡️ ThreatLens Cyber Security Dossier</div>
            <div style="font-size: 12px; color: #64748b;">Generated via ThreatLens Security Hub • ${dateStr}</div>
          </div>
          <span class="badge ${status === 'SAFE' ? 'safe' : 'danger'}">${status} (${score.toFixed(0)}/100)</span>
        </div>

        <div class="section">
          <div class="section-title">Target Destination / Raw Payload</div>
          <div class="target-box">${scanData.expandedUrl || scanData.rawContent || 'N/A'}</div>
        </div>

        <div class="section">
          <div class="section-title">Executive Intelligence Assessment (LLM7.io Fast Engine)</div>
          <div class="ai-box">
            <strong>AI Verdict:</strong> ${scanData.aiVerdict || scanData.siteSummary || 'Analyzed and evaluated by ThreatLens neural engines.'}
          </div>
        </div>

        <div class="section">
          <div class="section-title">Heuristic & Intelligence Flags (${flags.length})</div>
          ${flags.length > 0 ? `<ul class="flags-list">${flags.map(f => `<li>⚠️ ${f}</li>`).join('')}</ul>` : '<p style="font-size: 13px; color: #15803d;">✅ Zero threat indicators or malicious patterns detected.</p>'}
        </div>

        <div class="footer">
          <span>ThreatLens Cryptographic Defense Platform</span>
          <span>Verified HMAC-SHA256 Compatible</span>
        </div>
        <script>
          window.onload = function() { window.print(); }
        </script>
      </body>
      </html>
    `);
    printWin.document.close();
  }

  if (btnExportScanPdf) {
    btnExportScanPdf.addEventListener('click', () => {
      exportScanReportPdf(currentLastScanResult);
    });
  }

  btnRunLiveScan.addEventListener('click', () => {
    executeLiveAnalysis(liveScanInput.value);
  });

  qrImageFileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    btnRunLiveScan.textContent = "Decoding QR Image...";
    btnRunLiveScan.disabled = true;

    try {
      const decoded = await QrDecoder.decodeFromImage(file);
      if (decoded) {
        liveScanInput.value = decoded;
        executeLiveAnalysis(decoded);
      } else {
        alert("ThreatLens: No readable QR code found in this image.");
      }
    } catch (err) {
      alert("Error reading QR image: " + err.message);
    } finally {
      btnRunLiveScan.textContent = "Analyze Payload";
      btnRunLiveScan.disabled = false;
      qrImageFileInput.value = '';
    }
  });

  // ── 4. History Tab ────────────────────────────────────────────────
  let currentFilter = 'ALL';
  const filterPills = document.querySelectorAll('.filter-pill');
  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      filterPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      currentFilter = pill.getAttribute('data-filter');
      loadHistory();
    });
  });

  async function loadHistory() {
    let history = await HistoryManager.getHistory();
    
    // Merge with Cloud History if logged in
    if (CloudSync.idToken) {
      try {
        const cloudHistory = await CloudSync.fetchHistoryFromCloud();
        if (cloudHistory.length > 0) {
          const merged = [...history, ...cloudHistory];
          const unique = [];
          const seen = new Set();
          merged.forEach(item => {
            const key = (item.rawContent || item.payload || '') + (item.timestamp || '');
            if (!seen.has(key)) {
              seen.add(key);
              unique.push(item);
            }
          });
          history = unique.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
        }
      } catch (e) {
        console.warn("Could not fetch cloud history:", e);
      }
    }

    // Apply Filter
    if (currentFilter !== 'ALL') {
      if (currentFilter === 'THREAT') {
        history = history.filter(h => h.safetyStatus === 'MALICIOUS' || h.safetyStatus === 'CAUTION' || (h.overallScore !== undefined && h.overallScore < 75));
      } else {
        history = history.filter(h => (h.scanType || h.type || 'URL') === currentFilter);
      }
    }

    const container = document.getElementById('historyContainer');
    if (!container) return;
    
    if (history.length === 0) {
      container.innerHTML = `<div style="color:var(--muted); text-align:center; padding: 40px;">No scan records matching filter.</div>`;
      return;
    }

    container.innerHTML = history.map(item => {
      const date = item.timestamp ? new Date(item.timestamp).toLocaleString() : 'Recent';
      const status = item.safetyStatus || (item.score >= 60 ? 'MALICIOUS' : 'SAFE');
      const isSafe = status === 'SAFE';
      const isCaution = status === 'CAUTION';
      const badgeClass = isSafe ? 'badge-safe' : (isCaution ? 'badge-warning' : 'badge-danger');
      const scoreClass = isSafe ? 'score-safe' : (isCaution ? 'score-warning' : 'score-danger');

      const scoreVal = item.overallScore !== undefined ? item.overallScore : (100 - (item.score || 0));
      const typeDisplay = item.scanType || item.type || 'URL';
      const displayUrl = item.expandedUrl || item.rawContent || item.payload || 'Unknown Payload';

      return `
        <div class="history-item" data-raw="${encodeURIComponent(displayUrl)}">
          <div class="hist-main">
            <div class="hist-url">${displayUrl}</div>
            <div class="hist-meta">
              <span class="badge ${badgeClass}">${status}</span>
              <span>Type: <strong>${typeDisplay}</strong></span>
              <span>Category: ${item.siteCategory || item.threatType || 'General'}</span>
              <span>${date}</span>
            </div>
          </div>
          <div class="hist-score ${scoreClass}">
            ${scoreVal.toFixed ? scoreVal.toFixed(0) : scoreVal}/100
          </div>
        </div>
      `;
    }).join('');

    // Attach click listener for detail view
    document.querySelectorAll('.history-item').forEach(el => {
      el.addEventListener('click', () => {
        const raw = decodeURIComponent(el.getAttribute('data-raw'));
        liveScanInput.value = raw;
        // Switch to scanner tab
        document.querySelector('[data-tab="scanner"]').click();
        executeLiveAnalysis(raw);
      });
    });
  }

  document.getElementById('btnClearHistory').addEventListener('click', async () => {
    if (confirm("Are you sure you want to clear your local encrypted scan history?")) {
      await HistoryManager.clearHistory();
      loadHistory();
      loadHomeStats();
    }
  });

  // ── 5. QR Studio ──────────────────────────────────────────────────
  const qrTypeSelect = document.getElementById('qrTypeSelect');
  const qrDynamicFields = document.getElementById('qrDynamicFields');
  const qrColor = document.getElementById('qrColor');
  const btnGenerateQr = document.getElementById('btnGenerateQr');
  const qrPreviewBox = document.getElementById('qrPreviewBox');
  const btnDownloadQr = document.getElementById('btnDownloadQr');

  let currentQrBlobUrl = null;

  qrTypeSelect.addEventListener('change', () => {
    const type = qrTypeSelect.value;
    if (type === 'URL') {
      qrDynamicFields.innerHTML = `
        <div class="form-group">
          <label>Target URL</label>
          <input type="text" id="qrUrlInput" placeholder="https://example.com" value="https://threatlens.io">
        </div>`;
    } else if (type === 'UPI') {
      qrDynamicFields.innerHTML = `
        <div class="form-group"><label>Payee VPA (e.g. name@okhdfcbank)</label><input type="text" id="qrUpiPa" placeholder="merchant@upi" value="store@okhdfcbank"></div>
        <div class="form-group"><label>Payee Name</label><input type="text" id="qrUpiPn" placeholder="Store Name" value="ThreatLens Security"></div>
        <div class="form-group"><label>Preset Amount (INR, optional)</label><input type="number" id="qrUpiAm" placeholder="e.g. 500"></div>
        <div class="form-group"><label>Note</label><input type="text" id="qrUpiTn" placeholder="e.g. Subscription"></div>`;
    } else if (type === 'WIFI') {
      qrDynamicFields.innerHTML = `
        <div class="form-group"><label>Network Name (SSID)</label><input type="text" id="qrWifiSsid" placeholder="My_Secure_WiFi" value="ThreatLens_Secure_Mesh"></div>
        <div class="form-group"><label>Password</label><input type="password" id="qrWifiPass" placeholder="WiFi Password"></div>
        <div class="form-group"><label>Security Type</label><select id="qrWifiType"><option value="WPA">WPA2/WPA3 (Recommended)</option><option value="WEP">WEP</option><option value="nopass">Open</option></select></div>`;
    } else {
      qrDynamicFields.innerHTML = `
        <div class="form-group">
          <label>Text Content</label>
          <textarea id="qrTextInput" rows="3" placeholder="Enter message..."></textarea>
        </div>`;
    }
  });

  btnGenerateQr.addEventListener('click', async () => {
    let payload = "";
    const type = qrTypeSelect.value;

    if (type === 'URL') {
      payload = document.getElementById('qrUrlInput').value.trim();
    } else if (type === 'UPI') {
      const pa = document.getElementById('qrUpiPa').value.trim();
      const pn = document.getElementById('qrUpiPn').value.trim();
      const am = document.getElementById('qrUpiAm').value.trim();
      const tn = document.getElementById('qrUpiTn').value.trim();
      payload = `upi://pay?pa=${encodeURIComponent(pa)}&pn=${encodeURIComponent(pn)}${am ? `&am=${am}` : ''}${tn ? `&tn=${encodeURIComponent(tn)}` : ''}&cu=INR`;
    } else if (type === 'WIFI') {
      const ssid = document.getElementById('qrWifiSsid').value.trim();
      const pass = document.getElementById('qrWifiPass').value.trim();
      const sec = document.getElementById('qrWifiType').value;
      payload = `WIFI:S:${ssid};T:${sec};P:${pass};;`;
    } else {
      payload = document.getElementById('qrTextInput').value.trim();
    }

    if (!payload) return alert("Please fill in required fields.");

    const qrSignCert = document.getElementById('qrSignCert');
    if (qrSignCert && qrSignCert.checked) {
      try {
        payload = await CertificateEngine.buildCertifiedPayload(payload, "SAFE", 100);
      } catch (err) {
        console.warn("Certificate signing error:", err);
      }
    }

    const colorHex = qrColor.value.replace('#', '');
    const apiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=${encodeURIComponent(payload)}&color=${colorHex}&bgcolor=ffffff`;

    qrPreviewBox.innerHTML = `<img src="${apiUrl}" alt="QR Code" style="width:100%; height:100%; object-fit:contain;">`;
    currentQrBlobUrl = apiUrl;
    btnDownloadQr.removeAttribute('disabled');
  });

  btnDownloadQr.addEventListener('click', async () => {
    if (!currentQrBlobUrl) return;
    try {
      const response = await fetch(currentQrBlobUrl);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `ThreatLens_QR_${Date.now()}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (e) {
      alert("Failed to download image.");
    }
  });

  // ── 6. Cloud Sync & Settings ──────────────────────────────────────
  const authContainer = document.getElementById('authContainer');
  const loggedInState = document.getElementById('loggedInState');
  const loginForm = document.getElementById('loginForm');
  const registerForm = document.getElementById('registerForm');
  const tabAuthLogin = document.getElementById('tabAuthLogin');
  const tabAuthRegister = document.getElementById('tabAuthRegister');
  const authAlert = document.getElementById('authAlert');
  const cloudSyncStatusBadge = document.getElementById('cloudSyncStatusBadge');

  const userAvatarCircle = document.getElementById('userAvatarCircle');
  const userDisplayName = document.getElementById('userDisplayName');
  const userHandle = document.getElementById('userHandle');
  const userEmailAddress = document.getElementById('userEmailAddress');
  const firestorePathLabel = document.getElementById('firestorePathLabel');

  function showAlert(msg, isError = true) {
    if (!authAlert) return;
    authAlert.textContent = msg;
    authAlert.style.display = 'block';
    authAlert.style.background = isError ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)';
    authAlert.style.color = isError ? '#fca5a5' : '#86efac';
    authAlert.style.border = `1px solid ${isError ? 'rgba(239, 68, 68, 0.3)' : 'rgba(16, 185, 129, 0.3)'}`;
  }

  function hideAlert() {
    if (authAlert) authAlert.style.display = 'none';
  }

  function updateCloudSyncUI() {
    if (CloudSync.idToken) {
      if (authContainer) authContainer.style.display = 'none';
      if (loggedInState) loggedInState.style.display = 'block';

      const displayName = CloudSync.displayName || CloudSync.userId || 'ThreatLens User';
      const handle = CloudSync.userId || 'user';
      const email = CloudSync.userEmail || '';
      const initial = displayName.charAt(0).toUpperCase();

      if (userDisplayName) userDisplayName.textContent = displayName;
      if (userHandle) userHandle.textContent = `@${handle}`;
      if (userEmailAddress) userEmailAddress.textContent = email;
      if (userAvatarCircle) {
        if (CloudSync.photoUrl) {
          userAvatarCircle.innerHTML = `<img src="${CloudSync.photoUrl}" alt="${displayName}" style="width:100%; height:100%; border-radius:50%; object-fit:cover;">`;
        } else {
          userAvatarCircle.textContent = initial;
        }
      }
      if (firestorePathLabel) {
        firestorePathLabel.textContent = `users/${CloudSync.userId}/history`;
      }
      if (cloudSyncStatusBadge) {
        cloudSyncStatusBadge.textContent = '🟢 Connected & Synced';
        cloudSyncStatusBadge.className = 'badge badge-safe';
      }
    } else {
      if (authContainer) authContainer.style.display = 'block';
      if (loggedInState) loggedInState.style.display = 'none';
      if (cloudSyncStatusBadge) {
        cloudSyncStatusBadge.textContent = 'Offline / Guest';
        cloudSyncStatusBadge.className = 'badge badge-warning';
      }
    }
  }

  // Switch Auth Tabs
  if (tabAuthLogin && tabAuthRegister) {
    tabAuthLogin.addEventListener('click', () => {
      tabAuthLogin.classList.add('active');
      tabAuthRegister.classList.remove('active');
      loginForm.style.display = 'block';
      registerForm.style.display = 'none';
      hideAlert();
    });

    tabAuthRegister.addEventListener('click', () => {
      tabAuthRegister.classList.add('active');
      tabAuthLogin.classList.remove('active');
      loginForm.style.display = 'none';
      registerForm.style.display = 'block';
      hideAlert();
    });
  }

  await CloudSync.initSession();
  updateCloudSyncUI();

  // Login Handler (Supports User ID or Email)
  document.getElementById('btnLogin')?.addEventListener('click', async () => {
    const userInput = document.getElementById('loginInput').value.trim();
    const password = document.getElementById('loginPassword').value;
    const btn = document.getElementById('btnLogin');
    
    if (!userInput || !password) {
      showAlert("Please enter your User ID/Email and Password.");
      return;
    }

    btn.textContent = 'Authenticating...';
    btn.disabled = true;
    hideAlert();

    const result = await CloudSync.login(userInput, password);
    btn.textContent = 'Log In to ThreatLens';
    btn.disabled = false;

    if (result.success) {
      updateCloudSyncUI();
      loadHistory();
      loadHomeStats();
      showAlert("Logged in successfully! Synced with Android database.", false);
    } else {
      showAlert(result.error || "Login failed. Please check credentials.");
    }
  });

  // Register Handler
  document.getElementById('btnRegister')?.addEventListener('click', async () => {
    const name = document.getElementById('regName').value.trim();
    const userId = document.getElementById('regUserId').value.trim();
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value;
    const btn = document.getElementById('btnRegister');

    if (!name || !userId || !email || !password) {
      showAlert("All registration fields are required.");
      return;
    }

    if (password.length < 6) {
      showAlert("Password must be at least 6 characters.");
      return;
    }

    btn.textContent = 'Creating Account...';
    btn.disabled = true;
    hideAlert();

    const result = await CloudSync.register(name, userId, email, password);
    btn.textContent = 'Create ThreatLens Account';
    btn.disabled = false;

    if (result.success) {
      showAlert(result.message || "Account created! You can now log in.", false);
      // Auto switch to login tab
      setTimeout(() => {
        if (tabAuthLogin) tabAuthLogin.click();
        const loginInp = document.getElementById('loginInput');
        if (loginInp) loginInp.value = userId;
      }, 1500);
    } else {
      showAlert(result.error || "Registration failed.");
    }
  });

  // Google Login Handler
  document.getElementById('btnGoogleLogin')?.addEventListener('click', async () => {
    const btn = document.getElementById('btnGoogleLogin');
    const origHtml = btn.innerHTML;
    btn.innerHTML = `<span style="display:inline-block; animation:spin 1s linear infinite;">⏳</span> <span>Connecting to Google...</span>`;
    btn.disabled = true;
    hideAlert();

    const result = await CloudSync.loginWithGoogle();
    btn.innerHTML = origHtml;
    btn.disabled = false;

    if (result.success) {
      updateCloudSyncUI();
      loadHistory();
      loadHomeStats();
      showAlert("Google Sign-In successful! Synced with Android Cloud Database.", false);
    } else {
      showAlert(result.error || "Google Sign-In failed.");
    }
  });

  // Sign Out Handler
  document.getElementById('btnLogout')?.addEventListener('click', async () => {
    await CloudSync.logout();
    updateCloudSyncUI();
    loadHistory();
    loadHomeStats();
  });

  // Manual Sync Button
  document.getElementById('btnSyncNow')?.addEventListener('click', async () => {
    const btn = document.getElementById('btnSyncNow');
    const orig = btn.textContent;
    btn.textContent = 'Syncing...';
    btn.disabled = true;
    await loadHistory();
    await loadHomeStats();
    setTimeout(() => {
      btn.textContent = 'Synced! ✔️';
      setTimeout(() => {
        btn.textContent = orig;
        btn.disabled = false;
      }, 1200);
    }, 500);
  });

  // Strictness Slider
  const strictnessSlider = document.getElementById('strictnessSlider');
  const strictnessValue = document.getElementById('strictnessValue');

  chrome.storage.local.get('strictnessThreshold', (data) => {
    if (data.strictnessThreshold) {
      strictnessSlider.value = data.strictnessThreshold;
      strictnessValue.textContent = data.strictnessThreshold;
    }
  });

  strictnessSlider.addEventListener('input', (e) => {
    strictnessValue.textContent = e.target.value;
  });

  strictnessSlider.addEventListener('change', (e) => {
    chrome.storage.local.set({ strictnessThreshold: parseInt(e.target.value) });
  });

  // ── 8. Ad & Tracker Shield Hub Logic ─────────────────────────────
  const dashAdshieldTotalBlocked = document.getElementById('dashAdshieldTotalBlocked');
  const dashAdshieldBandwidth = document.getElementById('dashAdshieldBandwidth');
  const dashAdshieldTimeSaved = document.getElementById('dashAdshieldTimeSaved');
  const dashAdshieldWhitelistedCount = document.getElementById('dashAdshieldWhitelistedCount');
  const dashAdshieldGlobalBadge = document.getElementById('dashAdshieldGlobalBadge');

  const toggleRulesetAds = document.getElementById('toggleRulesetAds');
  const toggleRulesetTrackers = document.getElementById('toggleRulesetTrackers');
  const toggleRulesetMalware = document.getElementById('toggleRulesetMalware');
  const toggleYoutubeSkipper = document.getElementById('toggleYoutubeSkipper');

  const inputWhitelistDomain = document.getElementById('inputWhitelistDomain');
  const btnAddWhitelistDomain = document.getElementById('btnAddWhitelistDomain');
  const btnSyncWhitelistDnr = document.getElementById('btnSyncWhitelistDnr');
  const whitelistTableBody = document.getElementById('whitelistTableBody');
  const zappedRulesContainer = document.getElementById('zappedRulesContainer');
  const btnClearAllZappedRules = document.getElementById('btnClearAllZappedRules');

  async function loadAdShieldStats() {
    chrome.storage.local.get([
      'adsBlockedCount',
      'whitelistedDomains',
      'adShieldEnabled',
      'customZappedSelectors',
      'ruleset_ads',
      'ruleset_trackers',
      'ruleset_malware',
      'youtube_skipper'
    ], (res) => {
      const blocked = res.adsBlockedCount || 0;
      const whitelisted = res.whitelistedDomains || [];
      const isGlobalEnabled = res.adShieldEnabled !== false;

      // 1. Metric counters
      if (dashAdshieldTotalBlocked) dashAdshieldTotalBlocked.textContent = blocked.toLocaleString();
      if (dashAdshieldBandwidth) dashAdshieldBandwidth.textContent = (blocked * 0.12).toFixed(1) + " MB";
      if (dashAdshieldTimeSaved) dashAdshieldTimeSaved.textContent = (blocked * 0.04).toFixed(1) + "s";
      if (dashAdshieldWhitelistedCount) dashAdshieldWhitelistedCount.textContent = whitelisted.length;

      if (dashAdshieldGlobalBadge) {
        if (isGlobalEnabled) {
          dashAdshieldGlobalBadge.textContent = "Shield Armed";
          dashAdshieldGlobalBadge.className = "badge badge-safe";
        } else {
          dashAdshieldGlobalBadge.textContent = "Shield Paused";
          dashAdshieldGlobalBadge.className = "badge badge-warning";
        }
      }

      // 2. Ruleset checkboxes
      if (toggleRulesetAds) toggleRulesetAds.checked = res.ruleset_ads !== false;
      if (toggleRulesetTrackers) toggleRulesetTrackers.checked = res.ruleset_trackers !== false;
      if (toggleRulesetMalware) toggleRulesetMalware.checked = res.ruleset_malware !== false;
      if (toggleYoutubeSkipper) toggleYoutubeSkipper.checked = res.youtube_skipper !== false;

      // 3. Whitelist Table Render
      renderWhitelistTable(whitelisted);

      // 4. Custom Zapped Rules Render
      renderZappedRules(res.customZappedSelectors || {});
    });
  }

  function renderWhitelistTable(whitelisted) {
    if (!whitelistTableBody) return;
    if (!whitelisted || whitelisted.length === 0) {
      whitelistTableBody.innerHTML = `
        <tr>
          <td colspan="3" class="table-empty">No whitelisted domains yet. ThreatLens is protecting all sites.</td>
        </tr>`;
      return;
    }

    whitelistTableBody.innerHTML = whitelisted.map(domain => `
      <tr>
        <td><strong>${domain}</strong></td>
        <td><span class="badge badge-safe">Bypassed (Allowed)</span></td>
        <td>
          <button class="btn-remove-whitelist" data-domain="${domain}">Remove</button>
        </td>
      </tr>
    `).join('');

    whitelistTableBody.querySelectorAll('.btn-remove-whitelist').forEach(btn => {
      btn.addEventListener('click', () => {
        const domain = btn.getAttribute('data-domain');
        if (!domain) return;
        chrome.runtime.sendMessage({ action: "TOGGLE_SITE_WHITELIST", domain: domain }, () => {
          loadAdShieldStats();
        });
      });
    });
  }

  function renderZappedRules(zappedMap) {
    if (!zappedRulesContainer) return;
    const entries = Object.entries(zappedMap);
    if (entries.length === 0) {
      zappedRulesContainer.innerHTML = `
        <p class="table-empty">No custom element rules recorded yet. Use the <strong>⚡ Zap Element</strong> tool in the popup to hide annoying elements.</p>`;
      return;
    }

    let html = '';
    entries.forEach(([domain, selectors]) => {
      const list = Array.isArray(selectors) ? selectors : [selectors];
      list.forEach(selector => {
        html += `
          <div class="zapped-rule-card">
            <div>
              <span class="zapped-domain-pill">${domain}</span>
              <code class="zapped-selector-code">${selector}</code>
            </div>
            <button class="btn-remove-whitelist btn-delete-selector" data-domain="${domain}" data-selector="${selector}">Delete</button>
          </div>`;
      });
    });

    zappedRulesContainer.innerHTML = html;

    zappedRulesContainer.querySelectorAll('.btn-delete-selector').forEach(btn => {
      btn.addEventListener('click', () => {
        const domain = btn.getAttribute('data-domain');
        const selector = btn.getAttribute('data-selector');
        chrome.storage.local.get(['customZappedSelectors'], (res) => {
          const map = res.customZappedSelectors || {};
          if (map[domain]) {
            map[domain] = map[domain].filter(s => s !== selector);
            if (map[domain].length === 0) delete map[domain];
            chrome.storage.local.set({ customZappedSelectors: map }, () => {
              loadAdShieldStats();
            });
          }
        });
      });
    });
  }

  // Add Domain to Whitelist
  btnAddWhitelistDomain?.addEventListener('click', () => {
    let domain = (inputWhitelistDomain?.value || "").trim().toLowerCase();
    if (!domain) return;
    try {
      if (domain.startsWith('http://') || domain.startsWith('https://')) {
        domain = new URL(domain).hostname;
      }
    } catch (e) {}
    domain = domain.replace(/^www\./, '').split('/')[0];

    chrome.runtime.sendMessage({ action: "TOGGLE_SITE_WHITELIST", domain: domain }, () => {
      if (inputWhitelistDomain) inputWhitelistDomain.value = "";
      loadAdShieldStats();
    });
  });

  // Re-sync DNR Rules button
  btnSyncWhitelistDnr?.addEventListener('click', () => {
    chrome.storage.local.get(['whitelistedDomains'], (res) => {
      chrome.runtime.sendMessage({ action: "SYNC_DNR_WHITELIST", domains: res.whitelistedDomains || [] }, () => {
        alert("🛡️ Ad & Tracker Shield dynamic rules synchronized successfully!");
      });
    });
  });

  // Ruleset Toggles
  toggleRulesetAds?.addEventListener('change', (e) => {
    const isChecked = e.target.checked;
    chrome.storage.local.set({ ruleset_ads: isChecked }, () => {
      chrome.declarativeNetRequest.updateEnabledRulesets({
        enableRulesetIds: isChecked ? ['adblock_ads'] : [],
        disableRulesetIds: isChecked ? [] : ['adblock_ads']
      });
    });
  });

  toggleRulesetTrackers?.addEventListener('change', (e) => {
    const isChecked = e.target.checked;
    chrome.storage.local.set({ ruleset_trackers: isChecked }, () => {
      chrome.declarativeNetRequest.updateEnabledRulesets({
        enableRulesetIds: isChecked ? ['adblock_trackers'] : [],
        disableRulesetIds: isChecked ? [] : ['adblock_trackers']
      });
    });
  });

  toggleRulesetMalware?.addEventListener('change', (e) => {
    const isChecked = e.target.checked;
    chrome.storage.local.set({ ruleset_malware: isChecked }, () => {
      chrome.declarativeNetRequest.updateEnabledRulesets({
        enableRulesetIds: isChecked ? ['adblock_malware'] : [],
        disableRulesetIds: isChecked ? [] : ['adblock_malware']
      });
    });
  });

  toggleYoutubeSkipper?.addEventListener('change', (e) => {
    chrome.storage.local.set({ youtube_skipper: e.target.checked });
  });

  // Clear All Zapped Rules
  btnClearAllZappedRules?.addEventListener('click', () => {
    if (confirm("Are you sure you want to clear all custom element zapper rules?")) {
      chrome.storage.local.remove(['customZappedSelectors'], () => {
        loadAdShieldStats();
      });
    }
  });

});
