package com.axo.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.axo.ai.AppContainer

@Composable
inline fun <reified T : ViewModel> vm(crossinline make: () -> T): T =
    viewModel(factory = viewModelFactory { initializer { make() } })

@Composable
fun AxoNav(c: AppContainer) {
    val nav = rememberNavController()
    fun reset(to: String) = nav.navigate(to) { popUpTo(0) { inclusive = true } }

    NavHost(nav, "splash") {
        composable("splash") {
            SplashScreen {
                reset(if (!c.prefs.onboarded) "onboarding" else if (c.tokens.token == null) "login" else "chat")
            }
        }
        composable("onboarding") { OnboardingScreen { c.prefs.onboarded = true; reset("login") } }
        composable("login") {
            AuthScreen(vm { AuthVM(c) }, signup = false, onDone = { reset("chat") }, onSwitch = { nav.navigate("signup") })
        }
        composable("signup") {
            AuthScreen(vm { AuthVM(c) }, signup = true, onDone = { reset("chat") }, onSwitch = { nav.popBackStack() })
        }
        composable(
            "chat?chatId={chatId}&assistantId={assistantId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("assistantId") { type = NavType.StringType; defaultValue = "general" },
            ),
        ) { e ->
            val chatId = e.arguments?.getString("chatId")
            val aid = e.arguments?.getString("assistantId") ?: "general"
            val model = vm { ChatVM(c, chatId, aid) }
            ChatScreen(
                c, model,
                onNewChat = { nav.navigate("chat?assistantId=general") { popUpTo("chat?chatId={chatId}&assistantId={assistantId}") { inclusive = true } } },
                onHistory = { nav.navigate("history") },
                onExplore = { nav.navigate("explore") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("history") {
            HistoryScreen(vm { LibraryVM(c) }, back = { nav.popBackStack() }) { id -> nav.navigate("chat?chatId=$id") }
        }
        composable("explore") {
            ExploreScreen(
                vm { LibraryVM(c) }, back = { nav.popBackStack() },
                open = { aid -> nav.navigate("chat?assistantId=$aid") },
                create = { id -> nav.navigate(if (id == null) "create" else "create?id=$id") },
            )
        }
        composable(
            "create?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { e -> CreateAssistantScreen(vm { LibraryVM(c) }, e.arguments?.getString("id")) { nav.popBackStack() } }
        composable("settings") {
            SettingsScreen(c, vm { LibraryVM(c) }, back = { nav.popBackStack() }, onSignedOut = { reset("login") })
        }
    }
}
