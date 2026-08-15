package com.koneko.kerneltweak.tweaks

/**
 * One-tap presets that apply a governor + min/max clamp across every CPU
 * policy and every GPU node in one call, instead of tuning each cluster
 * and node by hand. Each preset picks the best-matching governor from
 * whatever that policy/node actually exposes (falls back to the first
 * available one rather than failing silently), so it still does
 * something sensible on devices with a different governor set.
 */
object ProfileTweaks {

    enum class Profile(val label: String) {
        BATTERY_SAVER("Battery Saver"),
        BALANCED("Balanced"),
        PERFORMANCE("Performance")
    }

    private fun pickGovernor(available: List<String>, preferredInOrder: List<String>): String? =
        preferredInOrder.firstOrNull { it in available } ?: available.firstOrNull()

    fun apply(profile: Profile, cpuPolicies: List<CpuPolicy>, gpuNodes: List<GpuState>) {
        val cpuWants = when (profile) {
            Profile.BATTERY_SAVER -> listOf("powersave", "conservative", "userspace")
            Profile.BALANCED -> listOf("schedutil", "walt", "interactive", "ondemand")
            Profile.PERFORMANCE -> listOf("performance", "userspace")
        }
        val gpuWants = when (profile) {
            Profile.BATTERY_SAVER -> listOf("powersave", "simple_ondemand", "msm-adreno-tz")
            Profile.BALANCED -> listOf("msm-adreno-tz", "simple_ondemand", "default")
            Profile.PERFORMANCE -> listOf("performance")
        }

        for (policy in cpuPolicies) {
            pickGovernor(policy.availableGovernors, cpuWants)?.let {
                CpuTweaks.setGovernor(policy.policyId, it)
            }
            val freqs = policy.availableFrequencies
            if (freqs.isNotEmpty()) {
                val (min, max) = clampRange(freqs, profile)
                CpuTweaks.setMinFreq(policy.policyId, min.toInt())
                CpuTweaks.setMaxFreq(policy.policyId, max.toInt())
            }
        }

        for (node in gpuNodes) {
            pickGovernor(node.availableGovernors, gpuWants)?.let {
                GpuTweaks.setGovernor(node.nodePath, it)
            }
            val freqs = node.availableFrequencies
            if (freqs.isNotEmpty()) {
                val (min, max) = clampRange(freqs, profile)
                GpuTweaks.setMinFreq(node.nodePath, min)
                GpuTweaks.setMaxFreq(node.nodePath, max)
            }
        }
    }

    /** Returns (min, max) picked from the sorted available list for a given profile. */
    private fun clampRange(sortedFreqs: List<Long>, profile: Profile): Pair<Long, Long> = when (profile) {
        Profile.BATTERY_SAVER -> sortedFreqs.first() to sortedFreqs[sortedFreqs.size / 2]
        Profile.BALANCED -> sortedFreqs.first() to sortedFreqs.last()
        Profile.PERFORMANCE -> sortedFreqs[sortedFreqs.size * 3 / 4] to sortedFreqs.last()
    }
}
