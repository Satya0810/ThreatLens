// ThreatLens Bundled Service Worker
// Self-contained to comply with ServiceWorkerGlobalScope specification
try {
  importScripts('/lib/jsQR.js');
} catch (e) {}

// --- UrlExpander.js ---
// UrlExpander.js
// Port of ThreatLens Android UrlExpander.kt

class UrlExpander {

  static SHORT_URL_DOMAINS = new Set([
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly",
    "is.gd", "buff.ly", "adf.ly", "shorte.st", "cutt.ly",
    "rebrand.ly", "tiny.cc", "bc.vc", "rb.gy", "v.gd"
  ]);

  /**
   * Checks if a URL domain is a known URL shortener.
   */
  static isShortened(urlString) {
    try {
      const hostname = new URL(urlString).hostname.toLowerCase();
      return this.SHORT_URL_DOMAINS.has(hostname) || Array.from(this.SHORT_URL_DOMAINS).some(d => hostname.endsWith("." + d));
    } catch (e) {
      return false;
    }
  }

  /**
   * Unwraps known search engine and social media redirect URLs
   * (e.g. Bing /ck/a?u=a1<base64>, Google /url?q=..., DuckDuckGo /l/?uddg=...)
   */
  static unwrapSearchEngineRedirect(urlString) {
    try {
      const parsed = new URL(urlString);
      const hostname = parsed.hostname.toLowerCase();

      // 1. Bing Redirects (u=a1<base64>)
      if (hostname.includes('bing.com') && parsed.pathname.includes('/ck/a')) {
        const uParam = parsed.searchParams.get('u');
        if (uParam) {
          let b64 = uParam;
          if (b64.startsWith('a1')) b64 = b64.substring(2);
          try {
            while (b64.length % 4 !== 0) b64 += '=';
            const decoded = atob(b64.replace(/-/g, '+').replace(/_/g, '/'));
            if (decoded.startsWith('http://') || decoded.startsWith('https://')) {
              return decoded;
            }
          } catch (e) {}
        }
      }

      // 2. Google Redirects (q=... or url=...)
      if (hostname.includes('google.') && parsed.pathname === '/url') {
        const q = parsed.searchParams.get('q') || parsed.searchParams.get('url');
        if (q && (q.startsWith('http://') || q.startsWith('https://'))) {
          return q;
        }
      }

      // 3. DuckDuckGo Redirects (uddg=...)
      if (hostname.includes('duckduckgo.com') && parsed.searchParams.get('uddg')) {
        const uddg = decodeURIComponent(parsed.searchParams.get('uddg'));
        if (uddg.startsWith('http://') || uddg.startsWith('https://')) {
          return uddg;
        }
      }

      // 4. Yahoo Redirects (/RU=.../)
      if (hostname.includes('yahoo.com') && parsed.pathname.includes('/RU=')) {
        const match = parsed.pathname.match(/\/RU=([^/]+)/);
        if (match && match[1]) {
          const decoded = decodeURIComponent(match[1]);
          if (decoded.startsWith('http://') || decoded.startsWith('https://')) {
            return decoded;
          }
        }
      }

      // 5. Facebook Redirects (u=...)
      if (hostname.includes('facebook.com') && parsed.pathname.includes('/l.php')) {
        const u = parsed.searchParams.get('u');
        if (u && (u.startsWith('http://') || u.startsWith('https://'))) {
          return decodeURIComponent(u);
        }
      }

      // 6. Generic redirect query params
      for (const param of ['url', 'dest', 'target', 'destination', 'redirect', 'link', 'to', 'out']) {
        const val = parsed.searchParams.get(param);
        if (val && (val.startsWith('http://') || val.startsWith('https://'))) {
          return decodeURIComponent(val);
        }
      }
    } catch (e) {}

    return urlString;
  }

  /**
   * Unrolls a URL through HTTP redirect chains (up to maxHops).
   * @param {string} urlString 
   * @param {number} maxHops 
   * @returns {Promise<{ originalUrl: string, expandedUrl: string, redirectChain: string[], wasShortened: boolean }>}
   */
  static async unroll(urlString, maxHops = 5) {
    const unwrapped = this.unwrapSearchEngineRedirect(urlString);
    const chain = [unwrapped];
    let currentUrl = unwrapped;
    const wasShortened = this.isShortened(unwrapped);

    // If not a URL or not HTTP, return immediately
    if (!unwrapped.startsWith("http://") && !unwrapped.startsWith("https://")) {
      return { originalUrl: urlString, expandedUrl: unwrapped, redirectChain: chain, wasShortened: false };
    }

    try {
      // In browser extension environment, fetch with redirect:'follow' or checking final response.url
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3000); // 3s timeout

      const response = await fetch(currentUrl, {
        method: 'HEAD',
        signal: controller.signal,
        redirect: 'follow',
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ThreatLens/1.0'
        }
      });
      clearTimeout(timeoutId);

      if (response && response.url && response.url !== currentUrl) {
        chain.push(response.url);
        currentUrl = response.url;
      }
    } catch (e) {
      // Fetch HEAD may fail due to CORS or network; fallback to original URL
    }

    return {
      originalUrl: urlString,
      expandedUrl: currentUrl,
      redirectChain: chain,
      wasShortened: wasShortened
    };
  }
}


// --- PayloadParser.js ---
// PayloadParser.js
// Port of ThreatLens Android QrDataParser.kt

class PayloadParser {
  
  static parse(rawString) {
    if (!rawString || typeof rawString !== 'string') {
      return { type: 'UNKNOWN', rawContent: rawString || '', isInteractive: false };
    }

    const str = rawString.trim();
    const lower = str.toLowerCase();

    // 0. ThreatLens Certified QR Envelope
    if (lower.startsWith('threatlenscert://')) {
      try {
        const base64 = str.substring('threatlenscert://'.length);
        const jsonStr = atob(base64);
        const certData = JSON.parse(jsonStr);
        
        // Recursively parse the inner true payload!
        const innerParsed = this.parse(certData.content);
        
        // Add certification metadata
        innerParsed.isCertified = true; 
        innerParsed.certScore = certData.score;
        innerParsed.certStatus = certData.status;
        innerParsed.certId = certData.id;
        innerParsed.isTampered = false; 
        
        return innerParsed;
      } catch (e) {
        // Fallthrough if parsing fails
      }
    }

    // 0.5. Password-Protected QR
    if (lower.startsWith('threatlenslock://')) {
      return {
        type: 'LOCKED',
        rawContent: str,
        isInteractive: true,
        actionData: { locked: true },
        displayLabel: '🔒 Password-Protected QR'
      };
    }

    // 1. UPI Payment
    if (lower.startsWith('upi://') || lower.startsWith('paytm://') || lower.startsWith('phonepe://') || lower.startsWith('gpay://') || lower.startsWith('bhim://')) {
      const upiData = this._parseUpi(str);
      return {
        type: 'UPI',
        rawContent: str,
        isInteractive: true,
        actionData: upiData,
        displayLabel: `UPI Payment: ${upiData.pn || upiData.pa || 'Direct Pay'}`
      };
    }

    // 2. Wi-Fi Config (e.g. WIFI:S:MyNetwork;T:WPA;P:Secret;H:false;;)
    if (str.startsWith('WIFI:') || str.startsWith('wifi:')) {
      const wifiData = this._parseWifi(str);
      return {
        type: 'WIFI',
        rawContent: str,
        isInteractive: true,
        actionData: wifiData,
        displayLabel: `WiFi Network: ${wifiData.ssid || 'Hidden'}`
      };
    }

    // 3. W3C Verifiable Credential
    if (lower.startsWith('threatlensvc://') || (str.startsWith('{') && str.includes('verifiableCredential'))) {
      return {
        type: 'CREDENTIAL',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Verifiable Digital Credential'
      };
    }

    // 4. Crypto Addresses
    if (lower.startsWith('bitcoin:') || lower.startsWith('ethereum:') || lower.startsWith('solana:') || /^0x[a-fA-F0-9]{40}$/.test(str)) {
      return {
        type: 'CRYPTO',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Cryptocurrency Wallet Address'
      };
    }

    // 5. Contact VCard / MeCard
    if (lower.startsWith('begin:vcard') || lower.startsWith('mecard:')) {
      return {
        type: 'VCARD',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Contact Card (vCard)'
      };
    }

    // 6. Direct Web URLs
    if (lower.startsWith('http://') || lower.startsWith('https://') || lower.startsWith('www.')) {
      const normalized = lower.startsWith('www.') ? `https://${str}` : str;
      return {
        type: 'URL',
        rawContent: normalized,
        isInteractive: true,
        displayLabel: normalized
      };
    }

    // Check if valid URL without explicit scheme
    try {
      const parsedUrl = new URL(str);
      if (parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:') {
        return {
          type: 'URL',
          rawContent: str,
          isInteractive: true,
          displayLabel: str
        };
      }
    } catch (e) {}

    // 7. Email, Phone, SMS
    if (lower.startsWith('mailto:') || (str.includes('@') && !str.includes(' ') && !str.includes('/'))) {
      const email = lower.startsWith('mailto:') ? str.substring(7) : str;
      return {
        type: 'EMAIL',
        rawContent: `mailto:${email}`,
        isInteractive: true,
        displayLabel: `Email: ${email}`
      };
    }

    if (lower.startsWith('tel:') || /^[+]?[0-9]{7,15}$/.test(str.replace(/[\s-]/g, ''))) {
      const phone = str.replace(/[^0-9+]/g, '');
      return {
        type: 'PHONE',
        rawContent: `tel:${phone}`,
        isInteractive: true,
        displayLabel: `Phone: ${phone}`
      };
    }

    // 8. SMS Messages
    if (lower.startsWith('smsto:') || lower.startsWith('sms:')) {
      const isSmsto = lower.startsWith('smsto:');
      const parts = str.substring(isSmsto ? 6 : 4).split(':');
      const phone = parts[0] || '';
      const body = parts[1] || '';
      return {
        type: 'SMS',
        rawContent: str,
        isInteractive: true,
        actionData: { phone, body },
        displayLabel: `SMS to ${phone}${body ? ': ' + body : ''}`
      };
    }

    // 9. Geo Location Coordinates
    if (lower.startsWith('geo:')) {
      const coords = str.substring(4).split('?')[0];
      return {
        type: 'LOCATION',
        rawContent: str,
        isInteractive: true,
        actionData: { geo: coords },
        displayLabel: `Location: ${coords}`
      };
    }

    // 10. Event Gatekeeper Ticket
    if (lower.startsWith('threatlens://ticket')) {
      return {
        type: 'TICKET',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'ThreatLens Verified Event Ticket'
      };
    }

    // Default: Plain Text
    return {
      type: 'TEXT',
      rawContent: str,
      isInteractive: false,
      displayLabel: str.length > 50 ? str.substring(0, 47) + '...' : str
    };
  }

  static _parseUpi(str) {
    let query = '';
    if (str.includes('?')) {
      query = str.substring(str.indexOf('?') + 1);
    }
    const params = new URLSearchParams(query);
    return {
      pa: params.get('pa') || '',
      pn: params.get('pn') || '',
      am: params.get('am') || null,
      cu: params.get('cu') || 'INR',
      tn: params.get('tn') || '',
      mc: params.get('mc') || ''
    };
  }

  static _parseWifi(str) {
    const getField = (prefix) => {
      const match = str.match(new RegExp(`${prefix}:(.*?)(?=;|$)`));
      return match ? match[1] : '';
    };

    return {
      ssid: getField('S'),
      security: getField('T') || 'WPA',
      password: getField('P'),
      hidden: getField('H')
    };
  }
}


// --- HeuristicsEngine.js ---
// HeuristicsEngine.js
// Port of ThreatLens Android HeuristicChecker.kt & ThreatAnalyzer.kt heuristics

