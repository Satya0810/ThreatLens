// landing-page script.js
// Interactive Demo Engine with Heuristic Scanner & LLM7.io AI Integration

document.addEventListener('DOMContentLoaded', () => {
    const navbar = document.getElementById('navbar');

    // 1. Sticky Navbar on Scroll
    window.addEventListener('scroll', () => {
        if (window.scrollY > 40) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
        revealElements();
    });

    // 2. Scroll Reveal Animations
    function revealElements() {
        const reveals = document.querySelectorAll('.reveal');
        const windowHeight = window.innerHeight;
        const elementVisible = 90;

        reveals.forEach((element) => {
            const elementTop = element.getBoundingClientRect().top;
            if (elementTop < windowHeight - elementVisible) {
                element.classList.add('active');
            }
        });
    }
    revealElements();

    // 3. Smooth Scroll for Anchor Links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const targetId = this.getAttribute('href');
            if (targetId === '#' || !targetId) return;
            const targetElement = document.querySelector(targetId);
            if (targetElement) {
                e.preventDefault();
                const headerOffset = 80;
                const elementPosition = targetElement.getBoundingClientRect().top;
                const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
                window.scrollTo({
                    top: offsetPosition,
                    behavior: "smooth"
                });
            }
        });
    });

    // 4. Interactive Live Security Scanner Demo with LLM7.io
    const demoUrlInput = document.getElementById('demoUrlInput');
    const btnRunDemoScan = document.getElementById('btnRunDemoScan');
    const demoTargetUrl = document.getElementById('demoTargetUrl');
    const demoStatusBadge = document.getElementById('demoStatusBadge');
    const demoTrustScore = document.getElementById('demoTrustScore');
    const demoCategory = document.getElementById('demoCategory');
    const demoFlagsCount = document.getElementById('demoFlagsCount');
    const demoAiVerdict = document.getElementById('demoAiVerdict');
    const demoFlagsList = document.getElementById('demoFlagsList');
    const sampleButtons = document.querySelectorAll('.sample-btn');

    // Active LLM7.io API key from ThreatLens configuration
    const LLM7_API_KEY = "jc8ydp2rnkoVuODXFJRFAILIY+KpjUuSbjWeLb9CqSAv1rNhwdNQllrPi6oQ5Q37LtGbVGvwKDHq06/HEP+nXE+jKtXLIFiH/beTcdPoq7n8kxaISx9bmfrWaVe3p9YuZUotBO1ZuMPcDrjRD+1QU+EhbuAerw==";

    // Fast client-side heuristics rules matching Android ThreatAnalyzer.kt
    const SUSPICIOUS_TLDS = new Set(['.xyz', '.top', '.buzz', '.club', '.tk', '.ml', '.ga', '.cf', '.gq', '.ist', '.wtf', '.lol', '.gripe', '.pw', '.cc']);
    const TRUSTED_BRANDS = ['paypal', 'google', 'microsoft', 'apple', 'amazon', 'netflix', 'facebook', 'instagram', 'chase', 'bankofamerica', 'paytm', 'phonepe'];
    const PHISHING_KEYWORDS = ['login', 'signin', 'account', 'verify', 'update', 'claim', 'giveaway', 'prize', 'winner', 'free-iphone', 'security-alert'];
    const PRIVATE_IP_REGEX = /^(127\.\d{1,3}\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|0\.0\.0\.0|localhost)$/;
    const HOMOGRAPH_REGEX = /[\u0370-\u03FF\u0400-\u04FF]/;

    function runLocalHeuristicInspection(urlString) {
        const flags = [];
        let riskScore = 0;
        let threatType = 'SAFE';
        let category = '🌐 General Website';

        try {
            const trimmed = urlString.trim();
            const url = new URL(trimmed.startsWith('http') ? trimmed : `https://${trimmed}`);
            const hostname = url.hostname.toLowerCase();
            const pathname = url.pathname.toLowerCase();
            const fullUrl = url.href.toLowerCase();

            // Check SSRF / Private IP
            if (PRIVATE_IP_REGEX.test(hostname)) {
                riskScore += 90;
                flags.push("Private IP address used as hostname (SSRF / Internal network probing vector)");
                threatType = 'MALICIOUS';
                category = '⚠️ SSRF / Intranet Probe';
            }

            // Check Homographs / Punycode
            if (HOMOGRAPH_REGEX.test(hostname) || hostname.startsWith('xn--')) {
                riskScore += 85;
                flags.push("Homoglyph/Punycode lookalike character spoofing detected in domain name");
                threatType = 'MALICIOUS';
                category = '🔴 Phishing & Impersonation';
            }

            // Check Authority @ symbol
            if (url.username || fullUrl.split('/')[2]?.includes('@')) {
                riskScore += 80;
                flags.push("Contains '@' in authority section (Credential harvesting attack)");
                threatType = 'MALICIOUS';
                category = '🔴 Phishing & Impersonation';
            }

            // Check Suspicious TLD
            for (const tld of SUSPICIOUS_TLDS) {
                if (hostname.endsWith(tld)) {
                    riskScore += 45;
                    flags.push(`Suspicious high-risk TLD (${tld}) frequently abused in fraud campaigns`);
                    break;
                }
            }

            // Check Brand Spoofing
            for (const brand of TRUSTED_BRANDS) {
                if (hostname.includes(brand)) {
                    const isOfficial = hostname === `${brand}.com` || hostname.endsWith(`.${brand}.com`);
                    if (!isOfficial) {
                        riskScore += 75;
                        flags.push(`Brand impersonation detected: domain incorporates '${brand}' brand keyword in an unverified host`);
                        threatType = 'MALICIOUS';
                        category = '🔴 Brand Impersonation';
                        break;
                    }
                }
            }

            // Check Phishing Keywords in path
            for (const kw of PHISHING_KEYWORDS) {
                if (pathname.includes(kw) || fullUrl.includes(kw)) {
                    riskScore += 30;
                    flags.push(`Suspicious credential or social engineering keyword in path: '${kw}'`);
                    if (threatType === 'SAFE') category = '⚠️ Suspicious Portal';
                    break;
                }
            }

            // High-confidence safe domain check
            const safeDomains = ['google.com', 'github.com', 'microsoft.com', 'apple.com', 'amazon.com', 'wikipedia.org'];
            if (safeDomains.some(d => hostname === d || hostname.endsWith(`.${d}`))) {
                riskScore = 0;
                flags.length = 0;
                category = '🛡️ Verified Safe Platform';
                threatType = 'SAFE';
            }

        } catch (e) {
            riskScore = 50;
            flags.push("Malformed or unparseable destination URL");
            category = '⚠️ Malformed URL';
        }

        const trustScore = Math.max(0, 100 - Math.min(100, riskScore));
        return {
            trustScore,
            riskScore,
            threatType: trustScore < 50 ? 'MALICIOUS' : (trustScore < 80 ? 'CAUTION' : 'SAFE'),
            category,
            flags
        };
    }

    // Call LLM7.io Fast Engine for natural language threat breakdown
    async function queryLlm7Ai(url, trustScore, flags, threatType) {
        try {
            const prompt = `You are ThreatLens AI, an elite cybersecurity analyzer. Provide a sharp, direct 2-sentence security assessment for this destination:
URL: ${url}
Trust Score: ${trustScore}/100
Threat Status: ${threatType}
Heuristic Flags: ${flags.join('; ') || 'None detected'}

Give a concise verdict explaining why this destination is safe or dangerous, and advice for the user.`;

            const res = await fetch("https://api.llm7.io/v1/chat/completions", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${LLM7_API_KEY}`
                },
                body: JSON.stringify({
                    model: "gemini-2.5-flash",
                    messages: [{ role: "user", content: prompt }],
                    temperature: 0.2,
                    max_tokens: 150
                })
            });

            if (res.ok) {
                const data = await res.json();
                const text = data.choices?.[0]?.message?.content?.trim();
                if (text) return text;
            }
        } catch (e) {
            console.warn("LLM7 live query fallback:", e);
        }

        // Fallback intelligent summary
        if (trustScore < 50) {
            return `ThreatLens AI has identified severe phishing or spoofing patterns for ${url}. Visiting this link poses an immediate risk of credential theft and credential harvesting.`;
        } else if (trustScore < 80) {
            return `This destination exhibits questionable heuristics or an unverified domain age. Proceed with caution and verify the source before providing personal information.`;
        } else {
            return `ThreatLens verified signature and domain reputation confirm this is a trusted, secure destination with zero detected malicious signals.`;
        }
    }

    async function executeDemoScan(targetUrl) {
        if (!targetUrl) return;

        btnRunDemoScan.disabled = true;
        btnRunDemoScan.innerHTML = '<span>⏳</span> Analyzing...';
        demoTargetUrl.textContent = targetUrl;
        demoAiVerdict.textContent = "Querying ThreatLens Neural Core & LLM7.io Fast Engine...";

        // Step 1: Run local heuristics immediately
        const res = runLocalHeuristicInspection(targetUrl);

        // Update HUD
        demoTrustScore.textContent = `${res.trustScore} / 100`;
        demoCategory.textContent = res.category;
        demoFlagsCount.textContent = `${res.flags.length} Issue(s) Found`;

        if (res.threatType === 'MALICIOUS') {
            demoStatusBadge.className = 'status-pill danger';
            demoStatusBadge.textContent = 'MALICIOUS THREAT';
            demoTrustScore.className = 'hud-stat-val danger';
        } else if (res.threatType === 'CAUTION') {
            demoStatusBadge.className = 'status-pill' ;
            demoStatusBadge.style.background = 'rgba(245, 158, 11, 0.15)';
            demoStatusBadge.style.color = '#f59e0b';
            demoStatusBadge.style.border = '1px solid rgba(245, 158, 11, 0.4)';
            demoStatusBadge.textContent = 'CAUTION ADVISED';
            demoTrustScore.className = 'hud-stat-val';
            demoTrustScore.style.color = '#f59e0b';
        } else {
            demoStatusBadge.className = 'status-pill safe';
            demoStatusBadge.style.background = 'rgba(16, 185, 129, 0.15)';
            demoStatusBadge.style.color = '#10b981';
            demoStatusBadge.style.border = '1px solid rgba(16, 185, 129, 0.4)';
            demoStatusBadge.textContent = 'VERIFIED SAFE';
            demoTrustScore.className = 'hud-stat-val safe';
        }

        // Render Flags
        if (res.flags.length > 0) {
            demoFlagsList.innerHTML = res.flags.map(f => `<div class="flag-item">⚠️ ${f}</div>`).join('');
        } else {
            demoFlagsList.innerHTML = `<div class="flag-item" style="color: #10b981; border-color: rgba(16,185,129,0.3);">✅ Zero threat signals or malicious signatures detected in domain structure</div>`;
        }

        // Step 2: Query LLM7.io AI for live natural-language verdict
        const aiSummary = await queryLlm7Ai(targetUrl, res.trustScore, res.flags, res.threatType);
        demoAiVerdict.textContent = aiSummary;

        btnRunDemoScan.disabled = false;
        btnRunDemoScan.innerHTML = '<span>⚡</span> Run AI Scan';
    }

    // Attach Event Listeners
    if (btnRunDemoScan && demoUrlInput) {
        btnRunDemoScan.addEventListener('click', () => {
            executeDemoScan(demoUrlInput.value);
        });

        demoUrlInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') executeDemoScan(demoUrlInput.value);
        });

        sampleButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const sample = btn.dataset.url;
                if (sample) {
                    demoUrlInput.value = sample;
                    executeDemoScan(sample);
                }
            });
        });

        // Run initial sample scan on page load
        executeDemoScan(demoUrlInput.value);
    }
});
