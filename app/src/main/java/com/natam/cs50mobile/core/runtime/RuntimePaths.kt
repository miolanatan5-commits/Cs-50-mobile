package com.natam.cs50mobile.core.runtime

import android.content.Context
import android.os.StatFs
import java.io.File

/** Todos os caminhos do runtime Linux num só lugar. */
class RuntimePaths(context: Context) {
    private val base = File(context.filesDir, "runtime")

    val rootfsDir = File(base, "rootfs")
    val stagingDir = File(base, "rootfs.staging")
    val downloadPart = File(base, "rootfs.tar.gz.part")
    val installedMarker = File(base, ".installed")
    val fakeProcDir = File(base, "fakeproc")

    /** Pasta de projetos do usuário, montada em /root/workspace dentro do Linux. */
    val workspaceDir = File(context.filesDir, "workspace")
    val prootTmpDir = File(context.cacheDir, "proot-tmp")

    // Os executáveis do PRoot vêm do APK (jniLibs) porque o Android 10+ não deixa executar
    // arquivos baixados para filesDir. Só nativeLibraryDir é executável.
    val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
    val prootBinary = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    fun hasProotBinaries(): Boolean = prootBinary.exists() && prootLoader.exists()

    fun prepareDirs() {
        base.mkdirs()
        workspaceDir.mkdirs()
        prootTmpDir.mkdirs()
    }

    fun freeBytes(): Long {
        base.mkdirs()
        return StatFs(base.path).availableBytes
    }
}
