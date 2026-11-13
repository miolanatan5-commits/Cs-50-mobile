package com.natam.cs50mobile.core.files

import com.natam.cs50mobile.core.runtime.deleteTree
import java.io.File

/**
 * Acesso às pastas e arquivos do workspace (a pasta que aparece como /root/workspace no Linux).
 * Tudo passa por [isInside]: nenhum caminho que escape do workspace (por "..", ou por um symlink
 * criado de dentro do Linux apontando para fora) é aceito.
 */
class WorkspaceFiles(val root: File) {

    data class Entry(val file: File, val isDirectory: Boolean, val size: Long) {
        val name: String get() = file.name
    }

    enum class CreateResult { OK, INVALID_NAME, EXISTS, ERROR }

    fun isInside(file: File): Boolean {
        val path = file.canonicalFile.path
        val base = root.canonicalFile.path
        return path == base || path.startsWith(base + File.separator)
    }

    fun isRoot(dir: File): Boolean = dir.canonicalFile == root.canonicalFile

    /** Pastas primeiro, depois arquivos, cada grupo em ordem alfabética sem diferenciar maiúsculas. */
    fun list(dir: File): List<Entry> {
        root.mkdirs()
        if (!isInside(dir)) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children
            .map { Entry(it, it.isDirectory, if (it.isFile) it.length() else 0L) }
            .sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun isValidName(name: String): Boolean = NAME.matches(name) && name != "." && name != ".."

    fun createFile(dir: File, name: String): Pair<CreateResult, File?> {
        val target = prepareTarget(dir, name)
        if (target.first != CreateResult.OK) return target.first to null
        val file = target.second!!
        return try {
            if (file.createNewFile()) CreateResult.OK to file else CreateResult.ERROR to null
        } catch (_: Exception) {
            CreateResult.ERROR to null
        }
    }

    fun createFolder(dir: File, name: String): CreateResult {
        val target = prepareTarget(dir, name)
        if (target.first != CreateResult.OK) return target.first
        return if (target.second!!.mkdir()) CreateResult.OK else CreateResult.ERROR
    }

    private fun prepareTarget(dir: File, name: String): Pair<CreateResult, File?> {
        if (!isValidName(name)) return CreateResult.INVALID_NAME to null
        if (!isInside(dir)) return CreateResult.ERROR to null
        val file = File(dir, name)
        if (file.exists()) return CreateResult.EXISTS to null
        return CreateResult.OK to file
    }

    fun delete(file: File): Boolean {
        if (!isInside(file) || file.canonicalFile == root.canonicalFile) return false
        deleteTree(file)
        return !file.exists()
    }

    /** Texto do arquivo, ou null se for grande demais para editar aqui. */
    fun read(file: File): String? {
        if (!isInside(file) || file.length() > MAX_EDIT_BYTES) return null
        return file.readText()
    }

    /** Grava por arquivo temporário + rename: se o app morrer no meio, o arquivo original sobrevive. */
    fun write(file: File, text: String) {
        if (!isInside(file)) return
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    /** "workspace" ou "workspace/pset1", para mostrar e falar. */
    fun relative(file: File): String {
        val rel = relativePart(file)
        return if (rel.isEmpty()) ROOT_LABEL else "$ROOT_LABEL/$rel"
    }

    /** Caminho do mesmo arquivo visto de dentro do Linux (PRoot). */
    fun guestPath(file: File): String {
        val rel = relativePart(file)
        return if (rel.isEmpty()) GUEST_ROOT else "$GUEST_ROOT/$rel"
    }

    private fun relativePart(file: File): String =
        file.canonicalFile.relativeTo(root.canonicalFile).path

    companion object {
        const val ROOT_LABEL = "workspace"
        const val GUEST_ROOT = "/root/workspace"
        const val MAX_EDIT_BYTES = 1024L * 1024
        private val NAME = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
