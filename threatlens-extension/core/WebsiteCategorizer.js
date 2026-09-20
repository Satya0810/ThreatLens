// WebsiteCategorizer.js
// Complete Port of ThreatLens Android WebsiteCategorizer.kt (100+ Categories + LLM7.io Zero-Shot AI Fallback)

import { Llm7Client } from './Llm7Client.js';

export class WebsiteCategorizer {

  static ThreatLevel = {
    SAFE: "SAFE",
    CAUTION: "CAUTION",
    DANGEROUS: "DANGEROUS"
  };

  // ═══════════════════════════════════════════════════════════════════════════
  //  FULL 100+ SITE CATEGORIES DEFINITIONS (Label, Emoji, ThreatLevel)
  //  1:1 Parity with com.safeqr.scanner.analysis.WebsiteCategorizer.SiteCategory
  // ═══════════════════════════════════════════════════════════════════════════
  static CATEGORIES = {
    // ── DANGEROUS CATEGORIES (ThreatLevel.DANGEROUS) ─────────────────────────
    MALWARE: { emoji: "🔴", label: "Malware", threatLevel: "DANGEROUS" },
    RANSOMWARE: { emoji: "🔴", label: "Ransomware", threatLevel: "DANGEROUS" },
    SPYWARE: { emoji: "🔴", label: "Spyware", threatLevel: "DANGEROUS" },
    BOTNET: { emoji: "🔴", label: "Botnet", threatLevel: "DANGEROUS" },
    EXPLOIT_KITS: { emoji: "🔴", label: "Exploit Kits", threatLevel: "DANGEROUS" },
    CRYPTOJACKING: { emoji: "🔴", label: "Cryptojacking", threatLevel: "DANGEROUS" },
    DDOS_TOOLS: { emoji: "🔴", label: "DDoS Tools", threatLevel: "DANGEROUS" },
    ROOTKITS: { emoji: "🔴", label: "Rootkits", threatLevel: "DANGEROUS" },
    TROJANS: { emoji: "🔴", label: "Trojans", threatLevel: "DANGEROUS" },
    PHISHING: { emoji: "🔴", label: "Phishing & Credential Harvesting", threatLevel: "DANGEROUS" },
    FINANCIAL_FRAUD: { emoji: "🔴", label: "Financial Fraud", threatLevel: "DANGEROUS" },
    IDENTITY_THEFT: { emoji: "🔴", label: "Identity Theft", threatLevel: "DANGEROUS" },
    INVESTMENT_SCAMS: { emoji: "🔴", label: "Investment Scams", threatLevel: "DANGEROUS" },
    FAKE_SHOPPING: { emoji: "🔴", label: "Fake Shopping", threatLevel: "DANGEROUS" },
    ROMANCE_SCAMS: { emoji: "🔴", label: "Romance Scams", threatLevel: "DANGEROUS" },
    JOB_SCAMS: { emoji: "🔴", label: "Job Scams", threatLevel: "DANGEROUS" },
    LOTTERY_SCAMS: { emoji: "🔴", label: "Lottery Scams", threatLevel: "DANGEROUS" },
    CHARITY_FRAUD: { emoji: "🔴", label: "Charity Fraud", threatLevel: "DANGEROUS" },
    TECH_SUPPORT_SCAMS: { emoji: "🔴", label: "Tech Support Scam", threatLevel: "DANGEROUS" },
    IMPERSONATION_SITES: { emoji: "🔴", label: "Brand Impersonation", threatLevel: "DANGEROUS" },
    CHILD_EXPLOITATION: { emoji: "🔴", label: "Child Exploitation", threatLevel: "DANGEROUS" },
    HUMAN_TRAFFICKING: { emoji: "🔴", label: "Human Trafficking", threatLevel: "DANGEROUS" },
    TERRORISM: { emoji: "🔴", label: "Terrorism", threatLevel: "DANGEROUS" },
    HATE_SPEECH: { emoji: "🔴", label: "Hate Speech", threatLevel: "DANGEROUS" },
    ILLEGAL_WEAPONS: { emoji: "🔴", label: "Illegal Weapons", threatLevel: "DANGEROUS" },
    HITMAN_SERVICES: { emoji: "🔴", label: "Hitman Services", threatLevel: "DANGEROUS" },
    SNUFF_GORE: { emoji: "🔴", label: "Snuff Gore", threatLevel: "DANGEROUS" },
    EXTREMIST_CONTENT: { emoji: "🔴", label: "Extremist Content", threatLevel: "DANGEROUS" },
    ILLEGAL_DRUG_SALES: { emoji: "🔴", label: "Illegal Drug Sales", threatLevel: "DANGEROUS" },
    STOLEN_DATA_MARKETS: { emoji: "🔴", label: "Stolen Data Markets", threatLevel: "DANGEROUS" },

    // ── CAUTION CATEGORIES (ThreatLevel.CAUTION) ─────────────────────────────
    PORNOGRAPHY: { emoji: "⚠️", label: "Pornography", threatLevel: "CAUTION" },
    EXPLICIT_ADULT_CONTENT: { emoji: "⚠️", label: "Explicit Adult Content", threatLevel: "CAUTION" },
    ESCORT_SERVICES: { emoji: "⚠️", label: "Escort Services", threatLevel: "CAUTION" },
    ADULT_DATING: { emoji: "⚠️", label: "Adult Dating", threatLevel: "CAUTION" },
    AGE_RESTRICTED_CONTENT: { emoji: "⚠️", label: "Age Restricted Content", threatLevel: "CAUTION" },
    CAM_SITES: { emoji: "⚠️", label: "Cam Sites", threatLevel: "CAUTION" },
    FETISH_CONTENT: { emoji: "⚠️", label: "Fetish Content", threatLevel: "CAUTION" },
    ADULT_GAMING: { emoji: "⚠️", label: "Adult Gaming", threatLevel: "CAUTION" },
    NUDITY: { emoji: "⚠️", label: "Nudity", threatLevel: "CAUTION" },
    SEXTING_PLATFORMS: { emoji: "⚠️", label: "Sexting Platforms", threatLevel: "CAUTION" },
    ONLINE_CASINOS: { emoji: "⚠️", label: "Online Casinos", threatLevel: "CAUTION" },
    SPORTS_BETTING: { emoji: "⚠️", label: "Sports Betting", threatLevel: "CAUTION" },
    POKER: { emoji: "⚠️", label: "Poker", threatLevel: "CAUTION" },
    ILLEGAL_GAMBLING: { emoji: "⚠️", label: "Illegal Gambling", threatLevel: "CAUTION" },
    CRYPTO_GAMBLING: { emoji: "⚠️", label: "Crypto Gambling", threatLevel: "CAUTION" },
    UNDERGROUND_BETTING: { emoji: "⚠️", label: "Underground Betting", threatLevel: "CAUTION" },
    LOTTERY_SITES: { emoji: "⚠️", label: "Lottery Sites", threatLevel: "CAUTION" },
    REAL_MONEY_GAMING: { emoji: "⚠️", label: "Real Money Gaming", threatLevel: "CAUTION" },
    FANTASY_BETTING: { emoji: "⚠️", label: "Fantasy Betting", threatLevel: "CAUTION" },
    GAMBLING_TUTORIALS: { emoji: "⚠️", label: "Gambling Tutorials", threatLevel: "CAUTION" },
    GAMBLING: { emoji: "⚠️", label: "Betting & Casino", threatLevel: "CAUTION" },
    MOVIE_PIRACY: { emoji: "⚠️", label: "Movie Piracy", threatLevel: "CAUTION" },
    SOFTWARE_PIRACY: { emoji: "⚠️", label: "Software Piracy", threatLevel: "CAUTION" },
    MUSIC_PIRACY: { emoji: "⚠️", label: "Music Piracy", threatLevel: "CAUTION" },
    GAME_PIRACY: { emoji: "⚠️", label: "Game Piracy", threatLevel: "CAUTION" },
    TORRENT_SITES: { emoji: "⚠️", label: "Torrent Sites", threatLevel: "CAUTION" },
    EBOOK_PIRACY: { emoji: "⚠️", label: "Ebook Piracy", threatLevel: "CAUTION" },
    SPORTS_STREAM_PIRACY: { emoji: "⚠️", label: "Sports Stream Piracy", threatLevel: "CAUTION" },
    ANIME_PIRACY: { emoji: "⚠️", label: "Anime Piracy", threatLevel: "CAUTION" },
    ACADEMIC_PIRACY: { emoji: "⚠️", label: "Academic Piracy", threatLevel: "CAUTION" },
    STREAMING_PIRACY: { emoji: "⚠️", label: "Streaming Piracy", threatLevel: "CAUTION" },
    PIRACY: { emoji: "⚠️", label: "Piracy & Torrenting", threatLevel: "CAUTION" },
    VPN_PROXY_SERVICES: { emoji: "⚠️", label: "VPN Proxy Services", threatLevel: "CAUTION" },
    DARK_WEB_ACCESS: { emoji: "⚠️", label: "Dark Web Access", threatLevel: "CAUTION" },
    CRYPTOCURRENCY: { emoji: "⚠️", label: "Cryptocurrency", threatLevel: "CAUTION" },
    ANONYMOUS_FILE_SHARING: { emoji: "⚠️", label: "Anonymous File Sharing", threatLevel: "CAUTION" },
    WHISTLEBLOWER_PLATFORMS: { emoji: "⚠️", label: "Whistleblower Platforms", threatLevel: "CAUTION" },
    CONSPIRACY_THEORIES: { emoji: "⚠️", label: "Conspiracy Theories", threatLevel: "CAUTION" },
    CONTROVERSIAL_POLITICS: { emoji: "⚠️", label: "Controversial Politics", threatLevel: "CAUTION" },
    PSEUDOSCIENCE: { emoji: "⚠️", label: "Pseudoscience", threatLevel: "CAUTION" },
    UNREGULATED_MEDICINE: { emoji: "⚠️", label: "Unregulated Medicine", threatLevel: "CAUTION" },
    OCCULT_PSYCHIC_SERVICES: { emoji: "⚠️", label: "Occult Psychic Services", threatLevel: "CAUTION" },
    EXTREME_IDEOLOGY: { emoji: "⚠️", label: "Extreme Ideology", threatLevel: "CAUTION" },
    SELF_HARM_COMMUNITIES: { emoji: "⚠️", label: "Self Harm Communities", threatLevel: "CAUTION" },
    EATING_DISORDER_COMMUNITIES: { emoji: "⚠️", label: "Eating Disorder Communities", threatLevel: "CAUTION" },
    INCEL_FORUMS: { emoji: "⚠️", label: "Incel Forums", threatLevel: "CAUTION" },
    CHEATING_AFFAIR_SITES: { emoji: "⚠️", label: "Cheating Affair Sites", threatLevel: "CAUTION" },
    SUGAR_DADDY_SITES: { emoji: "⚠️", label: "Sugar Daddy Sites", threatLevel: "CAUTION" },
    VIGILANTE_SITES: { emoji: "⚠️", label: "Vigilante Sites", threatLevel: "CAUTION" },
    CONTROVERSIAL_RELIGION: { emoji: "⚠️", label: "Controversial Religion", threatLevel: "CAUTION" },
    PROPAGANDA_SITES: { emoji: "⚠️", label: "Propaganda Sites", threatLevel: "CAUTION" },
    DEEPFAKE_TOOLS: { emoji: "⚠️", label: "Deepfake Tools", threatLevel: "CAUTION" },
    ALCOHOL_SALES: { emoji: "⚠️", label: "Alcohol Sales", threatLevel: "CAUTION" },
    TOBACCO_SALES: { emoji: "⚠️", label: "Tobacco Sales", threatLevel: "CAUTION" },
    FIREARMS_ACCESSORIES: { emoji: "⚠️", label: "Firearms Accessories", threatLevel: "CAUTION" },
    SURVEILLANCE_EQUIPMENT: { emoji: "⚠️", label: "Surveillance Equipment", threatLevel: "CAUTION" },
    SPY_GADGETS: { emoji: "⚠️", label: "Spy Gadgets", threatLevel: "CAUTION" },
    LOCK_PICKING_TOOLS: { emoji: "⚠️", label: "Lock Picking Tools", threatLevel: "CAUTION" },
    REGULATED_CHEMICALS: { emoji: "⚠️", label: "Regulated Chemicals", threatLevel: "CAUTION" },
    FIREWORKS_SALES: { emoji: "⚠️", label: "Fireworks Sales", threatLevel: "CAUTION" },
    AGE_GATED_CONTENT: { emoji: "⚠️", label: "Age Gated Content", threatLevel: "CAUTION" },
    GREY_MARKET_GOODS: { emoji: "⚠️", label: "Grey Market Goods", threatLevel: "CAUTION" },
    CRYPTO_EXCHANGES: { emoji: "⚠️", label: "Crypto Exchanges", threatLevel: "CAUTION" },
    NFT_MARKETPLACES: { emoji: "⚠️", label: "NFT Marketplaces", threatLevel: "CAUTION" },
    BINARY_OPTIONS: { emoji: "⚠️", label: "Binary Options", threatLevel: "CAUTION" },
    FOREX_TRADING: { emoji: "⚠️", label: "Forex Trading", threatLevel: "CAUTION" },
    UNREGULATED_INVESTMENT: { emoji: "⚠️", label: "Unregulated Investment", threatLevel: "CAUTION" },
    MLM_PLATFORMS: { emoji: "⚠️", label: "MLM Platforms", threatLevel: "CAUTION" },
    CRYPTO_MIXING: { emoji: "⚠️", label: "Crypto Mixing", threatLevel: "CAUTION" },
    PRIVACY_COINS: { emoji: "⚠️", label: "Privacy Coins", threatLevel: "CAUTION" },
    PEER_TO_PEER_LENDING: { emoji: "⚠️", label: "Peer To Peer Lending", threatLevel: "CAUTION" },
    UNREGULATED_CROWDFUNDING: { emoji: "⚠️", label: "Unregulated Crowdfunding", threatLevel: "CAUTION" },
    SUSPICIOUS_UNVERIFIED: { emoji: "⚠️", label: "Suspicious Unverified Domain", threatLevel: "CAUTION" },

    // ── SAFE CATEGORIES (ThreatLevel.SAFE) ───────────────────────────────────
    SCHOOLS_UNIVERSITIES: { emoji: "🎓", label: "Schools & Universities", threatLevel: "SAFE" },
    ONLINE_LEARNING: { emoji: "🎓", label: "Online Learning", threatLevel: "SAFE" },
    RESEARCH_JOURNALS: { emoji: "🎓", label: "Research Journals", threatLevel: "SAFE" },
    LIBRARIES: { emoji: "🎓", label: "Libraries", threatLevel: "SAFE" },
    LANGUAGE_LEARNING: { emoji: "🎓", label: "Language Learning", threatLevel: "SAFE" },
    CODING_TUTORIALS: { emoji: "🎓", label: "Coding Tutorials", threatLevel: "SAFE" },
    KIDS_EDUCATION: { emoji: "🎓", label: "Kids Education", threatLevel: "SAFE" },
    EXAM_PREPARATION: { emoji: "🎓", label: "Exam Preparation", threatLevel: "SAFE" },
    SKILL_DEVELOPMENT: { emoji: "🎓", label: "Skill Development", threatLevel: "SAFE" },
    VOCATIONAL_TRAINING: { emoji: "🎓", label: "Vocational Training", threatLevel: "SAFE" },
    EDUCATION: { emoji: "🎓", label: "Education & Academia", threatLevel: "SAFE" },
    SOFTWARE_DOWNLOADS: { emoji: "💻", label: "Software Downloads", threatLevel: "SAFE" },
    TECH_NEWS: { emoji: "💻", label: "Tech News", threatLevel: "SAFE" },
    DEVELOPER_TOOLS: { emoji: "💻", label: "Developer & Code Tools", threatLevel: "SAFE" },
    OPEN_SOURCE: { emoji: "💻", label: "Open Source", threatLevel: "SAFE" },
    CLOUD_SERVICES: { emoji: "☁️", label: "Cloud Services & Infrastructure", threatLevel: "SAFE" },
    CYBERSECURITY: { emoji: "🛡️", label: "Cybersecurity & Safety", threatLevel: "SAFE" },
    AI_ML_PLATFORMS: { emoji: "🤖", label: "AI & ML Platforms", threatLevel: "SAFE" },
    APP_STORES: { emoji: "💻", label: "App Stores", threatLevel: "SAFE" },
    TECH_FORUMS: { emoji: "💻", label: "Tech Forums", threatLevel: "SAFE" },
    HARDWARE_REVIEWS: { emoji: "💻", label: "Hardware Reviews", threatLevel: "SAFE" },
    BANKING: { emoji: "🏦", label: "Banking & Financial Services", threatLevel: "SAFE" },
    STOCK_MARKET: { emoji: "🏦", label: "Stock Market", threatLevel: "SAFE" },
    INSURANCE: { emoji: "🏦", label: "Insurance", threatLevel: "SAFE" },
    MUTUAL_FUNDS: { emoji: "🏦", label: "Mutual Funds", threatLevel: "SAFE" },
    TAX_ACCOUNTING: { emoji: "🏦", label: "Tax & Accounting", threatLevel: "SAFE" },
    CREDIT_SERVICES: { emoji: "🏦", label: "Credit Services", threatLevel: "SAFE" },
    PAYMENT_PLATFORMS: { emoji: "🏦", label: "Payment Platforms", threatLevel: "SAFE" },
    REAL_ESTATE_FINANCE: { emoji: "🏦", label: "Real Estate Finance", threatLevel: "SAFE" },
    PERSONAL_FINANCE: { emoji: "🏦", label: "Personal Finance", threatLevel: "SAFE" },
    GOVERNMENT_FINANCE: { emoji: "🏦", label: "Government Finance", threatLevel: "SAFE" },
    HOSPITALS_CLINICS: { emoji: "🏥", label: "Hospitals & Clinics", threatLevel: "SAFE" },
    MEDICAL_INFORMATION: { emoji: "🏥", label: "Medical Information", threatLevel: "SAFE" },
    MENTAL_HEALTH: { emoji: "🏥", label: "Mental Health", threatLevel: "SAFE" },
    PHARMACY: { emoji: "🏥", label: "Pharmacy", threatLevel: "SAFE" },
    FITNESS_WELLNESS: { emoji: "🏥", label: "Fitness & Wellness", threatLevel: "SAFE" },
    NUTRITION: { emoji: "🏥", label: "Nutrition", threatLevel: "SAFE" },
    TELEMEDICINE: { emoji: "🏥", label: "Telemedicine", threatLevel: "SAFE" },
    ALTERNATIVE_MEDICINE: { emoji: "🏥", label: "Alternative Medicine", threatLevel: "SAFE" },
    CHILD_HEALTH: { emoji: "🏥", label: "Child Health", threatLevel: "SAFE" },
    SENIOR_CARE: { emoji: "🏥", label: "Senior Care", threatLevel: "SAFE" },
    HEALTHCARE: { emoji: "🏥", label: "Healthcare & Medicine", threatLevel: "SAFE" },
    MOVIE_STREAMING: { emoji: "🎬", label: "Movie Streaming", threatLevel: "SAFE" },
    MUSIC_STREAMING: { emoji: "🎵", label: "Music & Audio", threatLevel: "SAFE" },
    GAMING: { emoji: "🎮", label: "Gaming Platform", threatLevel: "SAFE" },
    PODCASTS: { emoji: "🎬", label: "Podcasts", threatLevel: "SAFE" },
    COMICS_MANGA: { emoji: "🎬", label: "Comics & Manga", threatLevel: "SAFE" },
    ANIME: { emoji: "🎬", label: "Anime", threatLevel: "SAFE" },
    HUMOR_MEMES: { emoji: "🎬", label: "Humor & Memes", threatLevel: "SAFE" },
    CELEBRITY_NEWS: { emoji: "🎬", label: "Celebrity News", threatLevel: "SAFE" },
    TV_WEB_SERIES: { emoji: "🎬", label: "TV & Web Series", threatLevel: "SAFE" },
    LIVE_EVENTS: { emoji: "🎬", label: "Live Events", threatLevel: "SAFE" },
    SOCIAL_MEDIA: { emoji: "👥", label: "Social Media", threatLevel: "SAFE" },
    FORUMS_COMMUNITIES: { emoji: "💬", label: "Forums & Communities", threatLevel: "SAFE" },
    DATING_LEGIT: { emoji: "💬", label: "Dating", threatLevel: "SAFE" },
    MESSAGING: { emoji: "💬", label: "Messaging & Communication", threatLevel: "SAFE" },
    PROFESSIONAL_NETWORKING: { emoji: "💬", label: "Professional Networking", threatLevel: "SAFE" },
    BLOGGING: { emoji: "💬", label: "Blogging", threatLevel: "SAFE" },
    REVIEW_SITES: { emoji: "💬", label: "Review Sites", threatLevel: "SAFE" },
    FAN_COMMUNITIES: { emoji: "💬", label: "Fan Communities", threatLevel: "SAFE" },
    ALUMNI_NETWORKS: { emoji: "💬", label: "Alumni Networks", threatLevel: "SAFE" },
    VOLUNTEER_PLATFORMS: { emoji: "💬", label: "Volunteer Platforms", threatLevel: "SAFE" },
    NATIONAL_NEWS: { emoji: "📰", label: "National News", threatLevel: "SAFE" },
    INTERNATIONAL_NEWS: { emoji: "📰", label: "International News", threatLevel: "SAFE" },
    SPORTS_NEWS: { emoji: "📰", label: "Sports News", threatLevel: "SAFE" },
    SCIENCE_NEWS: { emoji: "📰", label: "Science News", threatLevel: "SAFE" },
    POLITICAL_NEWS: { emoji: "📰", label: "Political News", threatLevel: "SAFE" },
    WEATHER: { emoji: "📰", label: "Weather", threatLevel: "SAFE" },
    FACT_CHECKING: { emoji: "📰", label: "Fact Checking", threatLevel: "SAFE" },
    INVESTIGATIVE_JOURNALISM: { emoji: "📰", label: "Investigative Journalism", threatLevel: "SAFE" },
    LOCAL_NEWS: { emoji: "📰", label: "Local News", threatLevel: "SAFE" },
    SATIRE_LEGIT: { emoji: "📰", label: "Satire", threatLevel: "SAFE" },
    NEWS_MEDIA: { emoji: "📰", label: "News & Journalism", threatLevel: "SAFE" },
    ONLINE_RETAIL: { emoji: "🛒", label: "Online Retail", threatLevel: "SAFE" },
    GROCERY: { emoji: "🛒", label: "Grocery", threatLevel: "SAFE" },
    FASHION: { emoji: "🛒", label: "Fashion", threatLevel: "SAFE" },
    ELECTRONICS: { emoji: "🛒", label: "Electronics", threatLevel: "SAFE" },
    BOOKS: { emoji: "🛒", label: "Books", threatLevel: "SAFE" },
    HOME_FURNITURE: { emoji: "🛒", label: "Home & Furniture", threatLevel: "SAFE" },
    AUTOMOTIVE: { emoji: "🛒", label: "Automotive", threatLevel: "SAFE" },
    AUCTION_SITES: { emoji: "🛒", label: "Auction Sites", threatLevel: "SAFE" },
    B2B_WHOLESALE: { emoji: "🛒", label: "B2B Wholesale", threatLevel: "SAFE" },
    CLASSIFIED_ADS: { emoji: "🛒", label: "Classified Ads", threatLevel: "SAFE" },
    ECOMMERCE: { emoji: "🛒", label: "E-Commerce & Retail", threatLevel: "SAFE" },
    FLIGHT_BOOKING: { emoji: "✈️", label: "Flight Booking", threatLevel: "SAFE" },
    HOTEL_BOOKING: { emoji: "✈️", label: "Hotel Booking", threatLevel: "SAFE" },
    FOOD_DELIVERY: { emoji: "✈️", label: "Food Delivery", threatLevel: "SAFE" },
    RESTAURANT_REVIEWS: { emoji: "✈️", label: "Restaurant Reviews", threatLevel: "SAFE" },
    EVENT_TICKETING: { emoji: "✈️", label: "Event Ticketing", threatLevel: "SAFE" },
    WEDDING_PLANNING: { emoji: "✈️", label: "Wedding Planning", threatLevel: "SAFE" },
    FITNESS_APPS: { emoji: "✈️", label: "Fitness Apps", threatLevel: "SAFE" },
    GARDENING: { emoji: "✈️", label: "Gardening", threatLevel: "SAFE" },
    PET_CARE: { emoji: "✈️", label: "Pet Care", threatLevel: "SAFE" },
    INTERIOR_DESIGN: { emoji: "✈️", label: "Interior Design", threatLevel: "SAFE" },
    GOVERNMENT_PORTALS: { emoji: "🏛️", label: "Government Portals", threatLevel: "SAFE" },
    PUBLIC_SERVICES: { emoji: "🏛️", label: "Public Services", threatLevel: "SAFE" },
    NGOS_CHARITIES: { emoji: "🏛️", label: "NGOs & Charities", threatLevel: "SAFE" },
    LEGAL_SERVICES: { emoji: "🏛️", label: "Legal Services", threatLevel: "SAFE" },
    RELIGIOUS_INSTITUTIONS: { emoji: "🏛️", label: "Religious Institutions", threatLevel: "SAFE" },
    EMERGENCY_SERVICES: { emoji: "🏛️", label: "Emergency Services", threatLevel: "SAFE" },
    MILITARY_DEFENSE: { emoji: "🏛️", label: "Military & Defense", threatLevel: "SAFE" },
    POLITICAL_PARTIES: { emoji: "🏛️", label: "Political Parties", threatLevel: "SAFE" },
    GOVERNMENT: { emoji: "🏛️", label: "Government & Civic", threatLevel: "SAFE" },
    SEARCH_ENGINE: { emoji: "🔍", label: "Search Engine", threatLevel: "SAFE" },
    BUSINESS: { emoji: "🏢", label: "Business & Enterprise", threatLevel: "SAFE" },
    GENERAL_SAFE: { emoji: "🌐", label: "General Safe Website", threatLevel: "SAFE" }
  };

