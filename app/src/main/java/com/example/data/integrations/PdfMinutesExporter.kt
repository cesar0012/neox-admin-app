package com.example.data.integrations

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.MeetingNote
import com.example.data.model.MeetingTaskItem
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera el PDF formal de una minuta con la API nativa de Android (sin dependencias
 * externas): carátula, resumen ejecutivo, acciones, decisiones, citas textuales e
 * ítems detectados con su estado. Devuelve un Intent de compartir listo para usar.
 */
object PdfMinutesExporter {

    private val cyan = Color.rgb(34, 211, 238)
    private val dark = Color.rgb(15, 23, 42)
    private val gray = Color.rgb(100, 116, 139)

    fun export(context: Context, meeting: MeetingNote, items: List<MeetingTaskItem>): Intent {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 72dpi
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = 46f

        val title = Paint().apply { color = dark; textSize = 19f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val h2 = Paint().apply { color = cyan; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val body = Paint().apply { color = dark; textSize = 10f }
        val small = Paint().apply { color = gray; textSize = 8.5f }
        val line = Paint().apply { color = cyan; strokeWidth = 2f }

        fun ensureSpace(needed: Float) {
            if (y + needed > 800f) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 46f
            }
        }

        fun textBlock(label: String, content: String, bodyPaint: Paint = body) {
            if (content.isBlank()) return
            ensureSpace(40f)
            canvas.drawText(label, 46f, y, h2)
            y += 15f
            // Word-wrap simple por ancho aproximado
            val maxChars = 95
            content.split("\n").forEach { paragraph ->
                var remaining = paragraph.trim()
                if (remaining.isEmpty()) { y += 10f; return@forEach }
                while (remaining.isNotEmpty()) {
                    ensureSpace(16f)
                    canvas.drawText(remaining.take(maxChars), 46f, y, bodyPaint)
                    y += 13f
                    remaining = remaining.drop(maxChars)
                }
            }
            y += 8f
        }

        // ── Carátula ──
        canvas.drawRect(0f, 0f, 595f, 8f, line)
        canvas.drawText("Minuta de Junta", 46f, y, title); y += 16f
        canvas.drawText(meeting.title, 46f, y, title.apply { textSize = 13f }); y += 14f
        val dateStr = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy · HH:mm", Locale("es", "ES"))
            .format(Date(meeting.dateTimestamp)).replaceFirstChar { it.uppercase(Locale.getDefault()) }
        canvas.drawText("$dateStr  ·  Proyecto: ${meeting.jobTag}", 46f, y, small); y += 20f

        textBlock("RESUMEN EJECUTIVO", meeting.executiveSummary)
        textBlock("MIS ACCIONES", meeting.myActionItems)
        textBlock("ACCIONES DE OTROS", meeting.othersActionItems)
        textBlock("DECISIONES CLAVE", meeting.keyDecisions)

        // Citas textuales
        val quotes = parseQuotes(meeting.quotesJson)
        if (quotes.isNotEmpty()) {
            textBlock("CITAS TEXTUALES DE RESPALDO", quotes.joinToString("\n") { "« $it »" }, small)
        }

        // Ítems detectados y su estado
        if (items.isNotEmpty()) {
            ensureSpace(30f)
            canvas.drawText("COMPROMISOS DETECTADOS (${items.size})", 46f, y, h2); y += 14f
            val fmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
            items.forEach { it ->
                val state = when (it.status) {
                    "AGREGADA" -> "· AGREGADA A MIS TAREAS"
                    "DESCARTADA" -> "· descartada"
                    else -> if (it.dueTimestamp > 0) "· vence ${fmt.format(Date(it.dueTimestamp))}" else "· SIN FECHA"
                }
                ensureSpace(26f)
                canvas.drawText("• ${it.title}  $state", 46f, y, body); y += 12f
                if (it.contextQuote.isNotBlank()) {
                    ensureSpace(14f)
                    canvas.drawText("   « ${it.contextQuote.take(90)} »", 46f, y, small); y += 11f
                }
                y += 3f
            }
        }

        canvas.drawText("Generado por Neox Admin", 46f, 820f, small)
        doc.finishPage(page)

        val file = File(context.cacheDir, "minuta_${meeting.id}_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, meeting.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun parseQuotes(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}