class HeuristicsEngine {
  constructor() {
    this.suspiciousTLDs = new Set([
      '.xyz', '.top', '.pw', '.cc', '.club', '.tk', '.ml',
      '.ga', '.cf', '.gq', '.buzz', '.gripe', '.ist', '.wtf', '.lol'
    ]);

    this.trustedBrands = [
      'paypal', 'google', 'microsoft', 'apple', 'amazon',
      'netflix', 'facebook', 'instagram', 'chase', 'bankofamerica',
      'wellsfargo', 'binance', 'coinbase', 'paytm', 'phonepe', 'flipkart'
    ];

    this.scamKeywords = [
      'tech-support', 'crypto-doubler', 'double-your-btc', 'free-giveaway',
      'lottery-winner', 'account-suspended', 'urgent-action', 'claim-prize',
      'verify-account', 'update-billing', 'security-alert', 'unauthorized-access'
    ];

    this.highRiskKeywords = ['1xbet', 'casino', 'betting', 'gamble', 'free-spins', 'poker', 'slots'];
    this.piracyKeywords = [
      'torrent', 'crack', 'keygen', 'warez', 'nulled', 'vegamovie', 'vegamovies', 'vegamoviez',
      'tamilrockers', '1337x', 'fmovies', '123movies', 'mp4moviez', 'filmyzilla', 'movierulz',
      'bolly4u', 'worldfree4u', '9xmovie', 'downloadhub', 'skymovieshd', 'filmywap', 'katmovie'
    ];

    this.piracyDomains = new Set([
      "vegamovies.gripe", "vegamovies.nl", "vegamovies.to", "vegamovies.ist", "vegamoviez.com",
      "tamilrockers.ws", "tamilrockers.wc", "tamilrockers.com",
      "1337x.to", "1337x.st", "1337x.gd", "rarbg.to", "rarbg.me",
      "yts.mx", "yts.am", "yts.lt", "piratebay.org", "thepiratebay.org", "thepiratebay.se",
      "kickass.to", "kickasstorrents.to", "katcr.to", "limetorrents.cc", "limetorrents.info",
      "torrentz2.eu", "torrentz2.me", "nyaa.si", "nyaa.net",
      "fmovies.to", "fmovies.wtf", "fmovies.ps", "123movies.to", "123movies.net",
      "putlocker.to", "putlockers.fm", "gomovies.to", "gostream.is", "soap2day.to", "soap2day.ac",
      "hdmovie2.me", "hdmovie2.ws", "movierulz.com", "movierulz.pe", "movierulz.gs",
      "filmyzilla.com", "filmyzilla.in", "mp4moviez.com", "mp4moviez.in",
      "bolly4u.org", "bolly4u.cc", "worldfree4u.com", "worldfree4u.lol",
      "9xmovies.in", "9xmovies.com", "kuttymovies.com", "isaimini.com",
      "tamilyogi.com", "tamilyogi.cc", "ssrmovies.club", "extramovies.com",
      "downloadhub.in", "downloadhub.ws", "skymovieshd.com", "skymovieshd.in",
      "afilmywap.com", "filmywap.com", "pagalmovies.com", "cinevood.com",
      "katmoviehd.com", "katmoviehd.se"
    ]);

    this.piracyDomainKeywords = [
      "torrent", "movie", "moviez", "pirate", "warez", "crack", "nulled", "fmovie",
      "putlocker", "123movie", "gomovie", "soap2day", "movierulz", "filmyzilla",
      "mp4moviez", "hdmovie", "bolly4u", "9xmovie", "kuttymovie", "tamilyogi",
      "isaimini", "downloadhub", "skymovieshd", "filmywap", "pagalmovie", "cinevood",
      "katmovie", "vegamovie", "tamilrocker", "yts", "rarbg", "kickass", "limetorrent"
    ];

    this.piracyTLDs = new Set([
      ".gripe", ".ist", ".wc", ".gd", ".wtf", ".ps", ".ac", ".lol", ".to", ".is"
    ]);

    this.adultDomains = new Set([
      "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "redtube.com",
      "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "chaturbate.com",
      "bongacams.com", "stripchat.com", "livejasmin.com", "onlyfans.com", "fansly.com"
    ]);

    this.adultDomainKeywords = [
      "porn", "xxx", "sex", "nude", "cam", "fuck", "milf", "hentai", "mature", "nsfw"
    ];

    this.malwareExtensions = ['.exe', '.apk', '.scr', '.bat', '.cmd', '.vbs', '.jar', '.ps1', '.sh', '.msi', '.pif'];

    this.homographRegex = /[\u0370-\u03FF\u0400-\u04FF]/;
    this.ipRegex = /^(\d{1,3}\.){3}\d{1,3}$/;
    this.privateIpRegex = /^(127\.\d{1,3}\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|0\.0\.0\.0|localhost)$/;
    this.base64Regex = /[A-Za-z0-9+/]{40,}={0,2}/;
  }

  /**
   * Sanitizes zero-width and invisible unicode characters.
   */
  sanitizeUrl(urlString) {
    return urlString
      .replace(/[\u200B-\u200D\uFEFF]/g, '')
      .trim();
  }

  /**
   * Calculates Levenshtein edit distance between two strings.
   */
  levenshtein(s1, s2) {
    const dp = Array.from({ length: s1.length + 1 }, () => Array(s2.length + 1).fill(0));
    for (let i = 0; i <= s1.length; i++) dp[i][0] = i;
    for (let j = 0; j <= s2.length; j++) dp[0][j] = j;

    for (let i = 1; i <= s1.length; i++) {
      for (let j = 1; j <= s2.length; j++) {
        const cost = s1[i - 1] === s2[j - 1] ? 0 : 1;
        dp[i][j] = Math.min(
          dp[i - 1][j] + 1,      // deletion
          dp[i][j - 1] + 1,      // insertion
          dp[i - 1][j - 1] + cost // substitution
        );
      }
    }
    return dp[s1.length][s2.length];
  }

  /**
   * Performs in-depth heuristic security analysis on a URL.
   * Returns threat score (0 = Safe, 100 = Dangerous) and list of flags.
   */
  analyzeUrl(rawUrlString) {
    const flags = [];
    let threatScore = 0;
    let threatType = 'SAFE';

    // 0. Sanitize zero-width characters
    const urlString = this.sanitizeUrl(rawUrlString);
    if (urlString !== rawUrlString) {
      threatScore += 40;
      flags.push("Zero-width hidden unicode characters detected (Obfuscation evasion)");
      threatType = 'SUSPICIOUS';
    }

    const lowerUrl = urlString.toLowerCase();

    // 1. Check dangerous schemes
    if (lowerUrl.startsWith('data:') || lowerUrl.startsWith('javascript:')) {
      return {
        threatScore: 100,
        trustScore: 0,
        flags: ["Dangerous URL scheme (data: or javascript: execution vector)"],
        threatType: 'MALWARE'
      };
    }

    try {
      const url = new URL(urlString.startsWith('http') ? urlString : `https://${urlString}`);
      const hostname = url.hostname.toLowerCase();
      const parts = hostname.split('.');

      // 2. SSRF / Bogon / Private IP Address Check
      if (this.privateIpRegex.test(hostname)) {
        return {
          threatScore: 95,
          trustScore: 5,
          flags: ["Bogon / Private Local IP address detected (SSRF / Intranet target)"],
          threatType: 'MALWARE'
        };
      }

      // IP address in hostname (Public IP)
      if (this.ipRegex.test(hostname)) {
        threatScore += 65;
        flags.push("Public IP address used directly instead of verified domain name");
        if (threatType === 'SAFE') threatType = 'PHISHING';
      }

      // 3. Homograph / Cyrillic Punycode Attack
      if (this.homographRegex.test(hostname) || hostname.startsWith('xn--')) {
        threatScore += 80;
        flags.push("Homograph/Punycode character attack detected (Lookalike characters)");
        threatType = 'PHISHING';
      }

      // 4. Authority Phishing Trick (@ in authority)
      const authority = url.username || (urlString.split('/')[2] && urlString.split('/')[2].includes('@'));
      if (authority && !lowerUrl.startsWith('mailto:')) {
        threatScore += 80;
        flags.push("Contains '@' in authority section (Classic credential harvesting trick)");
        threatType = 'PHISHING';
      }

      // 5. Malware Executable Extension
      for (const ext of this.malwareExtensions) {
        if (url.pathname.toLowerCase().endsWith(ext)) {
          threatScore += 90;
          flags.push(`Direct download of high-risk executable file (${ext})`);
          threatType = 'MALWARE';
        }
      }

      // 6. Base64 payload in URL parameters
      if (this.base64Regex.test(url.search)) {
        threatScore += 35;
        flags.push("Large Base64 encoded payload detected in URL query parameters");
      }

      // 7. Typosquatting / Brand Impersonation (Exact substring and Levenshtein distance)
      const domainWithoutTld = parts.slice(0, -1).join('.');
      const officialSuffixes = ['.com', '.org', '.net', '.in', '.co.in', '.co.uk', '.io', '.app', '.dev', '.ai', '.ca', '.de', '.fr', '.jp', '.au', '.gov', '.edu'];

      for (const brand of this.trustedBrands) {
        const brandNoSpace = brand.replace(/[\s-]/g, '');
        // Check substring inside domain
        if (domainWithoutTld.includes(brandNoSpace)) {
          const isOfficial = officialSuffixes.some(s => hostname === `${brand}${s}` || hostname.endsWith(`.${brand}${s}`));
          if (!isOfficial) {
            threatScore += 75;
            flags.push(`Possible brand impersonation of '${brand}' in domain '${hostname}'`);
            threatType = 'PHISHING';
          }
        } else {
          // Check Levenshtein distance for typosquatting (e.g. paypa1, g00gle)
          for (const part of parts) {
            const dist = this.levenshtein(part, brandNoSpace);
            if (dist === 1 || (dist === 2 && brandNoSpace.length >= 6)) {
              threatScore += 70;
              flags.push(`Suspected typosquatting attack mimicking '${brand}' (Domain part: '${part}')`);
              threatType = 'PHISHING';
            }
          }
        }
      }

      // 8. Scam & Fraud Keywords
      for (const keyword of this.scamKeywords) {
        if (lowerUrl.includes(keyword)) {
          threatScore += 70;
          flags.push(`Scam/Social engineering keyword detected: '${keyword}'`);
          if (threatType === 'SAFE' || threatType === 'SUSPICIOUS') threatType = 'SCAM';
        }
      }

      // 8.5 Known Piracy Domain & Keyword Check (1:1 Android ThreatAnalyzer)
      const isPiracyDomain = this.piracyDomains.has(hostname) || Array.from(this.piracyDomains).some(d => hostname === d || hostname.endsWith('.' + d));
      const hasPiracyDomainKeyword = this.piracyDomainKeywords.some(k => hostname.includes(k));
      if (isPiracyDomain) {
        threatScore += 85;
        flags.push(`Known illegal piracy or copyright infringement domain (${hostname})`);
        threatType = 'PIRACY';
      } else if (hasPiracyDomainKeyword) {
        threatScore += 75;
        flags.push(`Domain contains piracy or illegal streaming keywords ('${hostname}')`);
        threatType = 'PIRACY';
      }

      // 8.6 Adult Content Domain & Keyword Check (1:1 Android ThreatAnalyzer)
      const isAdultDomain = this.adultDomains.has(hostname) || Array.from(this.adultDomains).some(d => hostname === d || hostname.endsWith('.' + d));
      const hasAdultDomainKeyword = this.adultDomainKeywords.some(k => hostname.includes(k));
      if (isAdultDomain) {
        threatScore += 80;
        flags.push(`Known explicit adult / pornography domain (${hostname})`);
        threatType = 'PORNOGRAPHY';
      } else if (hasAdultDomainKeyword) {
        threatScore += 70;
        flags.push(`Domain contains explicit adult keywords ('${hostname}')`);
        threatType = 'PORNOGRAPHY';
      }

      // 9. Piracy & Illegal Streaming Keywords in URL
      for (const keyword of this.piracyKeywords) {
        if (hostname.includes(keyword) || lowerUrl.includes(keyword)) {
          threatScore += 50;
          flags.push(`Piracy / unauthorized content keyword detected: '${keyword}'`);
          if (threatType === 'SAFE') threatType = 'PIRACY';
          break;
        }
      }

      // 10. High-Risk Gambling / Betting
      for (const keyword of this.highRiskKeywords) {
        if (hostname.includes(keyword)) {
          threatScore += 45;
          flags.push(`Gambling / high-risk keyword detected: '${keyword}'`);
          if (threatType === 'SAFE') threatType = 'HIGH-RISK';
          break;
        }
      }

      // 11. Suspicious TLD
      const tld = '.' + (parts[parts.length - 1] || '');
      if (this.suspiciousTLDs.has(tld)) {
        threatScore += 30;
        flags.push(`Suspicious or high-abuse Top Level Domain (${tld})`);
      }
      if (this.piracyTLDs.has(tld)) {
        threatScore += 40;
        flags.push(`High-risk piracy Top Level Domain (${tld})`);
      }

      // 12. Excessive Subdomains
      if (parts.length > 3 && !hostname.startsWith('www.')) {
        threatScore += 20 + (parts.length - 3) * 5;
        flags.push(`Excessive subdomains (${parts.length} parts, possible evasion technique)`);
      }

      // 13. Non-Standard Port
      if (url.port && url.port !== '80' && url.port !== '443') {
        threatScore += 25;
        flags.push(`Non-standard HTTP port used (:${url.port})`);
      }

      // 14. Abnormally Long URL
      if (urlString.length > 200) {
        threatScore += 15;
        flags.push(`Abnormally long URL structure (${urlString.length} characters)`);
      }

    } catch (e) {
      threatScore = 100;
      flags.push("Malformed or unparseable URL structure");
      threatType = 'UNKNOWN_THREAT';
    }

    threatScore = Math.min(100, Math.max(0, threatScore));
    const trustScore = Math.max(0, 100 - threatScore);

    if (threatScore >= 40 && threatType === 'SAFE') {
      threatType = 'SUSPICIOUS';
    }

    return {
      threatScore,
      trustScore,
      flags,
      threatType
    };
  }
}


