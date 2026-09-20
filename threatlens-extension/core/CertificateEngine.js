// CertificateEngine.js
// Cryptographic certificate embed + verify for ThreatLens QR codes matching Android CertificateEngine.kt

export class CertificateEngine {
  static CERT_SCHEME = "threatlenscert://";
  static SIGNING_KEY = "ThreatLens-S3cur3-S1gn1ng-K3y-2025!@#";

  /**
   * Generates a deterministic 8-char hex certificate ID from the content
   */
  static async generateCertId(content) {
    try {
      const enc = new TextEncoder();
      const hashBuffer = await crypto.subtle.digest('SHA-256', enc.encode(content));
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const hex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
      return hex.substring(0, 8).toUpperCase();
    } catch (e) {
      return Math.random().toString(16).substring(2, 10).toUpperCase();
    }
  }

  /**
   * Builds the canonical string representation for HMAC signing:
   * "${unsigned.id}|${unsigned.content}|${unsigned.status}|${unsigned.score}|${unsigned.ts}"
   */
  static canonicalString(payload) {
    return `${payload.id}|${payload.content}|${payload.status}|${payload.score}|${payload.ts}`;
  }

  /**
   * Signs canonical string using HMAC-SHA256 and returns Base64 string
   */
  static async sign(dataString) {
    const enc = new TextEncoder();
    const keyData = enc.encode(this.SIGNING_KEY);
    const key = await crypto.subtle.importKey(
      'raw',
      keyData,
      { name: 'HMAC', hash: { name: 'SHA-256' } },
      false,
      ['sign', 'verify']
    );
    const signatureBuffer = await crypto.subtle.sign('HMAC', key, enc.encode(dataString));
    const bytes = new Uint8Array(signatureBuffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  static utf8ToBase64(str) {
    const bytes = new TextEncoder().encode(str);
    let bin = '';
    for (let i = 0; i < bytes.length; i++) {
      bin += String.fromCharCode(bytes[i]);
    }
    return btoa(bin);
  }

  static base64ToUtf8(base64) {
    const bin = atob(base64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      bytes[i] = bin.charCodeAt(i);
    }
    return new TextDecoder().decode(bytes);
  }

  /**
   * Builds a certified QR content string with embedded HMAC-SHA256 signature
   * @param {string} originalContent 
   * @param {string} safetyStatus 
   * @param {number} score 
   * @returns {Promise<string>} e.g. "threatlenscert://..."
   */
  static async buildCertifiedPayload(originalContent, safetyStatus = "SAFE", score = 100) {
    const certId = await this.generateCertId(originalContent);
    const ts = Date.now();

    const unsigned = {
      v: 1,
      id: certId,
      content: originalContent,
      status: safetyStatus,
      score: score,
      ts: ts
    };

    const signature = await this.sign(this.canonicalString(unsigned));
    const signed = { ...unsigned, sig: signature };
    const jsonStr = JSON.stringify(signed);
    const encoded = this.utf8ToBase64(jsonStr);
    return `${this.CERT_SCHEME}${encoded}`;
  }

  /**
   * Verifies any string that has the `threatlenscert://` prefix
   * @param {string} certString 
   * @returns {Promise<{ isValid: boolean, isTampered: boolean, payload: object|null, reason?: string }>}
   */
  static async verify(certString) {
    if (!certString || !certString.startsWith(this.CERT_SCHEME)) {
      return { isValid: false, isTampered: false, payload: null, reason: "Not a certified QR payload" };
    }

    try {
      const base64 = certString.substring(this.CERT_SCHEME.length);
      const jsonStr = this.base64ToUtf8(base64);
      const payload = JSON.parse(jsonStr);

      if (!payload || !payload.sig || !payload.id || !payload.content) {
        return { isValid: false, isTampered: true, payload: null, reason: "Malformed certificate fields" };
      }

      const expectedSig = await this.sign(this.canonicalString(payload));
      if (expectedSig !== payload.sig) {
        return { isValid: false, isTampered: true, payload: payload, reason: "Cryptographic signature mismatch (Tampered QR)" };
      }

      return {
        isValid: true,
        isTampered: false,
        payload: payload,
        certId: payload.id,
        status: payload.status,
        score: payload.score,
        content: payload.content,
        timestamp: payload.ts
      };
    } catch (e) {
      return { isValid: false, isTampered: true, payload: null, reason: `Failed to decode certificate: ${e.message}` };
    }
  }
}
