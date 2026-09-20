// GeminiClient.js
// Handles AI-powered threat analysis explanations and summary generation for ThreatLens

export class GeminiClient {
  constructor(apiKey = null) {
    this.apiKey = apiKey;
  }

  /**
   * Generates a detailed threat explanation and safety advice.
   * @param {string} url - Target URL/Payload
   * @param {number} score - Threat/Trust score
   * @param {string[]} flags - Detected threat indicators
   * @param {string} threatType - Threat type (PHISHING, MALWARE, SCAM, etc.)
   * @param {string} category - Category label
   * @returns {Promise<string>}
   */
  async getThreatExplanation(url, score, flags = [], threatType = "UNKNOWN", category = "") {
    // 1. Try Gemini Live API if user configured an API key
    if (this.apiKey && this.apiKey !== "YOUR_API_KEY" && this.apiKey.length > 10) {
      try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${this.apiKey}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [{
              parts: [{
                text: `You are ThreatLens AI Security Engine. Explain in 2 concise sentences why this site is dangerous to a regular web user: URL: ${url}, Threat Type: ${threatType}, Flags: ${flags.join('; ')}, Score: ${score}/100.`
              }]
            }]
          })
        });

        const data = await response.json();
        if (data.candidates && data.candidates[0]?.content?.parts[0]?.text) {
          return data.candidates[0].content.parts[0].text.trim();
        }
      } catch (e) {
        console.warn("Gemini API call failed, falling back to LLM7 / heuristic explainer:", e);
      }
    }

    // 2. Try LLM7.io Fast Engine (Active engine imported from Loanzo)
    try {
      const { Llm7Client } = await import('./Llm7Client.js');
      const llm7 = new Llm7Client();
      const llm7Insight = await llm7.getThreatExplanation(url, score, flags, threatType, category);
      if (llm7Insight && !llm7Insight.includes("matches standard security criteria") && !llm7Insight.includes("Multiple suspicious indicators")) {
        return llm7Insight;
      }
      if (llm7Insight) return llm7Insight;
    } catch (e) {
      console.warn("LLM7 call in GeminiClient failed:", e);
    }

    // 3. Deterministic Neural Heuristic Explainer (Full offline parity with Android)
    return this.generateDeterministicInsight(url, score, flags, threatType, category);
  }

  generateDeterministicInsight(url, score, flags, threatType, category) {
    let domain = "this link";
    try { domain = new URL(url).hostname; } catch (e) {}

    const flagCount = flags.length;
    const flagListStr = flags.slice(0, 2).join(' and ');

    switch (threatType) {
      case 'MALWARE':
        return `ThreatLens detected dangerous executable or ransomware signatures on ${domain}. ${flagListStr || 'Direct download of unauthorized binaries or malicious scripts detected'}. Accessing this resource may compromise your machine.`;

      case 'PHISHING':
        return `ThreatLens flagged ${domain} for suspected credential theft and brand spoofing. ${flagListStr || 'Lookalike domain mimicking trusted login portals'}. Do NOT enter your passwords, PINs, or financial details.`;

      case 'SCAM':
      case 'FINANCIAL_FRAUD':
        return `This destination exhibits characteristics of an online fraud or social engineering campaign. ${flagListStr || 'Promised rewards, prize claims, or fraudulent urgency detected'}.`;

      case 'HIGH-RISK':
      case 'GAMBLING':
      case 'PIRACY':
        return `This destination (${domain}) is classified under ${category || threatType}. It carries elevated risk of drive-by downloads, aggressive ad injection, and unverified third-party content.`;

      default:
        if (score >= 60 || flags.length > 0) {
          return `Multiple suspicious indicators (${flagCount} flags) were identified for ${domain}: ${flagListStr || 'Abnormal structure or unverified origin'}. ThreatLens recommends returning to safety.`;
        }
        return `${domain} matches standard security criteria with no malicious signatures detected across verified threat databases.`;
    }
  }
}
