import os
import json
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score, confusion_matrix, precision_recall_curve

def train_threatlens_model():
    csv_path = r"C:\AndroidProjects\ThreatLens_FINAL_v2\ThreatLens\ml_pipeline\synthetic_upi_scam_corpus.csv"
    df = pd.read_csv(csv_path)
    
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
    
    # 3-Way Split: Train (60%), Calibration (20%), Test (20%)
    # Calibration set is strictly isolated for Split-Conformal Prediction!
    X_train, X_temp, y_train, y_temp = train_test_split(X, y, test_size=0.40, random_state=42, stratify=y)
    X_calib, X_test, y_calib, y_test = train_test_split(X_temp, y_temp, test_size=0.50, random_state=42, stratify=y_temp)
    
    print(f"Data Split -> Train: {len(X_train)} | Calibration: {len(X_calib)} | Test: {len(X_test)}")
    
    # ── Train XGBoost Model with Imbalance Weighting ──
    scale_pos_weight = (len(y_train) - sum(y_train)) / sum(y_train)
    model = xgb.XGBClassifier(
        n_estimators=120,
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
    
    # ── Evaluate on Test Set ──
    test_probs = model.predict_proba(X_test)[:, 1]
    auc = roc_auc_score(y_test, test_probs)
    print(f"\n==========================================")
    print(f"TEST ROC-AUC SCORE: {auc:.4f}")
    print(f"==========================================")
    
    # Feature Importances
    importances = model.feature_importances_
    feat_imp = sorted(zip(feature_cols, importances), key=lambda x: x[1], reverse=True)
    print("\nFeature Importances:")
    for f, imp in feat_imp:
        print(f"  {f:28s}: {imp:.4f}")
        
    # ── SPLIT-CONFORMAL CALIBRATION (Zero Alert Fatigue) ──
    # Alpha = 0.002 (Guarantees <= 0.2% False Positive Rate on normal transactions)
    alpha = 0.002
    safe_calib_indices = np.where(y_calib.values == 0)[0]
    safe_calib_probs = model.predict_proba(X_calib.iloc[safe_calib_indices])[:, 1]
    
    n_safe = len(safe_calib_probs)
    quantile_level = min(1.0, np.ceil((n_safe + 1) * (1.0 - alpha)) / n_safe)
    q_hat = float(np.quantile(safe_calib_probs, quantile_level))
    
    print(f"\n==========================================")
    print(f"CONFORMAL RISK CONTROL (ALERT FATIGUE PROOF)")
    print(f"==========================================")
    print(f"Significance Level (Alpha): {alpha * 100:.2f}% (Max Allowable False Alarms)")
    print(f"Calibrated Conformal Threshold (q_hat): {q_hat:.4f}")
    
    # Verify on Test Safe Transactions
    safe_test_indices = np.where(y_test.values == 0)[0]
    safe_test_probs = test_probs[safe_test_indices]
    empirical_fpr = (safe_test_probs >= q_hat).mean()
    print(f"Empirical False Alarm Rate on Unseen Test Safe Transactions: {empirical_fpr * 100:.3f}%")
    
    # Verify on Test Scam Transactions
    fraud_test_indices = np.where(y_test.values == 1)[0]
    fraud_test_probs = test_probs[fraud_test_indices]
    fraud_catch_rate = (fraud_test_probs >= q_hat).mean()
    print(f"Scam Workflow Recall at Conformal Threshold: {fraud_catch_rate * 100:.2f}%")
    print(f"==========================================\n")
    
    # ── Export Model Files ──
    out_dir = r"C:\AndroidProjects\ThreatLens_FINAL_v2\ThreatLens\ml_pipeline"
    model_json_path = os.path.join(out_dir, "xgboost_model.json")
    model.save_model(model_json_path)
    
    # Export Conformal Metadata for Android
    meta = {
        "conformal_threshold_q_hat": q_hat,
        "alpha": alpha,
        "roc_auc": float(auc),
        "empirical_fpr": float(empirical_fpr),
        "fraud_recall_at_q_hat": float(fraud_catch_rate),
        "feature_order": feature_cols
    }
    with open(os.path.join(out_dir, "conformal_metadata.json"), "w") as f:
        json.dump(meta, f, indent=2)
        
    print(f"Saved model to: {model_json_path}")
    print(f"Saved conformal metadata to: {os.path.join(out_dir, 'conformal_metadata.json')}")

if __name__ == "__main__":
    train_threatlens_model()
