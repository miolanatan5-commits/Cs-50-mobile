package com.natam.cs50mobile.ui

/** Texto para o TalkBack ler. [seq] muda a cada aviso, mesmo quando o texto se repete. */
data class Announcement(val seq: Long = 0L, val text: String = "")
