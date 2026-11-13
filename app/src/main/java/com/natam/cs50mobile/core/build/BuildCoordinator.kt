package com.natam.cs50mobile.core.build

import com.natam.cs50mobile.core.files.WorkspaceFiles
import com.natam.cs50mobile.core.terminal.ShellPrompt
import com.natam.cs50mobile.core.terminal.TerminalSession
import com.natam.cs50mobile.core.terminal.TerminalSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * "Compilar e rodar": monta o comando certo para o tipo de arquivo, manda para o terminal (assim o
 * programa pode pedir entrada, como o get_string do CS50) e, quando o prompt volta, lê a saída
 * procurando erros para a lista "Erros".
 */
class BuildCoordinator(
    scope: CoroutineScope,
    private val session: TerminalSession,
    private val files: WorkspaceFiles
) {
    enum class Outcome { STARTED, UNSUPPORTED, BUSY }

    data class Diagnostic(
        val file: File,
        val line: Int,
        val column: Int,
        val isError: Boolean,
        val message: String
    )

    data class Result(val seq: Long, val errors: Int, val warnings: Int)

    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val diagnostics: StateFlow<List<Diagnostic>> = _diagnostics.asStateFlow()

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    @Volatile private var pendingStart = -1L
    @Volatile private var runDir: File? = null
    private var resultSeq = 0L

    init {
        scope.launch { session.snapshots.collect { onSnapshot(it) } }
    }

    /** Comando para compilar e rodar [file] a partir da pasta dele, ou null se o tipo não é suportado. */
    fun commandFor(file: File): String? {
        val dir = quote(files.guestPath(file.parentFile ?: files.root))
        val name = file.name
        val base = file.nameWithoutExtension
        return when (file.extension.lowercase()) {
            // `make hello` usa as regras embutidas do make com CC/CFLAGS/LDLIBS do ambiente.
            "c" -> "cd $dir && make ${quote(base)} && ${quote("./$base")}"
            "py" -> "cd $dir && python3 ${quote(name)}"
            else -> null
        }
    }

    suspend fun run(file: File): Outcome {
        val command = commandFor(file) ?: return Outcome.UNSUPPORTED

        // Só manda o comando com o shell parado no prompt; senão ele iria para dentro de outro programa.
        val ready = withTimeoutOrNull(PROMPT_WAIT_MS) {
            session.snapshots.first { ShellPrompt.AT_PROMPT.containsMatchIn(it.partial) }
        } ?: return Outcome.BUSY

        _diagnostics.value = emptyList()
        _result.value = null
        runDir = file.parentFile
        pendingStart = ready.endIndex
        session.sendLine(command)
        return Outcome.STARTED
    }

    private fun onSnapshot(snap: TerminalSnapshot) {
        val start = pendingStart
        if (start < 0) return
        // Terminou quando o eco do comando já foi fechado (endIndex avançou) e o prompt voltou.
        if (snap.endIndex <= start || !ShellPrompt.AT_PROMPT.containsMatchIn(snap.partial)) return
        pendingStart = -1

        val from = maxOf(start, snap.firstIndex)
        val lines = snap.lines.subList((from - snap.firstIndex).toInt(), snap.lines.size)
        val diagnostics = CompilerOutputParser.parse(lines)
            .mapNotNull { resolve(it) }
            .distinct()
            .take(MAX_DIAGNOSTICS)

        _diagnostics.value = diagnostics
        _result.value = Result(
            seq = ++resultSeq,
            errors = diagnostics.count { it.isError },
            warnings = diagnostics.count { !it.isError }
        )
    }

    /** Traduz o caminho que o compilador imprimiu para um arquivo real do workspace. */
    private fun resolve(raw: CompilerOutputParser.Raw): Diagnostic? {
        val guestPrefix = WorkspaceFiles.GUEST_ROOT + "/"
        val file = if (raw.file.startsWith(guestPrefix)) {
            File(files.root, raw.file.removePrefix(guestPrefix))
        } else {
            File(runDir ?: files.root, raw.file)
        }
        if (!file.isFile || !files.isInside(file)) return null
        return Diagnostic(file, raw.line, raw.column, raw.isError, raw.message)
    }

    private fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private companion object {
        const val PROMPT_WAIT_MS = 20_000L
        const val MAX_DIAGNOSTICS = 50
    }
}
