package com.natam.cs50mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.natam.cs50mobile.ui.AppRoot
import com.natam.cs50mobile.ui.EditorViewModel
import com.natam.cs50mobile.ui.GithubViewModel
import com.natam.cs50mobile.ui.TerminalViewModel
import com.natam.cs50mobile.ui.theme.CS50MobileTheme

class MainActivity : ComponentActivity() {

    private val terminalViewModel: TerminalViewModel by viewModels()
    private val editorViewModel: EditorViewModel by viewModels()
    private val githubViewModel: GithubViewModel by viewModels()

    // Sem a permissão o serviço roda do mesmo jeito; só a notificação fica escondida.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CS50MobileTheme {
                AppRoot(terminalViewModel, editorViewModel, githubViewModel)
            }
        }
    }
}