// --- CloudDatasetManager.js ---
// CloudDatasetManager.js
// Handles dynamic syncing of Global Domain Overrides and Injected Category Keywords from Firebase

class CloudDatasetManager {
  static globalOverrides = {};
  static keywordSignals = {};

  static async fetchAndCacheAll() {
    try {
      const endpoint = `https://firestore.googleapis.com/v1/projects/threatlens-4065e/databases/(default)/documents/app_config/datasets`;
      const response = await fetch(endpoint);
      if (!response.ok) return;

      const doc = await response.json();
      if (doc.fields) {
        // Extract websiteCategorizerData
        const catData = doc.fields.websiteCategorizerData;
        if (catData && catData.mapValue && catData.mapValue.fields) {
          const fields = catData.mapValue.fields;
          
          // 1. Parse Global Domain Overrides
          if (fields.globalOverrides && fields.globalOverrides.mapValue && fields.globalOverrides.mapValue.fields) {
            const overrides = {};
            const raw = fields.globalOverrides.mapValue.fields;
            for (const domain in raw) {
              if (raw[domain].stringValue) {
                overrides[domain.toLowerCase().trim()] = raw[domain].stringValue;
              }
            }
            this.globalOverrides = overrides;
          }

          // 2. Parse Injected Category Keywords
          if (fields.keywordSignals && fields.keywordSignals.mapValue && fields.keywordSignals.mapValue.fields) {
            const signals = {};
            const raw = fields.keywordSignals.mapValue.fields;
            for (const cat in raw) {
              if (raw[cat].arrayValue && raw[cat].arrayValue.values) {
                signals[cat] = raw[cat].arrayValue.values.map(v => v.stringValue).filter(Boolean);
              }
            }
            this.keywordSignals = signals;
          }
        }
        
        await chrome.storage.local.set({
          'cloud_global_overrides': this.globalOverrides,
          'cloud_keyword_signals': this.keywordSignals
        });
      }
    } catch (e) {
      console.warn("Cloud datasets fetch fallback to cache:", e);
    }
  }

  static async loadFromCache() {
    return new Promise(resolve => {
      chrome.storage.local.get(['cloud_global_overrides', 'cloud_keyword_signals'], (res) => {
        if (res.cloud_global_overrides) this.globalOverrides = res.cloud_global_overrides;
        if (res.cloud_keyword_signals) this.keywordSignals = res.cloud_keyword_signals;
        resolve();
      });
    });
  }
}

// Initialize and sync Cloud Datasets
CloudDatasetManager.loadFromCache().then(() => CloudDatasetManager.fetchAndCacheAll());

// --- WebsiteCategorizer.js ---
// WebsiteCategorizer.js
// Port of ThreatLens Android WebsiteCategorizer.kt

class WebsiteCategorizer {

  static ThreatLevel = {
    SAFE: "SAFE",
    CAUTION: "CAUTION",
    DANGEROUS: "DANGEROUS"
  };

  // ═══════════════════════════════════════════════════════════════════════════
  //  SITE CATEGORIES DEFINITIONS (Label, Emoji, ThreatLevel)
  // ═══════════════════════════════════════════════════════════════════════════
  static CATEGORIES = {
    // Dangerous
    MALWARE: { emoji: "🔴", label: "Malware", threatLevel: "DANGEROUS" },
    PHISHING: { emoji: "🔴", label: "Phishing & Credential Harvesting", threatLevel: "DANGEROUS" },
    FINANCIAL_FRAUD: { emoji: "🔴", label: "Financial Fraud", threatLevel: "DANGEROUS" },
    TECH_SUPPORT_SCAMS: { emoji: "🔴", label: "Tech Support Scam", threatLevel: "DANGEROUS" },
    RANSOMWARE: { emoji: "🔴", label: "Ransomware Distribution", threatLevel: "DANGEROUS" },
    IMPERSONATION_SITES: { emoji: "🔴", label: "Brand Impersonation", threatLevel: "DANGEROUS" },

    // Caution
    PORNOGRAPHY: { emoji: "⚠️", label: "Pornography & Adult Content", threatLevel: "CAUTION" },
    GAMBLING: { emoji: "⚠️", label: "Betting & Casino", threatLevel: "CAUTION" },
    PIRACY: { emoji: "⚠️", label: "Piracy & Torrenting", threatLevel: "CAUTION" },
    SUSPICIOUS_UNVERIFIED: { emoji: "⚠️", label: "Suspicious Unverified Domain", threatLevel: "CAUTION" },

    // Safe
    SEARCH_ENGINE: { emoji: "🔍", label: "Search Engine", threatLevel: "SAFE" },
    AI_ML_PLATFORMS: { emoji: "🤖", label: "AI & ML Platform", threatLevel: "SAFE" },
    DEVELOPER_TOOLS: { emoji: "💻", label: "Developer & Code Tools", threatLevel: "SAFE" },
    CLOUD_SERVICES: { emoji: "☁️", label: "Cloud Services & Infrastructure", threatLevel: "SAFE" },
    CYBERSECURITY: { emoji: "🛡️", label: "Cybersecurity & Safety", threatLevel: "SAFE" },
    BANKING: { emoji: "🏦", label: "Banking & Financial Services", threatLevel: "SAFE" },
    ECOMMERCE: { emoji: "🛒", label: "E-Commerce & Retail", threatLevel: "SAFE" },
    SOCIAL_MEDIA: { emoji: "👥", label: "Social Networking", threatLevel: "SAFE" },
    MESSAGING: { emoji: "💬", label: "Messaging & Communication", threatLevel: "SAFE" },
    NEWS_MEDIA: { emoji: "📰", label: "News & Journalism", threatLevel: "SAFE" },
    MOVIE_STREAMING: { emoji: "🎬", label: "Video Streaming", threatLevel: "SAFE" },
    MUSIC_STREAMING: { emoji: "🎵", label: "Music & Audio", threatLevel: "SAFE" },
    GAMING: { emoji: "🎮", label: "Gaming Platform", threatLevel: "SAFE" },
    EDUCATION: { emoji: "🎓", label: "Education & Academia", threatLevel: "SAFE" },
    GOVERNMENT: { emoji: "🏛️", label: "Government & Civic", threatLevel: "SAFE" },
    HEALTHCARE: { emoji: "🏥", label: "Healthcare & Medicine", threatLevel: "SAFE" },
    BUSINESS: { emoji: "🏢", label: "Business & Enterprise", threatLevel: "SAFE" },
    GENERAL_SAFE: { emoji: "🌐", label: "General Safe Website", threatLevel: "SAFE" }
  };

