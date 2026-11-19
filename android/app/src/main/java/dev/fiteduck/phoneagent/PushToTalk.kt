package dev.fiteduck.phoneagent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MIN_SECONDS = 0.3

sealed interface Status {
    data object Idle : Status

    data object Listening : Status

    data object Sending : Status

    data class Heard(val text: String) : Status

    data class Failed(val reason: String) : Status
}

/** Press starts the microphone, release sends what was said to the host. */
class PushToTalk(
    private val scope: CoroutineScope,
    private val host: HostClient,
    private val onStatus: (Status) -> Unit,
) {
    private var status: Status = Status.Idle
        set(value) {
            field = value
            onStatus(value)
        }

    private val recorder = Recorder()

    fun pressed() {
        recorder.start()
        status = Status.Listening
    }

    fun released() {
        if (status != Status.Listening) return
        val recording = recorder.stop()
        if (recording.seconds < MIN_SECONDS) {
            status = Status.Failed("hold the button while you speak")
            return
        }
        status = Status.Sending
        scope.launch { status = host.transcribe(recording.wav).toStatus() }
    }
}

private fun Transcript.toStatus(): Status = when (this) {
    is Transcript.Heard -> Status.Heard(text)
    is Transcript.Failed -> Status.Failed(reason)
}

fun Status.message(): String = when (this) {
    Status.Idle -> ""
    Status.Listening -> "Listening"
    Status.Sending -> "Sending"
    is Status.Heard -> text.ifBlank { "(heard nothing)" }
    is Status.Failed -> reason
}
