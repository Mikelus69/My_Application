package de.touchgrass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        WakeUpHint()
    }
}

// Render legt den Server nach 15 Minuten ohne Anfrage schlafen, das Aufwachen dauert etwa eine Minute.
@Composable
fun WakeUpHint() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        visible = true
    }
    if (visible) {
        Text("Der Server wacht gerade auf, das kann bis zu einer Minute dauern.", style = MaterialTheme.typography.bodySmall)
    }
}