  // ═══════════════════════════════════════════════════════════════════════════
  //  KNOWN DOMAIN DATABASE (~250+ Popular Domains)
  // ═══════════════════════════════════════════════════════════════════════════
  static KNOWN_DOMAINS = {
    // AI / Tech
    "openai.com": "AI_ML_PLATFORMS",
    "chatgpt.com": "AI_ML_PLATFORMS",
    "anthropic.com": "AI_ML_PLATFORMS",
    "claude.ai": "AI_ML_PLATFORMS",
    "huggingface.co": "AI_ML_PLATFORMS",
    "midjourney.com": "AI_ML_PLATFORMS",
    "deepmind.google": "AI_ML_PLATFORMS",
    "gemini.google.com": "AI_ML_PLATFORMS",
    // Dev & Cloud
    "github.com": "DEVELOPER_TOOLS",
    "gitlab.com": "DEVELOPER_TOOLS",
    "bitbucket.org": "DEVELOPER_TOOLS",
    "stackoverflow.com": "DEVELOPER_TOOLS",
    "stackexchange.com": "DEVELOPER_TOOLS",
    "npmjs.com": "DEVELOPER_TOOLS",
    "pypi.org": "DEVELOPER_TOOLS",
    "aws.amazon.com": "CLOUD_SERVICES",
    "azure.microsoft.com": "CLOUD_SERVICES",
    "cloud.google.com": "CLOUD_SERVICES",
    "digitalocean.com": "CLOUD_SERVICES",
    "vercel.com": "CLOUD_SERVICES",
    "netlify.com": "CLOUD_SERVICES",
    "cloudflare.com": "CYBERSECURITY",
    "crowdstrike.com": "CYBERSECURITY",
    "virustotal.com": "CYBERSECURITY",
    // Search
    "google.com": "SEARCH_ENGINE",
    "bing.com": "SEARCH_ENGINE",
    "duckduckgo.com": "SEARCH_ENGINE",
    "ecosia.org": "SEARCH_ENGINE",
    "yahoo.com": "SEARCH_ENGINE",
    "wikipedia.org": "EDUCATION",
    // Social
    "facebook.com": "SOCIAL_MEDIA",
    "instagram.com": "SOCIAL_MEDIA",
    "twitter.com": "SOCIAL_MEDIA",
    "x.com": "SOCIAL_MEDIA",
    "linkedin.com": "SOCIAL_MEDIA",
    "reddit.com": "SOCIAL_MEDIA",
    "pinterest.com": "SOCIAL_MEDIA",
    "tiktok.com": "SOCIAL_MEDIA",
    "snapchat.com": "SOCIAL_MEDIA",
    "threads.net": "SOCIAL_MEDIA",
    "whatsapp.com": "MESSAGING",
    "telegram.org": "MESSAGING",
    "discord.com": "MESSAGING",
    "slack.com": "MESSAGING",
    // Entertainment / Media
    "youtube.com": "MOVIE_STREAMING",
    "netflix.com": "MOVIE_STREAMING",
    "primevideo.com": "MOVIE_STREAMING",
    "disneyplus.com": "MOVIE_STREAMING",
    "hotstar.com": "MOVIE_STREAMING",
    "hulu.com": "MOVIE_STREAMING",
    "twitch.tv": "GAMING",
    "spotify.com": "MUSIC_STREAMING",
    "apple.com": "ECOMMERCE",
    "music.apple.com": "MUSIC_STREAMING",
    "soundcloud.com": "MUSIC_STREAMING",
    "steampowered.com": "GAMING",
    "epicgames.com": "GAMING",
    "roblox.com": "GAMING",
    "chess.com": "GAMING",
    // E-Commerce & Banking
    "amazon.com": "ECOMMERCE",
    "amazon.in": "ECOMMERCE",
    "flipkart.com": "ECOMMERCE",
    "walmart.com": "ECOMMERCE",
    "ebay.com": "ECOMMERCE",
    "aliexpress.com": "ECOMMERCE",
    "meesho.com": "ECOMMERCE",
    "myntra.com": "ECOMMERCE",
    "swiggy.com": "ECOMMERCE",
    "zomato.com": "ECOMMERCE",
    "paypal.com": "BANKING",
    "stripe.com": "BANKING",
    "razorpay.com": "BANKING",
    "chase.com": "BANKING",
    "bankofamerica.com": "BANKING",
    "wellsfargo.com": "BANKING",
    "sbi.co.in": "BANKING",
    "hdfcbank.com": "BANKING",
    "icicibank.com": "BANKING",
    // News
    "bbc.com": "NEWS_MEDIA",
    "cnn.com": "NEWS_MEDIA",
    "nytimes.com": "NEWS_MEDIA",
    "theguardian.com": "NEWS_MEDIA",
    "reuters.com": "NEWS_MEDIA",
    "bloomberg.com": "NEWS_MEDIA",
    // Caution - Piracy & Adult (1:1 Android PIRACY_DOMAINS & ADULT_DOMAINS)
    "vegamovies.gripe": "PIRACY",
    "vegamovies.nl": "PIRACY",
    "vegamovies.to": "PIRACY",
    "vegamovies.ist": "PIRACY",
    "vegamoviez.com": "PIRACY",
    "tamilrockers.ws": "PIRACY",
    "tamilrockers.wc": "PIRACY",
    "tamilrockers.com": "PIRACY",
    "1337x.to": "PIRACY",
    "1337x.st": "PIRACY",
    "1337x.gd": "PIRACY",
    "rarbg.to": "PIRACY",
    "rarbg.me": "PIRACY",
    "yts.mx": "PIRACY",
    "yts.am": "PIRACY",
    "yts.lt": "PIRACY",
    "piratebay.org": "PIRACY",
    "thepiratebay.org": "PIRACY",
    "thepiratebay.se": "PIRACY",
    "kickass.to": "PIRACY",
    "kickasstorrents.to": "PIRACY",
    "katcr.to": "PIRACY",
    "limetorrents.cc": "PIRACY",
    "limetorrents.info": "PIRACY",
    "torrentz2.eu": "PIRACY",
    "torrentz2.me": "PIRACY",
    "nyaa.si": "PIRACY",
    "nyaa.net": "PIRACY",
    "fmovies.to": "PIRACY",
    "fmovies.wtf": "PIRACY",
    "fmovies.ps": "PIRACY",
    "123movies.to": "PIRACY",
    "123movies.net": "PIRACY",
    "putlocker.to": "PIRACY",
    "putlockers.fm": "PIRACY",
    "gomovies.to": "PIRACY",
    "gostream.is": "PIRACY",
    "soap2day.to": "PIRACY",
    "soap2day.ac": "PIRACY",
    "hdmovie2.me": "PIRACY",
    "hdmovie2.ws": "PIRACY",
    "movierulz.com": "PIRACY",
    "movierulz.pe": "PIRACY",
    "movierulz.gs": "PIRACY",
    "filmyzilla.com": "PIRACY",
    "filmyzilla.in": "PIRACY",
    "mp4moviez.com": "PIRACY",
    "mp4moviez.in": "PIRACY",
    "bolly4u.org": "PIRACY",
    "bolly4u.cc": "PIRACY",
    "worldfree4u.com": "PIRACY",
    "worldfree4u.lol": "PIRACY",
    "9xmovies.in": "PIRACY",
    "9xmovies.com": "PIRACY",
    "kuttymovies.com": "PIRACY",
    "isaimini.com": "PIRACY",
    "tamilyogi.com": "PIRACY",
    "tamilyogi.cc": "PIRACY",
    "ssrmovies.club": "PIRACY",
    "extramovies.com": "PIRACY",
    "downloadhub.in": "PIRACY",
    "downloadhub.ws": "PIRACY",
    "skymovieshd.com": "PIRACY",
    "skymovieshd.in": "PIRACY",
    "afilmywap.com": "PIRACY",
    "filmywap.com": "PIRACY",
    "pagalmovies.com": "PIRACY",
    "cinevood.com": "PIRACY",
    "katmoviehd.com": "PIRACY",
    "katmoviehd.se": "PIRACY",
    "pornhub.com": "PORNOGRAPHY",
    "xvideos.com": "PORNOGRAPHY",
    "xhamster.com": "PORNOGRAPHY",
    "xnxx.com": "PORNOGRAPHY",
    "redtube.com": "PORNOGRAPHY",
    "youporn.com": "PORNOGRAPHY",
    "tube8.com": "PORNOGRAPHY",
    "spankbang.com": "PORNOGRAPHY",
    "eporner.com": "PORNOGRAPHY",
    "chaturbate.com": "PORNOGRAPHY",
    "bongacams.com": "PORNOGRAPHY",
    "stripchat.com": "PORNOGRAPHY",
    "livejasmin.com": "PORNOGRAPHY",
    "onlyfans.com": "PORNOGRAPHY",
    "fansly.com": "PORNOGRAPHY",
    "1xbet.com": "GAMBLING",
    "bet365.com": "GAMBLING",
    "stake.com": "GAMBLING"
  };

