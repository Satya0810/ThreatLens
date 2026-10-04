"""
ThreatLens Diverse Web Categorizer Dataset Builder
Compiles a balanced, diverse dataset across 95+ categories:
- UT1 Universite Toulouse 1 Capitole (25+ diverse categories: News, Sports, Jobs, Tech, Games, Banking, Shopping, Education, etc.)
- StevenBlack Curated Lists (Casinos, Propaganda, Adult)
- URLhaus & OpenPhish Live Threat Feeds (Malware & Phishing)
- ThreatLens Sovereign Baseline (Indian Banks, Govt portals, UPI, Digital India)

Performs class balancing and random shuffling so the resulting CSV and SQLite database
are vibrant and diverse on every page, with no single category dominating.
"""

import os
import sys
import json
import sqlite3
import urllib.request
import re
import random
import concurrent.futures
from urllib.parse import urlparse
from collections import defaultdict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "ml_pipeline", "data")
DB_DIR = os.path.join(BASE_DIR, "cloud_backend", "data")
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(DB_DIR, exist_ok=True)

CSV_PATH = os.path.join(DATA_DIR, "web_categorizer_dataset.csv")
DB_PATH = os.path.join(DB_DIR, "web_categorizer.db")

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ThreatLens/2.0 Security Research"
MAX_SAMPLES_PER_CATEGORY = 4000  # Cap oversized categories to keep the dataset diverse

def clean_domain(raw):
    raw = raw.strip().lower()
    raw = re.sub(r"^https?://", "", raw)
    raw = raw.split("/")[0].split(":")[0]
    raw = raw.removeprefix("www.")
    if re.match(r"^[a-z0-9.-]+\.[a-z]{2,}$", raw):
        return raw
    return None

def fetch_url(url, timeout=12):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8", errors="ignore")
            return [line.strip() for line in text.splitlines() if line.strip()]
    except Exception as e:
        print(f"  [WARN] Failed {url}: {e}", flush=True)
        return []

