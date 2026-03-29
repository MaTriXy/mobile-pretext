package io.github.matrixy.pretext.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.matrixy.pretext.Pretext
import io.github.matrixy.pretext.android.IcuTextSegmenter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Pretext.setSegmenter(IcuTextSegmenter())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PretextDemoApp()
                }
            }
        }
    }
}

// ── Route definitions ──────────────────────────────────────────────

private enum class DemoRoute(
    val title: String,
    val subtitle: String,
    val icon: String
) {
    HEIGHT("Height Measurement", "Compute paragraph height without layout", "\uD83D\uDCCF"),
    LINES("Line Breaking", "See each line from layoutWithLines()", "\u2702\uFE0F"),
    MULTI("Multi-Language", "Hebrew, Arabic, CJK, Thai, Emoji, Bidi", "\uD83C\uDF10"),
    VARIABLE("Variable Width", "Text flowing around obstacles", "\uD83D\uDDB2"),
    RICH("Rich Note", "Mixed inline elements with live reflow", "\uD83D\uDCDD"),
    PERF("Performance Test", "Live layout speed with 200 texts", "\u26A1"),
    BREAKER("Pretext Breaker", "Text-based brick breaker game", "\uD83C\uDFAE"),
}

// ── App shell with NavHost ─────────────────────────────────────────

@Composable
fun PretextDemoApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            DemoListScreen(onDemoSelected = { route ->
                if (route == DemoRoute.BREAKER) {
                    // Breaker uses its own Activity (SurfaceView)
                    // handled via intent below
                } else {
                    navController.navigate(route.name)
                }
            })
        }
        composable(DemoRoute.HEIGHT.name) {
            DemoScreenWrapper("Height Measurement", onBack = { navController.popBackStack() }) {
                HeightMeasurementDemo()
            }
        }
        composable(DemoRoute.LINES.name) {
            DemoScreenWrapper("Line Breaking", onBack = { navController.popBackStack() }) {
                LineBreakingDemo()
            }
        }
        composable(DemoRoute.MULTI.name) {
            DemoScreenWrapper("Multi-Language", onBack = { navController.popBackStack() }) {
                MultiLanguageDemo()
            }
        }
        composable(DemoRoute.VARIABLE.name) {
            DemoScreenWrapper("Variable Width", onBack = { navController.popBackStack() }) {
                VariableWidthDemo()
            }
        }
        composable(DemoRoute.RICH.name) {
            DemoScreenWrapper("Rich Note", onBack = { navController.popBackStack() }) {
                RichNoteDemo()
            }
        }
        composable(DemoRoute.PERF.name) {
            DemoScreenWrapper("Performance Test", onBack = { navController.popBackStack() }, scrollable = false) {
                PerformanceBenchmark()
            }
        }
    }
}

// ── Home screen with demo cards ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoListScreen(onDemoSelected: (DemoRoute) -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mobile-Pretext", fontWeight = FontWeight.Bold)
                        Text(
                            "Pure-arithmetic text measurement for Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(DemoRoute.entries.toList()) { route ->
                DemoCard(
                    icon = route.icon,
                    title = route.title,
                    subtitle = route.subtitle,
                    onClick = {
                        if (route == DemoRoute.BREAKER) {
                            context.startActivity(
                                Intent(context, io.github.matrixy.pretext.sample.breaker.PretextBreakerActivity::class.java)
                            )
                        } else {
                            onDemoSelected(route)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DemoCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "\u203A",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Wrapper for individual demo screens ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreenWrapper(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            "\u2190 Back",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            content()
        }
    }
}
