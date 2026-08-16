# Security Policy

ThreatLens is dedicated to providing real-time threat intelligence and proactive cyber protection for QR codes and web destinations. We take the security and privacy of our users, codebases, and infrastructure seriously.

This document outlines our security policies, supported versions, vulnerability reporting process, and guidelines for coordinated disclosure.

---

## Supported Versions

We actively maintain and provide security patches for the following versions and components:

| Component | Version / Branch | Supported | Notes |
| :--- | :--- | :--- | :--- |
| **Android Application** | `2.x.x` (Latest Release) | :white_check_mark: | Active support via Google Play / GitHub Releases |
| **Android Application** | `< 2.0.0` | :x: | End-of-life; please update to the latest release |
| **Vercel / Cloud Functions Backend** | `main` branch | :white_check_mark: | Continuously deployed & monitored |
| **ThreatLens Browser Extension** | `1.x.x` (Latest) | :white_check_mark: | Chrome Web Store / Manifest V3 |
| **AI Training & Heuristic Models** | `main` branch | :white_check_mark: | Regular heuristic & model updates |

---

## Reporting a Vulnerability

> [!IMPORTANT]
> **Please do NOT report security vulnerabilities through public GitHub issues, discussions, pull requests, or social media channels.**

We support and encourage coordinated vulnerability disclosure. If you discover a security vulnerability in ThreatLens, please report it privately:

### Method 1: GitHub Private Vulnerability Reporting (Preferred)
1. Navigate to the repository's [Security Tab](../../security).
2. Click **"Report a vulnerability"** under the Advisories section.
3. Complete the advisory form with detailed technical findings and reproduction steps.

### Method 2: Security Contact Email
If private vulnerability reporting is unavailable, send an encrypted or direct email to our security team:
- **Email:** `security@threatlens.app` *(or create a private issue via GitHub security advisories)*
- **Subject Line:** `[SECURITY VULNERABILITY] <Brief Description of Issue>`

---

## What to Include in Your Report

To help us triage and resolve vulnerabilities efficiently, please include:

- **Type of Issue:** (e.g., Sandbox Escape, Insecure Data Storage, Broken Authentication, Heuristic Bypass, API Key Leakage, Insecure Intent Handling).
- **Affected Component:** Android app (version/build), Browser Extension, Cloud Function / Backend endpoint, or AI Pipeline.
- **Proof of Concept (PoC):** Step-by-step reproduction instructions, payload samples, or minimal reproduction scripts/APKs.
- **Impact Assessment:** Explanation of what an attacker could achieve (e.g., unauthorized data access, arbitrary code execution, denial of service).
- **Suggested Fix / Mitigation:** (Optional) Any recommendations to remediate the vulnerability.

---

## Scope & Boundaries

### In-Scope Vulnerabilities
- Sandbox WebView breakout or Javascript bridge exploitation.
- Insecure local storage or cryptographic flaws in HMAC verification / encrypted Room database.
- Exported Android component vulnerabilities (Activities, Receivers, Services, Providers).
- Insecure Intent filters or Deep Link hijacking.
- Cloud API endpoint vulnerabilities (SSRF, Authentication/Authorization bypass, Injection).
- Secret exposure or hardcoded production secrets in distributed binaries.
- Extension privilege escalation or cross-origin script injection.

### Out-of-Scope Vulnerabilities
- Attacks requiring physical access to an unlocked, rooted, or jailbroken device with compromised OS integrity.
- Volumetric DDoS attacks against backend infrastructure or third-party APIs.
- Social engineering, phishing, or physical attacks targeting contributors or maintainers.
- Upstream rate-limiting or service degradation from third-party threat intelligence APIs (e.g., VirusTotal, Safe Browsing quota exhaustion).
- Theoretical issues without demonstrable real-world security impact.

---

## Vulnerability Handling & Response Timeline

We commit to the following response timeline for all legitimate security reports:

| Milestone | Target Response Time |
| :--- | :--- |
| **Initial Acknowledgment** | Within **48 hours** |
| **Triage & Severity Assessment** | Within **3 to 5 business days** |
| **Fix Development & Testing** | **7 to 30 days** (depending on severity & complexity) |
| **Public Advisory & Release** | Coordinated with researcher after patch deployment |

---

## Safe Harbor & Responsible Disclosure

We appreciate the ethical security research community. When conducting security research under this policy, we ask that you:

- Make a good-faith effort to avoid privacy violations, data destruction, and service disruption.
- Only interact with test accounts or systems you own/control.
- Give us reasonable time to remediate the vulnerability before disclosing it publicly.
- Do not exploit the vulnerability beyond what is strictly necessary to demonstrate the proof of concept.

If you abide by these guidelines, we will **not** pursue legal action against you and will work collaboratively with you to resolve the issue.

---

## Security Best Practices in ThreatLens

ThreatLens integrates multi-layer security defenses by design:
- **Sandbox Browser:** Isolated WebViews run with restricted permissions, disabling unnecessary JavaScript execution and third-party cookie persistence.
- **On-Device Cryptography:** Keystore-backed encryption for scan history and HMAC-SHA256 verification for QR certificates.
- **Zero-PII Storage:** Scan URLs and heuristics are analyzed on-device or processed ephemerally without attaching persistent user identifiers.
- **Safe Intent Dispatch:** Strict explicit Intents and validated URL schemes prevent arbitrary URI execution.

---

## Acknowledgments & Hall of Fame

We believe in recognizing ethical security researchers who help protect the ThreatLens community. If you report a valid vulnerability and wish to be credited, we will happily feature your name/handle in our release notes and Security Hall of Fame.

*Thank you for helping keep ThreatLens and its users safe!*