  /**
   * Categorizes a domain/URL based on multi-signal matching.
   */
  static categorize(urlString, pageText = "") {
    if (!urlString) {
      return { categoryKey: "UNKNOWN", emoji: "❓", label: "Unknown", threatLevel: "SAFE", confidence: 0, reason: "Empty URL" };
    }

    let domain = "";
    let pathLower = "";
    try {
      const parsed = new URL(urlString.startsWith('http') ? urlString : `https://${urlString}`);
      domain = parsed.hostname.toLowerCase().trim();
      pathLower = parsed.pathname.toLowerCase();
    } catch (e) {
      domain = urlString.toLowerCase().trim();
    }

    // Strip 'www.'
    if (domain.startsWith("www.")) {
      domain = domain.substring(4);
    }

    // 0. Live Cloud Global Domain Override Check (Deployed from Android / Firebase)
    const cloudOverrides = CloudDatasetManager.globalOverrides;
    if (cloudOverrides) {
      if (cloudOverrides[domain]) {
        const catKey = cloudOverrides[domain].toUpperCase();
        const catDef = this.CATEGORIES[catKey] || { emoji: "🛡️", label: catKey, threatLevel: "CAUTION" };
        return {
          categoryKey: catKey,
          emoji: catDef.emoji,
          label: catDef.label,
          threatLevel: catDef.threatLevel,
          confidence: 1.0,
          reason: "Matched Global Domain Override (Deployed from Cloud Dataset)"
        };
      }
      for (const [ovDomain, catKey] of Object.entries(cloudOverrides)) {
        if (domain.endsWith("." + ovDomain)) {
          const catKeyUpper = catKey.toUpperCase();
          const catDef = this.CATEGORIES[catKeyUpper] || { emoji: "🛡️", label: catKeyUpper, threatLevel: "CAUTION" };
          return {
            categoryKey: catKeyUpper,
            emoji: catDef.emoji,
            label: catDef.label,
            threatLevel: catDef.threatLevel,
            confidence: 0.99,
            reason: `Matched Global Parent Domain Override (${ovDomain})`
          };
        }
      }
    }

    // 0.1 Injected Cloud Category Keywords Check (Deployed from Android / Firebase)
    const cloudKeywords = CloudDatasetManager.keywordSignals;
    if (cloudKeywords) {
      for (const catKey in cloudKeywords) {
        const words = cloudKeywords[catKey] || [];
        for (const kw of words) {
          if (kw && (domain.includes(kw.toLowerCase()) || pathLower.includes(kw.toLowerCase()))) {
            const catKeyUpper = catKey.toUpperCase();
            const catDef = this.CATEGORIES[catKeyUpper] || { emoji: "🛡️", label: catKeyUpper, threatLevel: "CAUTION" };
            return {
              categoryKey: catKeyUpper,
              emoji: catDef.emoji,
              label: catDef.label,
              threatLevel: catDef.threatLevel,
              confidence: 0.95,
              reason: `Matched injected cloud keyword: '${kw}' for ${catDef.label}`
            };
          }
        }
      }
    }

    // 1. Direct Known Domain Check
    if (this.KNOWN_DOMAINS[domain]) {
      const catKey = this.KNOWN_DOMAINS[domain];
      const catDef = this.CATEGORIES[catKey] || this.CATEGORIES.GENERAL_SAFE;
      return {
        categoryKey: catKey,
        emoji: catDef.emoji,
        label: catDef.label,
        threatLevel: catDef.threatLevel,
        confidence: 0.99,
        reason: `Matched verified domain database (${domain})`
      };
    }

    // Check parent domain (e.g., sub.github.com -> github.com)
    for (const [kDomain, catKey] of Object.entries(this.KNOWN_DOMAINS)) {
      if (domain.endsWith("." + kDomain)) {
        const catDef = this.CATEGORIES[catKey] || this.CATEGORIES.GENERAL_SAFE;
        return {
          categoryKey: catKey,
          emoji: catDef.emoji,
          label: catDef.label,
          threatLevel: catDef.threatLevel,
          confidence: 0.95,
          reason: `Verified subdomain of ${kDomain}`
        };
      }
    }

    // 2. TLD Structural Classification
    const parts = domain.split('.');
    const tld = '.' + (parts[parts.length - 1] || '');

    if (tld === ".edu" || tld === ".ac.in" || tld === ".ac.uk") {
      const c = this.CATEGORIES.EDUCATION;
      return { categoryKey: "EDUCATION", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.9, reason: "Academic Top-Level Domain" };
    }
    if (tld === ".gov" || tld === ".gov.in" || tld === ".mil") {
      const c = this.CATEGORIES.GOVERNMENT;
      return { categoryKey: "GOVERNMENT", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Official Government TLD" };
    }
    if ([".xxx", ".adult", ".porn", ".sex", ".cam"].includes(tld)) {
      const c = this.CATEGORIES.PORNOGRAPHY;
      return { categoryKey: "PORNOGRAPHY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Adult content TLD" };
    }
    if ([".bet", ".casino", ".poker"].includes(tld)) {
      const c = this.CATEGORIES.GAMBLING;
      return { categoryKey: "GAMBLING", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Gambling & Casino TLD" };
    }
    if ([".gripe", ".ist", ".wc", ".wtf", ".lol", ".gd", ".ps", ".ac", ".to", ".is"].includes(tld)) {
      const c = this.CATEGORIES.PIRACY;
      return { categoryKey: "PIRACY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "High-risk piracy TLD" };
    }

    // 3. Keyword Signals in URL / Hostname
    const lowerUrl = urlString.toLowerCase();
    const lowerPageText = (pageText || "").toLowerCase();
    
    // Adult (1:1 Android ADULT_KEYWORDS & ADULT_DOMAIN_KEYWORDS)
    const adultKeywords = ["porn", "xxx", "nude", "escort", "nsfw", "desi xxx", "desihd", "desi-hd", "cam girl", "onlyfans", "adult video", "fuck", "milf", "hentai"];
    if (adultKeywords.some(k => domain.includes(k) || lowerUrl.includes('/' + k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.PORNOGRAPHY;
      return { categoryKey: "PORNOGRAPHY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Adult keywords in URL structure or page content" };
    }
    // Gambling
    const gamblingKeywords = ["casino", "betting", "gamble", "free-spins", "slots", "poker", "jackpot", "1xbet", "stake"];
    if (gamblingKeywords.some(k => domain.includes(k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.GAMBLING;
      return { categoryKey: "GAMBLING", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Gambling keywords in domain or page content" };
    }
    // Piracy (1:1 Android PIRACY_DOMAIN_KEYWORDS)
    const piracyKeywords = [
      "torrent", "movie", "moviez", "pirate", "warez", "crack", "nulled", "fmovie",
      "putlocker", "123movie", "gomovie", "soap2day", "movierulz", "filmyzilla",
      "mp4moviez", "hdmovie", "bolly4u", "9xmovie", "kuttymovie", "tamilyogi",
      "isaimini", "downloadhub", "skymovieshd", "filmywap", "pagalmovie", "cinevood",
      "katmovie", "vegamovie", "tamilrocker", "yts", "rarbg", "kickass", "limetorrent"
    ];
    if (piracyKeywords.some(k => domain.includes(k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.PIRACY;
      return { categoryKey: "PIRACY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Piracy/Torrent keywords in domain or page content" };
    }
    // Scams
    const scamKeywords = ["tech-support", "crypto-doubler", "claim-prize", "account-suspended", "verify-id"];
    if (scamKeywords.some(k => domain.includes(k) || lowerUrl.includes(k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.TECH_SUPPORT_SCAMS;
      return { categoryKey: "TECH_SUPPORT_SCAMS", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Deceptive social engineering keywords" };
    }

    // Default Fallback
    const c = this.CATEGORIES.GENERAL_SAFE;
    return {
      categoryKey: "GENERAL_SAFE",
      emoji: c.emoji,
      label: c.label,
      threatLevel: c.threatLevel,
      confidence: 0.6,
      reason: "General Web Destination"
    };
  }

  static async categorizeAsync(urlString, pageTitle = "", pageText = "") {
    const syncResult = this.categorize(urlString, pageText);
    if (syncResult && syncResult.confidence >= 0.8 && syncResult.categoryKey !== "GENERAL_SAFE") {
      return syncResult;
    }

    try {
      const llm7 = new Llm7Client();
      const aiResult = await llm7.classifyWebsite(urlString, pageTitle, pageText);
      if (aiResult && aiResult.category) {
        const catKey = aiResult.category.toUpperCase();
        const catDef = this.CATEGORIES[catKey];
        if (catDef) {
          return {
            categoryKey: catKey,
            emoji: catDef.emoji,
            label: catDef.label,
            threatLevel: catDef.threatLevel,
            confidence: aiResult.confidence || 0.85,
            reason: `LLM7 AI: ${aiResult.reason || 'Classified via AI inference'}`
          };
        }
      }
    } catch (e) {
      console.warn("LLM7 AI Categorization failed in service-worker:", e);
    }

    return syncResult;
  }
}


// --- UpiPaymentAnalyzer.js ---
// UpiPaymentAnalyzer.js
// Port of ThreatLens Android UpiPaymentAnalyzer.kt & UpiTransactionMLEngine.kt

class UpiPaymentAnalyzer {

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
}


// --- WifiThreatAnalyzer.js ---
// WifiThreatAnalyzer.js
// Port of ThreatLens Android WifiThreatAnalyzer.kt

class WifiThreatAnalyzer {

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


// --- QrDecoder.js ---
// QrDecoder.js
// Handles client-side in-browser QR Code image extraction and decoding

class QrDecoder {

  /**
   * Decodes a QR code from an image source URL, HTMLImageElement, or Canvas.
   * @param {string|HTMLImageElement|Blob|ImageData} source 
   * @returns {Promise<string|null>} Decoded QR content or null if no QR found.
   */
  static async decodeFromImage(source) {
    try {
      // 1. Try Native Chrome BarcodeDetector API (Zero external dependency, fast C++ ML engine in Chromium)
      if ('BarcodeDetector' in window) {
        const barcodeDetector = new window.BarcodeDetector({ formats: ['qr_code'] });
        
        let imageBitmap = null;
        if (typeof source === 'string') {
          const img = await this.loadImage(source);
          imageBitmap = await createImageBitmap(img);
        } else if (source instanceof HTMLImageElement || source instanceof HTMLCanvasElement) {
          imageBitmap = await createImageBitmap(source);
        } else if (source instanceof Blob) {
          imageBitmap = await createImageBitmap(source);
        } else if (source instanceof ImageData) {
          imageBitmap = await createImageBitmap(source);
        }

        if (imageBitmap) {
          const barcodes = await barcodeDetector.detect(imageBitmap);
          if (barcodes && barcodes.length > 0) {
            return barcodes[0].rawValue;
          }
        }
      }
    } catch (e) {
      console.warn("Native BarcodeDetector pass failed, trying canvas fallback:", e);
    }

    // 2. Fallback via Canvas ImageData extraction
    try {
      if (typeof source === 'string') {
        const img = await this.loadImage(source);
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth || img.width;
        canvas.height = img.naturalHeight || img.height;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0);
        
        // If BarcodeDetector is available with canvas
        if ('BarcodeDetector' in window) {
          const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
          const codes = await detector.detect(canvas);
          if (codes.length > 0) return codes[0].rawValue;
        }
      }
    } catch (err) {
      console.error("Canvas QR decode failed:", err);
    }

    return null;
  }

  static loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "Anonymous";
      img.onload = () => resolve(img);
      img.onerror = (e) => reject(new Error("Failed to load image for QR decoding"));
      img.src = src;
    });
  }
}


// --- Llm7Client.js ---
class Llm7Client {
  constructor(apiKey = null) {
    this.apiKey = apiKey || "jc8ydp2rnkoVuODXFJRFAILIY+KpjUuSbjWeLb9CqSAv1rNhwdNQllrPi6oQ5Q37LtGbVGvwKDHq06/HEP+nXE+jKtXLIFiH/beTcdPoq7n8kxaISx9bmfrWaVe3p9YuZUotBO1ZuMPcDrjRD+1QU+EhbuAerw==";
    this.apiUrl = "https://api.llm7.io/v1/chat/completions";
    this.model = "default";
  }

  async getThreatExplanation(url, score, flags = [], threatType = "UNKNOWN", category = "") {
    if (this.apiKey && this.apiKey.length > 10) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 6000);

        const response = await fetch(this.apiUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.apiKey}`
          },
          body: JSON.stringify({
            model: this.model,
            messages: [
              {
                role: "system",
                content: "You are ThreatLens AI Security Engine. Explain in 2 concise sentences why this site is dangerous or safe to a regular web user. Be objective, crisp, and direct."
              },
              {
                role: "user",
                content: `URL: ${url}, Threat Type: ${threatType}, Category: ${category}, Flags: ${flags.join('; ')}, Score: ${score}/100.`
              }
            ],
            max_tokens: 180,
            temperature: 0.3
          }),
          signal: controller.signal
        });

        clearTimeout(timeoutId);

        if (response.ok) {
          const data = await response.json();
          if (data.choices && data.choices[0]?.message?.content) {
            return data.choices[0].message.content.trim();
          }
        }
      } catch (e) {
        if (e.name !== 'AbortError') {
          console.warn("LLM7 API call failed in service-worker:", e.message || e);
        }
      }
    }

    return this.generateDeterministicInsight(url, score, flags, threatType, category);
  }

  async classifyWebsite(url, pageTitle = "", pageText = "") {
    if (!this.apiKey || this.apiKey.length < 10) return null;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 6000);

      const systemPrompt = `You are ThreatLens AI Website Classifier. Classify the website into exactly one category based on its metadata.
Allowed categories:
MALWARE, PHISHING, FINANCIAL_FRAUD, TECH_SUPPORT_SCAMS, RANSOMWARE, IMPERSONATION_SITES,
PORNOGRAPHY, GAMBLING, PIRACY, SEARCH_ENGINE, AI_ML_PLATFORMS, DEVELOPER_TOOLS,
CLOUD_SERVICES, CYBERSECURITY, BANKING, ECOMMERCE, SOCIAL_MEDIA, MESSAGING,
NEWS_MEDIA, MOVIE_STREAMING, MUSIC_STREAMING, GAMING, EDUCATION, GOVERNMENT,
HEALTHCARE, BUSINESS, GENERAL_SAFE.

Respond ONLY with valid JSON in this exact structure:
{"category": "<CATEGORY_NAME>", "confidence": <0.0 to 1.0>, "reason": "<brief 1-sentence reason>"}`;

      const snippet = (pageText || "").substring(0, 350);
      const userPrompt = `URL: ${url}\nTitle: ${pageTitle}\nSnippet: ${snippet}`;

      const response = await fetch(this.apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          model: this.model,
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: userPrompt }
          ],
          max_tokens: 120,
          temperature: 0.2
        }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (response.ok) {
        const data = await response.json();
        const content = data.choices?.[0]?.message?.content;
        if (content) {
          const match = content.match(/\{[\s\S]*\}/);
          if (match) {
            return JSON.parse(match[0]);
          }
        }
      }
    } catch (e) {
      if (e.name !== 'AbortError') {
        console.warn("LLM7 website classification failed:", e.message || e);
      }
    }
    return null;
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

// --- GeminiClient.js ---
class GeminiClient {
  constructor(apiKey = null) {
    this.apiKey = apiKey;
    this.llm7 = new Llm7Client();
  }

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

    // 2. Try LLM7.io Fast Engine
    try {
      const llm7Insight = await this.llm7.getThreatExplanation(url, score, flags, threatType, category);
      if (llm7Insight && !llm7Insight.includes("matches standard security criteria") && !llm7Insight.includes("Multiple suspicious indicators")) {
        return llm7Insight;
      }
      if (llm7Insight) return llm7Insight;
    } catch (e) {
      console.warn("LLM7 call in GeminiClient failed:", e);
    }

    // 3. Deterministic Neural Heuristic Explainer
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

// --- CloudSync.js ---
class CloudSync {
  
  static CLOUD_FUNCTION_URL = "https://us-central1-threatlens-4065e.cloudfunctions.net";
  
  // Extracted from Android App's google-services.json
  static FIREBASE_API_KEY = "AIzaSyB5lzHQT5xJYGPzPZiiKPBciNIIq3Pq8_0";
  static FIREBASE_PROJECT_ID = "threatlens-4065e";
  static GOOGLE_CLIENT_ID = "171716467103-b09ukthoklag4na2p1sk30lduo41c098.apps.googleusercontent.com";

  // State
  static idToken = null;
  static refreshToken = null;
  static userId = null;
  static userEmail = null;
  static displayName = null;
  static photoUrl = null;
  static tokenExpiry = null;

  /**
   * Helper to sanitize User IDs identically to Android app
   */
  static sanitizeUserId(id) {
    if (!id) return "";
    return id.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
  }

  /**
   * Initialize session from local storage and auto-refresh token if needed
   */
  static async initSession() {
    return new Promise((resolve) => {
      chrome.storage.local.get([
        'cloudToken',
        'cloudRefreshToken',
        'cloudUserId',
        'cloudEmail',
        'cloudDisplayName',
        'cloudPhotoUrl',
        'cloudTokenExpiry'
      ], async (result) => {
        if (result.cloudToken) {
          this.idToken = result.cloudToken;
          this.refreshToken = result.cloudRefreshToken || null;
          this.userId = result.cloudUserId || null;
          this.userEmail = result.cloudEmail || null;
          this.displayName = result.cloudDisplayName || null;
          this.photoUrl = result.cloudPhotoUrl || null;
          this.tokenExpiry = result.cloudTokenExpiry || null;

          if (this.refreshToken && (!this.tokenExpiry || Date.now() > this.tokenExpiry - 300000)) {
            await this.refreshIdToken();
          }
        }
        resolve({
          isLoggedIn: !!this.idToken,
          userId: this.userId,
          userEmail: this.userEmail,
          displayName: this.displayName,
          photoUrl: this.photoUrl
        });
      });
    });
  }

  /**
   * Refreshes the Firebase ID token using the Refresh Token
   */
  static async refreshIdToken() {
    if (!this.refreshToken) return false;
    try {
      const response = await fetch(`https://securetoken.googleapis.com/v1/token?key=${this.FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(this.refreshToken)}`
      });

      if (!response.ok) return false;
      const data = await response.json();

      this.idToken = data.id_token || data.access_token;
      this.refreshToken = data.refresh_token || this.refreshToken;
      this.tokenExpiry = Date.now() + (parseInt(data.expires_in || "3600", 10) * 1000);

      await chrome.storage.local.set({
        cloudToken: this.idToken,
        cloudRefreshToken: this.refreshToken,
        cloudTokenExpiry: this.tokenExpiry
      });
      return true;
    } catch (e) {
      console.warn("Token refresh failed:", e);
      return false;
    }
  }

  /**
   * Resolves Firestore user record by email or sanitized userId
   */
  static async fetchUserProfile(sanitizedId) {
    try {
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitizedId}`;
      const response = await fetch(url);
      if (!response.ok) return null;
      const doc = await response.json();
      if (!doc || !doc.fields) return null;

      return {
        userId: doc.fields.userId?.stringValue || sanitizedId,
        email: doc.fields.email?.stringValue || null,
        name: doc.fields.name?.stringValue || null,
        photoUrl: doc.fields.photoUrl?.stringValue || null
      };
    } catch (e) {
      console.warn("Error fetching user profile:", e);
      return null;
    }
  }

  /**
   * Queries Firestore users collection by email address
   */
  static async findUserByEmail(email) {
    try {
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents:runQuery`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          structuredQuery: {
            from: [{ collectionId: "users" }],
            where: {
              fieldFilter: {
                field: { fieldPath: "email" },
                op: "EQUAL",
                value: { stringValue: email.trim().toLowerCase() }
              }
            },
            limit: 1
          }
        })
      });

      if (!response.ok) return null;
      const results = await response.json();
      if (Array.isArray(results) && results[0] && results[0].document && results[0].document.fields) {
        const doc = results[0].document;
        const rawDocId = doc.name.split('/').pop();
        return {
          userId: doc.fields.userId?.stringValue || rawDocId,
          email: doc.fields.email?.stringValue || email,
          name: doc.fields.name?.stringValue || null,
          photoUrl: doc.fields.photoUrl?.stringValue || null
        };
      }
      return null;
    } catch (e) {
      console.warn("Error finding user by email:", e);
      return null;
    }
  }

  /**
   * Push a local scan to Firestore 'history' collection (Matching Android ScanResult Schema)
   */
  static async syncScanToCloud(scanResult) {
    if (!this.idToken || !this.userId || !scanResult) return false;

    if (this.tokenExpiry && Date.now() > this.tokenExpiry) {
      await this.refreshIdToken();
    }

    try {
      const rawStr = scanResult.rawContent || scanResult.payload || scanResult.url || scanResult.expandedUrl || '';
      let hash = 0;
      for (let i = 0; i < rawStr.length; i++) {
        hash = Math.imul(31, hash) + rawStr.charCodeAt(i) | 0;
      }
      const docId = (hash !== 0 ? hash : Date.now()).toString();

      const payloadObject = {
        rawContent: scanResult.rawContent || scanResult.payload || rawStr,
        isUrl: scanResult.isUrl !== undefined ? scanResult.isUrl : (rawStr.startsWith('http://') || rawStr.startsWith('https://')),
        originalUrl: scanResult.originalUrl || scanResult.rawContent || rawStr,
        expandedUrl: scanResult.expandedUrl || scanResult.rawContent || rawStr,
        domain: scanResult.domain || (scanResult.isUrl ? new URL(rawStr).hostname : null),
        safetyStatus: scanResult.safetyStatus || 'SAFE',
        overallScore: scanResult.overallScore !== undefined ? scanResult.overallScore : (100 - (scanResult.riskScore || scanResult.score || 0)),
        heuristicFlags: scanResult.flags || scanResult.heuristicFlags || [],
        threatDetails: scanResult.threatDetails || scanResult.flags || [],
        siteCategory: scanResult.siteCategory || 'General',
        siteSummary: scanResult.siteSummary || scanResult.message || '',
        timestamp: scanResult.timestamp || Date.now(),
        upiAnalysis: scanResult.upiAnalysis || null,
        wifiAnalysis: scanResult.wifiAnalysis || null
      };

      const jsonPayload = JSON.stringify(payloadObject);
      const firestoreDoc = {
        fields: {
          timestamp: { integerValue: (scanResult.timestamp || Date.now()).toString() },
          data: { stringValue: jsonPayload }
        }
      };

      const sanitized = this.sanitizeUserId(this.userId);
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history?documentId=${docId}`;
      
      let response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.idToken}`
        },
        body: JSON.stringify(firestoreDoc)
      });
      
