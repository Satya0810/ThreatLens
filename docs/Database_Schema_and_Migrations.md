# 🗄️ THREATLENS: Database Schema, Storage Architecture & Migrations

This document specifies the complete data persistence layer across the **ThreatLens** ecosystem, covering the encrypted on-device Android Room database (SQLCipher), cloud persistence in Firebase Firestore, and the Chromium Extension storage schema.

---

## 1. Data Architecture Overview

ThreatLens follows an **offline-first, privacy-preserving storage model**:
1. **Local Mobile Layer**: Android Room ORM encrypted with **SQLCipher (256-bit AES-CBC)**. All local scan histories, parental configuration PINs, and whitelists reside exclusively on the device.
2. **Cloud Synchronization Layer**: Optional, user-authenticated synchronization using **Google Cloud Firestore**. Scan records are tied to the authenticated user's UID and synchronized across devices.
3. **Browser Extension Layer**: High-speed, non-blocking local storage via `chrome.storage.local` with strict size boundaries and FIFO/LRU memory capping.

---

## 2. Android Local Relational Database (Room + SQLCipher)

### 2.1 Database Configuration & Encryption
- **Database Name**: `threatlens_secure.db`
- **Current Version**: `2`
- **Encryption Algorithm**: 256-bit AES-CBC via SQLCipher
- **Key Derivation**: The database passphrase is dynamically generated and stored within the **Android KeyStore** under the alias `ThreatLensMasterKey`, preventing offline cold-boot database extraction from rooted devices.

```kotlin
// Database Builder with SQLCipher Support
val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
Room.databaseBuilder(context, ThreatLensDatabase::class.java, "threatlens_secure.db")
    .openHelperFactory(factory)
    .addMigrations(MIGRATION_1_2)
    .build()
```

---

### 2.2 Entity Schemas & Tables

#### 2.2.1 Table: `scan_history`
Stores all scanned QR codes, clicked links, and deep threat intelligence results.

```sql
CREATE TABLE IF NOT EXISTS scan_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    rawContent TEXT NOT NULL,
    isUrl INTEGER NOT NULL,
    originalUrl TEXT NOT NULL,
    expandedUrl TEXT NOT NULL,
    redirectChain TEXT NOT NULL,         -- JSON Array string: ["http://bit.ly/..", "https://dest.com"]
    domain TEXT,
    safetyStatus TEXT NOT NULL,          -- 'SAFE', 'CAUTION', 'MALICIOUS'
    overallScore INTEGER NOT NULL,       -- Trust Score: 0 to 100
    riskScore INTEGER NOT NULL,          -- Risk Score: 0 to 100
    threatType TEXT NOT NULL,            -- 'PHISHING', 'MALWARE', 'SCAM', 'SAFE', etc.
    flags TEXT NOT NULL,                 -- JSON Array string: ["Punycode lookalike", "Direct IP host"]
    siteCategory TEXT NOT NULL,          -- e.g., 'E-Commerce', 'Banking', 'Adult', 'Malware'
    siteSummary TEXT NOT NULL,
    aiInsight TEXT,                      -- Plain-English LLM7.io explanation
    timestamp INTEGER NOT NULL,          -- Epoch milliseconds
    isFavorite INTEGER NOT NULL DEFAULT 0,
    syncedToCloud INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS index_scan_history_timestamp ON scan_history(timestamp DESC);
CREATE INDEX IF NOT EXISTS index_scan_history_domain ON scan_history(domain);
CREATE INDEX IF NOT EXISTS index_scan_history_safetyStatus ON scan_history(safetyStatus);
```

#### 2.2.2 Table: `community_reports`
Stores reports submitted by the user regarding false positives or newly detected malicious domains.

```sql
CREATE TABLE IF NOT EXISTS community_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    url TEXT NOT NULL,
    domain TEXT NOT NULL,
    issueType TEXT NOT NULL,             -- 'FALSE_POSITIVE', 'MISSED_THREAT', 'SCAM', 'PHISHING'
    userNotes TEXT,
    submittedAt INTEGER NOT NULL,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING' -- 'PENDING', 'SYNCED', 'FAILED'
);

CREATE INDEX IF NOT EXISTS index_community_reports_submittedAt ON community_reports(submittedAt DESC);
```

#### 2.2.3 Table: `parental_configs`
Stores family safety settings, child-lock configurations, and bedtime schedules.

```sql
CREATE TABLE IF NOT EXISTS parental_configs (
    id INTEGER PRIMARY KEY NOT NULL,
    pinHash TEXT NOT NULL,               -- SHA-256 hash of 4-digit parental PIN
    pinSalt TEXT NOT NULL,               -- 16-byte random cryptographic salt
    isChildLockActive INTEGER NOT NULL DEFAULT 0,
    bedtimeEnabled INTEGER NOT NULL DEFAULT 0,
    bedtimeStartHour INTEGER NOT NULL DEFAULT 21, -- 9:00 PM
    bedtimeEndHour INTEGER NOT NULL DEFAULT 7,    -- 7:00 AM
    blockAdult INTEGER NOT NULL DEFAULT 1,
    blockGambling INTEGER NOT NULL DEFAULT 1,
    blockSocial INTEGER NOT NULL DEFAULT 0,
    blockGaming INTEGER NOT NULL DEFAULT 0,
    whitelistDomains TEXT NOT NULL DEFAULT '[]', -- JSON Array string
    blacklistDomains TEXT NOT NULL DEFAULT '[]'  -- JSON Array string
);
```

