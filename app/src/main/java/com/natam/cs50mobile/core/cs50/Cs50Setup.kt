package com.natam.cs50mobile.core.cs50

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.natam.cs50mobile.core.runtime.RuntimePaths
import java.io.File

/**
 * Instalador das ferramentas do CS50x dentro do Linux. O script (assets/cs50-setup.sh) é copiado
 * para /usr/local/bin/cs50-setup e executado no próprio terminal, para o usuário acompanhar o apt.
 */
object Cs50Setup {
    const val COMMAND = "cs50-setup"
    const val GITHUB_COMMAND = "cs50-github"

    /** Idioma passado aos scripts (eles só conhecem "pt" e "en"). */
    fun lang(): String = if (java.util.Locale.getDefault().language == "pt") "pt" else "en"

    private const val DONE_MARKER = "usr/local/share/cs50-setup.done"
    private val SCRIPTS = mapOf(
        "cs50-setup.sh" to "usr/local/bin/cs50-setup",
        "cs50-github.sh" to "usr/local/bin/cs50-github"
    )
    private const val PUBLIC_KEY = "root/.ssh/id_ed25519.pub"

    fun publicKey(paths: RuntimePaths): String? =
        File(paths.rootfsDir, PUBLIC_KEY).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun isInstalled(paths: RuntimePaths): Boolean = File(paths.rootfsDir, DONE_MARKER).exists()

    fun writeScript(context: Context, paths: RuntimePaths) {
        for ((asset, path) in SCRIPTS) {
            val target = File(paths.rootfsDir, path)
            target.parentFile?.mkdirs()
            context.assets.open(asset).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                Os.chmod(target.path, 0x1ED) // 0755
            } catch (_: ErrnoException) {
            }
        }
    }

    /** Aspas simples para o shell: um valor com espaço, aspas ou $ vira um único argumento seguro. */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
