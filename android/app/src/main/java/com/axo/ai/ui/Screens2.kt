package com.axo.ai.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axo.ai.AppContainer
import com.axo.ai.data.Assistant
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {}, body: @Composable (PaddingValues) -> Unit) =
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) }, actions = actions,
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
        floatingActionButton = fab, content = body,
    )

@Composable
fun HistoryScreen(vm: LibraryVM, back: () -> Unit, open: (String) -> Unit) {
    val chats by vm.chats.collectAsState()
    val q by vm.query.collectAsState()
    val archived by vm.archived.collectAsState()
    var renaming by remember { mutableStateOf<com.axo.ai.data.Chat?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    Page(if (archived) "Archived chats" else "Chat history", back, actions = {
        IconButton({ vm.archived.value = !archived }) { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, "Toggle archived") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                q, { vm.query.value = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                placeholder = { Text("Search chats") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp),
            )
            if (chats.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (q.isNotEmpty()) "No chats match your search." else "No chats yet. Start a new conversation!")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chats, key = { it.id }) { ch ->
                    Card(Modifier.fillMaxWidth().clickable { open(ch.id) }, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.title, Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 2)
                            IconButton({ menuFor = ch.id }) { Icon(Icons.Default.MoreVert, "Options") }
                            DropdownMenu(menuFor == ch.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("Rename") }, { renaming = ch; menuFor = null })
                                DropdownMenuItem({ Text(if (ch.archived) "Unarchive" else "Archive") }, { vm.archive(ch.id, !ch.archived); menuFor = null })
                                DropdownMenuItem({ Text("Delete") }, { vm.deleteChat(ch.id); menuFor = null })
                            }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { ch ->
        var t by remember(ch.id) { mutableStateOf(ch.title) }
        AlertDialog(
            onDismissRequest = { renaming = null }, title = { Text("Rename chat") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.rename(ch.id, t); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ExploreScreen(vm: LibraryVM, back: () -> Unit, open: (String) -> Unit, create: (String?) -> Unit) {
    val list by vm.assistants.collectAsState()
    var menuFor by remember { mutableStateOf<String?>(null) }
    Page("Explore GPTs", back, fab = {
        ExtendedFloatingActionButton({ create(null) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Create") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().clickable { open(a.id) }, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(neonBrush.let { androidx.compose.ui.graphics.Color(0x338B5CF6) }), Alignment.Center) { Text(a.icon, fontSize = 24.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(a.instructions, maxLines = 2, fontSize = 12.sp)
                        }
                        if (!a.builtIn) {
                            IconButton({ menuFor = a.id }) { Icon(Icons.Default.MoreVert, "Options") }
                            DropdownMenu(menuFor == a.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("Edit") }, { menuFor = null; create(a.id) })
                                DropdownMenuItem({ Text("Delete") }, { menuFor = null; vm.deleteAssistant(a.id) })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
fun CreateAssistantScreen(vm: LibraryVM, id: String?, done: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🤖") }
    var instr by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { id?.let { vm.assistant(it) }?.let { name = it.name; icon = it.icon; instr = it.instructions } }
    Page(if (id == null) "New assistant" else "Edit assistant", done) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Icon")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🤖", "🧠", "💻", "📚", "🎨", "🎮", "🌍", "🧑‍🏫", "⚖️", "🍳", "💪", "🚀").forEach {
                    FilterChip(icon == it, { icon = it }, label = { Text(it, fontSize = 22.sp) })
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instr, { instr = it }, label = { Text("Custom instructions") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            NeonButton("Save assistant") {
                if (name.isBlank() || instr.isBlank()) err = "Give your assistant a name and instructions."
                else { vm.saveAssistant(Assistant(id ?: UUID.randomUUID().toString(), name.trim(), icon, instr.trim(), false)); done() }
            }
        }
    }
}

@Composable
fun SettingsScreen(c: AppContainer, vm: LibraryVM, back: () -> Unit, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by c.prefs.theme.collectAsState()
    val font by c.prefs.fontSize.collectAsState()
    val instr by c.prefs.instructions.collectAsState()
    val error by vm.error.collectAsState()
    val user = remember { c.tokens.user() }
    var confirm by remember { mutableStateOf<String?>(null) }

    @Composable fun H(t: String) = Text(t, color = Neon3, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))

    Page("Settings", back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            H("Account")
            Text(user?.name?.ifBlank { "Guest" } ?: "Guest", style = MaterialTheme.typography.titleMedium)
            Text(user?.email?.ifBlank { "Guest mode: chats stay on this device" } ?: "", fontSize = 13.sp)
            OutlinedButton({ vm.signOut(onSignedOut) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Sign out") }

            H("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (k, l) ->
                    FilterChip(theme == k, { c.prefs.setTheme(k) }, label = { Text(l) })
                }
            }
            Text("Chat font size: ${font}sp", Modifier.padding(top = 10.dp))
            Slider(font.toFloat(), { c.prefs.setFont(it.toInt()) }, valueRange = 12f..24f)

            H("Personalization")
            OutlinedTextField(
                instr, { c.prefs.setInstructions(it.take(1500)) }, Modifier.fillMaxWidth(), minLines = 3,
                label = { Text("Custom AI instructions (sent with every chat)") },
            )

            H("Data controls")
            OutlinedButton({
                scope.launch {
                    val txt = vm.exportText()
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Export conversations"))
                }
            }, Modifier.fillMaxWidth()) { Text("Export conversations") }
            OutlinedButton({ confirm = "chats" }, Modifier.fillMaxWidth()) { Text("Delete all conversations") }
            OutlinedButton({ confirm = "local" }, Modifier.fillMaxWidth()) { Text("Clear local data") }
            OutlinedButton({ confirm = "account" }, Modifier.fillMaxWidth()) { Text("Delete account & data", color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            H("Coming soon")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    listOf("Premium models & advanced reasoning", "Higher usage & upload limits", "Advanced voice conversations",
                        "Video generation", "Cloud sync", "Premium themes").forEach { Text("•  $it", Modifier.padding(vertical = 2.dp), fontSize = 13.sp) }
                }
            }

            H("About")
            Text("AXO AI · version 1.0.0")
            Spacer(Modifier.height(40.dp))
        }
    }

    confirm?.let { k ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(when (k) { "chats" -> "Delete all conversations?"; "local" -> "Clear local data?"; else -> "Delete your account?" }) },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton({
                    when (k) {
                        "chats" -> vm.deleteAllChats()
                        "local" -> vm.clearLocal()
                        else -> vm.deleteAccount(onSignedOut)
                    }
                    confirm = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}
