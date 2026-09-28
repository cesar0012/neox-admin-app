package com.example.data.rag

import com.example.data.db.MemoryDao
import com.example.data.model.MemoryChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

data class RAGQueryResult(
    val chunk: MemoryChunk,
    val score: Double,
    val formattedCitation: String
)

class RAGMemoryEngine(private val memoryDao: MemoryDao) {

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    /**
     * Chunks and indexes text content from meetings, documents, or voice notes.
     */
    suspend fun indexContent(
        sourceId: Long,
        sourceType: String,
        title: String,
        jobTag: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.Default) {
        // First clear any previous chunks from this same source if updating
        memoryDao.deleteChunksBySource(sourceId, sourceType)

        val cleanText = content.trim()
        if (cleanText.isEmpty()) return@withContext

        // Segment into overlapping semantic chunks (~200 chars / 35-50 words)
        val sentences = cleanText.split(Regex("(?<=[.!?\\n])\\s+")).filter { it.isNotBlank() }
        val chunksToInsert = mutableListOf<MemoryChunk>()

        var currentChunkText = StringBuilder()
        var chunkIndex = 0
        val dateStr = dateFormatter.format(Date(timestamp))

        for (sentence in sentences) {
            if (currentChunkText.length + sentence.length > 280 && currentChunkText.isNotEmpty()) {
                val fullChunk = currentChunkText.toString().trim()
                val keywords = extractKeywords(fullChunk)
                val quote = if (fullChunk.length > 140) fullChunk.take(140) + "..." else fullChunk

                chunksToInsert.add(
                    MemoryChunk(
                        sourceId = sourceId,
                        sourceType = sourceType,
                        title = title,
                        jobTag = jobTag,
                        chunkIndex = chunkIndex++,
                        content = fullChunk,
                        keywords = keywords,
                        exactQuote = quote,
                        dateString = dateStr,
                        timestamp = timestamp
                    )
                )
                currentChunkText = StringBuilder()
            }
            if (currentChunkText.isNotEmpty()) currentChunkText.append(" ")
            currentChunkText.append(sentence)
        }

        if (currentChunkText.isNotEmpty()) {
            val fullChunk = currentChunkText.toString().trim()
            val keywords = extractKeywords(fullChunk)
            val quote = if (fullChunk.length > 140) fullChunk.take(140) + "..." else fullChunk

            chunksToInsert.add(
                MemoryChunk(
                    sourceId = sourceId,
                    sourceType = sourceType,
                    title = title,
                    jobTag = jobTag,
                    chunkIndex = chunkIndex,
                    content = fullChunk,
                    keywords = keywords,
                    exactQuote = quote,
                    dateString = dateStr,
                    timestamp = timestamp
                )
            )
        }

        if (chunksToInsert.isNotEmpty()) {
            memoryDao.insertChunks(chunksToInsert)
        }
    }

    /**
     * Vector Space RAG Retrieval using TF-IDF + Cosine Similarity & Keyword Boost.
     * Dramatically reduces tokens by returning only top-K relevant chunks with exact citations.
     */
    suspend fun queryMemory(query: String, topK: Int = 4, jobFilter: String? = null): List<RAGQueryResult> = withContext(Dispatchers.Default) {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val rawChunks = memoryDao.getAllChunksList()
        val allChunks = if (!jobFilter.isNullOrBlank() && jobFilter != "Todos") {
            rawChunks.filter { it.jobTag.equals(jobFilter, ignoreCase = true) }
        } else {
            rawChunks
        }
        if (allChunks.isEmpty()) return@withContext emptyList()

        // 1. Calculate Document Frequencies (DF) across chunks
        val docCount = allChunks.size.toDouble()
        val docFrequencies = mutableMapOf<String, Int>()

        val chunkTokensList = allChunks.map { chunk ->
            val tokens = tokenize("${chunk.title} ${chunk.content} ${chunk.keywords} ${chunk.jobTag}")
            val distinctTokens = tokens.toSet()
            for (token in distinctTokens) {
                docFrequencies[token] = (docFrequencies[token] ?: 0) + 1
            }
            tokens
        }

        // 2. Compute query vector (TF-IDF)
        val queryTf = mutableMapOf<String, Int>()
        for (token in queryTokens) {
            queryTf[token] = (queryTf[token] ?: 0) + 1
        }

        val queryVector = mutableMapOf<String, Double>()
        var queryNormSq = 0.0
        for ((term, tf) in queryTf) {
            val df = docFrequencies[term] ?: 1
            val idf = ln((docCount + 1.0) / (df + 0.5)) + 1.0
            val weight = (tf.toDouble()) * idf
            queryVector[term] = weight
            queryNormSq += weight * weight
        }
        val queryNorm = sqrt(queryNormSq).coerceAtLeast(1e-6)

        // 3. Score each chunk with Cosine Similarity + Keyword Match Boost
        val scoredList = mutableListOf<RAGQueryResult>()

        for (i in allChunks.indices) {
            val chunk = allChunks[i]
            val tokens = chunkTokensList[i]
            if (tokens.isEmpty()) continue

            val docTf = mutableMapOf<String, Int>()
            for (t in tokens) {
                docTf[t] = (docTf[t] ?: 0) + 1
            }

            var dotProduct = 0.0
            var docNormSq = 0.0

            for ((term, tf) in docTf) {
                val df = docFrequencies[term] ?: 1
                val idf = ln((docCount + 1.0) / (df + 0.5)) + 1.0
                val weight = tf.toDouble() * idf
                docNormSq += weight * weight

                val qWeight = queryVector[term]
                if (qWeight != null) {
                    dotProduct += weight * qWeight
                }
            }

            val docNorm = sqrt(docNormSq).coerceAtLeast(1e-6)
            var cosineSim = dotProduct / (queryNorm * docNorm)

            // Direct title or job tag match boost
            val queryLower = query.lowercase(Locale.ROOT)
            if (chunk.title.lowercase(Locale.ROOT).contains(queryLower)) {
                cosineSim += 0.35
            }
            if (chunk.jobTag.lowercase(Locale.ROOT).contains(queryLower)) {
                cosineSim += 0.20
            }

            if (cosineSim > 0.05) {
                val formattedCitation = "[Ref: ${chunk.sourceType} \"${chunk.title}\" (${chunk.dateString}) | Proyecto: ${chunk.jobTag}] \"${chunk.exactQuote}\""
                scoredList.add(RAGQueryResult(chunk, cosineSim, formattedCitation))
            }
        }

        scoredList.sortedByDescending { it.score }.take(topK)
    }

    private fun extractKeywords(text: String): String {
        val stopwords = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "a", "al", "en", "con", "por",
            "para", "que", "se", "es", "son", "y", "o", "pero", "si", "no", "este", "esta", "estos", "estas",
            "the", "and", "or", "to", "in", "at", "for", "on", "is", "are", "was", "with", "as", "by", "that"
        )
        return tokenize(text)
            .filter { it.length > 2 && it !in stopwords }
            .distinct()
            .take(15)
            .joinToString(", ")
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-záéíóúñü0-9]+"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
    }
}
