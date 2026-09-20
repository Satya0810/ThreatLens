# 🛠️ THREATLENS: Developer Onboarding & Architecture Guide

This guide provides developers, researchers, and contributors with the step-by-step instructions required to set up the development environment, build the Android application, load the Chromium browser extension, and execute unit and integration tests across the **ThreatLens** codebase.

---

## 1. Prerequisites & Tooling

Before cloning and building the project, ensure your workstation meets the following minimum toolchain requirements:

| Tool / SDK | Minimum Version | Recommended Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Java Development Kit (JDK)** | OpenJDK 17 | OpenJDK 17 or 21 | Android Gradle build toolchain |
| **Android Studio** | Hedgehog (2023.1) | Ladybug (2024.2) / Meerkat | Android IDE, SDK Manager & Emulator |
| **Android SDK** | API Level 26 | API Level 35 (Android 15) | Mobile compilation target |
| **Node.js** | v18.16.0 LTS | v20.x LTS | Extension testing, Cloud Functions, and scripts |
| **Python** | 3.10 | 3.11+ | AI training pipeline, report generation |
| **Chromium Browser** | v116+ | Latest Chrome / Edge / Brave | Extension development & Manifest V3 runtime |

---

## 2. Workspace Structure

```
ThreatLens/
├── app/                              # Android Native Application
│   ├── src/main/java/com/safeqr/scanner/
│   │   ├── ui/                       # Jetpack Compose UI Screens & ViewModels
│   │   ├── data/                     # ThreatAnalyzer, UrlExpander, Llm7Client, Room DB
│   │   └── data/remote/              # Threat API Retrofit Services
│   ├── src/main/res/                 # Icons, Drawables, Values
│   └── build.gradle.kts              # Android App Gradle Configuration
├── threatlens-extension/             # Chromium Manifest V3 Extension
│   ├── background/service-worker.js  # Background Service Worker & Interceptors
│   ├── content/                      # content-script.js, dom-scanner.js, surrogates.js
│   ├── pages/                        # dashboard.html/js/css, sandbox.html
│   ├── popup/                        # popup.html/js/css
│   ├── rules/                        # adblock_ads.json, adblock_trackers.json, adblock_malware.json
│   └── manifest.json                 # Extension Configuration (MV3)
├── landing-page/                     # Public Showcase & Live Demo Webpage
├── functions/                        # Firebase Cloud Functions (Node.js)
├── docs/                             # Complete Master Documentation Suite
├── local.properties.example          # Sample API Keys Configuration
├── build.gradle.kts                  # Root Gradle Configuration
└── settings.gradle.kts               # Gradle Project Settings
```

---

## 3. Android Application Setup & Build Guide

### 3.1 Environment Configuration (`local.properties`)
Create a file named `local.properties` in the project root directory (or copy from `local.properties.example`):

```properties
# Location of the Android SDK
sdk.dir=C:\\Users\\<username>\\AppData\\Local\\Android\\Sdk

# Threat Intelligence API Keys
SAFE_BROWSING_KEY=your_google_safe_browsing_key
VIRUS_TOTAL_KEY=your_virustotal_api_key
URL_SCAN_IO_KEY=your_urlscan_api_key
ABUSE_IPDB_KEY=your_abuseipdb_api_key
WHOIS_XML_KEY=your_whoisxml_api_key
CLOUDFLARE_KEY=your_cloudflare_radar_key

# Neural Threat Explanation API Key
LLM7_API_KEY=your_llm7_api_key
```

### 3.2 Building the Debug APK
Open a terminal in the project root directory and execute:

```powershell
# On Windows (PowerShell)
.\gradlew.bat assembleDebug

# On Linux / macOS
./gradlew assembleDebug
```

The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3.3 Installing the APK on a Physical Device or Emulator
Ensure USB Debugging is enabled on your device:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3.4 Running Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

---

## 4. Chromium Extension Setup & Debugging Guide

### 4.1 Loading the Unpacked Extension in Developer Mode
1. Open Google Chrome, Microsoft Edge, or Brave.
2. Navigate to `chrome://extensions` in the address bar.
3. Enable the **Developer mode** toggle switch in the top-right corner.
4. Click the **Load unpacked** button.
5. Select the `threatlens-extension/` directory from this repository:
   ```text
   c:\AndroidProjects\ThreatLens_FINAL_v2\ThreatLens\threatlens-extension
   ```
6. The **ThreatLens** extension card will appear with its shield icon.

### 4.2 Inspecting Background Service Worker Logs
1. On `chrome://extensions`, locate the **ThreatLens** card.
2. Click the link next to **Inspect views: service worker**.
3. Chrome DevTools will open, displaying real-time logs for:
   - `webNavigation.onBeforeNavigate` link intercepts.
   - Declarative Net Request dynamic ruleset updates.
   - Paused binary downloads and Chrome notifications.

### 4.3 Testing Link Guard & Cyber HUD
1. Navigate to any search engine (e.g., Bing or Google).
2. Click on any external search result link.
3. The in-page **Cyber HUD** with concentric Loanzo Light radar rings will appear instantly, analyzing the target destination before navigation proceeds.

---

## 5. Coding Standards & Architecture Guidelines

### 5.1 Kotlin & Jetpack Compose Standards
1. **Unidirectional Data Flow (UDF)**:
   - UI emits events to ViewModel (e.g., `onScanClicked(payload)`).
   - ViewModel updates immutable `StateFlow<ScanUiState>`.
   - Composable functions read `uiState` and render declaratively.
2. **Lifecycle Awareness**:
   - CameraX preview and analyzer must be bound to the `LocalLifecycleOwner.current`.
   - Release camera hardware immediately when navigating away from `ScannerScreen`.
3. **Thread Safety in WebView Client**:
   - `shouldInterceptRequest` executes on a background thread pool. Always dispatch UI state updates (e.g., `adBlockedCount++`) to the main looper via `Handler(Looper.getMainLooper()).post`.

### 5.2 JavaScript & Manifest V3 Standards
1. **No Unbounded Global State**:
   - Service workers are ephemeral. Store persistent state in `chrome.storage.local`.
   - In-memory caches (`activeThreatReports`, `notifiedDownloads`) must use bounded collections with FIFO/LRU eviction (max 100 entries).
2. **Async Message Port Rule**:
   - In `chrome.runtime.onMessage.addListener`, if `sendResponse` will be called asynchronously, the listener **must** return `true` synchronously.
3. **DOM Mutation Throttling**:
   - In `dom-scanner.js`, never execute `document.querySelectorAll` across the entire document inside an unthrottled `MutationObserver`. Always throttle with a 200ms `requestAnimationFrame` debounce.
