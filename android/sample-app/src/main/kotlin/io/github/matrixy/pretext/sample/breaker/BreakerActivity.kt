package io.github.matrixy.pretext.sample.breaker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.IcuTextSegmenter

private val DarkBg = Color(0xFF02060D)
private val WarmText = Color(0xFFF6F2DF)
private val HeartRed = Color(0xFFFF6666)

class PretextBreakerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Pretext.setSegmenter(IcuTextSegmenter())

        setContent {
            BreakerScreen(onExit = { finish() })
        }
    }
}

@Composable
private fun BreakerScreen(onExit: () -> Unit) {
    // Hold a reference to the game view to read state
    var gameView by remember { mutableStateOf<BreakerGameView?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }

    // Poll game state for toolbar (game runs on its own thread)
    LaunchedEffect(gameView) {
        while (true) {
            gameView?.let {
                score = it.score
                lives = it.lives
                level = it.level
            }
            kotlinx.coroutines.delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .systemBarsPadding() // Respects notch, status bar, nav bar
    ) {
        // Toolbar — two rows below the notch
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBg)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Row 1: EXIT + PRETEXT BREAKER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("EXIT", color = WarmText.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.weight(1f))
                Text("PRETEXT BREAKER", color = WarmText.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                // Hearts
                Text(
                    "\u2665".repeat(lives.coerceAtLeast(0)),
                    color = HeartRed,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Row 2: Score + Level
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SCORE: $score", color = WarmText, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("LEVEL $level", color = WarmText, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // Game canvas — fills the rest, no inset issues
        AndroidView(
            factory = { context ->
                BreakerGameView(context).also {
                    it.skipHud = true  // HUD is handled by Compose toolbar
                    gameView = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
