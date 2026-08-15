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

data class ThermalTrip(
    val zoneId: String,
    val index: Int,
    val type: String?,      // e.g. "passive", "critical", "hot"
    val tempMilliC: Int?
)

/**
 * Zones and cooling devices both come from the generic Linux thermal
 * framework (/sys/class/thermal/thermal_zoneN, /cooling_deviceN), so
 * enumeration here is chipset-agnostic and needs no vendor branches.
 * Trip points (trip_point_N_temp) are the actual temperature thresholds
 * that decide when throttling kicks in for a zone — writable on most
 * mainline-derived thermal drivers, which is what lets this app actually
 * change thermal behavior rather than just observe it.
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

    /** Set a cooling device's throttle level directly (0 = no throttle, up to maxState). */
    fun setCoolingState(deviceId: String, state: Int): Boolean =
        RootShell.write("$THERMAL_ROOT/$deviceId/cur_state", state.toString())

    /**
     * Trip points are fetched lazily per-zone (only when the user expands
     * a zone in the UI) rather than for every zone up front — a device can
     * have 20-30 zones and scanning all of their trip points on load would
     * be a lot of unnecessary root round-trips for data most zones never
     * show.
     */
    fun listTripPoints(zoneId: String): List<ThermalTrip> {
        val base = "$THERMAL_ROOT/$zoneId"
        val names = RootShell.cmd("ls $base 2>/dev/null")
            .filter { it.matches(Regex("trip_point_\\d+_temp")) }

        return names.mapNotNull { name ->
            val idx = Regex("trip_point_(\\d+)_temp").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            ThermalTrip(
                zoneId = zoneId,
                index = idx,
                type = RootShell.read("$base/trip_point_${idx}_type"),
                tempMilliC = RootShell.read("$base/trip_point_${idx}_temp")?.trim()?.toIntOrNull()
            )
        }.sortedBy { it.index }
    }

    /** Adjust the temperature threshold for a given trip point — this is the actual "how hot before it throttles" knob. */
    fun setTripTemp(zoneId: String, index: Int, milliC: Int): Boolean =
        RootShell.write("$THERMAL_ROOT/$zoneId/trip_point_${index}_temp", milliC.toString())

    /** Returns only the toggle paths that actually exist on this device. */
    fun availableToggles(): List<String> = KNOWN_TOGGLES.filter { RootShell.exists(it) }

    /** Current on/off state of a toggle, if readable. */
    fun currentToggleState(path: String): Boolean? =
        RootShell.read(path)?.trim()?.let { it == "1" || it.equals("true", ignoreCase = true) }

    fun setToggle(path: String, enabled: Boolean): Boolean {
        if (path !in KNOWN_TOGGLES || !RootShell.exists(path)) return false
        return RootShell.write(path, if (enabled) "1" else "0")
    }
}
