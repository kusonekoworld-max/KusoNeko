package com.koneko.kerneltweak.tweaks

import com.koneko.kerneltweak.root.RootShell

data class CpuPolicy(
    val policyId: String,      // e.g. "policy0"
    val cpus: List<Int>,       // cpus sharing this policy (cluster)
    val currentGovernor: String?,
    val availableGovernors: List<String>,
    val currentFreq: Int?,     // kHz, actual hardware freq when readable
    val minFreq: Int?,
    val maxFreq: Int?,
    val availableFrequencies: List<Int>
)

/**
 * Primary path: /sys/devices/system/cpu/cpufreq/policyN/, the standard
 * cpufreq layout on GKI kernels, covering clustered (big.LITTLE / tri-
 * cluster) setups across Qualcomm/MediaTek/Exynos without hardcoding a
 * chipset or CPU count.
 *
 * Fallback path: some older/simpler kernels don't expose the clustered
 * /cpufreq/policyN/ dir at all and only have per-core nodes under
 * /cpu/cpuN/cpufreq/. When policyN is absent, we synthesize one "policy"
 * per physical CPU from that layout so the rest of the app doesn't need
 * to care which layout the device uses.
 */
object CpuTweaks {

    private const val CPUFREQ_ROOT = "/sys/devices/system/cpu/cpufreq"
    private const val CPU_ROOT = "/sys/devices/system/cpu"

    fun listPolicies(): List<CpuPolicy> {
        val policyDirs = RootShell.cmd("ls $CPUFREQ_ROOT 2>/dev/null")
            .filter { it.startsWith("policy") }
            .sorted()

        if (policyDirs.isNotEmpty()) {
            return policyDirs.map { policyId -> readPolicy("$CPUFREQ_ROOT/$policyId", policyId) }
        }

        // Fallback: per-cpu cpufreq nodes, one synthetic "policy" each.
        val cpuDirs = RootShell.cmd("ls $CPU_ROOT 2>/dev/null")
            .filter { it.matches(Regex("cpu[0-9]+")) }
            .sorted()

        return cpuDirs.mapNotNull { cpuDir ->
            val base = "$CPU_ROOT/$cpuDir/cpufreq"
            if (!RootShell.exists(base)) return@mapNotNull null
            val cpuIndex = cpuDir.removePrefix("cpu").toIntOrNull()
            readPolicy(base, cpuDir, listOf(cpuIndex).filterNotNull())
        }
    }

    private fun readPolicy(base: String, id: String, fallbackCpus: List<Int> = emptyList()): CpuPolicy {
        val cpus = RootShell.readList("$base/related_cpus").mapNotNull { it.toIntOrNull() }
            .ifEmpty { fallbackCpus }

        var governors = RootShell.readList("$base/scaling_available_governors")
        if (governors.isEmpty() && cpus.isNotEmpty()) {
            // Some vendor kernels only populate this under cpuN, not the
            // shared policy dir — fall back to the first cpu in the cluster.
            governors = RootShell.readList("$CPU_ROOT/cpu${cpus.first()}/cpufreq/scaling_available_governors")
        }

        var freqs = RootShell.readList("$base/scaling_available_frequencies")
            .mapNotNull { it.toIntOrNull() }.sorted()
        if (freqs.isEmpty() && cpus.isNotEmpty()) {
            freqs = RootShell.readList("$CPU_ROOT/cpu${cpus.first()}/cpufreq/scaling_available_frequencies")
                .mapNotNull { it.toIntOrNull() }.sorted()
        }

        // Prefer cpuinfo_cur_freq (raw hardware counter) over scaling_cur_freq
        // (governor's last requested value, can lag on some drivers).
        val curFreq = RootShell.read("$base/cpuinfo_cur_freq")?.trim()?.toIntOrNull()
            ?: RootShell.read("$base/scaling_cur_freq")?.trim()?.toIntOrNull()

        return CpuPolicy(
            policyId = id,
            cpus = cpus,
            currentGovernor = RootShell.read("$base/scaling_governor")?.trim(),
            availableGovernors = governors,
            currentFreq = curFreq,
            minFreq = RootShell.read("$base/scaling_min_freq")?.trim()?.toIntOrNull(),
            maxFreq = RootShell.read("$base/scaling_max_freq")?.trim()?.toIntOrNull(),
            availableFrequencies = freqs
        )
    }

    /** Resolve the sysfs base for a given policyId, whichever layout was used. */
    private fun baseFor(policyId: String): String =
        if (policyId.startsWith("policy")) "$CPUFREQ_ROOT/$policyId"
        else "$CPU_ROOT/$policyId/cpufreq"

    fun setGovernor(policyId: String, governor: String): Boolean =
        RootShell.write("${baseFor(policyId)}/scaling_governor", governor)

    fun setMinFreq(policyId: String, freqKHz: Int): Boolean =
        RootShell.write("${baseFor(policyId)}/scaling_min_freq", freqKHz.toString())

    fun setMaxFreq(policyId: String, freqKHz: Int): Boolean =
        RootShell.write("${baseFor(policyId)}/scaling_max_freq", freqKHz.toString())
}