      if (response.status === 409) {
        const patchUrl = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history/${docId}`;
        response = await fetch(patchUrl, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.idToken}`
          },
          body: JSON.stringify(firestoreDoc)
        });
      } else if (response.status === 401) {
        const refreshed = await this.refreshIdToken();
        if (refreshed) {
          await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${this.idToken}`
            },
            body: JSON.stringify(firestoreDoc)
          });
        }
      }

      return response.ok;
    } catch (e) {
      console.error("Failed to sync scan to cloud:", e);
      return false;
    }
  }

  /**
   * Fetch History from Firestore (Matching Android CloudSyncManager.fetchHistoryFromCloud)
   */
  static async fetchHistoryFromCloud() {
    if (!this.idToken || !this.userId) return [];

    if (this.tokenExpiry && Date.now() > this.tokenExpiry) {
      await this.refreshIdToken();
    }

    try {
      const sanitized = this.sanitizeUserId(this.userId);
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history`;
      let response = await fetch(url, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${this.idToken}` }
      });

      if (response.status === 401) {
        const refreshed = await this.refreshIdToken();
        if (refreshed) {
          response = await fetch(url, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${this.idToken}` }
          });
        }
      }

      const data = await response.json();
      if (data.documents) {
        return data.documents.map(doc => {
          if (doc.fields && doc.fields.data && doc.fields.data.stringValue) {
            try {
              return JSON.parse(doc.fields.data.stringValue);
            } catch (e) { return null; }
          }
          return null;
        }).filter(item => item !== null);
      }
      return [];
    } catch (e) {
      console.error("Failed to fetch history:", e);
      return [];
    }
  }

  /**
   * Sends URL & DOM to Firebase Cloud Function for deep neural inspection
   */
  static async analyzeUrlDeeply(url, pageText = "") {
    try {
      const response = await fetch(`${this.CLOUD_FUNCTION_URL}/analyzeThreatUrl`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url, pageText: pageText })
      });

      if (!response.ok) return null;

      const result = await response.json();
      if (result.success && result.data) {
        return {
          score: result.data.score,
          flags: result.data.flags || [],
          threatType: result.data.threatType || 'SAFE',
          aiInsight: result.data.aiInsight || null
        };
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /**
   * Gets community reports for a given URL from Firestore.
   */
  static async getCommunityReports(url) {
    if (!url) return { count: 0, reasons: [] };
    try {
      const sanitizedUrl = url.replace(/[^a-zA-Z0-9]/g, "_");
      const endpoint = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/reports/${sanitizedUrl}`;
      const response = await fetch(endpoint);
      if (!response.ok) return { count: 0, reasons: [] };

      const doc = await response.json();
      if (doc.fields && doc.fields.reasons && doc.fields.reasons.arrayValue && doc.fields.reasons.arrayValue.values) {
        const reasons = doc.fields.reasons.arrayValue.values.map(v => v.stringValue).filter(Boolean);
        return { count: reasons.length, reasons: reasons };
      }
      return { count: 0, reasons: [] };
    } catch (e) {
      return { count: 0, reasons: [] };
    }
  }

  /**
   * Reports a website/URL to the global Firestore community database.
   */
  static async reportWebsite(url, issue) {
    if (!url || !issue) return false;
    try {
      const sanitizedUrl = url.replace(/[^a-zA-Z0-9]/g, "_");
      const current = await this.getCommunityReports(url);
      const updatedReasons = [...current.reasons, issue];

      const firestoreDoc = {
        fields: {
          reasons: {
            arrayValue: {
              values: updatedReasons.map(r => ({ stringValue: r }))
            }
          }
        }
      };

      const patchUrl = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/reports/${sanitizedUrl}?updateMask.fieldPaths=reasons`;
      const response = await fetch(patchUrl, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(firestoreDoc)
      });
      return response.ok;
    } catch (e) {
      return false;
    }
  }

  // Future: Add methods for fetching Community Reports here using Firebase REST API
  // static async fetchCommunityReports(url) { ... }
}


// --- HistoryManager.js ---
// HistoryManager.js
// Manages local storage of scan history, simulating the Room DB in the Android app.

class HistoryManager {
  
