// WifiThreatAnalyzer.js
// Port of ThreatLens Android WifiThreatAnalyzer.kt

export class WifiThreatAnalyzer {

  // Known public WiFi hotspot names frequently impersonated by Evil Twin attacks
  static KNOWN_PUBLIC_HOTSPOTS = [
    // Coffee & Fast Food
    "starbucks", "starbucks wifi", "starbucks_wifi", "starbuckz",
    "costa coffee", "costa_wifi", "costa wifi",
    "cafe coffee day", "ccd wifi", "ccd_wifi",
    "mcdonalds", "mcdonald's", "mcdonalds wifi", "mcd free wifi",
    "burger king", "burger_king_wifi", "kfc wifi", "kfc_wifi",
    "subway wifi", "dominos wifi",
    // Airports & Transport
    "airport", "airport wifi", "airport_wifi", "airport_free_wifi",
    "free airport wifi", "terminal wifi", "lounge wifi",
    "railwire", "rail_wire", "railtel", "railtel_wifi",
    "metro wifi", "metro_wifi", "station wifi",
    // Hotels & Public
    "hotel", "hotel wifi", "hotel_guest", "guest_wifi", "resort_wifi",
    "oyo wifi", "marriott", "marriott_wifi", "hilton", "hyatt",
    "free wifi", "free_wifi", "free internet", "public wifi", "open wifi",
    // Telecom Carriers
    "jio wifi", "jio_wifi", "jionet", "jio hotspot",
    "airtel wifi", "airtel_wifi", "airtel_hotspot",
    "bsnl wifi", "bsnl_wifi", "vodafone wifi", "vi wifi",
    // Tech & Campus
    "google guest", "google_guest", "googlewifi", "apple store", "eduroam",
    "campus wifi", "university wifi", "library wifi"
  ];

  static COMMON_PASSWORDS = [
    "password", "12345678", "123456789", "1234567890",
    "00000000", "11111111", "88888888", "87654321",
    "qwerty123", "abc12345", "abcd1234", "admin123",
    "password1", "password123", "wifi1234", "internet",
    "welcome1", "letmein", "default", "guest123", "hello123",
    "router123", "netgear1", "dlink123", "tplink123"
  ];

  static HOMOGLYPH_REGEX = /[\u0370-\u03FF\u0400-\u04FF]/;

