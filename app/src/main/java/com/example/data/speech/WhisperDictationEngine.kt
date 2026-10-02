package com.example.data.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Transcripción de audio con Whisper (API de Groq, compatible OpenAI).
 * Whisper entiende contexto y prosodia: devuelve texto en español con puntuación,
 * mayúsculas y estructura de frases — calidad muy superior al reconocedor del teléfono.
 */
object WhisperTranscriber {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val MODELS = listOf("whisper-large-v3-turbo", "whisper-large-v3")

    suspend fun transcribe(wav: ByteArray, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var lastErr: IllegalStateException? = null
            for (model in MODELS) {
                try {
                    return@runCatching post(wav, apiKey, model)
                } catch (e: IllegalStateException) {
                    lastErr = e
                    // Solo reintentar con el otro modelo si este no existe/disponible
                    val msg = e.message.orEmpty().lowercase()
                    if ("model" !in msg && "404" !in msg) throw e
                }
            }
            throw lastErr ?: IllegalStateException("Fallo de transcripción")
        }
    }

    private fun post(wav: ByteArray, apiKey: String, model: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "dictado.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("language", "es")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .build()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Groq ${resp.code}: ${txt.take(180)}")
            }
            return JSONObject(txt).optString("text", "").trim()
        }
    }
}

/**
 * Grabador de audio continuo con segmentación inteligente por silencio.
 *
 * La grabación NUNCA se detiene por pausas del usuario: el micrófono sigue abierto
 * todo el tiempo. Cuando detecta el final natural de una frase (~1.1s de silencio tras
 * haber hablado) cierra ese segmento y lo entrega como WAV listo para transcribir,
 * mientras sigue grabando el siguiente. Cierre forzado a los 24s de habla continua.
 * El umbral de voz es adaptativo (piso de ruido del ambiente) para funcionar en
 * lugares ruidosos.
 */
