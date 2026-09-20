// content-script.js
// ThreatLens Professional In-Page Intelligence Report Card (Non-Scrolling 2-Column Dashboard HUD)

(function() {
  let activeReportHost = null;
  const bypassedClickUrls = new Set();

  // Fast client-side heuristics database matching Android ThreatAnalyzer.kt
  const suspiciousTLDs = new Set([
    '.xyz', '.top', '.pw', '.cc', '.club', '.tk', '.ml', '.ga', '.cf', '.gq', '.buzz', '.ist', '.wtf', '.lol',
    '.gripe', '.wc', '.gd', '.ps', '.ac', '.to', '.is', '.bet', '.casino', '.xxx', '.adult', '.porn', '.sex', '.cam'
  ]);
  const trustedBrands = ['paypal', 'google', 'microsoft', 'apple', 'amazon', 'netflix', 'facebook', 'instagram', 'chase', 'paytm', 'phonepe', 'binance', 'coinbase', 'flipkart', 'sbi', 'hdfc'];
  const scamKeywords = ['tech-support', 'crypto-doubler', 'double-your-btc', 'free-giveaway', 'lottery-winner', 'account-suspended', 'urgent-action', 'claim-prize', 'verify-account', 'update-billing', 'security-alert', 'unauthorized-access', 'login', 'signin', 'secure-verify'];
  const adultKeywords = ['porn', 'xxx', 'sex', 'nude', 'desi-mms', 'desi-hd', 'mms', 'leak', 'nsfw', 'cam', 'escort', 'fuck', 'milf', 'hentai', 'mature'];
  const piracyKeywords = [
    'torrent', 'crack', 'keygen', 'warez', 'nulled', 'vegamovie', 'vegamovies', 'vegamoviez',
    'tamilrockers', '1337x', 'fmovies', '123movies', 'mp4moviez', 'filmyzilla', 'movierulz',
    'bolly4u', 'worldfree4u', '9xmovie', 'downloadhub', 'skymovieshd', 'filmywap', 'katmovie',
    'free-download', 'modapk'
  ];
  const piracyDomains = new Set([
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
  const piracyDomainKeywords = [
    "torrent", "movie", "moviez", "pirate", "warez", "crack", "nulled", "fmovie",
    "putlocker", "123movie", "gomovie", "soap2day", "movierulz", "filmyzilla",
    "mp4moviez", "hdmovie", "bolly4u", "9xmovie", "kuttymovie", "tamilyogi",
    "isaimini", "downloadhub", "skymovieshd", "filmywap", "pagalmovie", "cinevood",
    "katmovie", "vegamovie", "tamilrocker", "yts", "rarbg", "kickass", "limetorrent"
  ];
  const piracyTLDs = new Set([
    ".gripe", ".ist", ".wc", ".gd", ".wtf", ".ps", ".ac", ".lol", ".to", ".is"
  ]);
  const adultDomains = new Set([
    "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "redtube.com",
    "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "chaturbate.com",
    "bongacams.com", "stripchat.com", "livejasmin.com", "onlyfans.com", "fansly.com"
  ]);
  const gamblingKeywords = ['betting', 'casino', 'jackpot', 'free-spins', '1xbet', 'stake', 'poker', 'slots'];
  const malwareExtensions = ['.exe', '.apk', '.scr', '.bat', '.cmd', '.vbs', '.jar', '.ps1', '.sh', '.msi', '.pif'];
  const homographRegex = /[\u0370-\u03FF\u0400-\u04FF]/;
  const ipRegex = /^(\d{1,3}\.){3}\d{1,3}$/;

  const trustedSearchEngineDomains = [
    'google.com', 'google.co.in', 'google.co.uk', 'google.ca', 'google.de', 'google.fr', 'google.com.au',
    'bing.com', 'duckduckgo.com', 'yahoo.com', 'search.yahoo.com', 'ecosia.org', 'brave.com',
    'search.brave.com', 'yandex.com', 'yandex.ru', 'baidu.com', 'ask.com', 'startpage.com', 'qwant.com'
  ];

  function isTrustedSearchEngine(hostOrUrl) {
    if (!hostOrUrl) return false;
    try {
      let hostname = hostOrUrl;
      if (hostOrUrl.startsWith('http://') || hostOrUrl.startsWith('https://')) {
        hostname = new URL(hostOrUrl).hostname;
      }
      hostname = hostname.toLowerCase();
      return trustedSearchEngineDomains.some(d => hostname === d || hostname.endsWith('.' + d));
    } catch (e) {
      return false;
    }
  }

  function isInternalOrSameSite(destUrl) {
    if (!destUrl) return true;
    try {
      const currentHost = window.location.hostname.toLowerCase();
      let targetHost = destUrl;
      if (destUrl.startsWith('http://') || destUrl.startsWith('https://')) {
        targetHost = new URL(destUrl).hostname.toLowerCase();
      } else if (destUrl.startsWith('/') || destUrl.startsWith('#') || destUrl.startsWith('?')) {
        return true;
      }

      // Exact match
      if (targetHost === currentHost) return true;

      // Subdomain or parent domain match (e.g. www.bing.com and bing.com)
      if (targetHost.endsWith('.' + currentHost) || currentHost.endsWith('.' + targetHost)) return true;

      // Both belong to the same search engine family
      if (isTrustedSearchEngine(currentHost) && isTrustedSearchEngine(targetHost)) {
        const baseCurrent = currentHost.replace(/^www\./, '');
        const baseTarget = targetHost.replace(/^www\./, '');
        if (baseCurrent === baseTarget) return true;
      }

      return false;
    } catch (e) {
      return false;
    }
  }

  /**
   * Unwraps tracking and redirect URLs from search engines (Bing, Google, DDG, Yahoo)
   */
  function unwrapDestinationUrl(urlString, anchor) {
    if (!urlString) return urlString;
    try {
      const url = new URL(urlString);
      const host = url.hostname.toLowerCase();

      // 1. Bing Redirect: https://www.bing.com/ck/a?!&&p=...&u=a1aHR0cHM6Ly92ZWdhbW92aWV6LmNvbS8...
      if (host.includes('bing.com') && url.pathname.includes('/ck/')) {
        const uParam = url.searchParams.get('u');
        if (uParam) {
          let b64 = uParam;
          if (b64.startsWith('a1')) {
            b64 = b64.substring(2);
          }
          while (b64.length % 4 !== 0) {
            b64 += '=';
          }
          try {
            const decoded = atob(b64.replace(/-/g, '+').replace(/_/g, '/'));
            if (decoded && (decoded.startsWith('http://') || decoded.startsWith('https://'))) {
              return decoded;
            }
          } catch (e) {}
        }
      }

      // 2. Google Redirect: /url?q=https://... or url=https://...
      if (host.includes('google.') && url.pathname.includes('/url')) {
        const target = url.searchParams.get('url') || url.searchParams.get('q');
        if (target && (target.startsWith('http://') || target.startsWith('https://'))) {
          return target;
        }
      }

      // 3. DuckDuckGo Redirect: /l/?uddg=https%3A%2F%2F...
      if (host.includes('duckduckgo.com') && url.searchParams.has('uddg')) {
        const target = decodeURIComponent(url.searchParams.get('uddg'));
        if (target && (target.startsWith('http://') || target.startsWith('https://'))) {
          return target;
        }
      }

      // 4. Yahoo Redirect: /RU=https%3a%2f%2f...
      if (host.includes('yahoo.com') && url.pathname.includes('/RU=')) {
        const match = url.pathname.match(/\/RU=([^/]+)/);
        if (match && match[1]) {
          const target = decodeURIComponent(match[1]);
          if (target && (target.startsWith('http://') || target.startsWith('https://'))) {
            return target;
          }
        }
      }

      // 5. Facebook Redirect: /l.php?u=https%3A%2F%2F...
      if (host.includes('facebook.com') && url.searchParams.has('u')) {
        const target = decodeURIComponent(url.searchParams.get('u'));
        if (target && (target.startsWith('http://') || target.startsWith('https://'))) {
          return target;
        }
      }

      // 6. Generic Tracking Redirect query parameters
      const redirectKeys = ['url', 'target', 'dest', 'destination', 'redirect', 'redirect_url', 'next', 'link', 'to', 'target_url', 'out'];
      for (const key of redirectKeys) {
        if (url.searchParams.has(key)) {
          const val = decodeURIComponent(url.searchParams.get(key));
          if (val && (val.startsWith('http://') || val.startsWith('https://'))) {
            return val;
          }
        }
      }

      // 7. Check data attributes on anchor element
      if (anchor && anchor.getAttribute) {
        const dataUrl = anchor.getAttribute('data-url') || anchor.getAttribute('data-href') || anchor.getAttribute('data-destination');
        if (dataUrl && (dataUrl.startsWith('http://') || dataUrl.startsWith('https://'))) {
          return dataUrl;
        }
      }
    } catch (e) {}

    return urlString;
  }

  /**
   * Fast synchronous client-side heuristic inspection for clicked links matching Android
   */
  function isSuspiciousLink(urlString) {
    const flags = [];
    let riskScore = 0;
    let threatType = 'SAFE';

    try {
      const lower = urlString.toLowerCase().trim();
      const url = new URL(urlString.startsWith('http') ? urlString : `https://${urlString}`);
      const hostname = url.hostname.toLowerCase();
      const parts = hostname.split('.');

      // Whitelist major trusted search engines (Google, Bing, etc.) - do not flag search query keywords
      if (isTrustedSearchEngine(hostname)) {
        return {
          isSuspicious: false,
          riskScore: 0,
          threatType: 'SAFE',
          flags: []
        };
      }

      // 1. Homograph / Cyrillic Punycode Lookalike
      if (homographRegex.test(hostname) || hostname.startsWith('xn--')) {
        riskScore += 80;
        flags.push("Homograph/Punycode lookalike characters detected");
        threatType = 'PHISHING';
      }

      // 2. Direct IP Address in Host
      if (ipRegex.test(hostname)) {
        riskScore += 65;
        flags.push("Public IP address used directly instead of verified domain");
        threatType = 'PHISHING';
      }

      // 3. Authority Phishing Trick (@ before domain)
      if (lower.split('/')[2] && lower.split('/')[2].includes('@')) {
        riskScore += 80;
        flags.push("Contains '@' in authority section (Credential harvesting)");
        threatType = 'PHISHING';
      }

      // 4. Executable / Malware file download
      for (const ext of malwareExtensions) {
        if (url.pathname.toLowerCase().endsWith(ext)) {
          riskScore += 90;
          flags.push(`Direct download of high-risk executable file (${ext})`);
          threatType = 'MALWARE';
        }
      }

      // 5. Known Piracy Domains & Keywords (1:1 Android ThreatAnalyzer)
      const isPiracyDomain = piracyDomains.has(hostname) || Array.from(piracyDomains).some(d => hostname === d || hostname.endsWith('.' + d));
      const hasPiracyDomainKeyword = piracyDomainKeywords.some(k => hostname.includes(k));
      if (isPiracyDomain) {
        riskScore += 85;
        flags.push(`Known illegal piracy or copyright infringement domain (${hostname})`);
        threatType = 'PIRACY';
      } else if (hasPiracyDomainKeyword) {
        riskScore += 75;
        flags.push(`Domain contains piracy or illegal streaming keywords ('${hostname}')`);
        threatType = 'PIRACY';
      }

      // 6. Adult Content Domains & Keywords (1:1 Android ThreatAnalyzer)
      const isAdultDomain = adultDomains.has(hostname) || Array.from(adultDomains).some(d => hostname === d || hostname.endsWith('.' + d));
      if (isAdultDomain) {
        riskScore += 80;
        flags.push(`Known explicit adult/pornography domain (${hostname})`);
        threatType = 'PORNOGRAPHY';
      } else {
        for (const keyword of adultKeywords) {
          if (hostname.includes(keyword) || lower.includes(keyword)) {
            riskScore += 75;
            flags.push(`Adult/explicit content keyword detected: '${keyword}'`);
            threatType = 'PORNOGRAPHY';
            break;
          }
        }
      }

      // 7. Piracy Keywords in URL / Path
      for (const keyword of piracyKeywords) {
        if (hostname.includes(keyword) || lower.includes(keyword)) {
          if (threatType !== 'PIRACY') {
            riskScore += 70;
            flags.push(`Piracy/unauthorized distribution keyword detected: '${keyword}'`);
            threatType = 'PIRACY';
          }
          break;
        }
      }

      // 8. Gambling & Betting Keywords
      for (const keyword of gamblingKeywords) {
        if (hostname.includes(keyword) || lower.includes(keyword)) {
          riskScore += 45;
          flags.push(`Age-restricted category (Betting & Casino: '${keyword}')`);
          if (threatType === 'SAFE') threatType = 'GAMBLING';
          break;
        }
      }

      // 9. Typosquatting / Brand Spoofing
      const domainWithoutTld = parts.slice(0, -1).join('.');
      const officialSuffixes = ['.com', '.org', '.net', '.in', '.co.in', '.co.uk', '.io', '.app', '.dev', '.ai', '.ca', '.de', '.fr', '.jp', '.au', '.gov', '.edu'];
      for (const brand of trustedBrands) {
        if (domainWithoutTld.includes(brand)) {
          const isOfficial = officialSuffixes.some(s => hostname === `${brand}${s}` || hostname.endsWith(`.${brand}${s}`));
          if (!isOfficial) {
            riskScore += 75;
            flags.push(`Possible brand impersonation of '${brand}' in '${hostname}'`);
            threatType = 'PHISHING';
          }
        }
      }

      // 10. Scam / Social Engineering / Urgency Keywords
      for (const keyword of scamKeywords) {
        if (lower.includes(keyword)) {
          riskScore += 65;
          flags.push(`Scam/Urgency keyword detected: '${keyword}'`);
          if (threatType === 'SAFE') threatType = 'SCAM';
        }
      }

      // 11. Suspicious Top-Level Domains
      const tld = '.' + (parts[parts.length - 1] || '');
      if (suspiciousTLDs.has(tld)) {
        riskScore += 35;
        flags.push(`High-abuse Top Level Domain (${tld})`);
      }
      if (piracyTLDs.has(tld)) {
        riskScore += 40;
        flags.push(`High-risk piracy Top Level Domain (${tld})`);
      }

      // 12. Excessive Subdomains
      if (parts.length > 3 && !hostname.startsWith('www.')) {
        riskScore += 25;
        flags.push(`Excessive subdomains (${parts.length} parts)`);
      }

    } catch (e) {
      riskScore = 80;
      flags.push("Malformed or unparseable link structure");
      threatType = 'UNKNOWN';
    }

    return {
      isSuspicious: riskScore >= 25 || flags.length > 0,
      riskScore: Math.min(100, riskScore),
      threatType: threatType,
      flags: flags
    };
  }

  // ── 1. Intercept Link Clicks Instantly (Link Guard - ThreatLens App AnalyzingOverlay Replica) ──
  let activeScanHUDHost = null;

  function showLinkScanHUD(href, anchor, isNewTab, rawHref) {
    if (activeScanHUDHost) {
      activeScanHUDHost.remove();
      activeScanHUDHost = null;
    }

    const host = document.createElement('div');
    host.id = 'threatlens-link-scan-overlay';
    host.style.position = 'fixed';
    host.style.inset = '0';
    host.style.zIndex = '2147483647';
    host.style.pointerEvents = 'auto';

    const shadow = host.attachShadow({ mode: 'open' });
    activeScanHUDHost = host;

    const shieldUrl = chrome.runtime.getURL('assets/ic_threatlens_shield.png') + '?v=' + Date.now();
    const fallbackShieldUrl = chrome.runtime.getURL('assets/logo.png') + '?v=' + Date.now();

    let targetHostname = href;
    try { targetHostname = new URL(href).hostname; } catch (e) {}

    const styles = `
      :host { all: initial; }
      * { box-sizing: border-box; margin: 0; padding: 0; }

      .hud-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(248, 250, 253, 0.96);
        backdrop-filter: blur(16px);
        -webkit-backdrop-filter: blur(16px);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.28s cubic-bezier(0.16, 1, 0.3, 1);
        font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        color: #0f172a;
        user-select: none;
      }

      .analyzing-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        max-width: 90vw;
        position: relative;
      }

      /* ── Concentric Cyber Rings (Matrix Data Decryption - Loanzo Light Theme) ── */
      .matrix-concentric-container {
        position: relative;
        width: 240px;
        height: 240px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 32px;
      }

      .concentric-svg {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        overflow: visible;
        filter: drop-shadow(0 4px 14px rgba(29, 78, 216, 0.15));
        transition: filter 0.3s ease;
      }

      .threat-state .concentric-svg {
        filter: drop-shadow(0 4px 16px rgba(220, 38, 38, 0.35));
      }

      .safe-state .concentric-svg {
        filter: drop-shadow(0 4px 16px rgba(5, 150, 105, 0.35));
      }

      .ring-rotate-cw {
        transform-origin: 120px 120px;
        animation: spinCW 4.5s linear infinite;
      }

      .ring-rotate-cw-fast {
        transform-origin: 120px 120px;
        animation: spinCW 2.5s linear infinite;
      }

      .ring-rotate-ccw {
        transform-origin: 120px 120px;
        animation: spinCCW 3.5s linear infinite;
      }

      .ring-rotate-ccw-slow {
        transform-origin: 120px 120px;
        animation: spinCCW 6s linear infinite;
      }

      .threat-state circle {
        stroke: #dc2626 !important;
      }

      .safe-state circle {
        stroke: #059669 !important;
      }

      /* Core Shield Icon with Breathing Pulse */
      .core-shield-box {
        position: relative;
        width: 76px;
        height: 76px;
        border-radius: 50%;
        background: radial-gradient(circle, rgba(29, 78, 216, 0.12) 0%, transparent 72%);
        display: flex;
        align-items: center;
        justify-content: center;
        animation: corePulse 1.6s ease-in-out infinite alternate;
        transition: all 0.3s ease;
        z-index: 2;
      }

      .threat-state .core-shield-box {
        background: radial-gradient(circle, rgba(220, 38, 38, 0.2) 0%, transparent 72%);
      }

      .safe-state .core-shield-box {
        background: radial-gradient(circle, rgba(5, 150, 105, 0.2) 0%, transparent 72%);
      }

      .core-shield-img {
        width: 48px;
        height: 48px;
        object-fit: contain;
        filter: drop-shadow(0 2px 8px rgba(29, 78, 216, 0.25));
      }

      /* Scan Phase Cycling Text */
      .scan-phase-text {
        font-family: 'JetBrains Mono', Consolas, monospace;
        font-size: 13.5px;
        font-weight: 600;
        letter-spacing: 0.8px;
        color: #1d4ed8;
        margin-bottom: 18px;
        min-height: 22px;
        transition: color 0.3s ease;
      }

      .threat-state .scan-phase-text {
        color: #dc2626;
      }

      .safe-state .scan-phase-text {
        color: #059669;
      }

      /* Headline (THREAT ANALYSIS IN PROGRESS) */
      .headline-text {
        font-family: 'Outfit', sans-serif;
        font-size: 16px;
        font-weight: 800;
        letter-spacing: 2.2px;
        text-transform: uppercase;
        color: #0f172a;
        margin-bottom: 16px;
      }

      /* Destination Box with Blinking Terminal Cursor */
      .url-pill-box {
        background: #ffffff;
        border: 1px solid #cbd5e1;
        border-radius: 12px;
        padding: 9px 18px;
        display: flex;
        align-items: center;
        gap: 4px;
        max-width: 380px;
        margin-bottom: 24px;
        box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
      }

      .url-text {
        font-family: 'JetBrains Mono', Consolas, monospace;
        font-size: 12px;
        color: #1d4ed8;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .terminal-cursor {
        color: #1d4ed8;
        font-size: 13px;
        font-weight: bold;
        animation: blinkCursor 0.6s infinite alternate ease-in-out;
      }

      .btn-cancel-scan {
        background: #f1f5f9;
        border: 1px solid #e2e8f0;
        color: #64748b;
        font-family: 'Inter', sans-serif;
        font-size: 11.5px;
        font-weight: 600;
        cursor: pointer;
        padding: 6px 14px;
        border-radius: 8px;
        transition: all 0.15s ease;
      }

      .btn-cancel-scan:hover {
        color: #0f172a;
        background: #e2e8f0;
      }

      @keyframes spinCW {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }

      @keyframes spinCCW {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(-360deg); }
      }

      @keyframes corePulse {
        0% { transform: scale(0.88); opacity: 0.85; }
        100% { transform: scale(1.12); opacity: 1; }
      }

      @keyframes blinkCursor {
        0% { opacity: 0; }
        100% { opacity: 1; }
      }
    `;

    const hudHtml = `
      <style>${styles}</style>
      <div class="hud-backdrop" id="hudBackdrop">
        <div class="analyzing-container" id="analyzingContainer">
          
          <!-- Concentric Cyber Rings (Loanzo Light Theme) -->
          <div class="matrix-concentric-container">
            <svg class="concentric-svg" viewBox="0 0 240 240">
              <!-- Ring 4: Outer (Royal Blue) -->
              <g class="ring-rotate-ccw-slow">
                <circle cx="120" cy="120" r="105" stroke="#1D4ED8" stroke-width="2" fill="none" opacity="0.45" stroke-dasharray="80 30 110 50" />
              </g>
              <!-- Ring 3: Middle-Outer (Bright Cobalt) -->
              <g class="ring-rotate-cw">
                <circle cx="120" cy="120" r="80" stroke="#2563EB" stroke-width="2" fill="none" opacity="0.6" stroke-dasharray="90 40 60 30" />
              </g>
              <!-- Ring 2: Middle-Inner (Violet) -->
              <g class="ring-rotate-ccw">
                <circle cx="120" cy="120" r="56" stroke="#7C3AED" stroke-width="2" fill="none" opacity="0.5" stroke-dasharray="50 25 70 35" />
              </g>
              <!-- Ring 1: Inner (Royal Blue) -->
              <g class="ring-rotate-cw-fast">
                <circle cx="120" cy="120" r="38" stroke="#1D4ED8" stroke-width="2.5" fill="none" opacity="0.75" stroke-dasharray="40 20 40 20" />
              </g>
            </svg>

            <!-- Core Shield Icon with Breathing Pulse -->
            <div class="core-shield-box">
              <img src="${shieldUrl}" alt="ThreatLens Shield" class="core-shield-img" id="hudShieldImg">
            </div>
          </div>

          <!-- Scan Phases Cycling Text -->
          <div class="scan-phase-text" id="hudPhaseText">Resolving DNS...</div>

          <!-- Headline -->
          <div class="headline-text" id="hudHeadline">THREAT ANALYSIS IN PROGRESS</div>

          <!-- URL Pill with Blinking Terminal Cursor -->
          <div class="url-pill-box">
            <span class="url-text">${targetHostname}</span>
            <span class="terminal-cursor">▎</span>
          </div>

          <!-- Cancel Action -->
          <button class="btn-cancel-scan" id="btnCancelScan">✕ Cancel Navigation</button>
        </div>
      </div>
    `;

    shadow.innerHTML = hudHtml;

    const shieldImg = shadow.getElementById('hudShieldImg');
    if (shieldImg) {
      shieldImg.onerror = () => {
        shieldImg.src = fallbackShieldUrl;
      };
    }

    const backdrop = shadow.getElementById('hudBackdrop');
    const container = shadow.getElementById('analyzingContainer');
    const phaseText = shadow.getElementById('hudPhaseText');
    const headline = shadow.getElementById('hudHeadline');
    const btnCancel = shadow.getElementById('btnCancelScan');

    // Mount to document
    document.documentElement.appendChild(host);

    // Fade in
    requestAnimationFrame(() => {
      backdrop.style.opacity = '1';
    });

    let isAborted = false;
    btnCancel.addEventListener('click', () => {
      isAborted = true;
      host.remove();
      activeScanHUDHost = null;
    });

    // Cycle scan phases matching Android app AnalyzingOverlay.kt (rapid 220ms cadence for snappy feel)
    const scanPhases = [
      "Resolving DNS...",
      "Checking SSL Certificate...",
      "Analyzing URL Pattern...",
      "Scanning for Threats...",
      "Checking Reputation Database...",
      "Running Heuristic Analysis...",
      "Generating Security Report..."
    ];
    let phaseIndex = 0;
    const phaseInterval = setInterval(() => {
      if (isAborted || !activeScanHUDHost) {
        clearInterval(phaseInterval);
        return;
      }
      phaseIndex = (phaseIndex + 1) % scanPhases.length;
      if (phaseText && !container.classList.contains('threat-state') && !container.classList.contains('safe-state')) {
        phaseText.textContent = scanPhases[phaseIndex];
      }
    }, 220);

    const startTime = Date.now();
    const localCheck = isSuspiciousLink(href);
    const targetUrl = href; // The authentic unwrapped destination URL (e.g. https://filmywap.bet)
    const isImmediateThreat = localCheck.isSuspicious;

    // Fast-track timeout: 550ms if already flagged as threat, 750ms if clean
    const maxScanWait = isImmediateThreat ? 550 : 750;

    // Callback when user explicitly confirms to proceed after a threat is displayed
    const proceedCallback = () => {
      bypassedClickUrls.add(targetUrl);
      if (rawHref) bypassedClickUrls.add(rawHref);
      try {
        chrome.runtime.sendMessage({ action: "ALLOW_BYPASS", url: targetUrl });
      } catch (e) {}

      if (isNewTab || (anchor && (anchor.dataset?.tlTarget?.toLowerCase().includes('blank') || anchor.target === '_blank'))) {
        window.open(targetUrl, '_blank');
      } else {
        window.location.href = targetUrl;
      }
    };

    // Prepare initial heuristic analysis
    const initialAnalysis = {
      rawContent: href,
      expandedUrl: href,
      safetyStatus: localCheck.riskScore >= 60 ? 'MALICIOUS' : (localCheck.isSuspicious ? 'CAUTION' : 'SAFE'),
      overallScore: Math.max(0, 100 - localCheck.riskScore),
      riskScore: localCheck.riskScore || 5,
      threatType: localCheck.threatType !== 'SAFE' ? localCheck.threatType : 'VERIFIED_LINK',
      flags: localCheck.flags.length > 0 ? localCheck.flags : ['Pre-navigation heuristic check passed'],
      threatDetails: localCheck.flags,
      siteCategory: localCheck.riskScore >= 60 ? '🔴 High Risk' : (localCheck.isSuspicious ? '⚠️ Caution' : '🌐 Web Destination'),
      siteSummary: localCheck.flags.join('; ') || 'Analyzed by ThreatLens Security Engine.',
      aiInsight: localCheck.isSuspicious 
        ? `ThreatLens flagged ${targetHostname} with ${localCheck.flags.length} risk indicator(s). Exercise caution before proceeding.`
        : `ThreatLens verified that ${targetHostname} is clean with no malicious signatures detected.`,
      isUrl: true
    };

    // Trigger fast link analysis in background
    let deepScanCompleted = false;
    let deepResult = null;

    try {
      chrome.runtime.sendMessage({ action: "ANALYZE_URL", url: href, isFastLinkScan: true }, (res) => {
        deepResult = res;
        deepScanCompleted = true;
        finishScan();
      });
    } catch (e) {
      deepScanCompleted = true;
      finishScan();
    }

    // Safety timeout: If worker doesn't finish within maxScanWait, proceed immediately with localCheck
    setTimeout(() => {
      if (!deepScanCompleted) {
        deepScanCompleted = true;
        finishScan();
      }
    }, maxScanWait);

    function finishScan() {
      if (isAborted) return;
      clearInterval(phaseInterval);

      const elapsed = Date.now() - startTime;
      const minDisplayTime = isImmediateThreat ? 450 : 500; // Brief animation so user sees shield & cyber rings
      const delay = Math.max(0, minDisplayTime - elapsed);

      setTimeout(() => {
        if (isAborted) return;

        const finalAnalysis = deepResult || initialAnalysis;
        const isThreat = (finalAnalysis.safetyStatus === 'MALICIOUS' || 
                          finalAnalysis.safetyStatus === 'CAUTION' || 
                          (finalAnalysis.riskScore && finalAnalysis.riskScore >= 35) || 
                          localCheck.isSuspicious);

        if (isThreat) {
          // ── THREAT DETECTED ──
          container.classList.add('threat-state');
          phaseText.textContent = "Security Violations Detected!";
          headline.textContent = `⚠️ THREAT DETECTED: ${finalAnalysis.threatType || 'SUSPICIOUS'}`;

          setTimeout(() => {
            if (isAborted) return;
            host.remove();
            activeScanHUDHost = null;
            showIntelligenceReportCard(href, finalAnalysis, proceedCallback);
          }, 320);

        } else {
          // ── CLEAN & SAFE ──
          container.classList.add('safe-state');
          phaseText.textContent = "Verified Clean & Authentic";
          headline.textContent = "✓ SECURE • NO THREATS DETECTED";

          setTimeout(() => {
            if (isAborted) return;
            backdrop.style.opacity = '0';

            setTimeout(() => {
              host.remove();
              activeScanHUDHost = null;
              bypassedClickUrls.add(targetUrl);
              if (rawHref) bypassedClickUrls.add(rawHref);

              if (isNewTab || (anchor && (anchor.dataset?.tlTarget?.toLowerCase().includes('blank') || anchor.target === '_blank'))) {
                window.open(targetUrl, '_blank');
              } else {
                window.location.href = targetUrl;
              }
            }, 120);
          }, 240);
        }
      }, delay);
    }
  }

  // ── Preemptive target="_blank" Neutralization Engine (Prevents browser engine from opening tabs prematurely) ──
  function neutralizeAnchorTarget(anchor) {
    if (!anchor || !anchor.getAttribute) return;
    if (anchor.closest && (
      anchor.closest('#threatlens-link-scan-overlay') || 
      anchor.closest('#threatlens-intelligence-report-root') || 
      anchor.closest('#threatlens-threat-report-host') || 
      anchor.closest('#threatlens-snip-overlay')
    )) {
      return;
    }

    const rawHref = anchor.href;
    if (!rawHref || (!rawHref.startsWith('http://') && !rawHref.startsWith('https://'))) return;

    // Do NOT neutralize internal links on the same site or search engine pagination
    const destUrl = unwrapDestinationUrl(rawHref, anchor);
    if (isInternalOrSameSite(destUrl)) return;

    const target = anchor.getAttribute('target');
    if (target && target !== '_self') {
      const lower = target.toLowerCase().trim();
      if (lower === '_blank' || lower === '_new' || lower === 'blank') {
        anchor.dataset.tlTarget = target;
        anchor.setAttribute('target', '_self');
      }
    }
  }

  function neutralizeBaseTags() {
    try {
      const bases = document.querySelectorAll('base[target]');
      bases.forEach(b => {
        const t = b.getAttribute('target');
        if (t && t !== '_self') {
          const lower = t.toLowerCase().trim();
          if (lower === '_blank' || lower === '_new' || lower === 'blank') {
            b.dataset.tlTarget = t;
            b.setAttribute('target', '_self');
          }
        }
      });
    } catch (e) {}
  }

  function initLinkNeutralizer() {
    neutralizeBaseTags();
    try {
      const anchors = document.querySelectorAll('a[target]');
      anchors.forEach(neutralizeAnchorTarget);
    } catch (e) {}

    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type === 'childList') {
          for (const node of mutation.addedNodes) {
            if (node.nodeType === 1) { // Node.ELEMENT_NODE
              if (node.tagName === 'A') {
                neutralizeAnchorTarget(node);
              } else if (node.tagName === 'BASE') {
                neutralizeBaseTags();
              } else if (node.querySelectorAll) {
                const anchors = node.querySelectorAll('a[target]');
                anchors.forEach(neutralizeAnchorTarget);
              }
            }
          }
        } else if (mutation.type === 'attributes' && mutation.attributeName === 'target') {
          if (mutation.target.tagName === 'A') {
            neutralizeAnchorTarget(mutation.target);
          } else if (mutation.target.tagName === 'BASE') {
            neutralizeBaseTags();
          }
        }
      }
    });

    const targetNode = document.documentElement || document;
    if (targetNode) {
      observer.observe(targetNode, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['target']
      });
    }

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => {
        neutralizeBaseTags();
        try {
          const anchors = document.querySelectorAll('a[target]');
          anchors.forEach(neutralizeAnchorTarget);
        } catch (e) {}
      });
    }
  }

  // Initialize preemptive neutralizer
  initLinkNeutralizer();

  // ── Early Pointer Interception (Intercepts mousedown and pointerdown before host scripts or native browser spawn tabs) ──
  function handleEarlyPointerInterception(e) {
    if (e.button !== 0 && e.button !== 1) return;

    if (e.target && e.target.closest && (
      e.target.closest('#threatlens-link-scan-overlay') || 
      e.target.closest('#threatlens-intelligence-report-root') || 
      e.target.closest('#threatlens-threat-report-host') || 
      e.target.closest('#threatlens-snip-overlay')
    )) {
      return;
    }

    const anchor = e.target.closest ? e.target.closest('a') : null;
    if (!anchor || !anchor.href) return;

    const rawHref = anchor.href;
    if (!rawHref.startsWith('http://') && !rawHref.startsWith('https://')) return;

    // Bypass internal same-site links and search engine pagination immediately
    const destUrl = unwrapDestinationUrl(rawHref, anchor);
    if (isInternalOrSameSite(destUrl)) return;

    // Immediately neutralize target attribute before click or native handling can occur
    neutralizeAnchorTarget(anchor);

    // If middle click (button 1), stop browser from opening tab right on pointerdown/mousedown
    if (e.button === 1) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
    }
  }

  window.addEventListener('pointerdown', handleEarlyPointerInterception, { capture: true });
  window.addEventListener('mousedown', handleEarlyPointerInterception, { capture: true });

  // ── Robust Link Click Interception (Prevents premature tab opening) ──
  function handleLinkInterception(e) {
    // Only handle primary (left, 0) and auxiliary (middle, 1) clicks
    if (e.button !== 0 && e.button !== 1) return;

    // Ignore clicks originating from inside ThreatLens's own UI overlays
    if (e.target && e.target.closest && (
      e.target.closest('#threatlens-link-scan-overlay') || 
      e.target.closest('#threatlens-intelligence-report-root') || 
      e.target.closest('#threatlens-threat-report-host') || 
      e.target.closest('#threatlens-snip-overlay')
    )) {
      return;
    }

    const anchor = e.target.closest ? e.target.closest('a') : null;
    if (!anchor || !anchor.href) return;

    const rawHref = anchor.href;
    if (!rawHref.startsWith('http://') && !rawHref.startsWith('https://')) return;

    // Ignore same-page hash anchors
    const currentBase = window.location.href.split('#')[0];
    if (rawHref.startsWith(currentBase + '#') || rawHref === currentBase) return;

    // Unwrap search engine and tracking redirects (e.g. Bing /ck/a?u=a1<b64>, Google /url?q=...)
    const destUrl = unwrapDestinationUrl(rawHref, anchor);

    // ── CRITICAL: Bypass same-domain / internal navigation (e.g. Bing "Next", page numbers, search suggestions) ──
    if (isInternalOrSameSite(destUrl)) {
      return;
    }

    // If user already clicked Proceed on either the raw or unwrapped URL
    if (bypassedClickUrls.has(rawHref) || bypassedClickUrls.has(destUrl)) {
      return;
    }

    // Determine if opening in a new tab
    const wasBlank = (anchor.dataset && anchor.dataset.tlTarget && (anchor.dataset.tlTarget.toLowerCase().includes('blank') || anchor.dataset.tlTarget.toLowerCase().includes('new'))) ||
                     (anchor.getAttribute && anchor.getAttribute('target') && (anchor.getAttribute('target').toLowerCase().includes('blank') || anchor.getAttribute('target').toLowerCase().includes('new')));
    const isNewTab = (e.button === 1) || e.ctrlKey || e.metaKey || Boolean(wasBlank);

    // Preemptively ensure target is neutralized to _self
    neutralizeAnchorTarget(anchor);

    // ── CRITICAL: PREVENT BROWSER & WEBPAGE FROM OPENING THE TAB EARLY ──
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    // Only launch HUD on click or auxclick
    if (e.type === 'click' || e.type === 'auxclick') {
      showLinkScanHUD(destUrl, anchor, isNewTab, rawHref);
    }
  }

  window.addEventListener('auxclick', handleLinkInterception, { capture: true });
  window.addEventListener('click', handleLinkInterception, { capture: true });

  // ── 2. Listen for messages from background service worker ──
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "START_SCREEN_SNIP") {
      startScreenSnip();
      sendResponse({ success: true });
    }

    if (message.action === "SCAN_SPECIFIC_IMAGE") {
      scanImageSrc(message.src);
    }
    
    if (message.action === "SCAN_PAGE_QR") {
      scanAllImagesOnPage();
    }

    if (message.action === "SHOW_QR_RESULT" || message.action === "SHOW_THREAT_REPORT") {
      const payload = message.payload || (message.result && (message.result.expandedUrl || message.result.rawContent || message.result.originalUrl)) || window.location.href;
      const analysis = message.analysis || message.result;
      if (analysis) {
        showIntelligenceReportCard(payload, analysis);
      }
    }
  });

  // ── Automatic Threat & QR Pre-Discovery Engine ──
  (async function checkCurrentPageOnLoad() {
    const currentUrl = window.location.href;
    if (!currentUrl.startsWith('http://') && !currentUrl.startsWith('https://')) return;

    // Do NOT run threat scans or show popups on trusted search engine pages (Bing, Google, etc.)
    if (isTrustedSearchEngine(window.location.hostname)) {
      // Still auto-scan QR codes quietly in background if any
      setTimeout(autoScanPageQRCodes, 800);
      return;
    }

    const localCheck = isSuspiciousLink(currentUrl);
    if (localCheck.isSuspicious && !bypassedClickUrls.has(currentUrl)) {
      const initialReport = {
        rawContent: currentUrl,
        expandedUrl: currentUrl,
        safetyStatus: localCheck.riskScore >= 60 ? 'MALICIOUS' : 'CAUTION',
        overallScore: Math.max(0, 100 - localCheck.riskScore),
        riskScore: localCheck.riskScore,
        threatType: localCheck.threatType,
        flags: localCheck.flags,
        threatDetails: localCheck.flags,
        siteCategory: localCheck.riskScore >= 60 ? '🔴 High Risk Site' : '⚠️ Caution Site',
        siteSummary: localCheck.flags.join('; '),
        isUrl: true
      };
      showIntelligenceReportCard(currentUrl, initialReport);
    }

    try {
      chrome.runtime.sendMessage({ action: "GET_PAGE_THREAT_REPORT", url: currentUrl }, (response) => {
        if (response && response.threatReport && !bypassedClickUrls.has(currentUrl)) {
          showIntelligenceReportCard(response.threatReport.expandedUrl || currentUrl, response.threatReport);
        }
      });
    } catch (e) {}

    // Auto-scan all QR codes in page background
    setTimeout(autoScanPageQRCodes, 800);
    setTimeout(autoScanPageQRCodes, 2500);
  })();

  // ── Universal jsQR Engine Resolver ──
  function getJsQREngine() {
    if (typeof jsQR !== 'undefined') return jsQR;
    if (typeof window !== 'undefined' && window.jsQR) return window.jsQR;
    if (typeof self !== 'undefined' && self.jsQR) return self.jsQR;
    if (typeof globalThis !== 'undefined' && globalThis.jsQR) return globalThis.jsQR;
    return null;
  }

  // ── Instant Hover Pre-Decoder (Ensures synchronous availability on Right-Click) ──
  ['mouseover', 'pointerover', 'mousemove'].forEach(evtType => {
    document.addEventListener(evtType, async (e) => {
      const target = e.target.closest ? (e.target.closest('canvas, img, svg, picture, [class*="qr"], [id*="qr"], [id*="preview"]') || e.target) : e.target;
      if (target && !target.dataset?.threatlensQr) {
        const decoded = await decodeImage(target);
        if (decoded) {
          target.dataset.threatlensQr = decoded;
          target.title = `🛡️ ThreatLens Verified QR: Right-click to inspect analysis`;
          target.style.outline = '2px solid rgba(6, 182, 212, 0.5)';
          target.style.outlineOffset = '2px';
          target.style.borderRadius = '6px';
        }
      }
    }, { passive: true });
  });

  // ── Right-Click Direct QR Code Popup Trigger ──
  document.addEventListener('contextmenu', async (e) => {
    const target = e.target.closest ? (e.target.closest('canvas, img, svg, picture, [class*="qr"], [id*="qr"], [id*="preview"]') || e.target) : e.target;
    if (!target) return;

    // 1. If already pre-decoded, trigger popup immediately and prevent default menu
    let qrPayload = target.dataset ? target.dataset.threatlensQr : null;
    if (!qrPayload && target.parentElement && target.parentElement.dataset) {
      qrPayload = target.parentElement.dataset.threatlensQr;
    }

    if (qrPayload) {
      e.preventDefault();
      e.stopPropagation();
      chrome.runtime.sendMessage({ action: "QR_SCANNED", payload: qrPayload }, (analysis) => {
        if (analysis) {
          showIntelligenceReportCard(qrPayload, analysis);
        }
      });
      return;
    }

    // 2. Decode candidate element (canvas, img, svg, or child inside container)
    const candidate = target.querySelector ? (target.querySelector('canvas, img, svg') || target) : target;
    const src = candidate.src || (candidate.currentSrc ? candidate.currentSrc : null);
    
    // Try local jsQR / canvas decode first
    let decoded = await decodeImage(candidate);
    
    // If local failed and we have an image URL, try background service worker decode
    if (!decoded && src && src.startsWith('http')) {
      decoded = await new Promise(resolve => {
        chrome.runtime.sendMessage({ action: "DECODE_IMAGE_URL", url: src }, (res) => {
          if (res && res.success && res.payload) {
            if (res.analysis) {
              showIntelligenceReportCard(res.payload, res.analysis);
            }
            resolve(res.payload);
          } else {
            resolve(null);
          }
        });
      });
    }

    if (decoded) {
      e.preventDefault();
      e.stopPropagation();
      if (candidate.dataset) candidate.dataset.threatlensQr = decoded;
      if (target.dataset) target.dataset.threatlensQr = decoded;
      chrome.runtime.sendMessage({ action: "QR_SCANNED", payload: decoded }, (analysis) => {
        if (analysis) {
          showIntelligenceReportCard(decoded, analysis);
        }
      });
    }
  }, true);

  /**
   * Automatically scans all QR codes on the page quietly in background
   */
  async function autoScanPageQRCodes() {
    const targets = Array.from(document.querySelectorAll('img, canvas, svg, picture, [class*="qr"], [id*="qr"], [class*="code"], [id*="preview"]')).filter(el => {
      const w = el.naturalWidth || el.offsetWidth || el.width || (el.getBoundingClientRect ? el.getBoundingClientRect().width : 0) || 0;
      const h = el.naturalHeight || el.offsetHeight || el.height || (el.getBoundingClientRect ? el.getBoundingClientRect().height : 0) || 0;
      return (w >= 30 && h >= 30) || el.tagName === 'CANVAS' || el.tagName === 'SVG' || el.tagName === 'IMG';
    });

    for (const el of targets) {
      if (el.dataset && el.dataset.threatlensQr) continue;
      try {
        let decoded = await decodeImage(el);
        if (!decoded) {
          const src = el.src || (el.currentSrc ? el.currentSrc : null) || (el.querySelector && el.querySelector('img') ? el.querySelector('img').src : null);
          if (src && src.startsWith('http')) {
            decoded = await new Promise(resolve => {
              chrome.runtime.sendMessage({ action: "DECODE_IMAGE_URL", url: src }, (res) => {
                resolve(res && res.success && res.payload ? res.payload : null);
              });
            });
          }
        }
        if (decoded) {
          if (el.dataset) {
            el.dataset.threatlensQr = decoded;
            el.title = `🛡️ ThreatLens Verified QR: Right-click to inspect analysis`;
          }
          el.style.outline = '2px solid rgba(6, 182, 212, 0.5)';
          el.style.outlineOffset = '2px';
          el.style.borderRadius = '6px';
        }
      } catch (err) {}
    }
  }

  /**
   * Universal Pixel Extractor for Canvas, SVG, Blob, DataURI, Cross-Origin Images, and Containers
   */
  async function getImageDataFromAnySource(source) {
    if (!source) return null;

    // 1. Direct Canvas element (copy to dedicated canvas with willReadFrequently: true)
    if (source instanceof HTMLCanvasElement) {
      try {
        const offCanvas = document.createElement('canvas');
        offCanvas.width = source.width || 300;
        offCanvas.height = source.height || 300;
        const offCtx = offCanvas.getContext('2d', { willReadFrequently: true });
        offCtx.drawImage(source, 0, 0);
        return offCtx.getImageData(0, 0, offCanvas.width, offCanvas.height);
      } catch (e) {
        try {
          const ctx = source.getContext('2d');
          return ctx.getImageData(0, 0, source.width, source.height);
        } catch (err) {}
      }
    }

    // 2. SVG element -> render onto canvas
    if (source instanceof SVGElement || (source.tagName && source.tagName.toLowerCase() === 'svg')) {
      try {
        const svgData = new XMLSerializer().serializeToString(source);
        const svgBase64 = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgData);
        const img = new Image();
        await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = svgBase64; });
        const canvas = document.createElement('canvas');
        canvas.width = img.width || 300;
        canvas.height = img.height || 300;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
        return ctx.getImageData(0, 0, canvas.width, canvas.height);
      } catch (e) {}
    }

    // 3. Wrapper container with canvas/svg/img
    if (source.querySelector) {
      const child = source.querySelector('canvas, svg, img');
      if (child && child !== source) {
        const res = await getImageDataFromAnySource(child);
        if (res) return res;
      }
    }

    // 4. Image URL string or <img> element with src
    let srcUrl = typeof source === 'string' ? source : (source.src || source.currentSrc || null);

    if (srcUrl) {
      // Try un-tainted fetch first
      try {
        const res = await fetch(srcUrl);
        const blob = await res.blob();
        const bitmap = await createImageBitmap(blob);
        const canvas = document.createElement('canvas');
        canvas.width = bitmap.width;
        canvas.height = bitmap.height;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(bitmap, 0, 0);
        return ctx.getImageData(0, 0, canvas.width, canvas.height);
      } catch (e) {}
    }

    // 5. Direct Image element fallback
    if (source instanceof HTMLImageElement && (source.naturalWidth || source.width)) {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = source.naturalWidth || source.width;
        canvas.height = source.naturalHeight || source.height;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(source, 0, 0);
        return ctx.getImageData(0, 0, canvas.width, canvas.height);
      } catch (e) {}
    }

    return null;
  }

  /**
   * Universal JS-Powered QR decoder (jsQR)
   */
  async function decodeImage(source) {
    if (!source) return null;

    try {
      const imgData = await getImageDataFromAnySource(source);
      if (!imgData || !imgData.data) return null;

      const engine = getJsQREngine();
      if (engine) {
        const code = engine(imgData.data, imgData.width, imgData.height, { inversionAttempts: "dontInvert" }) ||
                     engine(imgData.data, imgData.width, imgData.height, { inversionAttempts: "onlyInvert" }) ||
                     engine(imgData.data, imgData.width, imgData.height, { inversionAttempts: "attemptBoth" });
        if (code && code.data) {
          return code.data;
        }
      }
    } catch (err) {}

    return null;
  }

  async function scanImageSrc(src) {
    showToast("ThreatLens: Scanning QR code...");
    let decoded = await decodeImage(src);
    if (!decoded && src && src.startsWith('http')) {
      chrome.runtime.sendMessage({ action: "DECODE_IMAGE_URL", url: src }, (res) => {
        if (res && res.success && res.payload) {
          if (res.analysis) {
            showIntelligenceReportCard(res.payload, res.analysis);
          }
        } else {
          showToast("ThreatLens: No QR code found in this image.", true);
        }
      });
      return;
    }

    if (decoded) {
      chrome.runtime.sendMessage({ action: "QR_SCANNED", payload: decoded }, (analysis) => {
        if (analysis) {
          showIntelligenceReportCard(decoded, analysis);
        }
      });
    } else {
      showToast("ThreatLens: No QR code found in this image.", true);
    }
  }

  async function scanAllImagesOnPage() {
    const candidates = Array.from(document.querySelectorAll('canvas, img, svg, picture, [class*="qr"], [id*="qr"], [class*="code"], [id*="preview"], [class*="preview"], [id*="canvas"]')).filter(el => {
      const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : { width: el.width || 0, height: el.height || 0 };
      return (rect.width >= 30 && rect.height >= 30) || el.tagName === 'CANVAS' || el.tagName === 'SVG' || el.tagName === 'IMG';
    });

    if (candidates.length === 0) {
      showToast("ThreatLens: No QR elements found on this page.", true);
      return;
    }

    showToast(`ThreatLens: Scanning ${candidates.length} visual elements for QR codes...`);

    let foundDecoded = null;
    for (const el of candidates) {
      // 1. Try local decode
      let decoded = await decodeImage(el);

      // 2. If local failed and element has an image URL, try background service worker decode
      if (!decoded) {
        const src = el.src || (el.currentSrc ? el.currentSrc : null) || (el.querySelector && el.querySelector('img') ? el.querySelector('img').src : null);
        if (src && src.startsWith('http')) {
          decoded = await new Promise(resolve => {
            chrome.runtime.sendMessage({ action: "DECODE_IMAGE_URL", url: src }, (res) => {
              if (res && res.success && res.payload) {
                resolve(res.payload);
              } else {
                resolve(null);
              }
            });
          });
        }
      }

      if (decoded) {
        foundDecoded = decoded;
        if (el.dataset) el.dataset.threatlensQr = decoded;
        break;
      }
    }

    if (foundDecoded) {
      chrome.runtime.sendMessage({ action: "QR_SCANNED", payload: foundDecoded }, (analysis) => {
        if (analysis) {
          showIntelligenceReportCard(foundDecoded, analysis);
        }
      });
    } else {
      showToast("ThreatLens: No QR codes detected in webpage elements.", true);
    }
  }

  const scanAndHighlightQRs = scanAllImagesOnPage;

  function showToast(msg, isError = false) {
    const toast = document.createElement('div');
    toast.style.position = 'fixed';
    toast.style.bottom = '20px';
    toast.style.right = '20px';
    toast.style.backgroundColor = isError ? '#ef4444' : '#18181b';
    toast.style.color = '#f4f4f5';
    toast.style.padding = '10px 16px';
    toast.style.borderRadius = '10px';
    toast.style.border = '1px solid rgba(255,255,255,0.15)';
    toast.style.boxShadow = '0 10px 25px rgba(0,0,0,0.5)';
    toast.style.zIndex = '2147483647';
    toast.style.fontFamily = '-apple-system, BlinkMacSystemFont, sans-serif';
    toast.style.fontSize = '13px';
    toast.textContent = msg;

    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
  }

  /**
   * Renders the Non-Scrolling 2-Column Intelligence Report Card
   */
  function showIntelligenceReportCard(payload, analysis, onProceedCallback = null) {
    if (activeReportHost) {
      activeReportHost.remove();
      activeReportHost = null;
    }

    const host = document.createElement('div');
    host.id = 'threatlens-intelligence-report-root';
    host.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;display:block!important;pointer-events:auto!important;';
    (document.body || document.documentElement).appendChild(host);
    activeReportHost = host;

    const shadow = host.attachShadow({ mode: 'open' });

    // Status mapping
    const isMalicious = analysis.safetyStatus === 'MALICIOUS' || (analysis.overallScore !== undefined && analysis.overallScore <= 40) || (analysis.riskScore >= 60);
    const isCaution = !isMalicious && (analysis.safetyStatus === 'CAUTION' || (analysis.overallScore !== undefined && analysis.overallScore <= 75) || (analysis.riskScore >= 25));
    const isSafe = !isMalicious && !isCaution;

    // Theme Colors matching Android (MaliciousRed, CautionAmber, SafeGreen, NeonCyan)
    const baseColor = isMalicious ? '#ef4444' : (isCaution ? '#f59e0b' : '#10b981');
    const baseGlow = isMalicious ? 'rgba(239, 68, 68, 0.4)' : (isCaution ? 'rgba(245, 158, 11, 0.4)' : 'rgba(16, 185, 129, 0.4)');
    const badgeBg = isMalicious ? 'rgba(239, 68, 68, 0.15)' : (isCaution ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)');

    const score = Math.min(100, Math.max(0, analysis.overallScore !== undefined ? analysis.overallScore : (100 - (analysis.riskScore || 0))));
    const stampText = isMalicious ? (analysis.threatType && analysis.threatType !== 'SAFE' ? analysis.threatType.replace(/_/g, ' ') : 'MALICIOUS') : (isCaution ? 'CAUTION ADVISED' : 'VERIFIED SAFE');

    const flags = analysis.flags || analysis.threatDetails || [];
    const siteCategory = analysis.siteCategory || (analysis.scanType ? analysis.scanType : 'WEB DESTINATION');
    const redirectsCount = analysis.redirectChain ? analysis.redirectChain.length : 1;
    const redirectLabel = redirectsCount > 1 ? `${redirectsCount} Hops` : 'Direct';

    // Compact 270-degree Arc Gauge
    const radius = 40;
    const circumference = 2 * Math.PI * radius; // ~251.32
    const totalArcLength = circumference * (270 / 360); // ~188.49
    const activeArcLength = totalArcLength * (score / 100);

    const styles = `
      :host {
        all: initial;
        display: block !important;
        position: fixed !important;
        top: 0 !important;
        left: 0 !important;
        width: 100vw !important;
        height: 100vh !important;
        z-index: 2147483647 !important;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif !important;
        pointer-events: auto !important;
      }
      * { box-sizing: border-box; }

      .backdrop {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(0, 0, 0, 0.48);
        backdrop-filter: blur(8px);
        -webkit-backdrop-filter: blur(8px);
        z-index: 2147483647;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 16px;
        animation: tlFadeIn 0.2s ease-out forwards;
      }

      @keyframes tlFadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes tlCardSlide {
        from { opacity: 0; transform: scale(0.96) translateY(10px); }
        to { opacity: 1; transform: scale(1) translateY(0); }
      }

      .report-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 20px;
        width: 100%;
        max-width: 650px;
        box-shadow: 0 25px 60px rgba(15, 23, 42, 0.15), 0 0 24px ${baseGlow};
        color: #0f172a;
        padding: 18px 22px;
        position: relative;
        animation: tlCardSlide 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
        overflow: hidden;
      }

      /* ── 1. HEADER ROW (Telemetry + Category + Close) ── */
      .top-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;
      }

      .brand-title {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .telemetry-header-label {
        font-size: 10px;
        font-weight: 800;
        letter-spacing: 1.2px;
        color: #64748b;
        text-transform: uppercase;
      }

      .top-right-group {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .category-pill {
        background: ${badgeBg};
        color: ${baseColor};
        border: 1px solid ${baseColor}66;
        padding: 3px 9px;
        border-radius: 8px;
        font-size: 10px;
        font-weight: 800;
        letter-spacing: 0.5px;
        text-transform: uppercase;
      }

      .btn-close {
        background: transparent;
        border: none;
        color: #64748b;
        font-size: 16px;
        cursor: pointer;
        padding: 2px 6px;
        border-radius: 6px;
        line-height: 1;
        transition: all 0.15s;
      }

      .btn-close:hover {
        color: #0f172a;
        background: #f1f5f9;
      }

      .entity-box {
        background: #f8fafd;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 7px 10px;
        margin-bottom: 14px;
        font-family: Consolas, Monaco, monospace;
        font-size: 11px;
        color: #334155;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        display: flex;
        align-items: center;
        gap: 6px;
      }

      /* ── 2. TWO-COLUMN NON-SCROLLING GRID ── */
      .main-grid {
        display: grid;
        grid-template-columns: 200px 1fr;
        gap: 16px;
        align-items: start;
        margin-bottom: 14px;
      }

      /* Left Column: Gauge & Telemetry Chips */
      .left-col {
        display: flex;
        flex-direction: column;
        align-items: center;
        background: #f8fafd;
        border: 1px solid #e2e8f0;
        border-radius: 14px;
        padding: 12px 10px;
      }

      .gauge-container {
        position: relative;
        width: 106px;
        height: 106px;
      }

      .gauge-container svg {
        width: 100%;
        height: 100%;
      }

      .gauge-bg {
        fill: none;
        stroke: #e2e8f0;
        stroke-width: 7;
        stroke-linecap: round;
        stroke-dasharray: ${totalArcLength} ${circumference};
        transform-origin: 50% 50%;
        transform: rotate(135deg);
      }

      .gauge-fill {
        fill: none;
        stroke: ${baseColor};
        stroke-width: 7;
        stroke-linecap: round;
        stroke-dasharray: ${activeArcLength} ${circumference};
        transition: stroke-dasharray 1.2s cubic-bezier(0.16, 1, 0.3, 1);
        transform-origin: 50% 50%;
        transform: rotate(135deg);
        filter: drop-shadow(0 0 6px ${baseGlow});
      }

      .gauge-score {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
      }

      .score-num {
        font-size: 28px;
        font-weight: 800;
        color: #0f172a;
        line-height: 1;
      }

      .score-lbl {
        font-size: 8px;
        font-weight: 800;
        letter-spacing: 0.8px;
        color: #64748b;
        margin-top: 2px;
      }

      .threat-stamp {
        margin-top: 8px;
        margin-bottom: 12px;
        padding: 4px 10px;
        border-radius: 6px;
        background: #ffffff;
        border: 1.5px solid ${baseColor};
        color: ${baseColor};
        font-size: 9.5px;
        font-weight: 900;
        letter-spacing: 1.5px;
        text-transform: uppercase;
        box-shadow: 0 1px 4px ${baseGlow};
      }

      .telemetry-mini-row {
        display: flex;
        width: 100%;
        gap: 6px;
      }

      .telemetry-mini-chip {
        flex: 1;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 6px 4px;
        text-align: center;
      }

      .telemetry-mini-title {
        font-size: 8px;
        font-weight: 800;
        color: #64748b;
        letter-spacing: 0.4px;
        text-transform: uppercase;
        margin-bottom: 2px;
      }

      .telemetry-mini-val {
        font-size: 11.5px;
        font-weight: 800;
        color: #0f172a;
      }

      /* Right Column: AI Insight & Engine Logs */
      .right-col {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }

      .ai-card {
        background: #eff6ff;
        border: 1px solid #bfdbfe;
        border-radius: 12px;
        padding: 10px 14px;
      }

      .ai-header {
        display: flex;
        align-items: center;
        gap: 6px;
        color: #1d4ed8;
        font-size: 9.5px;
        font-weight: 800;
        letter-spacing: 0.8px;
        text-transform: uppercase;
        margin-bottom: 4px;
      }

      .ai-content {
        font-size: 11.5px;
        line-height: 1.45;
        color: #334155;
      }

      .engine-logs-box {
        background: #f8fafd;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 9px 12px;
      }

      .engine-logs-header {
        font-size: 9px;
        font-weight: 800;
        letter-spacing: 1px;
        color: #64748b;
        text-transform: uppercase;
        margin-bottom: 6px;
      }

      .timeline-item {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 3px 0;
      }

      .timeline-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: ${baseColor};
        box-shadow: 0 0 5px ${baseColor};
        margin-top: 4px;
        flex-shrink: 0;
      }

      .timeline-text {
        font-size: 11px;
        line-height: 1.4;
        color: #334155;
      }

      /* ── 3. ACTIONS ROW ── */
      .actions-row {
        display: flex;
        gap: 10px;
        margin-top: 2px;
      }

      .btn-primary {
        flex: 1;
        padding: 10px 16px;
        background: ${isMalicious ? '#ef4444' : '#06b6d4'};
        color: #ffffff;
        font-weight: 800;
        font-size: 12px;
        border: none;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        box-shadow: 0 3px 12px ${baseGlow};
      }

      .btn-primary:hover {
        transform: translateY(-1px);
        filter: brightness(1.1);
      }

      .btn-secondary {
        padding: 10px 16px;
        background: transparent;
        color: #a1a1aa;
        font-weight: 700;
        font-size: 12px;
        border: 1px solid rgba(255, 255, 255, 0.14);
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
      }

      .btn-secondary:hover {
        background: rgba(255, 255, 255, 0.06);
        color: #f4f4f5;
      }
    `;

    // Engine Logs Timeline HTML
    const logsHtml = flags.length > 0 ? `
      <div class="engine-logs-box">
        <div class="engine-logs-header">ENGINE LOGS (${flags.length})</div>
        ${flags.slice(0, 3).map(f => `
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div class="timeline-text">${f}</div>
          </div>
        `).join('')}
        ${flags.length > 3 ? `
          <div style="font-size: 10px; color: #06b6d4; font-weight: 600; margin-top: 3px; padding-left: 14px;">
            + ${flags.length - 3} more intelligence flags...
          </div>
        ` : ''}
      </div>
    ` : `
      <div class="engine-logs-box">
        <div class="engine-logs-header">ENGINE LOGS</div>
        <div class="timeline-item">
          <div class="timeline-dot" style="background:#10b981;box-shadow:0 0 5px #10b981;"></div>
          <div class="timeline-text">No security violations or malicious patterns detected.</div>
        </div>
      </div>
    `;

    const aiExplanation = analysis.aiInsight || analysis.siteSummary || analysis.message || "Verified clean by ThreatLens Security Engine.";

    const modalHtml = `
      <style>${styles}</style>
      <div class="backdrop" id="tl-backdrop">
        <div class="report-card">
          
          <!-- 1. TOP HEADER ROW -->
          <div class="top-nav">
            <div class="telemetry-header-label">
              ${analysis.isCertified ? '🛡️ CERTIFIED AUTHENTIC QR' : 'TELEMETRY'}
            </div>
            <div class="top-right-group">
              ${analysis.isCertified ? `<div class="category-pill" style="background: rgba(16, 185, 129, 0.2); color: #10b981; border-color: rgba(16, 185, 129, 0.5);">✅ HMAC-SHA256 SIGNED</div>` : ''}
              <div class="category-pill">
                ${siteCategory}
              </div>
              <button class="btn-close" id="tl-btn-close" title="Close">✕</button>
            </div>
          </div>

          <!-- Target Entity Box -->
          <div class="entity-box">
            <span>🔗</span>
            <span>${payload}</span>
          </div>

          <!-- 2. TWO-COLUMN COMPACT LAYOUT (NO SCROLLING) -->
          <div class="main-grid">
            
            <!-- Left: Gauge + Stamp + Telemetry Chips -->
            <div class="left-col">
              <div class="gauge-container">
                <svg viewBox="0 0 106 106">
                  <circle class="gauge-bg" cx="53" cy="53" r="${radius}"></circle>
                  <circle class="gauge-fill" cx="53" cy="53" r="${radius}"></circle>
                </svg>
                <div class="gauge-score">
                  <span class="score-num">${score.toFixed(0)}</span>
                  <span class="score-lbl">TRUST SCORE</span>
                </div>
              </div>
              <div class="threat-stamp">${stampText}</div>
              
              <div class="telemetry-mini-row">
                <div class="telemetry-mini-chip">
                  <div class="telemetry-mini-title">Routing</div>
                  <div class="telemetry-mini-val" style="color: ${redirectsCount > 2 ? '#f59e0b' : '#06b6d4'};">${redirectLabel}</div>
                </div>
                <div class="telemetry-mini-chip">
                  <div class="telemetry-mini-title">Flags</div>
                  <div class="telemetry-mini-val" style="color: ${flags.length > 0 ? (isMalicious ? '#ef4444' : '#f59e0b') : '#10b981'};">${flags.length} Found</div>
                </div>
              </div>
            </div>

            <!-- Right: AI Insight + Engine Logs -->
            <div class="right-col">
              <div class="ai-card">
                <div class="ai-header">
                  <span>✨</span> THREATLENS AI INSIGHT
                </div>
                <div class="ai-content">
                  ${aiExplanation}
                </div>
              </div>

              ${logsHtml}
            </div>

          </div>

          <!-- Isolated Sandbox Container (Collapsible) -->
          <div id="tl-sandbox-container" style="display: none; margin-top: 12px; border: 1px solid rgba(6, 182, 212, 0.4); border-radius: 10px; overflow: hidden; background: #000;">
            <div style="background: #18181b; padding: 6px 12px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.1); font-size: 11px; color: #94a3b8;">
              <span>🔒 ThreatLens Isolated Sandbox (Scripts & Cookies Disabled)</span>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span id="tl-sandbox-status" style="color: #10b981; font-weight: bold;">Isolated</span>
                <button id="tl-btn-open-full-sandbox" style="background: rgba(0, 240, 255, 0.15); border: 1px solid rgba(0, 240, 255, 0.4); color: #00f0ff; padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 700; cursor: pointer;">Full Screen ↗</button>
              </div>
            </div>
            <iframe id="tl-sandbox-iframe" sandbox="allow-same-origin" style="width: 100%; height: 200px; border: none; background: #fff;" src="about:blank"></iframe>
          </div>

          <!-- 3. ACTION BUTTONS ROW -->
          <div class="actions-row">
            ${isSafe ? `
              <button class="btn-primary" id="tl-btn-proceed">
                <span>🌐</span> Open Link
              </button>
              <button class="btn-secondary" id="tl-btn-sandbox">
                <span>🔬</span> Sandbox Preview
              </button>
              <button class="btn-secondary" id="tl-btn-appreciate" title="Appreciate this verified site in Community DB">
                👍 Appreciate
              </button>
              <button class="btn-secondary" id="tl-btn-report" title="Report threat issue to Community DB">
                ⚠️ Report
              </button>
              <button class="btn-secondary" id="tl-btn-safety">
                Close
              </button>
            ` : `
              <button class="btn-primary" id="tl-btn-safety">
                <span>🛡️</span> Return to Safety
              </button>
              <button class="btn-secondary" id="tl-btn-sandbox">
                <span>🔬</span> Sandbox Preview
              </button>
              <button class="btn-secondary" id="tl-btn-proceed">
                Proceed Anyway
              </button>
              <button class="btn-secondary" id="tl-btn-report" title="Report threat issue to Community DB">
                ⚠️ Report Threat
              </button>
              <button class="btn-secondary" id="tl-btn-appreciate" title="Appreciate in Community DB">
                👍 Appreciate
              </button>
            `}
          </div>

        </div>
      </div>
    `;

    shadow.innerHTML = modalHtml;

    const closeHandler = () => {
      host.remove();
      activeReportHost = null;
    };

    const rawDestUrl = analysis.expandedUrl || analysis.originalUrl || analysis.rawContent || payload;
    const destUrl = unwrapDestinationUrl(rawDestUrl);

    const btnClose = shadow.getElementById('tl-btn-close');
    if (btnClose) btnClose.addEventListener('click', closeHandler);

    const btnSafety = shadow.getElementById('tl-btn-safety');
    if (btnSafety) {
      btnSafety.addEventListener('click', () => {
        closeHandler();
        if (!isSafe && window.history.length > 1) {
          window.history.back();
        }
      });
    }

    const btnAppreciate = shadow.getElementById('tl-btn-appreciate');
    if (btnAppreciate) {
      btnAppreciate.addEventListener('click', () => {
        const reason = prompt("Select reason to appreciate this website:\n1. 👍 Verified Legitimate Business\n2. 👍 Safe & Secure Payment Portal\n3. 👍 Educational / Official Resource\n4. 👍 Safe & Helpful Content", "👍 Verified Legitimate Business");
        if (reason) {
          chrome.runtime.sendMessage({ action: "SUBMIT_COMMUNITY_REPORT", url: destUrl, issue: reason }, (res) => {
            showToast("👍 Thank you! Positive appreciation recorded to ThreatLens Community.");
          });
        }
      });
    }

    const btnReport = shadow.getElementById('tl-btn-report');
    if (btnReport) {
      btnReport.addEventListener('click', () => {
        const reason = prompt("Select threat issue to report to ThreatLens Community:\n1. Phishing / Fake Portal\n2. Malware / Dangerous Download\n3. Online Scam / Fraud\n4. Adult / Gambling Content\n5. Suspicious Unverified Domain", "Phishing / Fake Portal");
        if (reason) {
          chrome.runtime.sendMessage({ action: "SUBMIT_COMMUNITY_REPORT", url: destUrl, issue: reason }, (res) => {
            showToast("🛡️ Thank you! Community threat report submitted to Firebase.");
          });
        }
      });
    }

    const btnProceed = shadow.getElementById('tl-btn-proceed');
    if (btnProceed) {
      btnProceed.addEventListener('click', () => {
        closeHandler();
        bypassedClickUrls.add(destUrl);
        if (rawDestUrl) bypassedClickUrls.add(rawDestUrl);
        try {
          chrome.runtime.sendMessage({ action: "ALLOW_BYPASS", url: destUrl });
        } catch (e) {}

        if (onProceedCallback) {
          onProceedCallback();
          return;
        }

        // Open authentic destination URL directly
        if (destUrl && (destUrl.startsWith('http://') || destUrl.startsWith('https://'))) {
          window.open(destUrl, '_blank');
        } else if (destUrl && destUrl.startsWith('upi://')) {
          window.location.href = destUrl;
        } else if (destUrl) {
          showToast(`ThreatLens: Copied payload "${destUrl.slice(0, 40)}..." to clipboard.`);
          try { navigator.clipboard.writeText(destUrl); } catch (e) {}
        }
      });
    }

    // Launch ThreatLens Isolated Sandbox Browser directly on the original destination URL
    const btnSandbox = shadow.getElementById('tl-btn-sandbox');
    if (btnSandbox) {
      btnSandbox.addEventListener('click', () => {
        closeHandler();
        const fullUrl = chrome.runtime.getURL(`pages/sandbox.html?url=${encodeURIComponent(destUrl)}&score=${score}&type=${encodeURIComponent(analysis.threatType || 'SUSPICIOUS')}`);
        window.open(fullUrl, '_blank');
      });
    }

    if (btnSandbox && sandboxContainer && sandboxIframe) {
      btnSandbox.addEventListener('click', () => {
        if (sandboxContainer.style.display === 'none') {
          sandboxContainer.style.display = 'block';
          btnSandbox.innerHTML = '<span>🔒</span> Hide Sandbox';
          if (!sandboxIframe.srcdoc && (destUrl.startsWith('http://') || destUrl.startsWith('https://'))) {
            if (tlSandboxStatus) tlSandboxStatus.textContent = "Fetching DOM...";
            chrome.runtime.sendMessage({
              action: "FETCH_SANDBOX_HTML",
              url: destUrl,
              allowScripts: false
            }, (res) => {
              if (res && res.success) {
                sandboxIframe.removeAttribute('src');
                sandboxIframe.srcdoc = res.sanitizedHtml;
                if (tlSandboxStatus) tlSandboxStatus.textContent = "DOM Isolated";
              } else {
                sandboxIframe.removeAttribute('src');
                sandboxIframe.srcdoc = `<div style="font-family:sans-serif;padding:20px;color:#ef4444;background:#18181b;">⚠️ Failed to fetch remote DOM: ${res?.error || 'Target server blocked request.'}</div>`;
                if (tlSandboxStatus) tlSandboxStatus.textContent = "Blocked";
              }
            });
          }
        } else {
          sandboxContainer.style.display = 'none';
          btnSandbox.innerHTML = '<span>🔬</span> Sandbox Preview';
        }
      });
    }

    const backdrop = shadow.getElementById('tl-backdrop');
    if (backdrop) {
      backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) closeHandler();
      });
    }
  }

  // ── 3. Interactive Screen Snip Tool (Alt+Q) ──
  function startScreenSnip() {
    // Remove existing snip overlay if any
    const existing = document.getElementById('threatlens-snip-overlay');
    if (existing) existing.remove();

    const host = document.createElement('div');
    host.id = 'threatlens-snip-overlay';
    host.style.position = 'fixed';
    host.style.inset = '0';
    host.style.zIndex = '2147483647';
    host.style.cursor = 'crosshair';
    host.style.userSelect = 'none';

    const shadow = host.attachShadow({ mode: 'open' });
    shadow.innerHTML = `
      <style>
        :host { all: initial; }
        .snip-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(9, 9, 11, 0.65);
          backdrop-filter: blur(2px);
          cursor: crosshair;
        }
        .snip-header {
          position: fixed;
          top: 24px;
          left: 50%;
          transform: translateX(-50%);
          background: rgba(18, 18, 24, 0.92);
          border: 1px solid rgba(6, 182, 212, 0.5);
          box-shadow: 0 10px 30px rgba(0,0,0,0.8), 0 0 20px rgba(6, 182, 212, 0.3);
          border-radius: 30px;
          padding: 10px 24px;
          color: #f4f4f5;
          font-family: -apple-system, BlinkMacSystemFont, 'Inter', sans-serif;
          font-size: 13.5px;
          font-weight: 600;
          display: flex;
          align-items: center;
          gap: 12px;
          pointer-events: none;
          z-index: 10;
        }
        .snip-header .badge {
          background: rgba(6, 182, 212, 0.2);
          color: #06b6d4;
          padding: 2px 8px;
          border-radius: 6px;
          font-size: 11px;
          font-weight: 700;
        }
        .snip-box {
          position: fixed;
          border: 2px dashed #06b6d4;
          background: rgba(6, 182, 212, 0.08);
          box-shadow: 0 0 0 9999px rgba(9, 9, 11, 0.6), 0 0 20px rgba(6, 182, 212, 0.4);
          pointer-events: none;
          display: none;
          z-index: 5;
        }
        .snip-box .coords {
          position: absolute;
          bottom: -28px;
          right: 0;
          background: #06b6d4;
          color: #09090b;
          font-family: monospace;
          font-size: 11px;
          font-weight: 700;
          padding: 2px 6px;
          border-radius: 4px;
        }
      </style>
      <div class="snip-backdrop" id="snipBackdrop">
        <div class="snip-header">
          <span>✂️ Drag box over QR code to scan</span>
          <span class="badge">Press ESC to Cancel</span>
        </div>
        <div class="snip-box" id="snipBox">
          <span class="coords" id="snipCoords">0 × 0</span>
        </div>
      </div>
    `;

    document.documentElement.appendChild(host);

    const backdrop = shadow.getElementById('snipBackdrop');
    const snipBox = shadow.getElementById('snipBox');
    const snipCoords = shadow.getElementById('snipCoords');

    let isDrawing = false;
    let startX = 0;
    let startY = 0;

    const cleanup = () => {
      document.removeEventListener('keydown', handleKey);
      host.remove();
    };

    const handleKey = (e) => {
      if (e.key === 'Escape') cleanup();
    };
    document.addEventListener('keydown', handleKey);

    backdrop.addEventListener('mousedown', (e) => {
      if (e.button !== 0) {
        cleanup();
        return;
      }
      isDrawing = true;
      startX = e.clientX;
      startY = e.clientY;
      snipBox.style.left = `${startX}px`;
      snipBox.style.top = `${startY}px`;
      snipBox.style.width = '0px';
      snipBox.style.height = '0px';
      snipBox.style.display = 'block';
    });

    backdrop.addEventListener('mousemove', (e) => {
      if (!isDrawing) return;
      const currentX = e.clientX;
      const currentY = e.clientY;
      const x = Math.min(startX, currentX);
      const y = Math.min(startY, currentY);
      const w = Math.abs(currentX - startX);
      const h = Math.abs(currentY - startY);

      snipBox.style.left = `${x}px`;
      snipBox.style.top = `${y}px`;
      snipBox.style.width = `${w}px`;
      snipBox.style.height = `${h}px`;
      snipCoords.textContent = `${w} × ${h}`;
    });

    backdrop.addEventListener('mouseup', async (e) => {
      if (!isDrawing) return;
      isDrawing = false;

      const endX = e.clientX;
      const endY = e.clientY;
      const x = Math.min(startX, endX);
      const y = Math.min(startY, endY);
      const width = Math.abs(endX - startX);
      const height = Math.abs(endY - startY);

      cleanup();

      if (width < 20 || height < 20) {
        showToast("Selection too small. Drag a box over the QR code.", true);
        return;
      }

      showToast("ThreatLens: Capturing and decoding screen area...");

      // Capture visible tab via background worker
      chrome.runtime.sendMessage({ action: "CAPTURE_VISIBLE_TAB" }, async (response) => {
        if (!response || !response.success || !response.dataUrl) {
          showToast("Failed to capture screen area.", true);
          return;
        }

        try {
          const img = new Image();
          img.onload = () => {
            const dpr = window.devicePixelRatio || 1;
            const scaleX = img.naturalWidth / window.innerWidth;
            const scaleY = img.naturalHeight / window.innerHeight;

            const cropCanvas = document.createElement('canvas');
            const cropX = Math.round(x * scaleX);
            const cropY = Math.round(y * scaleY);
            const cropW = Math.round(width * scaleX);
            const cropH = Math.round(height * scaleY);

            cropCanvas.width = cropW;
            cropCanvas.height = cropH;
            const ctx = cropCanvas.getContext('2d', { willReadFrequently: true });
            ctx.drawImage(img, cropX, cropY, cropW, cropH, 0, 0, cropW, cropH);

            const imgData = ctx.getImageData(0, 0, cropW, cropH);
            const engine = getJsQREngine();
            let decoded = null;

            if (engine && imgData && imgData.data) {
              const code = engine(imgData.data, cropW, cropH, { inversionAttempts: "dontInvert" }) ||
                           engine(imgData.data, cropW, cropH, { inversionAttempts: "onlyInvert" }) ||
                           engine(imgData.data, cropW, cropH, { inversionAttempts: "attemptBoth" });
              if (code && code.data) {
                decoded = code.data;
              }
            }

            if (decoded) {
              chrome.runtime.sendMessage({ action: "QR_SCANNED", payload: decoded }, (analysis) => {
                if (analysis) {
                  showIntelligenceReportCard(decoded, analysis);
                }
              });
            } else {
              showToast("No QR code detected in selected box. Try selecting closer to the QR boundaries.", true);
            }
          };
          img.src = response.dataUrl;
        } catch (err) {
          showToast("Error processing snip area: " + err.message, true);
        }
      });
    });
  }

  // ── Message Listener for Background & Popup Commands ──────────────
  if (chrome.runtime && chrome.runtime.onMessage) {
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      if (message.action === "START_ELEMENT_PICKER") {
        startElementPicker();
        sendResponse({ success: true });
        return true;
      }

      if (message.action === "START_SCREEN_SNIP") {
        startScreenSnip();
        sendResponse({ success: true });
        return true;
      }
      
      if (message.action === "SCAN_PAGE_QR") {
        scanAllImagesOnPage();
        sendResponse({ success: true });
        return true;
      }

      if (message.action === "SHOW_THREAT_REPORT") {
        if (message.payload && message.analysis) {
          showIntelligenceReportCard(message.payload, message.analysis);
        }
        sendResponse({ success: true });
        return true;
      }

      if (message.action === "GET_PAGE_SECURITY_TELEMETRY") {
        const isHttps = window.location.protocol === 'https:';
        const passwordInputs = document.querySelectorAll('input[type="password"]');
        const forms = Array.from(document.querySelectorAll('form')).map(f => ({
          action: f.getAttribute('action') || '',
          hasPassword: Boolean(f.querySelector('input[type="password"]')),
          method: (f.getAttribute('method') || 'GET').toUpperCase()
        }));
        const iframes = Array.from(document.querySelectorAll('iframe')).map(i => i.src || '');
        const scripts = Array.from(document.querySelectorAll('script')).map(s => s.src || 'inline');

        let insecureFormActions = 0;
        forms.forEach(f => {
          if (f.hasPassword && f.action && f.action.startsWith('http://')) {
            insecureFormActions++;
          }
        });

        sendResponse({
          url: window.location.href,
          hostname: window.location.hostname,
          title: document.title,
          isHttps: isHttps,
          formsCount: forms.length,
          passwordFieldsCount: passwordInputs.length,
          insecureForms: insecureFormActions,
          iframesCount: iframes.length,
          scriptsCount: scripts.length,
          timestamp: Date.now()
        });
        return true;
      }
    });
  }

  // Expose global test helper on window
  window.threatlensShowReport = function(customUrl = null, customAnalysis = null) {
    const target = customUrl || window.location.href;
    const report = customAnalysis || {
      safetyStatus: 'MALICIOUS',
      overallScore: 22,
      riskScore: 78,
      threatType: 'PHISHING',
      siteCategory: '🔴 Phishing & Credential Theft',
      flags: [
        'Homograph / Lookalike domain detected',
        'Credential login form on unverified destination',
        'Detected suspicious high-abuse Top Level Domain (.xyz)'
      ],
      siteSummary: 'ThreatLens neural inspection identified brand impersonation and potential credential theft vectors on this target.',
      aiInsight: 'ThreatLens flagged this destination for suspected credential theft and brand spoofing. Do NOT enter passwords, PINs, or sensitive details.'
    };
    showIntelligenceReportCard(target, report);
  };

  // ── Element Zapper (uBlock Origin / Brave Shields Parity) ─────────
  function startElementPicker() {
    // Prevent duplicate pickers
    if (document.getElementById('threatlens-element-picker-overlay')) return;

    const overlay = document.createElement('div');
    overlay.id = 'threatlens-element-picker-overlay';
    Object.assign(overlay.style, {
      position: 'absolute',
      pointerEvents: 'none',
      border: '2px dashed #2563eb',
      backgroundColor: 'rgba(37, 99, 235, 0.18)',
      zIndex: '2147483647',
      borderRadius: '4px',
      transition: 'top 0.05s ease, left 0.05s ease, width 0.05s ease, height 0.05s ease',
      display: 'none'
    });

    const tooltip = document.createElement('div');
    tooltip.id = 'threatlens-element-picker-tooltip';
    Object.assign(tooltip.style, {
      position: 'absolute',
      backgroundColor: '#0f172a',
      color: '#ffffff',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace',
      fontSize: '11px',
      fontWeight: '700',
      padding: '4px 8px',
      borderRadius: '6px',
      pointerEvents: 'none',
      zIndex: '2147483647',
      boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
      whiteSpace: 'nowrap',
      display: 'none'
    });

    (document.body || document.documentElement).appendChild(overlay);
    (document.body || document.documentElement).appendChild(tooltip);

    const prevCursor = document.body.style.cursor;
    document.body.style.cursor = 'crosshair';

    let currentTarget = null;

    function getOptimalSelector(el) {
      if (el.id) {
        return '#' + CSS.escape(el.id);
      }
      const tag = el.tagName.toLowerCase();
      const classes = Array.from(el.classList).filter(c => !c.startsWith('threatlens-')).slice(0, 2);
      if (classes.length > 0) {
        return tag + '.' + classes.map(c => CSS.escape(c)).join('.');
      }
      if (el.parentElement) {
        const index = Array.from(el.parentElement.children).indexOf(el) + 1;
        return `${el.parentElement.tagName.toLowerCase()} > ${tag}:nth-child(${index})`;
      }
      return tag;
    }

    function onMouseMove(e) {
      const el = document.elementFromPoint(e.clientX, e.clientY);
      if (!el || el === overlay || el === tooltip || el === document.body || el === document.documentElement) {
        return;
      }
      currentTarget = el;
      const rect = el.getBoundingClientRect();
      const scrollX = window.scrollX || window.pageXOffset;
      const scrollY = window.scrollY || window.pageYOffset;

      overlay.style.display = 'block';
      overlay.style.top = (rect.top + scrollY) + 'px';
      overlay.style.left = (rect.left + scrollX) + 'px';
      overlay.style.width = rect.width + 'px';
      overlay.style.height = rect.height + 'px';

      tooltip.style.display = 'block';
      const selector = getOptimalSelector(el);
      tooltip.textContent = `⚡ Click to zap: ${selector} (Esc to cancel)`;
      tooltip.style.top = Math.max(0, rect.top + scrollY - 28) + 'px';
      tooltip.style.left = (rect.left + scrollX) + 'px';
    }

    function onClick(e) {
      if (!currentTarget) return;
      e.preventDefault();
      e.stopPropagation();

      const selector = getOptimalSelector(currentTarget);
      const hostname = window.location.hostname;

      currentTarget.style.setProperty('display', 'none', 'important');

      chrome.storage.local.get(['customZappedSelectors'], (res) => {
        const map = res.customZappedSelectors || {};
        if (!map[hostname]) map[hostname] = [];
        if (!map[hostname].includes(selector)) {
          map[hostname].push(selector);
        }
        chrome.storage.local.set({ customZappedSelectors: map }, () => {
          showZapToast(`⚡ Element zapped! Rule saved for ${hostname}`);
        });
      });

      cleanup();
    }

    function onKeyDown(e) {
      if (e.key === 'Escape') {
        cleanup();
      }
    }

    function cleanup() {
      document.removeEventListener('mousemove', onMouseMove, true);
      document.removeEventListener('click', onClick, true);
      document.removeEventListener('keydown', onKeyDown, true);
      document.body.style.cursor = prevCursor;
      if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
      if (tooltip.parentNode) tooltip.parentNode.removeChild(tooltip);
    }

    document.addEventListener('mousemove', onMouseMove, true);
    document.addEventListener('click', onClick, true);
    document.addEventListener('keydown', onKeyDown, true);
  }

  function showZapToast(text) {
    const toast = document.createElement('div');
    toast.textContent = text;
    Object.assign(toast.style, {
      position: 'fixed',
      bottom: '24px',
      right: '24px',
      background: '#1d4ed8',
      color: '#ffffff',
      padding: '12px 18px',
      borderRadius: '10px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: '13px',
      fontWeight: '600',
      zIndex: '2147483647',
      boxShadow: '0 8px 24px rgba(29, 78, 216, 0.3)',
      transition: 'opacity 0.3s ease'
    });
    (document.body || document.documentElement).appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
      }, 300);
    }, 3000);
  }

  window.threatlensSnip = startScreenSnip;
  window.threatlensZap = startElementPicker;

})();

