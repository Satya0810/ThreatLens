"""
ThreatLens: Automated Importer for Online Categorization & Keyword Datasets
Downloads real open-access benchmark datasets from online mirrors (Kaggle/GitHub/URLhaus):
1. Kaggle Website Classification Dataset (1,408 real scraped websites with full body text)
2. Live Cyber Threat Feeds (URLhaus active threat tags, OpenPhish targets)
3. Indian Telecom Scam & Fraud Pretext Corpus (CERT-In / junioralive / UCI)

Uses TF-IDF + N-gram NLP extraction to automatically extract THOUSANDS of real keywords
and phrases attached to web categories for direct comparison against web scraper data.
"""

import os
import io
import json
import csv
import re
import urllib.request
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "ml_pipeline", "data")
CSV_OUT = os.path.join(DATA_DIR, "imported_web_category_keywords.csv")
JSON_OUT = os.path.join(BASE_DIR, "cloud_backend", "data", "imported_web_category_keywords.json")

# Category mapping from Kaggle benchmark categories to ThreatLens SiteCategory
KAGGLE_CAT_MAP = {
    "Travel": ("FLIGHT_BOOKING", "SAFE"),
    "Social Networking and Messaging": ("SOCIAL_MEDIA", "SAFE"),
    "News": ("NATIONAL_NEWS", "SAFE"),
    "Streaming Services": ("MOVIE_STREAMING", "SAFE"),
    "Sports": ("SPORTS_NEWS", "SAFE"),
    "Photography": ("BLOGGING", "SAFE"),
    "Law and Government": ("GOVERNMENT_PORTALS", "SAFE"),
    "Health and Fitness": ("HOSPITALS_CLINICS", "SAFE"),
    "Games": ("GAMING", "SAFE"),
    "E-Commerce": ("ONLINE_RETAIL", "SAFE"),
    "Forums": ("FORUMS_COMMUNITIES", "SAFE"),
    "Food": ("FOOD_DELIVERY", "SAFE"),
    "Education": ("SCHOOLS_UNIVERSITIES", "SAFE"),
    "Computers and Technology": ("DEVELOPER_TOOLS", "SAFE"),
    "Business/Corporate": ("BANKING", "SAFE"),
    "Adult": ("PORNOGRAPHY", "CAUTION")
}

COMMON_STOPWORDS = {
    "http", "https", "com", "www", "net", "org", "bit", "html", "php",
    "don", "miss", "let", "got", "just", "like", "get", "make", "know",
    "take", "see", "come", "think", "look", "want", "give", "use",
    "day", "days", "time", "year", "years", "people", "way", "thing",
    "good", "new", "first", "last", "long", "great", "little", "own",
    "other", "old", "right", "big", "high", "different", "small", "large",
    "next", "early", "young", "important", "few", "public", "bad", "same",
    "able", "dec", "december", "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "ago", "today", "yesterday", "tomorrow",
    "dear", "box", "cost", "chance", "apply", "liver", "ass"
}

def is_valid_keyword(kw):
    kw = kw.strip().lower()
    if len(kw) < 3:
        return False
    words = kw.split()
    if all(w in COMMON_STOPWORDS for w in words):
        return False
    return True

def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = re.sub(r"http\S+|www\S+", "", text)
    text = re.sub(r"[^a-zA-Z\s]", " ", text)
    return " ".join(text.lower().split())

