package com.ontik.countai.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.domain.model.DebugFrameSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionExportHelper {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private fun csvCell(value: String): String {
        val quote = "\""
        val doubleQuote = "\"\""
        return quote + value.replace(quote, doubleQuote) + quote
    }

    fun exportToCsv(context: Context, sessions: List<CountSessionEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ONTIK_Count_AI_History_${fileStampFormatter.format(Date())}.csv")

        val sb = StringBuilder()
        sb.appendLine("Session ID,Timestamp,Label,Preset,Final Count,Auto Count,Manual Adjustment,Direction,Duration (sec),100-Grain Test Mode,Error %,Notes")

        for (s in sessions) {
            val formattedDate = dateFormatter.format(Date(s.timestampMs))
            val row = listOf(
                s.id.toString(),
                csvCell(formattedDate),
                csvCell(s.label),
                csvCell(s.presetName),
                s.finalCount.toString(),
                s.autoCount.toString(),
                s.manualDelta.toString(),
                csvCell(s.direction),
                s.durationSeconds.toString(),
                if (s.isTestModeSession) "YES" else "NO",
                if (s.isTestModeSession) String.format(Locale.US, "%.1f%%", s.accuracyErrorPercent) else "N/A",
                csvCell(s.notes)
            ).joinToString(",")
            sb.appendLine(row)
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportToTxt(context: Context, sessions: List<CountSessionEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ONTIK_Count_AI_History_${fileStampFormatter.format(Date())}.txt")

        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("       ONTIK Count AI - History Log       ")
        sb.appendLine("==========================================")
        sb.appendLine("Exported: ${dateFormatter.format(Date())}")
        sb.appendLine("Total Sessions: ${sessions.size}")
        sb.appendLine("Grand Total Count: ${sessions.sumOf { it.finalCount }}")
        sb.appendLine("------------------------------------------")

        for (s in sessions) {
            val formattedDate = dateFormatter.format(Date(s.timestampMs))
            sb.appendLine("[#${s.id}] ${s.label} — Count: ${s.finalCount}")
            sb.appendLine("  Date     : $formattedDate")
            sb.appendLine("  Preset   : ${s.presetName} | Direction: ${s.direction}")
            sb.appendLine("  Details  : Auto=${s.autoCount}, Manual=${s.manualDelta}, Duration=${s.durationSeconds}s")
            if (s.notes.isNotBlank()) {
                sb.appendLine("  Notes    : ${s.notes}")
            }
            sb.appendLine("------------------------------------------")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportDebugLog(context: Context, snapshots: List<DebugFrameSnapshot>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ONTIK_Debug_10s_Log_${fileStampFormatter.format(Date())}.csv")
        val sb = StringBuilder()
        sb.appendLine("ElapsedMs,TotalCount,InZoneCount,FPS,NoiseStd,SingleGrainAreaPx")
        for (snap in snapshots) {
            sb.appendLine("${snap.timestampMs},${snap.totalCount},${snap.inZoneCount},${snap.fps},${String.format(Locale.US, "%.2f", snap.noiseStd)},${String.format(Locale.US, "%.1f", snap.singleGrainAreaPx)}")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        try {
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
            val chooser = Intent.createChooser(shareIntent, "Share ONTIK Count AI Export").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Throwable) {
            Toast.makeText(context, "Unable to open share dialog: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
