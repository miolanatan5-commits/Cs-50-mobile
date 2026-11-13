package com.natam.cs50mobile.core.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Apaga uma árvore SEM seguir symlinks. File.deleteRecursively() segue links para diretórios,
 * e um rootfs tem links absolutos (ex.: /var/run -> /run) que apontariam para fora dele.
 */
internal fun deleteTree(file: File) {
    val path = file.toPath()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        file.listFiles()?.forEach { deleteTree(it) }
    }
    file.delete()
}
