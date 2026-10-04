import os
import random
import numpy as np
import pandas as pd

def generate_upi_scam_corpus(n_samples=12000, output_path="synthetic_upi_scam_corpus.csv"):
    np.random.seed(42)
    random.seed(42)
    
    records = []
    
    # 50+ Real Indian UPI Handles
    legit_handles = [
        "okhdfcbank", "okaxis", "oksbi", "okicici", "ybl", "ibl", "axl",
        "paytm", "ptyes", "pthdfc", "upi", "bhim", "sbi", "hdfcbank", "icici"
    ]
    suspicious_mule_handles = [
        "top", "xyz", "online", "live", "bankverify", "refunddesk", "mulepay"
    ]
    
    # Pretext categories
    pretexts = ["DIGITAL_ARREST", "ELECTRICITY_CUT", "KYC_SUSPENSION", "REVERSE_UPI", "TASK_SCAM"]
    
    for i in range(n_samples):
        # In reality, fraud is rare (~8-10% in targeted risk pools)
        is_fraud = np.random.choice([0, 1], p=[0.90, 0.10])
        
        if is_fraud == 0:
            # ── Normal Legitimate Transaction (Chai, Kirana, Rent, Swiggy, P2P) ──
            scenario_type = np.random.choice(["MICRO_PAYMENT", "REGULAR_P2P", "MERCHANT_UTILITY", "HIGH_VALUE_RENT"], 
                                             p=[0.45, 0.35, 0.15, 0.05])
            if scenario_type == "MICRO_PAYMENT":
                amount = round(float(np.random.uniform(10.0, 150.0)), 2)
            elif scenario_type == "REGULAR_P2P":
                amount = round(float(np.random.uniform(200.0, 3000.0)), 2)
            elif scenario_type == "MERCHANT_UTILITY":
                amount = round(float(np.random.uniform(500.0, 2500.0)), 2)
            else:
                amount = round(float(np.random.uniform(10000.0, 45000.0)), 2)
                
            handle = random.choice(legit_handles)
            is_known_handle = 1
            vpa_entropy = round(float(np.random.uniform(1.2, 3.1)), 2)
            is_first_time_payee = np.random.choice([0, 1], p=[0.70, 0.30])
            
            # Add realistic real-world feature noise and edge-case overlap
            has_stranger_call = np.random.choice([0, 1], p=[0.94, 0.06]) # 6% benign users on phone with delivery/family
            has_screen_share = np.random.choice([0, 1], p=[0.995, 0.005]) # 0.5% benign screen sharing (remote work)
            has_accessibility_abuse = 0
            has_pretext_sms = np.random.choice([0, 1], p=[0.92, 0.08]) # 8% benign promotional/urgent SMS
            pretext_time_delta_mins = float(np.random.uniform(30.0, 1440.0)) if has_pretext_sms else 9999.0
            is_reverse_upi = 0
            mcc_risk_level = np.random.choice([0, 1, 2], p=[0.75, 0.20, 0.05])
            time_of_day_risk = np.random.choice([0, 1], p=[0.82, 0.18]) # 18% late night
            
        else:
            # ── Emerging AI-Enabled Scam Workflow ──
            scam_type = random.choice(pretexts)
            
            if scam_type == "DIGITAL_ARREST":
                # High amount, active stranger call (video/Skype), intimidation
                amount = round(float(np.random.choice([18000, 35000, 75000, 150000]) + np.random.uniform(50, 4500)), 2)
                has_stranger_call = np.random.choice([0, 1], p=[0.10, 0.90])
                has_screen_share = np.random.choice([0, 1], p=[0.45, 0.55])
                has_accessibility_abuse = np.random.choice([0, 1], p=[0.85, 0.15])
                has_pretext_sms = np.random.choice([0, 1], p=[0.15, 0.85])
                pretext_time_delta_mins = round(float(np.random.uniform(1.0, 35.0)), 1)
                is_reverse_upi = 0
                
            elif scam_type == "ELECTRICITY_CUT":
                # Urgent bill payment to personal VPA
                amount = round(float(np.random.uniform(850.0, 6500.0)), 2)
                has_stranger_call = np.random.choice([0, 1], p=[0.35, 0.65])
                has_screen_share = 0
                has_accessibility_abuse = 0
                has_pretext_sms = np.random.choice([0, 1], p=[0.05, 0.95])
                pretext_time_delta_mins = round(float(np.random.uniform(0.5, 15.0)), 1)
                is_reverse_upi = 0
                
            elif scam_type == "REVERSE_UPI":
                # "Scan to receive refund", am > 0 but framed as cashback
                amount = round(float(np.random.choice([2500, 5000, 9999, 15000, 25000])), 2)
                has_stranger_call = np.random.choice([0, 1], p=[0.40, 0.60])
                has_screen_share = 0
                has_accessibility_abuse = 0
                has_pretext_sms = np.random.choice([0, 1], p=[0.20, 0.80])
                pretext_time_delta_mins = round(float(np.random.uniform(0.2, 8.0)), 1)
                is_reverse_upi = 1
                
            elif scam_type == "KYC_SUSPENSION":
                # Fake YONO/HDFC update leading to AnyDesk or trojan
                amount = round(float(np.random.uniform(4000.0, 48000.0)), 2)
                has_stranger_call = np.random.choice([0, 1], p=[0.30, 0.70])
                has_screen_share = np.random.choice([0, 1], p=[0.40, 0.60])
                has_accessibility_abuse = np.random.choice([0, 1], p=[0.70, 0.30])
                has_pretext_sms = np.random.choice([0, 1], p=[0.10, 0.90])
                pretext_time_delta_mins = round(float(np.random.uniform(1.0, 25.0)), 1)
                is_reverse_upi = 0
                
            else: # TASK_SCAM
                # Telegram task scam initial deposit trap
                amount = round(float(np.random.choice([1500, 3000, 8000, 25000])), 2)
                has_stranger_call = np.random.choice([0, 1], p=[0.75, 0.25])
                has_screen_share = 0
                has_accessibility_abuse = 0
                has_pretext_sms = np.random.choice([0, 1], p=[0.10, 0.90])
                pretext_time_delta_mins = round(float(np.random.uniform(2.0, 45.0)), 1)
                is_reverse_upi = 0
                
            is_known_handle = np.random.choice([0, 1], p=[0.40, 0.60])
            vpa_entropy = round(float(np.random.uniform(2.8, 4.8)), 2)
            is_first_time_payee = np.random.choice([0, 1], p=[0.08, 0.92])
            mcc_risk_level = np.random.choice([0, 1, 2], p=[0.30, 0.35, 0.35])
            time_of_day_risk = np.random.choice([0, 1], p=[0.55, 0.45])
            
        records.append({
            "amount": amount,
            "has_stranger_call": int(has_stranger_call),
            "has_screen_share": int(has_screen_share),
            "has_accessibility_abuse": int(has_accessibility_abuse),
            "has_pretext_sms": int(has_pretext_sms),
            "pretext_time_delta_mins": float(pretext_time_delta_mins),
            "vpa_entropy": float(vpa_entropy),
            "is_known_handle": int(is_known_handle),
            "is_first_time_payee": int(is_first_time_payee),
            "is_reverse_upi": int(is_reverse_upi),
            "mcc_risk_level": int(mcc_risk_level),
            "time_of_day_risk": int(time_of_day_risk),
            "is_fraud": int(is_fraud)
        })
        
    df = pd.DataFrame(records)
    df.to_csv(output_path, index=False)
    print(f"Dataset generated successfully: {output_path}")
    print(f"Total rows: {len(df)} | Fraud rows: {df['is_fraud'].sum()} ({df['is_fraud'].mean()*100:.2f}%)")
    return df

if __name__ == "__main__":
    out_dir = r"C:\AndroidProjects\ThreatLens_FINAL_v2\ThreatLens\ml_pipeline"
    os.makedirs(out_dir, exist_ok=True)
    csv_file = os.path.join(out_dir, "synthetic_upi_scam_corpus.csv")
    generate_upi_scam_corpus(12000, csv_file)
