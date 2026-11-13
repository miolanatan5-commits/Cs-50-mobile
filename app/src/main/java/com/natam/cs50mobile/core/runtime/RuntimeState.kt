package com.natam.cs50mobile.core.runtime

sealed interface RuntimeState {
    data object Checking : RuntimeState

    /** O APK não trouxe libproot.so / libproot-loader.so. */
    data object MissingProot : RuntimeState

    data object NotInstalled : RuntimeState

    /** [total] é -1 quando o servidor não informa o tamanho. */
    data class Downloading(val bytes: Long, val total: Long) : RuntimeState

    data class Extracting(val entries: Int) : RuntimeState

    data object Ready : RuntimeState

    data class Failed(val message: String) : RuntimeState
}
