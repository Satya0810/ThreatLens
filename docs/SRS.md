# 📄 THREATLENS: Software Requirements Specification (SRS)
### Standard: IEEE Std 830-1998 Conforming Specification

---

## 1. Introduction

### 1.1 Purpose
This Software Requirements Specification (SRS) document details the complete functional, non-functional, interface, and performance requirements for the **ThreatLens** cybersecurity ecosystem. This specification governs both the native Android mobile application and the Chromium Manifest V3 browser extension.

### 1.2 Scope
ThreatLens is a multi-platform threat intelligence and security platform designed to detect, explain, and neutralize modern cyber threats, specifically:
- Malicious QR code attacks (**Quishing**) and deceptive physical barcode overlays.
- Multi-hop shortened URL redirects and credential-harvesting homoglyphs.
- Drive-by executable file downloads and binary malware distribution.
- Intrusive tracking networks, telemetry pixels, and advertising exchanges.
- Transparent clickjacking traps and rogue popup/popunder loops.
- Accidental exposure of minors to adult content, scams, and excessive screen time.

### 1.3 Definitions, Acronyms & Abbreviations
- **Quishing**: QR Code Phishing; embedding malicious URLs inside QR codes to deceive victims.
- **DNR**: Declarative Net Request; the modern Manifest V3 network filtering API in Chromium.
- **IoC**: Indicator of Compromise; forensic artifacts (IPs, hashes, domain characteristics) indicating an attack.
- **SQLCipher**: An extension providing transparent, 256-bit AES encryption of SQLite database files.
- **HMAC**: Hash-based Message Authentication Code; used for anti-tamper QR code signing.
- **XAI**: Explainable Artificial Intelligence; providing plain-language justifications for security verdicts.
- **HUD**: Heads-Up Display; an on-screen visual overlay presenting real-time analysis status.

---

## 2. Overall Description

### 2.1 Product Perspective
ThreatLens operates as an independent security suite that integrates seamlessly into existing workflows:
- On mobile devices, it functions as a default QR scanner, default browser choice, and secure sandbox.
- On desktop workstations, it functions as a background browser extension protecting users across all visited web pages.

### 2.2 User Characteristics
- **General Consumers**: Non-technical users who require intuitive, one-tap scanning, automatic ad blocking, and plain-English safety explanations without confusing security jargon.
- **Parents & Guardians**: Users who require child safety controls, bedtime schedules, and content filtering for family devices.
- **Security-Conscious Professionals**: Advanced users who inspect deep threat breakdowns, domain registration ages, SSL cipher suites, and individual API consensus scores.

### 2.3 Operating Environment
- **Mobile OS**: Android 8.0 (API Level 26) through Android 15 (API Level 35).
- **Desktop Browsers**: Google Chrome (v116+), Microsoft Edge (v116+), Brave, Opera, and other Chromium-based browsers supporting Manifest V3.
- **Backend Environment**: Firebase Cloud Firestore, Firebase Cloud Functions (Node.js 18+), Vercel Serverless.

---

## 3. Functional Requirements

### 3.1 Mobile Core & Quishing Defense (Android)

#### **FR-01: High-Speed ML Kit Camera QR Detection**
- **Description**: The system shall continuously process live camera frames and decode QR code matrices.
- **Input**: Video stream from device camera via CameraX.
- **Processing**: Google ML Kit Barcode Scanning engine tuned specifically for `FORMAT_QR_CODE`.
- **Output**: Decoded raw string payload; triggers haptic vibration and audio confirmation beep.
- **Latency Requirement**: Frame processing and decoding shall execute in $< 50\text{ ms}$ on mid-range hardware.

