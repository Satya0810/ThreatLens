import urllib.request
import json
import time

API_URL = "http://127.0.0.1:8000/api/v1/analyze/url"

test_urls = [
    ("State Bank of India (Official)", "https://www.sbi.co.in"),
    ("Flipkart (E-Commerce)", "https://www.flipkart.com"),
    ("Netflix (Movie Streaming)", "https://www.netflix.com/browse"),
    ("Confirmed URLhaus Malware", "https://nfadealer.top/download?payload=njRat.exe"),
    ("Scam KYC Phishing Lure", "http://sbi-bank-kyc-update.xyz/login.php"),
    ("Online Casino / Gambling", "https://888casino.com")
]

print("=== TESTING SOVEREIGN CLOUD WEB CATEGORIZER API ===")
for label, target_url in test_urls:
    payload = json.dumps({"url": target_url}).encode("utf-8")
    req = urllib.request.Request(API_URL, data=payload, headers={"Content-Type": "application/json"})
    
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            elapsed_ms = round((time.perf_counter() - t0) * 1000.0, 2)
            print(f"\n[URL]: {target_url} ({label})")
            print(f"  -> Category: {data['category']} ({data['category_label']})")
            print(f"  -> Threat Level: {data['threat_level']} | Is Threat: {data['is_threat']}")
            print(f"  -> Confidence: {data['confidence'] * 100:.1f}%")
            print(f"  -> Source: {data['data_source']}")
            print(f"  -> Latency: {data['evaluation_latency_ms']} ms (Roundtrip: {elapsed_ms} ms)")
            print(f"  -> Reasons: {data['explainable_reasons']}")
    except Exception as e:
        print(f"\n[FAILED] {target_url}: {e}")
