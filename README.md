<p align="center">
  <img src="app/src/main/res/drawable/ic_threatlens_logo.png" width="120" alt="ThreatLens Logo"/>
</p>

<h1 align="center">ThreatLens</h1>

<p align="center">
  <b>Cyber-Centric QR Code Security Scanner for Android</b><br/>
  <i>Real-time threat intelligence · Multi-API analysis · On-device heuristics</i>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#getting-started">Getting Started</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#project-structure">Project Structure</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#license">License</a>
</p>

---

## The Problem: The QR Code is NOT the threat. The Destination Is.

Standard camera apps and QR scanners have a massive security flaw: they treat QR codes blindly. A QR code is simply a barcode containing text (usually a URL). The danger isn't the QR code graphic itself; the danger is the **site it forces your phone to open**.

When you scan a code at a restaurant, parking meter, or in an email, the native camera app instantly opens the link without analyzing the destination website. Bad actors exploit this through **Quishing** (QR code phishing), pasting malicious QR codes over legitimate ones to direct users to credential-stealing pages, malware downloads, or scam payment portals.

## The Solution

**ThreatLens** acts as a real-time security firewall for QR codes. When you scan a QR code, ThreatLens doesn't just read the link — it:

1. **Expands** shortened URLs and unrolls redirect chains
2. **Analyzes** the destination with on-device heuristic rules (offline, instant)
3. **Queries** 12+ world-class threat intelligence APIs concurrently
4. **Classifies** content type (adult, piracy, payment, phishing, etc.)
5. **Calculates** a multi-dimensional trust score (0–100)
6. **Blocks** malicious execution with a visual safety verdict

---

## Features

| Feature | Description |
|---|---|
| 🎯 **Real-Time Camera Scan** | CameraX + ML Kit Barcode Scanning with low-latency detection |
| 🔗 **URL Redirect Unrolling** | Recursively follows HTTP redirect chains (up to 10 hops) |
| 🛡️ **12+ Threat Intelligence APIs** | Google Safe Browsing, VirusTotal, URLhaus, URLScan.io, AbuseIPDB, SSL Labs, Cloudflare Radar, Symantec, Cisco Talos, Spamhaus, CleanBrowsing, OpenPhish |
| 🧠 **On-Device Heuristics** | Homograph attack detection, typosquatting, suspicious TLDs, credential phishing patterns |
| 🏷️ **Smart Content Classification** | 40+ website categories with confidence scores using multi-signal analysis |
| 🔞 **Adult Content Filter** | Detects and blocks NSFW content with domain, TLD, and keyword analysis |
| 💳 **Payment QR Guard** | Detects UPI, cryptocurrency, and payment portal QR codes with fraud warnings |
| 🏴‍☠️ **Piracy Detection** | Identifies known piracy domains and torrent sites |
| 🔒 **Sandbox Browser** | Isolated WebView with disabled cookies/JS for safe URL inspection |
| 📊 **Intelligence Reports** | Detailed threat breakdown with API-by-API verdict visualization |
| 🤖 **AI Categorization** | Multi-signal website categorizer with federated learning |
| 🔐 **Certificate Engine** | HMAC-SHA256 signed QR certificates for verified safe codes |
| 📱 **Quick Settings Tile** | One-tap scan access from Android notification shade |
| 🌐 **Browser Integration** | Register as default browser + share target for link scanning |
| ☁️ **Cloud Sync** | Community threat reports + dynamic dataset updates via Firebase |
| 📈 **Scan History** | Encrypted local database with search, filter, and export |
| 🎨 **QR Generator** | Create QR codes for URLs, WiFi, contacts, events, and more |

---

## 👨‍👩‍👧‍👦 Parental Controls & Child Safety

ThreatLens isn't just for enterprise security; it's a powerful tool for families. Children often scan QR codes without understanding the risks, making them vulnerable to inappropriate content or scams. 

* **Strict Safe Search Enforcement**: ThreatLens automatically detects and blocks explicit (NSFW), violent, or restricted content.
* **Intelligent Content Filtering**: Uses domain reputation, TLD scoring, and heuristic keyword analysis to prevent access to unverified adult or high-risk domains.
* **Scam Protection for Kids**: Prevents young users from falling victim to "free Robux/V-Bucks" or gaming-related QR phishing scams by analyzing the true destination intent before opening.

---

## 🧠 AI-Powered Threat Detection

Traditional static blocklists are slow to update and easily bypassed by new malicious URLs. ThreatLens tackles this with an advanced **On-Device AI Engine**:

