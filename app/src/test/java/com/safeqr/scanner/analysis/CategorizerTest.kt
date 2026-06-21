package com.safeqr.scanner.analysis

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.util.Log
import com.safeqr.scanner.data.model.ScanResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CategorizerTest {

    @Test
    fun testTenWebsites() = runBlocking {
        val testUrls = listOf(
            "https://www.apple.com",             // Trusted Domain / Shopping / Tech
            "https://www.theverge.com",          // News
            "https://www.coursera.org",          // Education
            "https://pornhub.com",               // Adult (Caution/Malicious depending on policy)
            "https://www.chase.com",             // Finance
            "https://www.booking.com",           // Travel / Hotels
            "https://www.zillow.com",            // Real Estate
            "https://www.ign.com",               // Entertainment / Games
            "https://www.github.com",            // Open Source / Tech
            "https://1337x.to"                   // Piracy (Caution)
        )

        val analyzer = ThreatAnalyzer()

        println("\n=======================================================")
        println("  THREATLENS CATEGORIZER MULTIMODAL ENGINE TEST")
        println("=======================================================\n")

        for ((index, url) in testUrls.withIndex()) {
            println("Analyzing [${index + 1}/10]: $url")
            try {
                // ThreatAnalyzer runs all the parallel HTTP calls, scraper, AND the Categorizer
                val finalResult = analyzer.analyze(url)
                
                println("  -> Category:      ${finalResult.siteCategory}")
                println("  -> Trust Score:   ${finalResult.overallScore}/100.0")
                println("  -> Safety Status: ${finalResult.safetyStatus}")
                println("  -> Site Summary:  ${finalResult.siteSummary?.replace("\n", " ")?.take(150)}...")
            } catch (e: Exception) {
                println("  -> Error: ${e.message}")
            }
            println("-------------------------------------------------------")
        }
        
        println("\n✅ Test Completed Successfully.")
    }
}
