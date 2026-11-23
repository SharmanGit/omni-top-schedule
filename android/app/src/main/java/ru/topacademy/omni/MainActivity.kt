package ru.topacademy.omni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.topacademy.omni.ui.MainViewModel
import ru.topacademy.omni.ui.screens.LoginScreen
import ru.topacademy.omni.ui.screens.MainScreen
import ru.topacademy.omni.ui.theme.OmniTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ru.topacademy.omni.worker.ScheduleSyncWorker.schedule(this)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            OmniTheme(isDark = uiState.isDarkTheme) {
                if (uiState.isLoggedIn) {
                    MainScreen(viewModel = viewModel, uiState = uiState)
                } else {
                    LoginScreen(
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.loginError,
                        onLoginClick = { username, password ->
                            viewModel.login(username, password)
                        }
                    )
                }
            }
        }
    }
}
