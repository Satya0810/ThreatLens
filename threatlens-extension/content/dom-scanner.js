// dom-scanner.js
// ThreatLens Advanced AdBlock Plus, Video Ad Neutralizer & DOM Deep Packet Inspection (DPI) Shield
// Incorporates architectural patterns from Brave Shields, uBlock Origin, and AdGuard.

(function() {
  'use strict';

  // ── 1. Brave-Style Query Tracker Stripper (Anti-Bounce Tracking) ──
  const trackingParams = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'fbclid', 'gclid', 'msclkid', 'mc_eid', '_ga', 'zanpid', 'igshid', 'yclid', 'twclid', 'dclid'];

  function stripTrackingParams(urlStr) {
    try {
      const url = new URL(urlStr);
      let changed = false;
      for (const param of trackingParams) {
        if (url.searchParams.has(param)) {
          url.searchParams.delete(param);
          changed = true;
        }
      }
      return changed ? url.toString() : urlStr;
    } catch (e) {
      return urlStr;
    }
  }

  // Intercept anchor clicks to strip trackers transparently
  document.addEventListener('click', (e) => {
    const a = e.target.closest ? e.target.closest('a') : null;
    if (a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {
      const cleaned = stripTrackingParams(a.href);
      if (cleaned !== a.href) {
        a.href = cleaned;
      }
    }
  }, true);

  // ── 2. uBlock-Style Pop-up & Pop-under Interception Engine ──
  const originalOpen = window.open;
  const knownAdDomains = [
    'popads.net', 'popcash.net', 'propellerads.com', 'adnxs.com', 'exoclick.com', 
    'clickadu.com', 'adcash.com', 'doubleclick.net', 'ad-maven.com', 'hilltopads.net',
    'trafficstars.com', 'richpush.co', 'mgid.com', 'taboola.com', 'outbrain.com',
    'betting', 'casino', 'jackpot', 'crypto-doubler', 'dating', 'track', 'affiliate',
    'monetag.com', 'adsterra.com', 'juicyads.com', 'trafficjunky.com', 'realsrv.com'
  ];

  let lastUserClickTime = 0;
  document.addEventListener('click', () => {
    lastUserClickTime = Date.now();
  }, true);

  window.open = function(url, target, features) {
    const timeSinceClick = Date.now() - lastUserClickTime;
    const isUnsolicited = timeSinceClick > 1200;

    let isAdOrSpam = false;
    if (url && typeof url === 'string') {
      const lowerUrl = url.toLowerCase();
      isAdOrSpam = knownAdDomains.some(d => lowerUrl.includes(d)) || 
                   lowerUrl.includes('/pop') || 
                   lowerUrl.includes('click.php') || 
                   lowerUrl.includes('redirect.php') ||
                   lowerUrl.includes('track.php');
    }

    if (isAdOrSpam || (isUnsolicited && (!url || url === 'about:blank' || url.startsWith('http')))) {
      console.log("🛡️ ThreatLens Ad Shield: Blocked rogue popup/popunder ->", url || "unsolicited window");
      incrementBlockedCount();
      return null;
    }

    return originalOpen.apply(this, arguments);
  };

  let tabBlockedCount = 0;

  function incrementBlockedCount(amount = 1) {
    tabBlockedCount += amount;
    try {
      if (chrome.storage?.local) {
        chrome.storage.local.get(['adsBlockedCount'], (res) => {
          const count = (res.adsBlockedCount || 0) + amount;
          chrome.storage.local.set({ adsBlockedCount: count });
        });
      }
      chrome.runtime.sendMessage({
        action: "UPDATE_TAB_BLOCKED_COUNT",
        count: tabBlockedCount,
        url: window.location.href
      });
    } catch (e) {}
  }

  // ── 3. Anti-Scam Alert Freeze Loop Defeater ──
  let alertCount = 0;
  let lastAlertTime = 0;
  const originalAlert = window.alert;
  window.alert = function(msg) {
    const now = Date.now();
    if (now - lastAlertTime < 2000) {
      alertCount++;
      if (alertCount > 2) {
        console.warn("🛡️ ThreatLens: Blocked scam alert spam loop:", msg);
        return;
      }
    } else {
      alertCount = 0;
    }
    lastAlertTime = now;
    return originalAlert.apply(this, arguments);
  };

  // ── 4. Comprehensive Cosmetic Ad Shield (CSS Injection) ──
  function injectCosmeticFilters() {
    if (document.getElementById('threatlens-adblock-styles')) return;

    const css = `
      /* ThreatLens Universal Cosmetic Ad Shield */
      ins.adsbygoogle,
      iframe[src*="doubleclick.net"],
      iframe[src*="googlesyndication.com"],
      iframe[src*="googleadservices.com"],
      iframe[src*="googletagservices.com"],
      iframe[src*="adnxs.com"],
      iframe[src*="taboola.com"],
      iframe[src*="outbrain.com"],
      iframe[src*="popads.net"],
      iframe[src*="popcash.net"],
      iframe[src*="propellerads.com"],
      iframe[src*="adcash.com"],
      iframe[src*="clickadu.com"],
      iframe[src*="exoclick.com"],
      iframe[src*="ad-maven.com"],
      iframe[src*="mgid.com"],
      iframe[src*="criteo.com"],
      iframe[src*="adroll.com"],
      iframe[src*="rubiconproject.com"],
      iframe[src*="smartadserver.com"],
      iframe[id*="google_ads"],
      iframe[id*="aswift_"],
      div[id*="google_ads"],
      div[id*="ad-slot"],
      div[id*="ad_banner"],
      div[id*="ad-wrapper"],
      div[id*="dfp-ad-"],
      div[id*="gpt-ad-"],
      div[id*="div-gpt-ad-"],
      div[class*="ad-banner"],
      div[class*="sponsored-post"],
      div[class*="sponsored-content"],
      div[id*="popunder"],
      div[class*="popunder"],
      div[class*="ad_container"],
      div[class*="taboola-"],
      div[class*="outbrain-"],
      div[class*="trc_related_container"],
      div[class*="mgid-"],
      div[class*="revcontent-"],
      /* YouTube Ad Selectors */
      ytd-ad-slot-renderer,
      ytd-in-feed-ad-layout-renderer,
      ytd-banner-promo-renderer,
      ytd-statement-banner-renderer,
      ytd-companion-slot-renderer,
      .video-ads,
      .ytp-ad-module,
      .ytp-ad-overlay-container,
      .ytp-ad-player-overlay,
      .ytp-ad-text-overlay,
      .ytp-ad-message-container,
      .ytp-ad-progress-list,
      .sparkles-light-cta,
      #masthead-ad,
      /* Generic Overlay Selectors */
      a[href*="popads.net"],
      a[href*="popcash.net"],
      a[href*="propellerads.com"],
      a[href*="adsterra.com"] {
        display: none !important;
        visibility: hidden !important;
        height: 0 !important;
        width: 0 !important;
        opacity: 0 !important;
        pointer-events: none !important;
      }
    `;

    const styleEl = document.createElement('style');
    styleEl.id = 'threatlens-adblock-styles';
    styleEl.textContent = css;
    (document.head || document.documentElement).appendChild(styleEl);

    // Apply Custom Zapped Elements (Element Picker Rules)
    try {
      if (chrome.storage?.local) {
        chrome.storage.local.get(['customZappedSelectors'], (res) => {
          const map = res.customZappedSelectors || {};
          const host = window.location.hostname;
          const cleanHost = host.replace(/^www\./, '');
          const selectors = map[host] || map[cleanHost] || map['www.' + cleanHost];
          if (Array.isArray(selectors) && selectors.length > 0) {
            let customStyle = document.getElementById('threatlens-custom-zapped-styles');
            if (!customStyle) {
              customStyle = document.createElement('style');
              customStyle.id = 'threatlens-custom-zapped-styles';
              (document.head || document.documentElement).appendChild(customStyle);
            }
            customStyle.textContent = selectors.map(s => `${s} { display: none !important; }`).join('\n');
          }
        });
      }
    } catch (e) {}
  }

  // ── 5. Video Ad Fast-Forwarder & Auto-Skipper (YouTube & HTML5 Video) ──
  function handleVideoAds() {
    // 1. YouTube & Generic Video Ad Fast-Forward
    const video = document.querySelector('video.video-stream, video.html5-main-video, video');
    const isAdShowing = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');
    
    if (video && isAdShowing) {
      video.muted = true;
      video.playbackRate = 16.0;
      if (video.duration && !isNaN(video.duration)) {
        video.currentTime = video.duration;
      }
    }

    // 2. Auto-click skip button immediately
    const skipButtons = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button, button[class*="skip"], button[class*="Skip"]');
    for (const btn of skipButtons) {
      if (btn && btn.offsetParent !== null) {
        btn.click();
        incrementBlockedCount();
      }
    }
  }

  // ── 6. Anti-Clickjacking Transparent Trap Sweeper ──
  function removeClickjackingTraps() {
    const overlays = document.querySelectorAll('div, a');
    for (const el of overlays) {
      const style = window.getComputedStyle(el);
      if (style.position === 'fixed' || style.position === 'absolute') {
        const zIndex = parseInt(style.zIndex, 10);
        if (zIndex > 999 && (style.opacity === '0' || style.backgroundColor === 'rgba(0, 0, 0, 0)' || style.backgroundColor === 'transparent')) {
          if (el.offsetWidth > window.innerWidth * 0.7 && el.offsetHeight > window.innerHeight * 0.7) {
            if (el.id !== 'threatlens-intelligence-report-root' && el.id !== 'threatlens-snip-overlay') {
              console.log("🛡️ ThreatLens: Neutralized transparent clickjacking trap!");
              el.remove();
              incrementBlockedCount();
            }
          }
        }
      }
    }
  }

  // ── 7. DOM Ad Element Purger (Periodic & Mutation Based) ──
  function purgeAdElements() {
    const adSelectors = [
      'ins.adsbygoogle',
      'iframe[src*="doubleclick"]',
      'iframe[src*="googlesyndication"]',
      'iframe[src*="googleadservices"]',
      'iframe[src*="taboola"]',
      'iframe[src*="outbrain"]',
      'ytd-ad-slot-renderer',
      'ytd-banner-promo-renderer',
      '#masthead-ad',
      '.trc_related_container',
      'div[id*="div-gpt-ad"]',
      'div[id*="google_ads"]'
    ];

    let removed = 0;
    for (const sel of adSelectors) {
      const elements = document.querySelectorAll(sel);
      for (const el of elements) {
        if (el && el.parentElement) {
          el.remove();
          removed++;
        }
      }
    }

    if (removed > 0) {
      incrementBlockedCount(removed);
    }
  }

  // ── 8. Continuous Shield Observer ──
  const observer = new MutationObserver(() => {
    handleVideoAds();
    removeClickjackingTraps();
  });

  const currentHost = window.location.hostname.toLowerCase();

  function checkShieldStatus(callback) {
    try {
      if (chrome.storage?.local) {
        chrome.storage.local.get(['adShieldEnabled', 'whitelistedDomains'], (res) => {
          if (res && res.adShieldEnabled === false) {
            callback(false);
            return;
          }
          const list = res?.whitelistedDomains || [];
          const isWhitelisted = list.some(d => currentHost === d || currentHost.endsWith('.' + d));
          callback(!isWhitelisted);
        });
        return;
      }
    } catch (e) {}
    callback(true);
  }

  function startAdShield() {
    checkShieldStatus((isActive) => {
      if (!isActive) {
        console.log("🛡️ ThreatLens Ad Shield: Paused on this site by user settings.");
        return;
      }

      injectCosmeticFilters();
      removeClickjackingTraps();
      purgeAdElements();
      handleVideoAds();

      if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
      }

      // Periodic sweep for persistent video and dynamic ads
      setInterval(() => {
        handleVideoAds();
        purgeAdElements();
      }, 1000);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startAdShield);
  } else {
    startAdShield();
  }
})();