#### 2.2.4 Table: `whitelist_domains`
Stores domains manually whitelisted by the user to bypass heuristic and sandbox blocks.

```sql
CREATE TABLE IF NOT EXISTS whitelist_domains (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    domain TEXT NOT NULL UNIQUE,
    addedAt INTEGER NOT NULL,
    reason TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS index_whitelist_domains_domain ON whitelist_domains(domain);
```

---

### 2.3 Data Access Objects (DAOs)

#### `ScanDao`
```kotlin
@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanById(id: Long): ScanResultEntity?

    @Query("SELECT * FROM scan_history WHERE domain = :domain LIMIT 1")
    suspend fun getScanByDomain(domain: String): ScanResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanResultEntity): Long

    @Query("UPDATE scan_history SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearAllHistory()
}
```

---

### 2.4 Database Migrations

#### Migration: Version 1 $\rightarrow$ Version 2
Adds `aiInsight`, `redirectChain`, and `syncedToCloud` columns to support deep LLM7.io explanations and cross-device sync.

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE scan_history ADD COLUMN aiInsight TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE scan_history ADD COLUMN redirectChain TEXT NOT NULL DEFAULT '[]'")
        database.execSQL("ALTER TABLE scan_history ADD COLUMN syncedToCloud INTEGER NOT NULL DEFAULT 0")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_scan_history_timestamp ON scan_history(timestamp DESC)")
    }
}
```

---

## 3. Cloud Persistence (Firebase Cloud Firestore)

### 3.1 Document Hierarchy & Collections

```
/users/{userId}
    ├── profile: { displayName, email, createdAt, lastSyncAt }
    └── /scans/{scanId}
            ├── rawContent: String
            ├── expandedUrl: String
            ├── domain: String
            ├── safetyStatus: String ("SAFE" | "CAUTION" | "MALICIOUS")
            ├── overallScore: Number (0..100)
            ├── threatType: String
            ├── flags: Array<String>
            ├── aiInsight: String
            └── timestamp: Timestamp

/threat_reports/{reportId}
    ├── url: String
    ├── domain: String
    ├── issueType: String
    ├── reportedByUid: String
    ├── timestamp: Timestamp
    └── verificationStatus: String ("PENDING" | "CONFIRMED" | "REJECTED")

/dynamic_datasets/latest
    ├── blockedDomainKeywords: Array<String>
    ├── adultKeywords: Array<String>
    ├── scamKeywords: Array<String>
    ├── version: Number
    └── updatedAt: Timestamp
```

### 3.2 Firestore Security Rules (`firestore.rules`)
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User-isolated scan history
    match /users/{userId}/scans/{scanId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Community threat reporting: Any authenticated user can submit; only admins can modify status
    match /threat_reports/{reportId} {
      allow create: if request.auth != null && request.resource.data.reportedByUid == request.auth.uid;
      allow read: if request.auth != null;
      allow update, delete: if request.auth != null && request.auth.token.admin == true;
    }

    // Public read-only dynamic datasets
    match /dynamic_datasets/{datasetId} {
      allow read: if true;
      allow write: if false; // Updated only via backend admin SDK
    }
  }
}
```

---

## 4. Chromium Extension Local Storage (`chrome.storage.local`)

The browser extension uses `chrome.storage.local` with strict JSON schema structures:

| Storage Key | Type | Description | Max Size / Constraints |
| :--- | :--- | :--- | :--- |
| `adShieldEnabled` | `boolean` | Master toggle for Declarative Net Request rulesets. | Default: `true` |
| `whitelistedDomains` | `Array<string>` | User-whitelisted domains bypassing AdShield. | Max 1,000 domains |
| `adsBlockedCount` | `number` | Lifetime count of blocked ad and tracker requests. | Integer |
| `customZappedSelectors` | `Record<string, Array<string>>` | Custom CSS selectors per hostname hidden by Element Zapper. | Max 100 selectors per domain |
| `strictnessThreshold` | `number` | Heuristic risk threshold (default: 60). | Number (0..100) |
| `cloudToken` | `string` | Firebase Auth JWT token for cross-device sync. | Nullable string |
| `cloudUserId` | `string` | Authenticated user ID. | Nullable string |

### 4.1 Selector Sanitization & Quota Guard
To prevent stylesheet injection or storage quota exhaustion, custom selectors are validated before being saved:
```javascript
function sanitizeCssSelector(selector) {
  // Reject selectors with braces, quotes, semicolons, or HTML tags
  const validPattern = /^[a-zA-Z0-9_\-\.\#\:\s\[\]\=\*\>\+\~]+$/;
  if (!validPattern.test(selector)) return null;
  return selector.trim();
}
```

---

## 5. Data Privacy & Compliance Matrix

| Regulation | Compliance Measure in ThreatLens |
| :--- | :--- |
| **GDPR (EU) / DPDPA (India)** | **Zero Data Telemetry**: No browsing history or un-scanned URLs are collected. Local database encrypted with SQLCipher 256-bit AES. User can wipe all data with one tap via Settings. |
| **COPPA (Child Online Privacy)** | **Local Parental Lock**: Child protection PINs and bedtime schedules are hashed and stored locally. No child activity logs are uploaded to cloud servers. |
| **Data Portability** | **Export Functionality**: Users can export their scan history to standard JSON and CSV formats directly from `HistoryScreen.kt`. |
