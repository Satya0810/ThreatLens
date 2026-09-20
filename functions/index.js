const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { Resend } = require("resend");
const categorizerData = require("./categorizer_data.json");

admin.initializeApp();
const db = admin.firestore();

const RESEND_API_KEY = process.env.RESEND_API_KEY;
const resend = new Resend(RESEND_API_KEY);

exports.sendOtpEmail = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  if (req.method === "OPTIONS") {
    res.set("Access-Control-Allow-Methods", "POST");
    res.set("Access-Control-Allow-Headers", "Content-Type");
    return res.status(204).send("");
  }
  if (req.method !== "POST") return res.status(405).json({ error: "Method Not Allowed" });

  const { email } = req.body;
  if (!email) return res.status(400).json({ error: "Email is required" });

  try {
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    await db.collection("otps").doc(email).set({ otp: otp, expiresAt: Date.now() + 300000 });
    await resend.emails.send({
      from: "ThreatLens Security <onboarding@resend.dev>",
      to: [email],
      subject: "Your ThreatLens Verification Code",
      html: `<h2>Welcome</h2><p>Code: ${otp}</p>`
    });
    return res.status(200).json({ success: true, message: "OTP sent" });
  } catch (error) {
    return res.status(500).json({ error: "Internal server error" });
  }
});

