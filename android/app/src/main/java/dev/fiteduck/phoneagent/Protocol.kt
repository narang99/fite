package dev.fiteduck.phoneagent

import org.json.JSONObject

/** The wire messages from handoff.md that the voice path needs. */
private const val PROTOCOL_VERSION = 0

fun helloMessage(token: String): String = JSONObject()
    .put("type", "hello")
    .put("v", PROTOCOL_VERSION)
    .put("token", token)
    .put("device", JSONObject().put("model", android.os.Build.MODEL))
    .toString()

fun utteranceHeader(id: String): String = JSONObject()
    .put("type", "utterance")
    .put("v", PROTOCOL_VERSION)
    .put("id", id)
    .put("format", "wav")
    .put("sample_rate", SAMPLE_RATE)
    .toString()

sealed interface HostMessage {
    data object HelloOk : HostMessage

    data class Heard(val id: String, val text: String) : HostMessage

    data class Failed(val id: String, val reason: String) : HostMessage

    data object Ignored : HostMessage
}

fun parseHostMessage(text: String): HostMessage {
    val json = JSONObject(text)
    return when (json.getString("type")) {
        "hello_ok" -> HostMessage.HelloOk
        "result" -> parseResult(json)
        else -> HostMessage.Ignored
    }
}

private fun parseResult(json: JSONObject): HostMessage {
    val id = json.getString("id")
    if (json.getBoolean("ok")) {
        return HostMessage.Heard(id, json.getJSONObject("data").getString("text"))
    }
    return HostMessage.Failed(id, json.getJSONObject("error").getString("message"))
}
