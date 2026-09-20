// surrogates.js
// ThreatLens Ad & Tracker Defusing Scriptlets (Inspired by Brave Shields & uBlock Origin)
// Stubs common tracker APIs with zero-overhead no-op functions to prevent anti-adblock detection and page breakages.

(function() {
  'use strict';

  // 1. Anti-Adblock Defeaters (Neutralize adblock-detection flags)
  try {
    window.canRunAds = true;
    window.isAdBlockActive = false;
    window.adblockDetected = false;
    window.google_ad_status = 1;
    window.AdBlock = { isDetected: false, status: 'disabled' };
  } catch (e) {}

  // 2. Google AdSense / AdsByGoogle Surrogate
  try {
    if (!window.adsbygoogle) {
      window.adsbygoogle = {
        push: function(args) {
          return Array.isArray(args) ? args.length : 0;
        },
        loaded: true
      };
    }
  } catch (e) {}

  // 3. Google Analytics & GTag Surrogate Stubs
  try {
    if (!window.ga) {
      window.ga = function() {};
      window.ga.loaded = true;
      window.ga.create = function() {};
      window.ga.getByName = function() { return null; };
      window.ga.getAll = function() { return []; };
    }

    if (!window.gtag) {
      window.gtag = function() {};
    }

    if (!window.dataLayer) {
      window.dataLayer = [];
    }
  } catch (e) {}

  // 4. Google Publisher Tag (GPT) / DFP Surrogate
  try {
    if (!window.googletag) {
      const dummySlot = {
        addService: function() { return this; },
        setTargeting: function() { return this; },
        setCollapseEmptyDiv: function() { return this; },
        defineSizeMapping: function() { return this; }
      };

      window.googletag = {
        cmd: [],
        display: function() {},
        defineSlot: function() { return dummySlot; },
        defineOutOfPageSlot: function() { return dummySlot; },
        enableServices: function() {},
        pubads: function() {
          return {
            enableSingleRequest: function() {},
            collapseEmptyDivs: function() {},
            clear: function() {},
            refresh: function() {},
            addEventListener: function() {},
            setTargeting: function() {},
            disableInitialLoad: function() {}
          };
        },
        sizeMapping: function() {
          return {
            addSize: function() { return this; },
            build: function() { return []; }
          };
        }
      };

      if (Array.isArray(window.googletag.cmd)) {
        const queue = window.googletag.cmd.slice();
        window.googletag.cmd = {
          push: function(fn) {
            if (typeof fn === 'function') {
              try { fn(); } catch (err) {}
            }
          }
        };
        queue.forEach(fn => {
          if (typeof fn === 'function') {
            try { fn(); } catch (err) {}
          }
        });
      }
    }
  } catch (e) {}

  // 5. Facebook Pixel / Meta Tracker Surrogate
  try {
    if (!window.fbq) {
      window.fbq = function() {};
      window.fbq.loaded = true;
      window.fbq.push = function() {};
    }
  } catch (e) {}

  // 6. Taboola, Outbrain & Criteo Surrogates
  try {
    if (!window._taboola) {
      window._taboola = { push: function() {} };
    }
    if (!window.outbrain) {
      window.outbrain = { push: function() {} };
    }
    if (!window.criteo_q) {
      window.criteo_q = { push: function() {} };
    }
  } catch (e) {}

  // 7. Twitter (X), TikTok, Amazon & Clarity Surrogates
  try {
    if (!window.twq) {
      window.twq = function() {};
    }
    if (!window.ttq) {
      window.ttq = { track: function() {}, page: function() {}, load: function() {}, identify: function() {} };
    }
    if (!window.amznads) {
      window.amznads = { getAds: function() {}, setTargetingForGPT: function() {} };
    }
    if (!window.clarity) {
      window.clarity = function() {};
    }
  } catch (e) {}

})();
