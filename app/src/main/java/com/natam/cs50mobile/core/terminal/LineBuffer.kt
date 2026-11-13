package com.natam.cs50mobile.core.terminal

/**
 * Transforma o fluxo de bytes do shell em linhas de TEXTO PURO, com memória limitada.
 *
 * Em vez de emular uma grade VT100 (que um leitor de tela lê muito mal), o terminal é um fluxo de
 * linhas: sequências de escape (cores, cursor, títulos) são descartadas, '\r' reescreve a linha em
 * andamento (barras de progresso viram UMA linha, não cem) e '\b' apaga um caractere.
 *
 * Thread-safe: a leitura do processo escreve numa thread de IO e a UI lê fotos por [snapshot].
 */
class LineBuffer(
    private val maxLines: Int = 2_000,
    private val maxChars: Int = 400_000,
    private val maxLineLength: Int = 2_000
) {
    private enum class Mode { TEXT, ESC, CSI, OSC, OSC_ESC, CHARSET }

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var firstIndex = 0L
    private var totalChars = 0
    private val current = StringBuilder()
    private var cursor = 0
    private var version = 0L
    private var mode = Mode.TEXT

    // Sobra de bytes UTF-8 incompletos no fim de um bloco (um caractere pode vir cortado ao meio).
    private val pending = ByteArray(4)
    private var pendingLen = 0

    fun append(data: ByteArray, length: Int) = synchronized(lock) {
        val total = pendingLen + length
        val merged = if (pendingLen == 0) {
            data
        } else {
            ByteArray(total).also {
                System.arraycopy(pending, 0, it, 0, pendingLen)
                System.arraycopy(data, 0, it, pendingLen, length)
            }
        }

        var end = total
        var i = total - 1
        var back = 0
        while (i >= 0 && back < 4) {
            val b = merged[i].toInt() and 0xFF
            if ((b and 0xC0) == 0x80) { // byte de continuação: continua procurando o início
                i--
                back++
                continue
            }
            val need = when {
                b >= 0xF0 -> 4
                b >= 0xE0 -> 3
                b >= 0xC0 -> 2
                else -> 1
            }
            if (need > total - i) end = i
            break
        }

        val text = String(merged, 0, end, Charsets.UTF_8)
        pendingLen = total - end
        System.arraycopy(merged, end, pending, 0, pendingLen)

        for (ch in text) feed(ch)
        version++
    }

    /** Adiciona uma linha de mensagem do próprio app (não vinda do shell). */
    fun appendLine(text: String) = synchronized(lock) {
        if (current.isNotEmpty()) commit()
        current.append(text)
        commit()
        version++
    }

    fun clear() = synchronized(lock) {
        firstIndex += lines.size
        lines.clear()
        totalChars = 0
        current.setLength(0)
        cursor = 0
        version++
    }

    fun snapshot(): TerminalSnapshot = synchronized(lock) {
        TerminalSnapshot(firstIndex, lines.toList(), current.toString(), version)
    }

    private fun feed(ch: Char) {
        when (mode) {
            Mode.TEXT -> when {
                ch == '\u001B' -> mode = Mode.ESC
                ch == '\n' -> commit()
                ch == '\r' -> cursor = 0
                ch == '\b' -> if (cursor > 0) cursor--
                ch == '\t' -> repeat(4) { put(' ') }
                ch < ' ' || ch == '\u007F' -> Unit // demais controles (bell etc.)
                else -> put(ch)
            }
            Mode.ESC -> mode = when (ch) {
                '[' -> Mode.CSI
                ']' -> Mode.OSC
                '(', ')', '*', '+' -> Mode.CHARSET
                else -> Mode.TEXT
            }
            Mode.CSI -> if (ch in '@'..'~') mode = Mode.TEXT
            Mode.OSC -> when (ch) {
                '\u0007' -> mode = Mode.TEXT
                '\u001B' -> mode = Mode.OSC_ESC
            }
            Mode.OSC_ESC -> mode = Mode.TEXT
            Mode.CHARSET -> mode = Mode.TEXT
        }
    }

    private fun put(ch: Char) {
        if (cursor < current.length) current.setCharAt(cursor, ch) else current.append(ch)
        cursor++
        if (current.length >= maxLineLength) commit()
    }

    private fun commit() {
        val line = current.toString()
        current.setLength(0)
        cursor = 0
        lines.addLast(line)
        totalChars += line.length + 1
        while (lines.size > maxLines || totalChars > maxChars) {
            totalChars -= lines.removeFirst().length + 1
            firstIndex++
        }
    }
}
