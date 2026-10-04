"""
ThreatLens Web Categorizer Inference Engine
Sub-millisecond domain and URL threat classification powered by:
1. Live Threat URL Feed (URLhaus, OpenPhish)
2. 456,000+ Categorized Domain Knowledge Base (UT1, StevenBlack, Sovereign Baseline)
3. Progressive Subdomain Traversal
4. URL Lexical Heuristics & Brand Impersonation Detector
"""

import os
import re
import json
import time
import sqlite3
from urllib.parse import urlparse
from typing import Tuple, List, Dict, Any
from collections import defaultdict

DB_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data", "web_categorizer.db"
)

CATEGORY_LABELS = {
    "MALWARE": "Malware & Ransomware Distribution",
    "PHISHING": "Phishing & Credential Theft",
    "CRYPTOJACKING": "Cryptocurrency Mining Script",
    "ONLINE_CASINOS": "Gambling & Online Casinos",
    "PORNOGRAPHY": "Adult / Explicit Content",
    "PROPAGANDA_SITES": "Disinformation & Fake News",
    "BANKING": "Banking & Financial Services",
    "ONLINE_RETAIL": "E-Commerce & Online Shopping",
    "GAMING": "Video Games & Gaming Platforms",
    "MOVIE_STREAMING": "Video Streaming & Entertainment",
    "SOCIAL_MEDIA": "Social Networks & Communities",
    "GOVERNMENT_PORTALS": "Official Government Portal",
    "TECH_NEWS": "Technology News & Publications",
    "DEVELOPER_TOOLS": "Developer & Cloud Services",
    "SUSPICIOUS_LURE": "Suspicious Brand Impersonation / Scam Lure",
    "GENERAL_WEB": "Standard General Website"
}

SUSPICIOUS_TLDS = {
    ".xyz", ".top", ".club", ".work", ".click", ".loan", ".live",
    ".tk", ".ml", ".ga", ".cf", ".gq", ".buzz", ".guru", ".rest"
}

HIGH_RISK_FINANCIAL_BRANDS = [
    "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "paytm",
    "phonepe", "gpay", "bhim", "yono", "aadhaar", "uidai", "incometax",
    "amazon", "flipkart", "netflix", "microsoft", "google", "apple"
]

SCAM_KEYWORDS = [
    "kyc", "update", "verify", "verification", "blocked", "suspended",
    "login", "signin", "claim", "reward", "cashback", "refund",
    "lottery", "winner", "urgent", "security", "alert"
]

