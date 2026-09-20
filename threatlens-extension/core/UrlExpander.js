// UrlExpander.js
// Port of ThreatLens Android UrlExpander.kt

export class UrlExpander {

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
