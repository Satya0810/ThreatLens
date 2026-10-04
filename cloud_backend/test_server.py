"""
Verification script for ThreatLens Cloud AI Backend.
Tests 3 real-world scenarios:
1. Normal Street Retail (Chai / Swiggy) -> Expect SILENT_PASS
2. Digital Arrest Scam -> Expect PRE_PIN_INTERLOCK
3. Reverse UPI Collect Trap -> Expect PRE_PIN_INTERLOCK
"""

import sys
import os
import json
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi.testclient import TestClient
from app import app

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    print("[+] Health Check Passed:", response.json()["status"])

def test_scenarios():
    print("\n" + "="*60)
    print("RUNNING FORENSIC SCENARIO TESTS")
    print("="*60)

    # ── Scenario 1: Normal Street Chai Payment ──
    chai_payload = {
        "sanitized_payee_address": "sharma_tea@okhdfcbank",
        "sanitized_payee_name": "Sharma Tea Stall",
        "amount": 20.0,
        "note": "chai",
        "has_active_call": False,
        "is_screen_shared": False,
        "has_suspicious_accessibility": False
    }
    r1 = client.post("/api/v1/analyze/workflow", json=chai_payload)
    assert r1.status_code == 200
    data1 = r1.json()
    print(f"\n[Test 1] Normal Street Payment (Rs. 20 Chai):")
    print(f"         Decision: {data1['decision_tier']} (Expected: SILENT_PASS)")
    print(f"         Risk Score: {data1['risk_score']} | Fraud Prob: {data1['fraud_probability']}")
    print(f"         Latency: {data1['inference_latency_ms']} ms")
    assert data1["decision_tier"] == "SILENT_PASS", "Chai payment caused alert fatigue!"

    # ── Scenario 2: Digital Arrest Extortion Pipeline ──
    arrest_payload = {
        "sanitized_payee_address": "98127391829@mulepay",
        "sanitized_payee_name": "CBI Verification Escrow",
        "amount": 95000.0,
        "note": "bail verification deposit",
        "has_active_call": True,
        "is_screen_shared": False,
        "has_suspicious_accessibility": False,
        "latest_ingress_pretext_text": "CBI New Delhi: Arrest warrant issued under PMLA. Surrender or verify funds on video call.",
        "pretext_category": "DIGITAL_ARREST",
        "pretext_delta_minutes": 5.0
    }
    r2 = client.post("/api/v1/analyze/workflow", json=arrest_payload)
    assert r2.status_code == 200
    data2 = r2.json()
    print(f"\n[Test 2] Digital Arrest Extortion (Rs. 95,000 + Active Call):")
    print(f"         Decision: {data2['decision_tier']} (Expected: PRE_PIN_INTERLOCK)")
    print(f"         Detected: {data2['detected_workflow_type']}")
    print(f"         Risk Score: {data2['risk_score']} | Fraud Prob: {data2['fraud_probability']}")
    print(f"         Latency: {data2['inference_latency_ms']} ms")
    print(f"         Explainable Reasons:")
    for reason in data2["explainable_reasons"]:
        print(f"           - {reason}")
    assert data2["decision_tier"] == "PRE_PIN_INTERLOCK"

    # ── Scenario 3: Reverse-UPI Collect Request ──
    reverse_payload = {
        "sanitized_payee_address": "refund_claim_fast@live",
        "sanitized_payee_name": "Electricity Board Refund",
        "amount": 4999.0,
        "note": "claim refund cashback received",
        "has_active_call": False,
        "is_screen_shared": False,
        "has_suspicious_accessibility": False,
        "latest_ingress_pretext_text": "BSES Electricity: Excess bill amount Rs. 4,999 refund approved. Click to claim.",
        "pretext_category": "ELECTRICITY_CUT",
        "pretext_delta_minutes": 2.0
    }
    r3 = client.post("/api/v1/analyze/workflow", json=reverse_payload)
    assert r3.status_code == 200
    data3 = r3.json()
    print(f"\n[Test 3] Reverse-UPI Refund Trap (Rs. 4,999):")
    print(f"         Decision: {data3['decision_tier']} (Expected: PRE_PIN_INTERLOCK)")
    print(f"         Detected: {data3['detected_workflow_type']}")
    print(f"         Risk Score: {data3['risk_score']}")
    print(f"         Latency: {data3['inference_latency_ms']} ms")
    print(f"         Explainable Reasons:")
    for reason in data3["explainable_reasons"]:
        print(f"           - {reason}")
    assert data3["decision_tier"] == "PRE_PIN_INTERLOCK"

    print("\n" + "="*60)
    print("ALL 3 FORENSIC SCENARIOS PASSED WITH PERFECT ACCURACY!")
    print("="*60)

if __name__ == "__main__":
    test_health()
    test_scenarios()
