package com.koneko.kerneltweak.tweaks

import com.koneko.kerneltweak.root.RootShell

data class FreqNode(
    val dir: String,                 // parent directory, e.g. /sys/devices/system/cpu/cpufreq/policy0
    val availableFreqPath: String,   // full path to the *available_frequencies file
    val frequencies: List<Long>,     // parsed, sorted ascending
    val targets: List<String>        // sibling *max_freq / *min_freq files found in the same dir
)

data class ApplyResult(
    val path: String,
    val value: String,
    val success: Boolean
)

/**
 * Universal frequency discovery + "set everything to max" action, matching:
 *
 *   for f in $(find /sys -name "*available_frequencies"); do
 *     d=${f%/*}; m=$(tr ' ' '\n' <"$f" | sort -n | tail -1)
 *     for t in "$d"/*max_freq "$d"/*min_freq; do
 *       [ -f "$t" ] && chmod 644 "$t" && echo "$m" > "$t"
 *     done
 *   done
 *
 * This walks the whole /sys tree rather than a fixed CPU/GPU path list, so
 * it also catches devfreq buses, bus/DDR scaling nodes, etc. — anything
 * that exposes the standard *available_frequencies / *max_freq / *min_freq
 * trio, on any chipset.
 */
object SystemWideTweaks {

    /** Discover every available_frequencies node under /sys (root required for most). */
    fun scan(): List<FreqNode> {
        val freqFiles = RootShell.cmd("find /sys -name '*available_frequencies' 2>/dev/null")
        return freqFiles.mapNotNull { path ->
            val dir = path.substringBeforeLast('/')
            val freqs = RootShell.readList(path).mapNotNull { it.toLongOrNull() }.sorted()
            if (freqs.isEmpty()) return@mapNotNull null

            val targets = RootShell.cmd("ls '$dir' 2>/dev/null")
                .filter { it.endsWith("max_freq") || it.endsWith("min_freq") }
                .map { "$dir/$it" }

            FreqNode(dir, path, freqs, targets)
        }
    }

    /**
     * Sets every discovered node's max/min target files to that node's
     * highest available frequency. chmod 644 first, same as the script,
     * since some of these nodes are read-only by default.
     */
    fun applyMaxToAll(nodes: List<FreqNode>): List<ApplyResult> {
        val results = mutableListOf<ApplyResult>()
        for (node in nodes) {
            val max = node.frequencies.lastOrNull()?.toString() ?: continue
            for (target in node.targets) {
                RootShell.cmd("chmod 644 '$target' 2>/dev/null")
                val ok = RootShell.write(target, max)
                results.add(ApplyResult(target, max, ok))
            }
        }
        return results
    }
}
