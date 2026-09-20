// QrEncryptionEngine.js
// AES-256-GCM Password-Based Decryption matching ThreatLens Android QrEncryptionEngine.kt

export class QrEncryptionEngine {
  static LOCK_SCHEME = "threatlenslock://";
  static LOCK_VERSION = "v1";
  static PBKDF2_ITERATIONS = 10000;
  static KEY_LENGTH_BITS = 256;
  static GCM_TAG_LENGTH_BITS = 128;

  /**
   * Checks if string is a ThreatLens locked QR code
   */
  static isLockedQr(content) {
    return Boolean(content && typeof content === 'string' && content.trim().startsWith(this.LOCK_SCHEME));
  }

  /**
   * Converts base64 / base64url string to Uint8Array
   */
  static base64ToBytes(b64) {
    // Convert URL-safe base64 to standard base64
    let standardB64 = b64.replace(/-/g, '+').replace(/_/g, '/');
    while (standardB64.length % 4 !== 0) {
      standardB64 += '=';
    }
    const bin = atob(standardB64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      bytes[i] = bin.charCodeAt(i);
    }
    return bytes;
  }

  /**
   * Derives AES-GCM CryptoKey from password and salt using PBKDF2
   */
  static async deriveKey(password, saltBytes) {
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      enc.encode(password),
      "PBKDF2",
      false,
      ["deriveKey"]
    );

    return crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        salt: saltBytes,
        iterations: this.PBKDF2_ITERATIONS,
        hash: "SHA-256"
      },
      keyMaterial,
      { name: "AES-GCM", length: this.KEY_LENGTH_BITS },
      false,
      ["decrypt", "encrypt"]
    );
  }

  /**
   * Decrypts a password-locked QR code string.
   * @param {string} lockedContent e.g. "threatlenslock://v1:{salt}:{iv}:{ciphertext}"
   * @param {string} password The password to try
   * @returns {Promise<{ success: boolean, content: string|null, error: string|null }>}
   */
  static async decrypt(lockedContent, password) {
    if (!this.isLockedQr(lockedContent)) {
      return { success: false, content: null, error: "Not a ThreatLens locked QR code" };
    }

    try {
      const payload = lockedContent.trim().substring(this.LOCK_SCHEME.length);
      const parts = payload.split(":");
      if (parts.length !== 4) {
        return { success: false, content: null, error: "Invalid locked QR format" };
      }

      const [version, saltB64, ivB64, ciphertextB64] = parts;
      if (version !== this.LOCK_VERSION) {
        return { success: false, content: null, error: `Unsupported version: ${version}` };
      }

      const salt = this.base64ToBytes(saltB64);
      const iv = this.base64ToBytes(ivB64);
      const ciphertext = this.base64ToBytes(ciphertextB64);

      const key = await this.deriveKey(password, salt);

      // In Web Crypto API AES-GCM, ciphertext includes the authentication tag at the end (16 bytes)
      const decryptedBuffer = await crypto.subtle.decrypt(
        {
          name: "AES-GCM",
          iv: iv,
          tagLength: this.GCM_TAG_LENGTH_BITS
        },
        key,
        ciphertext
      );

      const decryptedText = new TextDecoder().decode(decryptedBuffer);
      return {
        success: true,
        content: decryptedText,
        error: null
      };

    } catch (e) {
      // AES-GCM throws OperationError if password is wrong or ciphertext has been tampered with
      return {
        success: false,
        content: null,
        error: "Incorrect password or tampered payload."
      };
    }
  }
}
