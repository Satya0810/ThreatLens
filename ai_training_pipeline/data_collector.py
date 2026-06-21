import csv
import time
import requests
from urllib.parse import urlparse

# ==========================================
# 1. DEFINE YOUR API FUNCTIONS HERE
# ==========================================
# You will replace these placeholders with your actual API calls.

def check_google_safe_browsing(url):
    # Example placeholder:
    # api_key = "YOUR_API_KEY"
    # endpoint = f"https://safebrowsing.googleapis.com/v4/threatMatches:find?key={api_key}"
    # Make request, return 1 if malicious, 0 if safe
    return 0

def check_virustotal(url):
    # Call VirusTotal API
    return 0

def check_openphish(url):
    # Call OpenPhish API
    return 1 if "xyz" in url else 0

def check_urlscan(url):
    # Call urlscan.io
    return 0

# ... Define the rest of your 11 APIs ...

def extract_url_features(url):
    """
    Extracts basic features from the URL itself. 
    The AI will use these features to learn patterns.
    """
    parsed = urlparse(url)
    return {
        "url_length": len(url),
        "domain_length": len(parsed.netloc),
        "has_https": 1 if parsed.scheme == "https" else 0,
        "num_dots": url.count('.'),
        "num_hyphens": url.count('-'),
        "has_login_keyword": 1 if "login" in url.lower() else 0,
        "has_admin_keyword": 1 if "admin" in url.lower() else 0
    }

def main():
    input_file = "sample_urls.csv"
    output_file = "training_dataset.csv"

    # In a real scenario, you would have a CSV with thousands of URLs
    sample_urls = [
        "https://www.google.com",
        "http://secure-login-update.xyz/admin",
        "https://github.com",
        "http://free-netflix-subscription-now.com"
    ]

    print(f"Starting data collection for {len(sample_urls)} URLs...")

    with open(output_file, mode='w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        
        # Write CSV Header
        writer.writerow([
            "url", 
            "url_length", "domain_length", "has_https", "num_dots", "num_hyphens", "has_login_keyword", "has_admin_keyword",
            "api_google", "api_virustotal", "api_openphish", "api_urlscan",
            "final_malicious_label" # This is what the AI will try to predict
        ])

        for url in sample_urls:
            print(f"Analyzing: {url}")
            
            # 1. Extract URL Features
            features = extract_url_features(url)
            
            # 2. Query all your Teachers (APIs)
            try:
                # Add delay to avoid rate limiting
                time.sleep(1) 
                
                g_safe = check_google_safe_browsing(url)
                v_total = check_virustotal(url)
                o_phish = check_openphish(url)
                u_scan = check_urlscan(url)
                
                # 3. Determine the final label. 
                # If ANY of the major APIs flag it, we consider it malicious (1).
                # You can adjust this logic (e.g., require at least 2 APIs to agree).
                total_flags = g_safe + v_total + o_phish + u_scan
                final_label = 1 if total_flags > 0 else 0

                # Write row to dataset
                writer.writerow([
                    url,
                    features["url_length"], features["domain_length"], features["has_https"], 
                    features["num_dots"], features["num_hyphens"], features["has_login_keyword"], features["has_admin_keyword"],
                    g_safe, v_total, o_phish, u_scan,
                    final_label
                ])
                
            except Exception as e:
                print(f"Error analyzing {url}: {e}")

    print(f"Dataset generated and saved to {output_file}!")

if __name__ == "__main__":
    main()
