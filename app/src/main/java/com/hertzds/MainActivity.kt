package com.hertzds

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hertzds.ui.AppVm
import com.hertzds.ui.keys.KeysScreen
import com.hertzds.ui.memory.MemoryScreen
import com.hertzds.ui.settings.SettingsScreen
import com.hertzds.ui.tasks.TasksScreen
import com.hertzds.ui.theme.HertzTheme
import kotlinx.coroutines.launch

class MainVm(val container: AppContainer) : ViewModel()

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as HertzApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)

            HertzTheme(settings?.themeMode ?: com.hertzds.data.prefs.ThemeMode.DARK) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    settings?.let { s ->
                        if (s.eulaAccepted) {
                            AppRoot(container)
                        } else {
                            EulaGate(
                                onAccept = { lifecycleScope.launch { container.settings.setEulaAccepted(true) } },
                                onDecline = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val vm: AppVm = viewModel(factory = viewModelFactory(container))

    DisposableEffect(Unit) {
        onDispose { vm.releaseVoice() }
    }

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            com.hertzds.ui.chat.ChatScreen(
                vm = vm,
                onOpenKeys = { navController.navigate("keys") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenMemory = { navController.navigate("memory") },
                onOpenTasks = { navController.navigate("tasks") },
            )
        }
        composable("keys") { KeysScreen(container, onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(container, onBack = { navController.popBackStack() }) }
        composable("memory") { MemoryScreen(container, onBack = { navController.popBackStack() }) }
        composable("tasks") { TasksScreen(container, onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun viewModelFactory(container: AppContainer): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppVm(container) as T
    }

/** First-run consent: everything stays local; only model calls leave the device. */
@Composable
private fun EulaGate(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Vítej v Hertz-DS", textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Lokální agentní asistent napojený na DeepSeek.\n\n" +
                        "• Veškerá data (chaty, paměť, soubory, klíče) zůstávají pouze v tomto zařízení.\n" +
                        "• Žádný účet, žádná telemetrie, žádný cloud.\n" +
                        "• Dotazy se odesílají přímo na api.deepseek.com pod tvým vlastním API klíčem " +
                        "a platí se z tvých kreditů.\n" +
                        "• Nástroje agenta mohou přistupovat k internetu a pracovní složce aplikace.\n\n" +
                        "Používáním souhlasíš s tímto modelem zpracování.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Souhlasím") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Ukončit") } },
    )
}
