package com.koneko.kerneltweak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
        Triple("Thermal", Icons.Filled.Thermostat, 2)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KernelTweak") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!rootGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Root access not granted — tweaks are disabled.",
                        modifier = Modifier.padding(12.dp),
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

            when (tab) {
                0 -> CpuTab(rootGranted)
                1 -> GpuTab(rootGranted)
                2 -> ThermalTab(rootGranted)
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
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            items(policies.orEmpty()) { policy ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
                            "${(policy.minFreq ?: 0) / 1000} – ${(policy.maxFreq ?: 0) / 1000} MHz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

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
                    }
                }
            }
        }
    }
}

@Composable
fun GpuTab(rootGranted: Boolean) {
    var state by remember { mutableStateOf<GpuState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rootGranted) {
        if (rootGranted) state = withContext(Dispatchers.IO) { GpuTweaks.getState() }
    }

    val s = state
    LoadingOrEmptyOrContent(
        loading = rootGranted && s == null,
        empty = s != null && !s.available,
        emptyMessage = "No GPU devfreq node found on this device."
    ) {
        if (s == null) return@LoadingOrEmptyOrContent
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.nodePath ?: "", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${(s.minFreq ?: 0) / 1_000_000} – ${(s.maxFreq ?: 0) / 1_000_000} MHz",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SectionLabel("Governor")
                    FlowChips(
                        options = s.availableGovernors,
                        selected = s.currentGovernor,
                        onSelect = { gov ->
                            scope.launch(Dispatchers.IO) {
                                GpuTweaks.setGovernor(gov)
                                state = GpuTweaks.getState()
                            }
                        }
                    )
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
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (rootGranted && toggles.isNotEmpty()) {
                item {
                    SectionLabel("Throttle toggles")
                }
                items(toggles) { path ->
                    var checked by remember { mutableStateOf(true) }
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
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }

            item { SectionLabel("Zones") }
            items(zones.orEmpty()) { zone ->
                val tempC = zone.tempMilliC?.let { it / 1000.0 }
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

/** Row of FilterChips where the currently-active option is visually selected. */
@Composable
private fun FlowChips(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.padding(top = 4.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}
