package dev.rpcdroid.nr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rpcdroid.nr.ui.GamesScreen
import dev.rpcdroid.nr.ui.HomeScreen
import dev.rpcdroid.nr.ui.OnboardingScreen
import dev.rpcdroid.nr.ui.RpcTheme
import dev.rpcdroid.nr.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var model: UiModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = UiModel(applicationContext)
        setContent { RpcTheme { Root(model) } }
    }

    override fun onDestroy() { model.dispose(); super.onDestroy() }
}

private enum class Dest(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Filled.Home),
    GAMES("Game", Icons.AutoMirrored.Filled.List),
    SETTINGS("Pengaturan", Icons.Filled.Settings),
}

@Composable
private fun Root(m: UiModel) {
    // Kembali dari pengaturan sistem / login: baca ulang izin & token
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { m.refresh(); m.applyMode() }
    LaunchedEffect(m.tick) { m.loadGames() }

    if (!m.onboarded) OnboardingScreen(m) else MainScaffold(m)
}

@Composable
private fun MainScaffold(m: UiModel) {
    var idx by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = idx == i, onClick = { idx = i },
                        icon = { Icon(d.icon, d.label) }, label = { Text(d.label) },
                    )
                }
            }
        },
    ) { pad ->
        val mod = Modifier.padding(pad)
        when (Dest.entries[idx]) {
            Dest.HOME -> HomeScreen(m, mod) { idx = Dest.SETTINGS.ordinal }
            Dest.GAMES -> GamesScreen(m, mod)
            Dest.SETTINGS -> SettingsScreen(m, mod)
        }
    }
}
