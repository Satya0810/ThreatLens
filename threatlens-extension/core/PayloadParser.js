// PayloadParser.js
// Port of ThreatLens Android QrDataParser.kt

export class PayloadParser {
  
  static parse(rawString) {
    if (!rawString || typeof rawString !== 'string') {
      return { type: 'UNKNOWN', rawContent: rawString || '', isInteractive: false };
    }

    const str = rawString.trim();
    const lower = str.toLowerCase();

    // 0. ThreatLens Certified QR Envelope
    if (lower.startsWith('threatlenscert://')) {
      try {
        const base64 = str.substring('threatlenscert://'.length);
        const jsonStr = atob(base64);
        const certData = JSON.parse(jsonStr);
        
        // Recursively parse the inner true payload!
        const innerParsed = this.parse(certData.content);
        
        // Add certification metadata
        innerParsed.isCertified = true; 
        innerParsed.certScore = certData.score;
        innerParsed.certStatus = certData.status;
        innerParsed.certId = certData.id;
        innerParsed.isTampered = false; 
        
        return innerParsed;
      } catch (e) {
        // Fallthrough if parsing fails
      }
    }

    // 0.5. Password-Protected QR
    if (lower.startsWith('threatlenslock://')) {
      return {
        type: 'LOCKED',
        rawContent: str,
        isInteractive: true,
        actionData: { locked: true },
        displayLabel: '🔒 Password-Protected QR'
      };
    }

    // 1. UPI Payment
    if (lower.startsWith('upi://') || lower.startsWith('paytm://') || lower.startsWith('phonepe://') || lower.startsWith('gpay://') || lower.startsWith('bhim://')) {
      const upiData = this._parseUpi(str);
      return {
        type: 'UPI',
        rawContent: str,
        isInteractive: true,
        actionData: upiData,
        displayLabel: `UPI Payment: ${upiData.pn || upiData.pa || 'Direct Pay'}`
      };
    }

    // 2. Wi-Fi Config (e.g. WIFI:S:MyNetwork;T:WPA;P:Secret;H:false;;)
    if (str.startsWith('WIFI:') || str.startsWith('wifi:')) {
      const wifiData = this._parseWifi(str);
      return {
        type: 'WIFI',
        rawContent: str,
        isInteractive: true,
        actionData: wifiData,
        displayLabel: `WiFi Network: ${wifiData.ssid || 'Hidden'}`
      };
    }

    // 3. W3C Verifiable Credential
    if (lower.startsWith('threatlensvc://') || (str.startsWith('{') && str.includes('verifiableCredential'))) {
      return {
        type: 'CREDENTIAL',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Verifiable Digital Credential'
      };
    }

    // 4. Crypto Addresses
    if (lower.startsWith('bitcoin:') || lower.startsWith('ethereum:') || lower.startsWith('solana:') || /^0x[a-fA-F0-9]{40}$/.test(str)) {
      return {
        type: 'CRYPTO',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Cryptocurrency Wallet Address'
      };
    }

    // 5. Contact VCard / MeCard
    if (lower.startsWith('begin:vcard') || lower.startsWith('mecard:')) {
      return {
        type: 'VCARD',
        rawContent: str,
        isInteractive: true,
        displayLabel: 'Contact Card (vCard)'
      };
    }

    // 6. Direct Web URLs
    if (lower.startsWith('http://') || lower.startsWith('https://') || lower.startsWith('www.')) {
      const normalized = lower.startsWith('www.') ? `https://${str}` : str;
      return {
        type: 'URL',
        rawContent: normalized,
        isInteractive: true,
        displayLabel: normalized
      };
    }

    // Check if valid URL without explicit scheme
    try {
      const parsedUrl = new URL(str);
      if (parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:') {
        return {
          type: 'URL',
          rawContent: str,
          isInteractive: true,
          displayLabel: str
        };
      }
    } catch (e) {}

    // 7. Email, Phone, SMS
    if (lower.startsWith('mailto:') || (str.includes('@') && !str.includes(' ') && !str.includes('/'))) {
      const email = lower.startsWith('mailto:') ? str.substring(7) : str;
      return {
        type: 'EMAIL',
        rawContent: `mailto:${email}`,
        isInteractive: true,
        displayLabel: `Email: ${email}`
      };
    }

    if (lower.startsWith('tel:') || /^[+]?[0-9]{7,15}$/.test(str.replace(/[\s-]/g, ''))) {
      const phone = str.replace(/[^0-9+]/g, '');
      return {
        type: 'PHONE',
        rawContent: `tel:${phone}`,
        isInteractive: true,
        displayLabel: `Phone: ${phone}`
      };
    }

    // Default: Plain Text
    return {
      type: 'TEXT',
      rawContent: str,
      isInteractive: false,
      displayLabel: str.length > 50 ? str.substring(0, 47) + '...' : str
    };
  }

  static _parseUpi(str) {
    let query = '';
    if (str.includes('?')) {
      query = str.substring(str.indexOf('?') + 1);
    }
    const params = new URLSearchParams(query);
    return {
      pa: params.get('pa') || '',
      pn: params.get('pn') || '',
      am: params.get('am') || null,
      cu: params.get('cu') || 'INR',
      tn: params.get('tn') || '',
      mc: params.get('mc') || ''
    };
  }

  static _parseWifi(str) {
    const getField = (prefix) => {
      const match = str.match(new RegExp(`${prefix}:(.*?)(?=;|$)`));
      return match ? match[1] : '';
    };

    return {
      ssid: getField('S'),
      security: getField('T') || 'WPA',
      password: getField('P'),
      hidden: getField('H')
    };
  }
}
