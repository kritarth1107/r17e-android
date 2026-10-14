package com.r17e.guide.live

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class FunctionCall(val id: String, val name: String, val args: JSONObject)

/** One parsed server message from the Gemini Live (Vertex BidiGenerateContent) websocket. */
sealed interface LiveEvent {
    data object SetupComplete : LiveEvent
    class Audio(val pcm24k: ByteArray) : LiveEvent
    data class Transcript(val text: String, val fromModel: Boolean) : LiveEvent
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    data class ToolCalls(val calls: List<FunctionCall>) : LiveEvent
    data class ToolCancel(val ids: List<String>) : LiveEvent
    data class GoAway(val timeLeft: String?) : LiveEvent
}

/** Pure JSON builders/parsers for the Live protocol, kept free of Android so they unit test. */
object LiveProtocol {
    const val INPUT_RATE = 16_000
    const val OUTPUT_RATE = 24_000
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun setup(modelResource: String, systemPrompt: String, tools: JSONArray, voice: String = "Aoede"): String =
        JSONObject().put("setup", JSONObject().apply {
            put("model", modelResource)
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().put("voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
            })
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            if (tools.length() > 0) put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
            put("inputAudioTranscription", JSONObject())
            put("outputAudioTranscription", JSONObject())
        }).toString()

    fun audioChunk(pcm16k: ByteArray, len: Int = pcm16k.size): String =
        JSONObject().put("realtimeInput", JSONObject().put("mediaChunks", JSONArray().put(JSONObject()
            .put("mimeType", "audio/pcm;rate=$INPUT_RATE")
            .put("data", b64e.encodeToString(if (len == pcm16k.size) pcm16k else pcm16k.copyOf(len)))))).toString()

    fun userText(text: String): String =
        JSONObject().put("clientContent", JSONObject()
            .put("turns", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("turnComplete", true)).toString()

    fun toolResponse(responses: List<Pair<FunctionCall, JSONObject>>): String =
        JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().apply {
            responses.forEach { (c, r) -> put(JSONObject().put("id", c.id).put("name", c.name).put("response", r)) }
        })).toString()

    fun parse(text: String): List<LiveEvent> {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<LiveEvent>()
        if (o.has("setupComplete")) out += LiveEvent.SetupComplete
        o.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    p.optJSONObject("inlineData")?.takeIf { it.optString("mimeType").startsWith("audio/") }
                        ?.let { out += LiveEvent.Audio(b64d.decode(it.getString("data"))) }
                }
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }
                ?.let { out += LiveEvent.Transcript(it, fromModel = false) }
            sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }
                ?.let { out += LiveEvent.Transcript(it, fromModel = true) }
            if (sc.optBoolean("interrupted")) out += LiveEvent.Interrupted
            if (sc.optBoolean("turnComplete")) out += LiveEvent.TurnComplete
        }
        o.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { fc ->
            out += LiveEvent.ToolCalls((0 until fc.length()).map { i ->
                val c = fc.getJSONObject(i)
                FunctionCall(c.optString("id"), c.getString("name"), c.optJSONObject("args") ?: JSONObject())
            })
        }
        o.optJSONObject("toolCallCancellation")?.optJSONArray("ids")?.let { ids ->
            out += LiveEvent.ToolCancel((0 until ids.length()).map { ids.getString(it) })
        }
        o.optJSONObject("goAway")?.let { out += LiveEvent.GoAway(it.optString("timeLeft").ifEmpty { null }) }
        return out
    }
}