class AudioSegmentRecorder(
    private val sampleRate: Int = 16_000,
    private val onRms: ((Float) -> Unit)? = null,          // 0..1 para el medidor visual
    private val onSegment: ((ByteArray) -> Unit)? = null,  // WAV de la frase cerrada
    private val onError: ((String) -> Unit)? = null
) {
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var active = false        // hilo de captura vivo

    // ── Parámetros de segmentación ──
    private val silenceToCloseMs = 1_400L
    private val maxSegmentMs = 24_000L
    private val minSpeechMs = 300L
    private val windowMs = 100L

    fun start() {
        if (active) return
        active = true
        thread = Thread({ captureLoop() }, "DictationRecorder").apply {
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    /**
     * Detiene la captura y LIBERA el micrófono por completo (flush de la frase en curso).
     * Crítico: mantener el AudioRecord abierto aunque no se use degrada el audio de otros
     * clientes del micrófono (el reconocedor de Google corre en otro proceso).
     */
    fun stop() {
        active = false
        try { thread?.join(900) } catch (_: InterruptedException) {}
        thread = null
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    fun release() = stop()

    @SuppressLint("MissingPermission") // el permiso se valida antes de crear el grabador
    private fun captureLoop() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            // MIC (no VOICE_RECOGNITION): varios fabricantes aplican a VOICE_RECOGNITION
            // supresión de ruido agresiva sin AGC que castiga la voz lejana del micrófono.
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate) // al menos 1s de buffer
            )
        } catch (t: Throwable) {
            onError?.invoke("No se pudo abrir el micrófono: ${t.message}")
            active = false
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError?.invoke("Micrófono ocupado por otra app")
            active = false
            return
        }
        audioRecord = record
        try {
            record.startRecording()
        } catch (t: Throwable) {
            record.release()
            audioRecord = null
            onError?.invoke("No se pudo iniciar la grabación: ${t.message}")
            active = false
            return
        }

        val window = ShortArray((sampleRate * windowMs / 1000).toInt())
        var pcm = ArrayList<Byte>(sampleRate * 2)         // PCM del segmento en curso
        var segMs = 0L
        var speechMs = 0L
        var silenceMs = 0L
        var hasSpeech = false
        var noiseFloor = 280.0                            // piso de ruido adaptativo
        var uiTick = 0

        while (active) {
            val n = record.read(window, 0, window.size)
            if (n <= 0) continue

            // RMS de la ventana
            var sum = 0.0
            for (i in 0 until n) { val v = window[i].toDouble(); sum += v * v }
            val rms = Math.sqrt(sum / n)

            // Umbral de voz permisivo: capta voz a distancia sin que el ruido abra segmentos.
            // El piso de ruido solo sube si la señal es claramente silenciosa y tiene tope.
            val speechThreshold = maxOf(noiseFloor * 1.9, 380.0)
            if (rms < noiseFloor * 1.4) {
                noiseFloor = ((noiseFloor * 0.97) + (rms * 0.03)).coerceAtMost(1_400.0)
            }

            val isSpeech = rms > speechThreshold
            if (++uiTick % 2 == 0) { // ~5 fps para el medidor
                val norm = (((rms - noiseFloor * 0.5) / 3200.0).coerceIn(0.0, 1.0)).toFloat()
                onRms?.invoke(norm)
            }

            segMs += windowMs
            if (isSpeech) {
                hasSpeech = true
                speechMs += windowMs
                silenceMs = 0
            } else if (hasSpeech) {
                silenceMs += windowMs
            }

            // Guardar audio del segmento (incluye la ventana donde arranca la frase)
            if (hasSpeech) {
                for (i in 0 until n) {
                    pcm.add((window[i].toInt() and 0xFF).toByte())
                    pcm.add((window[i].toInt() shr 8).toByte())
                }
            }

            val shouldClose = hasSpeech && (silenceMs >= silenceToCloseMs || segMs >= maxSegmentMs)
            if (shouldClose) {
                if (speechMs >= minSpeechMs && pcm.size > 44) {
                    onSegment?.invoke(wavFromPcm(pcm.toByteArray(), sampleRate))
                }
                pcm = ArrayList(sampleRate * 2)
                segMs = 0; speechMs = 0; silenceMs = 0; hasSpeech = false
            } else if (!hasSpeech && segMs >= 4_000) {
                // Solo ruido ambiente acumulado sin habla: resetear contadores
                segMs = 0
            }
        }

        // Flush final: frase en curso al detener la captura (pausa/cambio de modo/enviar)
        if (hasSpeech && speechMs >= minSpeechMs && pcm.size > 44) {
            onSegment?.invoke(wavFromPcm(pcm.toByteArray(), sampleRate))
        }
        try { record.stop() } catch (_: Exception) {}
        record.release()
        audioRecord = null
    }

    companion object {
        /** PCM16 mono -> WAV (header RIFF de 44 bytes + datos). */
        fun wavFromPcm(pcm: ByteArray, sampleRate: Int): ByteArray {
            val totalLen = pcm.size
            val wav = ByteArray(44 + totalLen)
            fun putInt(off: Int, v: Int) {
                wav[off] = (v and 0xFF).toByte()
                wav[off + 1] = ((v shr 8) and 0xFF).toByte()
                wav[off + 2] = ((v shr 16) and 0xFF).toByte()
                wav[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun putShort(off: Int, v: Int) {
                wav[off] = (v and 0xFF).toByte()
                wav[off + 1] = ((v shr 8) and 0xFF).toByte()
            }
            val byteRate = sampleRate * 2
            wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte()
            wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
            putInt(4, 36 + totalLen)
            wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte()
            wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()
            wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte()
            wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
            putInt(16, 16)                      // tamaño del chunk fmt
            putShort(20, 1)                     // PCM
            putShort(22, 1)                     // mono
            putInt(24, sampleRate)
            putInt(28, byteRate)
            putShort(32, 2)                     // block align
            putShort(34, 16)                    // bits por muestra
            wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte()
            wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
            putInt(40, totalLen)
            System.arraycopy(pcm, 0, wav, 44, totalLen)
            return wav
        }
    }
}
