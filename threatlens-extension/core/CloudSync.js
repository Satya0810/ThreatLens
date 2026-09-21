// CloudSync.js
// Handles communication with the ThreatLens Firebase Backend using REST APIs
// Full parity with Android App CloudSyncManager

export class CloudSync {
  
  static CLOUD_FUNCTION_URL = "https://us-central1-threatlens-4065e.cloudfunctions.net";
  
  // Firebase Configuration (Loaded dynamically from storage or config.local.js)
  static FIREBASE_API_KEY = "";
  static FIREBASE_PROJECT_ID = "threatlens-4065e";
  static GOOGLE_CLIENT_ID = "171716467103-b09ukthoklag4na2p1sk30lduo41c098.apps.googleusercontent.com";

  // State
  static idToken = null;
  static refreshToken = null;
  static userId = null;
  static userEmail = null;
  static displayName = null;
  static photoUrl = null;
  static tokenExpiry = null;

  /**
   * Helper to sanitize User IDs identically to Android app
   */
  static sanitizeUserId(id) {
    if (!id) return "";
    return id.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
  }

  /**
   * Initialize session from local storage and auto-refresh token if needed
   */
  static async initSession() {
    // Attempt to load local config file if present
    if (!this.FIREBASE_API_KEY) {
      try {
        const mod = await import('./config.local.js');
        if (mod.ThreatLensConfig?.FIREBASE_API_KEY) {
          this.FIREBASE_API_KEY = mod.ThreatLensConfig.FIREBASE_API_KEY;
        }
        if (mod.ThreatLensConfig?.FIREBASE_PROJECT_ID) {
          this.FIREBASE_PROJECT_ID = mod.ThreatLensConfig.FIREBASE_PROJECT_ID;
        }
        if (mod.ThreatLensConfig?.GOOGLE_CLIENT_ID) {
          this.GOOGLE_CLIENT_ID = mod.ThreatLensConfig.GOOGLE_CLIENT_ID;
        }
        if (mod.ThreatLensConfig?.CLOUD_FUNCTION_URL) {
          this.CLOUD_FUNCTION_URL = mod.ThreatLensConfig.CLOUD_FUNCTION_URL;
        }
      } catch (e) {
        // config.local.js is optional
      }
    }

    return new Promise((resolve) => {
      chrome.storage.local.get([
        'firebaseApiKey',
        'firebaseProjectId',
        'googleClientId',
        'cloudToken',
        'cloudRefreshToken',
        'cloudUserId',
        'cloudEmail',
        'cloudDisplayName',
        'cloudPhotoUrl',
        'cloudTokenExpiry'
      ], async (result) => {
        if (result.firebaseApiKey) {
          this.FIREBASE_API_KEY = result.firebaseApiKey;
        }
        if (result.firebaseProjectId) {
          this.FIREBASE_PROJECT_ID = result.firebaseProjectId;
        }
        if (result.googleClientId) {
          this.GOOGLE_CLIENT_ID = result.googleClientId;
        }

        if (result.cloudToken) {
          this.idToken = result.cloudToken;
          this.refreshToken = result.cloudRefreshToken || null;
          this.userId = result.cloudUserId || null;
          this.userEmail = result.cloudEmail || null;
          this.displayName = result.cloudDisplayName || null;
          this.photoUrl = result.cloudPhotoUrl || null;
          this.tokenExpiry = result.cloudTokenExpiry || null;

          // Check if token needs refresh (if older than 50 minutes or expired)
          if (this.refreshToken && (!this.tokenExpiry || Date.now() > this.tokenExpiry - 300000)) {
            await this.refreshIdToken();
          }
        }
        resolve({
          isLoggedIn: !!this.idToken,
          userId: this.userId,
          userEmail: this.userEmail,
          displayName: this.displayName,
          photoUrl: this.photoUrl
        });
      });
    });
  }

