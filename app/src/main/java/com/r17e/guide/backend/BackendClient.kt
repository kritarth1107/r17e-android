package com.r17e.guide.backend

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown for any non-2xx backend reply. [code] is the HTTP status, [error] the backend's error field. */
class BackendException(val code: Int, val error: String?) : IOException("backend $code: ${error ?: "?"}")

data class Registration(val deviceId: String, val deviceToken: String)

/** Minimal blocking client for r17e-backend. Call from a background thread. */
class BackendClient(
    private val baseUrl: String,
    private val http: Http = UrlConnectionHttp(),
) {
    /** Transport seam so parsing can be unit tested without a server. */
    fun interface Http {
        /** Returns (status, body). */
        fun post(url: String, json: String, bearer: String?): Pair<Int, String>
    }

    fun register(installId: String, model: String?, appVersion: String?): Registration {
        val body = JSONObject().apply {
            put("installId", installId); put("platform", "android")
            model?.let { put("model", it.take(128)) }
            appVersion?.let { put("appVersion", it.take(64)) }
        }
        val o = call("/v1/devices/register", body, null)
        return Registration(o.getString("deviceId"), o.getString("deviceToken"))
    }

    internal fun call(path: String, body: JSONObject, bearer: String?): JSONObject {
        val (code, text) = http.post(baseUrl.trimEnd('/') + path, body.toString(), bearer)
        val o = runCatching { JSONObject(text) }.getOrNull()
        if (code !in 200..299) throw BackendException(code, o?.optString("error")?.ifEmpty { null })
        return o ?: throw BackendException(code, "bad_json")
    }
}

class UrlConnectionHttp(private val timeoutMs: Int = 10_000) : BackendClient.Http {
    override fun post(url: String, json: String, bearer: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json")
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            c.outputStream.use { it.write(json.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally { c.disconnect() }
    }
}
