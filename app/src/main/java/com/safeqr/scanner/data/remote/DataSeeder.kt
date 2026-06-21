package com.safeqr.scanner.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.safeqr.scanner.analysis.HeuristicChecker.HeuristicDefaults
import com.safeqr.scanner.analysis.ThreatAnalyzerDefaults
import com.safeqr.scanner.analysis.WebsiteCategorizer.WebsiteCategorizerDefaults
import com.safeqr.scanner.ui.screens.SandboxDefaults

object DataSeeder {
    private const val TAG = "DataSeeder"

    fun seedDatabaseIfEmpty() {
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("app_config").document("datasets")

            docRef.get().addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.d(TAG, "No datasets found in Firestore. Seeding default data...")
                    val threatData = ThreatAnalyzerData(
                        TRANSACTION_SCHEMES = ThreatAnalyzerDefaults.TRANSACTION_SCHEMES,
                        TRANSACTION_DOMAINS = ThreatAnalyzerDefaults.TRANSACTION_DOMAINS,
                        TRANSACTION_PATH_KEYWORDS = ThreatAnalyzerDefaults.TRANSACTION_PATH_KEYWORDS,
                        ADULT_TLDS = ThreatAnalyzerDefaults.ADULT_TLDS,
                        ADULT_DOMAINS = ThreatAnalyzerDefaults.ADULT_DOMAINS,
                        ADULT_DOMAIN_KEYWORDS = ThreatAnalyzerDefaults.ADULT_DOMAIN_KEYWORDS,
                        ADULT_PATH_KEYWORDS = ThreatAnalyzerDefaults.ADULT_PATH_KEYWORDS,
                        TRUSTED_DOMAINS = ThreatAnalyzerDefaults.TRUSTED_DOMAINS,
                        PIRACY_DOMAINS = ThreatAnalyzerDefaults.PIRACY_DOMAINS,
                        PIRACY_DOMAIN_KEYWORDS = ThreatAnalyzerDefaults.PIRACY_DOMAIN_KEYWORDS,
                        PIRACY_TLDS = ThreatAnalyzerDefaults.PIRACY_TLDS,
                        PIRACY_KEYWORDS = ThreatAnalyzerDefaults.PIRACY_KEYWORDS,
                        BAITING_KEYWORDS = ThreatAnalyzerDefaults.BAITING_KEYWORDS,
                        ADULT_KEYWORDS = ThreatAnalyzerDefaults.ADULT_KEYWORDS,
                        SHOPPING_KEYWORDS = ThreatAnalyzerDefaults.SHOPPING_KEYWORDS,
                        SOCIAL_KEYWORDS = ThreatAnalyzerDefaults.SOCIAL_KEYWORDS,
                        NEWS_KEYWORDS = ThreatAnalyzerDefaults.NEWS_KEYWORDS,
                        EDUCATION_KEYWORDS = ThreatAnalyzerDefaults.EDUCATION_KEYWORDS,
                        ENTERTAINMENT_KEYWORDS = ThreatAnalyzerDefaults.ENTERTAINMENT_KEYWORDS
                    )

                    val categorizerData = WebsiteCategorizerData(
                        KNOWN_DOMAINS = WebsiteCategorizerDefaults.KNOWN_DOMAINS.mapValues { it.value.name },
                        TLD_CATEGORY_MAP = WebsiteCategorizerDefaults.TLD_CATEGORY_MAP.mapValues { it.value.name },
                        OG_TYPE_MAP = WebsiteCategorizerDefaults.OG_TYPE_MAP.mapValues { it.value.name },
                        KEYWORD_SIGNALS = com.safeqr.scanner.analysis.WebsiteCategorizer.KEYWORD_SIGNALS.map { signal ->
                            KeywordSignalData(signal.category.name, signal.keywords, signal.threshold)
                        }
                    )

                    val heuristicData = HeuristicCheckerData(
                        SUSPICIOUS_TLDS = HeuristicDefaults.SUSPICIOUS_TLDS,
                        SUSPICIOUS_KEYWORDS = HeuristicDefaults.SUSPICIOUS_KEYWORDS
                    )

                    val sandboxData = SandboxBrowserData(
                        blockedDomainKeywords = SandboxDefaults.blockedDomainKeywords
                    )

                    val wrapper = CloudDatasetsWrapper(
                        threatAnalyzerData = threatData,
                        websiteCategorizerData = categorizerData,
                        heuristicCheckerData = heuristicData,
                        sandboxBrowserData = sandboxData
                    )

                    docRef.set(wrapper)
                        .addOnSuccessListener { Log.d(TAG, "Datasets successfully seeded!") }
                        .addOnFailureListener { e -> Log.w(TAG, "Error seeding datasets", e) }
                } else {
                    Log.d(TAG, "Datasets already exist in Firestore.")
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Error checking datasets document", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized or error seeding datasets", e)
        }
    }

    fun forceUpdateDatabase() {
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("app_config").document("datasets")

            Log.d(TAG, "Forcing update of datasets in Firestore...")
            val threatData = ThreatAnalyzerData(
                TRANSACTION_SCHEMES = ThreatAnalyzerDefaults.TRANSACTION_SCHEMES,
                TRANSACTION_DOMAINS = ThreatAnalyzerDefaults.TRANSACTION_DOMAINS,
                TRANSACTION_PATH_KEYWORDS = ThreatAnalyzerDefaults.TRANSACTION_PATH_KEYWORDS,
                ADULT_TLDS = ThreatAnalyzerDefaults.ADULT_TLDS,
                ADULT_DOMAINS = ThreatAnalyzerDefaults.ADULT_DOMAINS,
                ADULT_DOMAIN_KEYWORDS = ThreatAnalyzerDefaults.ADULT_DOMAIN_KEYWORDS,
                ADULT_PATH_KEYWORDS = ThreatAnalyzerDefaults.ADULT_PATH_KEYWORDS,
                TRUSTED_DOMAINS = ThreatAnalyzerDefaults.TRUSTED_DOMAINS,
                PIRACY_DOMAINS = ThreatAnalyzerDefaults.PIRACY_DOMAINS,
                PIRACY_DOMAIN_KEYWORDS = ThreatAnalyzerDefaults.PIRACY_DOMAIN_KEYWORDS,
                PIRACY_TLDS = ThreatAnalyzerDefaults.PIRACY_TLDS,
                PIRACY_KEYWORDS = ThreatAnalyzerDefaults.PIRACY_KEYWORDS,
                BAITING_KEYWORDS = ThreatAnalyzerDefaults.BAITING_KEYWORDS,
                ADULT_KEYWORDS = ThreatAnalyzerDefaults.ADULT_KEYWORDS,
                SHOPPING_KEYWORDS = ThreatAnalyzerDefaults.SHOPPING_KEYWORDS,
                SOCIAL_KEYWORDS = ThreatAnalyzerDefaults.SOCIAL_KEYWORDS,
                NEWS_KEYWORDS = ThreatAnalyzerDefaults.NEWS_KEYWORDS,
                EDUCATION_KEYWORDS = ThreatAnalyzerDefaults.EDUCATION_KEYWORDS,
                ENTERTAINMENT_KEYWORDS = ThreatAnalyzerDefaults.ENTERTAINMENT_KEYWORDS
            )

            val categorizerData = WebsiteCategorizerData(
                KNOWN_DOMAINS = WebsiteCategorizerDefaults.KNOWN_DOMAINS.mapValues { it.value.name },
                TLD_CATEGORY_MAP = WebsiteCategorizerDefaults.TLD_CATEGORY_MAP.mapValues { it.value.name },
                OG_TYPE_MAP = WebsiteCategorizerDefaults.OG_TYPE_MAP.mapValues { it.value.name },
                KEYWORD_SIGNALS = com.safeqr.scanner.analysis.WebsiteCategorizer.KEYWORD_SIGNALS.map { signal ->
                    KeywordSignalData(signal.category.name, signal.keywords, signal.threshold)
                }
            )

            val heuristicData = HeuristicCheckerData(
                SUSPICIOUS_TLDS = HeuristicDefaults.SUSPICIOUS_TLDS,
                SUSPICIOUS_KEYWORDS = HeuristicDefaults.SUSPICIOUS_KEYWORDS
            )

            val sandboxData = SandboxBrowserData(
                blockedDomainKeywords = SandboxDefaults.blockedDomainKeywords
            )

            val wrapper = CloudDatasetsWrapper(
                threatAnalyzerData = threatData,
                websiteCategorizerData = categorizerData,
                heuristicCheckerData = heuristicData,
                sandboxBrowserData = sandboxData
            )

            docRef.set(wrapper)
                .addOnSuccessListener { Log.d(TAG, "Datasets successfully force-updated!") }
                .addOnFailureListener { e -> Log.w(TAG, "Error force updating datasets", e) }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized or error force updating datasets", e)
        }
    }
}
