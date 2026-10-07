package com.noise.netpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noise.netpulse.ui.DiagnosticViewModel
import com.noise.netpulse.ui.screen.MainScreen
import com.noise.netpulse.ui.theme.NetPulseTheme

class MainActivity : ComponentActivity() {
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

@Composable
fun NetPulseApp() {
    val viewModel: DiagnosticViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    MainScreen(network = uiState.network, localInfo = uiState.localInfo)
}
