# 🏗️ THREATLENS: Deep System Architecture Specification

This document provides the complete, authoritative architectural specification for the **ThreatLens** cybersecurity ecosystem. It details the system using the **C4 Model** (Context, Containers, Components, Code), sequence diagrams, state machines, and isolation matrices across both the Android application and the Chromium Manifest V3 browser extension.

---

## 1. Architectural Philosophy & Design Principles

1. **Defense-in-Depth**: Threat protection operates across multiple independent layers: on-device lexical heuristics, computer vision matrix validation, network-level Declarative Net Request rules, commercial threat intelligence APIs, and generative AI reasoning.
2. **Sub-Second Low Latency**: Security evaluation must not hinder user experience. Synchronous heuristics resolve in under 50ms; asynchronous remote queries complete in parallel with aggressive timeouts (under 750ms).
3. **Offline Resilience**: The core detection engine functions reliably without internet access using compiled regex patterns, offline heuristic tables, and local Room database caching.
4. **Zero-Trust Destination Inspection**: The QR code or link is treated as an untrusted carrier; only the fully expanded, unrolled destination endpoint is evaluated.
5. **Privacy by Design**: No browsing history or raw scan content is transmitted without explicit user action. Telemetry is anonymized and local databases are encrypted with 256-bit AES via SQLCipher.

---

## 2. C4 Architectural Blueprint

### 2.1 Level 1: System Context Diagram

```mermaid
C4Context
    title System Context (C4 Level 1) - ThreatLens Ecosystem

    Person(user, "User / Security Consumer", "Scans QR codes with mobile devices, browses the web on desktop, and downloads files.")
    
    System(threatlens_core, "ThreatLens Security Platform", "Multi-platform cybersecurity system providing real-time Quishing defense, link inspection, ad blocking, and AI threat explanation.")

    System_Ext(google_sb, "Google Safe Browsing API v4", "Global database of malware, social engineering, and unwanted software URLs.")
    System_Ext(virustotal, "VirusTotal v3 API", "Consolidated threat intelligence from 70+ antivirus and domain security engines.")
    System_Ext(abuseipdb, "AbuseIPDB & URLScan.io", "IP address abuse confidence, ASN reputation, and DOM screenshot analysis.")
    System_Ext(llm7_ai, "LLM7.io Cloud AI API", "Neural reasoning engine providing explainable threat summaries and intent evaluation.")
    System_Ext(firebase_cloud, "Firebase Cloud Firestore", "Encrypted cross-device history synchronization and community threat feeds.")

    Rel(user, threatlens_core, "Scans QR codes via camera; clicks links and navigates web")
    Rel(threatlens_core, google_sb, "Queries hash prefixes of destination URLs", "HTTPS/REST")
    Rel(threatlens_core, virustotal, "Queries domain and URL reputation scores", "HTTPS/REST")
    Rel(threatlens_core, abuseipdb, "Checks IP address abuse and network topology", "HTTPS/REST")
    Rel(threatlens_core, llm7_ai, "Sends extracted indicators of compromise for AI explanation", "HTTPS/JSON")
    Rel(threatlens_core, firebase_cloud, "Synchronizes encrypted scan records and blacklists", "HTTPS/gRPC")
```

### 2.2 Level 2: Container Architecture Diagram

```mermaid
C4Container
    title Container Diagram (C4 Level 2) - ThreatLens Infrastructure

    Container(android_app, "Android Mobile App", "Kotlin, Jetpack Compose, CameraX, ML Kit, Room", "Native mobile scanner, on-device heuristic engine, sandbox browser, and parental controls.")
    Container(chrome_ext, "Chromium Extension", "Manifest V3, JavaScript, Declarative Net Request", "Pre-navigation Link Guard, DNR AdShield, scriptlet surrogates, and executable download interceptor.")
    Container(landing_page, "Web Portal & Live Demo", "HTML5, Vanilla CSS3, JavaScript", "Public showcase, interactive URL analyzer demonstration, and APK distribution.")
    Container(cloud_backend, "Firebase & Vercel Functions", "Node.js Serverless", "Community threat reporting, API key proxy, and telemetry aggregation.")
    ContainerDb(firestore_db, "Cloud Firestore", "NoSQL Document Database", "Stores cross-device scan history, verified community reports, and dynamic datasets.")
    ContainerDb(sqlite_db, "Local Room Database", "SQLite + SQLCipher", "Encrypted on-device storage for scan history, parental settings, and whitelists.")

    Rel(android_app, sqlite_db, "Reads/Writes encrypted scan history", "SQLCipher API")
    Rel(android_app, firestore_db, "Synchronizes scan history with user account", "Firebase Android SDK")
    Rel(chrome_ext, cloud_backend, "Submits community reports & queries dynamic lists", "HTTPS/REST")
    Rel(cloud_backend, firestore_db, "Persists verified threats & community reports", "gRPC")
    Rel(android_app, landing_page, "Fetches dynamic dataset configuration", "HTTPS")
```