  // ═══════════════════════════════════════════════════════════════════════════
  //  KNOWN DOMAIN DATABASE (~250+ Popular Domains)
  // ═══════════════════════════════════════════════════════════════════════════
  static KNOWN_DOMAINS = {
    // AI / Tech
    "openai.com": "AI_ML_PLATFORMS",
    "chatgpt.com": "AI_ML_PLATFORMS",
    "anthropic.com": "AI_ML_PLATFORMS",
    "claude.ai": "AI_ML_PLATFORMS",
    "huggingface.co": "AI_ML_PLATFORMS",
    "midjourney.com": "AI_ML_PLATFORMS",
    "deepmind.google": "AI_ML_PLATFORMS",
    "gemini.google.com": "AI_ML_PLATFORMS",
    // Dev & Cloud
    "github.com": "DEVELOPER_TOOLS",
    "gitlab.com": "DEVELOPER_TOOLS",
    "bitbucket.org": "DEVELOPER_TOOLS",
    "stackoverflow.com": "DEVELOPER_TOOLS",
    "stackexchange.com": "DEVELOPER_TOOLS",
    "npmjs.com": "DEVELOPER_TOOLS",
    "pypi.org": "DEVELOPER_TOOLS",
    "aws.amazon.com": "CLOUD_SERVICES",
    "azure.microsoft.com": "CLOUD_SERVICES",
    "cloud.google.com": "CLOUD_SERVICES",
    "digitalocean.com": "CLOUD_SERVICES",
    "vercel.com": "CLOUD_SERVICES",
    "netlify.com": "CLOUD_SERVICES",
    "cloudflare.com": "CYBERSECURITY",
    "crowdstrike.com": "CYBERSECURITY",
    "virustotal.com": "CYBERSECURITY",
    // Search
    "google.com": "SEARCH_ENGINE",
    "bing.com": "SEARCH_ENGINE",
    "duckduckgo.com": "SEARCH_ENGINE",
    "ecosia.org": "SEARCH_ENGINE",
    "yahoo.com": "SEARCH_ENGINE",
    "wikipedia.org": "EDUCATION",
    // Social
    "facebook.com": "SOCIAL_MEDIA",
    "instagram.com": "SOCIAL_MEDIA",
    "twitter.com": "SOCIAL_MEDIA",
    "x.com": "SOCIAL_MEDIA",
    "linkedin.com": "PROFESSIONAL_NETWORKING",
    "reddit.com": "FORUMS_COMMUNITIES",
    "pinterest.com": "SOCIAL_MEDIA",
    "tiktok.com": "SOCIAL_MEDIA",
    "snapchat.com": "SOCIAL_MEDIA",
    "threads.net": "SOCIAL_MEDIA",
    "whatsapp.com": "MESSAGING",
    "telegram.org": "MESSAGING",
    "discord.com": "MESSAGING",
    "slack.com": "MESSAGING",
    // Entertainment
    "youtube.com": "MOVIE_STREAMING",
    "netflix.com": "MOVIE_STREAMING",
    "primevideo.com": "MOVIE_STREAMING",
    "disneyplus.com": "MOVIE_STREAMING",
    "hotstar.com": "MOVIE_STREAMING",
    "hulu.com": "MOVIE_STREAMING",
    "twitch.tv": "GAMING",
    "spotify.com": "MUSIC_STREAMING",
    "apple.com": "ONLINE_RETAIL",
    "music.apple.com": "MUSIC_STREAMING",
    "soundcloud.com": "MUSIC_STREAMING",
    "steampowered.com": "GAMING",
    "epicgames.com": "GAMING",
    "roblox.com": "GAMING",
    "chess.com": "GAMING",
    // E-Commerce & Banking
    "amazon.com": "ONLINE_RETAIL",
    "amazon.in": "ONLINE_RETAIL",
    "flipkart.com": "ONLINE_RETAIL",
    "walmart.com": "ONLINE_RETAIL",
    "ebay.com": "AUCTION_SITES",
    "aliexpress.com": "ONLINE_RETAIL",
    "meesho.com": "FASHION",
    "myntra.com": "FASHION",
    "swiggy.com": "FOOD_DELIVERY",
    "zomato.com": "FOOD_DELIVERY",
    "paypal.com": "PAYMENT_PLATFORMS",
    "stripe.com": "PAYMENT_PLATFORMS",
    "razorpay.com": "PAYMENT_PLATFORMS",
    "chase.com": "BANKING",
    "bankofamerica.com": "BANKING",
    "wellsfargo.com": "BANKING",
    "sbi.co.in": "BANKING",
    "hdfcbank.com": "BANKING",
    "icicibank.com": "BANKING",
    // News
    "bbc.com": "INTERNATIONAL_NEWS",
    "cnn.com": "INTERNATIONAL_NEWS",
    "nytimes.com": "NEWS_MEDIA",
    "theguardian.com": "INTERNATIONAL_NEWS",
    "reuters.com": "NEWS_MEDIA",
    "bloomberg.com": "STOCK_MARKET",
    // Piracy & Torrenting (1:1 Android PIRACY_DOMAINS)
    "vegamovies.gripe": "MOVIE_PIRACY",
    "vegamovies.nl": "MOVIE_PIRACY",
    "vegamovies.to": "MOVIE_PIRACY",
    "vegamovies.ist": "MOVIE_PIRACY",
    "vegamoviez.com": "MOVIE_PIRACY",
    "tamilrockers.ws": "MOVIE_PIRACY",
    "tamilrockers.wc": "MOVIE_PIRACY",
    "tamilrockers.com": "MOVIE_PIRACY",
    "1337x.to": "TORRENT_SITES",
    "1337x.st": "TORRENT_SITES",
    "1337x.gd": "TORRENT_SITES",
    "rarbg.to": "TORRENT_SITES",
    "rarbg.me": "TORRENT_SITES",
    "yts.mx": "MOVIE_PIRACY",
    "yts.am": "MOVIE_PIRACY",
    "yts.lt": "MOVIE_PIRACY",
    "piratebay.org": "TORRENT_SITES",
    "thepiratebay.org": "TORRENT_SITES",
    "thepiratebay.se": "TORRENT_SITES",
    "kickass.to": "TORRENT_SITES",
    "kickasstorrents.to": "TORRENT_SITES",
    "katcr.to": "TORRENT_SITES",
    "limetorrents.cc": "TORRENT_SITES",
    "limetorrents.info": "TORRENT_SITES",
    "torrentz2.eu": "TORRENT_SITES",
    "torrentz2.me": "TORRENT_SITES",
    "nyaa.si": "ANIME_PIRACY",
    "nyaa.net": "ANIME_PIRACY",
    "fmovies.to": "STREAMING_PIRACY",
    "fmovies.wtf": "STREAMING_PIRACY",
    "fmovies.ps": "STREAMING_PIRACY",
    "123movies.to": "STREAMING_PIRACY",
    "123movies.net": "STREAMING_PIRACY",
    "putlocker.to": "STREAMING_PIRACY",
    "putlockers.fm": "STREAMING_PIRACY",
    "gomovies.to": "STREAMING_PIRACY",
    "gostream.is": "STREAMING_PIRACY",
    "soap2day.to": "STREAMING_PIRACY",
    "soap2day.ac": "STREAMING_PIRACY",
    "hdmovie2.me": "STREAMING_PIRACY",
    "hdmovie2.ws": "STREAMING_PIRACY",
    "movierulz.com": "MOVIE_PIRACY",
    "movierulz.pe": "MOVIE_PIRACY",
    "movierulz.gs": "MOVIE_PIRACY",
    "filmyzilla.com": "MOVIE_PIRACY",
    "filmyzilla.in": "MOVIE_PIRACY",
    "mp4moviez.com": "MOVIE_PIRACY",
    "mp4moviez.in": "MOVIE_PIRACY",
    "bolly4u.org": "MOVIE_PIRACY",
    "bolly4u.cc": "MOVIE_PIRACY",
    "worldfree4u.com": "MOVIE_PIRACY",
    "worldfree4u.lol": "MOVIE_PIRACY",
    "9xmovies.in": "MOVIE_PIRACY",
    "9xmovies.com": "MOVIE_PIRACY",
    "kuttymovies.com": "MOVIE_PIRACY",
    "isaimini.com": "MOVIE_PIRACY",
    "tamilyogi.com": "MOVIE_PIRACY",
    "tamilyogi.cc": "MOVIE_PIRACY",
    "ssrmovies.club": "MOVIE_PIRACY",
    "extramovies.com": "MOVIE_PIRACY",
    "downloadhub.in": "MOVIE_PIRACY",
    "downloadhub.ws": "MOVIE_PIRACY",
    "skymovieshd.com": "MOVIE_PIRACY",
    "skymovieshd.in": "MOVIE_PIRACY",
    "afilmywap.com": "MOVIE_PIRACY",
    "filmywap.com": "MOVIE_PIRACY",
    "pagalmovies.com": "MOVIE_PIRACY",
    "cinevood.com": "MOVIE_PIRACY",
    "katmoviehd.com": "MOVIE_PIRACY",
    "katmoviehd.se": "MOVIE_PIRACY",
    // Adult & Gambling (1:1 Android ADULT_DOMAINS)
    "pornhub.com": "PORNOGRAPHY",
    "xvideos.com": "PORNOGRAPHY",
    "xhamster.com": "PORNOGRAPHY",
    "xnxx.com": "PORNOGRAPHY",
    "redtube.com": "PORNOGRAPHY",
    "youporn.com": "PORNOGRAPHY",
    "tube8.com": "PORNOGRAPHY",
    "spankbang.com": "PORNOGRAPHY",
    "eporner.com": "PORNOGRAPHY",
    "chaturbate.com": "CAM_SITES",
    "bongacams.com": "CAM_SITES",
    "stripchat.com": "CAM_SITES",
    "livejasmin.com": "CAM_SITES",
    "onlyfans.com": "EXPLICIT_ADULT_CONTENT",
    "fansly.com": "EXPLICIT_ADULT_CONTENT",
    "1xbet.com": "ONLINE_CASINOS",
    "bet365.com": "SPORTS_BETTING",
    "stake.com": "CRYPTO_GAMBLING"
  };

