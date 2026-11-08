package com.axo.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.axo.ai.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(msg: String) : Exception(msg)

@Serializable data class User(val id: String, val name: String = "", val email: String = "", val guest: Boolean = false)
@Serializable data class AuthRes(val token: String, val user: User)
@Serializable data class AuthReq(val email: String = "", val password: String = "", val name: String = "")
@Serializable data class ModelInfo(val id: String, val label: String, val role: String)
@Serializable data class ChatMsg(val role: String, val content: String)
@Serializable data class ChatReq(val model: String, val system: String, val messages: List<ChatMsg>)

/** Auth token + user stored encrypted (AES-256, Android Keystore). */
class TokenStore(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "axo_secure", MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    var token: String?
        get() = sp.getString("t", null)
        set(v) { sp.edit().putString("t", v).apply() }
    var userJson: String?
        get() = sp.getString("u", null)
        set(v) { sp.edit().putString("u", v).apply() }
    fun user(): User? = userJson?.let { runCatching { Json.decodeFromString<User>(it) }.getOrNull() }
    fun clear() = sp.edit().clear().apply()
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("axo", Context.MODE_PRIVATE)
    val theme = MutableStateFlow(sp.getString("theme", "dark")!!)
    val instructions = MutableStateFlow(sp.getString("instr", "")!!)
    val fontSize = MutableStateFlow(sp.getInt("font", 16))
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }
    fun setTheme(v: String) { theme.value = v; sp.edit().putString("theme", v).apply() }
    fun setInstructions(v: String) { instructions.value = v; sp.edit().putString("instr", v).apply() }
    fun setFont(v: Int) { fontSize.value = v; sp.edit().putInt("font", v).apply() }
    fun reset() { sp.edit().clear().apply(); theme.value = "dark"; instructions.value = ""; fontSize.value = 16 }
}

class Api(private val tokens: TokenStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mt = "application/json".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    private fun build(path: String, body: String?, method: String) = Request.Builder()
        .url(BuildConfig.BASE_URL + path)
        .apply {
            tokens.token?.let { header("Authorization", "Bearer $it") }
            when (method) {
                "GET" -> get()
                "DELETE" -> delete()
                else -> post((body ?: "{}").toRequestBody(mt))
            }
        }.build()

    private fun err(code: Int, t: String): String =
        runCatching { json.parseToJsonElement(t).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            ?: "Request failed ($code)."

    private suspend fun call(path: String, body: String? = null, method: String = "POST"): String =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(build(path, body, method)).execute().use { r ->
                    val t = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw ApiException(err(r.code, t))
                    t
                }
            } catch (e: IOException) {
                throw ApiException("Can't reach the server. Check your internet connection.")
            }
        }

    private suspend fun auth(path: String, req: AuthReq): User {
        val res = json.decodeFromString<AuthRes>(call(path, json.encodeToString(req)))
        tokens.token = res.token
        tokens.userJson = json.encodeToString(res.user)
        return res.user
    }

    suspend fun register(name: String, email: String, pw: String) = auth("auth/register", AuthReq(email, pw, name))
    suspend fun login(email: String, pw: String) = auth("auth/login", AuthReq(email, pw))
    suspend fun guest() = auth("auth/guest", AuthReq())
    suspend fun forgot(email: String) { call("auth/forgot", json.encodeToString(AuthReq(email = email))) }
    suspend fun deleteAccount() { call("account", method = "DELETE") }
    suspend fun models(): List<ModelInfo> = json.decodeFromString(call("v1/models", method = "GET"))

    /** Streams text deltas from the backend (SSE). Cancelling the collector cancels the HTTP call. */
    fun stream(model: String, system: String, msgs: List<ChatMsg>): Flow<String> = flow {
        val call = client.newCall(build("v1/chat", json.encodeToString(ChatReq(model, system, msgs)), "POST"))
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw ApiException(err(r.code, r.body?.string().orEmpty()))
                val src = r.body!!.source()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d == "[DONE]") break
                    val o = json.parseToJsonElement(d).jsonObject
                    o["error"]?.let { throw ApiException(it.jsonPrimitive.content) }
                    o["d"]?.let { emit(it.jsonPrimitive.content) }
                }
            }
        } catch (e: IOException) {
            if (call.isCanceled()) throw CancellationException("stopped")
            throw ApiException("Connection lost. Check your internet and try again.")
        }
    }.flowOn(Dispatchers.IO)
}
