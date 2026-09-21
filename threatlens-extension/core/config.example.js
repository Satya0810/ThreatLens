// config.example.js
// Template configuration for ThreatLens Extension.
// Copy this file to `config.local.js` (which is git-ignored) for local development,
// or configure these keys directly in the ThreatLens Dashboard -> Settings tab.

export const ThreatLensConfig = {
  // Firebase Web API Key for Cloud Sync and Authentication
  // Obtain from Firebase Console -> Project Settings -> General -> Web API Key
  FIREBASE_API_KEY: "",

  // Firebase Project ID
  FIREBASE_PROJECT_ID: "threatlens-4065e",

  // Google OAuth 2.0 Client ID
  GOOGLE_CLIENT_ID: "171716467103-b09ukthoklag4na2p1sk30lduo41c098.apps.googleusercontent.com",

  // Cloud Function Endpoint
  CLOUD_FUNCTION_URL: "https://us-central1-threatlens-4065e.cloudfunctions.net",

  // Optional LLM7.io API Key for AI threat explanations
  LLM7_API_KEY: ""
};