#### **FR-02: Recursive Redirect Unrolling**
- **Description**: The system shall follow HTTP redirect chains to uncover the true final landing URL.
- **Input**: Raw shortened or gateway URL (e.g., `bit.ly`, `tinyurl.com`, `t.co`, Bing `/ck/a`, Google `/url?q`).
- **Processing**: Recursively performs HTTP `HEAD`/`GET` requests up to a maximum depth of 10 hops, following `301`, `302`, `307`, and `308` response headers.
- **Output**: The resolved destination URL and the complete array of intermediate hops (`redirectChain`).

#### **FR-03: On-Device Deterministic Heuristic Risk Scoring**
- **Description**: The system shall evaluate the destination URL against deterministic heuristic security rules offline without external network dependency.
- **Input**: Fully resolved destination URL string.
- **Processing**:
  - Calculates Shannon entropy of the domain name ($H > 4.2$ flagged as DGA).
  - Checks for Cyrillic/Greek homoglyphs and `xn--` Punycode prefixes.
  - Checks for raw IPv4/IPv6 host addresses.
  - Detects `@` authority section credential-harvesting patterns.
  - Evaluates subdomain depth (penalizing $>3$ subdomain levels).
  - Matches against high-risk top-level domains (`.xyz`, `.top`, `.pw`, `.buzz`, `.gripe`, `.bet`).
- **Output**: Local Risk Score ($0–100$), safety status (`SAFE`, `CAUTION`, `MALICIOUS`), and specific threat flags.

#### **FR-04: Multi-API Threat Aggregation**
- **Description**: The system shall concurrently query external commercial threat feeds when online.
- **Input**: Destination URL and domain name.
- **Processing**: Dispatches concurrent asynchronous REST requests to:
  1. Google Safe Browsing API v4 (`threatMatches:find`).
  2. VirusTotal v3 API (`/urls`).
  3. AbuseIPDB API v2 (`/check`).
  4. URLScan.io API (`/scan`).
- **Output**: Aggregated threat score reflecting consensus across all reporting engines.
- **Timeout**: Enforces a strict 750ms timeout per API to avoid blocking user interaction.

#### **FR-05: AI-Powered Explainable Threat Reasoning (LLM7.io)**
- **Description**: The system shall generate a plain-English, 2-sentence summary explaining why a link is risky.
- **Input**: Extracted IoCs, heuristic flags, and domain classification.
- **Processing**: Invokes the **LLM7.io Cloud AI API** with a deterministic prompt enforcing brevity and factual clarity.
- **Output**: Natural-language explanation displayed directly on the Threat Report Card.

#### **FR-06: Isolated Sandbox Browser**
- **Description**: The system shall provide an isolated WebView for safe web exploration.
- **Processing**:
  - Disables cookie persistence (`CookieManager.removeAllCookies()`).
  - Disables DOM storage (`domStorageEnabled = false`).
  - Disables popup window creation (`onCreateWindow` returns false).
  - Neutralizes JavaScript execution when strict mode is enabled.
- **Output**: Secure, non-persistent browsing session with zero risk to host device storage.

#### **FR-07: HTTP 204 Ad & Tracker Interception in Sandbox**
- **Description**: The Sandbox Browser shall intercept network requests matching ad and tracker patterns.
- **Processing**: Overrides `WebViewClient.shouldInterceptRequest`. Matches requests against known ad/tracker domains.
- **Output**: Returns `WebResourceResponse("text/plain", "UTF-8", 204, "No Content", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))`.

#### **FR-08: Parental Controls & Bedtime Scheduling**
- **Description**: The system shall allow parents to protect children through PIN-secured restrictions.
- **Processing**:
  - Automatically blocks explicit adult content, gambling sites, and known online gaming scam portals.
  - Enforces bedtime hours (e.g., 21:00 to 07:00), disabling web navigation during restricted times.
  - Allows custom domain whitelisting and blacklisting.
- **Security**: Parental settings are secured behind an encrypted 4-digit PIN hash.