  /**
   * Saves a ScanResult object into local storage and synchronizes with Firebase cloud.
   * @param {Object} scanResult 
   */
  static async saveScan(scanResult) {
    if (!scanResult) return null;

    const record = {
      id: crypto.randomUUID ? crypto.randomUUID() : `scan_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      timestamp: scanResult.timestamp || Date.now(),
      payload: scanResult.rawContent || scanResult.payload || scanResult.expandedUrl || '',
      rawContent: scanResult.rawContent || scanResult.payload || '',
      type: scanResult.scanType || scanResult.type || 'URL',
      scanType: scanResult.scanType || scanResult.type || 'URL',
      overallScore: scanResult.overallScore !== undefined ? scanResult.overallScore : (100 - (scanResult.score || scanResult.riskScore || 0)),
      score: scanResult.riskScore !== undefined ? scanResult.riskScore : (scanResult.score || 0),
      riskScore: scanResult.riskScore !== undefined ? scanResult.riskScore : (scanResult.score || 0),
      safetyStatus: scanResult.safetyStatus || (scanResult.riskScore >= 60 ? 'MALICIOUS' : (scanResult.riskScore >= 30 ? 'CAUTION' : 'SAFE')),
      threatType: scanResult.threatType || 'SAFE',
      siteCategory: scanResult.siteCategory || 'General',
      siteSummary: scanResult.siteSummary || scanResult.message || '',
      threatDetails: scanResult.threatDetails || scanResult.flags || [],
      flags: scanResult.flags || [],
      upiAnalysis: scanResult.upiAnalysis || null,
      wifiAnalysis: scanResult.wifiAnalysis || null
    };

    return new Promise(async (resolve) => {
      chrome.storage.local.get(['threatlens_history'], async (result) => {
        let history = result.threatlens_history || [];
        history.unshift(record); // Add to beginning

        // Keep last 500 scans
        if (history.length > 500) {
          history = history.slice(0, 500);
        }

        chrome.storage.local.set({ 'threatlens_history': history }, async () => {
          // Attempt to sync to cloud
          try {
            await CloudSync.initSession();
            if (CloudSync.idToken) {
              await CloudSync.syncScanToCloud(record);
            }
          } catch (e) {
            console.warn("Cloud sync error:", e);
          }
          
          resolve(record);
        });
      });
    });
  }

  static async getHistory() {
    return new Promise((resolve) => {
      chrome.storage.local.get(['threatlens_history'], (result) => {
        resolve(result.threatlens_history || []);
      });
    });
  }

  static async clearHistory() {
    return new Promise((resolve) => {
      chrome.storage.local.remove(['threatlens_history'], () => {
        resolve();
      });
    });
  }
}


// --- ThreatAnalyzer.js ---
// ThreatAnalyzer.js
// Master pipeline matching Android ThreatAnalyzer.kt









// --- CertificateEngine.js ---
class CertificateEngine {
  static CERT_SCHEME = "threatlenscert://";
  static SIGNING_KEY = "ThreatLens-S3cur3-S1gn1ng-K3y-2025!@#";

  static async generateCertId(content) {
    try {
      const enc = new TextEncoder();
      const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(content));
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
      return hex.substring(0, 8).toUpperCase();
    } catch (e) {
      return Math.random().toString(16).substring(2, 10).toUpperCase();
    }
  }

  static canonicalString(payload) {
    return `${payload.id}|${payload.content}|${payload.status}|${payload.score}|${payload.ts}`;
  }

  static async sign(dataString) {
    const enc = new TextEncoder();
    const keyData = enc.encode(this.SIGNING_KEY);
    const key = await crypto.subtle.importKey(
      'raw',
      keyData,
      { name: 'HMAC', hash: { name: 'SHA-256' } },
      false,
      ['sign', 'verify']
    );
    const signatureBuffer = await crypto.subtle.sign('HMAC', key, enc.encode(dataString));
    const bytes = new Uint8Array(signatureBuffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  static utf8ToBase64(str) {
    const bytes = new TextEncoder().encode(str);
    let bin = '';
    for (let i = 0; i < bytes.length; i++) {
      bin += String.fromCharCode(bytes[i]);
    }
    return btoa(bin);
  }

  static base64ToUtf8(base64) {
    const bin = atob(base64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      bytes[i] = bin.charCodeAt(i);
    }
    return new TextDecoder().decode(bytes);
  }

  static async buildCertifiedPayload(originalContent, safetyStatus = "SAFE", score = 100) {
    const certId = await this.generateCertId(originalContent);
    const ts = Date.now();
    const unsigned = {
      v: 1,
      id: certId,
      content: originalContent,
      status: safetyStatus,
      score: score,
      ts: ts
    };
    const signature = await this.sign(this.canonicalString(unsigned));
    const signed = { ...unsigned, sig: signature };
    const jsonStr = JSON.stringify(signed);
    const encoded = this.utf8ToBase64(jsonStr);
    return `${this.CERT_SCHEME}${encoded}`;
  }

  static async verify(certString) {
    if (!certString || !certString.startsWith(this.CERT_SCHEME)) {
      return { isValid: false, isTampered: false, payload: null, reason: "Not a certified QR payload" };
    }
    try {
      const base64 = certString.substring(this.CERT_SCHEME.length);
      const jsonStr = this.base64ToUtf8(base64);
      const payload = JSON.parse(jsonStr);

      if (!payload || !payload.sig || !payload.id || !payload.content) {
        return { isValid: false, isTampered: true, payload: null, reason: "Malformed certificate fields" };
      }

      const expectedSig = await this.sign(this.canonicalString(payload));
      if (expectedSig !== payload.sig) {
        return { isValid: false, isTampered: true, payload: payload, reason: "Cryptographic signature mismatch (Tampered QR)" };
      }

      return {
        isValid: true,
        isTampered: false,
        payload: payload,
        certId: payload.id,
        status: payload.status,
        score: payload.score,
        content: payload.content,
        timestamp: payload.ts
      };
    } catch (e) {
      return { isValid: false, isTampered: true, payload: null, reason: `Failed to decode certificate: ${e.message}` };
    }
  }
}

class ThreatAnalyzer {
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

    // Step A.5: Fetch Page Content for Accurate Categorization (Skip during fast link scan to eliminate 3s latency)
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


// --- Service Worker Execution Logic ---
// service-worker.js
// Background service worker for ThreatLens Chrome Extension




const analyzer = new ThreatAnalyzer();
const bypassedUrls = new Set();
const activeThreatReports = new Map();

// 1. Setup Context Menu for QR Code Scanning
function registerContextMenus() {
  if (!chrome.contextMenus) return;
  chrome.contextMenus.removeAll(() => {
    if (chrome.runtime.lastError) {}
    chrome.contextMenus.create({
      id: "scan-screen-snip",
      title: "✂️ Snip & Scan QR Area (Alt+Q)",
      contexts: ["page", "video", "image", "selection"]
    }, () => {
      if (chrome.runtime.lastError) {}
    });
    chrome.contextMenus.create({
      id: "scan-qr-image",
      title: "🛡️ Scan QR Image with ThreatLens",
      contexts: ["image"]
    }, () => {
      if (chrome.runtime.lastError) {}
    });
  });
}

chrome.runtime.onInstalled.addListener(() => {
  registerContextMenus();
});

// Keyboard shortcut handler
if (chrome.commands && chrome.commands.onCommand) {
  chrome.commands.onCommand.addListener((command) => {
    if (command === "scan-screen-snip") {
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0] && tabs[0].id) {
          chrome.tabs.sendMessage(tabs[0].id, { action: "START_SCREEN_SNIP" }, () => {
            if (chrome.runtime.lastError) {}
          });
        }
      });
    }
  });
}

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "scan-screen-snip" && tab && tab.id) {
    chrome.tabs.sendMessage(tab.id, {
      action: "START_SCREEN_SNIP"
    }, () => { if (chrome.runtime.lastError) {} });
  } else if (info.menuItemId === "scan-qr-image" && tab && tab.id) {
    if (info.srcUrl) {
      chrome.tabs.sendMessage(tab.id, {
        action: "SCAN_SPECIFIC_IMAGE",
        src: info.srcUrl
      }, () => { if (chrome.runtime.lastError) {} });
    } else {
      chrome.tabs.sendMessage(tab.id, {
        action: "SCAN_PAGE_QR"
      }, () => { if (chrome.runtime.lastError) {} });
    }
  }
});

// 2. Listen for messages from Popup, Dashboard, Warning Page, or Content Scripts
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === "CAPTURE_VISIBLE_TAB") {
    chrome.tabs.captureVisibleTab(null, { format: 'png' }, (dataUrl) => {
      if (chrome.runtime.lastError || !dataUrl) {
        sendResponse({ success: false, error: chrome.runtime.lastError?.message || "Failed to capture visible screen." });
      } else {
        sendResponse({ success: true, dataUrl: dataUrl });
      }
    });
    return true;
  }

  if (message.action === "FETCH_SANDBOX_HTML") {
    const targetUrl = message.url;
    if (!targetUrl || !targetUrl.startsWith('http')) {
      sendResponse({ success: false, error: "Invalid URL or non-HTTP protocol." });
      return true;
    }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    const ua = message.isDesktop
      ? 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      : 'Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

    fetch(targetUrl, {
      signal: controller.signal,
      headers: {
        'User-Agent': ua
      }
    })
    .then(async (res) => {
      clearTimeout(timeout);
      if (!res.ok) throw new Error(`Remote server responded with HTTP ${res.status}`);
      const rawHtml = await res.text();

      // Threat & DOM Analysis
      const scriptMatches = rawHtml.match(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi) || [];
      const formMatches = rawHtml.match(/<form\b[^>]*>/gi) || [];
      const iframeMatches = rawHtml.match(/<iframe\b[^>]*>/gi) || [];
      const passwordInputs = (rawHtml.match(/type\s*=\s*["']password["']/gi) || []).length;

      const blockedTrackers = [];
      const trackerKeywords = ["analytics", "pixel", "doubleclick", "facebook.net", "google-analytics", "hotjar", "clarity.ms", "telemetry", "criteo", "appsflyer"];
      trackerKeywords.forEach(kw => {
        if (rawHtml.toLowerCase().includes(kw)) {
          blockedTrackers.push(`Blocked & Isolated Tracker Keyword: ${kw}`);
        }
      });

      let sanitized = rawHtml;
      if (!message.allowScripts) {
        sanitized = sanitized
          .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
          .replace(/\bon\w+\s*=\s*(["'])[\s\S]*?\1/gi, "")
          .replace(/<form\b[^>]*>/gi, '<form onsubmit="return false;" style="pointer-events:none;">');
      }

      // Inject base href
      if (/<head[^>]*>/i.test(sanitized)) {
        sanitized = sanitized.replace(/(<head[^>]*>)/i, `$1<base href="${targetUrl}">`);
      } else {
        sanitized = `<base href="${targetUrl}">${sanitized}`;
      }

      // Inject ThreatLens Sandbox Security Banner
      let hostname = 'unknown';
      try { hostname = new URL(targetUrl).hostname; } catch (e) {}
      const banner = `
        <div id="threatlens-isolated-banner" style="background:#09090b;color:#00f0ff;padding:8px 16px;font-family:sans-serif;font-size:11.5px;border-bottom:2px solid #00f0ff;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:99999999;box-shadow:0 2px 12px rgba(0,0,0,0.8);">
          <div style="display:flex;align-items:center;gap:8px;">
            <span>🔒</span>
            <strong style="letter-spacing:0.5px;">ThreatLens Isolated Sandbox</strong>
            <span style="background:rgba(16,185,129,0.2);color:#10b981;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:700;">Zero Cookies & Storage</span>
            ${!message.allowScripts ? '<span style="background:rgba(6,182,212,0.2);color:#06b6d4;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:700;">Scripts Blocked</span>' : '<span style="background:rgba(239,68,68,0.2);color:#ef4444;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:700;">Scripts Active</span>'}
          </div>
          <span style="color:#a1a1aa;font-family:monospace;font-size:11px;">Host: ${hostname}</span>
        </div>
      `;
      sanitized = sanitized.replace(/(<body[^>]*>)/i, `$1${banner}`);

      sendResponse({
        success: true,
        sanitizedHtml: sanitized,
        rawHtml: rawHtml,
        telemetry: {
          scriptsStripped: scriptMatches.length,
          formsDisarmed: formMatches.length,
          iframesFound: iframeMatches.length,
          passwordInputs: passwordInputs,
          blockedTrackers: blockedTrackers
        }
      });
    })
    .catch(err => {
      clearTimeout(timeout);
      sendResponse({ success: false, error: err.message });
    });
    return true;
  }

  if (message.action === "BUILD_CERTIFIED_QR") {
    CertificateEngine.buildCertifiedPayload(message.content, message.safetyStatus || "SAFE", message.score || 100)
      .then(certString => {
        sendResponse({ success: true, certString: certString });
      })
      .catch(err => {
        sendResponse({ success: false, error: err.message });
      });
    return true;
  }

  if (message.action === "START_SCREEN_SNIP") {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0] && tabs[0].id) {
        chrome.tabs.sendMessage(tabs[0].id, { action: "START_SCREEN_SNIP" }, () => {
          if (chrome.runtime.lastError) {}
        });
      }
    });
    sendResponse({ success: true });
    return true;
  }

  if (message.action === "ANALYZE_URL" || message.action === "ANALYZE_PAYLOAD") {
    const payload = message.url || message.payload;
    const isFastLinkScan = message.isFastLinkScan !== undefined ? message.isFastLinkScan : (message.action === "ANALYZE_URL");
    analyzer.analyze(payload, { isFastLinkScan })
      .then(result => {
        HistoryManager.saveScan(result);
        sendResponse(result);
      })
      .catch(error => {
        console.warn("Analysis Error:", error);
        sendResponse({ overallScore: 50, riskScore: 50, safetyStatus: 'CAUTION', message: "Error during analysis." });
      });
    return true;
  }
  
  if (message.action === "QR_SCANNED") {
    const decodedPayload = message.payload;
    analyzer.analyze(decodedPayload).then(result => {
      HistoryManager.saveScan(result);
      if (sender.tab && sender.tab.id) {
        chrome.tabs.sendMessage(sender.tab.id, {
          action: "SHOW_THREAT_REPORT",
          payload: decodedPayload,
          analysis: result
        }, () => { if (chrome.runtime.lastError) {} });
      }
      sendResponse(result);
    });
    return true;
  }

  if (message.action === "DECODE_IMAGE_URL") {
    const imgUrl = message.url;
    fetch(imgUrl)
      .then(res => res.blob())
      .then(blob => createImageBitmap(blob))
      .then(async (bitmap) => {
        // 1. Primary: OffscreenCanvas + jsQR
        try {
          const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
          const ctx = canvas.getContext('2d', { willReadFrequently: true });
          ctx.drawImage(bitmap, 0, 0);
          const imgData = ctx.getImageData(0, 0, bitmap.width, bitmap.height);
          if (typeof jsQR !== 'undefined' && imgData && imgData.data) {
            const code = jsQR(imgData.data, imgData.width, imgData.height, { inversionAttempts: "dontInvert" }) ||
                         jsQR(imgData.data, imgData.width, imgData.height, { inversionAttempts: "onlyInvert" }) ||
                         jsQR(imgData.data, imgData.width, imgData.height, { inversionAttempts: "attemptBoth" });
            if (code && code.data) {
              return code.data;
            }
          }
        } catch (err) {}

        // 2. Fallback: Native BarcodeDetector
        if ('BarcodeDetector' in self) {
          try {
            const detector = new self.BarcodeDetector({ formats: ['qr_code'] });
            const barcodes = await detector.detect(bitmap);
            if (barcodes && barcodes.length > 0 && barcodes[0].rawValue) {
              return barcodes[0].rawValue;
            }
          } catch (err) {}
        }
        return null;
      })
      .then(async (decoded) => {
        if (decoded) {
          const result = await analyzer.analyze(decoded);
          await HistoryManager.saveScan(result);
          if (sender.tab && sender.tab.id) {
            chrome.tabs.sendMessage(sender.tab.id, {
              action: "SHOW_THREAT_REPORT",
              payload: decoded,
              analysis: result
            }, () => { if (chrome.runtime.lastError) {} });
          }
          sendResponse({ success: true, payload: decoded, analysis: result });
        } else {
          sendResponse({ success: false, error: "No QR code detected" });
        }
      })
      .catch(e => {
        sendResponse({ success: false, error: e.message });
      });
    return true;
  }

  if (message.action === "SUBMIT_COMMUNITY_REPORT") {
    CloudSync.reportWebsite(message.url, message.issue).then(success => {
      sendResponse({ success: success });
    });
    return true;
  }

  if (message.action === "GET_PAGE_THREAT_REPORT") {
    const targetUrl = message.url || (sender.tab && sender.tab.url) || '';
    const report = activeThreatReports.get(targetUrl) || null;
    sendResponse({ threatReport: report });
    return true;
  }

  if (message.action === "ALLOW_BYPASS" && message.url) {
    bypassedUrls.add(message.url);
    activeThreatReports.delete(message.url);
    setTimeout(() => {
      bypassedUrls.delete(message.url);
    }, 5 * 60 * 1000);
    sendResponse({ success: true });
    return true;
  }

  // ── AdBlock Plus / Shield Handlers (uBlock Origin & AdGuard MV3 Parity) ──
  if (message.action === "UPDATE_TAB_BLOCKED_COUNT") {
    const tabId = sender.tab ? sender.tab.id : null;
    const count = message.count || 0;
    if (tabId) {
      chrome.action.setBadgeText({
        text: count > 0 ? (count > 99 ? "99+" : String(count)) : "",
        tabId: tabId
      });
      chrome.action.setBadgeBackgroundColor({
        color: "#2563eb",
        tabId: tabId
      });
    }
    sendResponse({ success: true });
    return true;
  }

  if (message.action === "GET_ADBLOCK_STATE") {
    chrome.storage.local.get(['adShieldEnabled', 'whitelistedDomains', 'adsBlockedCount'], (res) => {
      const isShieldEnabled = res.adShieldEnabled !== false;
      const whitelistedDomains = res.whitelistedDomains || [];
      const totalBlocked = res.adsBlockedCount || 0;
      sendResponse({
        adShieldEnabled: isShieldEnabled,
        whitelistedDomains: whitelistedDomains,
        totalBlocked: totalBlocked
      });
    });
    return true;
  }

  function syncDnrWhitelist(domains) {
    if (!chrome.declarativeNetRequest || !chrome.declarativeNetRequest.getDynamicRules) return;
    chrome.declarativeNetRequest.getDynamicRules((existingRules) => {
      const existingIds = (existingRules || [])
        .filter(r => r.id >= 10000 && r.id < 20000)
        .map(r => r.id);

      const newRules = (domains || []).map((domain, index) => ({
        id: 10000 + index,
        priority: 100,
        action: { type: "allow" },
        condition: {
          initiatorDomains: [domain],
          resourceTypes: [
            "main_frame", "sub_frame", "stylesheet", "script", "image", "font", "object", "xmlhttprequest", "ping", "media", "websocket", "other"
          ]
        }
      }));

      chrome.declarativeNetRequest.updateDynamicRules({
        removeRuleIds: existingIds,
        addRules: newRules
      }, () => {
        if (chrome.runtime.lastError) {
          console.warn("Error updating dynamic DNR rules:", chrome.runtime.lastError);
        }
      });
    });
  }

  if (message.action === "SYNC_DNR_WHITELIST") {
    const list = message.domains || [];
    syncDnrWhitelist(list);
    sendResponse({ success: true, count: list.length });
    return true;
  }

  if (message.action === "TOGGLE_SITE_WHITELIST") {
    const domain = message.domain ? message.domain.toLowerCase().trim() : '';
    if (!domain) {
      sendResponse({ success: false, error: "No domain provided" });
      return true;
    }

    chrome.storage.local.get(['whitelistedDomains'], (res) => {
      let list = res.whitelistedDomains || [];
      const exists = list.includes(domain);
      if (exists) {
        list = list.filter(d => d !== domain);
      } else {
        list.push(domain);
      }
      chrome.storage.local.set({ whitelistedDomains: list }, () => {
        syncDnrWhitelist(list);
        sendResponse({ success: true, isWhitelisted: !exists, whitelistedDomains: list });
      });
    });
    return true;
  }

  if (message.action === "TOGGLE_GLOBAL_ADSHIELD") {
    chrome.storage.local.get(['adShieldEnabled'], (res) => {
      const nextState = !(res.adShieldEnabled !== false);
      chrome.storage.local.set({ adShieldEnabled: nextState }, () => {
        chrome.declarativeNetRequest.updateEnabledRulesets({
          disableRulesetIds: nextState ? [] : ['adblock_ads', 'adblock_trackers', 'adblock_malware'],
          enableRulesetIds: nextState ? ['adblock_ads', 'adblock_trackers', 'adblock_malware'] : []
        }, () => {
          sendResponse({ success: true, adShieldEnabled: nextState });
        });
      });
    });
    return true;
  }
  
  if (message.type === "DOM_ALERT") {
    // Immediate high-risk block request from content script
    const { riskScore, flags, threatType } = message.payload;
    const targetUrl = (sender.tab && sender.tab.url) || sender.url || '';
    if (sender.tab && sender.tab.id && riskScore >= 60 && !bypassedUrls.has(targetUrl)) {
      const threatObj = {
        rawContent: targetUrl,
        expandedUrl: targetUrl,
        safetyStatus: 'MALICIOUS',
        overallScore: Math.max(0, 100 - riskScore),
        riskScore: riskScore,
        threatType: threatType,
        flags: flags,
        siteCategory: '🔴 Threat Detected',
        siteSummary: flags.join('; ')
      };
      triggerThreatReportCard(sender.tab.id, targetUrl, threatObj);
    }
  }

  if (message.type === "DOM_CONTENT") {
    const targetUrl = (sender.tab && sender.tab.url) || sender.url || message.payload.url || '';
    CloudSync.analyzeUrlDeeply(message.payload.url, message.payload.text).then(result => {
      if (result && result.score >= 65 && sender.tab && sender.tab.id && !bypassedUrls.has(targetUrl)) {
        const threatObj = {
          rawContent: targetUrl,
          expandedUrl: targetUrl,
          safetyStatus: 'MALICIOUS',
          overallScore: Math.max(0, 100 - result.score),
          riskScore: result.score,
          threatType: result.threatType || 'MALICIOUS',
          flags: result.flags || ['Deep Cloud Threat Inspection Failed'],
          siteCategory: '🔴 High Risk Site',
          aiInsight: result.aiInsight
        };
        triggerThreatReportCard(sender.tab.id, targetUrl, threatObj);
      }
    });
  }
});

function triggerThreatReportCard(tabId, url, threatResult) {
  if (!url) return;
  activeThreatReports.set(url, threatResult);
  if (tabId) {
    chrome.tabs.sendMessage(tabId, {
      action: "SHOW_THREAT_REPORT",
      payload: url,
      analysis: threatResult,
      result: threatResult
    }, () => {
      // Ignore if tab not ready yet; GET_PAGE_THREAT_REPORT will retrieve it upon page load
      if (chrome.runtime.lastError) {}
    });
  }
}

function syncDnrWhitelist(domains = []) {
  try {
    if (!chrome.declarativeNetRequest || !chrome.declarativeNetRequest.getDynamicRules) return;
    chrome.declarativeNetRequest.getDynamicRules(existingRules => {
      const removeRuleIds = existingRules.map(r => r.id);
      const addRules = domains.map((domain, idx) => ({
        id: 9000 + idx,
        priority: 100,
        action: { type: "allow" },
        condition: {
          initiatorDomains: [domain],
          resourceTypes: ["main_frame", "sub_frame", "stylesheet", "script", "image", "font", "object", "xmlhttprequest", "ping", "media", "websocket", "other"]
        }
      }));
      chrome.declarativeNetRequest.updateDynamicRules({
        removeRuleIds: removeRuleIds,
        addRules: addRules
      });
    });
  } catch (e) {
    console.warn("Failed to sync DNR whitelist:", e);
  }
}

// Initialize dynamic whitelist on service worker startup
chrome.storage.local.get(['whitelistedDomains'], (res) => {
  if (res && res.whitelistedDomains) {
    syncDnrWhitelist(res.whitelistedDomains);
  }
});

const trustedSearchEngineDomains = [
  'google.com', 'google.co.in', 'google.co.uk', 'google.ca', 'google.de', 'google.fr', 'google.com.au',
  'bing.com', 'duckduckgo.com', 'yahoo.com', 'search.yahoo.com', 'ecosia.org', 'brave.com',
  'search.brave.com', 'yandex.com', 'yandex.ru', 'baidu.com', 'ask.com', 'startpage.com', 'qwant.com'
];

function isSearchEngineUrl(urlStr) {
  if (!urlStr) return false;
  try {
    const host = new URL(urlStr).hostname.toLowerCase();
    return trustedSearchEngineDomains.some(d => host === d || host.endsWith('.' + d));
  } catch (e) {
    return false;
  }
}

// 3. Link Guard (Pre-Navigation Threat Check)
chrome.webNavigation.onBeforeNavigate.addListener(async (details) => {
  if (details.frameId === 0) {
    if (!details.url || details.url.includes(chrome.runtime.id) || details.url.startsWith('chrome://') || details.url.startsWith('chrome-extension://')) {
      return;
    }

    if (bypassedUrls.has(details.url) || isSearchEngineUrl(details.url)) {
      return;
    }

    const result = await analyzer.analyze(details.url);
    await HistoryManager.saveScan(result);
    
    const { strictnessThreshold = 60 } = await chrome.storage.local.get('strictnessThreshold');

    if (result.safetyStatus === 'MALICIOUS' || result.riskScore >= strictnessThreshold) {
      activeThreatReports.set(details.url, result);
    }
  }
});

chrome.webNavigation.onCompleted.addListener((details) => {
  if (details.frameId === 0 && details.url) {
    if (isSearchEngineUrl(details.url)) {
      return;
    }
    const report = activeThreatReports.get(details.url);
    if (report && !bypassedUrls.has(details.url)) {
      chrome.tabs.sendMessage(details.tabId, {
        action: "SHOW_THREAT_REPORT",
        payload: details.url,
        analysis: report,
        result: report
      }, () => {
        if (chrome.runtime.lastError) {}
      });
    }
  }
});

// ── 4. Download Interceptor & Binary Malware Guard ───────────────────
if (chrome.downloads) {
  const dangerousExtensions = ['.exe', '.scr', '.bat', '.vbs', '.iso', '.msi', '.apk', '.cmd', '.ps1', '.reg', '.dll', '.hta'];
  const safeSoftwareCdns = ['github.com', 'microsoft.com', 'apple.com', 'google.com', 'mozilla.org', 'videolan.org', 'python.org', 'nodejs.org'];
  const notifiedDownloads = new Set();

  function inspectAndInterceptDownload(downloadItem, resolvedFilename = null) {
    const downloadId = downloadItem.id;
    if (notifiedDownloads.has(downloadId)) return;

    const url = downloadItem.url || downloadItem.finalUrl || '';
    const filename = (resolvedFilename || downloadItem.filename || '').toLowerCase();
    
    let urlPath = '';
    try {
      urlPath = new URL(url).pathname.toLowerCase();
    } catch (e) {
      urlPath = url.toLowerCase();
    }

    const isDangerousExt = dangerousExtensions.some(ext => 
      filename.endsWith(ext) || 
      urlPath.endsWith(ext) || 
      url.toLowerCase().includes(ext + '?') || 
      url.toLowerCase().endsWith(ext)
    );

    if (!isDangerousExt) return;

    try {
      const downloadUrlObj = new URL(url);
      const domain = downloadUrlObj.hostname.toLowerCase();

      if (safeSoftwareCdns.some(safe => domain === safe || domain.endsWith('.' + safe))) {
        return; // Allow official verified software downloads
      }

      notifiedDownloads.add(downloadId);

      // Pause download immediately for inspection
      chrome.downloads.pause(downloadId, () => {
        if (chrome.runtime.lastError) {}
      });

      const displayFilename = (filename || urlPath).split(/[\\/]/).pop() || 'Executable file';

      // Trigger notification
      if (chrome.notifications && chrome.notifications.create) {
        chrome.notifications.create(`threatlens-dl-${downloadId}`, {
          type: 'basic',
          iconUrl: 'assets/ic_threatlens_shield.png',
          title: '⚠️ ThreatLens Download Guard',
          message: `Potentially high-risk binary paused: ${displayFilename} from ${domain}.`,
          priority: 2,
          requireInteraction: true,
          buttons: [
            { title: 'Resume Download' },
            { title: 'Cancel & Delete' }
          ]
        });
      }
    } catch (e) {}
  }

  if (chrome.downloads.onCreated) {
    chrome.downloads.onCreated.addListener((downloadItem) => {
      inspectAndInterceptDownload(downloadItem);
    });
  }

  if (chrome.downloads.onDeterminingFilename) {
    chrome.downloads.onDeterminingFilename.addListener((item, suggest) => {
      inspectAndInterceptDownload(item, item.filename);
      suggest(); // Always allow filename determination to proceed
    });
  }

  if (chrome.notifications && chrome.notifications.onButtonClicked) {
    chrome.notifications.onButtonClicked.addListener((notifId, buttonIndex) => {
      if (notifId.startsWith('threatlens-dl-')) {
        const downloadId = parseInt(notifId.replace('threatlens-dl-', ''), 10);
        if (buttonIndex === 0) {
          chrome.downloads.resume(downloadId);
        } else {
          chrome.downloads.cancel(downloadId);
        }
        chrome.notifications.clear(notifId);
      }
    });
  }
}
