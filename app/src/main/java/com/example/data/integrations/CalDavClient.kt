package com.example.data.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.PasswordAuthentication
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalDavEvent(
    val uid: String,
    val title: String,
    val start: Long,     // 0 con allDay=false imposible; allDay -> hora 00:00
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val description: String = ""
)

/**
 * Lectura de Google Calendar vía CalDAV (misma cuenta + contraseña de aplicación del
 * correo). Solo lectura por ahora: importa los eventos próximos a la agenda de Neox
 * como tareas tipo EVENTO/JUNTA, evitando duplicados por UID.
 */
object CalDavClient {

    /** Descarga los eventos entre [from] y [to] del calendario principal de la cuenta. */
    suspend fun fetchEvents(
        user: String,
        password: String,
        from: Long,
        to: Long
    ): Result<List<CalDavEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val body = """
                <?xml version="1.0" encoding="utf-8" ?>
                <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop>
                    <d:getetag/>
                    <c:calendar-data/>
                  </d:prop>
                  <c:filter>
                    <c:comp-filter name="VCALENDAR">
                      <c:comp-filter name="VEVENT">
                        <c:time-range start="${fmt.format(Date(from))}" end="${fmt.format(Date(to))}"/>
                      </c:comp-filter>
                    </c:comp-filter>
                  </c:filter>
                </c:calendar-query>
            """.trimIndent()

            val url = URL("https://calendar.google.com/calendar/dav/$user/events/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "REPORT"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 40_000
            conn.setRequestProperty("Depth", "1")
            conn.setRequestProperty("Content-Type", "application/xml; charset=UTF-8")
            val auth = android.util.Base64.encodeToString(
                "$user:$password".toByteArray(), android.util.Base64.NO_WRAP
            )
            conn.setRequestProperty("Authorization", "Basic $auth")
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(user, password.toCharArray())
            })
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val xml = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                val err = runCatching {
                    BufferedReader(InputStreamReader(conn.errorStream, Charsets.UTF_8)).use { it.readText() }
                }.getOrDefault("")
                throw IllegalStateException("CalDAV $code: ${err.take(160)}")
            }
            parseEvents(xml)
        }
    }

    /** Parser ligero del multistatus CalDAV -> ics -> VEVENTs. */
    internal fun parseEvents(xml: String): List<CalDavEvent> {
        val events = mutableListOf<CalDavEvent>()
        // El calendar-data viene escapado dentro del XML; con unescape general basta
        val ics = xml
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&amp;", "&").replace("&#13;", "").replace("&#10;", "\n")
        val vevents = ics.split("BEGIN:VEVENT").drop(1)
        val utcFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (block in vevents) {
            val fields = block.lines().associate { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) "" to "" else line.take(idx).takeWhile { it != ';' } to line.substring(idx + 1)
            }
            val uid = fields["UID"]?.trim().orEmpty()
            val summary = fields["SUMMARY"]?.trim().orEmpty()
            val dtStartRaw = fields["DTSTART"]?.trim().orEmpty()
            if (uid.isBlank() || summary.isBlank() || dtStartRaw.isBlank()) continue
            val allDay = !dtStartRaw.contains("T")
            val dtStart = runCatching {
                (if (allDay) dateFmt else utcFmt).parse(dtStartRaw)?.time ?: 0L
            }.getOrNull() ?: continue
            val dtEndRaw = fields["DTEND"]?.trim().orEmpty()
            val dtEnd = runCatching {
                (if (dtEndRaw.contains("T")) utcFmt else dateFmt).parse(dtEndRaw)?.time ?: dtStart
            }.getOrDefault(dtStart)
            events.add(
                CalDavEvent(
                    uid = uid,
                    title = summary,
                    start = dtStart,
                    end = dtEnd,
                    allDay = allDay,
                    location = fields["LOCATION"]?.trim().orEmpty(),
                    description = fields["DESCRIPTION"]?.trim().orEmpty()
                )
            )
        }
        return events.distinctBy { it.uid }
    }
}