#### **FR-09: Cryptographically Signed Certified QR Codes**
- **Description**: The system shall generate tamper-evident QR codes with cryptographic authenticity verification.
- **Processing**:
  - Calculates HMAC-SHA256 signature over `{payload, trust_score, issued_at}` using an app master secret.
  - Encodes the signature into the QR payload schema.
  - Decodes and verifies the signature when scanned by another ThreatLens client.
- **Output**: Verified authenticity badge confirming the QR code has not been altered or tampered with.

#### **FR-10: Encrypted Local Database Storage (Room + SQLCipher)**
- **Description**: The system shall persist scan records locally in an encrypted database.
- **Processing**: Room ORM configured with SQLCipher using 256-bit AES encryption.
- **Key Storage**: Database passphrase derived securely from the Android KeyStore.

---

### 3.2 Desktop Browser Extension (Manifest V3)

#### **FR-11: Pre-Navigation Link Guard & Cyber HUD**
- **Description**: The extension shall intercept link clicks before the browser navigates to the destination.
- **Processing**:
  - Captures `click`, `mousedown`, and `pointerdown` events on anchor tags.
  - Bypasses internal same-site links and search engine pagination.
  - For external links, halts navigation and displays the Cyber HUD overlay.
  - Analyzes the target URL in the background service worker.
- **Output**: Transitions to green "Safe State" and proceeds with navigation if clean, or displays red Threat Warning with bypass confirmation.

#### **FR-12: Declarative Net Request (DNR) Ad & Tracker Shield**
- **Description**: The extension shall block advertising, tracking, and malware requests at the network layer.
- **Processing**: Uses `chrome.declarativeNetRequest` with static rulesets:
  - `adblock_ads.json` (15,000+ rules).
  - `adblock_trackers.json` (10,000+ rules).
  - `adblock_malware.json` (5,000+ rules).
- **Output**: Blocks requests with zero JavaScript thread overhead and zero CPU usage on the main thread.

#### **FR-13: Scriptlet Surrogates for Anti-Adblock Defeating**
- **Description**: The extension shall inject surrogate scriptlets into the page's main world to defuse ad-blocker detection scripts.
- **Processing**: Stubs `window.adsbygoogle`, `window.ga`, `window.gtag`, and `window.googletag` with dummy no-op objects.
- **Output**: Pages load cleanly without displaying "Please disable your ad blocker" popups or paywalls.

#### **FR-14: Executable Download Interceptor**
- **Description**: The extension shall pause high-risk executable file downloads for user review.
- **Processing**: Listens on `chrome.downloads.onCreated` and `chrome.downloads.onDeterminingFilename`. Checks for extensions: `.exe`, `.scr`, `.bat`, `.vbs`, `.iso`, `.msi`, `.apk`, `.cmd`, `.ps1`.
- **Output**: Pauses download and triggers a high-priority Chrome Notification with options to "Resume Download" or "Cancel & Delete".

#### **FR-15: Element Zapper ("Zap Element")**
- **Description**: The extension shall allow users to click and permanently hide annoying DOM elements on any page.
- **Processing**: Injects an element-picker crosshair. When clicked, computes the CSS selector and persists it to `chrome.storage.local`. Injects custom style tag hiding the element on future page visits.

#### **FR-16: Screen-Snip Area QR Scanner (Alt+Q)**
- **Description**: The extension shall allow users to capture any region of their screen to detect and decode QR codes.
- **Processing**: Captures visible tab via `chrome.tabs.captureVisibleTab`, crops selected bounding box, and decodes via `OffscreenCanvas` and `jsQR`.
- **Output**: Displays the decoded payload and its complete threat analysis directly on screen.

---

## 4. Non-Functional Requirements (NFRs)

