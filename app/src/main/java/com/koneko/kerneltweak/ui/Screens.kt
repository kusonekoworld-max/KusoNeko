package com.koneko.kerneltweak.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koneko.kerneltweak.root.RootShell
import com.koneko.kerneltweak.tweaks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Dest(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Home", Icons.Filled.Dashboard),
    CPU("CPU", Icons.Filled.Speed),
    GPU("GPU", Icons.Filled.Memory),
    THERMAL("Thermal", Icons.Filled.Thermostat),
    SCAN("Scan", Icons.Filled.Bolt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelTweakApp(rootGranted: Boolean) {
    var dest by remember { mutableStateOf(Dest.DASHBOARD) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("KernelTweak", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (rootGranted) "root ready" else "root not granted",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (rootGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = dest == d,
                        onClick = { dest = d },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = dest, animationSpec = tween(220), label = "tab") { current ->
                when (current) {
                    Dest.DASHBOARD -> DashboardTab(rootGranted)
                    Dest.CPU -> CpuTab(rootGranted)
                    Dest.GPU -> GpuTab(rootGranted)
                    Dest.THERMAL -> ThermalTab(rootGranted)
                    Dest.SCAN -> ScanTab(rootGranted)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

/** Quick-glance summary + one-tap profile presets. */
@Composable
fun DashboardTab(rootGranted: Boolean) {
    var policies by remember { mutableStateOf<List<CpuPolicy>>(emptyList()) }
    var gpuNodes by remember { mutableStateOf<List<GpuState>>(emptyList()) }
    var zones by remember { mutableStateOf<List<ThermalZone>>(emptyList()) }
    var applying by remember { mutableStateOf<ProfileTweaks.Profile?>(null) }
    var lastApplied by remember { mutableStateOf<ProfileTweaks.Profile?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        zones = withContext(Dispatchers.IO) { ThermalTweaks.listZones() }
        if (rootGranted) {
            policies = withContext(Dispatchers.IO) { CpuTweaks.listPolicies() }
            gpuNodes = withContext(Dispatchers.IO) { GpuTweaks.scan() }
        }
    }

    LaunchedEffect(rootGranted) { refresh() }

    val hottest = zones.mapNotNull { it.tempMilliC }.maxOrNull()?.let { it / 1000.0 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "CPU clusters",
                    value = policies.size.toString(),
                    subtitle = policies.joinToString(" · ") { it.currentGovernor ?: "?" }
                        .ifEmpty { "no data" }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "GPU nodes",
                    value = gpuNodes.size.toString(),
                    subtitle = gpuNodes.firstOrNull()?.currentGovernor ?: "no data"
                )
            }
        }
        item {
            StatCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Hottest zone",
                value = hottest?.let { "%.1f°C".format(it) } ?: "n/a",
                subtitle = "${zones.size} thermal zone(s) tracked"
            )
        }

        item { SectionLabel("Profiles") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileButton(
                    icon = Icons.Filled.BatteryStd,
                    profile = ProfileTweaks.Profile.BATTERY_SAVER,
                    description = "Lower clocks, conservative governor — longer screen-on time",
                    enabled = rootGranted && !applying.let { it != null },
                    applying = applying == ProfileTweaks.Profile.BATTERY_SAVER,
                    applied = lastApplied == ProfileTweaks.Profile.BATTERY_SAVER
                ) {
                    applying = ProfileTweaks.Profile.BATTERY_SAVER
                    scope.launch(Dispatchers.IO) {
                        ProfileTweaks.apply(ProfileTweaks.Profile.BATTERY_SAVER, policies, gpuNodes)
                        refresh()
                        applying = null
                        lastApplied = ProfileTweaks.Profile.BATTERY_SAVER
                    }
                }
                ProfileButton(
                    icon = Icons.Filled.Tune,
                    profile = ProfileTweaks.Profile.BALANCED,
                    description = "Full range, adaptive governor — default daily driving",
                    enabled = rootGranted && applying == null,
                    applying = applying == ProfileTweaks.Profile.BALANCED,
                    applied = lastApplied == ProfileTweaks.Profile.BALANCED
                ) {
                    applying = ProfileTweaks.Profile.BALANCED
                    scope.launch(Dispatchers.IO) {
                        ProfileTweaks.apply(ProfileTweaks.Profile.BALANCED, policies, gpuNodes)
                        refresh()
                        applying = null
                        lastApplied = ProfileTweaks.Profile.BALANCED
                    }
                }
                ProfileButton(
                    icon = Icons.Filled.RocketLaunch,
                    profile = ProfileTweaks.Profile.PERFORMANCE,
                    description = "Top-quartile clocks locked in — gaming / benchmarking",
                    enabled = rootGranted && applying == null,
                    applying = applying == ProfileTweaks.Profile.PERFORMANCE,
                    applied = lastApplied == ProfileTweaks.Profile.PERFORMANCE
                ) {
                    applying = ProfileTweaks.Profile.PERFORMANCE
                    scope.launch(Dispatchers.IO) {
                        ProfileTweaks.apply(ProfileTweaks.Profile.PERFORMANCE, policies, gpuNodes)
                        refresh()
                        applying = null
                        lastApplied = ProfileTweaks.Profile.PERFORMANCE
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp), fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    profile: ProfileTweaks.Profile,
    description: String,
    enabled: Boolean,
    applying: Boolean,
    applied: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                applying -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                applied -> Icon(Icons.Filled.CheckCircle, contentDescription = "applied", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun CpuTab(rootGranted: Boolean) {
    var policies by remember { mutableStateOf<List<CpuPolicy>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() { policies = withContext(Dispatchers.IO) { CpuTweaks.listPolicies() } }
    LaunchedEffect(rootGranted) { if (rootGranted) reload() }

    LoadingOrEmptyOrContent(
        loading = rootGranted && policies == null,
        empty = policies?.isEmpty() == true,
        emptyMessage = "No cpufreq policies found."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(policies.orEmpty()) { policy ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(policy.policyId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "cpus ${policy.cpus.joinToString(",")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${policy.currentFreq?.div(1000) ?: "?"} MHz now",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (policy.availableGovernors.isNotEmpty()) {
                            SectionLabel("Governor")
                            FlowChips(
                                options = policy.availableGovernors,
                                selected = policy.currentGovernor,
                                onSelect = { gov ->
                                    scope.launch(Dispatchers.IO) {
                                        CpuTweaks.setGovernor(policy.policyId, gov)
                                        reload()
                                    }
                                }
                            )
                        } else {
                            ErrorHint("No scaling_available_governors exposed for this policy.")
                        }

                        if (policy.availableFrequencies.isNotEmpty()) {
                            FreqEditor(
                                label = "Frequency (MHz)",
                                values = policy.availableFrequencies.map { (it / 1000).toLong() },
                                current = policy.currentFreq?.let { (it / 1000).toLong() },
                                minValue = policy.minFreq?.let { (it / 1000).toLong() },
                                maxValue = policy.maxFreq?.let { (it / 1000).toLong() },
                                onSetMin = { v ->
                                    scope.launch(Dispatchers.IO) {
                                        CpuTweaks.setMinFreq(policy.policyId, (v * 1000).toInt())
                                        reload()
                                    }
                                },
                                onSetMax = { v ->
                                    scope.launch(Dispatchers.IO) {
                                        CpuTweaks.setMaxFreq(policy.policyId, (v * 1000).toInt())
                                        reload()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpuTab(rootGranted: Boolean) {
    var nodes by remember { mutableStateOf<List<GpuState>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() { nodes = withContext(Dispatchers.IO) { GpuTweaks.scan() } }
    LaunchedEffect(rootGranted) { if (rootGranted) reload() }

    LoadingOrEmptyOrContent(
        loading = rootGranted && nodes == null,
        empty = nodes?.isEmpty() == true,
        emptyMessage = "No GPU devfreq node found on this device."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(nodes.orEmpty()) { node ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(node.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            node.nodePath,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${node.currentFreq?.div(1_000_000) ?: "?"} MHz now",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (node.availableGovernors.isNotEmpty()) {
                            SectionLabel("Governor")
                            FlowChips(
                                options = node.availableGovernors,
                                selected = node.currentGovernor,
                                onSelect = { gov ->
                                    scope.launch(Dispatchers.IO) {
                                        GpuTweaks.setGovernor(node.nodePath, gov)
                                        reload()
                                    }
                                }
                            )
                        } else {
                            ErrorHint("No available_governors exposed on this node — governor switching isn't supported here.")
                        }

                        if (node.availableFrequencies.isNotEmpty()) {
                            FreqEditor(
                                label = "Frequency (MHz)",
                                values = node.availableFrequencies.map { it / 1_000_000 },
                                current = node.currentFreq?.div(1_000_000),
                                minValue = node.minFreq?.div(1_000_000),
                                maxValue = node.maxFreq?.div(1_000_000),
                                onSetMin = { v ->
                                    scope.launch(Dispatchers.IO) {
                                        GpuTweaks.setMinFreq(node.nodePath, v * 1_000_000)
                                        reload()
                                    }
                                },
                                onSetMax = { v ->
                                    scope.launch(Dispatchers.IO) {
                                        GpuTweaks.setMaxFreq(node.nodePath, v * 1_000_000)
                                        reload()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalTab(rootGranted: Boolean) {
    var zones by remember { mutableStateOf<List<ThermalZone>?>(null) }
    var toggles by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rootGranted) {
        zones = withContext(Dispatchers.IO) { ThermalTweaks.listZones() }
        if (rootGranted) toggles = withContext(Dispatchers.IO) { ThermalTweaks.availableToggles() }
    }

    LoadingOrEmptyOrContent(
        loading = zones == null,
        empty = zones?.isEmpty() == true,
        emptyMessage = "No thermal zones found."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (rootGranted && toggles.isNotEmpty()) {
                item { SectionLabel("Throttle toggles") }
                items(toggles) { path ->
                    var checked by remember { mutableStateOf(true) }
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent = { Text(path, style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { value ->
                                        checked = value
                                        scope.launch(Dispatchers.IO) { ThermalTweaks.setToggle(path, value) }
                                    }
                                )
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { SectionLabel("Zones") }
            items(zones.orEmpty()) { zone ->
                val tempC = zone.tempMilliC?.let { it / 1000.0 }
                val hot = (tempC ?: 0.0) >= 45.0
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.medium) {
                    ListItem(
                        headlineContent = { Text(zone.type ?: zone.zoneId) },
                        supportingContent = { Text(zone.zoneId) },
                        trailingContent = {
                            Text(
                                tempC?.let { "%.1f°C".format(it) } ?: "n/a",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hot) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Full-system frequency scan: walks /sys for every *available_frequencies
 * node and lets you push every discovered node to its max in one tap, or
 * tap an individual frequency chip to lock just that node to that value.
 */
@Composable
fun ScanTab(rootGranted: Boolean) {
    var nodes by remember { mutableStateOf<List<FreqNode>?>(null) }
    var results by remember { mutableStateOf<List<ApplyResult>>(emptyList()) }
    var applying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rootGranted) {
        if (rootGranted) nodes = withContext(Dispatchers.IO) { SystemWideTweaks.scan() }
    }

    LoadingOrEmptyOrContent(
        loading = rootGranted && nodes == null,
        empty = nodes?.isEmpty() == true,
        emptyMessage = "No *available_frequencies nodes found under /sys."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${nodes?.size ?: 0} frequency node(s) found under /sys",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(
                    onClick = {
                        val current = nodes ?: return@Button
                        applying = true
                        scope.launch(Dispatchers.IO) {
                            val r = SystemWideTweaks.applyMaxToAll(current)
                            results = r
                            applying = false
                        }
                    },
                    enabled = !applying && !nodes.isNullOrEmpty(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (applying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (applying) "Applying…" else "Set every node to max")
                }
            }

            if (results.isNotEmpty()) {
                item { SectionLabel("Last apply result") }
                items(results) { r ->
                    Text(
                        "${if (r.success) "✓" else "✗"} ${r.path} → ${r.value}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (r.success) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item { SectionLabel("Discovered nodes — tap a value to lock min+max there") }
            items(nodes.orEmpty()) { node ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            node.dir,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "min ${node.frequencies.first()} · max ${node.frequencies.last()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (node.targets.isEmpty()) {
                            ErrorHint("no max_freq/min_freq sibling here — read-only")
                        }
                        FlowRowFreqLock(node)
                    }
                }
            }
        }
    }
}

/** Tapping a value writes it to every max_freq/min_freq sibling for this node. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowFreqLock(node: FreqNode) {
    val scope = rememberCoroutineScope()
    var locked by remember(node.dir) { mutableStateOf<Long?>(null) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        node.frequencies.forEach { v ->
            FilterChip(
                selected = v == locked,
                onClick = {
                    if (node.targets.isEmpty()) return@FilterChip
                    locked = v
                    scope.launch(Dispatchers.IO) {
                        node.targets.forEach { target ->
                            RootShell.cmd("chmod 644 '$target' 2>/dev/null")
                            RootShell.write(target, v.toString())
                        }
                    }
                },
                enabled = node.targets.isNotEmpty(),
                label = { Text(v.toString()) }
            )
        }
    }
}

/** Shared loading / empty-state / content switcher so tabs don't repeat it. */
@Composable
private fun LoadingOrEmptyOrContent(
    loading: Boolean,
    empty: Boolean,
    emptyMessage: String,
    content: @Composable () -> Unit
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        empty -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> content()
    }
}

@Composable
private fun ErrorHint(text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** Wrapping row of FilterChips where the currently-active option is visually selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

/**
 * Editable min/max frequency row: a Min/Max segmented toggle picks which
 * bound you're setting, then tapping any value in the wrapping chip row
 * writes it immediately. The value matching the live current frequency is
 * marked with a dot so it's easy to tell what's actually running right now
 * versus what the min/max clamp is set to.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FreqEditor(
    label: String,
    values: List<Long>,
    current: Long?,
    minValue: Long?,
    maxValue: Long?,
    onSetMin: (Long) -> Unit,
    onSetMax: (Long) -> Unit
) {
    var editingMax by remember { mutableStateOf(true) }

    SectionLabel(label)
    SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 8.dp)) {
        SegmentedButton(
            selected = editingMax,
            onClick = { editingMax = true },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Set Max") }
        SegmentedButton(
            selected = !editingMax,
            onClick = { editingMax = false },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Set Min") }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEach { v ->
            val isBoundSelected = if (editingMax) v == maxValue else v == minValue
            FilterChip(
                selected = isBoundSelected,
                onClick = { if (editingMax) onSetMax(v) else onSetMin(v) },
                label = { Text(if (v == current) "$v •" else "$v") }
            )
        }
    }
}
