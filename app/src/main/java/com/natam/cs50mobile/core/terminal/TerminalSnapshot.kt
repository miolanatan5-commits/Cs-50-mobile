package com.natam.cs50mobile.core.terminal

/**
 * Foto imutável da saída do terminal.
 * [firstIndex] é o índice ABSOLUTO da primeira linha guardada: serve de chave estável na lista,
 * mesmo quando linhas antigas são descartadas do início do buffer.
 */
data class TerminalSnapshot(
    val firstIndex: Long,
    val lines: List<String>,
    /** Linha ainda em construção (ex.: o prompt "root@cs50:~# "). */
    val partial: String,
    val version: Long
) {
    val endIndex: Long get() = firstIndex + lines.size

    companion object {
        val EMPTY = TerminalSnapshot(0L, emptyList(), "", 0L)
    }
}
