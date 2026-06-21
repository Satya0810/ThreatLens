package com.safeqr.scanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeqr.scanner.analysis.ThreatAnalyzer
import com.safeqr.scanner.data.local.ScanDatabase
import com.safeqr.scanner.data.model.ScanResult
import com.safeqr.scanner.data.repository.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository

    init {
        val dao = ScanDatabase.getInstance(application).scanDao()
        val analyzer = ThreatAnalyzer()
        repository = ScanRepository(dao, analyzer)
    }

    val scanHistory: StateFlow<List<ScanResult>> = repository.getScanHistory()
        .map { allScans ->
            allScans.groupBy { it.rawContent }
                .map { (rawContent, scans) ->
                    val mostRecent = scans.maxByOrNull { it.timestamp } ?: scans.first()
                    val visitHistory = scans.map { it.timestamp }.sortedDescending()
                    mostRecent.copy(
                        visitCount = scans.size,
                        visitHistory = visitHistory
                    )
                }
                .sortedByDescending { it.timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedScan = MutableStateFlow<ScanResult?>(null)
    val selectedScan: StateFlow<ScanResult?> = _selectedScan.asStateFlow()

    fun selectScan(result: ScanResult) {
        _selectedScan.value = result
    }

    fun dismissSelected() {
        _selectedScan.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteScan(result: ScanResult) {
        viewModelScope.launch {
            repository.deleteScan(result.rawContent)
        }
    }
}