class WebCategorizerEngine:
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        if not os.path.exists(self.db_path):
            print(f"[WARN] Web Categorizer database not found at {self.db_path}. Running with heuristics only.")
            self.conn = None
        else:
            self.conn = sqlite3.connect(self.db_path, check_same_thread=False)
            self.conn.execute("PRAGMA query_only = ON;")

        # Load imported keywords for web scraper content matching
        keywords_json_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "data", "imported_web_category_keywords.json"
        )
        if not os.path.exists(keywords_json_path):
            keywords_json_path = os.path.join(
                os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                "data", "web_category_keywords.json"
            )

        self.keywords = []
        if os.path.exists(keywords_json_path):
            try:
                with open(keywords_json_path, "r", encoding="utf-8") as f:
                    kdata = json.load(f)
                    self.keywords = kdata.get("keywords", [])
                print(f"[*] Loaded {len(self.keywords):,} imported category keywords into WebCategorizerEngine.")
            except Exception as e:
                print(f"[WARN] Failed loading keywords JSON: {e}")

        self.keyword_patterns = []
        for item in self.keywords:
            kw = item.get("keyword", "").strip()
            if len(kw) >= 3:
                try:
                    pat = re.compile(r'\b' + re.escape(kw) + r'\b', re.IGNORECASE)
                    self.keyword_patterns.append((item, pat))
                except Exception:
                    pass

    def _clean_domain(self, raw: str) -> str:
        raw = raw.strip().lower()
        raw = re.sub(r"^https?://", "", raw)
        raw = raw.split("/")[0].split(":")[0]
        return raw.removeprefix("www.")

    def classify_url(self, raw_url: str) -> Dict[str, Any]:
        start_time = time.perf_counter()
        raw_url = raw_url.strip()
        if not raw_url.startswith("http://") and not raw_url.startswith("https://"):
            full_url = "http://" + raw_url
        else:
            full_url = raw_url

        parsed = urlparse(full_url)
        domain = self._clean_domain(parsed.netloc or full_url.split("/")[0])
        path_and_query = (parsed.path + "?" + parsed.query).lower()

        # Tier 1: Check Exact Threat URL Feed (URLhaus / OpenPhish)
        if self.conn:
            cur = self.conn.cursor()
            cur.execute(
                "SELECT category, threat_level, source FROM threat_urls WHERE url = ? LIMIT 1;",
                (raw_url,)
            )
            row = cur.fetchone()
            if not row and full_url != raw_url:
                cur.execute(
                    "SELECT category, threat_level, source FROM threat_urls WHERE url = ? LIMIT 1;",
                    (full_url,)
                )
                row = cur.fetchone()

            if row:
                latency_ms = round((time.perf_counter() - start_time) * 1000.0, 3)
                cat, threat_lvl, src = row
                return {
                    "url": raw_url,
                    "domain": domain,
                    "category": cat,
                    "category_label": CATEGORY_LABELS.get(cat, cat),
                    "threat_level": threat_lvl,
                    "confidence": 0.99,
                    "is_threat": threat_lvl == "DANGEROUS",
                    "explainable_reasons": [
                        f"Matched confirmed malicious threat feed: {src}.",
                        "This exact URL has been flagged in active cyber threat telemetry for malware or credential harvesting."
                    ],
                    "data_source": src,
                    "evaluation_latency_ms": latency_ms
                }

        # Tier 2: Check Exact Domain in Indexed Database
        if self.conn and domain:
            cur = self.conn.cursor()
            cur.execute(
                "SELECT category, threat_level, source FROM domains WHERE domain = ? LIMIT 1;",
                (domain,)
            )
            row = cur.fetchone()
            if row:
                cat, threat_lvl, src = row
                latency_ms = round((time.perf_counter() - start_time) * 1000.0, 3)
                return {
                    "url": raw_url,
                    "domain": domain,
                    "category": cat,
                    "category_label": CATEGORY_LABELS.get(cat, cat),
                    "threat_level": threat_lvl,
                    "confidence": 0.95,
                    "is_threat": threat_lvl == "DANGEROUS",
                    "explainable_reasons": [
                        f"Domain explicitly cataloged in {src}.",
                        f"Classified as {CATEGORY_LABELS.get(cat, cat)} with {threat_lvl} risk rating."
                    ],
                    "data_source": src,
                    "evaluation_latency_ms": latency_ms
                }

            # Tier 3: Subdomain-aware progressive parent domain traversal
            # e.g., 'ebank.secure.sbi.co.in' -> 'secure.sbi.co.in' -> 'sbi.co.in'
            parts = domain.split(".")
            if len(parts) > 2:
                for i in range(1, len(parts) - 1):
                    parent = ".".join(parts[i:])
                    cur.execute(
                        "SELECT category, threat_level, source FROM domains WHERE domain = ? LIMIT 1;",
                        (parent,)
                    )
                    parent_row = cur.fetchone()
                    if parent_row:
                        cat, threat_lvl, src = parent_row
                        latency_ms = round((time.perf_counter() - start_time) * 1000.0, 3)
                        return {
                            "url": raw_url,
                            "domain": domain,
                            "category": cat,
                            "category_label": CATEGORY_LABELS.get(cat, cat),
                            "threat_level": threat_lvl,
                            "confidence": 0.90,
                            "is_threat": threat_lvl == "DANGEROUS",
                            "explainable_reasons": [
                                f"Root authority '{parent}' verified in {src}.",
                                f"Subdomain inherits parent classification as {CATEGORY_LABELS.get(cat, cat)}."
                            ],
                            "data_source": f"{src}_PARENT_DOMAIN",
                            "evaluation_latency_ms": latency_ms
                        }

        # Tier 4: Lexical & Heuristic Scam / Brand Impersonation Engine
        heuristic_reasons = []
        is_suspicious = False
        threat_level = "SAFE"
        confidence = 0.50
        detected_category = "GENERAL_WEB"

        # Check for Raw IP Address in URL
        if re.search(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", domain):
            is_suspicious = True
            threat_level = "DANGEROUS"
            detected_category = "PHISHING"
            confidence = 0.88
            heuristic_reasons.append("URL uses raw numerical IP address instead of a recognized domain name.")

        # Check for Suspicious TLD
        for tld in SUSPICIOUS_TLDS:
            if domain.endswith(tld):
                is_suspicious = True
                heuristic_reasons.append(f"Domain uses high-abuse disposable TLD ({tld}).")
                if threat_level != "DANGEROUS":
                    threat_level = "CAUTION"
                break

        # Check for Brand Impersonation + Scam Trigger Tokens
        matched_brand = None
        for brand in HIGH_RISK_FINANCIAL_BRANDS:
            if brand in domain:
                matched_brand = brand
                break

        matched_scam_words = [w for w in SCAM_KEYWORDS if w in domain or w in path_and_query]

        if matched_brand and matched_scam_words:
            is_suspicious = True
            threat_level = "DANGEROUS"
            detected_category = "PHISHING"
            confidence = 0.92
            heuristic_reasons.append(
                f"Detected brand impersonation targeting '{matched_brand.upper()}' paired with urgent triggers: {', '.join(matched_scam_words)}."
            )
            heuristic_reasons.append("High probability of credential harvesting or fake KYC phishing page.")
        elif matched_brand and any(domain.endswith(tld) for tld in SUSPICIOUS_TLDS):
            is_suspicious = True
            threat_level = "DANGEROUS"
            detected_category = "PHISHING"
            confidence = 0.85
            heuristic_reasons.append(
                f"Financial/consumer brand '{matched_brand.upper()}' hosted on disposable TLD."
            )
        elif len(matched_scam_words) >= 2 and any(domain.endswith(tld) for tld in SUSPICIOUS_TLDS):
            is_suspicious = True
            threat_level = "CAUTION"
            detected_category = "SUSPICIOUS_LURE"
            confidence = 0.75
            heuristic_reasons.append(f"Urgent scam keywords ({', '.join(matched_scam_words)}) on suspicious domain.")

        # Multiple hyphens (common in homograph/typosquats)
        if domain.count("-") >= 3:
            is_suspicious = True
            heuristic_reasons.append(f"Excessive hyphenation ({domain.count('-')} hyphens) indicates domain typosquatting.")
            if threat_level != "DANGEROUS":
                threat_level = "CAUTION"

        latency_ms = round((time.perf_counter() - start_time) * 1000.0, 3)

        if not is_suspicious:
            heuristic_reasons.append("No active malware, phishing, or homoglyph risk flags identified.")
            return {
                "url": raw_url,
                "domain": domain,
                "category": "GENERAL_WEB",
                "category_label": "General / Uncategorized Web",
                "threat_level": "SAFE",
                "confidence": 0.60,
                "is_threat": False,
                "explainable_reasons": heuristic_reasons,
                "data_source": "ThreatLens_Lexical_Heuristics",
                "evaluation_latency_ms": latency_ms
            }

        return {
            "url": raw_url,
            "domain": domain,
            "category": detected_category,
            "category_label": CATEGORY_LABELS.get(detected_category, detected_category),
            "threat_level": threat_level,
            "confidence": confidence,
            "is_threat": threat_level == "DANGEROUS",
            "explainable_reasons": heuristic_reasons,
            "data_source": "ThreatLens_Heuristic_Engine",
            "evaluation_latency_ms": latency_ms
        }

    def classify_scraped_content(
        self,
        url: str = "",
        title: str = "",
        meta_keywords: str = "",
        meta_description: str = "",
        h1_h2_text: str = "",
        body_text: str = ""
    ) -> Dict[str, Any]:
        """
        Classifies scraped webpage text (title, meta tags, headings, body)
        by matching against the imported open benchmark keyword taxonomy.
        """
        start_time = time.perf_counter()

        title_lower = (title or "").lower()
        meta_kw_lower = (meta_keywords or "").lower()
        meta_desc_lower = (meta_description or "").lower()
        h1_h2_lower = (h1_h2_text or "").lower()
        body_lower = (body_text or "").lower()

        category_scores = defaultdict(float)
        category_threat_levels = {}
        matched_keywords = []

        for item, pat in self.keyword_patterns:
            cat = item["category"]
            base_weight = float(item.get("weight", 2.0))
            threat_lvl = item.get("threat_level", "SAFE")
            category_threat_levels[cat] = threat_lvl

            matched_scopes = []
            score_addition = 0.0

            # Title match (high signal: 3.5x)
            if pat.search(title_lower):
                matched_scopes.append("title")
                score_addition += base_weight * 3.5

            # Meta keywords (high signal: 3.0x)
            if pat.search(meta_kw_lower):
                matched_scopes.append("meta_keywords")
                score_addition += base_weight * 3.0

            # Headings H1/H2 (2.5x)
            if pat.search(h1_h2_lower):
                matched_scopes.append("headings")
                score_addition += base_weight * 2.5

            # Meta description (2.0x)
            if pat.search(meta_desc_lower):
                matched_scopes.append("meta_desc")
                score_addition += base_weight * 2.0

            # Body text match (1.0x, count bounded at 3)
            body_matches = len(pat.findall(body_lower))
            if body_matches > 0:
                cnt = min(3, body_matches)
                matched_scopes.append(f"body(x{cnt})")
                score_addition += base_weight * 1.0 * cnt

            if score_addition > 0:
                category_scores[cat] += score_addition
                matched_keywords.append({
                    "keyword": item["keyword"],
                    "category": cat,
                    "score_added": round(score_addition, 2),
                    "matched_in": matched_scopes,
                    "threat_level": threat_lvl
                })

        latency_ms = round((time.perf_counter() - start_time) * 1000.0, 3)

        if not category_scores:
            if url:
                url_res = self.classify_url(url)
                return {
                    "url": url,
                    "category": url_res["category"],
                    "category_label": url_res["category_label"],
                    "threat_level": url_res["threat_level"],
                    "confidence": url_res["confidence"],
                    "is_threat": url_res["is_threat"],
                    "matching_keywords": [],
                    "top_categories_scores": {},
                    "explainable_reasons": ["No explicit keywords matched in body text; falling back to domain threat feed.", *url_res["explainable_reasons"]],
                    "evaluation_latency_ms": latency_ms
                }

            return {
                "url": url,
                "category": "GENERAL_WEB",
                "category_label": "General / Uncategorized Web",
                "threat_level": "SAFE",
                "confidence": 0.50,
                "is_threat": False,
                "matching_keywords": [],
                "top_categories_scores": {},
                "explainable_reasons": ["No matching topical keywords detected in scraped page text."],
                "evaluation_latency_ms": latency_ms
            }

        # Sort candidate categories by cumulative score
        sorted_cats = sorted(category_scores.items(), key=lambda x: x[1], reverse=True)
        top_cat, top_score = sorted_cats[0]
        top_threat_level = category_threat_levels.get(top_cat, "SAFE")

        # Check if a cyber threat score is dominant or presents an acute risk
        for cat, sc in sorted_cats:
            if category_threat_levels.get(cat) == "DANGEROUS":
                if cat == top_cat:
                    top_threat_level = "DANGEROUS"
                    break
                elif sc >= 35.0 and sc >= 0.8 * top_score:
                    top_cat = cat
                    top_score = sc
                    top_threat_level = "DANGEROUS"
                    break

        total_score = sum(category_scores.values())
        confidence = round(min(0.99, max(0.65, top_score / (total_score + 1e-5) * 1.1)), 2)

        matched_for_top = [m["keyword"] for m in matched_keywords if m["category"] == top_cat][:5]
        reasons = [
            f"Classified as {CATEGORY_LABELS.get(top_cat, top_cat)} based on content analysis (Score: {top_score:.1f}).",
            f"Strongest keyword signals: {', '.join(matched_for_top)}."
        ]
        if top_threat_level == "DANGEROUS":
            reasons.append("HIGH ALERT: Critical cyber security threat keywords identified in scraped page text.")

        return {
            "url": url,
            "category": top_cat,
            "category_label": CATEGORY_LABELS.get(top_cat, top_cat),
            "threat_level": top_threat_level,
            "confidence": confidence,
            "is_threat": top_threat_level == "DANGEROUS",
            "matching_keywords": sorted(matched_keywords, key=lambda x: x["score_added"], reverse=True)[:15],
            "top_categories_scores": {k: round(v, 1) for k, v in sorted_cats[:5]},
            "explainable_reasons": reasons,
            "evaluation_latency_ms": latency_ms
        }

