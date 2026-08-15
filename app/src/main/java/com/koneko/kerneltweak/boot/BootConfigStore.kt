package com.koneko.kerneltweak.boot

import android.content.Context
import com.koneko.kerneltweak.tweaks.CpuPolicy
import com.koneko.kerneltweak.tweaks.CpuTweaks
import com.koneko.kerneltweak.tweaks.GpuState
import com.koneko.kerneltweak.tweaks.GpuTweaks
import com.koneko.kerneltweak.tweaks.ThermalTweaks
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshots the live governor + min/max freq for every CPU policy and GPU
 * node, plus which thermal toggles are on, into SharedPreferences as
 * plain JSON (org.json, no extra dependency needed). BootReceiver reads
 * this back and replays it once root is available after boot.
 */
object BootConfigStore {
    private const val PREFS = "kerneltweak_boot"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SNAPSHOT = "snapshot_json"
    private const val KEY_SAVED_AT = "saved_at"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun savedAt(context: Context): Long = prefs(context).getLong(KEY_SAVED_AT, 0L)

    fun disable(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun saveSnapshot(
        context: Context,
        cpuPolicies: List<CpuPolicy>,
        gpuNodes: List<GpuState>,
        thermalToggleStates: Map<String, Boolean>
    ) {
        val root = JSONObject()

        val cpuArr = JSONArray()
        cpuPolicies.forEach { p ->
            val o = JSONObject()
            o.put("policyId", p.policyId)
            p.currentGovernor?.let { o.put("governor", it) }
            p.minFreq?.let { o.put("minFreq", it) }
            p.maxFreq?.let { o.put("maxFreq", it) }
            cpuArr.put(o)
        }
        root.put("cpu", cpuArr)

        val gpuArr = JSONArray()
        gpuNodes.forEach { n ->
            val o = JSONObject()
            o.put("nodePath", n.nodePath)
            n.currentGovernor?.let { o.put("governor", it) }
            n.minFreq?.let { o.put("minFreq", it) }
            n.maxFreq?.let { o.put("maxFreq", it) }
            gpuArr.put(o)
        }
        root.put("gpu", gpuArr)

        val thermalObj = JSONObject()
        thermalToggleStates.forEach { (path, enabled) -> thermalObj.put(path, enabled) }
        root.put("thermalToggles", thermalObj)

        prefs(context).edit()
            .putString(KEY_SNAPSHOT, root.toString())
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Re-applies the saved snapshot. Call only once root is confirmed available. */
    fun applySnapshot(context: Context) {
        val json = prefs(context).getString(KEY_SNAPSHOT, null) ?: return
        val root = JSONObject(json)

        root.optJSONArray("cpu")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val policyId = o.getString("policyId")
                if (o.has("governor")) CpuTweaks.setGovernor(policyId, o.getString("governor"))
                if (o.has("minFreq")) CpuTweaks.setMinFreq(policyId, o.getInt("minFreq"))
                if (o.has("maxFreq")) CpuTweaks.setMaxFreq(policyId, o.getInt("maxFreq"))
            }
        }

        root.optJSONArray("gpu")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val nodePath = o.getString("nodePath")
                if (o.has("governor")) GpuTweaks.setGovernor(nodePath, o.getString("governor"))
                if (o.has("minFreq")) GpuTweaks.setMinFreq(nodePath, o.getLong("minFreq"))
                if (o.has("maxFreq")) GpuTweaks.setMaxFreq(nodePath, o.getLong("maxFreq"))
            }
        }

        root.optJSONObject("thermalToggles")?.let { obj ->
            obj.keys().forEach { path -> ThermalTweaks.setToggle(path, obj.getBoolean(path)) }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
