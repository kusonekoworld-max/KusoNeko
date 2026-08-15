package com.koneko.kerneltweak.tweaks

import android.os.Build
import com.koneko.kerneltweak.root.RootShell

data class InfoItem(val label: String, val value: String)

/**
 * Device + kernel info dump for the Info tab. Most of this doesn't
 * strictly need root (Build.* and /proc reads are usually world-
 * readable), but a couple (getenforce, some OEM /proc/version
 * restrictions) behave better with it, so this still goes through
 * RootShell rather than mixing raw File reads with root reads.
 */
object KernelInfoTweaks {

    fun collect(): List<InfoItem> {
        val items = mutableListOf<InfoItem>()

        items += InfoItem("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        items += InfoItem("Android version", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        items += InfoItem("Build fingerprint", Build.FINGERPRINT)

        RootShell.cmd("uname -r").firstOrNull()?.let { items += InfoItem("Kernel release", it) }
        RootShell.cmd("uname -m").firstOrNull()?.let { items += InfoItem("Architecture", it) }
        RootShell.read("/proc/version")?.let { items += InfoItem("Kernel version string", it) }

        RootShell.cmd("getenforce").firstOrNull()?.let { items += InfoItem("SELinux", it) }

        RootShell.read("/proc/cpuinfo")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { items += InfoItem("CPU hardware", it) }

        RootShell.cmd("nproc").firstOrNull()?.let { items += InfoItem("CPU cores", it) }

        RootShell.read("/proc/meminfo")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("MemTotal") }
            ?.substringAfter(":")
            ?.trim()
            ?.let { items += InfoItem("Total RAM", it) }

        RootShell.cmd("id").firstOrNull()?.let { items += InfoItem("Shell identity", it) }

        return items
    }
}
