// UpiPaymentAnalyzer.js
// Port of ThreatLens Android UpiPaymentAnalyzer.kt & UpiTransactionMLEngine.kt

import { Llm7Client } from './Llm7Client.js';

export class UpiPaymentAnalyzer {

  // ══════════════════════════════════════════════════════════════════
  //  KNOWN UPI HANDLE DATABASE (50+ handles mapped to bank names)
  // ══════════════════════════════════════════════════════════════════
  static KNOWN_UPI_HANDLES = {
    // PhonePe
    "ybl": "PhonePe (Yes Bank)",
    "ibl": "PhonePe (ICICI Bank)",
    "axl": "PhonePe (Axis Bank)",
    "sbi": "PhonePe (SBI)",
    "phon": "PhonePe",
    // Google Pay
    "okicici": "Google Pay (ICICI)",
    "okhdfcbank": "Google Pay (HDFC)",
    "okaxis": "Google Pay (Axis)",
    "oksbi": "Google Pay (SBI)",
    "gpay": "Google Pay",
    // Paytm
    "paytm": "Paytm",
    "ptyes": "Paytm (Yes Bank)",
    "pthdfc": "Paytm (HDFC)",
    "ptaxis": "Paytm (Axis)",
    "ptsbi": "Paytm (SBI)",
    // BHIM / Major Banks
    "upi": "BHIM UPI",
    "bhim": "BHIM UPI",
    "barodampay": "Bank of Baroda",
    "unionbankofindia": "Union Bank",
    "unionbank": "Union Bank",
    "cboi": "Central Bank of India",
    "csbpay": "CSB Bank",
    "dbs": "DBS Bank",
    "dlb": "Dhanalakshmi Bank",
    "federal": "Federal Bank",
    "freecharge": "Freecharge",
    "hdfcbank": "HDFC Bank",
    "hsbc": "HSBC Bank",
    "icici": "ICICI Bank",
    "idbi": "IDBI Bank",
    "idfc": "IDFC First Bank",
    "idfcbank": "IDFC First Bank",
    "indianbank": "Indian Bank",
    "indus": "IndusInd Bank",
    "iob": "Indian Overseas Bank",
    "jkb": "J&K Bank",
    "kotak": "Kotak Mahindra Bank",
    "kbl": "Karnataka Bank",
    "kvb": "Karur Vysya Bank",
    "lvb": "Lakshmi Vilas Bank",
    "mahb": "Bank of Maharashtra",
    "pnb": "Punjab National Bank",
    "psb": "Punjab & Sind Bank",
    "rbl": "RBL Bank",
    "sbin": "State Bank of India",
    "sc": "Standard Chartered",
    "scb": "Standard Chartered",
    "syndicate": "Syndicate Bank",
    "tmb": "Tamilnad Mercantile Bank",
    "ubi": "United Bank of India",
    "uboi": "Union Bank of India",
    "uco": "UCO Bank",
    "vijb" : "Vijaya Bank",
    "yesbank": "Yes Bank",
    "cnrb": "Canara Bank",
    "canrabank": "Canara Bank",
    "equitas": "Equitas Small Finance Bank",
    // Wallets & Fintechs
    "apl": "Amazon Pay",
    "rapl": "Amazon Pay",
    "waicici": "WhatsApp Pay (ICICI)",
    "wahdfcbank": "WhatsApp Pay (HDFC)",
    "wasbi": "WhatsApp Pay (SBI)",
    "waaxis": "WhatsApp Pay (Axis)",
    "jupiteraxis": "Jupiter (Axis)",
    "slice": "Slice",
    "niyoicici": "Niyo",
    "cred": "CRED",
    "supermoneyicici": "SuperMoney",
    "mobikwik": "MobiKwik",
    "airtel": "Airtel Payments Bank",
    "airtelmoney": "Airtel Money",
    "jio": "Jio Payments Bank",
    "postbank": "India Post Payments Bank",
    "ippb": "India Post Payments Bank"
  };

