"""
ThreatLens Web Categorizer Database Builder
Downloads and compiles multi-source open categorization datasets:
1. UT1 Universite Toulouse 1 Capitole (Bank, Shopping, Social, Malware, Phishing, Cryptojacking, Games, Video)
2. StevenBlack Curated Lists (Gambling, Pornography, Fake News / Propaganda)
3. URLhaus Live Threat Feed (Real-time active malware distribution URLs)
4. OpenPhish Live Threat Feed (Real-time active phishing URLs)
5. ThreatLens Sovereign Baseline (functions/categorizer_data.json: Indian Banks, Govt, Tech)

Builds an indexed SQLite database at cloud_backend/data/web_categorizer.db for sub-0.1ms lookups.
"""

import os
import sys
import json
import sqlite3
import urllib.request
import re
from urllib.parse import urlparse

# Ensure directories exist
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "cloud_backend", "data")
os.makedirs(OUTPUT_DIR, exist_ok=True)
DB_PATH = os.path.join(OUTPUT_DIR, "web_categorizer.db")

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ThreatLens/2.0 Security Research"

def fetch_url_lines(url, timeout=20):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8", errors="ignore")
            return [line.strip() for line in text.splitlines() if line.strip()]
    except Exception as e:
        print(f"  [WARN] Failed to fetch {url}: {e}")
        return []

def init_db(db_path):
    if os.path.exists(db_path):
        try:
            os.remove(db_path)
        except Exception:
            pass

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode = WAL;")
    cur.execute("PRAGMA synchronous = NORMAL;")

    cur.execute("""
        CREATE TABLE IF NOT EXISTS domains (
            domain TEXT PRIMARY KEY,
            category TEXT NOT NULL,
            threat_level TEXT NOT NULL,
            source TEXT NOT NULL
        );
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS threat_urls (
            url TEXT PRIMARY KEY,
            domain TEXT NOT NULL,
            category TEXT NOT NULL,
            threat_level TEXT NOT NULL,
            source TEXT NOT NULL
        );
    """)

    conn.commit()
    return conn

def clean_domain(raw):
    raw = raw.strip().lower()
    raw = re.sub(r"^https?://", "", raw)
    raw = raw.split("/")[0].split(":")[0]
    raw = raw.removeprefix("www.")
    if re.match(r"^[a-z0-9.-]+\.[a-z]{2,}$", raw):
        return raw
    return None

