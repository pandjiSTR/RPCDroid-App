package dev.rpcdroid.nr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.rpcdroid.nr.GameCatalog
import dev.rpcdroid.nr.UiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GamesScreen(m: UiModel, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val list = m.games.filter { it.name.contains(query, ignoreCase = true) }
    val active = m.games.count { it.pkg !in m.disabled }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Game", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp))
            Text(
                "$active dari ${m.games.size} aktif",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Cari game") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (list.isEmpty()) {
                Text(
                    if (m.games.isEmpty()) "Belum ada game dari daftar bawaan yang terpasang. Tambahkan app lewat tombol di bawah."
                    else "Tidak ada yang cocok.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.pkg }) { g -> GameRow(m, g) }
            }
        }
        ExtendedFloatingActionButton(
            text = { Text("Tambah app") }, icon = { Icon(Icons.Filled.Add, null) }, onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (showAdd) AddAppDialog(m) { showAdd = false }
}

@Composable
private fun GameRow(m: UiModel, g: GameCatalog.Game) {
    val isCustom = g.pkg in m.custom && !m.catalog.isBuiltin(g.pkg)
    ListItem(
        leadingContent = { AppIcon(g.pkg) },
        headlineContent = { Text(g.name) },
        supportingContent = {
            Text(
                when {
                    isCustom -> "App kustom · tanpa ikon Discord"
                    g.appId != GameCatalog.FALLBACK_APP_ID -> "Terdaftar di Discord"
                    g.icon != null -> "Ikon dari URL"
                    else -> "Tanpa ikon Discord"
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCustom) IconButton({ m.removeCustom(g.pkg) }) { Icon(Icons.Filled.Delete, "Hapus") }
                Switch(g.pkg !in m.disabled, { m.setGameOn(g.pkg, it) })
            }
        },
    )
}

@Composable
private fun AddAppDialog(m: UiModel, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val apps by produceState(emptyList<GameCatalog.AppEntry>()) {
        value = withContext(Dispatchers.Default) { m.catalog.launchable() }
    }
    val shown = apps.filter { !m.catalog.isKnown(it.pkg) && it.name.contains(q, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tambah app") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Cari app") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        ListItem(
                            leadingContent = { AppIcon(a.pkg) },
                            headlineContent = { Text(a.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { m.addCustom(a.pkg); onClose() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Tutup") } },
    )
}
