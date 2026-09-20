// ThreatAnalyzer.js
// Master pipeline matching Android ThreatAnalyzer.kt

import { HeuristicsEngine } from './HeuristicsEngine.js';
import { UpiPaymentAnalyzer } from './UpiPaymentAnalyzer.js';
import { WifiThreatAnalyzer } from './WifiThreatAnalyzer.js';
import { WebsiteCategorizer } from './WebsiteCategorizer.js';
import { UrlExpander } from './UrlExpander.js';
import { PayloadParser } from './PayloadParser.js';
import { CloudSync } from './CloudSync.js';
import { GeminiClient } from './GeminiClient.js';

import { CertificateEngine } from './CertificateEngine.js';

export class ThreatAnalyzer {
  constructor() {
    this.heuristics = new HeuristicsEngine();
    this.gemini = new GeminiClient();
  }

  /**
   * Analyzes any QR content / URL string and returns a comprehensive ScanResult.
   * Standardizes on Android ScanResult schema:
   * overallScore: 0 (Dangerous) to 100 (Safe)
   * riskScore: 0 (Safe) to 100 (Dangerous)
   * safetyStatus: 'SAFE' | 'CAUTION' | 'MALICIOUS'
   */
  async analyze(rawContent, options = {}) {
    if (!rawContent) {
      return this._buildSafeResult("", "Empty payload");
    }

    const trimmed = rawContent.trim();

    // ── 0. Cryptographic Certificate Verification ─────────────────────
    if (trimmed.toLowerCase().startsWith(CertificateEngine.CERT_SCHEME)) {
      const certCheck = await CertificateEngine.verify(trimmed);
      if (certCheck.isValid) {
        // Inner validated content
        const innerResult = await this.analyze(certCheck.content);
        return {
          ...innerResult,
          isCertified: true,
          certId: certCheck.certId,
          certScore: certCheck.score,
          certStatus: certCheck.status,
          safetyStatus: 'SAFE',
          overallScore: 100,
          riskScore: 0,
          siteCategory: `🛡️ Verified Certified QR (${certCheck.certId})`,
          flags: [`✅ Verified HMAC-SHA256 Cryptographic Certificate (ID: ${certCheck.certId})`, ...(innerResult.flags || [])]
        };
      } else if (certCheck.isTampered) {
        return {
          rawContent: trimmed,
          isUrl: false,
          scanType: 'TAMPERED_CERT',
          originalUrl: trimmed,
          expandedUrl: trimmed,
          domain: null,
          safetyStatus: 'MALICIOUS',
          overallScore: 0,
          riskScore: 100,
          threatType: 'TAMPERED_CERTIFICATE',
          threatDetails: ["🚨 Cryptographic signature mismatch! This QR certificate has been modified or forged."],
          flags: ["🚨 Tampered / Forged ThreatLens Certificate", certCheck.reason || "Invalid HMAC-SHA256 signature"],
          siteCategory: "⚠️ Forged Certificate",
          siteSummary: "Dangerous: This QR code presents a ThreatLens certificate envelope with an invalid signature. Do NOT trust or scan its destination.",
          recommendations: ["Do not open or trust this QR code", "Report this physical/digital QR code immediately"],
          timestamp: Date.now()
        };
      }
    }

    const parsed = PayloadParser.parse(trimmed);

    // ── 1. UPI / Banking QR Analysis ──────────────────────────────────
    if (parsed.type === 'UPI') {
      const upiResult = UpiPaymentAnalyzer.analyze(trimmed, parsed.actionData);
      const threatDetails = upiResult.flags.map(f => `${f.emoji} ${f.title}: ${f.description}`);

      return {
        rawContent: trimmed,
        isUrl: false,
        scanType: 'UPI',
        originalUrl: trimmed,
        expandedUrl: trimmed,
        domain: null,
        safetyStatus: upiResult.safetyStatus,
        overallScore: upiResult.trustScore,
        riskScore: upiResult.riskScore,
        threatType: upiResult.riskLevel === 'CRITICAL' ? 'UPI_SCAM' : (upiResult.safetyStatus === 'CAUTION' ? 'SUSPICIOUS' : 'SAFE'),
        threatDetails: threatDetails,
        flags: upiResult.flags.map(f => f.title),
        siteCategory: "🏧 UPI Payment",
        siteSummary: upiResult.summary,
        recommendations: upiResult.recommendations,
        upiAnalysis: upiResult,
        timestamp: Date.now()
      };
    }

    // ── 2. WiFi Network QR Analysis ───────────────────────────────────
    if (parsed.type === 'WIFI') {
      const wifiResult = WifiThreatAnalyzer.analyze(parsed.actionData);
      const threatDetails = wifiResult.flags.map(f => `${f.emoji} ${f.title}: ${f.description}`);

      return {
        rawContent: trimmed,
        isUrl: false,
        scanType: 'WIFI',
        originalUrl: trimmed,
        expandedUrl: trimmed,
        domain: null,
        safetyStatus: wifiResult.safetyStatus,
        overallScore: wifiResult.trustScore,
        riskScore: wifiResult.riskScore,
        threatType: wifiResult.isSpoofedSSID ? 'EVIL_TWIN' : (wifiResult.safetyStatus === 'MALICIOUS' ? 'HIGH_RISK_WIFI' : 'SAFE'),
        threatDetails: threatDetails,
        flags: wifiResult.flags.map(f => f.title),
        siteCategory: "📶 WiFi Network",
        siteSummary: wifiResult.summary,
        recommendations: wifiResult.recommendations,
        wifiAnalysis: wifiResult,
        timestamp: Date.now()
      };
    }

    // ── 3. Plain Text / Non-interactive / Static Formats ─────────────────
    const safeTypes = ['TEXT', 'PHONE', 'EMAIL', 'VCARD', 'CRYPTO', 'CREDENTIAL', 'SMS', 'LOCATION', 'TICKET'];
    if (safeTypes.includes(parsed.type)) {
      return this._buildSafeResult(trimmed, parsed.displayLabel, parsed.type);
    }

    // ── 4. Web URL Threat Analysis Pipeline ──────────────────────────
    // Step A: Shortened URL Unrolling
    const expandResult = await UrlExpander.unroll(trimmed);
    const targetUrl = expandResult.expandedUrl;

    let domain = "";
    try {
      domain = new URL(targetUrl).hostname.toLowerCase();
    } catch (e) {
      domain = targetUrl;
    }

    // Step A.5: Fetch Page Content for Accurate Categorization (Skip during fast link scan)
    let pageText = "";
    if (!options.isFastLinkScan) {
      try {
        if (targetUrl.startsWith('http')) {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 2000);
          const res = await fetch(targetUrl, { signal: controller.signal });
          clearTimeout(timeoutId);
          if (res.ok) {
            const html = await res.text();
            const titleMatch = html.match(/<title[^>]*>([^<]+)<\/title>/i);
            const titleText = titleMatch ? titleMatch[1] : "";
            const bodyText = html.substring(0, 10000).replace(/<[^>]+>/g, " ");
            pageText = `${titleText} ${bodyText}`.toLowerCase();
          }
        }
      } catch (e) {
        // Silently continue if site blocks fetch (CORS, offline, timeout)
      }
    }

