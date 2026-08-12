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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BulkQrItem(
    val id: String,
    val content: String,
    val label: String,
    val subLabel: String? = null
)

object BulkQrManager {

    /**
     * Parses a CSV string and returns a list of CloudEventTicket objects (legacy for tickets).
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
     * Parses generic CSV data (Content, Label, SubLabel) into a list of BulkQrItem.
     */
    fun parseCsvToGenericItems(csvData: String): List<BulkQrItem> {
        val items = mutableListOf<BulkQrItem>()
        val lines = csvData.trim().lines()
        for (line in lines) {
            if (line.isBlank() || line.lowercase().startsWith("content,label")) continue
            val parts = line.split(",").map { it.trim() }
            if (parts.isNotEmpty()) {
                val content = parts[0]
                val label = if (parts.size > 1) parts[1] else "Item ${items.size + 1}"
                val subLabel = if (parts.size > 2) parts[2] else null
                items.add(
                    BulkQrItem(
                        id = UUID.randomUUID().toString().take(6).uppercase(),
                        content = content,
                        label = label,
                        subLabel = subLabel
                    )
                )
            }
        }
        return items
    }

    /**
     * Generates a multi-page PDF containing generic QR codes.
     */
    suspend fun generateGenericPdf(
        context: Context,
        batchName: String,
        items: List<BulkQrItem>,
        colorTheme: QrColorTheme,
        bgStyle: QrBgStyle,
        dotStyle: QrDotStyle,
        eyeStyle: QrEyeStyle,
        logo: QrLogo
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val cols = 2
            val rows = 3
            val qrsPerPage = cols * rows
            val marginX = 50f
            val marginY = 50f
            val qrSize = 200
            val cellWidth = (pageWidth - 2 * marginX) / cols
            val cellHeight = (pageHeight - 2 * marginY) / rows

            val textPaint = Paint().apply {
                isAntiAlias = true; color = android.graphics.Color.BLACK; textSize = 12f
                textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val titlePaint = Paint().apply {
                isAntiAlias = true; color = android.graphics.Color.BLACK; textSize = 18f
                textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val subTextPaint = Paint().apply {
                isAntiAlias = true; color = android.graphics.Color.DKGRAY; textSize = 9f
                textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }

            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null

            for ((index, item) in items.withIndex()) {
                val pageIndex = index / qrsPerPage
                val indexOnPage = index % qrsPerPage

                if (indexOnPage == 0) {
                    currentPage?.let { pdfDocument.finishPage(it) }
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    canvas.drawText(batchName.uppercase(), pageWidth / 2f, marginY / 2f + 10f, titlePaint)
                }

                canvas?.let { c ->
                    val col = indexOnPage % cols
                    val row = indexOnPage / cols
                    val centerX = marginX + col * cellWidth + cellWidth / 2f
                    val centerY = marginY + row * cellHeight + cellHeight / 2f

                    val qrBitmap = CustomQrGenerator.generate(
                        content = item.content,
                        logo = logo, colorTheme = colorTheme,
                        dotStyle = dotStyle, eyeStyle = eyeStyle, bgStyle = bgStyle,
                        size = qrSize, frameText = null
                    )
                    c.drawBitmap(qrBitmap, centerX - qrSize / 2f, centerY - qrSize / 2f - 15f, null)
                    c.drawText(item.label, centerX, centerY + qrSize / 2f + 10f, textPaint)
                    if (item.subLabel != null) {
                        c.drawText(item.subLabel, centerX, centerY + qrSize / 2f + 25f, subTextPaint)
                    }
                }
            }
            currentPage?.let { pdfDocument.finishPage(it) }

            savePdfToDownloads(context, pdfDocument, "Batch_${batchName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Generates a ZIP file of PNGs for generic bulk QR items.
     */
    suspend fun generateGenericZip(
        context: Context,
        batchName: String,
        items: List<BulkQrItem>,
        colorTheme: QrColorTheme,
        bgStyle: QrBgStyle,
        dotStyle: QrDotStyle,
        eyeStyle: QrEyeStyle,
        logo: QrLogo
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        try {
            val fileName = "Batch_${batchName.replace(" ", "_")}_${System.currentTimeMillis()}.zip"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val zipFile = File(downloadsDir, fileName)
            
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for ((index, item) in items.withIndex()) {
                    val qrBitmap = CustomQrGenerator.generate(
                        content = item.content,
                        logo = logo, colorTheme = colorTheme,
                        dotStyle = dotStyle, eyeStyle = eyeStyle, bgStyle = bgStyle,
                        size = 800, frameText = item.label
                    )
                    val outStream = java.io.ByteArrayOutputStream()
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    val bytes = outStream.toByteArray()
                    
                    val entryName = "${item.id}_${item.label.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.png"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Legacy: Generates a multi-page PDF containing QR codes for the provided tickets.
     */
    suspend fun generatePdfTickets(
        context: Context,
        eventName: String,
        tickets: List<CloudEventTicket>,
        colorTheme: QrColorTheme,
        bgStyle: QrBgStyle
    ): Boolean = withContext(Dispatchers.IO) {
        val genericItems = tickets.map { ticket ->
            val payload = "threatlens://ticket?id=${ticket.ticketId}&sig=${ticket.signatureHash}"
            BulkQrItem(ticket.ticketId, payload, ticket.attendeeName, ticket.ticketTier)
        }
        generateGenericPdf(context, eventName, genericItems, colorTheme, bgStyle, QrDotStyle.ROUNDED, QrEyeStyle.CYBER_HEX, QrLogo.THREATLENS)
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