### 2.3 Level 3: Component Breakdown Diagrams

#### 2.3.1 Android Application Component Architecture

```mermaid
graph TD
    subgraph UI Layer [Jetpack Compose & Material 3]
        UI1[ScannerScreen: Camera Preview & Reticle]
        UI2[ResultBottomSheet: Trust Score & IoC Breakdown]
        UI3[SandboxBrowserScreen: Isolated WebView]
        UI4[HistoryScreen: Encrypted Log Viewer]
        UI5[ParentalControlScreen: PIN & Bedtime Settings]
        UI6[CertifiedQrScreen: QR Generator & Verifier]
    end

    subgraph ViewModel Layer
        VM1[ScannerViewModel: Camera & Scan State]
        VM2[HistoryViewModel: Database Queries]
        VM3[SettingsViewModel: Preference Synchronization]
    end

    subgraph Domain & Analysis Layer
        D1[UrlExpander: 10-Hop Redirect Unroller]
        D2[ThreatAnalyzer: Heuristic & Scoring Engine]
        D3[WebsiteCategorizer: 40+ Category Classifier]
        D4[Llm7Client: Cloud AI Threat Explainer]
    end

    subgraph Data & Persistence Layer
        DA1[ScanRepository: Unified Data Coordinator]
        DA2[ThreatLensDatabase: Room + SQLCipher 256-bit AES]
        DA3[ThreatApiClient: Retrofit Remote Service]
        DA4[PreferencesManager: DataStore & EncryptedSharedPreferences]
    end

    UI1 --> VM1
    UI2 --> VM1
    UI3 --> VM1
    UI4 --> VM2
    UI5 --> VM3
    UI6 --> VM1

    VM1 --> DA1
    VM2 --> DA1
    VM3 --> DA4

    DA1 --> D1
    DA1 --> D2
    DA1 --> D3
    DA1 --> D4
    DA1 --> DA2
    DA1 --> DA3
```

#### 2.3.2 Chrome Extension Component Architecture

```mermaid
graph TD
    subgraph Browser Context & UI
        E1[popup.html / popup.js: Status Ring & Controls]
        E2[dashboard.html / dashboard.js: AdShield Hub]
        E3[content-script.js: In-Page Cyber HUD]
        E4[dom-scanner.js: AdShield & Clickjacking Sweeper]
        E5[surrogates.js: Scriptlet Anti-Adblock Neutralizer]
    end

    subgraph Background Service Worker [service-worker.js]
        SW1[webNavigation.onBeforeNavigate: Pre-Navigation Guard]
        SW2[chrome.downloads: Binary Interceptor]
        SW3[chrome.declarativeNetRequest: Dynamic Whitelist Sync]
        SW4[Offscreen Canvas + jsQR: Image QR Decoder]
        SW5[ThreatAnalyzer Engine: JS Heuristic Port]
        SW6[Capped Cache: FIFO LRU Active Threat Reports]
    end

    E1 -->|chrome.runtime.sendMessage| SW1
    E2 -->|chrome.runtime.sendMessage| SW3
    E3 -->|chrome.runtime.sendMessage| SW5
    E4 -->|chrome.runtime.sendMessage| SW3
    SW1 --> SW5
    SW5 --> SW6
```

---

## 3. Runtime Execution Lifecycle & Sequence Diagrams

### 3.1 End-to-End QR Code Scan & Threat Evaluation Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Cam as CameraX / ML Kit
    participant Exp as UrlExpander
    participant Heur as ThreatAnalyzer (Local)
    participant APIs as Remote Threat APIs
    participant AI as LLM7.io Cloud AI
    participant UI as ResultBottomSheet
    participant DB as SQLCipher Room DB

    User->>Cam: Points camera at QR code
    Cam->>Cam: ML Kit detects barcode matrix (<45ms)
    Cam->>Exp: Passes raw payload string
    
    alt Payload is Shortened URL
        Exp->>Exp: Recursively follows HTTP redirects (up to 10 hops)
        Exp-->>Heur: Returns authentic destination URL
    else Payload is Direct URL / Plain Text
        Exp-->>Heur: Returns original payload
    end

    par Synchronous Local Heuristics
        Heur->>Heur: Checks Shannon entropy, homoglyphs, IP hosts, suspicious TLDs (<15ms)
        Heur-->>UI: Emits immediate heuristic risk score
    and Asynchronous Remote Queries
        Heur->>APIs: Concurrent queries (Google Safe Browsing, VirusTotal, AbuseIPDB)
        APIs-->>Heur: Returns threat intelligence consensus
    and AI Threat Explanation
        Heur->>AI: Sends extracted IoCs and domain structure
        AI-->>Heur: Returns 2-sentence natural language explanation
    end

    Heur->>Heur: Computes composite Trust Score (0–100)
    Heur->>DB: Persists encrypted scan record
    Heur->>UI: Renders complete Threat Report Card
    UI-->>User: Displays visual verdict (SAFE / CAUTION / MALICIOUS)
