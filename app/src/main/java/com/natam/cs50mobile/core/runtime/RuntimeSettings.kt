package com.natam.cs50mobile.core.runtime

import android.content.Context

class RuntimeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)

    var rootfsUrl: String
        get() = prefs.getString("rootfsUrl", DEFAULT_ROOTFS_URL).orEmpty().ifBlank { DEFAULT_ROOTFS_URL }
        set(value) = prefs.edit().putString("rootfsUrl", value.trim()).apply()

    /** Opcional: se preenchido, o download é verificado contra este SHA-256. */
    var rootfsSha256: String
        get() = prefs.getString("rootfsSha256", "").orEmpty()
        set(value) = prefs.edit().putString("rootfsSha256", value.trim().lowercase()).apply()

    /** Ligado automaticamente se o PRoot cair logo ao iniciar (alguns kernels Samsung). */
    var noSeccomp: Boolean
        get() = prefs.getBoolean("noSeccomp", false)
        set(value) = prefs.edit().putBoolean("noSeccomp", value).apply()

    var announceOutput: Boolean
        get() = prefs.getBoolean("announceOutput", true)
        set(value) = prefs.edit().putBoolean("announceOutput", value).apply()

    /** No editor, ler a linha com os símbolos por extenso ("abre chaves", "ponto e vírgula"). */
    var spellSymbols: Boolean
        get() = prefs.getBoolean("spellSymbols", true)
        set(value) = prefs.edit().putBoolean("spellSymbols", value).apply()

    companion object {
        // Ubuntu Base é o rootfs oficial mais enxuto (~30 MB baixados). O nome do arquivo muda a cada
        // ponto de release (24.04.x): se der 404, troque o endereço na tela de instalação.
        const val DEFAULT_ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz"
    }
}
