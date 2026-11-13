package com.natam.cs50mobile.core.terminal

/** Padrões do prompt do bash (PS1 = `\u@cs50:\w\$ `), usados para saber quando um comando terminou. */
object ShellPrompt {
    /** Linha ainda em construção que parece um prompt: "root@cs50:~# ". */
    val AT_PROMPT = Regex("""[#$] ?$""")

    /** Linha já fechada que é o eco de um comando digitado depois do prompt. */
    val ECHO_LINE = Regex("""^\S+@cs50:\S*[#$] """)
}
