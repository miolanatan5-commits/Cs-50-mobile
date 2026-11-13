package com.natam.cs50mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.runtime.RuntimeState

private const val TAB_TERMINAL = 0
private const val TAB_FILES = 1
private const val TAB_GITHUB = 2

@Composable
fun AppRoot(vm: TerminalViewModel, editorVm: EditorViewModel, githubVm: GithubViewModel) {
    val runtime by vm.runtimeState.collectAsStateWithLifecycle()
    val openFile by editorVm.open.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TERMINAL) }

    // Voltar dentro de um arquivo salva e fecha o arquivo, em vez de sair do app.
    BackHandler(enabled = tab == TAB_FILES && openFile != null) { editorVm.closeFile() }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (runtime is RuntimeState.Ready) {
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == TAB_TERMINAL,
                        onClick = { tab = TAB_TERMINAL },
                        text = { Text(stringResource(R.string.tab_terminal)) }
                    )
                    Tab(
                        selected = tab == TAB_FILES,
                        onClick = { tab = TAB_FILES },
                        text = { Text(stringResource(R.string.tab_files)) }
                    )
                    Tab(
                        selected = tab == TAB_GITHUB,
                        onClick = { tab = TAB_GITHUB },
                        text = { Text(stringResource(R.string.tab_github)) }
                    )
                }
                when {
                    tab == TAB_TERMINAL -> TerminalScreen(vm, Modifier.weight(1f))
                    tab == TAB_GITHUB -> GithubScreen(
                        vm = githubVm,
                        onSentToTerminal = { tab = TAB_TERMINAL },
                        modifier = Modifier.weight(1f)
                    )
                    openFile == null -> FilesScreen(editorVm, Modifier.weight(1f))
                    else -> EditorScreen(
                        vm = editorVm,
                        onRunStarted = { tab = TAB_TERMINAL },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            SetupScreen(vm, runtime, Modifier.padding(padding))
        }
    }
}
