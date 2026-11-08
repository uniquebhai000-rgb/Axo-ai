package com.axo.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axo.ai.AppContainer
import com.axo.ai.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class AuthVM(private val c: AppContainer) : ViewModel() {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val info = MutableStateFlow<String?>(null)

    private fun run(ok: () -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
        loading.value = true; error.value = null; info.value = null
        try { block(); ok() } catch (e: Exception) { error.value = e.message ?: "Something went wrong." }
        loading.value = false
    }
    fun login(e: String, p: String, ok: () -> Unit) = run(ok) { c.api.login(e.trim(), p) }
    fun signup(n: String, e: String, p: String, ok: () -> Unit) = run(ok) { c.api.register(n.trim(), e.trim(), p) }
    fun guest(ok: () -> Unit) = run(ok) { c.api.guest() }
    fun forgot(e: String) = run({}) { c.api.forgot(e.trim()) }
}

class ChatVM(private val c: AppContainer, chatId: String?, private val assistantId: String) : ViewModel() {
    private val dao = c.db.dao()
    val id = chatId ?: UUID.randomUUID().toString()
    val messages = dao.messages(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streaming = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val selected = MutableStateFlow<String?>(null)
    val modelsLoading = MutableStateFlow(true)
    private var job: Job? = null

    init { loadModels() }

    fun loadModels() = viewModelScope.launch {
        modelsLoading.value = true
        try {
            models.value = c.api.models()
            if (selected.value == null) selected.value = models.value.firstOrNull()?.id
            if (models.value.isEmpty()) error.value = "No AI models are configured on the server. Add an API key to the backend .env."
        } catch (e: Exception) { error.value = e.message }
        modelsLoading.value = false
    }

    fun select(m: String) { selected.value = m }

    fun send(text: String) {
        if (text.isBlank() || job?.isActive == true) return
        job = viewModelScope.launch {
            if (dao.chat(id) == null)
                dao.upsertChat(Chat(id, text.trim().take(40), assistantId, selected.value ?: "", false, now()))
            dao.insertMsg(Msg(UUID.randomUUID().toString(), id, "user", text.trim(), 0, now()))
            generate()
        }
    }

    fun regenerate() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            dao.messagesOnce(id).lastOrNull()?.takeIf { it.role == "assistant" }?.let { dao.deleteMsg(it.id) }
            generate()
        }
    }

    fun edit(m: Msg, newText: String) {
        if (newText.isBlank() || job?.isActive == true) return
        job = viewModelScope.launch {
            dao.deleteAfter(id, m.createdAt)
            dao.updateMsg(m.id, newText.trim())
            generate()
        }
    }

    fun stop() { job?.cancel() }
    fun delete(m: Msg) = viewModelScope.launch { dao.deleteMsg(m.id) }
    fun rate(m: Msg, r: Int) = viewModelScope.launch { dao.rate(m.id, if (m.rating == r) 0 else r) }

    private suspend fun generate() {
        val model = selected.value ?: run { error.value = "No AI model available."; return }
        error.value = null
        streaming.value = ""
        val sb = StringBuilder()
        try {
            val a = dao.assistant(assistantId)
            val system = listOfNotNull(
                a?.instructions?.takeIf { it.isNotBlank() },
                c.prefs.instructions.value.takeIf { it.isNotBlank() }?.let { "User's custom instructions:\n$it" },
            ).joinToString("\n\n")
            val history = dao.messagesOnce(id).map { ChatMsg(it.role, it.content) }
            c.api.stream(model, system, history).collect { sb.append(it); streaming.value = sb.toString() }
        } catch (e: CancellationException) {
            // user pressed stop: keep partial text
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            withContext(NonCancellable) {
                if (sb.isNotEmpty()) dao.insertMsg(Msg(UUID.randomUUID().toString(), id, "assistant", sb.toString(), 0, now()))
                dao.touch(id, now())
                streaming.value = null
            }
        }
    }

    private fun now() = System.currentTimeMillis()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryVM(val c: AppContainer) : ViewModel() {
    private val dao = c.db.dao()
    val query = MutableStateFlow("")
    val archived = MutableStateFlow(false)
    val chats = combine(query, archived) { q, a -> q to a }
        .flatMapLatest { dao.chats(it.second, it.first) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assistants = dao.assistants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)

    fun rename(id: String, t: String) = viewModelScope.launch { if (t.isNotBlank()) dao.rename(id, t.trim()) }
    fun archive(id: String, a: Boolean) = viewModelScope.launch { dao.archive(id, a) }
    fun deleteChat(id: String) = viewModelScope.launch { dao.deleteChat(id) }
    fun deleteAllChats() = viewModelScope.launch { dao.deleteAllChats() }
    fun saveAssistant(a: Assistant) = viewModelScope.launch { dao.upsertAssistant(a) }
    fun deleteAssistant(id: String) = viewModelScope.launch { dao.deleteAssistant(id) }
    suspend fun assistant(id: String) = dao.assistant(id)

    suspend fun exportText(): String {
        val sb = StringBuilder("AXO AI export\n\n")
        for (ch in dao.allChats()) {
            sb.append("# ${ch.title}\n")
            dao.messagesOnce(ch.id).forEach { sb.append("${if (it.role == "user") "You" else "AXO AI"}: ${it.content}\n\n") }
        }
        return sb.toString()
    }

    fun clearLocal() = viewModelScope.launch { dao.deleteAllChats(); dao.deleteCustomAssistants(); c.prefs.reset() }

    fun deleteAccount(done: () -> Unit) = viewModelScope.launch {
        try {
            if (c.tokens.user()?.guest != true) c.api.deleteAccount()
            dao.deleteAllChats(); dao.deleteCustomAssistants(); c.prefs.reset(); c.tokens.clear()
            done()
        } catch (e: Exception) { error.value = e.message }
    }

    fun signOut(done: () -> Unit) { c.tokens.clear(); done() }
}