```

### 3.2 Pre-Navigation Link Guard & Cyber HUD Sequence (Browser Extension)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Page as Web Page DOM
    participant CS as content-script.js
    participant SW as service-worker.js
    participant DNR as Declarative Net Request

    User->>Page: Clicks anchor link (<a href="...">)
    Page->>CS: Capture mousedown/click event
    CS->>CS: Check if internal/same-site or search engine pagination
    
    alt External Untrusted Link
        CS->>CS: e.preventDefault() & e.stopImmediatePropagation()
        CS->>CS: Inject Shadow DOM Cyber HUD (Concentric Radar Rings)
        CS->>SW: sendMessage({ action: "ANALYZE_URL", url })
        
        SW->>SW: Evaluate heuristics & query threat cache
        SW-->>CS: Return safety status & trust score
        
        alt Verified Safe (Score >= 75)
            CS->>CS: HUD transitions to green "Safe State" (240ms)
            CS->>Page: window.location.href = destinationUrl
        else Threat Detected (Score < 75)
            CS->>CS: HUD transitions to red "Threat State"
            CS->>CS: Mounts Intelligence Report Card overlay with bypass confirmation
        end
    else Internal or Whitelisted Link
        CS->>Page: Allow native browser navigation
    end
```

---

## 4. Threat Scoring Mathematical Model

ThreatLens calculates the composite **Trust Score ($T$)** on a deterministic scale of $0$ to $100$:

$$T = \max\left(0, \min\left(100, 100 - \sum P_{\text{heuristic}} - \sum P_{\text{api}} + B_{\text{trust}}\right)\right)$$

### 4.1 Heuristic Penalty Matrix ($\sum P_{\text{heuristic}}$)

| Indicator of Compromise (IoC) | Penalty ($P$) | Trigger Condition |
| :--- | :---: | :--- |
| **Homoglyph / Punycode Lookalike** | $80$ | Domain contains Cyrillic/Greek characters or begins with `xn--`. |
| **Direct IP Host Address** | $65$ | Hostname matches IPv4 pattern `^(\d{1,3}\.){3}\d{1,3}$`. |
| **Authority Section Phishing** | $80$ | URL contains `@` before hostname (credential harvesting trick). |
| **High-Risk Executable Extension** | $90$ | URL path ends with `.exe`, `.scr`, `.bat`, `.vbs`, `.iso`, `.apk`. |
| **High Shannon Entropy ($H > 4.2$)** | $30$ | Calculated domain character entropy indicates DGA generation. |
| **Excessive Subdomain Depth** | $25$ | Hostname contains $>3$ subdomain levels (e.g., `a.b.c.d.evil.com`). |
| **High-Risk TLD Abuse** | $40$ | Domain ends in `.xyz`, `.top`, `.pw`, `.buzz`, `.gripe`, `.bet`, `.lol`. |
| **Brand Spoofing Keyword** | $50$ | Known brand (e.g., `paypal`, `google`) present in non-official domain. |

### 4.2 API Penalty Matrix ($\sum P_{\text{api}}$)

| Threat API Feed | Penalty ($P$) | Trigger Condition |
| :--- | :---: | :--- |
| **Google Safe Browsing v4** | $100$ | Match found in `MALWARE`, `SOCIAL_ENGINEERING`, or `UNWANTED_SOFTWARE`. |
| **VirusTotal Consensus** | $20 \times N$ | Flagged by $N$ independent antivirus engines (capped at $100$). |
| **AbuseIPDB Abuse Score** | $\text{Score} \times 0.8$ | Confidence score $> 25\%$ reported by abuse community. |
| **URLhaus Malware Feed** | $100$ | URL or host listed in active payload distribution database. |

### 4.3 Trust Bonus Matrix ($B_{\text{trust}}$)