    // Step B: Fast Local Heuristic Rules
    const heuristicResult = this.heuristics.analyzeUrl(targetUrl);
    let finalRiskScore = heuristicResult.threatScore;
    let flags = [...heuristicResult.flags];
    let threatType = heuristicResult.threatType;

    // Step C: Multi-Signal & AI Categorization (Instant synchronous check for fast link scan, async for deep scan)
    const catResult = options.isFastLinkScan
      ? WebsiteCategorizer.categorize(targetUrl, pageText)
      : await WebsiteCategorizer.categorizeAsync(targetUrl, "", pageText);
    const siteCategory = `${catResult.emoji} ${catResult.label}`;

    if (catResult.threatLevel === WebsiteCategorizer.ThreatLevel.DANGEROUS) {
      finalRiskScore = Math.max(finalRiskScore, 85);
      flags.push(`Website Categorizer: Classified as ${catResult.label} (High Risk)`);
      if (threatType === 'SAFE') threatType = catResult.categoryKey;
    } else if (catResult.threatLevel === WebsiteCategorizer.ThreatLevel.CAUTION) {
      finalRiskScore = Math.max(finalRiskScore, 45);
      flags.push(`Website Categorizer: Age-restricted or cautioned category (${catResult.label})`);
      if (threatType === 'SAFE') threatType = 'HIGH-RISK';
    }

    // Step D: Deep Cloud Analysis (SafeBrowsing, VirusTotal, URLHaus, DNS blocklists)
    if (targetUrl.startsWith('http') && !options.isFastLinkScan) {
      try {
        const cloudResult = await CloudSync.analyzeUrlDeeply(targetUrl, pageText);
        if (cloudResult) {
          finalRiskScore = Math.max(finalRiskScore, cloudResult.score);
          if (cloudResult.flags && cloudResult.flags.length > 0) {
            flags = [...new Set([...flags, ...cloudResult.flags])];
          }
          if (cloudResult.threatType && cloudResult.threatType !== 'SAFE') {
            threatType = cloudResult.threatType;
          }
        }
      } catch (e) {
        console.warn("Cloud deep check skipped / offline fallback:", e);
      }
    }

    // Step D2: Community Threat Intelligence (Live Firestore Query)
    let communityReportsCount = 0;
    let communityReportReasons = [];
    if (targetUrl.startsWith('http')) {
      try {
        const communityData = await CloudSync.getCommunityReports(targetUrl);
        if (communityData && communityData.count > 0) {
          communityReportsCount = communityData.count;
          communityReportReasons = communityData.reasons;
          
          const negativeReports = communityReportReasons.filter(r => !r.startsWith('👍'));
          const positiveReports = communityReportReasons.filter(r => r.startsWith('👍'));

          if (negativeReports.length > 0) {
            finalRiskScore = Math.max(finalRiskScore, Math.min(100, 35 + negativeReports.length * 15));
            flags.push(`👥 Community Flagged: ${negativeReports.length} user report(s) filed (${negativeReports.slice(0, 2).join(', ')})`);
            if (negativeReports.length >= 3 && threatType === 'SAFE') {
              threatType = 'COMMUNITY_REPORTED';
            }
          }
          if (positiveReports.length > 0 && negativeReports.length === 0) {
            finalRiskScore = Math.max(0, finalRiskScore - positiveReports.length * 8);
          }
        }
      } catch (e) {}
    }

