package com.natam.cs50mobile.core.runtime

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Extrator de tar.gz em Kotlin puro (sem biblioteca extra, para não inchar o APK).
 * Lê em streaming com buffer fixo, então o uso de RAM é constante, não importa o tamanho do rootfs.
 *
 * Suporta: arquivos, diretórios, symlinks, hard links, nomes longos GNU (L/K) e cabeçalhos PAX.
 * Ignora: dispositivos, FIFOs. Rejeita caminhos com ".." e qualquer escrita que escape de [destDir].
 */
object TarGzExtractor {
    private const val BLOCK = 512
    private const val MAX_META = 1 shl 20

    fun extract(
        archive: File,
        destDir: File,
        ensureActive: () -> Unit,
        onProgress: (entries: Int) -> Unit
    ) {
        destDir.mkdirs()
        val root = destDir.canonicalFile
        val dirModes = ArrayList<Pair<File, Int>>()
        val copyBuffer = ByteArray(64 * 1024)
        var entries = 0

        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null

        GZIPInputStream(archive.inputStream().buffered(64 * 1024), 64 * 1024).use { input ->
            val header = ByteArray(BLOCK)
            while (true) {
                ensureActive()
                if (!readFully(input, header)) break
                if (header.all { it == 0.toByte() }) break

                val type = (header[156].toInt() and 0xFF).toChar()
                val size = parseSize(header)

                // Entradas de metadados: valem para a próxima entrada de verdade.
                when (type) {
                    'L' -> { longName = readText(input, size); continue }
                    'K' -> { longLink = readText(input, size); continue }
                    'x' -> {
                        val pax = parsePax(readText(input, size))
                        paxPath = pax["path"]
                        paxLink = pax["linkpath"]
                        continue
                    }
                    'g' -> { skipFully(input, padded(size)); continue }
                }

                val rawName = longName ?: paxPath ?: headerName(header)
                val rawLink = longLink ?: paxLink ?: cString(header, 157, 100)
                longName = null; longLink = null; paxPath = null; paxLink = null

                val rel = sanitize(rawName)
                val mode = parseOctal(header, 100, 8).toInt()

                if (rel == null) { // caminho perigoso
                    skipFully(input, padded(size))
                    continue
                }
                if (rel.isEmpty()) { // a própria raiz ("./")
                    skipFully(input, padded(size))
                    continue
                }

                val target = File(root, rel)
                val parent = target.parentFile
                if (parent == null || !isInside(root, parent)) {
                    skipFully(input, padded(size))
                    continue
                }

                when (type) {
                    '5' -> {
                        if (isInside(root, target)) {
                            target.mkdirs()
                            dirModes.add(target to mode)
                        }
                        skipFully(input, padded(size))
                    }
                    '0', '\u0000', '7' -> {
                        parent.mkdirs()
                        removeIfLink(target)
                        FileOutputStream(target).use { out ->
                            var left = size
                            while (left > 0) {
                                val n = input.read(copyBuffer, 0, minOf(copyBuffer.size.toLong(), left).toInt())
                                if (n < 0) throw IOException("tar truncado em $rel")
                                out.write(copyBuffer, 0, n)
                                left -= n
                            }
                        }
                        skipFully(input, padded(size) - size)
                        chmod(target, (mode and 0x1FF) or 0x180) // rw para o dono, sem bits especiais
                    }
                    '2' -> {
                        parent.mkdirs()
                        removeIfLink(target)
                        target.delete()
                        try {
                            Os.symlink(rawLink, target.path)
                        } catch (_: ErrnoException) {
                        }
                        skipFully(input, padded(size))
                    }
                    '1' -> {
                        parent.mkdirs()
                        val source = sanitize(rawLink)?.let { File(root, it) }
                        if (source != null && source.exists() && isInside(root, source)) {
                            target.delete()
                            try {
                                Os.link(source.path, target.path)
                            } catch (_: ErrnoException) {
                                source.copyTo(target, overwrite = true)
                            }
                        }
                        skipFully(input, padded(size))
                    }
                    else -> skipFully(input, padded(size))
                }

                entries++
                if (entries % 100 == 0) onProgress(entries)
            }
        }

        // Modos de diretório só no fim: um diretório 0555 impediria de criar os arquivos dentro dele.
        for ((dir, mode) in dirModes.asReversed()) {
            chmod(dir, (mode and 0x1FF) or 0x1C0) // dono sempre com rwx
        }
        onProgress(entries)
    }

    private fun chmod(file: File, mode: Int) {
        try {
            Os.chmod(file.path, mode)
        } catch (_: ErrnoException) {
        }
    }

    private fun removeIfLink(file: File) {
        if (Files.isSymbolicLink(file.toPath())) file.delete()
    }

    private fun isInside(root: File, file: File): Boolean {
        val path = file.canonicalFile.path
        return path == root.path || path.startsWith(root.path + File.separator)
    }

    /** Normaliza o caminho do tar. Devolve null se tiver "..", "" para a raiz. */
    private fun sanitize(name: String): String? {
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun headerName(h: ByteArray): String {
        val name = cString(h, 0, 100)
        val isUstar = String(h, 257, 5, Charsets.ISO_8859_1) == "ustar"
        val prefix = if (isUstar) cString(h, 345, 155) else ""
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun cString(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end] != 0.toByte()) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(h: ByteArray, off: Int, len: Int): Long {
        val s = cString(h, off, len).trim()
        return if (s.isEmpty()) 0L else s.toLongOrNull(8) ?: 0L
    }

    private fun parseSize(h: ByteArray): Long {
        if ((h[124].toInt() and 0x80) != 0) { // base-256 (arquivos > 8 GB)
            var v = 0L
            for (i in 125 until 136) v = (v shl 8) or (h[i].toLong() and 0xFF)
            return v
        }
        return parseOctal(h, 124, 12)
    }

    private fun padded(size: Long): Long = (size + BLOCK - 1) / BLOCK * BLOCK

    private fun readText(input: InputStream, size: Long): String {
        if (size > MAX_META) {
            skipFully(input, padded(size))
            return ""
        }
        val bytes = ByteArray(size.toInt())
        if (!readFully(input, bytes)) throw IOException("tar truncado")
        skipFully(input, padded(size) - size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n')
    }

    /** Registros PAX: "<tamanho> chave=valor\n". Lido como ISO-8859-1 para o tamanho (em bytes) bater. */
    private fun parsePax(text: String): Map<String, String> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val raw = String(bytes, Charsets.ISO_8859_1)
        val out = HashMap<String, String>()
        var i = 0
        while (i < raw.length) {
            val space = raw.indexOf(' ', i)
            if (space < 0) break
            val len = raw.substring(i, space).toIntOrNull() ?: break
            if (len <= 0 || i + len > raw.length) break
            val record = raw.substring(space + 1, i + len).trimEnd('\n')
            val eq = record.indexOf('=')
            if (eq > 0) {
                val value = String(record.substring(eq + 1).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                out[record.substring(0, eq)] = value
            }
            i += len
        }
        return out
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) {
                if (off == 0) return false // fim limpo do arquivo, na fronteira de um bloco
                throw IOException("tar truncado")
            }
            off += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val sink = ByteArray(8192)
        while (left > 0) {
            val n = input.read(sink, 0, minOf(sink.size.toLong(), left).toInt())
            if (n < 0) throw IOException("tar truncado")
            left -= n
        }
    }
}
