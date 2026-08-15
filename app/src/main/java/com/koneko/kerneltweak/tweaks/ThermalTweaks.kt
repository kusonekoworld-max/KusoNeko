package com.koneko.kerneltweak.tweaks

import com.koneko.kerneltweak.root.RootShell

data class ThermalZone(
    val zoneId: String,     // e.g. "thermal_zone0"
    val type: String?,      // e.g. "cpu-0-0-usr"
    val tempMilliC: Int?
)

data class CoolingDevice(
    val deviceId: String,   // e.g. "cooling_device0"
    val type: String?,      // e.g. "thermal-cpufreq-0"
    val curState: Int?,
    val maxState: Int?
)

/**
 * Zones and cooling devices both come from the generic Linux thermal
 * framework (/sys/class/thermal/thermal_zoneN, /cooling_deviceN), so
 * enumeration here is chipset-agnostic and needs no vendor branches.
 *
 * Throttle-disable knobs, by contrast, are genuinely vendor-specific
 * module params with no common path — kept as a checked candidate list
 * so only the ones actually present on the device are ever surfaced.
 */
object ThermalTweaks {

    private const val THERMAL_ROOT = "/sys/class/thermal"

    private val KNOWN_TOGGLES = listOf(
        "/sys/module/msm_thermal/parameters/enabled",        // Qualcomm (older)
        "/sys/module/msm_thermal/core_control/enabled",      // Qualcomm (older)
        "/sys/module/mtk_thermal/parameters/enabled",        // MediaTek
        "/sys/class/thermal/thermal_message/sconfig",        // Samsung Exynos IPA
        "/sys/kernel/ipa_thermal/enabled"                    // generic IPA builds
    )

    fun listZones(): List<ThermalZone> {
        val zoneDirs = RootShell.cmd("ls $THERMAL_ROOT 2>/dev/null")
            .filter { it.startsWith("thermal_zone") }
            .sorted()

        return zoneDirs.map { zoneId ->
            val base = "$THERMAL_ROOT/$zoneId"
            ThermalZone(
                zoneId = zoneId,
                type = RootShell.read("$base/type"),
                tempMilliC = RootShell.read("$base/temp")?.trim()?.toIntOrNull()
            )
        }
    }

    fun listCoolingDevices(): List<CoolingDevice> {
        val dirs = RootShell.cmd("ls $THERMAL_ROOT 2>/dev/null")
            .filter { it.startsWith("cooling_device") }
            .sorted()

        return dirs.map { deviceId ->
            val base = "$THERMAL_ROOT/$deviceId"
            CoolingDevice(
                deviceId = deviceId,
                type = RootShell.read("$base/type"),
                curState = RootShell.read("$base/cur_state")?.trim()?.toIntOrNull(),
                maxState = RootShell.read("$base/max_state")?.trim()?.toIntOrNull()
            )
        }
    }

    /** Returns only the toggle paths that actually exist on this device. */
    fun availableToggles(): List<String> = KNOWN_TOGGLES.filter { RootShell.exists(it) }

    fun setToggle(path: String, enabled: Boolean): Boolean {
        if (path !in KNOWN_TOGGLES || !RootShell.exists(path)) return false
        return RootShell.write(path, if (enabled) "1" else "0")
    }
}