  /**
   * Synchronously categorizes a domain/URL based on multi-signal matching.
   */
  static categorize(urlString, pageText = "") {
    let domain = "";
    try {
      domain = new URL(urlString).hostname.toLowerCase();
    } catch (e) {
      domain = urlString.toLowerCase();
    }

    if (domain.startsWith("www.")) {
      domain = domain.substring(4);
    }

    // 1. Direct Known Domain Check
    if (this.KNOWN_DOMAINS[domain]) {
      const catKey = this.KNOWN_DOMAINS[domain];
      const catDef = this.CATEGORIES[catKey] || this.CATEGORIES.GENERAL_SAFE;
      return {
        categoryKey: catKey,
        emoji: catDef.emoji,
        label: catDef.label,
        threatLevel: catDef.threatLevel,
        confidence: 0.99,
        reason: `Matched verified domain database (${domain})`
      };
    }

    // Check parent domain
    for (const [kDomain, catKey] of Object.entries(this.KNOWN_DOMAINS)) {
      if (domain.endsWith("." + kDomain)) {
        const catDef = this.CATEGORIES[catKey] || this.CATEGORIES.GENERAL_SAFE;
        return {
          categoryKey: catKey,
          emoji: catDef.emoji,
          label: catDef.label,
          threatLevel: catDef.threatLevel,
          confidence: 0.95,
          reason: `Verified subdomain of ${kDomain}`
        };
      }
    }

    // 2. TLD Structural Classification
    const parts = domain.split('.');
    const tld = '.' + (parts[parts.length - 1] || '');

    if (tld === ".edu" || tld === ".ac.in" || tld === ".ac.uk") {
      const c = this.CATEGORIES.SCHOOLS_UNIVERSITIES;
      return { categoryKey: "SCHOOLS_UNIVERSITIES", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.9, reason: "Academic Top-Level Domain" };
    }
    if (tld === ".gov" || tld === ".gov.in" || tld === ".mil") {
      const c = this.CATEGORIES.GOVERNMENT_PORTALS;
      return { categoryKey: "GOVERNMENT_PORTALS", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Official Government TLD" };
    }
    if ([".xxx", ".adult", ".porn", ".sex", ".cam"].includes(tld)) {
      const c = this.CATEGORIES.PORNOGRAPHY;
      return { categoryKey: "PORNOGRAPHY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Adult content TLD" };
    }
    if ([".bet", ".casino", ".poker"].includes(tld)) {
      const c = this.CATEGORIES.ONLINE_CASINOS;
      return { categoryKey: "ONLINE_CASINOS", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.95, reason: "Gambling & Casino TLD" };
    }
    if ([".gripe", ".ist", ".wc", ".wtf", ".lol", ".gd", ".ps", ".ac", ".to", ".is"].includes(tld)) {
      const c = this.CATEGORIES.STREAMING_PIRACY;
      return { categoryKey: "STREAMING_PIRACY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "High-risk piracy TLD" };
    }

    // 3. Keyword Signals in URL / Hostname
    const lowerUrl = urlString.toLowerCase();
    const lowerPageText = (pageText || "").toLowerCase();
    
    // Adult Keywords
    const adultKeywords = ["porn", "xxx", "nude", "escort", "nsfw", "desi xxx", "desihd", "desi-hd", "cam girl", "onlyfans", "adult video", "fuck", "milf", "hentai"];
    if (adultKeywords.some(k => domain.includes(k) || lowerUrl.includes('/' + k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.PORNOGRAPHY;
      return { categoryKey: "PORNOGRAPHY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Adult keywords in URL structure or page content" };
    }
    // Gambling Keywords
    const gamblingKeywords = ["casino", "betting", "gamble", "free-spins", "slots", "poker", "jackpot", "1xbet", "stake"];
    if (gamblingKeywords.some(k => domain.includes(k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.ONLINE_CASINOS;
      return { categoryKey: "ONLINE_CASINOS", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Gambling keywords in domain or page content" };
    }
    // Piracy Keywords (1:1 Android PIRACY_DOMAIN_KEYWORDS)
    const piracyKeywords = [
      "torrent", "movie", "moviez", "pirate", "warez", "crack", "nulled", "fmovie",
      "putlocker", "123movie", "gomovie", "soap2day", "movierulz", "filmyzilla",
      "mp4moviez", "hdmovie", "bolly4u", "9xmovie", "kuttymovie", "tamilyogi",
      "isaimini", "downloadhub", "skymovieshd", "filmywap", "pagalmovie", "cinevood",
      "katmovie", "vegamovie", "tamilrocker", "yts", "rarbg", "kickass", "limetorrent"
    ];
    if (piracyKeywords.some(k => domain.includes(k) || lowerPageText.includes(k))) {
      const c = this.CATEGORIES.MOVIE_PIRACY;
      return { categoryKey: "MOVIE_PIRACY", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Piracy indicators in domain or page content" };
    }
    // Phishing / Scams
    const scamKeywords = ["tech-support", "crypto-doubler", "claim-prize", "free-giveaway", "lottery-winner", "account-suspended", "verify-account"];
    if (scamKeywords.some(k => domain.includes(k) || lowerUrl.includes(k))) {
      const c = this.CATEGORIES.PHISHING;
      return { categoryKey: "PHISHING", emoji: c.emoji, label: c.label, threatLevel: c.threatLevel, confidence: 0.85, reason: "Social engineering / phishing patterns detected" };
    }

    // Default Fallback
    const def = this.CATEGORIES.GENERAL_SAFE;
    return {
      categoryKey: "GENERAL_SAFE",
      emoji: def.emoji,
      label: def.label,
      threatLevel: def.threatLevel,
      confidence: 0.6,
      reason: "General web destination (Default heuristic classification)"
    };
  }

  /**
   * Asynchronously categorizes a destination using heuristic signals first,
   * and falls back to LLM7.io Fast Engine for deep zero-shot classification.
   */
  static async categorizeAsync(urlString, metaDescription = "", pageText = "") {
    const localResult = this.categorize(urlString, pageText);

    // If local heuristic confidence is high (known domain or high-confidence signal), return immediately
    if (localResult.confidence >= 0.85) {
      return localResult;
    }

    // Otherwise, invoke LLM7.io for zero-shot taxonomy classification
    try {
      const llm7 = new Llm7Client();
      const classifiedKey = await llm7.classifyWebsite(urlString, metaDescription, pageText);
      if (classifiedKey && this.CATEGORIES[classifiedKey]) {
        const catDef = this.CATEGORIES[classifiedKey];
        return {
          categoryKey: classifiedKey,
          emoji: catDef.emoji,
          label: catDef.label,
          threatLevel: catDef.threatLevel,
          confidence: 0.95,
          reason: `AI classification via LLM7.io Fast Engine (${catDef.label})`
        };
      }
    } catch (e) {
      console.warn("LLM7 async categorization fallback:", e);
    }

    return localResult;
  }
}
