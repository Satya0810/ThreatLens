"""
ThreatLens Web Category Keyword Dataset Builder
Compiles thousands of curated, weighted keywords and phrases mapped to SiteCategory.
Used to compare against web scraper returned content:
- Page Title (<title>)
- Meta Keywords (<meta name="keywords">)
- Meta Description (<meta name="description">)
- Headings (<h1>, <h2>)
- Body Text & Anchor Links

Exports:
- ml_pipeline/data/web_category_keywords.csv
- cloud_backend/data/web_category_keywords.json
"""

import os
import json
import csv

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_OUT = os.path.join(BASE_DIR, "ml_pipeline", "data", "web_category_keywords.csv")
JSON_OUT = os.path.join(BASE_DIR, "cloud_backend", "data", "web_category_keywords.json")

KEYWORD_DATA = [
    # ── DANGEROUS: Phishing & Credential Theft ──────────────────────────────────
    ("PHISHING", "DANGEROUS", 5.0, [
        "urgent kyc verification", "kyc update mandatory", "account suspended within 24 hours",
        "verify your netbanking", "sbi yono kyc link", "pan card update required",
        "electricity power disconnected tonight", "unpaid electricity bill update",
        "sim card block warning", "sim verification required", "biometric aadhaar lock update",
        "debit card expiring re-activate", "credit card reward points expire today",
        "income tax refund pending claim", "customs duty parcel pending", "lottery prize money claim",
        "click here to avoid account freeze", "login to unfreeze your account", "confirm your upi pin",
        "enter otp to verify payment", "security notice account compromised", "re-verify banking credentials",
        "hdfc netbanking session expired", "icici bank urgent kyc", "axis bank account block",
        "pnb one netbanking verify", "kotak 811 verification link", "paytm kyc expired",
        "phonepe cashback pending claim", "google pay reward claim now", "bhim upi refund portal",
        "post office parcel delivery failed", "india post address confirmation update",
        "challan penalty pending pay now", "traffic police challan discount", "epfo claim reject verify uan",
        "urgent bank security verification", "enter cvv and expiry to verify", "update registered mobile number",
        "deactivation of banking services", "immediate action required bank account", "restore restricted access",
        "unauthorized transaction detected verify", "confirm debit card details", "validate payment pin",
        "apple id suspended verify identity", "google account security alert verify password",
        "paypal account limited resolve now", "netflix payment failed update billing",
        "microsoft 365 password expires today", "dhl package customs fee unpaid",
        "fedex delivery address missing update", "amazon order on hold confirm details",
        "cryptocurrency wallet compromised sync", "metamask seed phrase restore",
        "ledger live update firmware security", "binance withdrawal security verification"
    ]),

    ("TECH_SUPPORT_SCAMS", "DANGEROUS", 5.0, [
        "call microsoft support immediately", "windows defender security warning", "computer infected with virus",
        "trojan spy detected do not restart", "toll free support helpline", "error code 0x800",
        "apple security alert compromised", "call customer service toll-free", "browser locked by administrator",
        "firewall breached call now", "critical system alert spyware detected", "install anydesk to resolve",
        "download teamviewer support", "install quicksupport for assistance", "grant remote desktop access",
        "certified technician online help", "windows activation key expired call", "financial information compromised call",
        "contact customer care number", "instant refund support representative", "screen share for diagnosis",
        "technician will take control to clean virus", "ransomware attack threat call helpdesk",
        "firewall error 0x8024402c", "call 1800 toll free tollfree support", "certified windows engineer"
    ]),

    ("INVESTMENT_SCAMS", "DANGEROUS", 5.0, [
        "guaranteed daily returns", "double your money in 7 days", "100% risk free trading",
        "automated forex trading bot", "high yield investment program", "daily 10% profit guaranteed",
        "telegram vip crypto signals", "whatsapp stock tip group", "multilevel crypto earning",
        "binary options guaranteed profit", "deposit 1000 earn 10000", "task completion bonus withdraw",
        "crypto cloud mining passive income", "exclusive private placement platform", "pre-ipo guaranteed allocation",
        "zero risk arbitrage bot", "earn compounding daily interest", "join rich investors club",
        "sebi approved trading tip guarantee", "stock market jackpot call daily profit", "guaranteed profit sharing",
        "exclusive insider institutional trade", "crypto doubling smart contract", "automated wealth generation system"
    ]),

    ("JOB_SCAMS", "DANGEROUS", 5.0, [
        "part time work from home", "earn 5000 daily from mobile", "youtube video like job",
        "telegram task rating job", "simple data entry typing job", "amazon product review job",
        "google maps review earning", "prepaid task deposit needed", "daily payout instant withdrawal",
        "no experience required high salary", "whatsapp freelance hiring manager", "part-time online agent",
        "recharge task wallet balance", "instant sign up bonus withdrawal", "hotel rating task job",
        "merchant task order processing", "tier 2 task upgrade deposit", "daily salary credited on telegram",
        "hiring urgent online assistants 500/hr", "tiktok video watching job pay", "app testing daily income"
    ]),

    ("LOTTERY_SCAMS", "DANGEROUS", 5.0, [
        "kaun banega crorepati lucky draw", "kbc lottery winner list", "whatsapp lucky draw winner",
        "claim your 25 lakh lottery", "unclaimed prize money winner", "official winner confirmation",
        "lucky coupon prize distribution", "pay processing fee to release prize", "congratulations selected as winner",
        "foreign lottery winning ticket", "gift parcel release fee", "rbi prize transfer approval",
        "mega jackpot winner certificate", "claim prize before deadline", "international sweepstakes winner notice",
        "lucky dip mobile winner reward", "tax clearance fee for lottery prize"
    ]),

    ("MALWARE", "DANGEROUS", 5.0, [
        "download cracked exe", "keygen crack serial patch", "bypass antivirus protection",
        "disable windows defender to run", "njrat trojan payload", "remote access trojan rat",
        "stealer payload discord token", "redline stealer build", "cryptojacker silent miner",
        "ransomware decrypt tool", "modded apk unlimited money", "free wallhack cheat aimbot",
        "download free robux generator", "free fortnite vbucks generator", "bypass cloudflare bot script",
        "payload injector dll", "run as administrator setup.exe", "powershell bypass execution policy",
        "obfuscated powershell script download", "macro enabled excel invoice xlsxm", "svchost trojan injection",
        "credential dumping mimikatz", "keylogger background capture", "reverse shell connection listener",
        "covert command and control server c2", "bypass uac prompt payload"
    ]),

    ("RANSOMWARE", "DANGEROUS", 5.0, [
        "all your files have been encrypted", "pay bitcoin ransom to decrypt", "ransomware payment portal tor",
        "decryption key instructions", "readme_for_decrypt.txt", "personal identification key ransom",
        "time left to pay ransom countdown", "files deleted permanently unless paid", "leaked confidential database auction"
    ]),

    ("SPYWARE", "DANGEROUS", 5.0, [
        "spy on spouse whatsapp chat", "hidden call recorder apk", "track mobile location secretly",
        "stealth microphone listening app", "pegasus spyware payload", "read deleted sms secretly",
        "remote camera surveillance stealth", "invisible keystroke logger app", "stalkerware phone monitor"
    ]),

    ("BOTNET", "DANGEROUS", 5.0, [
        "mirai botnet scanner", "ddos stresser booter service", "irc botnet command controller",
        "zombie machines ddos attack", "layer 7 attack booter", "udp flood amplification botnet"
    ]),

    ("EXPLOIT_KITS", "DANGEROUS", 5.0, [
        "zero day exploit payload", "browser heap spray exploit", "privilege escalation exploit poc",
        "cve remote code execution rce", "metasploit payload staged", "buffer overflow shellcode"
    ]),

    ("CRYPTOJACKING", "DANGEROUS", 5.0, [
        "coinhive miner script", "monero browser miner", "silent web cryptominer",
        "wasm cryptominer worker", "crypto cpu mining in background", "harvest xmr browser",
        "webassembly cryptonight miner", "in-browser coin mining pool", "cryptojack background process"
    ]),

    ("ILLEGAL_DRUG_SALES", "DANGEROUS", 5.0, [
        "buy fentanyl online no rx", "order oxycontin stealth delivery", "cocaine direct vendor",
        "mdma pills postal delivery", "methamphetamine darknet shop", "buy controlled prescription pills",
        "cannabis weed delivery postal discreet", "psychedelic mushrooms discreet shipment"
    ]),

    ("ILLEGAL_WEAPONS", "DANGEROUS", 5.0, [
        "buy unregistered firearms", "ghost gun build kit", "3d printed gun files stl",
        "silencer suppressor untraceable", "glock auto sear switch", "military grade explosives purchase"
    ]),

    ("STOLEN_DATA_MARKETS", "DANGEROUS", 5.0, [
        "buy credit card dumps cvv", "leaked sql database dump", "fullz identity package ssn",
        "cloned atm debit card", "fresh hacked paypal logs", "stealer combo list email pass",
        "corporate data breach dump sale", "buy verified kyc accounts"
    ]),

    # ── CAUTION: Gambling & Betting ─────────────────────────────────────────────
    ("ONLINE_CASINOS", "CAUTION", 4.0, [
        "online casino real money", "live roulette tables", "slot machine jackpots",
        "deposit bonus 100%", "free spins promo code", "blackjack live dealer",
        "teen patti real cash", "andar bahar live game", "dragon tiger cash game",
        "aviator crash betting game", "roulette spin wheel", "baccarat tournament",
        "crypto gambling faucet", "poker cash tables", "megaways jackpot slot",
        "lightning roulette multiplier", "high roller casino lounge", "instant withdrawal casino",
        "welcome bonus 50000 casino", "live croupier card dealing", "spin the wheel bonus"
    ]),

    ("SPORTS_BETTING", "CAUTION", 4.0, [
        "cricket betting exchange id", "ipl betting odds match winner", "betfair sports exchange",
        "in-play live betting rates", "football accumulator bet slip", "horse racing live odds",
        "over under match goals bet", "handicap betting lines", "top batsman market odds",
        "bookmaker sign up bonus", "sports betting telegram id", "session fancy betting cricket"
    ]),

    ("POKER", "CAUTION", 4.0, [
        "online texas holdem poker", "poker tournament buy-in chips", "omaha poker cash games",
        "poker high stakes table", "freeroll poker tournament prize", "wsop satellite online poker"
    ]),

    ("PORNOGRAPHY", "CAUTION", 4.0, [
        "adult video streaming", "live cam girls sex", "hardcore pornography",
        "xxx movies free", "erotic video clips", "explicit webcam shows",
        "escort service contact", "adult sex dating", "nude photo gallery",
        "adult tube clips hd", "milf amateur porn movies", "strip chat live models"
    ]),

    ("SOFTWARE_PIRACY", "CAUTION", 4.0, [
        "torrent download magnet link", "full version crack download", "license key generator",
        "bypass activation keygen", "patched crack exe", "download nulled wordpress theme",
        "adobe premiere pro free crack", "windows 11 activator kms", "direct torrent mirror",
        "fl studio cracked regkey", "autocad crack serial number", "repack fitgirl download",
        "crack status dmenu", "unlocked steam rip download", "keygen xforce software"
    ]),

    ("MOVIE_PIRACY", "CAUTION", 4.0, [
        "watch online free full movie", "download movie 1080p torrent", "camrip hindi dubbed",
        "filmywap new movie download", "tamilrockers movie mirror", "pagalworld mp3 songs free",
        "streaming hdrip bluray free", "pirated web series download", "free movie stream no signup",
        "123movies free watch", "fmovies stream free hd", "telegram movie channel download link"
    ]),

    ("VPN_PROXY_SERVICES", "CAUTION", 3.5, [
        "free vpn proxy extension", "unblock websites anonymous ip", "hide ip address location",
        "wireguard openvpn config", "socks5 proxy server list", "bypass geo restriction streaming",
        "encrypted tunnel no logs vpn", "nordvpn expressvpn surfshark", "tor onion bridge proxy"
    ]),

    ("PROPAGANDA_SITES", "CAUTION", 3.5, [
        "conspiracy theory exposed truth", "deep state globalist agenda", "illuminati secret society",
        "faked moon landing hoax", "chemtrails population control", "suppressed cancer cure secret",
        "stolen election ballot stuffing", "microchip vaccine depopulation", "sensationalized clickbait rumor"
    ]),

    ("CRYPTOCURRENCY", "CAUTION", 3.5, [
        "bitcoin btc price chart", "ethereum eth gas fees", "solana ecosystem token",
        "decentralized exchange dex swap", "yield farming liquidity pool", "crypto wallet private key",
        "crypto market cap ranking", "defi lending protocol", "smart contract deployment gas",
        "stablecoin usdt usdc reserves", "crypto staking rewards apy", "crypto hardware ledger"
    ]),

    ("CRYPTO_EXCHANGES", "CAUTION", 3.5, [
        "binance spot margin trading", "wazirx crypto order book", "coinbase pro trading platform",
        "bybit perpetual futures leverage", "kraken order matching engine", "kucoin trade altcoins",
        "deposit fiat buy crypto", "crypto withdrawal 2fa security"
    ]),

    ("NFT_MARKETPLACES", "CAUTION", 3.5, [
        "opensea nft marketplace", "mint generative art nft", "rarible digital collectibles",
        "erc-721 token metadata", "floor price ethereum volume", "nft smart contract minting"
    ]),

    # ── SAFE: Banking, Finance & Payments ───────────────────────────────────────
    ("BANKING", "SAFE", 4.5, [
        "savings account interest rate", "fixed deposit fd calculator", "recurring deposit rd",
        "personal loan interest rates", "home loan eligibility calculator", "net banking login",
        "mobile banking application", "branch locator ifsc code", "micr code rtgs neft",
        "cheque book request", "bank account statement download", "debit card pin generation",
        "credit card apply online", "demat trading account link", "car loan financing",
        "education loan interest subsidy", "sovereign gold bond sgb", "public provident fund ppf",
        "senior citizen savings scheme", "nre nro account banking", "forex card exchange rates",
        "overdraft facility against fd", "lockers availability bank branch", "nominee addition bank",
        "state bank of india online", "hdfc bank personal banking", "icici bank internet banking",
        "punjab national bank netbanking", "axis bank corporate banking", "kotak mahindra net banking",
        "bank of baroda connect", "canara bank online portal", "union bank netbanking login"
    ]),

    ("PAYMENT_PLATFORMS", "SAFE", 4.5, [
        "upi qr code scanner", "virtual payment address vpa", "scan and pay merchant",
        "bhim upi transactions", "instant money transfer imps", "unified payments interface",
        "payment gateway api integration", "pos card swipe machine", "soundbox payment alert",
        "fastag recharge wallet", "metro card smart recharge", "utility bill payment electricity",
        "recharge prepaid mobile dth", "credit card bill payment", "merchant checkout integration",
        "recurring mandate e-mandate", "payment settlement dashboard", "refund processing gateway",
        "razorpay merchant dashboard", "paytm payments bank wallet", "phonepe upi transaction",
        "google pay tez payments", "ccavenue payment gateway", "cashfree payouts api"
    ]),

    ("STOCK_MARKET", "SAFE", 4.0, [
        "nse bse live share price", "nifty 50 sensex index", "stock market market depth",
        "demat account opening zerodha", "groww app stock investment", "angel one angel broking",
        "stock portfolio holdings nav", "candlestick technical chart tradingview", "intraday margin trading",
        "initial public offering ipo allotment", "dividend yield record date", "market capitalization large cap",
        "futures and options f&o open interest", "bull bear market trend analysis"
    ]),

    ("MUTUAL_FUNDS", "SAFE", 4.0, [
        "systematic investment plan sip calculator", "equity mutual funds returns", "debt hybrid liquid funds",
        "elss tax saving mutual fund", "net asset value nav today", "amfi mutual fund india",
        "asset management company amc", "index fund low expense ratio", "fund manager track record"
    ]),

    ("INSURANCE", "SAFE", 4.0, [
        "term life insurance policy cover", "health insurance cashless hospitalization", "motor car two wheeler insurance",
        "critical illness insurance rider", "claim settlement ratio policybazaar", "insurance premium calculator",
        "third party vehicle liability", "zero depreciation insurance cover", "lic life insurance corporation policy",
        "travel insurance international emergency", "marine cargo insurance policy"
    ]),

    ("TAX_ACCOUNTING", "SAFE", 4.0, [
        "tax deduction at source tds 16", "advance tax quarterly payment", "chartered accountant audit report",
        "gst invoice calculation e-way bill", "capital gains tax calculation", "section 80c tax saving deductions",
        "form 26as annual tax statement", "balance sheet profit loss statement", "corporate tax computation"
    ]),

    ("PERSONAL_FINANCE", "SAFE", 4.0, [
        "cibil credit score check free", "experian credit rating report", "budget planning monthly expenses",
        "emergency savings fund target", "debt consolidation repayment plan", "retirement pension planning calculator",
        "wealth management family office", "financial literacy savings tips"
    ]),

    # ── SAFE: Government & Public Sector ────────────────────────────────────────
    ("GOVERNMENT_PORTALS", "SAFE", 4.5, [
        "unique identification authority of india", "aadhaar enrolment status check",
        "download e-aadhaar letter", "order aadhaar pvc card", "update address in aadhaar",
        "permanent account number pan", "nsdl e-gov pan portal", "utiitsl pan application",
        "link aadhaar with pan card", "income tax department e-filing portal",
        "file income tax return itr", "view form 26as tax credit", "annual information statement ais",
        "employees provident fund organisation", "epfo uan member portal", "epf passbook balance check",
        "online provident fund claim withdrawal", "passport seva kendra appointment",
        "track passport application status", "police clearance certificate pcc",
        "voter service portal epic card", "national voters services nsvp", "electoral roll search name",
        "ministry of road transport and highways", "parivahan sewa driving licence",
        "vahan vehicle registration details", "e-challan payment online status", "fitness certificate vehicle",
        "digilocker digital india initiative", "verified government documents wallet",
        "central board of indirect taxes and customs", "gst portal login gstr 3b", "goods and services tax return",
        "national portal of india citizen services", "gazette of india official notification",
        "right to information online rti request", "pm kisan samman nidhi status", "ayushman bharat golden card",
        "ration card status food supply portal", "national scholarship portal nsp", "umang citizen application",
        "mygov citizen engagement portal", "swachh bharat mission dashboard", "invest india trade portal"
    ]),

    ("PUBLIC_SERVICES", "SAFE", 4.0, [
        "birth certificate municipality portal", "death certificate application online", "property tax online payment",
        "water supply connection bill", "electricity board online consumer login", "land records bhulekh khasra khatauni",
        "trade license renewal corporation", "building plan sanction authority", "sewerage drainage complaint portal"
    ]),

    ("PUBLIC_TRANSPORT", "SAFE", 4.0, [
        "irctc train ticket reservation", "train running status live tracker", "pnr confirmation probability",
        "tatkal ticket booking timings", "berth coach seat position", "vande bharat express timetable",
        "metro smart card recharge", "state road transport bus booking", "ksrtc msrtc bus timetable",
        "train fare chart enquiry", "irctc e-catering food delivery on track", "indian railways passenger enquiry",
        "delhi metro dmrc smart card", "mumbai local suburban train schedule", "bengaluru namma metro timetable"
    ]),

    ("LEGAL_SERVICES", "SAFE", 4.0, [
        "supreme court of india orders", "high court case status cause list", "e-courts services case information",
        "legal notice draft advocate consultation", "consumer dispute redressal commission", "vakalatnama court filing",
        "arbitration conciliation dispute", "trademark patent registration ipindia", "bail application criminal lawyer"
    ]),

    # ── SAFE: E-Commerce & Retail ───────────────────────────────────────────────
    ("ONLINE_RETAIL", "SAFE", 4.0, [
        "add to cart", "buy now checkout", "shopping bag items", "free delivery on orders over",
        "cash on delivery available", "return and exchange policy", "order tracking status",
        "customer ratings and reviews", "product specifications features", "discount coupon promo code",
        "wishlist save for later", "in stock ready to ship", "out of stock notify me",
        "lightning deals flash sale", "amazon prime delivery", "flipkart assured quality",
        "seller details warranty information", "secure payment checkout ssl", "easy 7 days return",
        "price drop alert", "compare products", "verified purchase review", "emi on credit cards available",
        "meesho online shopping lowest prices", "myntra fashion shopping festival", "ajio online clothes shopping",
        "tata cliq luxury online store", "nykaa beauty cosmetics makeup", "croma electronics retail store"
    ]),

    ("GROCERY", "SAFE", 3.5, [
        "instant 10 minute delivery", "fresh fruits and vegetables", "dairy bread and eggs",
        "organic farm fresh produce", "cooking essentials oil atta", "grocery shopping delivery",
        "zepto delivery", "blinkit order", "swiggy instamart", "bigbasket daily", "dmart ready online",
        "grocery discounts pantry essentials", "frozen foods snacks beverages"
    ]),

    ("FASHION", "SAFE", 3.5, [
        "mens shirts trousers formal wear", "womens ethnic sarees kurtis", "western dresses casual wear",
        "designer sneakers athletic shoes", "handbags leather backpacks wallets", "gold jewelry diamond necklaces",
        "sunglasses luxury watches timepieces", "fashion seasonal collection sale", "size chart fitting guide"
    ]),

    ("ELECTRONICS", "SAFE", 3.5, [
        "smartphone 5g amoled display", "laptop intel core i7 processor", "4k smart tv dolby audio",
        "wireless noise cancelling headphones", "bluetooth portable speakers", "smartwatch fitness health tracking",
        "mirrorless dslr digital camera", "gaming console graphics card gpu", "tablet ipad stylus pen"
    ]),

    # ── SAFE: Technology & Developer Platforms ──────────────────────────────────
    ("DEVELOPER_TOOLS", "SAFE", 4.0, [
        "github repository code", "git pull request merge", "commit history diff",
        "api documentation reference", "sdk software development kit", "restful api endpoint",
        "graphql query schema", "npm package manager registry", "pypi python package install",
        "dockerfile container build", "kubernetes cluster orchestration", "ci cd pipeline workflow",
        "source code browse issues", "stackoverflow question answer", "developer console dashboard",
        "webhooks event trigger", "open source apache license", "unit testing integration test",
        "gitlab devops platform", "bitbucket code repository", "postman api testing workspace",
        "visual studio code extensions", "jetbrains intellij idea ide", "terraform infrastructure as code"
    ]),

    ("AI_ML_PLATFORMS", "SAFE", 4.0, [
        "artificial intelligence platform", "large language model llm", "deep neural network training",
        "transformer model architecture", "hugging face model hub", "generative ai prompts",
        "computer vision classification", "natural language processing nlp", "vector embeddings search",
        "retrieval augmented generation rag", "reinforcement learning rlhf", "fine tuning model checkpoint",
        "open ai api key", "anthropic claude inference", "diffusion model generation", "chatgpt prompt assistant",
        "langchain agent workflow", "pytorch tensor operations", "tensorflow keras model",
        "pinecone vector database", "mistral open weights model", "gemini multimodality model"
    ]),

    ("CLOUD_SERVICES", "SAFE", 4.0, [
        "amazon web services aws console", "google cloud platform gcp", "microsoft azure portal",
        "virtual machine ec2 instance", "s3 bucket storage object", "serverless lambda functions",
        "cdn content delivery network", "cloudflare dns protection", "vercel serverless deployment",
        "cloud database postgres rds", "kubernetes engine gke", "elastic load balancer alb",
        "digitalocean droplet hosting", "heroku application deployment", "supabase backend as service"
    ]),

    ("CYBERSECURITY", "SAFE", 4.0, [
        "threat intelligence feed indicator", "endpoint detection and response edr", "zero trust architecture",
        "siem log security analysis", "cve vulnerability database nvd", "penetration testing report pentest",
        "security operations center soc 24/7", "ssl tls certificate encryption", "ddos mitigation cloudflare",
        "web application firewall waf rules", "security compliance soc2 iso27001", "identity and access management iam"
    ]),

    # ── SAFE: News, Media & Publishing ──────────────────────────────────────────
    ("NATIONAL_NEWS", "SAFE", 3.5, [
        "breaking news headlines", "live news coverage updates", "editorial opinion column",
        "prime minister speech address", "parliament budget session update", "cabinet committee decisions",
        "supreme court ruling judgment", "election results live tally", "state assembly election coverage",
        "pti press trust of india", "ani news wire report", "investigative journalism report",
        "weather department imd alert", "monsoon forecast rainfall", "economic survey rbi monetary policy",
        "the hindu national news", "times of india latest updates", "hindustan times headlines",
        "indian express epaper news", "ndtv 24x7 live news", "dd news official broadcast"
    ]),

    ("INTERNATIONAL_NEWS", "SAFE", 3.5, [
        "united nations security council resolution", "global geopolitics foreign policy",
        "white house official press briefing", "european union summit regulations",
        "reuters international wire news", "associated press global headlines",
        "bbc world news coverage", "al jazeera english international", "geopolitical diplomatic relations"
    ]),

    ("SPORTS_NEWS", "SAFE", 3.5, [
        "live cricket score ball by ball", "ipl match live streaming", "t20 world cup scorecard",
        "premier league football table", "champions league match fixture", "cricket batting bowling figures",
        "olympic games medal tally", "grand slam tennis tournament", "formula 1 race standings",
        "badminton world tour", "pro kabaddi league live", "player transfer news rumors",
        "espncricinfo live scorecard", "cricbuzz ball by ball commentary", "fifa world cup football qualifiers"
    ]),

    # ── SAFE: Education, Careers & Exams ────────────────────────────────────────
    ("SCHOOLS_UNIVERSITIES", "SAFE", 4.0, [
        "university undergraduate admissions", "postgraduate degree curriculum", "academic calendar semester",
        "faculty departments research", "campus placement drive records", "alumni association network",
        "tuition fees scholarship financial aid", "convocation ceremony degree", "prospectus download application",
        "hostel accommodation campus", "vice chancellor address", "accreditation naac nirf ranking",
        "indian institute of technology iit", "national institute of technology nit", "delhi university admissions du"
    ]),

    ("ONLINE_LEARNING", "SAFE", 3.5, [
        "online course certificate", "free video lectures tutorial", "complete python bootcamp",
        "data science machine learning course", "interactive coding challenges", "coursera course enrollment",
        "udemy discount coupons", "edx university courses", "nptel swayam course registration",
        "full stack web development", "practice quiz mock tests", "learning path roadmap",
        "khan academy free lessons", "unacademy plus subscription", "byjus learning program"
    ]),

    ("EXAM_PREPARATION", "SAFE", 4.0, [
        "sarkari result admit card", "upsc civil services preliminary exam", "jee main answer key rank",
        "neet ug admit card scorecard", "gate entrance exam syllabus", "ssc cgl notification exam date",
        "ibps bank po clerical exam", "cat entrance mock test series", "previous year question papers",
        "cutoff marks merit list", "answer key challenge window", "exam hall ticket download",
        "testbook mock test pass", "gradeup byjus exam prep", "nda na entrance examination"
    ]),

    ("PROFESSIONAL_NETWORKING", "SAFE", 4.0, [
        "job openings careers apply", "upload resume cv portfolio", "full time part time remote jobs",
        "hiring talent recruiter connect", "salary compensation range glassdoor", "company reviews workplace culture",
        "interview questions preparation", "linkedin profile professional network", "naukri job alerts portal",
        "indeed employment search", "internship stipend certificate", "human resources contact hr",
        "foundit monster jobs india", "shine job search portal", "angellist wellfound startup jobs"
    ]),

    # ── SAFE: Travel & Hospitality ──────────────────────────────────────────────
    ("FLIGHT_BOOKING", "SAFE", 4.0, [
        "book flight tickets online", "flight schedule departure arrival", "pnr status enquiry",
        "web check in boarding pass", "baggage allowance cabin check-in", "frequent flyer miles rewards",
        "airline fare calendar lowest", "domestic international flights", "direct non stop flights",
        "round trip one way multi city", "terminal departure gates", "air india indiGo spicejet vistara",
        "makemytrip flight booking", "cleartrip cheap air tickets", "easemytrip discount flights"
    ]),

    ("HOTEL_BOOKING", "SAFE", 4.0, [
        "hotel room reservation", "check in check out dates", "deluxe luxury suite room",
        "guest ratings and reviews", "complimentary breakfast wifi", "swimming pool hotel amenities",
        "resort vacation staycation", "cancellation policy refundable", "oyo rooms booking",
        "homestay villas holiday rental", "hotel location map distance", "booking.com hotel reservation",
        "agoda hotels cheap deals", "airbnb vacation home rentals"
    ]),

    # ── SAFE: Healthcare & Wellness ─────────────────────────────────────────────
    ("HOSPITALS_CLINICS", "SAFE", 4.0, [
        "book doctor appointment online", "hospital opd timings consultation", "specialist doctor cardiology",
        "neurology orthopedic oncology", "emergency trauma center 24x7", "health checkup package full body",
        "laboratory pathology test booking", "radiology mri ct scan appointment", "patient admission guidelines",
        "nabh accredited hospital", "icu bed availability helpline", "cashless insurance tpa desk",
        "apollo hospitals appointment", "fortis healthcare specialist", "max healthcare doctor consultation",
        "aiims opd online registration"
    ]),

    ("PHARMACY", "SAFE", 4.0, [
        "order prescription medicine online", "upload doctor prescription rx", "generic medicine substitute",
        "home delivery medicines", "wellness supplements vitamins", "diabetic care bp monitor",
        "1mg pharmacy order", "apollo pharmacy online", "netmeds medicine delivery", "pharmeasy discount coupon",
        "medlife online pharmacy", "ayurvedic herbal medicines patanjali"
    ]),

    ("FITNESS_WELLNESS", "SAFE", 3.5, [
        "gym membership workout routines", "yoga asanas meditation guide", "weight loss diet plan calories",
        "protein powder fitness supplements", "home workout fitness trainer", "marathon running training",
        "mental wellness stress relief", "cult fit workout classes", "healthify me calorie counter"
    ]),

    # ── SAFE: Entertainment, Media & Lifestyle ──────────────────────────────────
    ("MOVIE_STREAMING", "SAFE", 3.5, [
        "watch movies online streaming", "original web series episodes", "official movie trailer teaser",
        "watch in 4k ultra hd dolby", "english subtitles audio tracks", "netflix original shows",
        "disney hotstar live streaming", "amazon prime video series", "continue watching recommendations",
        "cast and crew synopsis", "season finale all episodes", "zee5 web series online", "sonyliv sports live stream"
    ]),

    ("MUSIC_STREAMING", "SAFE", 3.5, [
        "stream songs playlist online", "latest albums top 50 charts", "artist official discography",
        "lyrics with song playback", "podcast episodes audio show", "spotify playlist recommendations",
        "apple music lossless audio", "gaana jiosaavn stream songs", "radio stations live stream",
        "amazon prime music hd", "youtube music streaming playlist"
    ]),

    ("GAMING", "SAFE", 3.5, [
        "online multiplayer gameplay", "pc console video game", "steam workshop mods community",
        "epic games free weekly game", "esports championship live tournament", "walkthrough guide tips tricks",
        "game patch release notes", "discord gaming server community", "chess online play lichess",
        "play with grandmaster bot", "fps battle royale rank up", "role playing game rpg quest",
        "playstation store ps5 games", "xbox game pass cloud gaming", "nintendo switch e-shop"
    ]),

    ("FOOD_DELIVERY", "SAFE", 3.5, [
        "order food online delivery", "restaurant menu dishes price", "cuisine north indian chinese",
        "biryani pizza burger desserts", "track delivery partner live location", "swiggy discount coupon code",
        "zomato gold dining discount", "ratings delivery time 30 mins", "pure veg restaurant filter",
        "e-catering train food delivery", "bakery cakes birthday ordering"
    ]),

    ("SOCIAL_MEDIA", "SAFE", 3.5, [
        "share post timeline feed", "follow profile followers count", "like comment repost retweet",
        "story reel video views", "trending hashtags explore page", "direct message chat friend",
        "tag friends in photo", "social community group page", "verified creator profile badge",
        "instagram creator studio", "twitter x trends today", "facebook community groups"
    ]),

    ("MESSAGING", "SAFE", 4.0, [
        "end to end encrypted chat", "send audio message voice note", "video call group conference",
        "share documents files images", "whatsapp web scan qr code", "telegram channel public group",
        "signal messenger private chat", "instant messaging notification", "chat backup restore"
    ]),

    ("WEATHER", "SAFE", 3.5, [
        "live weather temperature radar", "hourly rainfall forecast humidity", "wind speed air quality aqi",
        "monsoon warning imd alert", "cyclone live tracker satellite", "fog visibility weather warning"
    ]),

    ("AUTOMOTIVE", "SAFE", 3.5, [
        "car on road price features", "electric vehicle ev range charging", "bike mileage test ride booking",
        "certified used cars spinny", "cardekho carwale price comparisons", "auto insurance vehicle servicing",
        "maruti suzuki hyundai tata motors", "mahindra suv booking test drive"
    ])
]

