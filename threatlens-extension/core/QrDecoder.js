// QrDecoder.js
// Handles client-side in-browser QR Code image extraction and decoding with multi-engine fallback

export class QrDecoder {

  /**
   * Decodes a QR code from an image source URL, HTMLImageElement, Canvas, Blob, or File.
   * @param {string|HTMLImageElement|HTMLCanvasElement|Blob|File|ImageData} source 
   * @returns {Promise<string|null>} Decoded QR content or null if no QR found.
   */
  static async decodeFromImage(source) {
    if (!source) return null;

    let canvas = null;
    let width = 0;
    let height = 0;

    try {
      if (source instanceof Blob || source instanceof File) {
        const bitmap = await createImageBitmap(source);
        width = bitmap.width;
        height = bitmap.height;
        canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(bitmap, 0, 0);
      } else if (typeof source === 'string') {
        const img = await this.loadImage(source);
        width = img.naturalWidth || img.width;
        height = img.naturalHeight || img.height;
        canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);
      } else if (source instanceof HTMLImageElement) {
        width = source.naturalWidth || source.width;
        height = source.naturalHeight || source.height;
        canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(source, 0, 0);
      } else if (source instanceof HTMLCanvasElement) {
        canvas = source;
        width = canvas.width;
        height = canvas.height;
      }
    } catch (e) {
      console.warn("Image preparation failed in QrDecoder:", e);
    }

    // 1. Try Native BarcodeDetector if canvas available
    if (canvas && 'BarcodeDetector' in window) {
      try {
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        const barcodes = await detector.detect(canvas);
        if (barcodes && barcodes.length > 0 && barcodes[0].rawValue) {
          return barcodes[0].rawValue;
        }
      } catch (e) {}
    }

    // 2. Try jsQR engine on canvas ImageData
    if (canvas && width > 0 && height > 0) {
      try {
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        const imgData = ctx.getImageData(0, 0, width, height);
        const jsQR = typeof window !== 'undefined' ? window.jsQR : (typeof self !== 'undefined' ? self.jsQR : null);
        if (jsQR && imgData && imgData.data) {
          const code = jsQR(imgData.data, width, height, { inversionAttempts: "dontInvert" }) ||
                       jsQR(imgData.data, width, height, { inversionAttempts: "onlyInvert" }) ||
                       jsQR(imgData.data, width, height, { inversionAttempts: "attemptBoth" });
          if (code && code.data) {
            return code.data;
          }
        }
      } catch (e) {}
    }

    // 3. Fallback via background service worker for remote URLs
    if (typeof source === 'string' && source.startsWith('http') && typeof chrome !== 'undefined' && chrome.runtime?.sendMessage) {
      try {
        const res = await new Promise(resolve => {
          chrome.runtime.sendMessage({ action: "DECODE_IMAGE_URL", url: source }, resolve);
        });
        if (res && res.success && res.payload) {
          return res.payload;
        }
      } catch (e) {}
    }

    return null;
  }

  static loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "Anonymous";
      img.onload = () => resolve(img);
      img.onerror = (e) => reject(new Error("Failed to load image for QR decoding"));
      img.src = src;
    });
  }
}