def import_datasets():
    print("=" * 65)
    print("ThreatLens: Importing Online Datasets & Extracting Keywords")
    print("=" * 65)

    all_extracted = []
    seen_keywords = set()

    # ─────────────────────────────────────────────────────────────────────────
    # 1. Download & Extract from Kaggle Website Classification Dataset
    # ─────────────────────────────────────────────────────────────────────────
    url_kaggle = "https://cdn.jsdelivr.net/gh/MainakRepositor/Datasets@master/website_classification.csv"
    print(f"\n[1/3] Downloading Kaggle Website Classification Dataset from CDN...")
    req = urllib.request.Request(url_kaggle, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        df_web = pd.read_csv(io.BytesIO(resp.read()))
    print(f"  -> Ingested {len(df_web)} real websites across {df_web['Category'].nunique()} categories.")

    # Save local copy of the raw dataset
    raw_save_path = os.path.join(DATA_DIR, "kaggle_website_classification.csv")
    df_web.to_csv(raw_save_path, index=False)
    print(f"  -> Saved raw dataset to: {raw_save_path}")

    # Extract top keywords per category using TF-IDF (1-grams and 2-grams)
    print("\n[*] Running TF-IDF N-gram extraction on Kaggle website corpus...")
    for kaggle_cat, grp in df_web.groupby("Category"):
        mapped_cat, threat_lvl = KAGGLE_CAT_MAP.get(kaggle_cat, ("GENERAL_WEB", "SAFE"))
        texts = [clean_text(t) for t in grp["cleaned_website_text"].dropna()]
        if not texts:
            continue

        # Extract top 80 n-grams per category
        vec = TfidfVectorizer(
            max_features=80,
            stop_words="english",
            ngram_range=(1, 3),
            min_df=2,
            token_pattern=r"(?u)\b[a-zA-Z]{3,}\b"
        )
        try:
            tfidf_mat = vec.fit_transform(texts)
            feature_names = vec.get_feature_names_out()
            scores = tfidf_mat.sum(axis=0).A1
            ranked_terms = sorted(zip(feature_names, scores), key=lambda x: x[1], reverse=True)

            for term, score in ranked_terms:
                term_clean = term.strip().lower()
                if term_clean not in seen_keywords and is_valid_keyword(term_clean):
                    seen_keywords.add(term_clean)
                    weight = round(min(5.0, 2.0 + (score / (len(texts) + 1))), 2)
                    all_extracted.append({
                        "keyword": term_clean,
                        "category": mapped_cat,
                        "weight": weight,
                        "threat_level": threat_lvl,
                        "source": f"Kaggle_Website_{kaggle_cat}",
                        "token_length": len(term_clean.split())
                    })
            print(f"  -> [{kaggle_cat:30}] -> {mapped_cat:20}: Extracted {len(ranked_terms)} keywords")
        except Exception as e:
            print(f"  [WARN] Failed extraction for {kaggle_cat}: {e}")

    # ─────────────────────────────────────────────────────────────────────────
    # 2. Extract Real Scam & Pretext Keywords from Indian Cyber Fraud Corpus
    # ─────────────────────────────────────────────────────────────────────────
    print("\n[2/3] Extracting Fraud & Phishing Pretext Keywords from Real Corpus...")
    sms_corpus_path = os.path.join(DATA_DIR, "master_indian_sms_pretext_corpus.csv")
    if os.path.exists(sms_corpus_path):
        df_sms = pd.read_csv(sms_corpus_path)
        scam_col = "is_scam" if "is_scam" in df_sms.columns else "label"
        fraud_texts = df_sms[df_sms[scam_col] == 1]["text"].dropna().tolist()
        vec_fraud = TfidfVectorizer(
            max_features=150,
            stop_words="english",
            ngram_range=(1, 3),
            min_df=3,
            token_pattern=r"(?u)\b[a-zA-Z]{3,}\b"
        )
        vec_fraud.fit(fraud_texts)
        for term in vec_fraud.get_feature_names_out():
            term_clean = term.strip().lower()
            if term_clean not in seen_keywords and is_valid_keyword(term_clean):
                seen_keywords.add(term_clean)
                all_extracted.append({
                    "keyword": term_clean,
                    "category": "PHISHING",
                    "weight": 5.0,
                    "threat_level": "DANGEROUS",
                    "source": "CERT-In_Indian_Scam_Corpus",
                    "token_length": len(term_clean.split())
                })
        print(f"  -> Extracted {len(vec_fraud.get_feature_names_out())} scam/phishing pretext keywords.")

    # ─────────────────────────────────────────────────────────────────────────
    # 3. Live Threat Feeds (URLhaus Threat Tags)
    # ─────────────────────────────────────────────────────────────────────────
    print("\n[3/3] Ingesting Live Malware & Cyber Threat Signatures from URLhaus...")
    try:
        urlhaus_url = "https://urlhaus.abuse.ch/downloads/csv_recent/"
        req = urllib.request.Request(urlhaus_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            uh_lines = resp.read().decode("utf-8", errors="ignore").splitlines()
        
        uh_tags = set()
        for line in uh_lines:
            if line.startswith("#") or not line.strip():
                continue
            parts = [p.strip(' "') for p in line.split(",")]
            if len(parts) >= 7:
                # tags are in column index 6
                raw_tags = parts[6]
                for tag in raw_tags.split(";"):
                    t = tag.strip().lower()
                    if t and len(t) > 2:
                        uh_tags.add(t)

        for tag in sorted(uh_tags):
            if tag not in seen_keywords:
                seen_keywords.add(tag)
                all_extracted.append({
                    "keyword": tag,
                    "category": "MALWARE",
                    "weight": 5.0,
                    "threat_level": "DANGEROUS",
                    "source": "URLhaus_Live_Tag",
                    "token_length": len(tag.split())
                })
        print(f"  -> Extracted {len(uh_tags)} live cyber malware tags/signatures.")
    except Exception as e:
        print(f"  [WARN] URLhaus extraction: {e}")

    # Sort keywords by weight descending, then token_length descending
    all_extracted.sort(key=lambda x: (x["weight"], x["token_length"]), reverse=True)

    print("\n" + "=" * 65)
    print(f"Total Imported Real Keywords: {len(all_extracted):,}")
    print("=" * 65)

    # Export to CSV
    print(f"\nWriting Imported Keywords to CSV: {CSV_OUT}...")
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["keyword", "category", "weight", "threat_level", "source", "token_length"])
        writer.writeheader()
        writer.writerows(all_extracted)
    print(f"  -> Saved CSV successfully ({os.path.getsize(CSV_OUT) / 1024:.1f} KB).")

    # Export to JSON
    print(f"\nWriting Imported Keywords to JSON: {JSON_OUT}...")
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump({
            "version": "2.0.0",
            "imported_from": [
                "Kaggle: MainakRepositor/Datasets (website_classification.csv)",
                "URLhaus: abuse.ch live threat tags feed",
                "CERT-In / junioralive / UCI real SMS scam corpus"
            ],
            "total_keywords": len(all_extracted),
            "keywords": all_extracted
        }, f, indent=2, ensure_ascii=False)
    print(f"  -> Saved JSON successfully ({os.path.getsize(JSON_OUT) / 1024:.1f} KB).")
    print("=" * 65)

if __name__ == "__main__":
    import_datasets()
