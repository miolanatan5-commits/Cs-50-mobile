package com.natam.cs50mobile.core.process

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Um processo filho (normalmente o PRoot) com leitura, escrita e parada controladas.
 * Nenhuma chamada bloqueante roda na Main thread: leitura e espera ficam em Dispatchers.IO.
 *
 * [onOutput] recebe o buffer compartilhado da leitura: quem recebe tem que consumir na hora
 * e não pode guardar a referência (é assim que o uso de memória fica constante).
 */
class ManagedProcess(
    private val spec: ProotCommandBuilder.LaunchSpec,
    private val scope: CoroutineScope,
    private val onOutput: (data: ByteArray, length: Int) -> Unit
) {
    private val exitCode = CompletableDeferred<Int>()
    private val writeMutex = Mutex()

    @Volatile private var process: Process? = null

    @Volatile var pid: Int = -1
        private set

    val isAlive: Boolean get() = process?.isAlive == true

    /** Lança IOException se o executável não puder ser iniciado. */
    fun start() {
        check(process == null) { "Processo já iniciado" }
        val builder = ProcessBuilder(spec.argv)
            .directory(spec.workDir)
            .redirectErrorStream(true) // stdout e stderr no mesmo fluxo, como num terminal
        builder.environment().apply {
            clear() // não vaza o ambiente do Android para dentro do Linux
            putAll(spec.env)
        }
        val p = builder.start()
        process = p
        pid = ProcessIds.pidOf(p)

        val reader = scope.launch(Dispatchers.IO) { pump(p) }
        scope.launch(Dispatchers.IO) {
            val code = try {
                p.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            // Dá um instante para a leitura esvaziar o que sobrou no pipe.
            withTimeoutOrNull(800) { reader.join() }
            runCatching { p.inputStream.close() }
            runCatching { p.outputStream.close() }
            exitCode.complete(code)
        }
    }

    suspend fun awaitExit(): Int = exitCode.await()

    /** Envia bytes para o stdin do processo. Devolve false se o processo já não existe. */
    suspend fun write(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val p = process ?: return@withLock false
            try {
                p.outputStream.write(data)
                p.outputStream.flush()
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    /** SIGTERM, espera [graceMs] e, se ainda estiver vivo, SIGKILL. */
    suspend fun stop(graceMs: Long = 1_500) {
        val p = process ?: return
        if (!p.isAlive) return
        signal(OsConstants.SIGTERM)
        val exited = withTimeoutOrNull(graceMs) { exitCode.await() } != null
        if (!exited) {
            signal(OsConstants.SIGKILL)
            p.destroyForcibly()
            withTimeoutOrNull(1_000) { exitCode.await() }
        }
    }

    private fun signal(signal: Int) {
        if (pid > 0) {
            try {
                Os.kill(pid, signal)
            } catch (_: ErrnoException) {
            }
        } else {
            process?.destroy()
        }
    }

    private fun pump(p: Process) {
        val buffer = ByteArray(8 * 1024)
        try {
            val input = p.inputStream
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) onOutput(buffer, n)
            }
        } catch (_: IOException) {
            // O pipe fechou: o processo terminou.
        }
    }
}