    // ── Apply Android 1:1 Strict Category & DPI Caps ──
    let overallTrustScore = Math.max(0, 100 - finalRiskScore);

    if (catResult.threatLevel === WebsiteCategorizer.ThreatLevel.DANGEROUS) {
      if (overallTrustScore > 25) overallTrustScore = 25;
      finalRiskScore = Math.max(finalRiskScore, 75);
      if (threatType === 'SAFE') threatType = catResult.categoryKey || 'DANGEROUS_CATEGORY';
    } else if (catResult.threatLevel === WebsiteCategorizer.ThreatLevel.CAUTION) {
      if (overallTrustScore > 55) overallTrustScore = 55;
      finalRiskScore = Math.max(finalRiskScore, 45);
      if (threatType === 'SAFE') threatType = 'CAUTION_ADVISED';
    }

    // Heuristic flag count penalties (matching Android -15 per flag)
    if (flags.length > 0 && overallTrustScore > 85) {
      overallTrustScore = Math.max(20, 100 - flags.length * 15);
      finalRiskScore = 100 - overallTrustScore;
    }

    // ── Calculate Final SafetyStatus (Matching Android Thresholds: <= 40 MALICIOUS, <= 75 CAUTION) ──
    let safetyStatus = 'SAFE';
    if (overallTrustScore <= 40 || finalRiskScore >= 60 || threatType === 'MALWARE' || threatType === 'PHISHING') {
      safetyStatus = 'MALICIOUS';
    } else if (overallTrustScore <= 75 || finalRiskScore >= 25 || catResult.threatLevel === 'CAUTION' || flags.length > 0) {
      safetyStatus = 'CAUTION';
    }

    // Step E: Generate AI Insight Explanation
    const aiInsight = await this.gemini.getThreatExplanation(targetUrl, finalRiskScore, flags, threatType, catResult.label);

    // Step F: Build Pillar Intelligence Summary matching Android
    const siteSummary = this._buildSiteSummary({
      domain,
      safetyStatus,
      trustScore: overallTrustScore,
      flags,
      catResult,
      aiInsight
    });

    let message = "This link appears secure and verified.";
    if (safetyStatus === 'MALICIOUS') {
      message = `High Risk: ${flags[0] || 'Dangerous threat signatures detected'}`;
    } else if (safetyStatus === 'CAUTION') {
      message = `Caution: ${flags[0] || 'Potential security risks or adult/gambling content'}`;
    }

    return {
      rawContent: trimmed,
      isUrl: true,
      scanType: 'URL',
      originalUrl: trimmed,
      expandedUrl: targetUrl,
      redirectChain: expandResult.redirectChain,
      wasShortened: expandResult.wasShortened,
      domain: domain,
      safetyStatus: safetyStatus,
      overallScore: overallTrustScore, // 100 = Safe, 0 = Dangerous
      riskScore: finalRiskScore,       // 0 = Safe, 100 = Dangerous
      threatType: threatType,
      threatDetails: flags,
      flags: flags,
      siteCategory: siteCategory,
      siteSummary: siteSummary,
      aiInsight: aiInsight,
      communityReportsCount: communityReportsCount,
      communityReportReasons: communityReportReasons,
      message: message,
      timestamp: Date.now()
    };
  }

  _buildSafeResult(content, label, type = 'TEXT') {
    return {
      rawContent: content,
      isUrl: false,
      scanType: type,
      originalUrl: content,
      expandedUrl: content,
      domain: null,
      safetyStatus: 'SAFE',
      overallScore: 100,
      riskScore: 0,
      threatType: 'SAFE',
      threatDetails: [],
      flags: [],
      siteCategory: `📝 Plain ${type}`,
      siteSummary: `Verified safe ${type} content: ${label}`,
      message: "Safe content.",
      timestamp: Date.now()
    };
  }

  _buildSiteSummary({ domain, safetyStatus, trustScore, flags, catResult, aiInsight }) {
    const lines = [
      `🌐 Destination: ${domain || 'Web Target'}`,
      `🏷️ Category: ${catResult.emoji} ${catResult.label}`,
      `🛡️ Security Status: ${safetyStatus} (Trust Score: ${trustScore.toFixed(0)}/100)`,
      ``,
      `🔍 Heuristic & Threat Flags:`,
      flags.length > 0 ? flags.map(f => `• ${f}`).join('\n') : '• Clean across all heuristic inspection filters.',
      ``,
      `✨ AI Analysis:`,
      aiInsight
    ];
    return lines.join('\n');
  }
}
