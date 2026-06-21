package com.safeqr.scanner.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

enum class QrLogo(val label: String) {
    NONE("None"),
    THREATLENS("ThreatLens Shield"),
    SMART_ICON("Smart Brand Logo"),
    CUSTOM_IMAGE("Custom Image")
}

enum class QrColorTheme(val label: String, val colors: List<Int>) {
    NEON_CYAN("Neon Cyan", listOf(0xFF00F0FF.toInt(), 0xFF00F0FF.toInt())),
    ELECTRIC_PURPLE("Electric Purple", listOf(0xFFFF00FF.toInt(), 0xFFFF00FF.toInt())),
    LIME_GREEN("Lime Green", listOf(0xFF39FF14.toInt(), 0xFF39FF14.toInt())),
    CYBER_GRADIENT("Cyber Gradient", listOf(0xFF00F0FF.toInt(), 0xFFFF00FF.toInt())),
    TRON_GRADIENT("Tron Gradient", listOf(0xFF00F0FF.toInt(), 0xFF39FF14.toInt())),
    VOLCANO_GRADIENT("Volcano Gradient", listOf(0xFFFF003C.toInt(), 0xFFFFB800.toInt())),
    MATRIX_GRADIENT("Matrix Gradient", listOf(0xFF39FF14.toInt(), 0xFF004411.toInt())),
    GOLD_GRADIENT("Golden Luxe", listOf(0xFFFFD700.toInt(), 0xFFFF8C00.toInt())),
    OCEAN_GRADIENT("Deep Ocean", listOf(0xFF0080FF.toInt(), 0xFF00F0FF.toInt())),
    ROSE_GRADIENT("Rose Fire", listOf(0xFFFF69B4.toInt(), 0xFFFF1493.toInt()))
}

enum class QrDotStyle(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    HEXAGON("Hexagon"),
    CROSSES("Matrix Crosses"),
    DIAMONDS("Diamonds")
}

enum class QrEyeStyle(val label: String) {
    SQUARE("Classic Square"),
    ROUNDED("Sleek Rounded"),
    CYBER_HEX("Cyber Octagon"),
    GLOW_SHIELD("Glow Shield")
}

enum class QrBgStyle(val label: String) {
    LIGHT("Solid Light"),
    DARK("Cyber Dark"),
    TRANSPARENT("Transparent"),
    GRADIENT_BG("Neon Vignette")
}

object CustomQrGenerator {

    /**
     * Generates a fully styled and customized QR code as a Bitmap.
     */
    fun generate(
        content: String,
        logo: QrLogo,
        colorTheme: QrColorTheme,
        dotStyle: QrDotStyle,
        eyeStyle: QrEyeStyle,
        bgStyle: QrBgStyle,
        customImage: Bitmap? = null,
        size: Int = 800,
        frameText: String? = null
    ): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 2

        // Always encode with ErrorCorrectionLevel H to ensure we have spare capacity for the central logo cutout
        val qrCode = Encoder.encode(content, ErrorCorrectionLevel.H, hints)
        val matrix = qrCode.matrix
        val matrixSize = matrix.width
        val moduleSize = size.toFloat() / matrixSize

        val baseSize = size
        val framePadding = if (!frameText.isNullOrBlank()) baseSize / 5 else 0
        val totalHeight = baseSize + framePadding

