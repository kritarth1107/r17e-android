package com.r17e.guide.live

import android.util.Log
import com.r17e.guide.backend.LiveCredentials
import okhttp3.*
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** One Gemini Live websocket. Events arrive on OkHttp's reader thread. */
class LiveSession(
    private val creds: LiveCredentials,
    private val systemPrompt: String,
    private val tools: JSONArray,
    private val listener: Listener,
    private val resumeHandle: String? = null,
    client: OkHttpClient = defaultClient,
) {
    interface Listener {
        fun onEvent(e: LiveEvent)
        fun onClosed(reason: String, error: Throwable?)
    }

    @Volatile var ready = false; private set
    @Volatile private var closedByUs = false
    private val ws: WebSocket = client.newWebSocket(
        Request.Builder().url(creds.websocketUrl).header("Authorization", "Bearer ${creds.accessToken}").build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(LiveProtocol.setup(creds.modelResource, systemPrompt, tools, resumeHandle = resumeHandle))
            }
            override fun onMessage(webSocket: WebSocket, text: String) = dispatch(text)
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = dispatch(bytes.utf8())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (closedByUs) return
                ready = false; listener.onClosed("closed $code $reason", null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closedByUs) return
                ready = false; Log.w(TAG, "live failure ${response?.code}", t)
                listener.onClosed("failure ${response?.code ?: ""}", t)
            }
        })

    private fun dispatch(text: String) {
        for (e in LiveProtocol.parse(text)) {
            if (e is LiveEvent.SetupComplete) ready = true
            listener.onEvent(e)
        }
    }

    fun sendAudio(buf: ByteArray, len: Int) { if (ready) ws.send(LiveProtocol.audioChunk(buf, len)) }
    fun sendText(text: String) { if (ready) ws.send(LiveProtocol.userText(text)) }
    fun send(raw: String) = ws.send(raw)
    fun close() { closedByUs = true; ready = false; ws.close(1000, "bye") }

    companion object {
        private const val TAG = "R17E_LIVE"
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
        }
    }
}
