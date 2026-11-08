package com.axo.ai.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axo.ai.AppContainer
import com.axo.ai.data.Msg

private val suggestions = listOf(
    "Explain quantum computing in simple terms",
    "Write a Python function to merge two sorted lists",
    "Give me a 3-day workout plan",
    "Help me write a polite email asking for a deadline extension",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(c: AppContainer, vm: ChatVM, onNewChat: () -> Unit, onHistory: () -> Unit, onExplore: () -> Unit, onSettings: () -> Unit) {
    val msgs by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val error by vm.error.collectAsState()
    val models by vm.models.collectAsState()
    val selected by vm.selected.collectAsState()
    val font by c.prefs.fontSize.collectAsState()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Msg?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(msgs.size, streaming) {
        val n = list.layoutInfo.totalItemsCount
        if (n > 0) list.scrollToItem(n - 1)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column(Modifier.clickable { pick = true }) {
                        Logo(18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(models.firstOrNull { it.id == selected }?.label ?: "No model", fontSize = 12.sp)
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(pick, { pick = false }) {
                        if (models.isEmpty()) DropdownMenuItem({ Text("No models configured") }, onClick = { vm.loadModels(); pick = false })
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Column { Text(m.label); Text(m.role, fontSize = 11.sp) } },
                                onClick = { vm.select(m.id); pick = false },
                                trailingIcon = { if (m.id == selected) Icon(Icons.Default.Check, null) },
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onNewChat) { Icon(Icons.Default.Add, "New chat") }
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Chat history") }, { menu = false; onHistory() }, leadingIcon = { Icon(Icons.Default.History, null) })
                        DropdownMenuItem({ Text("Explore GPTs") }, { menu = false; onExplore() }, leadingIcon = { Icon(Icons.Default.Explore, null) })
                        DropdownMenuItem({ Text("Settings") }, { menu = false; onSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (msgs.isEmpty() && streaming == null) item {
                    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Logo(40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("How can I help you today?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        suggestions.forEach {
                            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { vm.send(it) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(it, Modifier.padding(16.dp), fontSize = 14.sp)
                            }
                        }
                    }
                }
                items(msgs, key = { it.id }) { m ->
                    MessageItem(m, isLast = m.id == msgs.last().id && streaming == null, font = font, vm = vm, onEdit = { editing = m })
                }
                streaming?.let { s -> item { Bubble(false) { if (s.isEmpty()) LinearProgressIndicator(Modifier.width(80.dp)) else RichText(s, font) } } }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) } }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message AXO AI") },
                    shape = RoundedCornerShape(24.dp), maxLines = 6,
                )
                Spacer(Modifier.width(8.dp))
                if (streaming != null) FilledIconButton({ vm.stop() }) { Icon(Icons.Default.Stop, "Stop generating") }
                else FilledIconButton({ vm.send(input); input = "" }, enabled = input.isNotBlank() && selected != null) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }

    editing?.let { m ->
        var t by remember(m.id) { mutableStateOf(m.content) }
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("Edit message") },
            text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ vm.edit(m, t); editing = null }) { Text("Save & resend") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Bubble(user: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (user) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 340.dp),
        ) { Box(Modifier.padding(12.dp)) { content() } }
    }
}

@Composable
private fun MessageItem(m: Msg, isLast: Boolean, font: Int, vm: ChatVM, onEdit: () -> Unit) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val user = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Bubble(user) { SelectionContainer { RichText(m.content, font) } }
        Row {
            @Composable fun act(i: androidx.compose.ui.graphics.vector.ImageVector, d: String, tint: Color = LocalContentColor.current, f: () -> Unit) =
                IconButton(f, Modifier.size(34.dp)) { Icon(i, d, Modifier.size(17.dp), tint = tint) }
            act(Icons.Default.ContentCopy, "Copy") { clip.setText(AnnotatedString(m.content)) }
            if (user) act(Icons.Default.Edit, "Edit", f = onEdit)
            else {
                act(Icons.Default.ThumbUp, "Like", if (m.rating == 1) Neon3 else LocalContentColor.current) { vm.rate(m, 1) }
                act(Icons.Default.ThumbDown, "Dislike", if (m.rating == -1) Neon1 else LocalContentColor.current) { vm.rate(m, -1) }
                act(Icons.Default.Share, "Share") {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.content), "Share"))
                }
                if (isLast) act(Icons.Default.Refresh, "Regenerate") { vm.regenerate() }
            }
            act(Icons.Default.Delete, "Delete") { vm.delete(m) }
        }
    }
}

/** Renders text with fenced ``` code blocks. */
@Composable
fun RichText(text: String, size: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.split("```").forEachIndexed { i, p ->
            if (i % 2 == 1) {
                val code = p.substringAfter("\n", p).trimEnd()
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0F0F1C)) {
                    Text(
                        code, fontFamily = FontFamily.Monospace, fontSize = (size - 2).sp, color = Neon3,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            } else if (p.isNotBlank()) Text(p.trim(), fontSize = size.sp)
        }
    }
}
