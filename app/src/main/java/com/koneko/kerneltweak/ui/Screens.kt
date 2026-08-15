package com.koneko.kerneltweak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koneko.kerneltweak.tweaks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelTweakApp(rootGranted: Boolean) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        Triple("CPU", Icons.Filled.Speed, 0),
        Triple("GPU", Icons.Filled.Memory, 1),
        Triple("Thermal", Icons.Filled.Thermostat, 2),
        Triple("Scan", Icons.Filled.Bolt, 3)
    )
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { Text("KernelTweak") },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
                if (!rootGranted) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Root access not granted — tweaks are disabled.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                SecondaryTabRow(selectedTabIndex = tab) {
                    tabs.forEach { (label, icon, index) ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(label) },
                            icon = { Icon(icon, contentDescription = label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> CpuTab(rootGranted)
                1 -> GpuTab(rootGranted)
                2 -> ThermalTab(rootGranted)
                3 -> ScanTab(rootGranted)
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
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
}

@Composable
fun CpuTab(rootGranted: Boolean) {
    var policies by remember { mutableStateOf<List<CpuPolicy>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rootGranted) {
        if (rootGranted) policies = withContext(Dispatchers.IO) { CpuTweaks.listPolicies() }
    }

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
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(policy.policyId, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "cpus ${policy.cpus.joinToString(",")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${policy.currentFreq?.div(1000) ?: "?"} MHz now  ·  ${(policy.minFreq ?: 0) / 1000} – ${(policy.maxFreq ?: 0) / 1000} MHz range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (policy.availableGovernors.isNotEmpty()) {
                            SectionLabel("Governor")
                            FlowChips(
                                options = policy.availableGovernors,
                                selected = policy.currentGovernor,
                                onSelect = { gov ->
                                    scope.launch(Dispatchers.IO) {
                                        CpuTweaks.setGovernor(policy.policyId, gov)
                                        policies = CpuTweaks.listPolicies()
                                    }
                                }
                            )
                        } else {
                            Text(
                                "No scaling_available_governors exposed for this policy.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (policy.availableFrequencies.isNotEmpty()) {
                            SectionLabel("Available frequencies (MHz)")
                            FlowFreqChips(
                                values = policy.availableFrequencies.map { it / 1000 },
                                highlight = policy.currentFreq?.div(1000)
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

    LaunchedEffect(rootGranted) {
        if (rootGranted) nodes = withContext(Dispatchers.IO) { GpuTweaks.scan() }
    }

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
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(node.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            node.nodePath,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${node.currentFreq?.div(1_000_000) ?: "?"} MHz now  ·  ${(node.minFreq ?: 0) / 1_000_000} – ${(node.maxFreq ?: 0) / 1_000_000} MHz range",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (node.availableGovernors.isNotEmpty()) {
                            SectionLabel("Governor")
                            FlowChips(
                                options = node.availableGovernors,
                                selected = node.currentGovernor,
                                onSelect = { gov ->
                                    scope.launch(Dispatchers.IO) {
                                        GpuTweaks.setGovernor(node.nodePath, gov)
                                        nodes = GpuTweaks.scan()
                                    }
                                }
                            )
                        } else {
                            Text(
                                "No available_governors exposed on this node — governor switching isn't supported here.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (node.availableFrequencies.isNotEmpty()) {
                            SectionLabel("Available frequencies (MHz)")
                            FlowFreqChips(
                                values = node.availableFrequencies.map { it / 1_000_000 },
                                highlight = node.currentFreq?.div(1_000_000)
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
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(zone.type ?: zone.zoneId) },
                        supportingContent = { Text(zone.zoneId) },
                        trailingContent = {
                            Text(
                                tempC?.let { "%.1f°C".format(it) } ?: "n/a",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
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
 * node (cpu, gpu, devfreq buses, anything else exposing the same trio) and
 * lets you push every discovered node to its max in one tap — same effect
 * as the shell one-liner, done natively with root already held.
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
                    modifier = Modifier.fillMaxWidth()
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

            item { SectionLabel("Discovered nodes") }
            items(nodes.orEmpty()) { node ->
                ElevatedCard(Modifier.fillMaxWidth()) {
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
                            Text(
                                "no max_freq/min_freq sibling here — read-only",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        FlowFreqChips(node.frequencies)
                    }
                }
            }
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

/** Wrapping row of read-only chips listing raw frequency values; `highlight`
 *  (if it matches a value) is drawn as a selected FilterChip so the current
 *  operating point stands out among the full table. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowFreqChips(values: List<Long>, highlight: Long? = null) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEach { v ->
            if (v == highlight) {
                FilterChip(selected = true, onClick = {}, label = { Text(v.toString()) })
            } else {
                AssistChip(onClick = {}, label = { Text(v.toString()) })
            }
        }
    }
}
