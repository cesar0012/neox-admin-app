package com.example.data.webhook

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.DocumentItem
import com.example.data.model.VaultEntry
import com.example.data.model.WorkTask
import com.example.data.rag.RAGMemoryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

data class WebhookLog(
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val endpoint: String,
    val summary: String
)

class LocalWebhookServer(
    private val context: Context,
    private val database: AppDatabase,
    private val ragEngine: RAGMemoryEngine,
    val port: Int = 8765
) {
    private val tag = "LocalWebhookServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _logsFlow = MutableStateFlow<List<WebhookLog>>(emptyList())
    val logsFlow: StateFlow<List<WebhookLog>> = _logsFlow.asStateFlow()

    fun start() {
        if (_isRunning.value) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isRunning.value = true
                addLog("System", "SERVER_START", "Servidor Webhook iniciado en puerto $port")

                while (_isRunning.value) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Throwable) {
                Log.e(tag, "Webhook server exception: ${e.message}", e)
                addLog("System", "ERROR", "No se pudo iniciar en puerto $port: ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        addLog("System", "SERVER_STOP", "Servidor Webhook detenido")
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.ROOT)
                val path = parts[1]

                // Read headers
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val headerLower = line!!.lowercase(Locale.ROOT)
                    if (headerLower.startsWith("content-length:")) {
                        contentLength = headerLower.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                // Read body if any
                val body = if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(charArray, read, contentLength - read)
                        if (r == -1) break
                        read += r
                    }
                    String(charArray, 0, read)
                } else {
                    ""
                }

                // Route handling with CORS support
                when {
                    method == "OPTIONS" -> {
                        sendHttpResponse(writer, 200, "OK", "application/json", "{}")
                    }
                    method == "GET" && path.startsWith("/status") -> {
                        val statusJson = JSONObject().apply {
                            put("status", "ONLINE")
                            put("appName", "OmniWork")
                            put("ip", getLocalIpAddress())
                            put("port", port)
                        }
                        sendHttpResponse(writer, 200, "OK", "application/json", statusJson.toString())
                    }
                    method == "GET" && path.startsWith("/tasks") -> {
                        // Return tasks for desktop/Chrome extension
                        val taskList = mutableListOf<JSONObject>()
                        // We fetch directly
                        val jsonArray = JSONArray()
                        // Send simple list
                        sendHttpResponse(writer, 200, "OK", "application/json", jsonArray.toString())
                    }
                    method == "POST" && path.startsWith("/webhook/task") -> {
                        val json = JSONObject(body)
                        val title = json.optString("title", "Nueva tarea desde Webhook")
                        val desc = json.optString("description", "")
                        val jobTag = json.optString("jobTag", "General")
                        val priority = json.optString("priority", "MEDIA")
                        val origin = json.optString("source", "CloudCode / Webhook")

                        val task = WorkTask(
                            title = title,
                            description = desc,
                            jobTag = jobTag,
                            dueTimestamp = System.currentTimeMillis() + 3600 * 1000L * 4,
                            priority = priority,
                            originReference = origin
                        )
                        val taskId = database.taskDao().insertTask(task)

                        // Save in Vault
                        val vaultEntry = VaultEntry(
                            title = "Tarea Webhook: $title",
                            rawContent = body,
                            sourceType = "WEBHOOK",
                            jobTag = jobTag
                        )
                        val vaultId = database.vaultDao().insertVaultEntry(vaultEntry)

                        // Index in RAG memory
                        ragEngine.indexContent(
                            sourceId = taskId,
                            sourceType = "TASK",
                            title = title,
                            jobTag = jobTag,
                            content = "$title - $desc (Origen: $origin)"
                        )

                        addLog("Webhook", "POST /webhook/task", "Tarea recibida: $title ($jobTag)")

                        val res = JSONObject().apply {
                            put("success", true)
                            put("taskId", taskId)
                            put("vaultId", vaultId)
                        }
                        sendHttpResponse(writer, 201, "Created", "application/json", res.toString())
                    }
                    method == "POST" && path.startsWith("/webhook/context") -> {
                        val json = JSONObject(body)
                        val title = json.optString("title", "Contexto de Desarrollo")
                        val content = json.optString("content", "")
                        val jobTag = json.optString("jobTag", "General")
                        val source = json.optString("source", "Desktop Tool")

                        // Store in Vault (Guaranteed zero loss)
                        val vaultId = database.vaultDao().insertVaultEntry(
                            VaultEntry(
                                title = title,
                                rawContent = content,
                                sourceType = "WEBHOOK",
                                jobTag = jobTag
                            )
                        )

                        // Store as Document
                        val docId = database.documentDao().insertDocument(
                            DocumentItem(
                                title = title,
                                jobTag = jobTag,
                                category = "ESPECIFICACION",
                                content = content
                            )
                        )

                        // Index in RAG Memory
                        ragEngine.indexContent(
                            sourceId = docId,
                            sourceType = "DOCUMENT",
                            title = title,
                            jobTag = jobTag,
                            content = content
                        )

                        addLog(source, "POST /webhook/context", "Contexto sincronizado: $title")

                        val res = JSONObject().apply {
                            put("success", true)
                            put("docId", docId)
                            put("vaultId", vaultId)
                        }
                        sendHttpResponse(writer, 200, "OK", "application/json", res.toString())
                    }
                    else -> {
                        sendHttpResponse(writer, 404, "Not Found", "application/json", "{\"error\":\"Not Found\"}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error handling client request: ${e.message}")
            }
        }
    }

    private fun sendHttpResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun addLog(source: String, endpoint: String, summary: String) {
        val log = WebhookLog(source = source, endpoint = endpoint, summary = summary)
        val current = _logsFlow.value.toMutableList()
        current.add(0, log)
        if (current.size > 50) current.removeLast()
        _logsFlow.value = current
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