        val bmp = Bitmap.createBitmap(baseSize, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Draw Background
        val bgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val resolveBgColor = when (bgStyle) {
            QrBgStyle.LIGHT -> AndroidColor.WHITE
            QrBgStyle.DARK -> 0xFF030509.toInt() // DarkBackground color from Theme
            QrBgStyle.TRANSPARENT -> AndroidColor.TRANSPARENT
            QrBgStyle.GRADIENT_BG -> 0xFF030509.toInt() // Will draw radial gradient separately
        }
        bgPaint.color = resolveBgColor
        
        if (!frameText.isNullOrBlank()) {
            val cornerRadius = baseSize * 0.05f
            canvas.drawRoundRect(0f, 0f, baseSize.toFloat(), totalHeight.toFloat(), cornerRadius, cornerRadius, bgPaint)
        } else {
            canvas.drawRect(0f, 0f, baseSize.toFloat(), baseSize.toFloat(), bgPaint)
        }

        // For GRADIENT_BG, paint a subtle radial vignette over the solid dark base
        if (bgStyle == QrBgStyle.GRADIENT_BG) {
            val radialPaint = Paint().apply {
                isAntiAlias = true
                shader = android.graphics.RadialGradient(
                    baseSize / 2f, baseSize / 2f,
                    baseSize * 0.8f,
                    intArrayOf(0xFF0A0F1A.toInt(), 0xFF030509.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, baseSize.toFloat(), baseSize.toFloat(), radialPaint)
        }

        // Determine coordinates for central Logo Zone (7x7 modules area)
        val half = matrixSize / 2
        val logoRadius = 3 // 7x7 modules cutout
        val hasLogo = logo != QrLogo.NONE

        // Setup Paint for QR code foreground pixels
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Apply Cyan/Magenta/Green color themes or gradients
        if (colorTheme.colors[0] == colorTheme.colors[1]) {
            paint.color = colorTheme.colors[0]
        } else {
            // Apply stunning gradient shader flowing from top-left to bottom-right
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                colorTheme.colors[0], colorTheme.colors[1],
                Shader.TileMode.CLAMP
            )
        }

        // 2. Iterate and draw active QR Modules (skip Eyes and Logo zones)
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                // Skip if inactive
                if (matrix.get(c, r).toInt() == 0) continue

                // Skip Finder patterns (three 7x7 squares in corners)
                if (isFinderPattern(r, c, matrixSize)) continue

                // Skip Logo Zone in the middle
                if (hasLogo && r in (half - logoRadius)..(half + logoRadius) && c in (half - logoRadius)..(half + logoRadius)) continue

                // Calculate pixel coordinates
                val mLeft = c * moduleSize
                val mTop = r * moduleSize
                val mRight = mLeft + moduleSize
                val mBottom = mTop + moduleSize
                val cx = mLeft + moduleSize / 2f
                val cy = mTop + moduleSize / 2f

                when (dotStyle) {
                    QrDotStyle.SQUARE -> {
                        canvas.drawRect(mLeft, mTop, mRight, mBottom, paint)
                    }
                    QrDotStyle.ROUNDED -> {
                        val radius = moduleSize * 0.46f
                        canvas.drawCircle(cx, cy, radius, paint)
                    }
                    QrDotStyle.HEXAGON -> {
                        val w = moduleSize * 0.48f
                        val h = moduleSize * 0.48f
                        val path = Path().apply {
                            moveTo(cx, cy - h)
                            lineTo(cx + w * 0.86f, cy - h * 0.5f)
                            lineTo(cx + w * 0.86f, cy + h * 0.5f)
                            lineTo(cx, cy + h)
                            lineTo(cx - w * 0.86f, cy + h * 0.5f)
                            lineTo(cx - w * 0.86f, cy - h * 0.5f)
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                    QrDotStyle.CROSSES -> {
                        val t = moduleSize * 0.22f
                        val len = moduleSize * 0.44f
                        canvas.drawRect(cx - t / 2f, cy - len, cx + t / 2f, cy + len, paint)
                        canvas.drawRect(cx - len, cy - t / 2f, cx + len, cy + t / 2f, paint)
                    }
                    QrDotStyle.DIAMONDS -> {
                        val half = moduleSize * 0.46f
                        val path = Path().apply {
                            moveTo(cx, cy - half)  // top
                            lineTo(cx + half, cy)  // right
                            lineTo(cx, cy + half)  // bottom
                            lineTo(cx - half, cy)  // left
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }

        // Setup separate paints for High-Res Eye outlines
        val eyePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = moduleSize
            if (colorTheme.colors[0] == colorTheme.colors[1]) {
                color = colorTheme.colors[0]
            } else {
                shader = paint.shader
            }
        }

        val eyeInnerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            if (colorTheme.colors[0] == colorTheme.colors[1]) {
                color = colorTheme.colors[0]
            } else {
                shader = paint.shader
            }
        }

        // 3. Draw the Three High-Resolution Corner Eyes
        val eyeRectTL = RectF(0f, 0f, 7 * moduleSize, 7 * moduleSize)
        val eyeRectTR = RectF((matrixSize - 7) * moduleSize, 0f, size.toFloat(), 7 * moduleSize)
        val eyeRectBL = RectF(0f, (matrixSize - 7) * moduleSize, 7 * moduleSize, size.toFloat())

        drawEye(canvas, eyeRectTL, eyeStyle, eyePaint, eyeInnerPaint, moduleSize)
        drawEye(canvas, eyeRectTR, eyeStyle, eyePaint, eyeInnerPaint, moduleSize)
        drawEye(canvas, eyeRectBL, eyeStyle, eyePaint, eyeInnerPaint, moduleSize)

        // 4. Draw Center Logo
        if (hasLogo) {
            val logoRect = RectF(
                (half - logoRadius) * moduleSize,
                (half - logoRadius) * moduleSize,
                (half + logoRadius + 1) * moduleSize,
                (half + logoRadius + 1) * moduleSize
            )

            // Auto-resolve Smart brand logo or ThreatLens
            val logoType = if (logo == QrLogo.THREATLENS) {
                "THREATLENS"
            } else {
                getSmartLogoType(content)
            }

            val pColor = colorTheme.colors[0] // Logo foreground matches first theme color
            val bColor = if (bgStyle == QrBgStyle.TRANSPARENT) {
                0xFF030509.toInt() // Transparents gets dark-backed shield
            } else {
                resolveBgColor
            }

            if (logo == QrLogo.CUSTOM_IMAGE && customImage != null) {
                // Draw custom image as logo
                drawCustomImageLogo(canvas, logoRect, customImage, bColor)
            } else {
                drawLogo(canvas, logoRect, logoType, pColor, bColor)
            }
        }

        // 5. Draw Frame Text
        if (!frameText.isNullOrBlank()) {
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = if (bgStyle == QrBgStyle.LIGHT) AndroidColor.BLACK else AndroidColor.WHITE
                textAlign = Paint.Align.CENTER
                textSize = framePadding * 0.35f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val textY = baseSize + (framePadding / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(frameText, baseSize / 2f, textY, textPaint)
        }

        return bmp
    }

    private fun isFinderPattern(r: Int, c: Int, matrixSize: Int): Boolean {
        if (r in 0..6 && c in 0..6) return true
        if (r in 0..6 && c >= matrixSize - 7) return true
        if (r >= matrixSize - 7 && c in 0..6) return true
        return false
    }

    private fun drawEye(canvas: Canvas, rect: RectF, eyeStyle: QrEyeStyle, paint: Paint, innerPaint: Paint, moduleSize: Float) {
        canvas.save()
        when (eyeStyle) {
            QrEyeStyle.SQUARE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = moduleSize
                val offset = moduleSize / 2f
                canvas.drawRect(
                    rect.left + offset, rect.top + offset,
                    rect.right - offset, rect.bottom - offset, paint
                )
                // Inner 3x3 core
                innerPaint.style = Paint.Style.FILL
                canvas.drawRect(
                    rect.left + 2 * moduleSize, rect.top + 2 * moduleSize,
                    rect.right - 2 * moduleSize, rect.bottom - 2 * moduleSize, innerPaint
                )
            }
            QrEyeStyle.ROUNDED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = moduleSize
                val offset = moduleSize / 2f
                val outerRadius = moduleSize * 2.2f
                val outlineRect = RectF(
                    rect.left + offset, rect.top + offset,
                    rect.right - offset, rect.bottom - offset
                )
                canvas.drawRoundRect(outlineRect, outerRadius, outerRadius, paint)

                // Inner 3x3 circle core
                innerPaint.style = Paint.Style.FILL
                val innerRect = RectF(
                    rect.left + 2 * moduleSize, rect.top + 2 * moduleSize,
                    rect.right - 2 * moduleSize, rect.bottom - 2 * moduleSize
                )
                canvas.drawRoundRect(innerRect, moduleSize * 1f, moduleSize * 1f, innerPaint)
            }
            QrEyeStyle.CYBER_HEX -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = moduleSize
                val offset = moduleSize / 2f
                val r = RectF(
                    rect.left + offset, rect.top + offset,
                    rect.right - offset, rect.bottom - offset
                )

                // Octagonal/chamfered frame
                val path = Path().apply {
                    val chamfer = moduleSize * 1.6f
                    moveTo(r.left + chamfer, r.top)
                    lineTo(r.right - chamfer, r.top)
                    lineTo(r.right, r.top + chamfer)
                    lineTo(r.right, r.bottom - chamfer)
                    lineTo(r.right - chamfer, r.bottom)
                    lineTo(r.left + chamfer, r.bottom)
                    lineTo(r.left, r.bottom - chamfer)
                    lineTo(r.left, r.top + chamfer)
                    close()
                }
                canvas.drawPath(path, paint)

                // Center is a neat solid circle core
                innerPaint.style = Paint.Style.FILL
                canvas.drawCircle(rect.centerX(), rect.centerY(), 1.5f * moduleSize, innerPaint)
            }
            QrEyeStyle.GLOW_SHIELD -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = moduleSize * 1.3f
                val offset = moduleSize * 0.65f
                val outlineRect = RectF(
                    rect.left + offset, rect.top + offset,
                    rect.right - offset, rect.bottom - offset
                )
                // Draw rounded outer ring
                canvas.drawRoundRect(outlineRect, moduleSize * 1.2f, moduleSize * 1.2f, paint)

                // Inner core is a cyberpunk diamond/shield shape
                innerPaint.style = Paint.Style.FILL
                val path = Path().apply {
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val w = 1.6f * moduleSize
                    moveTo(cx, cy - w)
                    lineTo(cx + w, cy)
                    lineTo(cx, cy + w)
                    lineTo(cx - w, cy)
                    close()
                }
                canvas.drawPath(path, innerPaint)
            }
        }
        canvas.restore()
    }

