package com.natam.cs50mobile

import android.content.Context
import com.natam.cs50mobile.core.build.BuildCoordinator
import com.natam.cs50mobile.core.files.WorkspaceFiles
import com.natam.cs50mobile.core.process.ProotCommandBuilder
import com.natam.cs50mobile.core.runtime.RuntimeManager
import com.natam.cs50mobile.core.runtime.RuntimePaths
import com.natam.cs50mobile.core.runtime.RuntimeSettings
import com.natam.cs50mobile.core.runtime.RuntimeState
import com.natam.cs50mobile.core.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Injeção de dependência manual (sem Hilt/Dagger: build bem mais rápido no celular).
 * Tudo aqui vive no escopo do APP, não da Activity: o shell sobrevive a girar a tela
 * e a recriar a interface.
 */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val paths = RuntimePaths(context)
    val settings = RuntimeSettings(context)
    val runtime = RuntimeManager(context, appScope, paths, settings)
    val commands = ProotCommandBuilder(paths, settings)

    val workspace = WorkspaceFiles(paths.workspaceDir)
    val mainSession = TerminalSession(context, appScope, commands, settings)
    val build = BuildCoordinator(appScope, mainSession, workspace)

    /**
     * Texto da notificação enquanto houver algo rodando; null quando não há mais motivo para
     * manter o TerminalService em primeiro plano (aí ele se encerra sozinho).
     */
    val keepAlive: StateFlow<String?> = combine(
        runtime.state,
        mainSession.status
    ) { runtimeState, terminal ->
        when {
            runtimeState is RuntimeState.Downloading || runtimeState is RuntimeState.Extracting ->
                context.getString(R.string.notif_installing)
            terminal is TerminalSession.Status.Starting || terminal is TerminalSession.Status.Running ->
                context.getString(R.string.notif_terminal)
            else -> null
        }
    }.stateIn(appScope, SharingStarted.Eagerly, null)

    fun stopAll() {
        appScope.launch {
            runtime.cancel()
            mainSession.stop()
        }
    }
}
