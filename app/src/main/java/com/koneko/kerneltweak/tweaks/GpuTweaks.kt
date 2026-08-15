package com.koneko.kerneltweak.tweaks

import com.koneko.kerneltweak.root.RootShell

data class GpuState(
    val nodePath: String,       // full devfreq node dir
    val label: String,          // short display name, e.g. "kgsl-3d0"
    val currentGovernor: String?,
    val availableGovernors: List<String>,
    val currentFreq: Long?,     // Hz
    val minFreq: Long?,         // Hz
    val maxFreq: Long?,         // Hz
    val availableFrequencies: List<Long>
)

/**
 * Scans every GPU-related devfreq node instead of assuming there's only
 * one. Snapdragon devices commonly expose more than one: the core GPU
 * (kgsl-3d0) plus a GPU bandwidth/bus scaling node (gpubw/bwmon) — each
 * with its own governor and frequency table. Missing the second one is
 * why "scan governor GPU" can look incomplete on some devices.
 *
 * Node discovery, in order:
 *  1. /sys/class/kgsl/kgsl-3d0/devfreq — direct Adreno core GPU node,
 *     read first/always since this project's primary targets are
 *     Snapdragon GKI kernels and this path is guaranteed complete.
 *  2. Every entry under /sys/class/devfreq/ whose name matches a GPU
 *     hint (gpu, kgsl, mali, img, kbase, gpubw, bwmon) — covers bus/
 *     bandwidth scaling nodes plus non-Adreno GPUs (Mali, PowerVR) via
 *     the generic Linux devfreq framework.
 * Results are de-duplicated by resolved directory.
 */
object GpuTweaks {

    private const val KGSL_PRIMARY = "/sys/class/kgsl/kgsl-3d0/devfreq"
    private const val DEVFREQ_ROOT = "/sys/class/devfreq"

    private val GPU_NAME_HINTS = listOf("gpu", "kgsl", "mali", "img", "kbase", "gpubw", "bwmon")

    fun scan(): List<GpuState> {
        val nodeDirs = LinkedHashSet<String>()

        if (RootShell.exists(KGSL_PRIMARY)) nodeDirs += KGSL_PRIMARY

        val entries = RootShell.cmd("ls $DEVFREQ_ROOT 2>/dev/null")
        entries.filter { name -> GPU_NAME_HINTS.any { hint -> name.contains(hint, ignoreCase = true) } }
            .forEach { nodeDirs += "$DEVFREQ_ROOT/$it" }

        return nodeDirs.mapNotNull { readNode(it) }
    }

    private fun readNode(node: String): GpuState? {
        if (!RootShell.exists(node)) return null
        val parentDir = node.substringBeforeLast('/')

        var governors = RootShell.readList("$node/available_governors")
        if (governors.isEmpty()) {
            // Older Adreno pwrscale builds expose this one level up.
            governors = RootShell.readList("$parentDir/available_governors")
        }

        var freqs = RootShell.readList("$node/available_frequencies")
            .mapNotNull { it.toLongOrNull() }.sorted()
        if (freqs.isEmpty()) {
            // Legacy Adreno node name before the devfreq framework took over.
            freqs = RootShell.readList("$parentDir/gpu_available_frequencies")
                .mapNotNull { it.toLongOrNull() }.sorted()
        }

        // label: parent dir's basename, e.g. "kgsl-3d0" or "soc:qcom,gpubw"
        val label = parentDir.substringAfterLast('/')

        return GpuState(
            nodePath = node,
            label = label,
            currentGovernor = RootShell.read("$node/governor")?.trim(),
            availableGovernors = governors,
            currentFreq = RootShell.read("$node/cur_freq")?.trim()?.toLongOrNull(),
            minFreq = RootShell.read("$node/min_freq")?.trim()?.toLongOrNull(),
            maxFreq = RootShell.read("$node/max_freq")?.trim()?.toLongOrNull(),
            availableFrequencies = freqs
        )
    }

    fun setGovernor(nodePath: String, governor: String): Boolean =
        RootShell.write("$nodePath/governor", governor)

    fun setMinFreq(nodePath: String, freqHz: Long): Boolean =
        RootShell.write("$nodePath/min_freq", freqHz.toString())

    fun setMaxFreq(nodePath: String, freqHz: Long): Boolean =
        RootShell.write("$nodePath/max_freq", freqHz.toString())
}
