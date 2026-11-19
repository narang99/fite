package dev.fiteduck.phoneagent

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.UUID
import kotlin.coroutines.resume

private const val TIMEOUT_MS = 30_000L

sealed interface Transcript {
    data class Heard(val text: String) : Transcript

    data class Failed(val reason: String) : Transcript
}

/** The phone dials out, as the protocol requires. One connection carries one utterance. */
class HostClient(private val url: String, private val token: String) {
    private val http = OkHttpClient()

    suspend fun transcribe(wav: ByteArray): Transcript = try {
        withTimeout(TIMEOUT_MS) { dialAndSend(wav) }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        Transcript.Failed("the host did not answer")
    }

    private suspend fun dialAndSend(wav: ByteArray): Transcript =
        suspendCancellableCoroutine { continuation ->
            val listener = UtteranceListener(token, wav) { transcript ->
                if (continuation.isActive) continuation.resume(transcript)
            }
            val socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
            continuation.invokeOnCancellation { socket.cancel() }
        }
}

/** Says hello, then sends the utterance once the host accepts it, and reports the first answer. */
private class UtteranceListener(
    private val token: String,
    private val wav: ByteArray,
    private val onDone: (Transcript) -> Unit,
) : WebSocketListener() {
    private val id = UUID.randomUUID().toString()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send(helloMessage(token))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        when (val message = parseHostMessage(text)) {
            HostMessage.HelloOk -> sendUtterance(webSocket)
            is HostMessage.Heard -> finish(webSocket, Transcript.Heard(message.text))
            is HostMessage.Failed -> finish(webSocket, Transcript.Failed(message.reason))
            HostMessage.Ignored -> Unit
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        onDone(Transcript.Failed("could not reach the host: ${t.message}"))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onDone(Transcript.Failed("the host closed the connection: $reason"))
    }

    private fun sendUtterance(webSocket: WebSocket) {
        webSocket.send(utteranceHeader(id))
        webSocket.send(wav.toByteString())
    }

    private fun finish(webSocket: WebSocket, transcript: Transcript) {
        onDone(transcript)
        webSocket.close(1000, null)
    }
}
