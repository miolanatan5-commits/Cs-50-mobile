package com.natam.cs50mobile.core.process

internal object ProcessIds {
    /**
     * PID do processo filho, ou -1 se não der para descobrir. Necessário para enviar sinais.
     * Usa reflexão porque Process.pid() não aparece na android.jar de todas as versões.
     */
    fun pidOf(process: Process): Int {
        try {
            val value = process.javaClass.getMethod("pid").invoke(process)
            if (value is Number) return value.toInt()
        } catch (_: Throwable) {
        }
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (_: Throwable) {
            -1
        }
    }
}
