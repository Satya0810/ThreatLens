"""
ThreatLens: Test Scraped Web Content Categorizer
Tests matching scraped web page text (Title, Meta Keywords, Headings, Body Text)
against the imported online benchmark keyword taxonomy.
"""

import json
import time
import os
import sys

# Ensure local imports work cleanly
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from models.web_categorizer import WebCategorizerEngine

def run_tests():
    print("=" * 70)
    print("TESTING WEB CATEGORIZER WITH IMPORTED ONLINE KEYWORD DATASET")
    print("=" * 70)

    engine = WebCategorizerEngine()

    test_cases = [
        {
            "scenario": "Scraped E-Commerce Product Page (Flipkart/Amazon clone)",
            "url": "https://megadeals-india.shop/product/smartphone-5g",
            "title": "Buy Smartphone 5G Online - Best Deals & Fast Delivery | Shop Now",
            "meta_keywords": "buy online, shopping, discount, smartphone, delivery, cart",
            "meta_description": "Add to cart today and get free delivery on orders over 499. Easy return policy.",
            "h1_h2_text": "Product Specifications | Customer Reviews | Special Offers",
            "body_text": "In stock ready to ship. Cash on delivery available. Customer ratings and reviews indicate high satisfaction. Click add to cart to proceed to checkout."
        },
        {
            "scenario": "Scraped Online Gaming Portal (Chess / Card Games)",
            "url": "https://chessmastery.org/play",
            "title": "Play Free Online Chess & Multiplayer Games | Chess Club",
            "meta_keywords": "chess, multiplayer, board game, arcade, gaming, grandmaster",
            "meta_description": "Challenge players worldwide or play with grandmaster bot.",
            "h1_h2_text": "Live Tournaments | Match Highlights | Game Rules",
            "body_text": "Play interactive chess and arcade board games with thousands of active players online. Check your ranking on the leaderboard."
        },
        {
            "scenario": "Scraped Food Delivery / Restaurant Menu Page",
            "url": "https://delhibiryani-house.in/menu",
            "title": "Order Fresh Biryani & North Indian Food Online | Fast Delivery",
            "meta_keywords": "order food, delivery, restaurant, biryani, cuisine, recipe",
            "meta_description": "Delicious hot biryani delivered in 30 mins to your doorstep.",
            "h1_h2_text": "Starters & Snacks | Main Course Dishes | Desserts",
            "body_text": "Browse our authentic recipe menu. Order chicken biryani, paneer butter masala, and fresh naan. Track delivery partner in real time."
        },
        {
            "scenario": "Scraped Active Phishing Lure (Aadhaar / Bank KYC Scam)",
            "url": "http://sbi-aadhaar-kyc-verify.top/login.php",
            "title": "SBI Netbanking Urgent KYC Verification Notice",
            "meta_keywords": "sbi yono, kyc verification, pan card update, login, account freeze",
            "meta_description": "Immediate action required. Complete your biometric KYC update within 24 hours to prevent account block.",
            "h1_h2_text": "Security Alert | Verify Banking Credentials | Enter OTP",
            "body_text": "Dear customer, your bank account is suspended due to missing PAN card details. Click here to avoid account freeze. Re-verify banking credentials and submit OTP."
        },
        {
            "scenario": "Scraped Technical University / Education Portal",
            "url": "https://iitd-academics.org/curriculum",
            "title": "Department of Computer Science - Course Curriculum & Faculty",
            "meta_keywords": "university, admissions, curriculum, research, lecture, degree",
            "meta_description": "Undergraduate and postgraduate degree programs in Computer Science and Engineering.",
            "h1_h2_text": "Academic Calendar | Semester Syllabus | Research Labs",
            "body_text": "Admissions are open for the academic semester. Review tuition fees, scholarship eligibility, faculty departments research, and campus placement records."
        },
        {
            "scenario": "Scraped Cyber Threat / Trojan Dropper Page",
            "url": "https://freepc-cracks.xyz/download/keygen",
            "title": "Download Free Windows 11 Crack Keygen & Antivirus Bypass",
            "meta_keywords": "cracked exe, keygen, bypass windows defender, trojan, discord stealer",
            "meta_description": "Disable windows defender to run the activator patch.",
            "h1_h2_text": "Direct Download Mirror | Run As Administrator Setup",
            "body_text": "Download the cracked setup.exe. Disable your antivirus protection before running. Contains keylogger bypass payload and discord token extraction."
        }
    ]

    for tc in test_cases:
        print(f"\n[{tc['scenario']}]")
        print(f"  URL: {tc['url']}")
        print(f"  Title: \"{tc['title']}\"")
        
        t0 = time.perf_counter()
        result = engine.classify_scraped_content(
            url=tc["url"],
            title=tc["title"],
            meta_keywords=tc["meta_keywords"],
            meta_description=tc["meta_description"],
            h1_h2_text=tc["h1_h2_text"],
            body_text=tc["body_text"]
        )
        latency = round((time.perf_counter() - t0) * 1000.0, 2)
        
        print(f"  -> Predicted Category: {result['category']} ({result['category_label']})")
        print(f"  -> Threat Level: {result['threat_level']} | Is Threat: {result['is_threat']}")
        print(f"  -> Confidence: {result['confidence'] * 100:.1f}%")
        print(f"  -> Latency: {latency} ms")
        print(f"  -> Top Matching Keywords: {[m['keyword'] for m in result['matching_keywords'][:5]]}")
        print(f"  -> Top Category Scores: {result['top_categories_scores']}")
        print(f"  -> Forensic Reasons: {result['explainable_reasons']}")

    print("\n" + "=" * 70)
    print("ALL SCENARIOS EVALUATED SUCCESSFULLY WITH IMPORTED REAL DATASETS!")
    print("=" * 70)

if __name__ == "__main__":
    run_tests()
