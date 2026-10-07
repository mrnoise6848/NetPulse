package com.noise.netpulse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noise.netpulse.ui.DiagnosticViewModel
import com.noise.netpulse.ui.screen.DetailScreen
import com.noise.netpulse.ui.screen.HistoryScreen
import com.noise.netpulse.ui.screen.MainScreen
import com.noise.netpulse.ui.theme.NetPulseTheme

/** Screens of the app; navigation is simple ViewModel-held state. */
enum class AppScreen { MAIN, DETAIL, HISTORY }class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetPulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NetPulseApp()
                }
            }
        }
    }
}

/** Opens Android network settings; NetPulse never modifies settings itself. */
private fun openNetworkSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

@Composable
private fun HistoryButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("History") }
}

@Composable
fun NetPulseApp() {
    val context = LocalContext.current
    val viewModel: DiagnosticViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val screen by viewModel.screen.collectAsState()

    when (screen) {
        AppScreen.MAIN -> Column {
            MainScreen(
                network = uiState.network,
                localInfo = uiState.localInfo,
                running = uiState.running,
                currentStep = uiState.currentStep,
                report = uiState.report,
                onRunDiagnostic = viewModel::runDiagnostic,
                onCancelDiagnostic = viewModel::cancelDiagnostic,
                onOpenDetails = viewModel::openDetails,
                onOpenNetworkSettings = { openNetworkSettings(context) },
            )
            HistoryButton(onClick = viewModel::openHistory)
        }
        AppScreen.DETAIL -> Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::openMain) { Text("← Main") }
            DetailScreen(report = uiState.report)
        }
        AppScreen.HISTORY -> Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::openMain) { Text("← Main") }
            HistoryScreen(
                entries = uiState.history,
                onDeleteEntry = viewModel::deleteHistoryEntry,
                onClearHistory = viewModel::clearHistory,
            )
        }
    }
}
