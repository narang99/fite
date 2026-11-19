package dev.fiteduck.phoneagent

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PushToTalkScreen(
    status: Status,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onShowFloatingMic: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TalkButton(listening = status == Status.Listening, onPress, onRelease)
        Text(
            status.message(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onShowFloatingMic) { Text("Show floating mic") }
    }
}

@Composable
private fun TalkButton(listening: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val color = if (listening) Color(0xFFD93025) else Color(0xFF1F6FEB)
    Box(
        Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Hold to talk", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
