"""
ThreatLens-X: Multimodal Fusion & Split-Conformal Prediction Engine
Solves the "Alert Fatigue / Chai-Stall" Dilemma:
Guarantees False Positive Rate on normal UPI payments <= 0.2% (alpha = 0.002)
Exports model weights & calibration thresholds for on-device Android execution.
"""

import os
import json
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score, confusion_matrix, precision_recall_fscore_support
from dataset_builder import generate_upi_scam_corpus

def run_conformal_fusion_pipeline():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    csv_path = os.path.join(base_dir, "synthetic_upi_scam_corpus.csv")
    
    # Regenerate fresh dataset with realistic noise and edge-case overlap
    df = generate_upi_scam_corpus(12000, csv_path)
    print(f"[+] Loaded Dataset: {len(df)} total transactions, {df['is_fraud'].sum()} fraud events ({df['is_fraud'].mean()*100:.2f}%)")
    
    feature_cols = [
        "amount",
        "has_stranger_call",
        "has_screen_share",
        "has_accessibility_abuse",
        "has_pretext_sms",
        "pretext_time_delta_mins",
        "vpa_entropy",
        "is_known_handle",
        "is_first_time_payee",
        "is_reverse_upi",
        "mcc_risk_level",
        "time_of_day_risk"
    ]
    
    X = df[feature_cols]
    y = df["is_fraud"]
    
    # Stratified 3-Way Split:
    # 60% Train, 20% Calibration (Strictly isolated for conformal risk bounds), 20% Holdout Test
    X_train, X_temp, y_train, y_temp = train_test_split(X, y, test_size=0.40, random_state=42, stratify=y)
    X_calib, X_test, y_calib, y_test = train_test_split(X_temp, y_temp, test_size=0.50, random_state=42, stratify=y_temp)
    
    print(f"[+] Partitions -> Train: {len(X_train)} | Calibration: {len(X_calib)} | Test: {len(X_test)}")
    
    # ── Train Gradient Boosted Decision Forest ──
    scale_pos_weight = (len(y_train) - sum(y_train)) / sum(y_train)
    model = xgb.XGBClassifier(
        n_estimators=100,
        max_depth=4,
        learning_rate=0.06,
        scale_pos_weight=scale_pos_weight,
        subsample=0.85,
        colsample_bytree=0.85,
        objective="binary:logistic",
        eval_metric="auc",
        random_state=42
    )
    model.fit(X_train, y_train)
    
    # ── Evaluate on Isolated Test Set ──
    test_probs = model.predict_proba(X_test)[:, 1]
    auc = roc_auc_score(y_test, test_probs)
    print(f"\n" + "=" * 60)
    print(f"[+] TEST ROC-AUC: {auc:.4f}")
    print("=" * 60)
    
    # ── Split-Conformal Risk Control Calibration ──
    alpha = 0.002 # 0.2% max false alarm budget on daily retail transactions
    safe_calib_indices = np.where(y_calib.values == 0)[0]
    safe_calib_scores = model.predict_proba(X_calib.iloc[safe_calib_indices])[:, 1]
    
    n_safe = len(safe_calib_scores)
    # Quantile formula with finite-sample correction
    quantile_level = min(1.0, np.ceil((n_safe + 1) * (1.0 - alpha)) / n_safe)
    q_hat = float(np.quantile(safe_calib_scores, quantile_level))
    
    # Validate on Holdout Test Set
    safe_test_indices = np.where(y_test.values == 0)[0]
    fraud_test_indices = np.where(y_test.values == 1)[0]
    
    safe_test_scores = test_probs[safe_test_indices]
    fraud_test_scores = test_probs[fraud_test_indices]
    
    empirical_fpr = float((safe_test_scores > q_hat).mean())
    fraud_recall = float((fraud_test_scores > q_hat).mean())
    
    print("\n[+] CONFORMAL RISK CALIBRATION RESULTS:")
    print(f"    Target False Alarm Tolerance (alpha): {alpha * 100:.2f}%")
    print(f"    Calibrated Decision Threshold (q_hat): {q_hat:.4f}")
    print(f"    Empirical Test False Alarm Rate (FPR): {empirical_fpr * 100:.3f}% (Guarantee Met: {empirical_fpr <= alpha})")
    print(f"    Scam Detection Recall at q_hat:        {fraud_recall * 100:.2f}%")
    
    # Confusion Matrix at q_hat
    y_pred_binary = (test_probs > q_hat).astype(int)
    cm = confusion_matrix(y_test, y_pred_binary)
    print("\n[+] Test Set Confusion Matrix (at Conformal Threshold):")
    print(f"    TN: {cm[0,0]} | FP: {cm[0,1]}")
    print(f"    FN: {cm[1,0]}  | TP: {cm[1,1]}")
    
    # Save Model and Conformal Metadata
    model_json_path = os.path.join(base_dir, "xgboost_model.json")
    model.save_model(model_json_path)
    
    meta = {
        "model_architecture": "XGBoost + Split-Conformal Quantile Calibrator",
        "conformal_threshold_q_hat": q_hat,
        "alpha": alpha,
        "test_roc_auc": round(float(auc), 4),
        "empirical_fpr": round(empirical_fpr, 5),
        "fraud_recall_at_q_hat": round(fraud_recall, 4),
        "total_training_samples": len(df),
        "feature_order": feature_cols,
        "confusion_matrix": {
            "true_negatives": int(cm[0,0]),
            "false_positives": int(cm[0,1]),
            "false_negatives": int(cm[1,0]),
            "true_positives": int(cm[1,1])
        }
    }
    
    meta_path = os.path.join(base_dir, "conformal_metadata.json")
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)
        
    print(f"\n[+] Saved Conformal Metadata to: {meta_path}")
    print(f"[+] Saved Trained XGBoost Model to: {model_json_path}")
    return meta

if __name__ == "__main__":
    run_conformal_fusion_pipeline()
