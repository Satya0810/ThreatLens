// warning.js
// Logic for ThreatLens Security Intelligence Report Interstitial

import { Llm7Client } from '../core/Llm7Client.js';
import { GeminiClient } from '../core/GeminiClient.js';

document.addEventListener('DOMContentLoaded', async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const targetUrl = urlParams.get('url') || 'Unknown Destination';
  const riskScore = parseInt(urlParams.get('score'), 10) || 85;
  const threatType = urlParams.get('type') || 'MALICIOUS';
  
  let flags = [];
  try {
    flags = JSON.parse(urlParams.get('flags') || '[]');
  } catch (e) {
    flags = [urlParams.get('flags') || "Suspicious malicious patterns detected"];
  }

  const trustScore = Math.max(0, Math.min(100, 100 - riskScore));

  const urlDisplay = document.getElementById('urlDisplay');
  const scoreDisplay = document.getElementById('scoreDisplay');
  const scoreGaugeFill = document.getElementById('scoreGaugeFill');
  const threatStamp = document.getElementById('threatStamp');
  const flagsCountDisplay = document.getElementById('flagsCountDisplay');
  const safetyStatusDisplay = document.getElementById('safetyStatusDisplay');
  const siteCategoryPill = document.getElementById('siteCategoryPill');
  const aiExplanation = document.getElementById('aiExplanation');
  const btnBack = document.getElementById('btnBack');
  const btnProceed = document.getElementById('btnProceed');

  urlDisplay.textContent = targetUrl;
  scoreDisplay.textContent = trustScore;
  flagsCountDisplay.textContent = `${flags.length} Found`;

  // Animate 270-Degree SVG Gauge
  const radius = 46;
  const circumference = 2 * Math.PI * radius; // ~289.02
  const totalArc = circumference * (270 / 360); // ~216.77
  const activeArc = totalArc * (trustScore / 100);

  if (scoreGaugeFill) {
    scoreGaugeFill.style.strokeDasharray = `${activeArc} ${circumference}`;
  }

  if (threatStamp) {
    threatStamp.textContent = threatType.replace(/_/g, ' ');
  }

  if (siteCategoryPill) {
    siteCategoryPill.textContent = threatType.replace(/_/g, ' ').toUpperCase();
  }

  // Generate / Fetch AI Threat Insight via LLM7.io Fast Engine
  const llm7 = new Llm7Client();
  const insight = await llm7.getThreatExplanation(targetUrl, riskScore, flags, threatType);
  aiExplanation.textContent = insight;

  // Render Engine Logs Timeline
  const flagsContainer = document.getElementById('warningFlagsBox');
  if (flagsContainer && flags.length > 0) {
    flagsContainer.innerHTML = flags.slice(0, 5).map(f => `
      <div class="timeline-item">
        <div class="timeline-dot"></div>
        <div class="timeline-text">${f}</div>
      </div>
    `).join('') + (flags.length > 5 ? `
      <div style="font-size: 11px; color: #06b6d4; font-weight: 600; margin-top: 4px; padding-left: 18px;">
        + ${flags.length - 5} more intelligence flags...
      </div>
    ` : '');
  }

  const btnSandboxPreview = document.getElementById('btnSandboxPreview');
  const warningSandboxBox = document.getElementById('warningSandboxBox');
  const warningSandboxIframe = document.getElementById('warningSandboxIframe');
  const sandboxStatusMsg = document.getElementById('sandboxStatusMsg');
  const btnOpenFullSandbox = document.getElementById('btnOpenFullSandbox');

  async function loadSandboxedPage(url, iframe) {
    if (!url || !url.startsWith('http')) {
      iframe.srcdoc = `<div style="font-family:sans-serif;padding:24px;color:#94a3b8;background:#18181b;">Non-HTTP payload cannot be previewed in web sandbox.</div>`;
      return;
    }

    if (sandboxStatusMsg) sandboxStatusMsg.textContent = "Fetching & Isolating DOM...";

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 7000); // 7s timeout

      const res = await fetch(url, {
        signal: controller.signal,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ThreatLens-Sandbox/1.0'
        }
      });
      clearTimeout(timeoutId);

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const rawHtml = await res.text();

      // Sanitize HTML
      let sanitized = rawHtml
        .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
        .replace(/\bon\w+\s*=\s*(["'])[\s\S]*?\1/gi, "")
        .replace(/<form\b[^>]*>/gi, '<form onsubmit="return false;" style="pointer-events:none;">');

      // Inject base href so relative images and styles resolve
      if (/<head[^>]*>/i.test(sanitized)) {
        sanitized = sanitized.replace(/(<head[^>]*>)/i, `$1<base href="${url}">`);
      } else {
        sanitized = `<base href="${url}">${sanitized}`;
      }

      // Safe banner
      const banner = `
        <div style="background:#09090b;color:#00f0ff;padding:6px 12px;font-family:sans-serif;font-size:11px;border-bottom:2px solid #00f0ff;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:999999;">
          <span>🔒 <strong>ThreatLens Isolated Sandbox</strong> (Scripts & Cookies Blocked)</span>
          <span style="color:#a1a1aa;font-size:10px;">Host: ${new URL(url).hostname}</span>
        </div>
      `;
      sanitized = sanitized.replace(/(<body[^>]*>)/i, `$1${banner}`);

      iframe.removeAttribute('src');
      iframe.srcdoc = sanitized;

      if (sandboxStatusMsg) sandboxStatusMsg.textContent = "Safe DOM Rendered (Scripts Disabled)";
    } catch (err) {
      console.warn("Sandbox fetch failed:", err);
      iframe.srcdoc = `
        <div style="font-family:sans-serif;padding:30px 20px;color:#ef4444;background:#18181b;height:100%;box-sizing:border-box;">
          <h3 style="margin:0 0 8px 0;font-size:15px;">⚠️ Isolated Fetch Intercepted</h3>
          <p style="color:#cbd5e1;font-size:12px;line-height:1.5;margin:0 0 10px 0;">
            Could not directly fetch remote page: ${err.message}. The target server may be offline or blocking automated requests.
          </p>
          <div style="font-family:monospace;font-size:11px;color:#94a3b8;background:#09090b;padding:8px 10px;border-radius:6px;word-break:break-all;">
            ${url}
          </div>
        </div>
      `;
      if (sandboxStatusMsg) sandboxStatusMsg.textContent = "Fetch Blocked";
    }
  }

  if (btnSandboxPreview && warningSandboxBox && warningSandboxIframe) {
    btnSandboxPreview.addEventListener('click', () => {
      if (warningSandboxBox.style.display === 'none') {
        warningSandboxBox.style.display = 'block';
        btnSandboxPreview.innerHTML = '<span>🔒</span> Hide Sandbox';
        loadSandboxedPage(targetUrl, warningSandboxIframe);
      } else {
        warningSandboxBox.style.display = 'none';
        btnSandboxPreview.innerHTML = '<span>🔬</span> Sandbox Preview';
      }
    });
  }

  if (btnOpenFullSandbox) {
    btnOpenFullSandbox.addEventListener('click', () => {
      const fullUrl = chrome.runtime.getURL(`pages/sandbox.html?url=${encodeURIComponent(targetUrl)}&score=${riskScore}&type=${encodeURIComponent(threatType)}`);
      window.open(fullUrl, '_blank');
    });
  }

  btnBack.addEventListener('click', () => {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      window.location.href = 'https://google.com';
    }
  });

  btnProceed.addEventListener('click', () => {
    const confirmation = confirm("WARNING: ThreatLens flagged this page as high risk.\n\nAre you sure you want to bypass ThreatLens protection?");
    if (confirmation) {
      chrome.runtime.sendMessage({ action: "ALLOW_BYPASS", url: targetUrl }, () => {
        window.location.href = targetUrl;
      });
    }
  });
});