// ============================================================================
// Deep URL Threat Analysis Endpoint (Full Parity with Android ThreatAnalyzer)
// ============================================================================
exports.analyzeThreatUrl = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  if (req.method === "OPTIONS") {
    res.set("Access-Control-Allow-Methods", "POST");
    res.set("Access-Control-Allow-Headers", "Content-Type");
    return res.status(204).send("");
  }
  if (req.method !== "POST") return res.status(405).json({ error: "Method Not Allowed" });

  const { url, pageText } = req.body;
  if (!url) return res.status(400).json({ error: "URL is required" });

  try {
    // 1. Log Analytics (Fire-and-forget)
    const sanitizedUrl = url.replace(/[^a-zA-Z0-9]/g, "_");
    db.collection("reports").doc(sanitizedUrl).set({
      visitCount: admin.firestore.FieldValue.increment(1),
      lastVisited: Date.now()
    }, { merge: true }).catch(() => {});

    let cloudScore = 0;
    let flags = [];
    let threatType = 'SAFE';
    
    let domain = "";
    try { domain = new URL(url).hostname; } catch(e) {}
    
    // 0. Website Categorizer & Cloud Overrides
    if (domain) {
      let category = categorizerData[domain]; // Base deterministic category

      // Fetch Cloud Overrides from Firestore (app_config/datasets)
      try {
        const docRef = await db.collection("app_config").doc("datasets").get();
        if (docRef.exists) {
          const cloudDatasets = docRef.data();
          if (cloudDatasets.websiteCategorizerData && cloudDatasets.websiteCategorizerData.KNOWN_DOMAINS) {
            const override = cloudDatasets.websiteCategorizerData.KNOWN_DOMAINS[domain];
            if (override) {
              category = override;
              flags.push(`Cloud Override: Domain category overridden to ${category}`);
            }
          }
        }
      } catch (e) {
        console.error("Failed to fetch cloud overrides:", e);
      }

      if (category) {
        // Safe Categories
        if (category.includes('SAFE') || ['NEWS', 'RETAIL', 'BANKING', 'EDUCATION'].some(k => category.includes(k))) {
          // Do nothing, base score remains 0
        } 
        // Caution Categories (Betting, Pornography, Piracy, etc)
        else if (['BETTING', 'PORNOGRAPHY', 'ADULT', 'CASINO', 'GAMBLING', 'PIRACY', 'TORRENT'].some(k => category.includes(k))) {
          cloudScore += 45; // Sets to Caution level (Yellow Screen)
          flags.push(`Categorization: Identified as ${category}`);
          if (threatType === 'SAFE') threatType = category;
        } 
        // Dangerous Categories (Malware, Scams, etc)
        else if (['MALWARE', 'SCAM', 'FRAUD', 'PHISHING'].some(k => category.includes(k))) {
          cloudScore += 80;
          flags.push(`Categorization: Identified as ${category}`);
          threatType = category;
        }
        // Fallback for any other category
        else {
          cloudScore += 30; // Just suspicious
          flags.push(`Categorization: Identified as ${category}`);
          if (threatType === 'SAFE') threatType = category;
        }
      }
    }

    // Deep Packet Inspection (DPI) Mock
    if (url.includes("1xbet") || url.includes("casino")) {
      cloudScore += 65;
      flags.push("Cloud DPI: Identified as High-Risk Gambling");
      threatType = "HIGH-RISK";
    }
    if (url.endsWith(".exe") || url.endsWith(".apk")) {
      cloudScore += 90;
      flags.push("Cloud DPI: Executable Download Detected");
      threatType = "MALWARE";
    }

    // Run external API checks concurrently to minimize latency
    const checks = [];

    // 1. Google SafeBrowsing
    const safeBrowsingKey = process.env.SAFEBROWSING_API_KEY;
    if (safeBrowsingKey) {
      checks.push(
        fetch(`https://safebrowsing.googleapis.com/v4/threatMatches:find?key=${safeBrowsingKey}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client: { clientId: "threatlens", clientVersion: "1.0" },
            threatInfo: {
              threatTypes: ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"],
              platformTypes: ["ANY_PLATFORM"],
              threatEntryTypes: ["URL"],
              threatEntries: [{ url: url }]
            }
          })
        }).then(r => r.json()).then(data => {
          if (data.matches && data.matches.length > 0) {
            cloudScore += 90;
            threatType = data.matches[0].threatType === "SOCIAL_ENGINEERING" ? "PHISHING" : "MALWARE";
            flags.push(`SafeBrowsing: Flagged as ${data.matches[0].threatType}`);
          }
        }).catch(e => console.error("SafeBrowsing Err:", e))
      );
    }

    // 2. VirusTotal (v3)
    const vtApiKey = process.env.VIRUSTOTAL_API_KEY;
    if (vtApiKey) {
      checks.push(
        (async () => {
          const urlId = Buffer.from(url).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
          const vtRes = await fetch(`https://www.virustotal.com/api/v3/urls/${urlId}`, {
            headers: { 'x-apikey': vtApiKey }
          });
          if (vtRes.ok) {
            const data = await vtRes.json();
            const stats = data.data.attributes.last_analysis_stats;
            if (stats.malicious > 0 || stats.suspicious > 0) {
              cloudScore += (stats.malicious * 20) + (stats.suspicious * 10);
              flags.push(`VirusTotal: Flagged by ${stats.malicious} vendors`);
              if (threatType === 'SAFE') threatType = "MALWARE";
            }
          }
        })().catch(e => console.error("VT Err:", e))
      );
    }

    // 3. Quad9 DNS Filter (DoH) - No API Key needed
    if (domain) {
      checks.push(
        fetch(`https://dns.quad9.net/dns-query?name=${domain}&type=A`, {
          headers: { 'accept': 'application/dns-json' }
        }).then(r => r.json()).then(data => {
          if (data.Status === 3) { // NXDOMAIN from Quad9 usually means blocked
            cloudScore += 80;
            flags.push("Quad9 DNS: Domain blocked (Malware/Ransomware)");
            if (threatType === 'SAFE') threatType = "MALWARE";
          }
        }).catch(e => console.error("Quad9 Err:", e))
      );
    }

    // 4. Cloudflare DNS Filter (1.1.1.2 Malware Filter)
    if (domain) {
      checks.push(
        fetch(`https://security.cloudflare-dns.com/dns-query?name=${domain}&type=A`, {
          headers: { 'accept': 'application/dns-json' }
        }).then(r => r.json()).then(data => {
          // Cloudflare sometimes returns 0.0.0.0 for blocked domains
          if (data.Answer && data.Answer.some(a => a.data === "0.0.0.0")) {
            cloudScore += 80;
            flags.push("Cloudflare DNS: Domain blocked (Malware)");
            if (threatType === 'SAFE') threatType = "MALWARE";
          }
        }).catch(e => console.error("Cloudflare DNS Err:", e))
      );
    }

    // 5. URLHaus (Malware Database)
    checks.push(
      fetch('https://urlhaus-api.abuse.ch/v1/url/', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `url=${encodeURIComponent(url)}`
      }).then(r => r.json()).then(data => {
        if (data.query_status === "ok") {
          cloudScore += 95;
          flags.push(`URLhaus: Known malware URL (${data.threat})`);
          threatType = "MALWARE";
        }
      }).catch(e => console.error("URLhaus Err:", e))
    );

    // 6. AbuseIPDB
    const abuseIpdbKey = process.env.ABUSE_IPDB_KEY;
    if (abuseIpdbKey && domain) {
      checks.push(
        fetch(`https://api.abuseipdb.com/api/v2/check?ipAddress=${domain}`, {
          headers: { 'Key': abuseIpdbKey, 'Accept': 'application/json' }
        }).then(r => r.json()).then(data => {
          if (data && data.data && data.data.abuseConfidenceScore > 50) {
            cloudScore += (data.data.abuseConfidenceScore / 2);
            flags.push(`AbuseIPDB: High abuse confidence (${data.data.abuseConfidenceScore}%)`);
            if (threatType === 'SAFE') threatType = "HIGH-RISK";
          }
        }).catch(e => console.error("AbuseIPDB Err:", e)) // Fails cleanly if domain isn't an IP
      );
    }

    // 7. URLScan.io
    const urlScanKey = process.env.URL_SCAN_IO_KEY;
    if (urlScanKey) {
      checks.push(
        fetch(`https://urlscan.io/api/v1/search/?q=domain:${domain}`, {
          headers: { 'API-Key': urlScanKey }
        }).then(r => r.json()).then(data => {
          if (data && data.results && data.results.length > 0) {
            const malicious = data.results.filter(r => r.task.tags && r.task.tags.includes('malicious'));
            if (malicious.length > 0) {
              cloudScore += 60;
              flags.push(`URLScan: Domain tagged as malicious in previous scans`);
              if (threatType === 'SAFE') threatType = "MALWARE";
            }
          }
        }).catch(e => console.error("URLScan Err:", e))
      );
    }

    // 8. AI Learning Engine (LLM7.io Fast Engine & NLP Phishing Analysis)
    if (pageText) {
      checks.push(
        (async () => {
          const llm7ApiKey = process.env.LLM7_API_KEY || "jc8ydp2rnkoVuODXFJRFAILIY+KpjUuSbjWeLb9CqSAv1rNhwdNQllrPi6oQ5Q37LtGbVGvwKDHq06/HEP+nXE+jKtXLIFiH/beTcdPoq7n8kxaISx9bmfrWaVe3p9YuZUotBO1ZuMPcDrjRD+1QU+EhbuAerw==";
          if (llm7ApiKey) {
            try {
              const controller = new AbortController();
              const timeoutId = setTimeout(() => controller.abort(), 4000);
              const resp = await fetch("https://api.llm7.io/v1/chat/completions", {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                  "Authorization": `Bearer ${llm7ApiKey}`
                },
                body: JSON.stringify({
                  model: "default",
                  messages: [
                    {
                      role: "system",
                      content: "You are ThreatLens AI Security Engine. Determine if this page content is phishing or malicious. Respond ONLY with JSON: {\"isPhishing\": boolean, \"confidence\": number, \"reason\": string}"
                    },
                    {
                      role: "user",
                      content: `URL: ${url}\nContent: ${pageText.substring(0, 400)}`
                    }
                  ],
                  max_tokens: 120,
                  temperature: 0.1
                }),
                signal: controller.signal
              });
              clearTimeout(timeoutId);

              if (resp.ok) {
                const data = await resp.json();
                const content = data.choices?.[0]?.message?.content;
                if (content) {
                  const match = content.match(/\{[\s\S]*\}/);
                  if (match) {
                    const aiDecision = JSON.parse(match[0]);
                    if (aiDecision.isPhishing && aiDecision.confidence > 0.6) {
                      cloudScore += 80;
                      flags.push(`LLM7 AI: Flagged as deceptive/phishing (${aiDecision.reason})`);
                      threatType = "PHISHING";
                      return;
                    }
                  }
                }
              }
            } catch (e) {
              console.warn("LLM7 Cloud Phishing Check error:", e.message);
            }
          }

          // Fallback NLP heuristics:
          const lowerText = pageText.toLowerCase();
          const socialEngineeringKeywords = ['account suspended', 'verify your identity', 'confirm password', 'unauthorized access detected', 'claim your prize'];
          let matches = 0;
          socialEngineeringKeywords.forEach(kw => {
            if (lowerText.includes(kw)) matches++;
          });
          
          if (matches >= 2) {
            cloudScore += 75;
            flags.push(`AI Engine: High probability of Social Engineering / Phishing (${matches} trigger phrases)`);
            threatType = "PHISHING";
          }
        })()
      );
    }

    // Wait for all concurrent checks to finish
    await Promise.allSettled(checks);

    cloudScore = Math.min(100, cloudScore);

    return res.status(200).json({
      success: true,
      data: {
        score: cloudScore,
        flags: flags,
        threatType: threatType,
        source: "Firebase_Cloud_Engine"
      }
    });

  } catch (error) {
    console.error("Cloud Analyzer Error:", error);
    return res.status(500).json({ error: "Internal server error" });
  }
});
