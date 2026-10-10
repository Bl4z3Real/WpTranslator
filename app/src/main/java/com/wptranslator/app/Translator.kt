package com.wptranslator.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Engine { GOOGLE, BING }

data class TranslationResult(val text: String, val detected: String?)

/**
 * Usa gli endpoint pubblici "web" di Google Translate e di Microsoft Edge/Bing Translator
 * (nessuna chiave API). Sono non ufficiali: possono cambiare o avere limiti di utilizzo.
 */
object Translator {
    private var bingToken: String? = null
    private var bingTokenTime = 0L

    fun translate(engine: Engine, text: String, from: Lang?, to: Lang): TranslationResult =
        when (engine) {
            Engine.GOOGLE -> google(text, from, to)
            Engine.BING -> bing(text, from, to)
        }

    private fun google(text: String, from: Lang?, to: Lang): TranslationResult {
        val sl = from?.google ?: "auto"
        val q = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=$sl&tl=${to.google}&dt=t&q=$q"
        val body = http(url, "GET", null, null)
        val root = JSONArray(body)
        val parts = root.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONArray(i) ?: continue
            if (p.isNull(0)) continue
            sb.append(p.getString(0))
        }
        val detected = if (from == null) root.optString(2, "").ifBlank { null } else null
        return TranslationResult(sb.toString(), detected)
    }

    private fun bingToken(): String {
        val now = System.currentTimeMillis()
        val cached = bingToken
        if (cached != null && now - bingTokenTime < 8 * 60 * 1000) return cached
        val tok = http("https://edge.microsoft.com/translate/auth", "GET", null, null).trim()
        bingToken = tok
        bingTokenTime = now
        return tok
    }

    private fun bing(text: String, from: Lang?, to: Lang): TranslationResult {
        val fromParam = if (from == null) "" else "&from=${from.bing}"
        val url = "https://api-edge.cognitive.microsofttranslator.com/translate" +
            "?api-version=3.0$fromParam&to=${to.bing}"
        val payload = JSONArray().put(JSONObject().put("Text", text)).toString()
        val body = http(url, "POST", payload, "Bearer ${bingToken()}")
        val item = JSONArray(body).getJSONObject(0)
        val out = item.getJSONArray("translations").getJSONObject(0).getString("text")
        val detected = item.optJSONObject("detectedLanguage")?.optString("language")
        return TranslationResult(out, detected)
    }

    private fun http(url: String, method: String, body: String?, auth: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            if (auth != null) c.setRequestProperty("Authorization", auth)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                if (code == 401) bingToken = null
                throw RuntimeException("HTTP $code")
            }
            return text
        } finally {
            c.disconnect()
        }
    }
}
