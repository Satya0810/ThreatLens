import { Resend } from 'resend';

// Initialize Resend with Vercel environment variable
const resend = new Resend(process.env.RESEND_API_KEY);

export default async function handler(req, res) {
  // CORS setup
  res.setHeader('Access-Control-Allow-Credentials', true);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version');

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const { email, otp } = req.body;

  if (!email || !otp) {
    return res.status(400).json({ error: 'Missing email or otp' });
  }

  try {
    const data = await resend.emails.send({
      from: 'ThreatLens <onboarding@resend.dev>',
      to: email,
      subject: 'Your ThreatLens OTP Code',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f8f9fa; border-radius: 10px;">
          <h2 style="color: #6C5CE7; margin-bottom: 20px;">ThreatLens Authentication</h2>
          <p style="font-size: 16px; color: #555;">Your one-time password (OTP) to securely log in is:</p>
          <div style="background: white; padding: 15px; border-radius: 8px; text-align: center; margin: 20px 0; border: 1px solid #eee;">
            <h1 style="letter-spacing: 8px; color: #2d3436; margin: 0; font-size: 32px;">${otp}</h1>
          </div>
          <p style="font-size: 14px; color: #888;">This code is valid for 5 minutes. Do not share it with anyone.</p>
        </div>
      `
    });

    res.status(200).json({ success: true, data });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
}