def build_database():
    print(f"[*] Initializing Web Categorizer SQLite database at: {DB_PATH}")
    conn = init_db(DB_PATH)
    cur = conn.cursor()

    total_domains = 0
    total_urls = 0

    # ─────────────────────────────────────────────────────────────────────────────
    # 1. ThreatLens Sovereign Baseline (functions/categorizer_data.json)
    # ─────────────────────────────────────────────────────────────────────────────
    baseline_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "functions", "categorizer_data.json"
    )
    if os.path.exists(baseline_path):
        print("\n[1/5] Ingesting Sovereign Baseline (Indian Banks, Govt, Tech Platforms)...")
        with open(baseline_path, "r", encoding="utf-8") as f:
            base_data = json.load(f)
        
        baseline_rows = []
        for domain, category in base_data.items():
            cd = clean_domain(domain)
            if cd:
                # Determine threat level based on category
                cat_upper = category.upper()
                threat_level = "SAFE"
                if cat_upper in ["PHISHING", "MALWARE", "RANSOMWARE", "SPYWARE", "FINANCIAL_FRAUD", "CRYPTOJACKING"]:
                    threat_level = "DANGEROUS"
                elif cat_upper in ["PORNOGRAPHY", "ONLINE_CASINOS", "GAMBLING", "PROPAGANDA_SITES"]:
                    threat_level = "CAUTION"
                baseline_rows.append((cd, cat_upper, threat_level, "ThreatLens Sovereign Baseline"))

        cur.executemany("INSERT OR REPLACE INTO domains VALUES (?, ?, ?, ?)", baseline_rows)
        conn.commit()
        print(f"  -> Added {len(baseline_rows)} curated baseline domains.")
        total_domains += len(baseline_rows)

    # ─────────────────────────────────────────────────────────────────────────────
    # 2. UT1 Categories (Toulouse 1 Capitole via jsdelivr CDN)
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n[2/5] Downloading UT1 University Categorized Datasets...")
    ut1_sources = [
        ("bank", "BANKING", "SAFE"),
        ("shopping", "ONLINE_RETAIL", "SAFE"),
        ("social_networks", "SOCIAL_MEDIA", "SAFE"),
        ("audio-video", "MOVIE_STREAMING", "SAFE"),
        ("games", "GAMING", "SAFE"),
        ("cryptojacking", "CRYPTOJACKING", "DANGEROUS"),
        ("malware", "MALWARE", "DANGEROUS"),
        ("phishing", "PHISHING", "DANGEROUS"),
    ]

    for cat_name, mapped_cat, threat_lvl in ut1_sources:
        url = f"https://cdn.jsdelivr.net/gh/olbat/ut1-blacklists@master/blacklists/{cat_name}/domains"
        lines = fetch_url_lines(url)
        rows = []
        for l in lines:
            cd = clean_domain(l)
            if cd:
                rows.append((cd, mapped_cat, threat_lvl, f"UT1_{cat_name.upper()}"))
        
        if rows:
            cur.executemany("INSERT OR IGNORE INTO domains VALUES (?, ?, ?, ?)", rows)
            conn.commit()
            print(f"  -> UT1 [{cat_name}]: {len(rows)} domains ingested ({mapped_cat})")
            total_domains += len(rows)

    # ─────────────────────────────────────────────────────────────────────────────
    # 3. StevenBlack Curated Lists (Gambling, Adult, Fake News)
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n[3/5] Downloading StevenBlack Curated Topic Feeds...")
    steven_sources = [
        ("gambling", "ONLINE_CASINOS", "CAUTION"),
        ("fakenews", "PROPAGANDA_SITES", "CAUTION"),
        ("porn", "PORNOGRAPHY", "CAUTION")
    ]

    for list_name, mapped_cat, threat_lvl in steven_sources:
        url = f"https://cdn.jsdelivr.net/gh/StevenBlack/hosts@master/alternates/{list_name}/hosts"
        lines = fetch_url_lines(url)
        rows = []
        for l in lines:
            if l.startswith("0.0.0.0 ") and len(l.split()) >= 2:
                d = l.split()[1]
                cd = clean_domain(d)
                if cd and cd != "0.0.0.0":
                    rows.append((cd, mapped_cat, threat_lvl, f"StevenBlack_{list_name.upper()}"))

        if rows:
            cur.executemany("INSERT OR IGNORE INTO domains VALUES (?, ?, ?, ?)", rows)
            conn.commit()
            print(f"  -> StevenBlack [{list_name}]: {len(rows)} domains ingested ({mapped_cat})")
            total_domains += len(rows)

    # ─────────────────────────────────────────────────────────────────────────────
    # 4. URLhaus Live Threat Feed (Real-time active malware downloads)
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n[4/5] Ingesting URLhaus Live Cyber Threat Feed...")
    urlhaus_feed = "https://urlhaus.abuse.ch/downloads/csv_recent/"
    urlhaus_lines = fetch_url_lines(urlhaus_feed)
    threat_url_rows = []
    threat_domain_rows = []

    for line in urlhaus_lines:
        if line.startswith("#") or not line.strip():
            continue
        parts = [p.strip(' "') for p in line.split(",")]
        if len(parts) >= 6:
            # id,dateadded,url,url_status,last_online,threat,tags,urlhaus_link,reporter
            full_url = parts[2]
            threat_type = parts[5].lower()
            cat = "MALWARE"
            if "phish" in threat_type or "phish" in line.lower():
                cat = "PHISHING"
            
            try:
                parsed = urlparse(full_url)
                d = clean_domain(parsed.netloc)
                if d and full_url.startswith("http"):
                    threat_url_rows.append((full_url, d, cat, "DANGEROUS", "URLhaus_Live"))
                    threat_domain_rows.append((d, cat, "DANGEROUS", "URLhaus_Live_Domain"))
            except Exception:
                pass

    if threat_url_rows:
        cur.executemany("INSERT OR REPLACE INTO threat_urls VALUES (?, ?, ?, ?, ?)", threat_url_rows)
        cur.executemany("INSERT OR IGNORE INTO domains VALUES (?, ?, ?, ?)", threat_domain_rows)
        conn.commit()
        print(f"  -> URLhaus: Ingested {len(threat_url_rows)} active threat URLs and {len(threat_domain_rows)} domains.")
        total_urls += len(threat_url_rows)

    # ─────────────────────────────────────────────────────────────────────────────
    # 5. OpenPhish Live Threat Feed
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n[5/5] Ingesting OpenPhish Live Phishing Feed...")
    openphish_url = "https://openphish.com/feed.txt"
    openphish_lines = fetch_url_lines(openphish_url)
    phish_url_rows = []
    phish_domain_rows = []

    for full_url in openphish_lines:
        if full_url.startswith("http"):
            try:
                parsed = urlparse(full_url)
                d = clean_domain(parsed.netloc)
                if d:
                    phish_url_rows.append((full_url, d, "PHISHING", "DANGEROUS", "OpenPhish_Live"))
                    phish_domain_rows.append((d, "PHISHING", "DANGEROUS", "OpenPhish_Live_Domain"))
            except Exception:
                pass

    if phish_url_rows:
        cur.executemany("INSERT OR REPLACE INTO threat_urls VALUES (?, ?, ?, ?, ?)", phish_url_rows)
        cur.executemany("INSERT OR IGNORE INTO domains VALUES (?, ?, ?, ?)", phish_domain_rows)
        conn.commit()
        print(f"  -> OpenPhish: Ingested {len(phish_url_rows)} active phishing URLs.")
        total_urls += len(phish_url_rows)

    # ─────────────────────────────────────────────────────────────────────────────
    # Indexing & Optimization
    # ─────────────────────────────────────────────────────────────────────────────
    print("\n[*] Creating B-Tree indexes for sub-millisecond retrieval...")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_domain_search ON domains(domain);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_threat_url_search ON threat_urls(url);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_threat_domain_search ON threat_urls(domain);")
    cur.execute("VACUUM;")
    conn.commit()

    # Verify counts
    cur.execute("SELECT COUNT(*) FROM domains;")
    final_domains = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM threat_urls;")
    final_urls = cur.fetchone()[0]

    cur.execute("SELECT category, COUNT(*) FROM domains GROUP BY category ORDER BY COUNT(*) DESC LIMIT 10;")
    top_cats = cur.fetchall()

    conn.close()

    db_size_mb = os.path.getsize(DB_PATH) / (1024 * 1024)
    print("\n" + "=" * 60)
    print("SUCCESS: Web Categorizer Database Compiled!")
    print(f"  - Database Location: {DB_PATH}")
    print(f"  - Total Unique Categorized Domains: {final_domains:,}")
    print(f"  - Total Live Threat URLs: {final_urls:,}")
    print(f"  - SQLite File Size: {db_size_mb:.2f} MB")
    print("  - Top 10 Categories in Database:")
    for cat, cnt in top_cats:
        print(f"      * {cat}: {cnt:,} domains")
    print("=" * 60)

if __name__ == "__main__":
    build_database()
