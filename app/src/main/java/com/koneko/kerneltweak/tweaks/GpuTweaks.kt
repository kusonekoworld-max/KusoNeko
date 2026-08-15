package com.koneko.kerneltweak.tweaks

import com.koneko.kerneltweak.root.RootShell

data class GpuState(
    val available: Boolean,
    val nodePath: String?,
    val currentGovernor: String?,
    val availableGovernors: List<String>,
    val minFreq: Long?,
    val maxFreq: Long?,
    val availableFrequencies: List<Long>
)

/**
 * Universal GPU path resolution via the generic Linux devfreq framework
 * (/sys/class/devfreq/<device>/), which every major vendor's GPU driver
 * registers into — kgsl-3d0 (Adreno/Snapdragon), Mali (Exynos/MediaTek/
 * Unisoc), PowerVR/IMG, etc. Instead of hardcoding one vendor's path, we
 * enumerate /sys/class/devfreq and pick the entry that looks like a GPU,
 * so this works across chipsets without per-device branches.
 *
 * Falls back to the legacy kgsl symlink directly if devfreq enumeration
 * comes up empty (some older kernels expose it there but not mirrored
 * under /sys/class/devfreq).
 */
object GpuTweaks {

    private const val DEVFREQ_ROOT = "/sys/class/devfreq"
    private const val KGSL_FALLBACK = "/sys/class/kgsl/kgsl-3d0/devfreq"

    private val GPU_NAME_HINTS = listOf("gpu", "kgsl", "mali", "img", "kbase", "gpufreq")

    private var cachedNodePath: String? = null

    /** Resolve the active GPU devfreq node once and cache it for this process. */
    private fun resolveNodePath(): String? {
        cachedNodePath?.let { if (RootShell.exists(it)) return it }

        val entries = RootShell.cmd("ls $DEVFREQ_ROOT 2>/dev/null")
        val match = entries.firstOrNull { name ->
            GPU_NAME_HINTS.any { hint -> name.contains(hint, ignoreCase = true) }
        }
        if (match != null) {
            val path = "$DEVFREQ_ROOT/$match"
            cachedNodePath = path
            return path
        }

        if (RootShell.exists(KGSL_FALLBACK)) {
            cachedNodePath = KGSL_FALLBACK
            return KGSL_FALLBACK
        }
        return null
    }

    fun getState(): GpuState {
        val node = resolveNodePath()
            ?: return GpuState(false, null, null, emptyList(), null, null, emptyList())

        return GpuState(
            available = true,
            nodePath = node,
            currentGovernor = RootShell.read("$node/governor"),
            availableGovernors = RootShell.read("$node/available_governors")
                ?.trim()?.split(" ") ?: emptyList(),
            minFreq = RootShell.read("$node/min_freq")?.trim()?.toLongOrNull(),
            maxFreq = RootShell.read("$node/max_freq")?.trim()?.toLongOrNull(),
            availableFrequencies = RootShell.read("$node/available_frequencies")
                ?.trim()?.split(" ")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        )
    }

    fun setGovernor(governor: String): Boolean {
        val node = resolveNodePath() ?: return false
        return RootShell.write("$node/governor", governor)
    }

    fun setMinFreq(freqHz: Long): Boolean {
        val node = resolveNodePath() ?: return false
        return RootShell.write("$node/min_freq", freqHz.toString())
    }

    fun setMaxFreq(freqHz: Long): Boolean {
        val node = resolveNodePath() ?: return false
        return RootShell.write("$node/max_freq", freqHz.toString())
    }
}