    private fun getSmartLogoType(content: String): String {
        val lower = content.lowercase()
        return when {
            lower.contains("instagram.com") -> "INSTAGRAM"
            lower.contains("linkedin.com") -> "LINKEDIN"
            lower.contains("github.com") -> "GITHUB"
            lower.contains("twitter.com") || lower.contains("x.com") -> "TWITTER"
            lower.contains("gmail.com") || lower.startsWith("mailto:") -> "EMAIL"
            lower.startsWith("tel:") -> "PHONE"
            lower.startsWith("smsto:") || lower.startsWith("sms:") -> "SMS"
            lower.startsWith("wifi:") -> "WIFI"
            lower.startsWith("upi:") -> "PAYMENT"
            else -> "GLOBE"
        }
    }

    private fun drawCustomImageLogo(canvas: Canvas, rect: RectF, bitmap: Bitmap, bgColor: Int) {
        val r = rect.width() / 2f
        val platePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = bgColor
        }
        canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, platePaint)
        
        // Scale bitmap to fit
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, (rect.width() * 0.8f).toInt(), (rect.height() * 0.8f).toInt(), true)
        
        val saveCount = canvas.save()
        // Clip to rounded rect
        val path = Path().apply {
            addRoundRect(
                RectF(rect.centerX() - scaledBitmap.width / 2f, rect.centerY() - scaledBitmap.height / 2f, 
                      rect.centerX() + scaledBitmap.width / 2f, rect.centerY() + scaledBitmap.height / 2f),
                r * 0.2f, r * 0.2f, Path.Direction.CW
            )
        }
        canvas.clipPath(path)
        canvas.drawBitmap(scaledBitmap, rect.centerX() - scaledBitmap.width / 2f, rect.centerY() - scaledBitmap.height / 2f, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(saveCount)
    }

    private fun drawLogo(canvas: Canvas, rect: RectF, logoType: String, primaryColor: Int, bgColor: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f

        // 1. Draw a clean backing plate to clear out any QR modules in the background
        val platePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = bgColor
        }
        // Draw backing plate
        canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, platePaint)

        // Draw outline border around the plate
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = r * 0.12f
            color = primaryColor
        }
        canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, borderPaint)

        // 2. Draw the actual logo icon inside
        val iconPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = r * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = primaryColor
        }

        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = primaryColor
        }

        val innerR = r * 0.55f // Scale icon within the plate

        when (logoType) {
            "THREATLENS" -> {
                // Draw a Cyber Shield logo
                val shieldPath = Path().apply {
                    val topY = cy - innerR
                    val botY = cy + innerR * 0.8f
                    val w = innerR * 0.8f
                    moveTo(cx, topY)
                    cubicTo(cx + w, topY, cx + w, cy - innerR * 0.2f, cx + w * 0.8f, cy + innerR * 0.4f)
                    lineTo(cx, botY)
                    lineTo(cx - w * 0.8f, cy + innerR * 0.4f)
                    cubicTo(cx - w, cy - innerR * 0.2f, cx - w, topY, cx, topY)
                    close()
                }
                canvas.drawPath(shieldPath, iconPaint)

                // Glowing 'T' in shield
                val tPaint = Paint(iconPaint).apply { strokeWidth = r * 0.15f }
                canvas.drawLine(cx - innerR * 0.35f, cy - innerR * 0.25f, cx + innerR * 0.35f, cy - innerR * 0.25f, tPaint)
                canvas.drawLine(cx, cy - innerR * 0.25f, cx, cy + innerR * 0.35f, tPaint)
            }
            "INSTAGRAM" -> {
                val cameraRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                canvas.drawRoundRect(cameraRect, innerR * 0.35f, innerR * 0.35f, iconPaint)
                canvas.drawCircle(cx, cy, innerR * 0.4f, iconPaint)
                canvas.drawCircle(cx + innerR * 0.45f, cy - innerR * 0.45f, innerR * 0.08f, fillPaint)
            }
            "LINKEDIN" -> {
                val cardRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
                canvas.drawRoundRect(cardRect, innerR * 0.25f, innerR * 0.25f, fillPaint)

                val textPaint = Paint().apply {
                    isAntiAlias = true
                    color = bgColor
                    textSize = innerR * 1.35f
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val fontMetrics = textPaint.fontMetrics
                val baseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText("in", cx, baseline, textPaint)
            }
            "GITHUB" -> {
                val catPath = Path().apply {
                    moveTo(cx - innerR * 0.3f, cy + innerR * 0.7f)
                    cubicTo(cx - innerR * 0.8f, cy + innerR * 0.6f, cx - innerR * 0.8f, cy - innerR * 0.3f, cx - innerR * 0.5f, cy - innerR * 0.5f)
                    lineTo(cx - innerR * 0.6f, cy - innerR * 0.85f)
                    lineTo(cx - innerR * 0.2f, cy - innerR * 0.65f)
                    cubicTo(cx - innerR * 0.1f, cy - innerR * 0.75f, cx + innerR * 0.1f, cy - innerR * 0.75f, cx + innerR * 0.2f, cy - innerR * 0.65f)
                    lineTo(cx + innerR * 0.6f, cy - innerR * 0.85f)
                    lineTo(cx + innerR * 0.5f, cy - innerR * 0.5f)
                    cubicTo(cx + innerR * 0.8f, cy - innerR * 0.3f, cx + innerR * 0.8f, cy + innerR * 0.6f, cx + innerR * 0.3f, cy + innerR * 0.7f)
                    close()
                }
                canvas.drawPath(catPath, fillPaint)
            }
            "EMAIL" -> {
                val mailRect = RectF(cx - innerR, cy - innerR * 0.6f, cx + innerR, cy + innerR * 0.6f)
                canvas.drawRoundRect(mailRect, innerR * 0.15f, innerR * 0.15f, iconPaint)
                val flapPath = Path().apply {
                    moveTo(cx - innerR, cy - innerR * 0.5f)
                    lineTo(cx, cy + innerR * 0.1f)
                    lineTo(cx + innerR, cy - innerR * 0.5f)
                }
                canvas.drawPath(flapPath, iconPaint)
            }
            "PHONE" -> {
                val receiverPath = Path().apply {
                    moveTo(cx - innerR * 0.4f, cy - innerR * 0.4f)
                    quadTo(cx - innerR * 0.7f, cy, cx - innerR * 0.2f, cy + innerR * 0.5f)
                    lineTo(cx, cy + innerR * 0.3f)
                    quadTo(cx - innerR * 0.3f, cy, cx - innerR * 0.2f, cy - innerR * 0.2f)
                    close()
                }
                canvas.drawPath(receiverPath, fillPaint)
                canvas.drawArc(RectF(cx - innerR * 0.1f, cy - innerR * 0.5f, cx + innerR * 0.5f, cy + innerR * 0.1f), -90f, 90f, false, iconPaint)
            }
            "WIFI" -> {
                canvas.drawCircle(cx, cy + innerR * 0.5f, innerR * 0.15f, fillPaint)
                iconPaint.style = Paint.Style.STROKE
                canvas.drawArc(RectF(cx - innerR * 0.4f, cy - innerR * 0.1f, cx + innerR * 0.4f, cy + innerR * 0.7f), -145f, 110f, false, iconPaint)
                canvas.drawArc(RectF(cx - innerR * 0.8f, cy - innerR * 0.5f, cx + innerR * 0.8f, cy + innerR * 1.1f), -145f, 110f, false, iconPaint)
            }
            "PAYMENT" -> {
                val cardRect = RectF(cx - innerR, cy - innerR * 0.55f, cx + innerR, cy + innerR * 0.55f)
                canvas.drawRoundRect(cardRect, innerR * 0.15f, innerR * 0.15f, iconPaint)
                val stripPaint = Paint(fillPaint).apply { strokeWidth = r * 0.1f }
                canvas.drawLine(cx - innerR, cy - innerR * 0.15f, cx + innerR, cy - innerR * 0.15f, stripPaint)
                canvas.drawCircle(cx - innerR * 0.4f, cy + innerR * 0.2f, innerR * 0.12f, fillPaint)
            }
            "SMS" -> {
                val bubblePath = Path().apply {
                    val rect = RectF(cx - innerR, cy - innerR * 0.6f, cx + innerR, cy + innerR * 0.5f)
                    addRoundRect(rect, innerR * 0.25f, innerR * 0.25f, Path.Direction.CW)
                    moveTo(cx - innerR * 0.4f, cy + innerR * 0.5f)
                    lineTo(cx - innerR * 0.6f, cy + innerR * 0.85f)
                    lineTo(cx - innerR * 0.2f, cy + innerR * 0.5f)
                }
                canvas.drawPath(bubblePath, fillPaint)
                val dotPaint = Paint().apply { color = bgColor; isAntiAlias = true }
                canvas.drawCircle(cx - innerR * 0.3f, cy - innerR * 0.05f, innerR * 0.08f, dotPaint)
                canvas.drawCircle(cx, cy - innerR * 0.05f, innerR * 0.08f, dotPaint)
                canvas.drawCircle(cx + innerR * 0.3f, cy - innerR * 0.05f, innerR * 0.08f, dotPaint)
            }
            else -> {
                canvas.drawCircle(cx, cy, innerR, iconPaint)
                canvas.drawOval(RectF(cx - innerR * 0.35f, cy - innerR, cx + innerR * 0.35f, cy + innerR), iconPaint)
                canvas.drawLine(cx - innerR, cy, cx + innerR, cy, iconPaint)
            }
        }
    }
}
