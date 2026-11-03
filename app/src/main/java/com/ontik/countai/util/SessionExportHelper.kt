package com.ontik.countai.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ontik.countai.data.local.CountSessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates and shares CSV and TXT reports of counting history sessions for offline archiving or spreadsheet analysis.
 */
object SessionExportHelper {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportToCsv(context: Context, sessions: List<CountSessionEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ONTIK_Count_AI_History_${fileStampFormatter.format(Date())}.csv")

        val sb = StringBuilder()
        sb.appendLine("Session ID,Timestamp,Mode,Final Count,AI Detected Count,Manual Adjustment,Direction,Confidence Threshold,Duration (sec),Model Used,Notes")

        for (session in sessions) {
            val formattedDate = dateFormatter.format(Date(session.timestampMs))
            val safeNotes = session.notes.replace(""", """")
            val safeModel = session.modelName.replace(""", """")
            sb.appendLine(
                "${session.id},"$formattedDate","${session.mode}",${session.finalCount},${session.aiDetectedCount},${session.manualDelta},"${session.direction}",${session.confidenceThreshold},${session.durationSeconds},"$safeModel","$safeNotes""
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportToTxt(context: Context, sessions: List<CountSessionEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ONTIK_Count_AI_Report_${fileStampFormatter.format(Date())}.txt")

        val sb = StringBuilder()
        sb.appendLine("====================================================")
        sb.appendLine("            ONTIK Count AI - Session Report         ")
        sb.appendLine("====================================================")
        sb.appendLine("Generated: ${dateFormatter.format(Date())}")
        sb.appendLine("Total Sessions Recorded: ${sessions.size}")
        sb.appendLine("Cumulative Objects Counted: ${sessions.sumOf { it.finalCount }}")
        sb.appendLine("----------------------------------------------------")
        sb.appendLine()

        sessions.forEachIndexed { index, s ->
            sb.appendLine("Session #${index + 1} (ID: ${s.id})")
            sb.appendLine("  Date & Time       : ${dateFormatter.format(Date(s.timestampMs))}")
            sb.appendLine("  Counting Mode     : ${s.mode}")
            sb.appendLine("  Total Final Count : ${s.finalCount}")
            sb.appendLine("  AI Raw Count      : ${s.aiDetectedCount}")
            sb.appendLine("  Manual Adjustment : ${if (s.manualDelta >= 0) "+${s.manualDelta}" else "${s.manualDelta}"}")
            sb.appendLine("  Direction         : ${s.direction}")
            sb.appendLine("  Confidence Config : ${(s.confidenceThreshold * 100).toInt()}%")
            sb.appendLine("  Duration          : ${s.durationSeconds}s")
            sb.appendLine("  AI Model          : ${s.modelName}")
            if (s.notes.isNotBlank()) {
                sb.appendLine("  Notes             : ${s.notes}")
            }
            sb.appendLine("----------------------------------------------------")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ONTIK Count AI Export - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export ONTIK Count AI History").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
