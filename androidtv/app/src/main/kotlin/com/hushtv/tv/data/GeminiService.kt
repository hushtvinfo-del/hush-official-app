package com.hushtv.tv.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Natural-language library search powered by Gemini.
 *
 *   User: "movies based on true stories about survival"
 *   Gemini → ["The Revenant", "127 Hours", "Cast Away", ...]
 *   We fuzzy-match those titles against the user's Xtream catalog and return
 *   only the ones they can actually play.
 */
object GeminiService {

    private const val URL = "https://generativelanguage.googleapis.com/v1beta/models/${ApiKeys.GEMINI_MODEL}:generateContent"
    private const val JSON_MIME = "application/json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /**
     * @param query Natural-language user query.
     * @param kind "movies" | "series" — hints Gemini at what to return.
     * @return Up to 30 popular title strings matching the query.
     */
    suspend fun suggestTitles(query: String, kind: String = "movies"): List<String> =
        withContext(Dispatchers.IO) {
            val prompt = buildPrompt(query.trim(), kind)
            val bodyJson = """
                {
                  "contents":[{"parts":[{"text":${escape(prompt)}}]}],
                  "generationConfig":{
                    "temperature":0.4,
                    "responseMimeType":"application/json"
                  }
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("$URL?key=${ApiKeys.GEMINI}")
                .post(bodyJson.toRequestBody(JSON_MIME.toMediaType()))
                .build()

            runCatching {
                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: return@runCatching emptyList()
                if (!resp.isSuccessful) return@runCatching emptyList()
                parseResponse(respBody)
            }.getOrDefault(emptyList())
        }

    private fun buildPrompt(query: String, kind: String): String {
        val target = if (kind == "series") "TV series" else "movies"
        return """
            You are a film expert curating $target recommendations for a user browsing their personal library.
            
            User query: "$query"
            
            Return a JSON object with a single key "titles" whose value is an ARRAY of up to 30 popular, well-known $target that best match the query. Include titles from every era (classic → current) if relevant. Only include the exact $target title — no year, no year in parentheses, no extra punctuation. Example:
            {"titles":["The Matrix","Inception","Interstellar"]}
            
            If the query is vague, lean towards the most critically acclaimed and widely-watched ${target.lowercase()}. Return JSON only — no prose, no markdown fences.
        """.trimIndent()
    }

    /** Escape a string for safe embedding inside a JSON string literal. */
    private fun escape(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\""

    @JsonClass(generateAdapter = true)
    private data class GeminiPart(val text: String? = null)
    @JsonClass(generateAdapter = true)
    private data class GeminiContent(val parts: List<GeminiPart> = emptyList())
    @JsonClass(generateAdapter = true)
    private data class GeminiCandidate(val content: GeminiContent? = null)
    @JsonClass(generateAdapter = true)
    private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())
    @JsonClass(generateAdapter = true)
    private data class TitlesEnvelope(val titles: List<String> = emptyList())

    private fun parseResponse(raw: String): List<String> {
        val envelope = runCatching {
            moshi.adapter(GeminiResponse::class.java).fromJson(raw)
        }.getOrNull() ?: return emptyList()
        val firstText = envelope.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: return emptyList()
        // Sometimes Gemini wraps JSON in markdown code fences, strip them.
        val cleaned = firstText
            .trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching {
            moshi.adapter(TitlesEnvelope::class.java).fromJson(cleaned)
        }.getOrNull()?.titles?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    /**
     * Match Gemini's suggestions against your Xtream library.
     * Returns library cards in the order Gemini ranked them, deduping repeats.
     */
    fun matchAgainstLibrary(
        suggestions: List<String>,
        library: List<MediaCard>,
    ): List<MediaCard> {
        if (suggestions.isEmpty() || library.isEmpty()) return emptyList()
        val lowerLib = library.map { it to it.title.lowercase() }
        val seen = mutableSetOf<String>()
        val out = mutableListOf<MediaCard>()
        suggestions.forEach { s ->
            val target = s.lowercase()
            val hit = lowerLib.firstOrNull { (_, lt) ->
                lt == target || lt.contains(target) || target.contains(lt)
            }?.first
            if (hit != null && seen.add(hit.id)) out += hit
        }
        return out
    }
}
