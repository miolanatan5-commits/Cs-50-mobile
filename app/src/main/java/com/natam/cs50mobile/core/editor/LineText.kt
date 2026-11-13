package com.natam.cs50mobile.core.editor

import com.natam.cs50mobile.R

/** Operações por linha sobre o texto do editor, e a "fala" de uma linha de código para o TalkBack. */
object LineText {
    /** Símbolos do atalho do editor (digitar chaves e ponto e vírgula no teclado do celular é lento). */
    val SYMBOLS: List<Pair<String, Int>> = listOf(
        "{" to R.string.sym_open_brace,
        "}" to R.string.sym_close_brace,
        "(" to R.string.sym_open_paren,
        ")" to R.string.sym_close_paren,
        "[" to R.string.sym_open_bracket,
        "]" to R.string.sym_close_bracket,
        ";" to R.string.sym_semicolon,
        "\"" to R.string.sym_quote,
        "'" to R.string.sym_apostrophe,
        "#" to R.string.sym_hash,
        "*" to R.string.sym_asterisk,
        "&" to R.string.sym_ampersand,
        "<" to R.string.sym_less,
        ">" to R.string.sym_greater,
        "\\" to R.string.sym_backslash,
        "|" to R.string.sym_pipe
    )

    private val SPOKEN: Map<Char, Int> =
        SYMBOLS.associate { it.first[0] to it.second } + mapOf(
            ',' to R.string.sym_comma,
            '.' to R.string.sym_dot,
            ':' to R.string.sym_colon,
            '=' to R.string.sym_equals,
            '+' to R.string.sym_plus,
            '-' to R.string.sym_minus,
            '/' to R.string.sym_slash,
            '!' to R.string.sym_exclam,
            '%' to R.string.sym_percent,
            '_' to R.string.sym_underscore,
            '~' to R.string.sym_tilde,
            '^' to R.string.sym_caret,
            '?' to R.string.sym_question,
            '@' to R.string.sym_at,
            '$' to R.string.sym_dollar,
            '`' to R.string.sym_backtick
        )

    private const val MAX_SPOKEN = 300
    private val SPACES = Regex("\\s+")

    fun lineCount(text: String): Int = 1 + text.count { it == '\n' }

    /** Índice (a partir de 0) da linha onde está o [offset]. */
    fun lineOfOffset(text: String, offset: Int): Int {
        val end = offset.coerceIn(0, text.length)
        var lines = 0
        for (i in 0 until end) if (text[i] == '\n') lines++
        return lines
    }

    /** Posição do primeiro caractere da linha [line] (a partir de 0). */
    fun lineStart(text: String, line: Int): Int {
        if (line <= 0) return 0
        var index = 0
        var found = 0
        while (found < line) {
            val newline = text.indexOf('\n', index)
            if (newline < 0) return text.length
            index = newline + 1
            found++
        }
        return index
    }

    fun lineContent(text: String, line: Int): String {
        val start = lineStart(text, line)
        val newline = text.indexOf('\n', start)
        val end = if (newline < 0) text.length else newline
        return text.substring(start, end).trimEnd('\r')
    }

    /** Quantos espaços de recuo a linha tem (tab conta 4). */
    fun indentOf(line: String): Int {
        var spaces = 0
        for (c in line) {
            when (c) {
                ' ' -> spaces++
                '\t' -> spaces += 4
                else -> return spaces
            }
        }
        return spaces
    }

    /**
     * Texto da linha pronto para ser falado. Com [spell], os símbolos viram palavras
     * ("printf abre parênteses aspas olá aspas fecha parênteses ponto e vírgula"), porque a leitura
     * normal do TalkBack costuma engolir pontuação, e em código a pontuação é o que importa.
     */
    fun spoken(content: String, spell: Boolean, name: (Int) -> String): String {
        val trimmed = content.trim()
        if (!spell) return trimmed.take(MAX_SPOKEN)
        val out = StringBuilder()
        for (c in trimmed) {
            val word = SPOKEN[c]
            if (word != null) out.append(' ').append(name(word)).append(' ') else out.append(c)
        }
        return out.toString().replace(SPACES, " ").trim().take(MAX_SPOKEN)
    }
}

/** Recuo automático ao apertar Enter, como qualquer editor de código faz. */
object AutoIndent {
    /**
     * Recuo a inserir logo depois do '\n' que acabou de ser digitado em [cursor - 1].
     * Repete o recuo da linha de cima e soma 4 espaços depois de "{" (ou ":" em Python).
     */
    fun indentAfterNewline(text: String, cursor: Int, python: Boolean): String {
        val newlineIndex = cursor - 1
        if (newlineIndex < 0 || newlineIndex >= text.length) return ""
        val start = text.lastIndexOf('\n', newlineIndex - 1) + 1
        val previous = text.substring(start, newlineIndex)
        val indent = previous.takeWhile { it == ' ' || it == '\t' }
        val trimmed = previous.trimEnd()
        val extra = if (trimmed.endsWith("{") || (python && trimmed.endsWith(":"))) "    " else ""
        return indent + extra
    }
}