| Trust Signal | Bonus ($B$) | Condition |
| :--- | :---: | :--- |
| **Verified Top 1M Alexa/Tranco** | $+10$ | Domain verified in global top authority rankings. |
| **Valid Extended Validation SSL** | $+5$ | Certificate issued by trusted root CA with high-grade TLS 1.3 cipher. |
| **Domain Registration Age $> 2$ Years** | $+5$ | Domain registered $> 730$ days ago with stable DNS history. |

### 4.4 Verdict Thresholds

```
   0                     50                    80                   100
   ┌─────────────────────┬─────────────────────┬──────────────────────┐
   │   🔴 MALICIOUS      │    🟡 CAUTION       │      🟢 SAFE         │
   │  Access Blocked     │   Warning Dialog    │   Direct Access      │
   └─────────────────────┴─────────────────────┴──────────────────────┘
```

---

## 5. Sandbox Browser Security & Isolation Matrix

The Android `SandboxBrowserScreen.kt` provides an isolated runtime environment for inspecting suspicious web destinations:

| Security Attribute | Standard Chrome / WebView | ThreatLens Isolated Sandbox | Security Benefit |
| :--- | :--- | :--- | :--- |
| **Cookie Persistence** | Enabled (Persistent across sessions) | **Disabled** (`CookieManager.removeAllCookies()`) | Prevents session hijacking and persistent tracking across sites. |
| **DOM Storage (localStorage)** | Enabled | **Disabled** (`domStorageEnabled = false`) | Blocks persistent client-side identifier storage. |
| **Cache & Form Autofill** | Enabled | **Disabled** (No cache, no saved passwords) | Prevents credential leaks from spoofed input fields. |
| **Pop-up Windows** | Allowed with permission | **Blocked** (`onCreateWindow` returns false) | Eliminates malicious pop-unders and deceptive overlays. |
| **Ad & Tracker Network Requests**| Allowed | **Intercepted with HTTP 204** | Saves bandwidth, neutralizes pixel trackers, and speeds page load. |
| **Child Lock / Bedtime Lock** | None | **Enforced** (PIN-protected schedules) | Protects younger users from inappropriate content and late-night usage. |

---

## 6. Declarative Net Request (DNR) Architecture

ThreatLens implements native Manifest V3 network filtering using `chrome.declarativeNetRequest`:

```mermaid
graph TD
    subgraph DNR Ruleset Architecture
        R1[Static Ruleset: adblock_ads.json - 15,000+ Rules]
        R2[Static Ruleset: adblock_trackers.json - 10,000+ Rules]
        R3[Static Ruleset: adblock_malware.json - 5,000+ Rules]
        R4[Dynamic Ruleset: User Whitelist - IDs 10000..19999]
    end

    subgraph Network Evaluation Pipeline
        NET[Incoming Browser Network Request] --> CHECK{Is Domain in Whitelist?}
        CHECK -->|Yes| ALLOW[Action: ALLOW - Priority 100]
        CHECK -->|No| MATCH{Matches Static Rules?}
        MATCH -->|Ad / Tracker / Malware| BLOCK[Action: BLOCK - Priority 1]
        MATCH -->|No Match| PASS[Action: ALLOW Default]
    end

    R4 --> CHECK
    R1 --> MATCH
    R2 --> MATCH
    R3 --> MATCH
```

### 6.1 Scriptlet Surrogates (`surrogates.js`)
To prevent websites from detecting ad-blockers and showing anti-adblock paywalls or disabling functionality, ThreatLens injects zero-overhead scriptlet surrogates in the main world:
- **`window.adsbygoogle`**: Stubs Google AdSense with a dummy push handler and `loaded = true`.
- **`window.ga` & `window.gtag`**: Stubs Google Analytics with no-op functions.
- **`window.googletag`**: Stubs Google Publisher Tag (GPT) / DFP with mock slot definitions and empty queue executors.
- **`window.canRunAds` & `window.isAdBlockActive`**: Forces anti-adblock detection variables to report that ads are running normally.

---

## 7. Fault Tolerance & Memory Management

1. **Service Worker Lifetime & FIFO Eviction**:
   - MV3 service workers can be terminated by Chrome when idle. ThreatLens stores essential settings in `chrome.storage.local`.
   - In-memory data structures (`activeThreatReports` and `notifiedDownloads`) are wrapped in bounded `CappedMap` and `CappedSet` structures capped at 100 entries to prevent memory leaks during long-running sessions.
2. **MutationObserver Throttling**:
   - `dom-scanner.js` employs a 200ms `requestAnimationFrame` debounce to prevent forced reflows and frame drops on heavy single-page applications.
3. **Safe Interception Error Boundaries**:
   - All network intercepts, image decoders, and API queries are wrapped in strict `try/catch` blocks with deterministic fallbacks, ensuring that an API outage never locks up the user's browser.
