package de.touchgrass.ui.record

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.TimerStore
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

fun formatElapsed(duration: Duration): String {
    val seconds = duration.seconds
    return "%02d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
fun rememberNow(ticking: Boolean): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(ticking) {
        now = Instant.now()
        while (ticking) {
            delay(500)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun TimerTabLabel(timerStore: TimerStore, label: String) {
    val timer by timerStore.state.collectAsStateWithLifecycle(initialValue = null)
    val now = rememberNow(ticking = timer?.isPaused == false)
    Text(timer?.let { formatElapsed(it.elapsed(now)) } ?: label)
}
