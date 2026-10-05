package com.example.data.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store

data class EmailMsg(
    val from: String,
    val subject: String,
    val date: Long,
    val snippet: String
)

/**
 * Lectura de correo vía IMAP (Gmail con contraseña de aplicación; también sirve para
 * Outlook/otros proveedores IMAP). Solo lectura de los mensajes más recientes:
 * el agente analiza el resumen y detecta compromisos.
 */
object EmailClient {

    data class ImapConfig(
        val user: String,
        val password: String,
        val host: String = "imap.gmail.com",
        val port: Int = 993
    )

    fun configFor(user: String, password: String): ImapConfig {
        val host = when {
            user.endsWith("@gmail.com", true) || user.endsWith("@googlemail.com", true) -> "imap.gmail.com"
            user.endsWith("@outlook.com", true) || user.endsWith("@hotmail.com", true) -> "outlook.office365.com"
            user.endsWith("@yahoo.com", true) -> "imap.mail.yahoo.com"
            else -> "imap.gmail.com"
        }
        return ImapConfig(user, password, host)
    }

    /** Prueba de conexión: devuelve error humano o null si todo bien. */
    suspend fun testConnection(cfg: ImapConfig): String? = withContext(Dispatchers.IO) {
        runCatching {
            val store = open(cfg)
            store.close()
        }.exceptionOrNull()?.message?.let { "Conexión fallida: ${it.take(140)}" }
    }

    /**
     * Descarga los últimos [max] correos (más recientes primero) con remitente, asunto,
     * fecha y un fragmento del cuerpo para análisis del agente.
     */
    suspend fun fetchRecent(cfg: ImapConfig, max: Int = 15): Result<List<EmailMsg>> = withContext(Dispatchers.IO) {
        runCatching {
            val store = open(cfg)
            store.use { s ->
                val inbox: Folder = s.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
                val total = inbox.messageCount
                if (total == 0) return@runCatching emptyList()
                val start = (total - max + 1).coerceAtLeast(1)
                val msgs = inbox.getMessages(start, total)
                msgs.reversed().mapNotNull { m ->
                    val content = runCatching {
                        when (val part = m.content) {
                            is String -> part
                            is javax.mail.Multipart -> {
                                val sb = StringBuilder()
                                for (i in 0 until part.count) {
                                    val bp = part.getBodyPart(i)
                                    if (bp.isMimeType("text/plain")) sb.append(bp.content?.toString() ?: "")
                                }
                                sb.toString()
                            }
                            else -> ""
                        }
                    }.getOrDefault("")
                    val from = runCatching {
                        (m.from?.firstOrNull()?.toString() ?: "?").substringBefore("<").ifBlank { m.from?.firstOrNull().toString() }
                    }.getOrDefault("?")
                    EmailMsg(
                        from = from.trim(),
                        subject = runCatching { m.subject ?: "(sin asunto)" }.getOrDefault("(sin asunto)"),
                        date = runCatching { m.sentDate?.time ?: 0L }.getOrDefault(0L),
                        snippet = content.replace(Regex("\\s+"), " ").trim().take(600)
                    )
                }
            }
        }
    }

    private fun open(cfg: ImapConfig): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", cfg.host)
            put("mail.imaps.port", cfg.port.toString())
            put("mail.imaps.ssl.enable", "true")
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(cfg.host, cfg.port, cfg.user, cfg.password)
        return store
    }
}
