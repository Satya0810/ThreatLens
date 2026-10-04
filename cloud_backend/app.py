"""
ThreatLens Sovereign Cloud AI Backend
Problem Statement 2: AI-Driven Scam Pattern Recognition (RAKSHAM Hackathon)
FastAPI Server orchestrating:
1. Ingress Multilingual Transformer (IndicBERT-v2 / Pretext Analysis)
2. PyTorch Geometric Relational GCN (Mule Ring Centrality)
3. Split-Conformal Risk Calibrator (Zero Alert Fatigue Engine)
"""

import time
import os
import sys

# Ensure local imports work cleanly
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from schemas import (
    UpiThreatRequest, UpiThreatResponse,
    UrlThreatRequest, UrlThreatResponse,
    WebScrapedContentRequest, WebScrapedContentResponse
)
from models.pretext_transformer import PretextTransformerEngine
from models.mule_rgcn import MuleGraphEngine
from models.conformal_fusion import ConformalFusionEngine
from models.web_categorizer import WebCategorizerEngine

app = FastAPI(
    title="ThreatLens Sovereign AI Gateway",
    description="Zero-PII Cloud AI Inference Service for Real-Time Scam Pattern Recognition & Web Threat Categorization",
    version="2.0.0"
)

# Enable CORS for local Android development and web testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Deep AI Engines
transformer_engine = PretextTransformerEngine()
mule_graph_engine = MuleGraphEngine()
conformal_fusion_engine = ConformalFusionEngine()
web_categorizer_engine = WebCategorizerEngine()

@app.get("/")
def root():
    return {
        "message": "ThreatLens Sovereign Cloud AI Gateway is LIVE",
        "documentation": "/docs",
        "health": "/health",
        "version": "2.0.0",
        "status": "ONLINE"
    }

@app.get("/ping")
def ping():
    return {"pong": True}

@app.get("/health")
def health_check():
    return {
        "status": "HEALTHY",
        "service": "ThreatLens Sovereign Cloud AI",
        "version": "2.0.0",
        "models": {
            "model_1_nlp": "Multilingual Social Engineering Transformer (IndicBERT-v2 / XLM-RoBERTa)",
            "model_2_graph": "PyTorch Geometric Relational Graph Convolutional Network (R-GCN)",
            "model_3_fusion": "Split-Conformal Multimodal Calibrator (alpha=0.002, FPR <= 0.2%)",
            "model_4_web_categorizer": "Online Threat Intelligence & Topical Categorizer (456,908 domains + 1,811 live threat URLs)"
        },
        "target_problem_statement": "PS2: AI-Driven Scam Pattern Recognition (RAKSHAM Hackathon, IIT Delhi & Amazon)"
    }

@app.post("/api/v1/analyze/workflow", response_model=UpiThreatResponse)
def analyze_scam_workflow(request: UpiThreatRequest):
    start_time = time.perf_counter()

    try:
        # 1. Model 1: Ingress Pretext Analysis (NLP)
        nlp_category = "BENIGN"
        nlp_confidence = 0.0
        nlp_reasons = []

        if request.latest_ingress_pretext_text:
            nlp_category, nlp_confidence, nlp_reasons = transformer_engine.analyze_text(
                request.latest_ingress_pretext_text
            )
        elif request.pretext_category and request.pretext_category != "BENIGN":
            nlp_category = request.pretext_category
            nlp_confidence = 0.85
            nlp_reasons = [f"Client matched known high-risk pretext: {nlp_category}"]

        # 2. Model 2: Relational Graph & VPA Mule Detection (GNN)
        mule_risk, mule_reasons = mule_graph_engine.evaluate_vpa(
            request.sanitized_payee_address,
            request.sanitized_payee_name
        )

        # 3. Model 3: Multimodal Conformal Fusion
        risk_score, fraud_prob, decision_tier, detected_workflow, fusion_reasons = (
            conformal_fusion_engine.evaluate_threat(
                amount=request.amount,
                has_active_call=request.has_active_call,
                is_screen_shared=request.is_screen_shared,
                has_accessibility=request.has_suspicious_accessibility,
                nlp_confidence=nlp_confidence,
                nlp_category=nlp_category,
                mule_risk=mule_risk,
                note=request.note,
                mcc=request.mcc
            )
        )

        # Aggregate forensic explainability reasons
        all_reasons = fusion_reasons + mule_reasons + nlp_reasons
        # Remove duplicate strings while preserving order
        deduped_reasons = list(dict.fromkeys(all_reasons))

        latency_ms = round((time.perf_counter() - start_time) * 1000.0, 2)

        return UpiThreatResponse(
            risk_score=risk_score,
            fraud_probability=fraud_prob,
            decision_tier=decision_tier,
            conformal_threshold=conformal_fusion_engine.CONFORMAL_Q_HAT,
            guaranteed_max_false_alarm_rate=0.002,
            detected_workflow_type=detected_workflow,
            explainable_reasons=deduped_reasons,
            nlp_pretext_confidence=nlp_confidence,
            mule_network_risk=mule_risk,
            inference_latency_ms=latency_ms
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Inference failure: {str(e)}")

@app.post("/api/v1/analyze/url", response_model=UrlThreatResponse)
def analyze_url_threat(request: UrlThreatRequest):
    """
    Classifies a website URL or domain using the compiled 456,000+ domain knowledge base,
    active cyber threat feeds (URLhaus, OpenPhish), and heuristic scam indicators.
    """
    try:
        verdict = web_categorizer_engine.classify_url(request.url)
        return UrlThreatResponse(**verdict)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Web categorization failure: {str(e)}")

@app.post("/api/v1/analyze/webpage", response_model=WebScrapedContentResponse)
def analyze_scraped_webpage(request: WebScrapedContentRequest):
    """
    Classifies scraped website content (title, meta keywords, headings, body text)
    by evaluating keyword density and confidence against the imported real-world benchmark taxonomy.
    """
    try:
        verdict = web_categorizer_engine.classify_scraped_content(
            url=request.url,
            title=request.title,
            meta_keywords=request.meta_keywords,
            meta_description=request.meta_description,
            h1_h2_text=request.h1_h2_text,
            body_text=request.body_text
        )
        return WebScrapedContentResponse(**verdict)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Web content categorization failure: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    print(f"[*] Starting ThreatLens Sovereign Cloud AI Backend on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port)

