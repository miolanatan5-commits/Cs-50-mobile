package com.natam.cs50mobile.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natam.cs50mobile.CS50App
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.build.BuildCoordinator
import com.natam.cs50mobile.core.editor.AutoIndent
import com.natam.cs50mobile.core.editor.LineText
import com.natam.cs50mobile.core.files.WorkspaceFiles
import com.natam.cs50mobile.core.terminal.TerminalSession
import com.natam.cs50mobile.service.TerminalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Navegação pelos arquivos do workspace, edição de texto e o botão "Compilar e rodar". */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CS50App).container
    private val files = container.workspace
    private val session = container.mainSession

    private val _dir = MutableStateFlow(files.root)
    val dir: StateFlow<File> = _dir.asStateFlow()

    private val _entries = MutableStateFlow<List<WorkspaceFiles.Entry>>(emptyList())
    val entries: StateFlow<List<WorkspaceFiles.Entry>> = _entries.asStateFlow()

    private val _open = MutableStateFlow<File?>(null)
    val open: StateFlow<File?> = _open.asStateFlow()

    private val _text = MutableStateFlow(TextFieldValue(""))
    val text: StateFlow<TextFieldValue> = _text.asStateFlow()

    private val _announcement = MutableStateFlow(Announcement())
    val announcement: StateFlow<Announcement> = _announcement.asStateFlow()

    private val _spellSymbols = MutableStateFlow(container.settings.spellSymbols)
    val spellSymbols: StateFlow<Boolean> = _spellSymbols.asStateFlow()

    val diagnostics: StateFlow<List<BuildCoordinator.Diagnostic>> = container.build.diagnostics
    val buildResult: StateFlow<BuildCoordinator.Result?> = container.build.result

    private var savedText = ""
    private var announceSeq = 0L
    private var autosaveJob: Job? = null

    init {
        refresh()
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun announce(text: String) {
        announceSeq++
        _announcement.value = Announcement(announceSeq, text)
    }

    private fun announce(@StringRes id: Int, vararg args: Any) = announce(str(id, *args))

    // ------------------------------------------------------------ pastas e arquivos

    fun label(dir: File): String = files.relative(dir)

    fun isRoot(dir: File): Boolean = files.isRoot(dir)

    fun refresh() {
        viewModelScope.launch {
            _entries.value = withContext(Dispatchers.IO) { files.list(_dir.value) }
        }
    }

    fun openDirectory(dir: File) {
        if (!files.isInside(dir)) return
        _dir.value = dir
        refresh()
        announce(R.string.folder_opened, files.relative(dir))
    }

    fun goUp() {
        val current = _dir.value
        val parent = current.parentFile
        if (files.isRoot(current) || parent == null) return
        openDirectory(parent)
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            val (result, file) = withContext(Dispatchers.IO) { files.createFile(_dir.value, name.trim()) }
            when (result) {
                WorkspaceFiles.CreateResult.OK -> {
                    refresh()
                    if (file != null) openFile(file)
                }
                WorkspaceFiles.CreateResult.INVALID_NAME -> announce(R.string.name_invalid)
                WorkspaceFiles.CreateResult.EXISTS -> announce(R.string.name_exists)
                WorkspaceFiles.CreateResult.ERROR -> announce(R.string.create_error)
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            val result = withContext(Dispatchers.IO) { files.createFolder(_dir.value, clean) }
            when (result) {
                WorkspaceFiles.CreateResult.OK -> {
                    refresh()
                    announce(R.string.created_folder, clean)
                }
                WorkspaceFiles.CreateResult.INVALID_NAME -> announce(R.string.name_invalid)
                WorkspaceFiles.CreateResult.EXISTS -> announce(R.string.name_exists)
                WorkspaceFiles.CreateResult.ERROR -> announce(R.string.create_error)
            }
        }
    }

    fun delete(entry: WorkspaceFiles.Entry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { files.delete(entry.file) }
            if (ok) {
                announce(R.string.deleted, entry.name)
                refresh()
            } else {
                announce(R.string.delete_error)
            }
        }
    }

    // ------------------------------------------------------------ edição

    /** Abre [file]; com [line] (a partir de 1), já posiciona o cursor e fala essa linha. */
    fun openFile(file: File, line: Int? = null) {
        viewModelScope.launch {
            autosaveJob?.cancel()
            saveNow()
            val content = try {
                withContext(Dispatchers.IO) { files.read(file) }
            } catch (_: Exception) {
                null
            }
            if (content == null) {
                announce(R.string.file_unreadable)
                return@launch
            }
            savedText = content
            val count = LineText.lineCount(content)
            val target = ((line ?: 1) - 1).coerceIn(0, count - 1)
            _text.value = TextFieldValue(content, TextRange(LineText.lineStart(content, target)))
            _open.value = file
            file.parentFile?.let { _dir.value = it }

            val opened = str(R.string.file_opened, file.name, count)
            announce(if (line != null) opened + ". " + lineSpeech(content, target) else opened)
        }
    }

    fun onTextChange(new: TextFieldValue) {
        val old = _text.value
        var next = new
        val cursor = new.selection.start

        // Enter digitado: repete o recuo da linha de cima (e soma um nível depois de "{" ou ":").
        if (new.selection.collapsed &&
            new.text.length == old.text.length + 1 &&
            cursor >= 1 &&
            new.text[cursor - 1] == '\n' &&
            new.text.removeRange(cursor - 1, cursor) == old.text
        ) {
            val python = _open.value?.extension.equals("py", ignoreCase = true)
            val indent = AutoIndent.indentAfterNewline(new.text, cursor, python)
            if (indent.isNotEmpty()) {
                next = TextFieldValue(
                    new.text.substring(0, cursor) + indent + new.text.substring(cursor),
                    TextRange(cursor + indent.length)
                )
            }
        }

        _text.value = next
        if (next.text != old.text) scheduleAutosave()
    }

    /** Insere [text] no cursor (ou no lugar da seleção). Usado pelos botões de símbolos. */
    fun insert(text: String) {
        val value = _text.value
        val selection = value.selection
        val newText = value.text.replaceRange(selection.min, selection.max, text)
        _text.value = TextFieldValue(newText, TextRange(selection.min + text.length))
        scheduleAutosave()
    }

    fun setSpellSymbols(enabled: Boolean) {
        container.settings.spellSymbols = enabled
        _spellSymbols.value = enabled
    }

    // ------------------------------------------------------------ navegação por linha

    fun readLine() {
        val value = _text.value
        announce(lineSpeech(value.text, currentLine()))
    }

    fun moveLine(delta: Int) = moveTo(currentLine() + delta)

    /** [number] conta a partir de 1, como o usuário vê. */
    fun goToLine(number: Int) = moveTo(number - 1)

    private fun currentLine(): Int =
        LineText.lineOfOffset(_text.value.text, _text.value.selection.start)

    private fun moveTo(index: Int) {
        val value = _text.value
        val target = index.coerceIn(0, LineText.lineCount(value.text) - 1)
        _text.value = value.copy(selection = TextRange(LineText.lineStart(value.text, target)))
        announce(lineSpeech(value.text, target))
    }

    private fun lineSpeech(text: String, index: Int): String {
        val content = LineText.lineContent(text, index)
        val body = if (content.isBlank()) {
            str(R.string.line_blank)
        } else {
            val indent = LineText.indentOf(content)
            val spoken = LineText.spoken(content, _spellSymbols.value) { str(it) }
            if (indent > 0) str(R.string.line_indent, indent) + ", " + spoken else spoken
        }
        return str(R.string.line_spoken, index + 1, LineText.lineCount(text), body)
    }

    // ------------------------------------------------------------ salvar

    fun save() {
        viewModelScope.launch {
            autosaveJob?.cancel()
            saveNow()
            announce(R.string.file_saved)
        }
    }

    fun saveQuietly() {
        viewModelScope.launch { saveNow() }
    }

    fun closeFile() {
        viewModelScope.launch {
            autosaveJob?.cancel()
            saveNow()
            _open.value = null
            refresh()
        }
    }

    private suspend fun saveNow() {
        val file = _open.value ?: return
        val current = _text.value.text
        if (current == savedText) return
        withContext(Dispatchers.IO) { files.write(file, current) }
        savedText = current
    }

    // Salva sozinho 2 s depois da última tecla: se o Android matar o app, não se perde o trabalho.
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_MS)
            saveNow()
        }
    }

    // ------------------------------------------------------------ compilar e rodar

    fun run(onStarted: () -> Unit) {
        viewModelScope.launch {
            val file = _open.value ?: return@launch
            autosaveJob?.cancel()
            saveNow()

            if (container.build.commandFor(file) == null) {
                announce(R.string.run_unsupported, file.name)
                return@launch
            }

            // Garante o terminal de pé antes de mandar o comando.
            TerminalService.start(getApplication<Application>())
            val status = session.status
            val current = status.value
            if (current is TerminalSession.Status.Idle || current is TerminalSession.Status.Exited) {
                session.start()
            }
            withTimeoutOrNull(START_WAIT_MS) { status.first { it is TerminalSession.Status.Running } }

            when (container.build.run(file)) {
                BuildCoordinator.Outcome.STARTED -> onStarted()
                BuildCoordinator.Outcome.UNSUPPORTED -> announce(R.string.run_unsupported, file.name)
                BuildCoordinator.Outcome.BUSY -> announce(R.string.run_busy)
            }
        }
    }

    fun openDiagnostic(diagnostic: BuildCoordinator.Diagnostic) {
        openFile(diagnostic.file, diagnostic.line)
    }

    private companion object {
        const val AUTOSAVE_MS = 2_000L
        const val START_WAIT_MS = 15_000L
    }
}
