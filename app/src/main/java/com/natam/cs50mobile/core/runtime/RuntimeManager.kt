package com.natam.cs50mobile.core.runtime

import android.system.ErrnoException
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Instala o sistema Linux sob demanda: baixa (com retomada), extrai e deixa pronto para o PRoot.
 * O APK não carrega nenhum rootfs. Tudo roda no escopo do app, então a instalação sobrevive à
 * Activity ser fechada/girada (o TerminalService mantém o processo vivo enquanto isso).
 */
class RuntimeManager(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val paths: RuntimePaths,
    private val settings: RuntimeSettings
) {
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Checking)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        _state.value = when {
            !paths.hasProotBinaries() -> RuntimeState.MissingProot
            paths.installedMarker.exists() -> RuntimeState.Ready
            else -> RuntimeState.NotInstalled
        }
    }

    fun install() {
        if (job?.isActive == true) return
        if (!paths.hasProotBinaries()) {
            _state.value = RuntimeState.MissingProot
            return
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                paths.prepareDirs()
                if (paths.freeBytes() < MIN_FREE_BYTES) {
                    throw IOException(context.getString(com.natam.cs50mobile.R.string.err_no_space))
                }
                download(allowRestart = true)
                extractAndFinish()
                _state.value = RuntimeState.Ready
            } catch (e: CancellationException) {
                _state.value = RuntimeState.NotInstalled
                throw e
            } catch (e: Exception) {
                _state.value = RuntimeState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Remove o sistema Linux instalado (a pasta workspace do usuário é preservada). */
    fun uninstall() {
        if (job?.isActive == true) return
        deleteTree(paths.rootfsDir)
        deleteTree(paths.stagingDir)
        paths.downloadPart.delete()
        paths.installedMarker.delete()
        refresh()
    }

    // ---------------------------------------------------------------- download

    private suspend fun download(allowRestart: Boolean) {
        val url = settings.rootfsUrl
        val part = paths.downloadPart
        var existing = if (part.exists()) part.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CS50Mobile")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val append: Boolean
            when (code) {
                HttpURLConnection.HTTP_PARTIAL -> append = true
                HttpURLConnection.HTTP_OK -> {
                    append = false
                    existing = 0L
                }
                416 -> { // o que existe em disco não serve: recomeça do zero uma vez
                    part.delete()
                    if (allowRestart) {
                        conn.disconnect()
                        return download(allowRestart = false)
                    }
                    throw IOException(context.getString(com.natam.cs50mobile.R.string.err_resume_refused))
                }
                else -> throw IOException(context.getString(com.natam.cs50mobile.R.string.err_download_http, code))
            }

            val remaining = conn.contentLengthLong
            val total = if (remaining >= 0) existing + remaining else -1L
            var done = existing
            var lastEmit = 0L
            _state.value = RuntimeState.Downloading(done, total)

            val ctx = currentCoroutineContext()
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ctx.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150) {
                            lastEmit = now
                            _state.value = RuntimeState.Downloading(done, total)
                        }
                    }
                }
            }
            if (total >= 0 && done != total) throw IOException("Download incompleto ($done de $total bytes)")
            _state.value = RuntimeState.Downloading(done, total)
        } finally {
            conn.disconnect()
        }

        val expected = settings.rootfsSha256
        if (expected.isNotBlank() && sha256(part) != expected) {
            part.delete()
            throw IOException(context.getString(com.natam.cs50mobile.R.string.err_sha))
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val b = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------------------------------------------------------------- extração

    private suspend fun extractAndFinish() {
        val ctx = currentCoroutineContext()
        _state.value = RuntimeState.Extracting(0)

        deleteTree(paths.stagingDir)
        TarGzExtractor.extract(
            archive = paths.downloadPart,
            destDir = paths.stagingDir,
            ensureActive = { ctx.ensureActive() },
            onProgress = { _state.value = RuntimeState.Extracting(it) }
        )
        configureRootfs(paths.stagingDir)

        deleteTree(paths.rootfsDir)
        if (!paths.stagingDir.renameTo(paths.rootfsDir)) {
            throw IOException(context.getString(com.natam.cs50mobile.R.string.err_move))
        }
        paths.downloadPart.delete()
        paths.installedMarker.writeText(System.currentTimeMillis().toString())
    }

    /** Ajustes que o Ubuntu Base precisa para funcionar dentro do PRoot. */
    private fun configureRootfs(dir: File) {
        for (sub in listOf("dev", "proc", "sys", "root", "root/workspace", "tmp", "run", "etc/apt/apt.conf.d")) {
            File(dir, sub).mkdirs()
        }
        try {
            Os.chmod(File(dir, "tmp").path, 0x3FF) // 1777
        } catch (_: ErrnoException) {
        }

        File(dir, "etc/resolv.conf").apply {
            delete() // no Ubuntu Base pode ser um symlink para fora
            writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        }
        File(dir, "etc/hosts").apply {
            delete()
            writeText("127.0.0.1 localhost\n::1 localhost\n")
        }
        File(dir, "etc/hostname").apply {
            delete()
            writeText("cs50\n")
        }
        // O apt tenta rodar como o usuário "_apt", que não existe como uid real dentro do PRoot.
        File(dir, "etc/apt/apt.conf.d/01-cs50-proot").writeText("APT::Sandbox::User \"root\";\n")
    }

    private companion object {
        const val MIN_FREE_BYTES = 400L * 1024 * 1024
    }
}
