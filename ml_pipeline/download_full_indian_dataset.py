"""
Download and Assemble Full-Scale Real Indian SMS & Smishing Corpora
Downloads:
1. Indian Telecom SMS Spam Collection (2,267 real Indian SMS messages) from junioralive/india-spam-sms-classification
2. UCI SMS Spam Collection Dataset (5,572 real mobile messages)
3. High-Impact Indian Cybercrime Pretexts (Digital Arrest, Electricity Cut, YONO KYC, Reverse-UPI)
Merges them into a comprehensive real-world training dataset.
"""

import os
import io
import requests
import pandas as pd

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA_DIR, exist_ok=True)

def download_real_indian_sms():
    print("[*] Fetching Real Indian Telecom SMS Dataset (2,267 rows)...")
    url = "https://cdn.jsdelivr.net/gh/junioralive/india-spam-sms-classification@main/dataset/spam_ham_india.csv"
    headers = {"User-Agent": "Mozilla/5.0"}
    
    r = requests.get(url, headers=headers, timeout=15)
    r.raise_for_status()
    
    df_india = pd.read_csv(io.StringIO(r.content.decode("utf-8", errors="ignore")))
    df_india.rename(columns={"Msg": "text", "Label": "label"}, inplace=True)
    df_india["is_scam"] = (df_india["label"].astype(str).str.lower().str.strip() == "spam").astype(int)
    df_india["origin"] = "Indian_Telecom_Corpus"
    
    dest_path = os.path.join(DATA_DIR, "indian_telecom_spam_sms.csv")
    df_india.to_csv(dest_path, index=False)
    print(f"[+] Saved: {dest_path}")
    print(f"    Rows: {len(df_india)} | Scams: {df_india['is_scam'].sum()} | Benign: {(df_india['is_scam'] == 0).sum()}")
    return df_india

def assemble_master_corpus():
    # 1. Indian Telecom dataset (2,267 rows)
    df_india = download_real_indian_sms()
    
    # 2. UCI SMS dataset (5,572 rows)
    uci_path = os.path.join(DATA_DIR, "sms_spam_collection.csv")
    if os.path.exists(uci_path):
        df_uci = pd.read_csv(uci_path)
        df_uci["is_scam"] = (df_uci["label"].astype(str).str.lower().str.strip() == "spam").astype(int)
        df_uci["origin"] = "UCI_Global_Benchmark"
    else:
        df_uci = pd.DataFrame()

    # 3. High-Impact Indian Cybercrime Incident Transcripts
    pretext_path = os.path.join(DATA_DIR, "indian_smishing_pretext_corpus.csv")
    if os.path.exists(pretext_path):
        df_pretext = pd.read_csv(pretext_path)
        df_pretext.rename(columns={"category": "label"}, inplace=True)
        df_pretext["origin"] = "CERT_In_Cybercrime_Transcripts"
    else:
        df_pretext = pd.DataFrame()

    # Merge into Master NLP Corpus
    master_df = pd.concat([df_india[["text", "is_scam", "origin"]], 
                           df_uci[["text", "is_scam", "origin"]], 
                           df_pretext[["text", "is_scam", "origin"]]], ignore_index=True)
    master_df.dropna(subset=["text"], inplace=True)
    master_df.drop_duplicates(subset=["text"], inplace=True)
    
    master_path = os.path.join(DATA_DIR, "master_indian_sms_pretext_corpus.csv")
    master_df.to_csv(master_path, index=False)
    print("\n" + "=" * 60)
    print(f"[+] MASTER REAL-WORLD SMS & PRETEXT CORPUS CREATED: {master_path}")
    print(f"    Total Rows: {len(master_df)}")
    print(f"    Scam / Smishing Messages: {master_df['is_scam'].sum()}")
    print(f"    Legitimate / Benign Messages: {(master_df['is_scam'] == 0).sum()}")
    print("=" * 60)
    return master_df

if __name__ == "__main__":
    assemble_master_corpus()
