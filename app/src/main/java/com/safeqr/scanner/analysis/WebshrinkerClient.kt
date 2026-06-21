package com.safeqr.scanner.analysis

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.safeqr.scanner.analysis.WebsiteCategorizer.SiteCategory

object WebshrinkerClient {
    private const val TAG = "WebshrinkerClient"
    private const val BASE_URL = "https://api.webshrinker.com/categories/v3/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getCategories(url: String, apiKey: String): List<SiteCategory> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            val base64Url = Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            val authHeader = "Basic " + Base64.encodeToString(apiKey.toByteArray(), Base64.NO_WRAP)

            val request = Request.Builder()
                .url("$BASE_URL$base64Url")
                .addHeader("Authorization", authHeader)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Webshrinker API failed: ${response.code} ${response.message}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            val dataArray = json.optJSONArray("data") ?: return@withContext emptyList()
            if (dataArray.length() == 0) return@withContext emptyList()

            val categoriesArray = dataArray.getJSONObject(0).optJSONArray("categories") ?: return@withContext emptyList()

            val siteCategories = mutableListOf<SiteCategory>()
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(i)
                val catId = catObj.optString("id", "").lowercase()
                
                val mappedCategory = mapWebshrinkerCategory(catId)
                if (mappedCategory != null) {
                    siteCategories.add(mappedCategory)
                }
            }

            return@withContext siteCategories
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Webshrinker", e)
            return@withContext emptyList()
        }
    }

    private fun mapWebshrinkerCategory(catId: String): SiteCategory? {
        return when {
            // Threats & Malicious
            catId.contains("malware") || catId.contains("phishing") || catId.contains("scam") -> SiteCategory.MALWARE
            catId.contains("spam") -> SiteCategory.PHISHING
            
            // Adult & Explicit
            catId.contains("adult") || catId.contains("pornography") || catId.contains("mature") -> SiteCategory.PORNOGRAPHY
            catId.contains("nudity") -> SiteCategory.NUDITY
            catId.contains("escort") || catId.contains("dating") && catId.contains("adult") -> SiteCategory.ADULT_DATING
            
            // Gambling
            catId.contains("gambling") || catId.contains("casino") -> SiteCategory.ONLINE_CASINOS
            catId.contains("betting") || catId.contains("wagering") -> SiteCategory.SPORTS_BETTING
            
            // Illegal / Piracy
            catId.contains("illegal") || catId.contains("piracy") || catId.contains("copyright") -> SiteCategory.MOVIE_PIRACY
            catId.contains("drugs") && catId.contains("illegal") -> SiteCategory.ILLEGAL_DRUG_SALES
            catId.contains("weapons") -> SiteCategory.ILLEGAL_WEAPONS
            catId.contains("hate") || catId.contains("extremis") -> SiteCategory.EXTREMIST_CONTENT
            
            // Shopping & E-Commerce
            catId.contains("shopping") || catId.contains("ecommerce") || catId.contains("e-commerce") || catId.contains("retail") -> SiteCategory.ONLINE_RETAIL
            catId.contains("fashion") || catId.contains("apparel") || catId.contains("clothing") -> SiteCategory.FASHION
            catId.contains("auction") -> SiteCategory.AUCTION_SITES
            
            // Social & Communication
            catId.contains("social") || catId.contains("networking") -> SiteCategory.SOCIAL_MEDIA
            catId.contains("dating") -> SiteCategory.DATING_LEGIT
            catId.contains("forum") || catId.contains("community") || catId.contains("message board") -> SiteCategory.FORUMS_COMMUNITIES
            catId.contains("blog") -> SiteCategory.BLOGGING
            catId.contains("messaging") || catId.contains("chat") || catId.contains("email") -> SiteCategory.MESSAGING
            
            // News & Media
            catId.contains("news") || catId.contains("journalism") -> SiteCategory.NATIONAL_NEWS
            catId.contains("weather") -> SiteCategory.WEATHER
            catId.contains("sports") -> SiteCategory.SPORTS_NEWS
            
            // Finance & Business
            catId.contains("finance") || catId.contains("banking") || catId.contains("financial") -> SiteCategory.BANKING
            catId.contains("insurance") -> SiteCategory.INSURANCE
            catId.contains("invest") || catId.contains("stock") || catId.contains("trading") -> SiteCategory.STOCK_MARKET
            catId.contains("crypto") || catId.contains("blockchain") -> SiteCategory.CRYPTOCURRENCY
            catId.contains("real estate") || catId.contains("property") || catId.contains("realty") -> SiteCategory.REAL_ESTATE_FINANCE
            catId.contains("payment") -> SiteCategory.PAYMENT_PLATFORMS
            
            // Technology
            catId.contains("technology") || catId.contains("software") || catId.contains("computing") -> SiteCategory.TECH_NEWS
            catId.contains("developer") || catId.contains("programming") -> SiteCategory.DEVELOPER_TOOLS
            catId.contains("ai") || catId.contains("artificial intelligence") || catId.contains("machine learning") -> SiteCategory.AI_ML_PLATFORMS
            catId.contains("cloud") || catId.contains("hosting") -> SiteCategory.CLOUD_SERVICES
            catId.contains("security") || catId.contains("antivirus") -> SiteCategory.CYBERSECURITY
            
            // Education
            catId.contains("education") || catId.contains("academic") || catId.contains("learning") -> SiteCategory.SCHOOLS_UNIVERSITIES
            catId.contains("reference") || catId.contains("encyclopedia") || catId.contains("library") -> SiteCategory.LIBRARIES
            
            // Health
            catId.contains("health") || catId.contains("medical") || catId.contains("pharma") -> SiteCategory.MEDICAL_INFORMATION
            catId.contains("fitness") || catId.contains("wellness") -> SiteCategory.FITNESS_WELLNESS
            catId.contains("mental health") || catId.contains("psychology") -> SiteCategory.MENTAL_HEALTH
            
            // Entertainment
            catId.contains("entertainment") || catId.contains("streaming") || catId.contains("movies") || catId.contains("television") -> SiteCategory.MOVIE_STREAMING
            catId.contains("gaming") || catId.contains("games") -> SiteCategory.GAMING
            catId.contains("music") || catId.contains("audio") || catId.contains("radio") -> SiteCategory.MUSIC_STREAMING
            catId.contains("humor") || catId.contains("comedy") -> SiteCategory.HUMOR_MEMES
            catId.contains("anime") || catId.contains("manga") || catId.contains("comics") -> SiteCategory.ANIME
            catId.contains("podcast") -> SiteCategory.PODCASTS
            
            // Travel & Food
            catId.contains("travel") || catId.contains("tourism") || catId.contains("hotel") || catId.contains("airlines") -> SiteCategory.HOTEL_BOOKING
            catId.contains("food") || catId.contains("restaurant") || catId.contains("recipe") || catId.contains("cooking") -> SiteCategory.FOOD_DELIVERY
            
            // Government & Society
            catId.contains("government") || catId.contains("politics") -> SiteCategory.GOVERNMENT_PORTALS
            catId.contains("legal") || catId.contains("law") -> SiteCategory.LEGAL_SERVICES
            catId.contains("religion") || catId.contains("spiritual") -> SiteCategory.RELIGIOUS_INSTITUTIONS
            catId.contains("ngo") || catId.contains("charity") || catId.contains("nonprofit") -> SiteCategory.NGOS_CHARITIES
            catId.contains("military") || catId.contains("defense") -> SiteCategory.MILITARY_DEFENSE
            
            // Automotive & Home
            catId.contains("auto") || catId.contains("vehicle") || catId.contains("car") || catId.contains("motor") -> SiteCategory.AUTOMOTIVE
            catId.contains("home") || catId.contains("garden") || catId.contains("interior") -> SiteCategory.HOME_FURNITURE
            catId.contains("pet") || catId.contains("animal") -> SiteCategory.PET_CARE
            
            // Career
            catId.contains("job") || catId.contains("career") || catId.contains("recruit") || catId.contains("employment") -> SiteCategory.PROFESSIONAL_NETWORKING
            
            // Privacy & VPN
            catId.contains("vpn") || catId.contains("proxy") || catId.contains("anonymizer") -> SiteCategory.VPN_PROXY_SERVICES
            
            // Alcohol & Tobacco
            catId.contains("alcohol") || catId.contains("beer") || catId.contains("wine") || catId.contains("liquor") -> SiteCategory.ALCOHOL_SALES
            catId.contains("tobacco") || catId.contains("smoking") || catId.contains("vaping") -> SiteCategory.TOBACCO_SALES
            
            else -> null
        }
    }
}
