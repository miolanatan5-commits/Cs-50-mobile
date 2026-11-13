package com.natam.cs50mobile.core.build

import com.natam.cs50mobile.core.terminal.ShellPrompt

/** Encontra erros do clang/gcc e tracebacks do Python na saída do terminal. */
object CompilerOutputParser {
    data class Raw(
        val file: String,
        val line: Int,
        val column: Int,
        val isError: Boolean,
        val message: String
    )

    // hello.c:5:5: error: use of undeclared identifier 'x'
    private val C_DIAGNOSTIC =
        Regex("""^(.+?):(\d+):(?:(\d+):)?\s*(fatal error|error|warning):\s*(.*)$""")

    //   File "hello.py", line 3, in <module>
    private val PY_FRAME = Regex("""^\s*File "(.+?)", line (\d+)""")

    fun parse(lines: List<String>): List<Raw> {
        val found = ArrayList<Raw>()
        var lastFrameFile: String? = null
        var lastFrameLine = 0

        for (line in lines) {
            val c = C_DIAGNOSTIC.find(line)
            if (c != null) {
                val g = c.groupValues
                found += Raw(
                    file = g[1],
                    line = g[2].toInt(),
                    column = g[3].toIntOrNull() ?: 0,
                    isError = g[4] != "warning",
                    message = g[5].trim()
                )
                continue
            }
            val p = PY_FRAME.find(line)
            if (p != null) {
                lastFrameFile = p.groupValues[1]
                lastFrameLine = p.groupValues[2].toInt()
            }
        }

        // Traceback do Python: o último quadro é onde estourou; a última linha é a exceção.
        val frameFile = lastFrameFile
        if (frameFile != null) {
            val message = lines.lastOrNull {
                it.isNotBlank() && !ShellPrompt.ECHO_LINE.containsMatchIn(it)
            }.orEmpty().trim()
            found += Raw(frameFile, lastFrameLine, 0, true, message)
        }
        return found
    }
}