  /**
   * Refreshes the Firebase ID token using the Refresh Token
   */
  static async refreshIdToken() {
    if (!this.refreshToken || !this.FIREBASE_API_KEY) return false;
    try {
      const response = await fetch(`https://securetoken.googleapis.com/v1/token?key=${this.FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(this.refreshToken)}`
      });

      if (!response.ok) return false;
      const data = await response.json();

      this.idToken = data.id_token || data.access_token;
      this.refreshToken = data.refresh_token || this.refreshToken;
      this.tokenExpiry = Date.now() + (parseInt(data.expires_in || "3600", 10) * 1000);

      await chrome.storage.local.set({
        cloudToken: this.idToken,
        cloudRefreshToken: this.refreshToken,
        cloudTokenExpiry: this.tokenExpiry
      });
      return true;
    } catch (e) {
      console.warn("Token refresh failed:", e);
      return false;
    }
  }

  /**
   * Resolves Firestore user record by email or sanitized userId
   */
  static async fetchUserProfile(sanitizedId) {
    try {
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitizedId}`;
      const response = await fetch(url);
      if (!response.ok) return null;
      const doc = await response.json();
      if (!doc || !doc.fields) return null;

      return {
        userId: doc.fields.userId?.stringValue || sanitizedId,
        email: doc.fields.email?.stringValue || null,
        name: doc.fields.name?.stringValue || null,
        photoUrl: doc.fields.photoUrl?.stringValue || null
      };
    } catch (e) {
      console.warn("Error fetching user profile:", e);
      return null;
    }
  }

  /**
   * Queries Firestore users collection by email address
   */
  static async findUserByEmail(email) {
    try {
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents:runQuery`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          structuredQuery: {
            from: [{ collectionId: "users" }],
            where: {
              fieldFilter: {
                field: { fieldPath: "email" },
                op: "EQUAL",
                value: { stringValue: email.trim().toLowerCase() }
              }
            },
            limit: 1
          }
        })
      });

      if (!response.ok) return null;
      const results = await response.json();
      if (Array.isArray(results) && results[0] && results[0].document && results[0].document.fields) {
        const doc = results[0].document;
        const rawDocId = doc.name.split('/').pop();
        return {
          userId: doc.fields.userId?.stringValue || rawDocId,
          email: doc.fields.email?.stringValue || email,
          name: doc.fields.name?.stringValue || null,
          photoUrl: doc.fields.photoUrl?.stringValue || null
        };
      }
      return null;
    } catch (e) {
      console.warn("Error finding user by email:", e);
      return null;
    }
  }

  /**
   * Login using User ID or Email and Password (Full parity with Android App)
   */
  static async login(userIdOrEmail, password) {
    try {
      const cleanInput = (userIdOrEmail || "").trim();
      if (!cleanInput || !password) {
        return { success: false, error: "Please enter your User ID or Email and Password." };
      }

      let actualEmail = cleanInput;
      let targetUserId = null;

      // If user typed a User ID (no @ sign)
      if (!cleanInput.includes('@')) {
        const sanitized = this.sanitizeUserId(cleanInput);
        const profile = await this.fetchUserProfile(sanitized);
        if (!profile || !profile.email) {
          return { success: false, error: "User ID not found! Please register first or check spelling." };
        }
        actualEmail = profile.email;
        targetUserId = profile.userId || sanitized;
      }

      // Authenticate with Firebase REST API
      if (!this.FIREBASE_API_KEY) {
        return { success: false, error: "Firebase Web API Key is not configured. Please add your key in Dashboard Settings or core/config.local.js." };
      }
      const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${this.FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: actualEmail,
          password: password,
          returnSecureToken: true
        })
      });

      const data = await response.json();
      if (data.error) {
        const msg = data.error.message || "";
        if (msg.includes("EMAIL_NOT_FOUND") || msg.includes("INVALID_PASSWORD") || msg.includes("INVALID_LOGIN_CREDENTIALS")) {
          return { success: false, error: "Incorrect password or account not found." };
        } else if (msg.includes("USER_DISABLED")) {
          return { success: false, error: "This account has been disabled." };
        } else if (msg.includes("TOO_MANY_ATTEMPTS_TRY_LATER")) {
          return { success: false, error: "Too many failed attempts. Please try again in a few minutes." };
        }
        return { success: false, error: msg || "Authentication failed." };
      }

      this.idToken = data.idToken;
      this.refreshToken = data.refreshToken;
      this.userEmail = data.email;
      this.tokenExpiry = Date.now() + (parseInt(data.expiresIn || "3600", 10) * 1000);

      // If we don't have the targetUserId yet, resolve it via Firestore
      if (!targetUserId) {
        const userByEmail = await this.findUserByEmail(data.email);
        if (userByEmail && userByEmail.userId) {
          targetUserId = userByEmail.userId;
          this.displayName = userByEmail.name || data.displayName || null;
          this.photoUrl = userByEmail.photoUrl || null;
        } else {
          // Fallback to sanitized email prefix
          targetUserId = this.sanitizeUserId(data.email.split('@')[0]);
          this.displayName = data.displayName || targetUserId;
        }
      } else {
        const profile = await this.fetchUserProfile(this.sanitizeUserId(targetUserId));
        this.displayName = (profile && profile.name) || data.displayName || targetUserId;
        this.photoUrl = (profile && profile.photoUrl) || null;
      }

      this.userId = this.sanitizeUserId(targetUserId);

      // Save session in extension storage
      await chrome.storage.local.set({
        cloudToken: this.idToken,
        cloudRefreshToken: this.refreshToken,
        cloudUserId: this.userId,
        cloudEmail: this.userEmail,
        cloudDisplayName: this.displayName,
        cloudPhotoUrl: this.photoUrl,
        cloudTokenExpiry: this.tokenExpiry
      });

      return {
        success: true,
        userId: this.userId,
        email: this.userEmail,
        displayName: this.displayName,
        photoUrl: this.photoUrl
      };
    } catch (e) {
      console.error("Login failed:", e);
      return { success: false, error: "Network error. Please check your internet connection." };
    }
  }

  /**
   * Register a new user with email, password, and userId (Matching Android App)
   */
  static async register(name, userId, email, password) {
    try {
      const cleanName = (name || "").trim();
      const cleanUserId = (userId || "").trim();
      const cleanEmail = (email || "").trim();
      const sanitizedId = this.sanitizeUserId(cleanUserId);

      if (!cleanName || !cleanUserId || !cleanEmail || !password) {
        return { success: false, error: "All fields are required." };
      }

      if (password.length < 6) {
        return { success: false, error: "Password must be at least 6 characters." };
      }

      // 1. Check if User ID is already taken
      const existingUser = await this.fetchUserProfile(sanitizedId);
      if (existingUser) {
        return { success: false, error: "This User ID is already taken! Please choose another." };
      }

      // 2. Create user in Firebase Authentication
      if (!this.FIREBASE_API_KEY) {
        return { success: false, error: "Firebase Web API Key is not configured. Please add your key in Dashboard Settings or core/config.local.js." };
      }
      const signUpRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${this.FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: cleanEmail,
          password: password,
          returnSecureToken: true
        })
      });

      const data = await signUpRes.json();
      if (data.error) {
        const msg = data.error.message || "";
        if (msg.includes("EMAIL_EXISTS")) {
          return { success: false, error: "This email is already registered. Please sign in." };
        } else if (msg.includes("INVALID_EMAIL")) {
          return { success: false, error: "Please enter a valid email address." };
        }
        return { success: false, error: msg || "Registration failed." };
      }

      // 3. Send email verification
      try {
        await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${this.FIREBASE_API_KEY}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            requestType: "VERIFY_EMAIL",
            idToken: data.idToken
          })
        });
      } catch (err) {
        console.warn("Could not send verification email:", err);
      }

      // 4. Create user profile in Firestore at users/{sanitizedId}
      const firestoreDoc = {
        fields: {
          name: { stringValue: cleanName },
          userId: { stringValue: cleanUserId },
          email: { stringValue: cleanEmail },
          createdAt: { integerValue: Date.now().toString() }
        }
      };

      await fetch(`https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitizedId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${data.idToken}`
        },
        body: JSON.stringify(firestoreDoc)
      });

      return {
        success: true,
        message: "Registration successful! A verification email has been sent to your inbox."
      };
    } catch (e) {
      console.error("Registration error:", e);
      return { success: false, error: "Network error during registration." };
    }
  }

  /**
   * Login using Google OAuth via chrome.identity.launchWebAuthFlow and Firebase signInWithIdp
   */
  static async loginWithGoogle() {
    if (!this.FIREBASE_API_KEY) {
      return { success: false, error: "Firebase Web API Key is not configured. Please add your key in Dashboard Settings or core/config.local.js." };
    }
    return new Promise((resolve) => {
      try {
        const redirectUri = chrome.identity.getRedirectURL();
        const nonce = Math.random().toString(36).substring(2) + Date.now().toString();
        
        // Standard OAuth2 endpoint for Google Identity
        const authUrl = `https://accounts.google.com/o/oauth2/v2/auth` +
          `?client_id=${encodeURIComponent(this.GOOGLE_CLIENT_ID)}` +
          `&response_type=${encodeURIComponent('token id_token')}` +
          `&redirect_uri=${encodeURIComponent(redirectUri)}` +
          `&scope=${encodeURIComponent('openid email profile')}` +
          `&nonce=${encodeURIComponent(nonce)}` +
          `&prompt=select_account`;

        chrome.identity.launchWebAuthFlow({ url: authUrl, interactive: true }, async (responseUrl) => {
          if (chrome.runtime.lastError || !responseUrl) {
            const errMessage = chrome.runtime.lastError ? chrome.runtime.lastError.message : "Authentication window was closed.";
            
            // Try fallback to chrome.identity.getAuthToken if available
            return this._loginWithGoogleGetAuthTokenFallback(resolve, errMessage);
          }

          try {
            // Parse tokens from response fragment (#access_token=...&id_token=...)
            const hashIndex = responseUrl.indexOf('#');
            const queryString = hashIndex !== -1 ? responseUrl.substring(hashIndex + 1) : '';
            const params = new URLSearchParams(queryString);
            const idToken = params.get('id_token');
            const accessToken = params.get('access_token');

            if (!idToken && !accessToken) {
              return resolve({ success: false, error: "No authentication tokens returned by Google." });
            }

            const postBody = idToken 
              ? `id_token=${idToken}&providerId=google.com`
              : `access_token=${accessToken}&providerId=google.com`;

            // Exchange with Firebase Identity Toolkit
            const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${this.FIREBASE_API_KEY}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                postBody: postBody,
                requestUri: "http://localhost",
                returnIdpCredential: true,
                returnSecureToken: true
              })
            });

            const data = await response.json();
            if (data.error) {
              return resolve({ success: false, error: data.error.message || "Firebase Google login failed." });
            }

            await this._finalizeGoogleLogin(data, resolve);
          } catch (e) {
            console.error("Google token exchange error:", e);
            resolve({ success: false, error: "Failed to exchange Google token with Firebase." });
          }
        });
      } catch (e) {
        console.error("Google login initiation error:", e);
        resolve({ success: false, error: e.message || "Failed to start Google sign-in." });
      }
    });
  }

  /**
   * Fallback for getAuthToken
   */
  static _loginWithGoogleGetAuthTokenFallback(resolve, primaryError) {
    if (!chrome.identity.getAuthToken) {
      return resolve({ success: false, error: primaryError });
    }

    chrome.identity.getAuthToken({ interactive: true }, async (token) => {
      if (chrome.runtime.lastError || !token) {
        console.warn("getAuthToken fallback failed:", chrome.runtime.lastError);
        return resolve({ success: false, error: primaryError || "Google Sign-In was cancelled or failed." });
      }

      try {
        const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${this.FIREBASE_API_KEY}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            postBody: `access_token=${token}&providerId=google.com`,
            requestUri: "http://localhost",
            returnIdpCredential: true,
            returnSecureToken: true
          })
        });

        const data = await response.json();
        if (data.error) {
          return resolve({ success: false, error: data.error.message });
        }

        await this._finalizeGoogleLogin(data, resolve);
      } catch (e) {
        resolve({ success: false, error: primaryError || "Google Sign-In failed." });
      }
    });
  }

  /**
   * Finalizes Google Sign-In state and syncs/creates user document in Firestore
   */
  static async _finalizeGoogleLogin(data, resolve) {
    try {
      this.idToken = data.idToken;
      this.refreshToken = data.refreshToken;
      this.userEmail = data.email;
      this.displayName = data.displayName || data.email.split('@')[0];
      this.photoUrl = data.photoUrl || null;
      this.tokenExpiry = Date.now() + (parseInt(data.expiresIn || "3600", 10) * 1000);

      // Check if user already has a document in Firestore (linked by email)
      const existing = await this.findUserByEmail(data.email);
      let targetUserId;

      if (existing && existing.userId) {
        targetUserId = existing.userId;
        if (existing.name) this.displayName = existing.name;
        if (existing.photoUrl) this.photoUrl = existing.photoUrl;
      } else {
        // New Google user! Create Firestore document matching Android App CloudSyncManager schema
        targetUserId = this.sanitizeUserId(data.email.split('@')[0]);
        const sanitizedId = this.sanitizeUserId(targetUserId);

        const newProfile = {
          fields: {
            userId: { stringValue: targetUserId },
            email: { stringValue: data.email },
            name: { stringValue: this.displayName },
            photoUrl: { stringValue: this.photoUrl || "" },
            createdAt: { integerValue: Date.now().toString() }
          }
        };

        try {
          await fetch(`https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitizedId}`, {
            method: 'PATCH',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${this.idToken}`
            },
            body: JSON.stringify(newProfile)
          });
        } catch (err) {
          console.warn("Could not create initial Google user doc in Firestore:", err);
        }
      }

      this.userId = this.sanitizeUserId(targetUserId);

      // Save session in local storage
      await chrome.storage.local.set({
        cloudToken: this.idToken,
        cloudRefreshToken: this.refreshToken,
        cloudUserId: this.userId,
        cloudEmail: this.userEmail,
        cloudDisplayName: this.displayName,
        cloudPhotoUrl: this.photoUrl,
        cloudTokenExpiry: this.tokenExpiry
      });

      resolve({
        success: true,
        userId: this.userId,
        email: this.userEmail,
        displayName: this.displayName,
        photoUrl: this.photoUrl
      });
    } catch (e) {
      console.error("Error finalizing Google login:", e);
      resolve({ success: false, error: "Failed to finalize session." });
    }
  }

  /**
   * Logout and clear session
   */
  static async logout() {
    this.idToken = null;
    this.refreshToken = null;
    this.userId = null;
    this.userEmail = null;
    this.displayName = null;
    this.photoUrl = null;
    this.tokenExpiry = null;
    
    await chrome.storage.local.remove([
      'cloudToken',
      'cloudRefreshToken',
      'cloudUserId',
      'cloudEmail',
      'cloudDisplayName',
      'cloudPhotoUrl',
      'cloudTokenExpiry'
    ]);
  }

  /**
   * Push a local scan to Firestore 'history' collection (Matching Android ScanResult Schema)
   */
  static async syncScanToCloud(scanResult) {
    if (!this.idToken || !this.userId || !scanResult) return false;

    // Check if token expired
    if (this.tokenExpiry && Date.now() > this.tokenExpiry) {
      await this.refreshIdToken();
    }

    try {
      // Create a deterministic hash for the docId matching Android rawContent.hashCode()
      const rawStr = scanResult.rawContent || scanResult.payload || scanResult.url || scanResult.expandedUrl || '';
      let hash = 0;
      for (let i = 0; i < rawStr.length; i++) {
        hash = Math.imul(31, hash) + rawStr.charCodeAt(i) | 0;
      }
      const docId = (hash !== 0 ? hash : Date.now()).toString();

      // Format payload matching Android ScanResult data model exactly
      const payloadObject = {
        rawContent: scanResult.rawContent || scanResult.payload || rawStr,
        isUrl: scanResult.isUrl !== undefined ? scanResult.isUrl : (rawStr.startsWith('http://') || rawStr.startsWith('https://')),
        originalUrl: scanResult.originalUrl || scanResult.rawContent || rawStr,
        expandedUrl: scanResult.expandedUrl || scanResult.rawContent || rawStr,
        domain: scanResult.domain || (scanResult.isUrl ? new URL(rawStr).hostname : null),
        safetyStatus: scanResult.safetyStatus || 'SAFE',
        overallScore: scanResult.overallScore !== undefined ? scanResult.overallScore : (100 - (scanResult.riskScore || scanResult.score || 0)),
        heuristicFlags: scanResult.flags || scanResult.heuristicFlags || [],
        threatDetails: scanResult.threatDetails || scanResult.flags || [],
        siteCategory: scanResult.siteCategory || 'General',
        siteSummary: scanResult.siteSummary || scanResult.message || '',
        timestamp: scanResult.timestamp || Date.now(),
        upiAnalysis: scanResult.upiAnalysis || null,
        wifiAnalysis: scanResult.wifiAnalysis || null
      };

      const jsonPayload = JSON.stringify(payloadObject);
      const firestoreDoc = {
        fields: {
          timestamp: { integerValue: (scanResult.timestamp || Date.now()).toString() },
          data: { stringValue: jsonPayload }
        }
      };

      const sanitized = this.sanitizeUserId(this.userId);
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history?documentId=${docId}`;
      
      let response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.idToken}`
        },
        body: JSON.stringify(firestoreDoc)
      });
      
      // If document already exists, POST returns 409 (ALREADY_EXISTS). Update with PATCH
      if (response.status === 409) {
        const patchUrl = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history/${docId}`;
        response = await fetch(patchUrl, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.idToken}`
          },
          body: JSON.stringify(firestoreDoc)
        });
      } else if (response.status === 401) {
        // Token expired, refresh and retry once
        const refreshed = await this.refreshIdToken();
        if (refreshed) {
          await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${this.idToken}`
            },
            body: JSON.stringify(firestoreDoc)
          });
        }
      }

      return response.ok;
    } catch (e) {
      console.error("Failed to sync scan to cloud:", e);
      return false;
    }
  }

  /**
   * Fetch History from Firestore (Matching Android CloudSyncManager.fetchHistoryFromCloud)
   */
  static async fetchHistoryFromCloud() {
    if (!this.idToken || !this.userId) return [];

    if (this.tokenExpiry && Date.now() > this.tokenExpiry) {
      await this.refreshIdToken();
    }

    try {
      const sanitized = this.sanitizeUserId(this.userId);
      const url = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${sanitized}/history`;
      let response = await fetch(url, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${this.idToken}` }
      });

      if (response.status === 401) {
        const refreshed = await this.refreshIdToken();
        if (refreshed) {
          response = await fetch(url, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${this.idToken}` }
          });
        }
      }

      const data = await response.json();
      if (data.documents) {
        return data.documents.map(doc => {
          if (doc.fields && doc.fields.data && doc.fields.data.stringValue) {
            try {
              return JSON.parse(doc.fields.data.stringValue);
            } catch (e) { return null; }
          }
          return null;
        }).filter(item => item !== null);
      }
      return [];
    } catch (e) {
      console.error("Failed to fetch history:", e);
      return [];
    }
  }

  /**
   * Sends URL & DOM to Firebase Cloud Function for deep neural inspection
   */
  static async analyzeUrlDeeply(url, pageText = "") {
    try {
      const response = await fetch(`${this.CLOUD_FUNCTION_URL}/analyzeThreatUrl`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url, pageText: pageText })
      });

      if (!response.ok) return null;

      const result = await response.json();
      if (result.success && result.data) {
        return {
          score: result.data.score,
          flags: result.data.flags || [],
          threatType: result.data.threatType || 'SAFE',
          aiInsight: result.data.aiInsight || null
        };
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /**
   * Gets community reports for a given URL from Firestore.
   */
  static async getCommunityReports(url) {
    if (!url) return { count: 0, reasons: [] };
    try {
      const sanitizedUrl = url.replace(/[^a-zA-Z0-9]/g, "_");
      const endpoint = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/reports/${sanitizedUrl}`;
      const response = await fetch(endpoint);
      if (!response.ok) return { count: 0, reasons: [] };

      const doc = await response.json();
      if (doc.fields && doc.fields.reasons && doc.fields.reasons.arrayValue && doc.fields.reasons.arrayValue.values) {
        const reasons = doc.fields.reasons.arrayValue.values.map(v => v.stringValue).filter(Boolean);
        return { count: reasons.length, reasons: reasons };
      }
      return { count: 0, reasons: [] };
    } catch (e) {
      return { count: 0, reasons: [] };
    }
  }

  /**
   * Reports a website/URL to the global Firestore community database.
   */
  static async reportWebsite(url, issue) {
    if (!url || !issue) return false;
    try {
      const sanitizedUrl = url.replace(/[^a-zA-Z0-9]/g, "_");
      const current = await this.getCommunityReports(url);
      const updatedReasons = [...current.reasons, issue];

      const firestoreDoc = {
        fields: {
          reasons: {
            arrayValue: {
              values: updatedReasons.map(r => ({ stringValue: r }))
            }
          }
        }
      };

      const patchUrl = `https://firestore.googleapis.com/v1/projects/${this.FIREBASE_PROJECT_ID}/databases/(default)/documents/reports/${sanitizedUrl}?updateMask.fieldPaths=reasons`;
      const response = await fetch(patchUrl, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(firestoreDoc)
      });
      return response.ok;
    } catch (e) {
      return false;
    }
  }
}
