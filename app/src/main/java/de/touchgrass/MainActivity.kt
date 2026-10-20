package de.touchgrass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.touchgrass.ui.TouchGrassRoot
import de.touchgrass.ui.theme.TouchGrassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TouchGrassApp).container
        setContent {
            TouchGrassTheme {
                TouchGrassRoot(container)
            }
        }
    }
}
