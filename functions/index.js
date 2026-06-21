const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { Resend } = require("resend");

admin.initializeApp();
const db = admin.firestore();

// IMPORTANT: Replace this with your actual Resend API Key
const RESEND_API_KEY = "re_DUFAB8ye_9Z8znK1TBUauRgDUpnsSjMQQ";
const resend = new Resend(RESEND_API_KEY);

exports.sendOtpEmail = functions.https.onRequest(async (req, res) => {
  // Allow CORS
  res.set("Access-Control-Allow-Origin", "*");
  if (req.method === "OPTIONS") {
    res.set("Access-Control-Allow-Methods", "POST");
    res.set("Access-Control-Allow-Headers", "Content-Type");
    res.status(204).send("");
    return;
  }

  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method Not Allowed" });
  }

  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ error: "Email is required" });
  }

  try {
    // Generate 6-digit OTP
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes

    // Save to Firestore
    await db.collection("otps").doc(email).set({
      otp: otp,
      expiresAt: expiresAt
    });

    // Send email using Resend
    const { data, error } = await resend.emails.send({
      from: "ThreatLens Security <onboarding@resend.dev>", // Using Resend dev domain for testing
      to: [email],
      subject: "Your ThreatLens Verification Code",
      html: `
        <div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto;">
          <h2 style="color: #4A90E2;">Welcome to ThreatLens</h2>
          <p>Your one-time verification code is:</p>
          <div style="background-color: #f4f4f4; padding: 15px; border-radius: 8px; text-align: center; margin: 20px 0;">
            <h1 style="letter-spacing: 5px; margin: 0; color: #333;">${otp}</h1>
          </div>
          <p style="color: #666; font-size: 14px;">This code will expire in 5 minutes.</p>
          <hr style="border: none; border-top: 1px solid #eaeaea; margin: 20px 0;" />
          <p style="color: #999; font-size: 12px; text-align: center;">If you didn't request this code, you can safely ignore this email.</p>
        </div>
      `
    });

    if (error) {
      console.error("Resend error:", error);
      return res.status(500).json({ error: "Failed to send email" });
    }

    return res.status(200).json({ success: true, message: "OTP sent successfully" });
  } catch (error) {
    console.error("Server error:", error);
    return res.status(500).json({ error: "Internal server error" });
  }
});
