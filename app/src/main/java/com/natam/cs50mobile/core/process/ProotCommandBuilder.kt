package com.natam.cs50mobile.core.process

import com.natam.cs50mobile.core.runtime.RuntimePaths
import com.natam.cs50mobile.core.runtime.RuntimeSettings
import java.io.File

/** Monta a linha de comando e o ambiente para executar algo dentro do rootfs via PRoot. */
class ProotCommandBuilder(
    private val paths: RuntimePaths,
    private val settings: RuntimeSettings
) {
    data class LaunchSpec(
        val argv: List<String>,
        val env: Map<String, String>,
        val workDir: File
    )

    /**
     * Shell interativo. Rodar o bash dentro de `script` dá a ele um pseudo-terminal de verdade
     * (Ctrl+C vira SIGINT, apt e programas interativos funcionam) sem precisar de código nativo. O `--noediting`
     * desliga o readline: a saída sai como texto puro, sem sequências de cursor, que é o que um
     * leitor de tela quer ler.
     */
    fun shell(): LaunchSpec {
        val hasScript = File(paths.rootfsDir, "usr/bin/script").exists()
        val guest = if (hasScript) {
            listOf(
                "/usr/bin/script", "-qefc",
                "exec /bin/bash --noprofile --norc --noediting -i",
                "/dev/null"
            )
        } else {
            listOf("/bin/bash", "--noprofile", "--norc", "--noediting", "-i")
        }
        return build(guest)
    }

    /** Executa um comando avulso dentro do Linux (daemons, tarefas de preparo). */
    fun exec(vararg guest: String): LaunchSpec = build(guest.toList())

    // O terminal abre direto na pasta que o editor enxerga.
    private fun build(guest: List<String>, cwd: String = "/root/workspace"): LaunchSpec {
        paths.prepareDirs()
        FakeProc.ensure(paths.fakeProcDir)

        val argv = ArrayList<String>()
        argv += paths.prootBinary.path
        argv += listOf("--kill-on-exit", "--link2symlink", "-0")
        argv += listOf("-r", paths.rootfsDir.path)
        argv.bind("/dev")
        argv.bind("/proc")
        argv.bind("/sys")
        argv.bind("/proc/self/fd", "/dev/fd")
        argv.bind(paths.workspaceDir.path, "/root/workspace")
        // O Android bloqueia a leitura destes arquivos de /proc para apps: usa versões falsas.
        for (name in FakeProc.FILES.keys) {
            val fake = File(paths.fakeProcDir, name)
            if (fake.exists()) argv.bind(fake.path, "/proc/$name")
        }
        argv += listOf("-w", cwd)
        argv += guest

        val env = LinkedHashMap<String, String>()
        env["PROOT_TMP_DIR"] = paths.prootTmpDir.path
        env["PROOT_LOADER"] = paths.prootLoader.path
        env["LD_LIBRARY_PATH"] = paths.nativeLibDir.path // caso o PRoot dependa de libtalloc.so
        if (settings.noSeccomp) env["PROOT_NO_SECCOMP"] = "1"
        env["HOME"] = "/root"
        env["USER"] = "root"
        env["LOGNAME"] = "root"
        env["SHELL"] = "/bin/bash"
        env["LANG"] = "C.UTF-8"
        env["TERM"] = "dumb"
        env["TMPDIR"] = "/tmp"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["PS1"] = """\u@cs50:\w\$ """
        env["DEBIAN_FRONTEND"] = "noninteractive"
        env["PIP_BREAK_SYSTEM_PACKAGES"] = "1" // o Ubuntu 24.04 recusa pip fora de venv sem isto

        // Ambiente de compilação do CS50: `make hello` já compila hello.c do jeito do curso.
        env["CC"] = "clang"
        env["CFLAGS"] = cflags()
        env["LDLIBS"] = "-lcrypt -lcs50 -lm"

        return LaunchSpec(argv, env, paths.workspaceDir)
    }

    /** Flags do CS50. Os sanitizers só entram se o instalador confirmou que funcionam neste aparelho. */
    private fun cflags(): String {
        val sanitizers = File(paths.rootfsDir, "usr/local/share/cs50-sanitize").exists()
        val base = "-ggdb3 -O0 -std=c11 -Wall -Werror -Wextra -Wno-sign-compare " +
            "-Wno-unused-parameter -Wno-unused-variable -Wshadow"
        return if (sanitizers) {
            "-fsanitize=signed-integer-overflow -fsanitize=undefined $base"
        } else {
            base
        }
    }

    private fun MutableList<String>.bind(host: String, guest: String = host) {
        add("-b")
        add("$host:$guest")
    }
}

/** Conteúdo mínimo para ferramentas como uptime, free, top e Node.js não quebrarem. */
internal object FakeProc {
    val FILES: Map<String, String> = mapOf(
        "loadavg" to "0.00 0.00 0.00 1/100 1\n",
        "uptime" to "12345.67 12345.67\n",
        "version" to "Linux version 6.1.0-cs50 (proot) #1 SMP PREEMPT\n",
        "stat" to buildString {
            append("cpu  100 0 100 10000 0 0 0 0 0 0\n")
            for (i in 0 until 4) append("cpu$i 25 0 25 2500 0 0 0 0 0 0\n")
            append("intr 0\nctxt 0\nbtime 1700000000\nprocesses 100\nprocs_running 1\nprocs_blocked 0\n")
        }
    )

    fun ensure(dir: File) {
        dir.mkdirs()
        for ((name, content) in FILES) {
            val f = File(dir, name)
            if (!f.exists()) f.writeText(content)
        }
    }
}