* **Federated Learning**: The app learns from novel, newly encountered threat patterns and securely shares generalized models back to the cloud without compromising your privacy or sharing your actual scan data.
* **Generative AI Analysis**: Powered by Google Gemini, the AI categorizer performs deep multi-signal analysis of website content, intent, and structure, assigning a category and threat score instantly.
* **Zero-Day Phishing Detection**: The AI heuristic checker identifies homograph attacks (e.g., `g00gle.com`), typosquatting, and deceptive UI patterns indicative of credential phishing, even if the URL has never been seen before.

---

## Architecture

ThreatLens uses **MVVM (Model-View-ViewModel)** with **Unidirectional Data Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Jetpack Compose)                │
│  ScannerScreen · HistoryScreen · SettingsScreen · Results   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                 ViewModel Layer                              │
│  ScannerViewModel · HistoryViewModel · AuthViewModel        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Data Layer                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  ScanRepository (Single Source of Truth)             │    │
│  └───────┬────────────────────────┬────────────────────┘    │
│          │                        │                          │
│  ┌───────▼────────┐  ┌───────────▼──────────────────┐      │
│  │ Room Database   │  │ Threat Analysis Pipeline      │      │
│  │ (SQLCipher)     │  │ ┌─────────────────────────┐  │      │
│  └────────────────┘  │ │ URL Expander             │  │      │
│                       │ │ Heuristic Checker        │  │      │
│                       │ │ Website Categorizer      │  │      │
│                       │ │ 12+ Remote APIs (Retrofit)│ │      │
│                       │ └─────────────────────────┘  │      │
│                       └──────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Threat Scoring Algorithm

```
Score = 100 − Σ(API Penalties) − Σ(Heuristic Penalties) + Bonuses
```

| Score Range | Status | Action |
|---|---|---|
| **≥ 80** | 🟢 SAFE | Direct open button |
| **50–79** | 🟡 CAUTION | Highlights threats, warns before opening |
| **< 50** | 🔴 MALICIOUS | Blocks access behind confirmation modal |

---


## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + UDF |
| **Camera** | CameraX |
| **QR Detection** | Google ML Kit Barcode Scanning |
| **QR Generation** | ZXing Core |
| **Networking** | Retrofit + OkHttp |
| **Database** | Room ORM + SQLCipher |
| **Auth** | Firebase Auth + Google Sign-In |
| **Cloud** | Firebase Firestore |
| **Background Work** | WorkManager |
| **AI** | Google Gemini (Generative AI) |
| **Security** | EncryptedSharedPreferences, Biometric API, HMAC-SHA256 |
| **Dependency Injection** | Manual (singleton pattern) |
| **Build System** | Gradle (Kotlin DSL) |

---

## Project Structure

```
app/src/main/java/com/safeqr/scanner/
├── MainActivity.kt                  # Entry point, intent handling
├── SafeQRApplication.kt             # Application class, initialization
├── analysis/                         # Threat analysis engine
│   ├── ThreatAnalyzer.kt            # Main analysis orchestrator (12+ APIs)
│   ├── HeuristicChecker.kt          # Offline heuristic rules engine
│   ├── QrDataParser.kt              # QR data type parser (URL, WiFi, etc.)
│   ├── UrlExpander.kt               # HTTP redirect chain unroller
│   ├── WebsiteCategorizer.kt        # Multi-signal website categorizer
│   ├── AILearningEngine.kt          # On-device ML threat scoring
│   ├── AIFederatedWorker.kt         # Background federated learning sync
│   └── WebshrinkerClient.kt         # Enterprise categorization API client
├── data/
│   ├── ApiKeys.kt                   # BuildConfig-backed API key accessor
│   ├── PreferencesManager.kt        # SharedPreferences wrapper
│   ├── SecureVaultManager.kt        # Encrypted credential storage
│   ├── local/                       # Room database, DAOs, entities
│   ├── model/                       # Data classes (ScanResult, SafetyStatus)
│   ├── remote/                      # Retrofit API interfaces (17 services)
│   └── repository/                  # ScanRepository (single source of truth)
├── navigation/
│   └── NavGraph.kt                  # Compose Navigation graph
├── security/
│   └── CertificateEngine.kt         # HMAC-SHA256 QR certificate system
├── service/
│   ├── ScannerTileService.kt        # Quick Settings tile
│   └── WeeklyDigestWorker.kt        # Background weekly scan digest
├── ui/
│   ├── components/                  # Reusable UI components
│   ├── screens/                     # Full-screen Composables
│   └── theme/                       # Material 3 theme, colors, typography
├── utils/
│   └── SmartRouter.kt               # Deep link routing utility
└── viewmodel/                       # ViewModels for each feature
```

---

## Team
 
The following team members contributed to building ThreatLens:

* **[SATYAM KUMAR]** - Project Lead / Developer
* **[AMAN PATEL]** - [SYSTEM INTEGRATION]
* **[DEEPANSHU]** - [TESTING & DEBUGGING]



## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with 🛡️ by the ThreatLens Team
</p>