  /**
   * Analyzes parsed WiFi configuration payload.
   */
  static analyze(actionData = {}) {
    const flags = [];
    const recommendations = [];
    let riskScore = 0;

    const ssid = (actionData.ssid || actionData.S || "").trim();
    const password = actionData.password || actionData.P || null;
    const securityType = (actionData.security || actionData.T || (password ? "WPA2" : "NOPASS")).toUpperCase().trim();
    const isHidden = actionData.hidden === "true" || actionData.hidden === "1" || actionData.H === "true" || actionData.H === "1";
    const hasPassword = Boolean(password && password.length > 0);

    const ssidLower = ssid.toLowerCase();
    const normalizedSsid = ssidLower.replace(/[_\s-]+/g, ' ');

    // 1. Encryption Grade & Protocol Risks
    let encryptionGrade = "A";
    let encryptionName = "WPA2-AES";

    if (securityType === "NOPASS" || securityType === "OPEN" || !hasPassword) {
      encryptionGrade = "F";
      encryptionName = "Open (Unencrypted)";
      riskScore += 45;
      flags.push({
        id: "OPEN_NETWORK",
        severity: "HIGH",
        emoji: "🔓",
        title: "Unencrypted Open Network",
        description: "This network transmits data in plaintext. Any attacker on the same network can intercept sensitive traffic.",
        scorePenalty: 45
      });
      recommendations.push("Do not enter passwords, bank credentials, or sensitive info while connected to an open network without a VPN.");
    } else if (securityType === "WEP") {
      encryptionGrade = "F";
      encryptionName = "WEP (Deprecated & Broken)";
      riskScore += 50;
      flags.push({
        id: "WEP_DEPRECATED",
        severity: "CRITICAL",
        emoji: "🔴",
        title: "Obsolete WEP Encryption",
        description: "WEP encryption is cryptographically broken and can be cracked in under 60 seconds.",
        scorePenalty: 50
      });
      recommendations.push("Avoid connecting to WEP networks; upgrade the router to WPA2-AES or WPA3.");
    } else if (securityType === "WPA") {
      encryptionGrade = "C";
      encryptionName = "WPA (Legacy TKIP)";
      riskScore += 25;
      flags.push({
        id: "WPA_LEGACY",
        severity: "MEDIUM",
        emoji: "⚠️",
        title: "Legacy WPA Security",
        description: "WPA (TKIP) is outdated and vulnerable to handshake capture attacks.",
        scorePenalty: 25
      });
    } else if (securityType === "WPA3" || securityType === "SAE") {
      encryptionGrade = "A+";
      encryptionName = "WPA3-SAE";
    }

    // 2. Evil Twin / Public Hotspot Spoofing Check
    const isKnownPublicHotspot = this.KNOWN_PUBLIC_HOTSPOTS.some(h => {
      const normH = h.replace(/[_\s-]+/g, ' ');
      return normalizedSsid.includes(normH) || ssidLower.includes(h);
    });
    let isSpoofedSSID = false;

    if (isKnownPublicHotspot) {
      if (securityType === "NOPASS" || securityType === "OPEN" || !hasPassword) {
        riskScore += 40;
        isSpoofedSSID = true;
        flags.push({
          id: "EVIL_TWIN_RISK",
          severity: "CRITICAL",
          emoji: "🚨",
          title: "Potential Evil Twin Hotspot",
          description: `"${ssid}" matches a well-known brand/public hotspot name without encryption. Attackers commonly broadcast rogue APs to execute Man-in-the-Middle (MitM) attacks.`,
          scorePenalty: 40
        });
        recommendations.push("Verify with venue staff that this is an official network, and never download files or apps from captive portals.");
      } else {
        flags.push({
          id: "PUBLIC_HOTSPOT_BRAND",
          severity: "LOW",
          emoji: "ℹ️",
          title: "Public Hotspot Brand",
          description: `Network name matches recognized public hotspot "${ssid}".`,
          scorePenalty: 10
        });
      }
    }

    // 3. Homoglyph / Cyrillic Character Impersonation in SSID
    if (this.HOMOGLYPH_REGEX.test(ssid)) {
      riskScore += 50;
      isSpoofedSSID = true;
      flags.push({
        id: "SSID_HOMOGLYPH",
        severity: "CRITICAL",
        emoji: "🎭",
        title: "Homoglyph SSID Spoofing",
        description: "The SSID uses lookalike Cyrillic or Greek characters to visually mimic legitimate network names.",
        scorePenalty: 50
      });
    }

    // 4. Password Strength & Common Password Check
    let passwordStrength = "NONE";
    let passwordScore = 0.0;

    if (hasPassword) {
      const passLower = password.toLowerCase();
      if (this.COMMON_PASSWORDS.includes(passLower)) {
        riskScore += 35;
        passwordStrength = "VERY_WEAK";
        passwordScore = 0.1;
        flags.push({
          id: "TRIVIAL_PASSWORD",
          severity: "HIGH",
          emoji: "🔑",
          title: "Trivially Weak Default Password",
          description: `The password "${password}" is on top common dictionary attack lists.`,
          scorePenalty: 35
        });
      } else if (password.length < 8) {
        riskScore += 25;
        passwordStrength = "WEAK";
        passwordScore = 0.25;
        flags.push({
          id: "SHORT_PASSWORD",
          severity: "MEDIUM",
          emoji: "⚠️",
          title: "Short Password (< 8 characters)",
          description: "WPA2 requires at least 8 characters. Shorter passwords are vulnerable.",
          scorePenalty: 25
        });
      } else {
        // Entropy estimate
        let pool = 0;
        if (/[a-z]/.test(password)) pool += 26;
        if (/[A-Z]/.test(password)) pool += 26;
        if (/[0-9]/.test(password)) pool += 10;
        if (/[^a-zA-Z0-9]/.test(password)) pool += 32;

        const entropy = password.length * Math.log2(Math.max(2, pool));
        if (entropy > 60) {
          passwordStrength = "EXCELLENT";
          passwordScore = 1.0;
        } else if (entropy > 45) {
          passwordStrength = "STRONG";
          passwordScore = 0.8;
        } else {
          passwordStrength = "MODERATE";
          passwordScore = 0.55;
        }
      }
    }

    // 5. Hidden SSID
    if (isHidden) {
      flags.push({
        id: "HIDDEN_SSID",
        severity: "LOW",
        emoji: "👻",
        title: "Hidden Network (Non-Broadcast SSID)",
        description: "Hidden networks cause client devices to constantly broadcast probe requests, leaking location history.",
        scorePenalty: 5
      });
    }

    riskScore = Math.min(100, Math.max(0, riskScore));
    const trustScore = Math.max(0, 100 - riskScore);

    let safetyStatus = 'SAFE';
    let riskLevel = 'LOW';
    if (riskScore >= 60) {
      safetyStatus = 'MALICIOUS';
      riskLevel = 'CRITICAL';
    } else if (riskScore >= 25) {
      safetyStatus = 'CAUTION';
      riskLevel = 'MEDIUM';
    }

    if (recommendations.length === 0) {
      recommendations.push("Ensure your device's firewall is enabled and turn off local file sharing on unfamiliar networks.");
    }

    const summary = `WiFi Network: "${ssid || 'Hidden'}" | Encryption: ${encryptionName} (${encryptionGrade}) | Risk: ${riskLevel} (${riskScore.toFixed(0)}%)`;

    return {
      riskScore: riskScore,
      trustScore: trustScore,
      safetyStatus: safetyStatus,
      riskLevel: riskLevel,
      flags: flags,
      encryptionGrade: encryptionGrade,
      encryptionName: encryptionName,
      passwordStrength: passwordStrength,
      passwordScore: passwordScore,
      isSpoofedSSID: isSpoofedSSID,
      summary: summary,
      recommendations: recommendations,
      ssid: ssid || null,
      securityType: securityType,
      isHidden: isHidden,
      hasPassword: hasPassword,
      password: password
    };
  }
}
