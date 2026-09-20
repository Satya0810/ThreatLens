// HistoryManager.js
// Manages local storage of scan history, simulating the Room DB in the Android app.
import { CloudSync } from './CloudSync.js';

export class HistoryManager {
  
  /**
   * Saves a ScanResult object into local storage and synchronizes with Firebase cloud.
   * @param {Object} scanResult 
   */
  static async saveScan(scanResult) {
    if (!scanResult) return null;

    const record = {
      id: crypto.randomUUID ? crypto.randomUUID() : `scan_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      timestamp: scanResult.timestamp || Date.now(),
      payload: scanResult.rawContent || scanResult.payload || scanResult.expandedUrl || '',
      rawContent: scanResult.rawContent || scanResult.payload || '',
      type: scanResult.scanType || scanResult.type || 'URL',
      scanType: scanResult.scanType || scanResult.type || 'URL',
      overallScore: scanResult.overallScore !== undefined ? scanResult.overallScore : (100 - (scanResult.score || scanResult.riskScore || 0)),
      score: scanResult.riskScore !== undefined ? scanResult.riskScore : (scanResult.score || 0),
      riskScore: scanResult.riskScore !== undefined ? scanResult.riskScore : (scanResult.score || 0),
      safetyStatus: scanResult.safetyStatus || (scanResult.riskScore >= 60 ? 'MALICIOUS' : (scanResult.riskScore >= 30 ? 'CAUTION' : 'SAFE')),
      threatType: scanResult.threatType || 'SAFE',
      siteCategory: scanResult.siteCategory || 'General',
      siteSummary: scanResult.siteSummary || scanResult.message || '',
      threatDetails: scanResult.threatDetails || scanResult.flags || [],
      flags: scanResult.flags || [],
      upiAnalysis: scanResult.upiAnalysis || null,
      wifiAnalysis: scanResult.wifiAnalysis || null
    };

    return new Promise(async (resolve) => {
      chrome.storage.local.get(['threatlens_history'], async (result) => {
        let history = result.threatlens_history || [];
        history.unshift(record); // Add to beginning

        // Keep last 500 scans
        if (history.length > 500) {
          history = history.slice(0, 500);
        }

        chrome.storage.local.set({ 'threatlens_history': history }, async () => {
          // Attempt to sync to cloud
          try {
            await CloudSync.initSession();
            if (CloudSync.idToken) {
              await CloudSync.syncScanToCloud(record);
            }
          } catch (e) {
            console.warn("Cloud sync error:", e);
          }
          
          resolve(record);
        });
      });
    });
  }

  static async getHistory() {
    return new Promise((resolve) => {
      chrome.storage.local.get(['threatlens_history'], (result) => {
        resolve(result.threatlens_history || []);
      });
    });
  }

  static async clearHistory() {
    return new Promise((resolve) => {
      chrome.storage.local.remove(['threatlens_history'], () => {
        resolve();
      });
    });
  }
}
