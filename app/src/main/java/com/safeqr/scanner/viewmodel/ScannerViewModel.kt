package com.safeqr.scanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.analysis.ThreatAnalyzer
import com.safeqr.scanner.data.local.ScanDatabase
import com.safeqr.scanner.data.model.SafetyStatus
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.data.repository.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanStats(
    val total: Int = 0,
    val safe: Int = 0,
    val caution: Int = 0,
    val threats: Int = 0,
    val safePercentage: Float = 1f,
    val adultBlocked: Int = 0,
    val paymentsDetected: Int = 0,
    val deviceSafetyScore: Int = 100,
    val recentThreats: List<String> = emptyList(),
    val weeklyTrend: List<Int> = List(7) { 0 },
    val aiCaught: Int = 0,
    val dbCaught: Int = 0,
    val phishingCount: Int = 0,
    val trackerCount: Int = 0,
    val adultCount: Int = 0
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ScanDatabase.getInstance(application)
    private val dao = db.scanDao()
    private val reportDao = db.reportDao()

    private fun createRepository(): ScanRepository {
        val analyzer = ThreatAnalyzer()
        return ScanRepository(dao, analyzer, reportDao)
    }

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _showResult = MutableStateFlow(false)
    val showResult: StateFlow<Boolean> = _showResult.asStateFlow()

    private val _scanningEnabled = MutableStateFlow(true)
    val scanningEnabled: StateFlow<Boolean> = _scanningEnabled.asStateFlow()

    private val _analyzingUrl = MutableStateFlow("")
    val analyzingUrl: StateFlow<String> = _analyzingUrl.asStateFlow()

    private val _scanStats = MutableStateFlow(ScanStats())
    val scanStats: StateFlow<ScanStats> = _scanStats.asStateFlow()

    init {
        com.safeqr.scanner.analysis.AILearningEngine.init(application)
        viewModelScope.launch {
            try {
                dao.getAllScans().collect { rawScans ->
                    val scans = rawScans.groupBy { it.rawContent }.map { it.value.maxByOrNull { s -> s.timestamp } ?: it.value.first() }
                    val total = scans.size
                    val safe = scans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.SAFE.name }
                    val caution = scans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.CAUTION.name }
                    val threats = scans.count { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS.name }
                    val pct = if (total > 0) safe.toFloat() / total.toFloat() else 1f
                    val adult = scans.count { it.isAdultContent }
                    val payment = scans.count { it.isTransaction }
                    val score = (pct * 100).toInt().coerceIn(0, 100)
                    
                    val maliciousScans = scans.filter { it.safetyStatus == com.safeqr.scanner.data.model.SafetyStatus.MALICIOUS.name }
                    val recentThreats = maliciousScans.sortedByDescending { it.timestamp }.take(3).mapNotNull { it.domain ?: it.rawContent }
                    
                    val now = System.currentTimeMillis()
                    val dayMs = 86400000L
                    val weeklyTrend = (0..6).map { dayIndex ->
                        val start = now - (dayIndex + 1) * dayMs
                        val end = now - dayIndex * dayMs
                        scans.count { it.timestamp in start..end }
                    }.reversed()
                    
                    val gson = com.google.gson.Gson()
                    val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    
                    val aiCaught = maliciousScans.count {
                        val details: List<String> = try { gson.fromJson(it.threatDetails, listType) ?: emptyList() } catch (e: Exception) { emptyList() }
                        details.isNotEmpty()
                    }
                    val dbCaught = maliciousScans.size - aiCaught
                    
                    val phishingCount = maliciousScans.count {
                        val details: List<String> = try { gson.fromJson(it.threatDetails, listType) ?: emptyList() } catch (e: Exception) { emptyList() }
                        details.any { detail -> detail.lowercase().contains("phish") } || it.siteCategory == "Phishing"
                    }
                    val trackerCount = maliciousScans.count {
                        val details: List<String> = try { gson.fromJson(it.threatDetails, listType) ?: emptyList() } catch (e: Exception) { emptyList() }
                        details.any { detail -> detail.lowercase().contains("tracker") } || it.siteCategory == "Tracker"
                    }
                    
                    _scanStats.value = ScanStats(
                        total = total,
                        safe = safe,
                        caution = caution,
                        threats = threats,
                        safePercentage = pct,
                        adultBlocked = adult,
                        paymentsDetected = payment,
                        deviceSafetyScore = score,
                        recentThreats = recentThreats,
                        weeklyTrend = weeklyTrend,
                        aiCaught = aiCaught,
                        dbCaught = dbCaught,
                        phishingCount = phishingCount,
                        trackerCount = trackerCount,
                        adultCount = adult
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private suspend fun postProcessScan(result: ScanResult) {
        _scanResult.value = result
        syncToCloudIfLoggedIn(result)
        _showResult.value = true
        
        // ── AUTONOMOUS AI TRAINING ──
        if (result.isUrl && result.expandedUrl != null) {
            val targetLabel = if (result.safetyStatus == SafetyStatus.MALICIOUS || result.overallScore < 50f) 1.0f else 0.0f
            com.safeqr.scanner.analysis.AILearningEngine.train(getApplication(), result, targetLabel)
        }
    }

    private fun syncToCloudIfLoggedIn(result: ScanResult) {
        viewModelScope.launch {
            val userId = com.safeqr.scanner.data.PreferencesManager.getCurrentUserId(getApplication())
            if (userId != null) {
                com.safeqr.scanner.data.remote.CloudSyncManager.syncScanToCloud(userId, result)
            }
        }
    }

    fun onQrCodeDetected(rawValue: String, onSuccess: () -> Unit = {}) {
        if (_isAnalyzing.value || !_scanningEnabled.value || _showResult.value) return // State-based rule: No scan if analyzing or result is visible
        
        onSuccess()
        _scanningEnabled.value = false
        _isAnalyzing.value = true
        _analyzingUrl.value = rawValue
        
        // BYPASS: If this is an internal ThreatLens generated QR code, bypass the API analysis.
        if (rawValue.contains("tl.app/") || rawValue.startsWith("tl.app/")) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(800) // ⏳ Guarantee animation playback
                _scanResult.value = ScanResult(
                    rawContent = rawValue,
                    isUrl = true,
                    originalUrl = rawValue,
                    expandedUrl = rawValue,
                    safetyStatus = SafetyStatus.SAFE,
                    overallScore = 100f,
                    threatDetails = listOf("Internal ThreatLens QR Code verified.")
                )
                _isAnalyzing.value = false
                _showResult.value = true
            }
            return
        }

        // BYPASS: Dynamic Links Intercept (Kill Switch & Geo-Fencing)
        if (com.safeqr.scanner.data.remote.DynamicLinkManager.isDynamicLink(rawValue)) {
            val shortCode = com.safeqr.scanner.data.remote.DynamicLinkManager.extractShortCode(rawValue)
            if (shortCode != null) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(800) // ⏳ Guarantee animation playback
                    try {
                        val resolvedUrl = com.safeqr.scanner.data.remote.DynamicLinkManager.resolveLink(shortCode, "US")
                        _scanResult.value = ScanResult(
                            rawContent = resolvedUrl,
                            isUrl = true,
                            originalUrl = resolvedUrl,
                            expandedUrl = resolvedUrl,
                            safetyStatus = SafetyStatus.SAFE,
                            overallScore = 100f,
                            threatDetails = listOf("Resolved from Secure Dynamic Link Server")
                        )
                    } catch (e: Exception) {
                        _scanResult.value = ScanResult(
                            rawContent = rawValue,
                            isUrl = true,
                            originalUrl = rawValue,
                            expandedUrl = rawValue,
                            safetyStatus = SafetyStatus.MALICIOUS,
                            overallScore = 0f,
                            threatDetails = listOf(e.message ?: "Access Denied")
                        )
                    }
                    _isAnalyzing.value = false
                    _showResult.value = true
                }
                return
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // ⏳ Guarantee animation playback
            try {
                val repository = createRepository()
                
                // BYPASS: If this URL is already in history, use the cache instantly
                val cached = repository.checkCache(rawValue)
                if (cached != null) {
                    postProcessScan(cached)
                    _isAnalyzing.value = false
                    return@launch
                }

                val wsApiKey = com.safeqr.scanner.data.PreferencesManager.getWebshrinkerApiKey(getApplication())
                val result = repository.analyzeScan(rawValue, wsApiKey)
                postProcessScan(result)
            } catch (e: Exception) {
                _scanResult.value = ScanResult(
                    rawContent = rawValue,
                    isUrl = false,
                    safetyStatus = SafetyStatus.UNKNOWN
                )
                _showResult.value = true
            } finally {
                _isAnalyzing.value = false
                _analyzingUrl.value = ""
            }
        }
    }

    fun analyzeUrl(url: String) {
        if (_isAnalyzing.value) return
        _scanningEnabled.value = false
        _isAnalyzing.value = true
        _analyzingUrl.value = url
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // ⏳ Guarantee animation playback
            try {
                val repository = createRepository()
                
                // BYPASS: If this URL is already in history, use the cache instantly
                val cached = repository.checkCache(url)
                if (cached != null) {
                    postProcessScan(cached)
                    _isAnalyzing.value = false
                    return@launch
                }

                val wsApiKey = com.safeqr.scanner.data.PreferencesManager.getWebshrinkerApiKey(getApplication())
                val result = repository.analyzeScan(url, wsApiKey)
                postProcessScan(result)
            } catch (e: Exception) {
                _scanResult.value = ScanResult(
                    rawContent = url,
                    isUrl = true,
                    safetyStatus = SafetyStatus.UNKNOWN
                )
                _showResult.value = true
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeImage(rawValue: String) {
        if (_isAnalyzing.value) return
        _scanningEnabled.value = false
        _isAnalyzing.value = true
        _analyzingUrl.value = rawValue
        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // ⏳ Guarantee animation playback
            try {
                val repository = createRepository()
                val wsApiKey = com.safeqr.scanner.data.PreferencesManager.getWebshrinkerApiKey(getApplication())
                val result = repository.analyzeScan(rawValue, wsApiKey)
                postProcessScan(result)
            } catch (e: Exception) {
                _scanResult.value = ScanResult(
                    rawContent = rawValue,
                    isUrl = false,
                    safetyStatus = SafetyStatus.UNKNOWN
                )
                _showResult.value = true
            } finally {
                _isAnalyzing.value = false
                _analyzingUrl.value = ""
            }
        }
    }

    fun dismissResult() {
        _showResult.value = false
        _scanResult.value = null
        _scanningEnabled.value = true
    }

    fun resetScanner() {
        _scanResult.value = null
        _isAnalyzing.value = false
        _showResult.value = false
        _scanningEnabled.value = true
    }

    fun reportWebsite(rawContent: String, issue: String) {
        viewModelScope.launch {
            reportDao.insert(com.safeqr.scanner.data.model.ReportEntity(url = rawContent, issue = issue))
            
            // Push to Cloud Sync Database for cross-device reporting
            com.safeqr.scanner.data.remote.CloudSyncManager.reportWebsite(rawContent, issue)
            
            // Re-fetch the scan from history to append the report count and reason
            val existing = dao.findByContent(rawContent)
            if (existing != null) {
                val scanResult = existing.toScanResult()
                val currentReasons = scanResult.communityReportReasons.toMutableList()
                currentReasons.add(issue)
                
                // DYNAMICALLY update the result using the shared algorithm
                val updatedResult = com.safeqr.scanner.analysis.ThreatAnalyzer.applyDynamicCommunityReports(
                    scanResult = scanResult,
                    newCommunityReasons = currentReasons,
                    visitCount = Math.max(1L, scanResult.visitCount.toLong())
                )
                
                val updatedEntity = com.safeqr.scanner.data.local.ScanEntity.fromScanResult(updatedResult).copy(id = existing.id)
                dao.insert(updatedEntity)
                
                // Update StateFlow if it's the currently viewed result to reflect changes IMMEDIATELY in UI
                val currentResult = _scanResult.value
                if (currentResult != null && currentResult.rawContent == rawContent) {
                    _scanResult.value = updatedResult
                }
            }
        }
    }

    fun appreciateWebsite(rawContent: String) {
        viewModelScope.launch {
            val appreciationReason = "👍 Appreciated by a user"
            reportDao.insert(com.safeqr.scanner.data.model.ReportEntity(url = rawContent, issue = appreciationReason))
            
            // Push to Cloud Sync Database for cross-device reporting
            com.safeqr.scanner.data.remote.CloudSyncManager.reportWebsite(rawContent, appreciationReason)
            
            // Re-fetch the scan from history to append the report count and reason
            val existing = dao.findByContent(rawContent)
            if (existing != null) {
                val scanResult = existing.toScanResult()
                val currentReasons = scanResult.communityReportReasons.toMutableList()
                currentReasons.add(appreciationReason)
            
                // DYNAMICALLY update the result using the shared algorithm
                val updatedResult = com.safeqr.scanner.analysis.ThreatAnalyzer.applyDynamicCommunityReports(
                    scanResult = scanResult,
                    newCommunityReasons = currentReasons,
                    visitCount = Math.max(1L, scanResult.visitCount.toLong())
                )
                
                val updatedEntity = com.safeqr.scanner.data.local.ScanEntity.fromScanResult(updatedResult).copy(id = existing.id)
                dao.insert(updatedEntity)
                
                // Update StateFlow if it's the currently viewed result to reflect changes IMMEDIATELY in UI
                val currentResult = _scanResult.value
                if (currentResult != null && currentResult.rawContent == rawContent) {
                    _scanResult.value = updatedResult
                }
            }
        }
    }
}
