package com.safeqr.scanner.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrDataParserIntegrationTest {

    @Test
    fun testVCardParsing() {
        val payload = "BEGIN:VCARD\nVERSION:3.0\nN:John Doe\nFN:John Doe\nTEL:1234567890\nEMAIL:john@doe.com\nORG:ThreatLens\nEND:VCARD"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.VCARD, parsed.type)
        assertEquals("John Doe", parsed.primaryText)
        assertEquals("1234567890 | john@doe.com | ThreatLens", parsed.secondaryText)
    }

    @Test
    fun testLocationParsing() {
        val payload = "geo:12.345,67.890"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.LOCATION, parsed.type)
        assertEquals("Map Coordinates", parsed.primaryText)
        assertEquals("Lat: 12.345 | Lng: 67.890", parsed.secondaryText)
    }

    @Test
    fun testWifiParsing() {
        val payload = "WIFI:S:MySecretNetwork;T:WPA;P:SuperSecret123;;"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.WIFI, parsed.type)
        assertEquals("MySecretNetwork", parsed.primaryText)
        assertEquals("Security: WPA", parsed.secondaryText)
    }

    @Test
    fun testEmailParsing() {
        val payload = "mailto:test@example.com?subject=Hello%20World&body=Test%20Body"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.EMAIL, parsed.type)
        assertEquals("test@example.com", parsed.primaryText)
        assertEquals("Subject: Hello World", parsed.secondaryText)
    }

    @Test
    fun testSmsParsing() {
        val payload = "smsto:+1234567890:Hello there"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.SMS, parsed.type)
        assertEquals("+1234567890", parsed.primaryText)
        assertEquals("Hello there", parsed.secondaryText)
    }

    @Test
    fun testPaymentParsing() {
        val payload = "upi://pay?pa=test@upi&pn=TestUser&am=500.00&cu=INR"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.PAYMENT, parsed.type)
        assertEquals("Pay: TestUser", parsed.primaryText)
        assertEquals("Amount: ₹500.00", parsed.secondaryText)
    }

    @Test
    fun testCryptoParsing() {
        val payload = "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?amount=0.1"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.CRYPTO, parsed.type)
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", parsed.primaryText)
        assertEquals("Amount: 0.1", parsed.secondaryText)
    }

    @Test
    fun testEventParsing() {
        val payload = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nSUMMARY:Meeting\nDESCRIPTION:Important meeting\nDTSTART:20230101T120000Z\nDTEND:20230101T130000Z\nLOCATION:Office\nEND:VEVENT\nEND:VCALENDAR"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.EVENT, parsed.type)
        assertEquals("Meeting", parsed.primaryText)
        assertEquals("20230101T120000Z | Office", parsed.secondaryText)
    }

    @Test
    fun testTicketParsing() {
        val payload = "threatlens://ticket?id=TKT-1234&sig=mockhash"
        val parsed = QrDataParser.parse(payload)
        assertEquals(QrDataType.TICKET, parsed.type)
        assertEquals("Ticket ID: TKT-1234", parsed.primaryText)
        assertEquals("Secure Gatekeeper Pass", parsed.secondaryText)
    }
}