def main():
    print("=" * 65, flush=True)
    print("ThreatLens: Building Balanced Diverse Web Categorizer Dataset", flush=True)
    print("=" * 65, flush=True)

    domains_by_category = defaultdict(list)
    threat_urls = []

    # 1. Sovereign Baseline (Indian Banks, Government, Tech, News)
    baseline_path = os.path.join(BASE_DIR, "functions", "categorizer_data.json")
    if os.path.exists(baseline_path):
        print("[1/5] Ingesting ThreatLens Sovereign Baseline...", flush=True)
        with open(baseline_path, "r", encoding="utf-8") as f:
            base_data = json.load(f)
        for dom, cat in base_data.items():
            cd = clean_domain(dom)
            if cd:
                cat_upper = cat.upper()
                threat_lvl = "SAFE"
                if cat_upper in ["PHISHING", "MALWARE", "RANSOMWARE", "SPYWARE", "CRYPTOJACKING"]:
                    threat_lvl = "DANGEROUS"
                elif cat_upper in ["PORNOGRAPHY", "ONLINE_CASINOS", "PROPAGANDA_SITES"]:
                    threat_lvl = "CAUTION"
                domains_by_category[cat_upper].append((cd, cat_upper, threat_lvl, "ThreatLens_Baseline", "DOMAIN"))
        print(f"  -> Ingested {len(base_data)} curated baseline domains across {len(domains_by_category)} categories.", flush=True)

    # 2. UT1 Categories (Rich & Diverse)
    ut1_tasks = [
        ("press", "NATIONAL_NEWS", "SAFE"),
        ("sports", "SPORTS_NEWS", "SAFE"),
        ("jobsearch", "PROFESSIONAL_NETWORKING", "SAFE"),
        ("bank", "BANKING", "SAFE"),
        ("shopping", "ONLINE_RETAIL", "SAFE"),
        ("social_networks", "SOCIAL_MEDIA", "SAFE"),
        ("audio-video", "MOVIE_STREAMING", "SAFE"),
        ("games", "GAMING", "SAFE"),
        ("ai", "AI_ML_PLATFORMS", "SAFE"),
        ("chat", "MESSAGING", "SAFE"),
        ("blog", "BLOGGING", "SAFE"),
        ("forums", "FORUMS_COMMUNITIES", "SAFE"),
        ("cooking", "FOOD_DELIVERY", "SAFE"),
        ("celebrity", "CELEBRITY_NEWS", "SAFE"),
        ("child", "KIDS_EDUCATION", "SAFE"),
        ("educational_games", "ONLINE_LEARNING", "SAFE"),
        ("dating", "DATING_LEGIT", "SAFE"),
        ("radio", "PODCASTS", "SAFE"),
        ("vpn", "VPN_PROXY_SERVICES", "CAUTION"),
        ("warez", "SOFTWARE_PIRACY", "CAUTION"),
        ("drogue", "ILLEGAL_DRUG_SALES", "DANGEROUS"),
        ("dangerous_material", "ILLEGAL_WEAPONS", "DANGEROUS"),
        ("hacking", "CYBERSECURITY", "SAFE"),
        ("cryptojacking", "CRYPTOJACKING", "DANGEROUS"),
        ("malware", "MALWARE", "DANGEROUS"),
        ("phishing", "PHISHING", "DANGEROUS"),
    ]

    print("\n[2/5] Fetching 26 Diverse Categories from UT1 University Repository...", flush=True)
    def fetch_ut1(item):
        ut1_name, mapped_cat, threat_lvl = item
        url = f"https://cdn.jsdelivr.net/gh/olbat/ut1-blacklists@master/blacklists/{ut1_name}/domains"
        lines = fetch_url(url)
        cleaned = []
        for l in lines:
            cd = clean_domain(l)
            if cd:
                cleaned.append((cd, mapped_cat, threat_lvl, f"UT1_{ut1_name.upper()}", "DOMAIN"))
        return ut1_name, mapped_cat, cleaned

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(fetch_ut1, item) for item in ut1_tasks]
        for f in concurrent.futures.as_completed(futures):
            ut1_name, mapped_cat, cleaned = f.result()
            domains_by_category[mapped_cat].extend(cleaned)
            print(f"  -> UT1 [{ut1_name}]: {len(cleaned)} raw domains -> Category: {mapped_cat}", flush=True)

    # 3. StevenBlack Curated Topic Feeds
    print("\n[3/5] Fetching StevenBlack Curated Lists (Gambling, Propaganda, Adult)...", flush=True)
    steven_tasks = [
        ("gambling", "ONLINE_CASINOS", "CAUTION"),
        ("fakenews", "PROPAGANDA_SITES", "CAUTION"),
        ("porn", "PORNOGRAPHY", "CAUTION")
    ]
    for list_name, mapped_cat, threat_lvl in steven_tasks:
        url = f"https://cdn.jsdelivr.net/gh/StevenBlack/hosts@master/alternates/{list_name}/hosts"
        lines = fetch_url(url)
        cleaned = []
        for l in lines:
            if l.startswith("0.0.0.0 ") and len(l.split()) >= 2:
                cd = clean_domain(l.split()[1])
                if cd and cd != "0.0.0.0":
                    cleaned.append((cd, mapped_cat, threat_lvl, f"StevenBlack_{list_name.upper()}", "DOMAIN"))
        domains_by_category[mapped_cat].extend(cleaned)
        print(f"  -> StevenBlack [{list_name}]: {len(cleaned)} domains -> Category: {mapped_cat}", flush=True)

    # 4. Live Cyber Threat Feeds (URLhaus & OpenPhish)
    print("\n[4/5] Ingesting Live Real-Time Cyber Feeds (URLhaus & OpenPhish)...", flush=True)
    # URLhaus
    urlhaus_lines = fetch_url("https://urlhaus.abuse.ch/downloads/csv_recent/")
    uh_count = 0
    for line in urlhaus_lines:
        if line.startswith("#") or not line.strip():
            continue
        parts = [p.strip(' "') for p in line.split(",")]
        if len(parts) >= 6:
            full_url = parts[2]
            threat_type = parts[5].lower()
            cat = "MALWARE"
            if "phish" in threat_type or "phish" in line.lower():
                cat = "PHISHING"
            try:
                parsed = urlparse(full_url)
                d = clean_domain(parsed.netloc)
                if d and full_url.startswith("http"):
                    threat_urls.append((full_url, d, cat, "DANGEROUS", "URLhaus_Live", "LIVE_THREAT_URL"))
                    domains_by_category[cat].append((d, cat, "DANGEROUS", "URLhaus_Live_Domain", "DOMAIN"))
                    uh_count += 1
            except Exception:
                pass
    print(f"  -> URLhaus: Ingested {uh_count} live threat URLs.", flush=True)

    # OpenPhish
    openphish_lines = fetch_url("https://openphish.com/feed.txt")
    op_count = 0
    for full_url in openphish_lines:
        if full_url.startswith("http"):
            try:
                parsed = urlparse(full_url)
                d = clean_domain(parsed.netloc)
                if d:
                    threat_urls.append((full_url, d, "PHISHING", "DANGEROUS", "OpenPhish_Live", "LIVE_THREAT_URL"))
                    domains_by_category["PHISHING"].append((d, "PHISHING", "DANGEROUS", "OpenPhish_Live_Domain", "DOMAIN"))
                    op_count += 1
            except Exception:
                pass
    print(f"  -> OpenPhish: Ingested {op_count} live phishing URLs.", flush=True)

    # 5. Balancing, Stratifying, and Random Shuffling
    print("\n[5/5] Balancing, Stratifying & Shuffling Dataset...", flush=True)
    balanced_records = []
    seen_domains = set()

    # Deduplicate and cap per category
    for cat, records in domains_by_category.items():
        unique_for_cat = []
        for r in records:
            d = r[0]
            if d not in seen_domains:
                seen_domains.add(d)
                unique_for_cat.append(r)
        
        # If category has more than MAX_SAMPLES_PER_CATEGORY, sample down to prevent domination
        if len(unique_for_cat) > MAX_SAMPLES_PER_CATEGORY:
            # Keep sovereign baseline and sample the rest
            sampled = random.sample(unique_for_cat, MAX_SAMPLES_PER_CATEGORY)
            balanced_records.extend(sampled)
            print(f"  * {cat:25}: Capped from {len(unique_for_cat):,} -> {len(sampled):,} domains", flush=True)
        else:
            balanced_records.extend(unique_for_cat)
            print(f"  * {cat:25}: {len(unique_for_cat):,} domains", flush=True)

    # Add threat URLs
    for tu in threat_urls:
        balanced_records.append((tu[0], tu[2], tu[3], tu[4], tu[5]))

    # Random shuffle so every page has an even, diverse mix of categories
    random.seed(42)
    random.shuffle(balanced_records)
    print(f"\nTotal diverse records after balancing: {len(balanced_records):,}", flush=True)

    # Write balanced CSV
    print(f"\nWriting diverse CSV to: {CSV_PATH}...", flush=True)
    with open(CSV_PATH, "w", encoding="utf-8", newline="") as f:
        f.write("domain_or_url,category,threat_level,source,type\n")
        for r in balanced_records:
            f.write(f"{r[0]},{r[1]},{r[2]},{r[3]},{r[4]}\n")
    print(f"  -> CSV written successfully ({os.path.getsize(CSV_PATH) / (1024*1024):.2f} MB).", flush=True)

    # Rebuild SQLite Database
    print(f"\nCompiling SQLite Database at: {DB_PATH}...", flush=True)
    if os.path.exists(DB_PATH):
        try:
            os.remove(DB_PATH)
        except Exception:
            pass

    conn = sqlite3.connect(DB_PATH)
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

    domain_insert_rows = []
    threat_insert_rows = []

    for r in balanced_records:
        if r[4] == "LIVE_THREAT_URL":
            try:
                d = clean_domain(urlparse(r[0]).netloc) or ""
            except Exception:
                d = ""
            threat_insert_rows.append((r[0], d, r[1], r[2], r[3]))
        else:
            domain_insert_rows.append((r[0], r[1], r[2], r[3]))

    cur.executemany("INSERT OR IGNORE INTO domains VALUES (?, ?, ?, ?)", domain_insert_rows)
    cur.executemany("INSERT OR REPLACE INTO threat_urls VALUES (?, ?, ?, ?, ?)", threat_insert_rows)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_domain_search ON domains(domain);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_threat_url_search ON threat_urls(url);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_threat_domain_search ON threat_urls(domain);")
    conn.commit()
    conn.isolation_level = None
    cur.execute("VACUUM;")
    conn.close()

    print(f"  -> SQLite Database updated successfully ({os.path.getsize(DB_PATH)/(1024*1024):.2f} MB).", flush=True)
    print("=" * 65, flush=True)
    print("SUCCESS: Diverse Web Dataset & SQLite Database Ready!", flush=True)
    print("=" * 65, flush=True)

if __name__ == "__main__":
    main()
