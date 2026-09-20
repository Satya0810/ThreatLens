// HeuristicsEngine.js
// Port of ThreatLens Android HeuristicChecker.kt & ThreatAnalyzer.kt heuristics

export class HeuristicsEngine {
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

      // 1.5 Whitelist Trusted Search Engines (Google, Bing, etc.) - do not flag search queries
      const trustedSearchEngineDomains = [
        'google.com', 'google.co.in', 'google.co.uk', 'google.ca', 'google.de', 'google.fr', 'google.com.au',
        'bing.com', 'duckduckgo.com', 'yahoo.com', 'search.yahoo.com', 'ecosia.org', 'brave.com',
        'search.brave.com', 'yandex.com', 'yandex.ru', 'baidu.com', 'ask.com', 'startpage.com', 'qwant.com'
      ];
      if (trustedSearchEngineDomains.some(d => hostname === d || hostname.endsWith('.' + d))) {
        return {
          threatScore: 0,
          trustScore: 100,
          flags: ["Verified Search Engine"],
          threatType: 'SAFE'
        };
      }

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