def build_keyword_dataset():
    print("=" * 65)
    print("ThreatLens: Compiling Rich Web Category Keywords Dataset")
    print("=" * 65)

    all_keywords = []
    category_summary = {}

    for cat, threat_lvl, weight, phrases in KEYWORD_DATA:
        for phrase in phrases:
            clean_phrase = phrase.strip().lower()
            all_keywords.append({
                "keyword": clean_phrase,
                "category": cat,
                "weight": weight,
                "threat_level": threat_lvl,
                "token_length": len(clean_phrase.split())
            })
        category_summary[cat] = category_summary.get(cat, 0) + len(phrases)

    all_keywords.sort(key=lambda x: (x["weight"], x["token_length"]), reverse=True)

    print(f"Total Unique Curated Keywords: {len(all_keywords):,}")
    print(f"Total Categories Covered: {len(category_summary):,}\n")
    print("Top Categories by Keyword Count:")
    for cat, count in sorted(category_summary.items(), key=lambda x: x[1], reverse=True)[:15]:
        print(f"  * {cat:25}: {count} keywords/phrases")

    # 1. Export CSV
    print(f"\nWriting Keyword CSV to: {CSV_OUT}...")
    with open(CSV_OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["keyword", "category", "weight", "threat_level", "token_length"])
        writer.writeheader()
        writer.writerows(all_keywords)
    print(f"  -> Exported CSV successfully ({os.path.getsize(CSV_OUT) / 1024:.1f} KB).")

    # 2. Export JSON for Fast Cloud Engine & Android Sync
    print(f"\nWriting Keyword JSON to: {JSON_OUT}...")
    with open(JSON_OUT, "w", encoding="utf-8", newline="") as f:
        json.dump({
            "version": "2.0.0",
            "total_keywords": len(all_keywords),
            "categories_count": len(category_summary),
            "keywords": all_keywords
        }, f, indent=2, ensure_ascii=False)
    print(f"  -> Exported JSON successfully ({os.path.getsize(JSON_OUT) / 1024:.1f} KB).")
    print("=" * 65)

if __name__ == "__main__":
    build_keyword_dataset()
