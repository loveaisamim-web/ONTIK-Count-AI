package com.ontik.countai.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ontik.countai.data.local.CountSessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a high-resolution, print-ready PDF report for PRO users using Android's native [PdfDocument]
 * (zero external network dependencies, 100% offline).
 *
 * Includes:
 * - ONTIK Count AI vector logo & brand header
 * - Session summary metrics (Today / Total sessions / Grand total objects)
 * - 7-session visual bar chart
 * - Detailed session table (Date/Time, Label, Preset, Count, Accuracy Test %, Notes)
 */
object PdfReportGenerator {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun generateSessionReportPdf(context: Context, sessions: List<CountSessionEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "ONTIK_Count_AI_Pro_Report_${fileStampFormatter.format(Date())}.pdf")

        val pdf = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val greenPrimary = Color.rgb(22, 163, 74)
        val darkSlate = Color.rgb(15, 23, 42)
        val mutedSlate = Color.rgb(100, 116, 139)
        val softBg = Color.rgb(241, 245, 249)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Header Banner & Vector Brand Logo
        paint.color = darkSlate
        canvas.drawRoundRect(RectF(32f, 28f, (pageWidth - 32).toFloat(), 108f), 14f, 14f, paint)

        // Green Logo Icon Box
        paint.color = greenPrimary
        canvas.drawRoundRect(RectF(48f, 44f, 96f, 92f), 10f, 10f, paint)
        paint.color = Color.WHITE
        paint.strokeWidth = 3f
        canvas.drawLine(56f, 68f, 88f, 68f, paint)
        canvas.drawCircle(66f, 58f, 4f, paint)
        canvas.drawCircle(78f, 78f, 4f, paint)

        // Title Text
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("ONTIK Count AI — PRO Report", 112f, 66f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 11f
        paint.color = Color.rgb(203, 213, 225)
        canvas.drawText("Generated: ${dateFormatter.format(Date())} • 100% Offline OpenCV Precision Engine", 112f, 86f, paint)

        // 2. Summary Metric Cards + Mini Chart
        val totalSessions = sessions.size
        val grandTotalCount = sessions.sumOf { it.finalCount }
        paint.color = softBg
        canvas.drawRoundRect(RectF(32f, 122f, 290f, 196f), 12f, 12f, paint)
        canvas.drawRoundRect(RectF(305f, 122f, (pageWidth - 32).toFloat(), 196f), 12f, 12f, paint)

        paint.color = mutedSlate
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL SESSIONS", 48f, 142f, paint)
        canvas.drawText("CUMULATIVE GRAINS COUNTED", 48f, 174f, paint)

        paint.color = greenPrimary
        paint.textSize = 16f
        canvas.drawText("$totalSessions", 210f, 144f, paint)
        canvas.drawText("$grandTotalCount", 210f, 176f, paint)

        // Mini bar chart inside right card
        paint.color = mutedSlate
        paint.textSize = 10f
        canvas.drawText("RECENT SESSIONS CHART", 320f, 140f, paint)
        val recent = sessions.take(8).reversed()
        val maxC = (recent.maxOfOrNull { it.finalCount } ?: 1).coerceAtLeast(1)
        recent.forEachIndexed { idx, s ->
            val bx = 320f + idx * 28f
            val bh = ((s.finalCount.toFloat() / maxC) * 36f).coerceAtLeast(4f)
            paint.color = greenPrimary
            canvas.drawRoundRect(RectF(bx, 186f - bh, bx + 18f, 186f), 4f, 4f, paint)
        }

        // 3. Table Header
        var y = 224f
        paint.color = greenPrimary
        canvas.drawRoundRect(RectF(32f, y - 16f, (pageWidth - 32).toFloat(), y + 10f), 6f, 6f, paint)
        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Date & Time", 42f, y, paint)
        canvas.drawText("Label / Batch", 155f, y, paint)
        canvas.drawText("Preset", 295f, y, paint)
        canvas.drawText("Count", 400f, y, paint)
        canvas.drawText("Accuracy / Note", 460f, y, paint)

        y += 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f

        val maxRows = 22
        sessions.take(maxRows).forEachIndexed { index, s ->
            if (index % 2 == 1) {
                paint.color = softBg
                canvas.drawRect(32f, y - 13f, (pageWidth - 32).toFloat(), y + 7f, paint)
            }
            paint.color = darkSlate
            canvas.drawText(dateFormatter.format(Date(s.timestampMs)), 42f, y, paint)
            canvas.drawText(s.label.take(22), 155f, y, paint)
            canvas.drawText(s.presetName.substringBefore(" ").take(16), 295f, y, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = greenPrimary
            canvas.drawText("${s.finalCount}", 400f, y, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = mutedSlate
            val noteCol = if (s.isTestModeSession) {
                "Test Err: ${String.format(Locale.US, "%.1f%%", s.accuracyErrorPercent)}"
            } else {
                s.notes.take(18)
            }
            canvas.drawText(noteCol, 460f, y, paint)
            y += 22f
        }

        // Footer
        paint.color = mutedSlate
        paint.textSize = 9f
        canvas.drawText("Generated by ONTIK Count AI • Precision Offline Computer Vision Counter", 32f, (pageHeight - 24).toFloat(), paint)

        pdf.finishPage(page)
        FileOutputStream(outFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()
        return outFile
    }
}
