package com.safeqr.scanner.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.safeqr.scanner.data.model.CloudEventTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object BulkTicketManager {

    /**
     * Parses a CSV string and returns a list of CloudEventTicket objects.
     * Expected CSV format: AttendeeName,TicketTier (optional)
     */
    fun parseCsvToTickets(eventId: String, csvData: String): List<CloudEventTicket> {
        val tickets = mutableListOf<CloudEventTicket>()
        val lines = csvData.trim().lines()
        
        for (line in lines) {
            if (line.isBlank() || line.startsWith("Name,Tier")) continue
            val parts = line.split(",").map { it.trim() }
            if (parts.isNotEmpty()) {
                val name = parts[0]
                val tier = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "Standard"
                
                // Generate a unique ticket ID
                val shortId = UUID.randomUUID().toString().take(8).uppercase()
                val ticketId = "TKT-$eventId-$shortId"
                
                val timeSlice = System.currentTimeMillis() / 30000
                val raw = "$ticketId:$timeSlice"
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .take(8)

                tickets.add(
                    CloudEventTicket(
                        ticketId = ticketId,
                        eventId = eventId,
                        attendeeId = UUID.randomUUID().toString(),
                        attendeeName = name,
                        ticketTier = tier,
                        signatureHash = hash,
                        isScanned = false
                    )
                )
            }
        }
        return tickets
    }

    /**
     * Generates a multi-page PDF containing QR codes for the provided tickets.
     */
    suspend fun generatePdfTickets(
        context: Context,
        eventName: String,
        tickets: List<CloudEventTicket>,
        colorTheme: QrColorTheme,
        bgStyle: QrBgStyle
    ): Boolean = withContext(Dispatchers.IO) {
        if (tickets.isEmpty()) return@withContext false

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width at 72 PPI
            val pageHeight = 842 // A4 height at 72 PPI
            
            val cols = 2
            val rows = 3
            val qrsPerPage = cols * rows
            
            val marginX = 50f
            val marginY = 50f
            val qrSize = 200
            
            val cellWidth = (pageWidth - 2 * marginX) / cols
            val cellHeight = (pageHeight - 2 * marginY) / rows

            val textPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
                textSize = 12f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            
            val titlePaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
                textSize = 18f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }

            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null

            for ((index, ticket) in tickets.withIndex()) {
                val pageIndex = index / qrsPerPage
                val indexOnPage = index % qrsPerPage

                if (indexOnPage == 0) {
                    if (currentPage != null) {
                        pdfDocument.finishPage(currentPage)
                    }
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    
                    // Draw Event Name Header
                    canvas.drawText(eventName.uppercase(), pageWidth / 2f, marginY / 2f + 10f, titlePaint)
                }

                if (canvas != null) {
                    val col = indexOnPage % cols
                    val row = indexOnPage / cols
                    
                    val centerX = marginX + col * cellWidth + cellWidth / 2f
                    val centerY = marginY + row * cellHeight + cellHeight / 2f
                    
                    // Generate payload
                    val payload = "threatlens://ticket?id=${ticket.ticketId}&sig=${ticket.signatureHash}"
                    
                    // Generate QR Bitmap using CustomQrGenerator
                    val qrBitmap = CustomQrGenerator.generate(
                        content = payload,
                        logo = QrLogo.THREATLENS,
                        colorTheme = colorTheme,
                        dotStyle = QrDotStyle.ROUNDED,
                        eyeStyle = QrEyeStyle.CYBER_HEX,
                        bgStyle = bgStyle,
                        size = qrSize,
                        frameText = ticket.ticketTier
                    )
                    
                    // Draw QR
                    canvas.drawBitmap(qrBitmap, centerX - qrSize / 2f, centerY - qrSize / 2f - 15f, null)
                    
                    // Draw Attendee Name
                    canvas.drawText(ticket.attendeeName, centerX, centerY + qrSize / 2f + 10f, textPaint)
                    
                    // Draw Ticket ID
                    textPaint.textSize = 9f
                    canvas.drawText(ticket.ticketId, centerX, centerY + qrSize / 2f + 25f, textPaint)
                    textPaint.textSize = 12f // Reset
                }
            }

            if (currentPage != null) {
                pdfDocument.finishPage(currentPage)
            }

            // Save PDF
            savePdfToDownloads(context, pdfDocument, "Tickets_${eventName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun savePdfToDownloads(context: Context, pdfDocument: PdfDocument, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri).use { out ->
                    if (out != null) {
                        pdfDocument.writeTo(out)
                    }
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
        }
    }
}