  // High-risk scam keywords commonly used in fraudulent UPI QR codes
  static PHISHING_PAYEE_KEYWORDS = [
    "refund", "cashback", "kyc", "support", "customer care", "helpdesk",
    "lottery", "prize", "bonus", "reward", "winner", "survey", "gift",
    "free recharge", "airtel care", "jio care", "paytm care", "phonepe care",
    "gpay support", "customercare", "verification", "urgent", "update"
  ];

  static RECENT_SCAN_TIMESTAMPS = [];

  /**
   * Analyzes UPI parameters and raw string to compute threat flags, ML probability, and risk score.
   */
  static analyze(rawString, parsedFields = {}) {
    const flags = [];
    const recommendations = [];
    let heuristicRisk = 0;

    const pa = (parsedFields.pa || "").trim(); // Payee VPA
    const pn = (parsedFields.pn || "").trim(); // Payee Name
    const am = parsedFields.am ? parseFloat(parsedFields.am) : null; // Amount
    const tn = (parsedFields.tn || "").trim(); // Transaction Note
    const cu = (parsedFields.cu || "INR").trim(); // Currency
    const mc = (parsedFields.mc || "").trim(); // Merchant Code

    // Extract handle
    let handle = null;
    let handleBank = null;
    if (pa.includes('@')) {
      const parts = pa.split('@');
      handle = parts[1].toLowerCase().trim();
      handleBank = this.KNOWN_UPI_HANDLES[handle] || null;
    }

    // 1. Check Missing Payee Address (VPA)
    if (!pa) {
      heuristicRisk += 70;
      flags.push({
        id: "MISSING_VPA",
        severity: "HIGH",
        emoji: "⚠️",
        title: "Missing Payee Address",
        description: "The QR code has no payment address (pa parameter), making it malformed or malicious."
      });
    }

    // 2. Check Unknown / Suspicious Handle
    if (handle && !handleBank) {
      heuristicRisk += 35;
      flags.push({
        id: "UNKNOWN_HANDLE",
        severity: "MEDIUM",
        emoji: "❓",
        title: "Unrecognized Bank Handle",
        description: `@${handle} is not in the registry of recognized Indian bank UPI handles. Proceed with caution.`
      });
      recommendations.push(`Verify the recipient handle @${handle} directly before making payment.`);
    }

    // 3. Check Phishing Keywords in Payee Name or Transaction Note
    const combinedText = `${pn} ${tn}`.toLowerCase();
    const matchedKeywords = this.PHISHING_PAYEE_KEYWORDS.filter(k => combinedText.includes(k));
    if (matchedKeywords.length > 0) {
      heuristicRisk += 55;
      flags.push({
        id: "SCAM_KEYWORD",
        severity: "HIGH",
        emoji: "🚨",
        title: "Suspicious Payee / Note Keywords",
        description: `Contains deceptive scam phrases: "${matchedKeywords.join(', ')}". Fraudsters frequently impersonate customer support or offer fake refunds/lotteries to drain accounts.`
      });
      recommendations.push("Do NOT approve this payment if someone claimed they are sending you money or a refund. Approving a UPI payment always DEDUCTS money from your bank.");
    }

    // 4. Collect Request Scam / Pay Reversal Trick
    if (rawString.toLowerCase().includes("collect") || tn.toLowerCase().includes("receive") || tn.toLowerCase().includes("claim")) {
      heuristicRisk += 50;
      flags.push({
        id: "REVERSAL_SCAM",
        severity: "HIGH",
        emoji: "💸",
        title: "Possible Reverse Payment Scam",
        description: "Transaction context implies receiving money, but scanning a QR code is strictly a payment transfer."
      });
    }

    // 5. Pre-filled High Amount Warning
    if (am !== null && !isNaN(am)) {
      if (am > 10000) {
        heuristicRisk += 25;
        flags.push({
          id: "HIGH_AMOUNT",
          severity: "MEDIUM",
          emoji: "💰",
          title: "Large Preset Amount",
          description: `QR code has a pre-set amount of ₹${am.toLocaleString('en-IN')}. Verify the exact figure before confirming with UPI PIN.`
        });
      } else if (am === 1.0) {
        flags.push({
          id: "MICRO_PROBE",
          severity: "LOW",
          emoji: "🔍",
          title: "Micro-Verification Charge",
          description: "Preset amount of ₹1. Often used by scammers to test account authorization."
        });
      }
    }

    // 6. Rapid-fire scan detection (3+ scans in 5 mins)
    const now = Date.now();
    this.RECENT_SCAN_TIMESTAMPS.push(now);
    // Keep only last 5 minutes
    const fiveMinutesAgo = now - 5 * 60 * 1000;
    while (this.RECENT_SCAN_TIMESTAMPS.length > 0 && this.RECENT_SCAN_TIMESTAMPS[0] < fiveMinutesAgo) {
      this.RECENT_SCAN_TIMESTAMPS.shift();
    }
    if (this.RECENT_SCAN_TIMESTAMPS.length >= 3) {
      flags.push({
        id: "RAPID_FIRE",
        severity: "LOW",
        emoji: "⚡",
        title: "High Scan Frequency",
        description: "Multiple UPI QR codes scanned in rapid succession. Beware of pressure tactics."
      });
    }

    // 7. Simulated ML Fraud Classifier (port of UpiTransactionMLEngine)
    let mlFraudScore = 0.05; // Base low fraud probability
    if (matchedKeywords.length > 0) mlFraudScore += 0.45;
    if (!handleBank) mlFraudScore += 0.25;
    if (am && am > 5000) mlFraudScore += 0.15;
    if (mc && mc.length === 4 && mc !== "0000") mlFraudScore -= 0.10; // Verified merchant code reduces risk
    mlFraudScore = Math.max(0.0, Math.min(1.0, mlFraudScore));

    // Combine Heuristics (60%) + ML (40%)
    const combinedRisk = Math.min(100, Math.max(0, heuristicRisk * 0.6 + (mlFraudScore * 100) * 0.4));
    const trustScore = Math.max(0, Math.min(100, 100 - combinedRisk));

    let safetyStatus = 'SAFE';
    let riskLevel = 'LOW';
    if (combinedRisk >= 60) {
      safetyStatus = 'MALICIOUS';
      riskLevel = 'CRITICAL';
    } else if (combinedRisk >= 25) {
      safetyStatus = 'CAUTION';
      riskLevel = 'MEDIUM';
    }

    if (recommendations.length === 0) {
      recommendations.push("Always verify the payee name shown on your UPI payment app before entering your UPI PIN.");
    }

    const summary = `UPI Payee: ${pn || pa || 'Unknown'} | Provider: ${handleBank || handle || 'Unknown'} | Risk: ${riskLevel} (${combinedRisk.toFixed(0)}%)`;

    return {
      riskScore: combinedRisk,
      trustScore: trustScore,
      safetyStatus: safetyStatus,
      riskLevel: riskLevel,
      flags: flags,
      payeeVerified: handleBank !== null && !matchedKeywords.length,
      vpaHandle: handle,
      handleBankName: handleBank,
      summary: summary,
      recommendations: recommendations,
      mlConfidence: mlFraudScore,
      payeeName: pn || null,
      payeeVpa: pa || null,
      amount: am,
      transactionNote: tn || null,
      currency: cu,
      merchantCode: mc || null
    };
  }

  /**
   * Evaluates ambiguous transaction notes and payee names for social engineering / scams using LLM7.io.
   */
  static async analyzeSemanticScamWithLlm7(pn, tn, am) {
    if (!pn && !tn) return null;
    try {
      const llm7 = new Llm7Client();
      const prompt = `Analyze this UPI payment request for scam / fraud indicators:
Payee Name: "${pn || 'Unknown'}"
Transaction Note: "${tn || 'None'}"
Amount: "${am ? '₹' + am : 'Not specified'}"

Is this payment request likely a fraud/scam (such as lottery claim, fake refund, KYC phishing, or reverse payment trick)?
Answer in valid JSON only with keys:
- isScam: boolean
- confidence: number (0 to 1)
- reason: short explanation (1 sentence)`;

      const response = await llm7.generateResponse(prompt);
      const jsonMatch = response.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        return JSON.parse(jsonMatch[0]);
      }
    } catch (e) {
      console.warn("LLM7 UPI semantic analysis failed:", e);
    }
    return null;
  }
}
