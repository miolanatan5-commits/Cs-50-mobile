package com.natam.cs50mobile.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natam.cs50mobile.CS50App
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.cs50.Cs50Setup
import com.natam.cs50mobile.core.runtime.RuntimeState
import com.natam.cs50mobile.core.terminal.ShellPrompt
import com.natam.cs50mobile.core.terminal.TerminalSession
import com.natam.cs50mobile.core.terminal.TerminalSnapshot
import com.natam.cs50mobile.service.TerminalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CS50App).container
    private val session = container.mainSession

    val runtimeState: StateFlow<RuntimeState> = container.runtime.state
    val terminalStatus: StateFlow<TerminalSession.Status> = session.status
    val snapshot: StateFlow<TerminalSnapshot> = session.snapshots

    private val _announcement = MutableStateFlow(Announcement())
    val announcement: StateFlow<Announcement> = _announcement.asStateFlow()

    private val _announceEnabled = MutableStateFlow(container.settings.announceOutput)
    val announceEnabled: StateFlow<Boolean> = _announceEnabled.asStateFlow()

    private val _cs50Installed = MutableStateFlow(false)
    val cs50Installed: StateFlow<Boolean> = _cs50Installed.asStateFlow()

    val rootfsUrl: String get() = container.settings.rootfsUrl

    private var announcedUpTo = session.snapshots.value.endIndex
    private var announceSeq = 0L
    private var awaitingResult = false

    init {
        refreshCs50()
        // collectLatest cancela o delay a cada nova saída: só anuncia depois que a saída "assenta".
        // Assim um `apt install` com centenas de linhas vira poucos avisos, não uma metralhadora.
        viewModelScope.launch {
            session.snapshots.collectLatest { snap ->
                delay(SETTLE_MS)
                announceNew(snap)
            }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    // ------------------------------------------------------------ instalação do Linux

    fun installRuntime(url: String) {
        container.settings.rootfsUrl = url
        TerminalService.start(getApplication<Application>())
        container.runtime.install()
    }

    fun cancelInstall() = container.runtime.cancel()

    fun refreshRuntime() = container.runtime.refresh()

    // ------------------------------------------------------------ terminal

    fun ensureStarted() {
        if (container.runtime.state.value !is RuntimeState.Ready) return
        if (session.status.value is TerminalSession.Status.Idle) startSession()
    }

    fun startSession() {
        TerminalService.start(getApplication<Application>())
        session.start()
    }

    fun restartSession() {
        viewModelScope.launch {
            session.stop()
            startSession()
        }
    }

    fun sendLine(text: String) {
        viewModelScope.launch { session.sendLine(text) }
    }

    fun interrupt() {
        viewModelScope.launch { session.sendControl(CTRL_C) }
    }

    fun endOfInput() {
        viewModelScope.launch { session.sendControl(CTRL_D) }
    }

    fun clearOutput() = session.clear()

    fun setAnnounceEnabled(enabled: Boolean) {
        container.settings.announceOutput = enabled
        _announceEnabled.value = enabled
    }

    /** Lê de novo as últimas linhas, a pedido do usuário (funciona mesmo com o anúncio automático desligado). */
    fun readLast() {
        val snap = session.snapshots.value
        val tail = (snap.lines.takeLast(READ_LAST_LINES) + snap.partial)
            .filter { it.isNotBlank() }
        publish(tail.joinToString("\n").ifBlank { str(R.string.no_output) })
    }

    // ------------------------------------------------------------ ferramentas do CS50

    /** Copia o instalador para dentro do Linux e roda no próprio terminal, para acompanhar o apt. */
    fun installCs50Tools() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Cs50Setup.writeScript(getApplication<Application>(), container.paths)
            }
            session.sendLine(Cs50Setup.COMMAND + " " + Cs50Setup.lang())
        }
    }

    private fun refreshCs50() {
        viewModelScope.launch(Dispatchers.IO) {
            _cs50Installed.value = Cs50Setup.isInstalled(container.paths)
        }
    }

    // ------------------------------------------------------------ anúncios

    private fun announceNew(snap: TerminalSnapshot) {
        val from = maxOf(announcedUpTo, snap.firstIndex)
        var fresh = snap.lines.subList((from - snap.firstIndex).toInt(), snap.lines.size)
        announcedUpTo = snap.endIndex

        val atPrompt = ShellPrompt.AT_PROMPT.containsMatchIn(snap.partial)
        if (atPrompt) refreshCs50()

        // Linhas de eco ("root@cs50:~# make hello") são o comando que o próprio usuário (ou o botão
        // Compilar e rodar) mandou: não precisam ser lidas de volta, mas provam que há um comando
        // em andamento, e aí o aviso "Pronto" faz sentido quando o prompt voltar.
        if (fresh.any { ShellPrompt.ECHO_LINE.containsMatchIn(it) }) awaitingResult = true

        if (!_announceEnabled.value) {
            if (atPrompt) awaitingResult = false
            return
        }

        fresh = fresh.filter { it.isNotBlank() && !ShellPrompt.ECHO_LINE.containsMatchIn(it) }

        val parts = ArrayList<String>()
        if (fresh.size > MAX_ANNOUNCED_LINES) {
            parts += str(R.string.announce_skipped, fresh.size - MAX_ANNOUNCED_LINES)
        }
        parts += fresh.takeLast(MAX_ANNOUNCED_LINES)
        if (atPrompt && awaitingResult) parts += str(R.string.announce_ready)
        if (atPrompt) awaitingResult = false

        if (parts.isNotEmpty()) publish(parts.joinToString("\n").takeLast(MAX_ANNOUNCED_CHARS))
    }

    private fun publish(text: String) {
        announceSeq++
        _announcement.value = Announcement(announceSeq, text)
    }

    private companion object {
        const val SETTLE_MS = 350L
        const val MAX_ANNOUNCED_LINES = 6
        const val MAX_ANNOUNCED_CHARS = 700
        const val READ_LAST_LINES = 10
        const val CTRL_C = 3
        const val CTRL_D = 4
    }
}
