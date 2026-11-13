package com.natam.cs50mobile.core.terminal

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.process.ManagedProcess
import com.natam.cs50mobile.core.process.ProotCommandBuilder
import com.natam.cs50mobile.core.runtime.RuntimeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Uma sessão de shell: processo + buffer de linhas + estado.
 * Vive no escopo do app (não da Activity), então girar a tela ou fechar o app da lista de recentes
 * não derruba a sessão enquanto o TerminalService estiver ativo.
 * Para ter várias abas no futuro, basta criar mais instâncias.
 */
class TerminalSession(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val builder: ProotCommandBuilder,
    private val settings: RuntimeSettings
) {
    sealed interface Status {
        data object Idle : Status
        data object Starting : Status
        data object Running : Status
        data class Exited(val code: Int) : Status
    }

    private val buffer = LineBuffer()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _snapshots = MutableStateFlow(TerminalSnapshot.EMPTY)
    val snapshots: StateFlow<TerminalSnapshot> = _snapshots.asStateFlow()

    // Rajadas de saída (um `apt install`, por exemplo) viram no máximo ~12 atualizações de UI por
    // segundo: o canal CONFLATED junta todos os avisos pendentes em um só.
    private val dirty = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var process: ManagedProcess? = null
    @Volatile private var stopRequested = false
    private var runJob: Job? = null

    init {
        scope.launch {
            for (signal in dirty) {
                _snapshots.value = buffer.snapshot()
                delay(RENDER_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (runJob?.isActive == true) return
        stopRequested = false
        runJob = scope.launch { runSession(allowSeccompFallback = !settings.noSeccomp) }
    }

    private suspend fun runSession(allowSeccompFallback: Boolean) {
        _status.value = Status.Starting
        val proc = ManagedProcess(builder.shell(), scope) { data, length -> onOutput(data, length) }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            proc.start()
        } catch (e: IOException) {
            system(R.string.sys_start_failed, e.message.orEmpty())
            _status.value = Status.Exited(-1)
            return
        }
        process = proc
        _status.value = Status.Running

        val code = proc.awaitExit()
        process = null

        // Morreu na hora com código de sinal (>=128): em alguns kernels Samsung o seccomp do PRoot
        // derruba o processo. Tenta uma vez sem ele e lembra da escolha.
        val quick = SystemClock.elapsedRealtime() - startedAt < QUICK_CRASH_MS
        if (!stopRequested && quick && code >= 128 && allowSeccompFallback) {
            settings.noSeccomp = true
            system(R.string.sys_retry_no_seccomp)
            runSession(allowSeccompFallback = false)
            return
        }
        system(R.string.sys_exited, code)
        _status.value = Status.Exited(code)
    }

    suspend fun sendLine(text: String) {
        val proc = process
        if (proc == null || !proc.write((text + "\n").toByteArray())) {
            system(R.string.sys_not_running)
        }
    }

    /** Ex.: 3 = Ctrl+C (SIGINT para o comando em primeiro plano), 4 = Ctrl+D (fim de entrada). */
    suspend fun sendControl(code: Int) {
        process?.write(byteArrayOf(code.toByte()))
    }

    suspend fun stop() {
        stopRequested = true
        process?.stop()
        runJob?.join()
    }

    fun clear() {
        buffer.clear()
        dirty.trySend(Unit)
    }

    private fun onOutput(data: ByteArray, length: Int) {
        buffer.append(data, length)
        dirty.trySend(Unit)
    }

    private fun system(@StringRes id: Int, vararg args: Any) {
        buffer.appendLine("[cs50] " + appContext.getString(id, *args))
        dirty.trySend(Unit)
    }

    private companion object {
        const val RENDER_INTERVAL_MS = 80L
        const val QUICK_CRASH_MS = 3_000L
    }
}