| ID | Category | Requirement Description | Success Metric |
| :--- | :--- | :--- | :--- |
| **NFR-01** | **Performance** | On-device heuristic analysis shall complete in $< 50\text{ ms}$. Remote API aggregation shall complete in $< 750\text{ ms}$. | P95 latency $< 800\text{ ms}$ total scan time. |
| **NFR-02** | **Memory** | Background service worker and content script caches must be bounded. | Max 100 entries in memory with FIFO/LRU eviction. |
| **NFR-03** | **Frame Rate** | DOM scanning and MutationObservers must not degrade webpage rendering. | Maintain 60 FPS scrolling on heavy SPAs (YouTube, Twitter/X). |
| **NFR-04** | **Security** | Local scan history must be encrypted against device extraction. | 256-bit AES encryption via SQLCipher. |
| **NFR-05** | **Privacy** | No personal browsing history or un-scanned URLs shall be transmitted to cloud servers. | Zero telemetry leakage verified via network proxy auditing. |
| **NFR-06** | **Reliability** | The system must remain fully functional when device is offline. | Local heuristics and offline cached threat lists operate without internet. |
| **NFR-07** | **Availability** | Cloud API proxy and Firebase sync services shall maintain high availability. | 99.9% uptime for cloud functions. |
| **NFR-08** | **Usability** | Threat verdicts must be understandable by non-technical consumers. | Color-coded status (Safe/Caution/Malicious) with 2-sentence plain English summaries. |
| **NFR-09** | **Compatibility** | The system shall support diverse Android hardware and Chromium browsers. | Android API 26..35; Chrome/Edge/Brave v116+. |
| **NFR-10** | **Battery Efficiency**| CameraX and background listeners must release hardware resources immediately when not scanning. | $< 1\%$ battery consumption in typical daily usage. |

---

## 5. Requirements Traceability Matrix

| Functional Requirement | Target Platform | Core Implementation Component | Test Case ID |
| :--- | :--- | :--- | :--- |
| **FR-01: ML Kit Camera Scan** | Android | `ScannerScreen.kt`, `CameraX` | `TC-AND-01` |
| **FR-02: Redirect Unrolling** | Android & Extension | `UrlExpander.kt`, `UrlExpander.js` | `TC-COR-01` |
| **FR-03: Local Heuristics** | Android & Extension | `ThreatAnalyzer.kt`, `HeuristicsEngine.js` | `TC-COR-02` |
| **FR-04: Threat APIs** | Android & Extension | `ThreatApiClients.kt`, `service-worker.js` | `TC-API-01` |
| **FR-05: AI Explanation** | Android & Extension | `Llm7Client.kt`, `Llm7Client.js` | `TC-AI-01` |
| **FR-06: Sandbox Browser** | Android | `SandboxBrowserScreen.kt` | `TC-AND-02` |
| **FR-07: HTTP 204 Ad Intercept**| Android | `SandboxBrowserScreen.kt` | `TC-AND-03` |
| **FR-08: Parental Controls** | Android | `ParentalControlScreen.kt`, `PreferencesManager.kt`| `TC-AND-04` |
| **FR-09: Certified QR** | Android & Extension | `CertifiedQrScreen.kt`, `CertificateEngine.js` | `TC-SEC-01` |
| **FR-10: SQLCipher Storage** | Android | `ThreatLensDatabase.kt`, `Room` | `TC-SEC-02` |
| **FR-11: Link Guard HUD** | Extension | `content-script.js`, `service-worker.js` | `TC-EXT-01` |
| **FR-12: DNR AdShield** | Extension | `adblock_ads.json`, `adblock_trackers.json` | `TC-EXT-02` |
| **FR-13: Scriptlet Surrogates** | Extension | `surrogates.js` | `TC-EXT-03` |
| **FR-14: Download Guard** | Extension | `service-worker.js` (`chrome.downloads`) | `TC-EXT-04` |
| **FR-15: Element Zapper** | Extension | `popup.js`, `content-script.js`, `dom-scanner.js`| `TC-EXT-05` |
| **FR-16: Screen Snip (Alt+Q)** | Extension | `service-worker.js`, `content-script.js`, `jsQR.js`| `TC-EXT-06` |
