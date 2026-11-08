package com.axo.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(go: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900)); delay(500); go() }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.alpha(a.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(46.sp)
            Spacer(Modifier.height(8.dp))
            Text("Your personal AI assistant", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun OnboardingScreen(done: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Spacer(Modifier.height(48.dp))
            Logo(36.sp)
            Spacer(Modifier.height(12.dp))
            Text("Ask anything. Create anything.", fontSize = 26.sp, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(28.dp))
            listOf(
                "💬" to "Streaming AI chat that remembers context",
                "🧠" to "Pick the model that fits the task",
                "🎭" to "Custom assistants with your own instructions",
                "🔒" to "Your keys stay on the server, chats stay on your device",
            ).forEach { (i, t) ->
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(i, fontSize = 24.sp); Spacer(Modifier.width(14.dp)); Text(t)
                }
            }
        }
        NeonButton("Get started", onClick = done)
    }
}

@Composable
fun AuthScreen(vm: AuthVM, signup: Boolean, onDone: () -> Unit, onSwitch: () -> Unit) {
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val info by vm.info.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Logo(34.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (signup) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(28.dp))
        if (signup) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            pw, { pw = it }, label = { Text("Password (8+ characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        (local ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center) }
        info?.let { Text(it, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        NeonButton(if (signup) "Sign up" else "Log in", loading = loading) {
            local = when {
                !email.contains("@") -> "Enter a valid email address."
                pw.length < 8 -> "Password must be at least 8 characters."
                else -> null
            }
            if (local == null) { if (signup) vm.signup(name, email, pw, onDone) else vm.login(email, pw, onDone) }
        }
        if (!signup) TextButton({
            if (email.contains("@")) vm.forgot(email) else local = "Enter your email above first."
        }) { Text("Forgot password?") }
        TextButton(onSwitch) { Text(if (signup) "Already have an account? Log in" else "New here? Create an account") }
        OutlinedButton({ vm.guest(onDone) }, Modifier.fillMaxWidth()) { Text("Continue as guest") }
    }
}
