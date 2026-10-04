from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any

class UpiThreatRequest(BaseModel):
    """
    Zero-PII Payload transmitted from ThreatLens Android client to Sovereign Cloud AI.
    All personal identifiers (Aadhaar, PAN, phone numbers, personal names) are redacted client-side.
    """
    sanitized_payee_address: str = Field(..., description="Sanitized VPA e.g. 'refund99@ybl'")
    sanitized_payee_name: str = Field(default="", description="Sanitized payee display name")
    amount: float = Field(default=0.0, ge=0.0, description="Transaction amount in INR")
    note: str = Field(default="", description="Transaction note or payment purpose")
    mcc: str = Field(default="", description="Merchant Category Code if available")
    
    # Real-Time Device Telemetry
    has_active_call: bool = Field(default=False, description="Whether a phone call is active")
    is_screen_shared: bool = Field(default=False, description="Whether screen mirroring/remote desktop is active")
    has_suspicious_accessibility: bool = Field(default=False, description="Whether non-system accessibility services are active")
    
    # Ingress Context (Vector 1)
    latest_ingress_pretext_text: Optional[str] = Field(default=None, description="Redacted SMS/chat pretext message")
    pretext_category: Optional[str] = Field(default=None, description="Classified category from client matcher")
    pretext_delta_minutes: Optional[float] = Field(default=9999.0, description="Minutes since pretext message arrived")

class UpiThreatResponse(BaseModel):
    """
    Calibrated Risk Verdict returned by Cloud AI to Android client.
    """
    risk_score: float = Field(..., ge=0.0, le=100.0, description="Risk score 0-100")
    fraud_probability: float = Field(..., ge=0.0, le=1.0, description="Calibrated posterior probability")
    decision_tier: str = Field(..., description="SILENT_PASS | CONTEXTUAL_MICRO_NUDGE | PRE_PIN_INTERLOCK")
    conformal_threshold: float = Field(default=0.01, description="Calibrated quantile threshold (q_hat)")
    guaranteed_max_false_alarm_rate: float = Field(default=0.002, description="Statistical FPR bound (alpha = 0.2%)")
    detected_workflow_type: str = Field(..., description="Scam workflow classification")
    explainable_reasons: List[str] = Field(default=[], description="Human-understandable forensic reasons")
    nlp_pretext_confidence: float = Field(default=0.0, description="Transformer pretext model confidence")
    mule_network_risk: float = Field(default=0.0, description="R-GCN mule centrality score")
    inference_latency_ms: float = Field(..., description="Model evaluation duration in milliseconds")

class UrlThreatRequest(BaseModel):
    """
    Request payload to classify a URL or domain against the Web Categorizer.
    """
    url: str = Field(..., description="URL or domain to analyze")

class UrlThreatResponse(BaseModel):
    """
    Structured categorization verdict and cyber threat assessment for a URL.
    """
    url: str = Field(..., description="Target URL")
    domain: str = Field(..., description="Extracted domain")
    category: str = Field(..., description="Category code (e.g. BANKING, PHISHING, MALWARE, ONLINE_RETAIL)")
    category_label: str = Field(..., description="Human-readable category description")
    threat_level: str = Field(..., description="SAFE | CAUTION | DANGEROUS")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Confidence score 0.0-1.0")
    is_threat: bool = Field(..., description="True if dangerous/malicious")
    explainable_reasons: List[str] = Field(default=[], description="Forensic explainability reasons")
    evaluation_latency_ms: float = Field(..., description="Lookup latency in milliseconds")

class WebScrapedContentRequest(BaseModel):
    """
    Scraped webpage text attributes returned by web scraper for topical categorization.
    """
    url: str = Field(default="", description="Target URL")
    title: str = Field(default="", description="Page <title> text")
    meta_keywords: str = Field(default="", description="Meta keywords content")
    meta_description: str = Field(default="", description="Meta description content")
    h1_h2_text: str = Field(default="", description="H1 and H2 heading texts")
    body_text: str = Field(default="", description="Page body content extracted by scraper")

class WebScrapedContentResponse(BaseModel):
    """
    Categorization response based on NLP matching against imported keyword corpus.
    """
    url: str = Field(default="", description="Target URL")
    category: str = Field(..., description="Top classified category code")
    category_label: str = Field(..., description="Human readable category label")
    threat_level: str = Field(..., description="SAFE | CAUTION | DANGEROUS")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Classification confidence")
    is_threat: bool = Field(..., description="True if dangerous")
    matching_keywords: List[Dict[str, Any]] = Field(default=[], description="Keywords matched from scraper text")
    top_categories_scores: Dict[str, float] = Field(default={}, description="Top candidate category scores")
    explainable_reasons: List[str] = Field(default=[], description="Explainability reasons")
    evaluation_latency_ms: float = Field(..., description="Processing latency in milliseconds")

