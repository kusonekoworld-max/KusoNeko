package com.koneko.kerneltweak.root

import com.topjohnwu.superuser.Shell

/**
 * Thin wrapper around libsu. Keep ALL su access funneled through here so
 * every tweak module shares one shell session instead of spawning `su`
 * per read/write.
 */
object RootShell {

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    /** Call once, e.g. from Application.onCreate(), to request root early. */
    fun requestRoot(onResult: (granted: Boolean) -> Unit) {
        Shell.getShell { shell -> onResult(shell.isRoot) }
    }

    fun isRootGranted(): Boolean = Shell.isAppGrantedRoot() == true

    /** Read a sysfs node. Returns null if the path doesn't exist or read fails. */
    fun read(path: String): String? {
        val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
        if (!result.isSuccess) return null
        val out = result.out.joinToString("\n").trim()
        return out.ifEmpty { null }
    }

    /** Write a value to a sysfs node. Returns true on success. */
    fun write(path: String, value: String): Boolean {
        val result = Shell.cmd("echo '$value' > '$path' 2>/dev/null").exec()
        return result.isSuccess
    }

    /** Check whether a sysfs node exists and is accessible at all. */
    fun exists(path: String): Boolean {
        val result = Shell.cmd("[ -e '$path' ] && echo 1 || echo 0").exec()
        return result.out.joinToString("").trim() == "1"
    }

    /** Run an arbitrary shell command as root, e.g. for globbing cpu dirs. */
    fun cmd(command: String): List<String> {
        val result = Shell.cmd(command).exec()
        return if (result.isSuccess) result.out else emptyList()
    }
}
