@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.koneko.kerneltweak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koneko.kerneltweak.boot.BootConfigStore
import com.koneko.kerneltweak.root.RootShell
import com.koneko.kerneltweak.tweaks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HISTORY_LEN = 40
private const val POLL_MS = 2000L

private enum class Dest(
    val label: String,
    val iconFilled: androidx.compose.ui.graphics.vector.ImageVector,
    val iconOutlined: androidx.compose.ui.graphics.vector.ImageVector
) {
    DASHBOARD("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CPU("CPU", Icons.Filled.Speed, Icons.Outlined.Speed),
    GPU("GPU", Icons.Filled.Memory, Icons.Outlined.Memory),
    THERMAL("Thermal", Icons.Filled.Thermostat, Icons.Outlined.Thermostat),
    INFO("Info", Icons.Filled.Info, Icons.Filled.Info), // reached via the top-left icon, not bottom nav
    SCAN("Scan", Icons.Filled.Bolt, Icons.Filled.Bolt)  // reached from the Dashboard card, not bottom nav
}

// Only these 4 sit in the bottom nav — Info and Scan are reached from the top bar / a dashboard card instead.
private val BOTTOM_NAV_DESTS = listOf(Dest.DASHBOARD, Dest.CPU, Dest.GPU, Dest.THERMAL)

/**
 * Root scaffold. Both bars use their Material3 default `windowInsets`
 * (do NOT override with WindowInsets(0)) so the top bar pads for the
 * status bar and the nav bar pads for the gesture/3-button bar instead
 * of content sliding underneath them. Pair this with
 * `enableEdgeToEdge()` in the hosting Activity's onCreate so the system
 * bars are transparent and these container colors are what's visible —
 * not an OS-picked default.
 *
 * Info now lives as a top-left icon button instead of a 5th bottom-nav
 * tab: it's a one-off reference screen, not something you switch to
 * repeatedly like CPU/GPU/Thermal, so it doesn't need permanent bottom
 * real estate.
 */
@Composable
fun KernelTweakApp(rootGranted: Boolean) {
    var dest by remember { mutableStateOf(Dest.DASHBOARD) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("KusoNekoTune", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (rootGranted) "root ready" else "root not granted",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (rootGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    when (dest) {
                        Dest.SCAN, Dest.INFO -> IconButton(onClick = { dest = Dest.DASHBOARD }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        else -> IconButton(onClick = { dest = Dest.INFO }) {
                            Icon(Icons.Filled.Info, contentDescription = "Kernel info")
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                BOTTOM_NAV_DESTS.forEach { d ->
                    val selected = dest == d
                    NavigationBarItem(
                        selected = selected,
                        onClick = { dest = d },
                        icon = {
                            Icon(
                                if (selected) d.iconFilled else d.iconOutlined,
                                contentDescription = d.label
                            )
                        },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = dest, animationSpec = tween(220), label = "tab") { current ->
                when (current) {
                    Dest.DASHBOARD -> DashboardTab(rootGranted, onOpenScan = { dest = Dest.SCAN })
                    Dest.CPU -> CpuTab(rootGranted)
                    Dest.GPU -> GpuTab(rootGranted)
                    Dest.THERMAL -> ThermalTab(rootGranted)
                    Dest.INFO -> InfoTab()
                    Dest.SCAN -> ScanTab(rootGranted)
                }
            }
        }
    }
}

/** Material3 Expressive wavy loader — drop-in swap for CircularProgressIndicator. */
@Composable
private fun WavyLoader(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    CircularProgressIndicator(modifier = modifier.size(size), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun WavyLoaderSmall(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
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

/**
 * Home tab, redesigned: a big status hero up top instead of the title
 * being the only place root state shows, a 3-across glance row (CPU /
 * GPU / hottest zone), then quick actions (scan, boot-apply) grouped
 * together, then profiles last since they're the "commit" action.
 */
@Composable
fun DashboardTab(rootGranted: Boolean, onOpenScan: () -> Unit) {
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroStatusCard(rootGranted, hottest) }

        item { SectionLabel("At a glance") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "CPU",
                    value = policies.size.toString(),
                    subtitle = policies.joinToString(" · ") { it.currentGovernor ?: "?" }.ifEmpty { "no data" }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Memory,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "GPU",
                    value = gpuNodes.size.toString(),
                    subtitle = gpuNodes.firstOrNull()?.currentGovernor ?: "no data"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Thermostat,
                    containerColor = tempContainerColor(hottest, MaterialTheme.colorScheme),
                    contentColor = tempOnContainerColor(hottest, MaterialTheme.colorScheme),
                    title = "Hottest",
                    value = hottest?.let { "%.0f°".format(it) } ?: "n/a",
                    subtitle = "${zones.size} zone(s)"
                )
            }
        }

        item { SectionLabel("Quick actions") }
        item {
            ElevatedCard(onClick = onOpenScan, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = Icons.Filled.Bolt,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Full system frequency scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Find and max every frequency node under /sys",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { BootConfigCard(rootGranted, policies, gpuNodes) }

        item { SectionLabel("Profiles") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileButton(
                    icon = Icons.Filled.BatteryStd,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    profile = ProfileTweaks.Profile.BATTERY_SAVER,
                    description = "Lower clocks, conservative governor — longer screen-on time",
                    enabled = rootGranted && applying == null,
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
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
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

/**
 * Big status banner at the top of Home — root state front and center
 * with a colored tonal background instead of a small caption under the
 * app-bar title, plus the hottest-zone reading so the very first thing
 * you see answers "is it safe to push clocks right now".
 */
@Composable
private fun HeroStatusCard(rootGranted: Boolean, hottest: Double?) {
    val containerColor = if (rootGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val onContainerColor = if (rootGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (rootGranted) "Root ready" else "Root not granted",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor
                )
                Text(
                    if (rootGranted) "Full tuning access is available."
                    else "Grant root to unlock CPU/GPU/thermal tuning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainerColor.copy(alpha = 0.85f)
                )
            }
            if (hottest != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f°C".format(hottest),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainerColor
                    )
                    Text("hottest zone", style = MaterialTheme.typography.labelMedium, color = onContainerColor.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** Circular tonal icon container — the "icon chip" look used throughout AOSP system apps. */
@Composable
private fun IconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 44.dp
) {
    Surface(shape = CircleShape, color = containerColor, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
private fun BootConfigCard(rootGranted: Boolean, policies: List<CpuPolicy>, gpuNodes: List<GpuState>) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BootConfigStore.isEnabled(context)) }
    var savedAt by remember { mutableStateOf(BootConfigStore.savedAt(context)) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = Icons.Filled.Update,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Apply on boot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        saving -> "Saving current settings…"
                        enabled && savedAt > 0 ->
                            "Saved " + SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(savedAt))
                        else -> "Snapshot current CPU/GPU/thermal settings, reapply every boot"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (saving) {
                WavyLoaderSmall()
            } else {
                Switch(
                    checked = enabled,
                    enabled = rootGranted,
                    onCheckedChange = { checked ->
                        if (checked) {
                            saving = true
                            scope.launch(Dispatchers.IO) {
                                val toggles = ThermalTweaks.availableToggles()
                                val toggleStates = toggles.associateWith { ThermalTweaks.currentToggleState(it) ?: true }
                                BootConfigStore.saveSnapshot(context, policies, gpuNodes, toggleStates)
                                enabled = true
                                savedAt = BootConfigStore.savedAt(context)
                                saving = false
                            }
                        } else {
                            BootConfigStore.disable(context)
                            enabled = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    title: String,
    value: String,
    subtitle: String
) {
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(icon, containerColor, contentColor, size = 32.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
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
            IconBadge(icon, containerColor, contentColor)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                applying -> WavyLoaderSmall()
                applied -> Icon(Icons.Filled.CheckCircle, contentDescription = "applied", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun CpuTab(rootGranted: Boolean) {
    var policies by remember { mutableStateOf<List<CpuPolicy>?>(null) }
    val history = remember { mutableStateMapOf<String, List<Float>>() }
    val scope = rememberCoroutineScope()

    suspend fun poll() {
        val fresh = withContext(Dispatchers.IO) { CpuTweaks.listPolicies() }
        policies = fresh
        fresh.forEach { p ->
            val mhz = p.currentFreq?.div(1000)?.toFloat() ?: return@forEach
            val prev = history[p.policyId].orEmpty()
            history[p.policyId] = (prev + mhz).takeLast(HISTORY_LEN)
        }
    }

    LaunchedEffect(rootGranted) {
        if (!rootGranted) return@LaunchedEffect
        poll()
        while (isActive) {
            delay(POLL_MS)
            poll()
        }
    }

    LoadingOrEmptyOrContent(
        loading = rootGranted && policies == null,
        empty = policies?.isEmpty() == true,
        emptyMessage = "No cpufreq policies found."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(policies.orEmpty(), key = { it.policyId }) { policy ->
                TweakCard(
                    modifier = Modifier.animateItem(),
                    title = policy.policyId,
                    subtitle = "cpus ${policy.cpus.joinToString(",")}",
                    valueText = "${policy.currentFreq?.div(1000) ?: "?"} MHz",
                    statusText = policy.currentGovernor ?: "no governor",
                    graphHistory = history[policy.policyId].orEmpty()
                ) {
                    if (policy.availableGovernors.isNotEmpty()) {
                        SectionLabel("Governor")
                        FlowChips(
                            options = policy.availableGovernors,
                            selected = policy.currentGovernor,
                            onSelect = { gov ->
                                scope.launch(Dispatchers.IO) {
                                    CpuTweaks.setGovernor(policy.policyId, gov)
                                    poll()
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
                                    poll()
                                }
                            },
                            onSetMax = { v ->
                                scope.launch(Dispatchers.IO) {
                                    CpuTweaks.setMaxFreq(policy.policyId, (v * 1000).toInt())
                                    poll()
                                }
                            }
                        )
                    } else {
                        ErrorHint("No scaling_available_frequencies exposed — min/max cannot be set here.")
                    }
                }
            }
        }
    }
}

@Composable
fun GpuTab(rootGranted: Boolean) {
    var nodes by remember { mutableStateOf<List<GpuState>?>(null) }
    val history = remember { mutableStateMapOf<String, List<Float>>() }
    val scope = rememberCoroutineScope()

    suspend fun poll() {
        val fresh = withContext(Dispatchers.IO) { GpuTweaks.scan() }
        nodes = fresh
        fresh.forEach { n ->
            val mhz = n.currentFreq?.div(1_000_000)?.toFloat() ?: return@forEach
            val prev = history[n.nodePath].orEmpty()
            history[n.nodePath] = (prev + mhz).takeLast(HISTORY_LEN)
        }
    }

    LaunchedEffect(rootGranted) {
        if (!rootGranted) return@LaunchedEffect
        poll()
        while (isActive) {
            delay(POLL_MS)
            poll()
        }
    }

    LoadingOrEmptyOrContent(
        loading = rootGranted && nodes == null,
        empty = nodes?.isEmpty() == true,
        emptyMessage = "No GPU devfreq node found on this device."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(nodes.orEmpty(), key = { it.nodePath }) { node ->
                TweakCard(
                    modifier = Modifier.animateItem(),
                    title = node.label,
                    subtitle = node.nodePath,
                    valueText = "${node.currentFreq?.div(1_000_000) ?: "?"} MHz",
                    statusText = node.currentGovernor ?: "no governor",
                    graphHistory = history[node.nodePath].orEmpty()
                ) {
                    if (node.availableGovernors.isNotEmpty()) {
                        SectionLabel("Governor")
                        FlowChips(
                            options = node.availableGovernors,
                            selected = node.currentGovernor,
                            onSelect = { gov ->
                                scope.launch(Dispatchers.IO) {
                                    GpuTweaks.setGovernor(node.nodePath, gov)
                                    poll()
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
                                    poll()
                                }
                            },
                            onSetMax = { v ->
                                scope.launch(Dispatchers.IO) {
                                    GpuTweaks.setMaxFreq(node.nodePath, v * 1_000_000)
                                    poll()
                                }
                            }
                        )
                    } else {
                        ErrorHint("No available_frequencies exposed — min/max cannot be set here.")
                    }
                }
            }
        }
    }
}

/**
 * Sparkline of recent MHz samples — pure Canvas, no chart lib. Draws a
 * smoothed line with a gradient fill under it, AOSP "Running services /
 * CPU usage" style. Renders nothing but reserves height until there are
 * ≥2 samples so cards don't jump in size once polling kicks in.
 */
@Composable
private fun MiniFreqGraph(
    history: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val fillColor = lineColor.copy(alpha = 0.20f)
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        if (history.size < 2) return@Canvas
        val maxV = history.max()
        val minV = history.min()
        val range = (maxV - minV).let { if (it < 1f) 1f else it }
        val stepX = size.width / (history.size - 1)
        val linePath = Path()
        val fillPath = Path()
        history.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minV) / range) * size.height * 0.85f - size.height * 0.05f
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
        drawPath(
            linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Shared collapsed-by-default card for CPU policies and GPU nodes: header
 * always shows the name, the live value, current governor, and a small
 * live MHz sparkline — enough to read the state of the device at a
 * glance without expanding. Tapping it reveals the governor picker +
 * frequency editor.
 */
@Composable
private fun TweakCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    valueText: String,
    statusText: String,
    graphHistory: List<Float> = emptyList(),
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }

    ElevatedCard(modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        valueText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (graphHistory.size >= 2) {
                MiniFreqGraph(
                    graphHistory,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(140))
            ) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

/** Cool → warm → hot color scale shared by the dashboard hottest-zone card and thermal zone rows. */
private fun tempColor(c: Double?, scheme: ColorScheme): Color = when {
    c == null -> scheme.onSurfaceVariant
    c < 40.0 -> scheme.primary
    c < 55.0 -> Color(0xFFF29900)
    else -> scheme.error
}

private fun tempContainerColor(c: Double?, scheme: ColorScheme): Color = when {
    c == null -> scheme.surfaceContainerHigh
    c < 40.0 -> scheme.primaryContainer
    c < 55.0 -> Color(0xFFFFE7C2)
    else -> scheme.errorContainer
}

private fun tempOnContainerColor(c: Double?, scheme: ColorScheme): Color = when {
    c == null -> scheme.onSurfaceVariant
    c < 40.0 -> scheme.onPrimaryContainer
    c < 55.0 -> Color(0xFF7A4A00)
    else -> scheme.onErrorContainer
}

@Composable
fun ThermalTab(rootGranted: Boolean) {
    var zones by remember { mutableStateOf<List<ThermalZone>?>(null) }
    var toggles by remember { mutableStateOf<List<String>>(emptyList()) }
    var coolingDevices by remember { mutableStateOf<List<CoolingDevice>>(emptyList()) }

    LaunchedEffect(rootGranted) {
        zones = withContext(Dispatchers.IO) { ThermalTweaks.listZones() }
        if (rootGranted) {
            toggles = withContext(Dispatchers.IO) { ThermalTweaks.availableToggles() }
            coolingDevices = withContext(Dispatchers.IO) { ThermalTweaks.listCoolingDevices() }
        }
    }

    LoadingOrEmptyOrContent(
        loading = zones == null,
        empty = zones?.isEmpty() == true,
        emptyMessage = "No thermal zones found."
    ) {
        val hottest = zones.orEmpty().mapNotNull { it.tempMilliC }.maxOrNull()?.let { it / 1000.0 }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Thermostat,
                    containerColor = tempContainerColor(hottest, MaterialTheme.colorScheme),
                    contentColor = tempOnContainerColor(hottest, MaterialTheme.colorScheme),
                    title = "Hottest right now",
                    value = hottest?.let { "%.1f°C".format(it) } ?: "n/a",
                    subtitle = "${zones.orEmpty().size} zone(s) reporting"
                )
            }
            item { SectionLabel("Zones — tap a zone to adjust its trip points") }
            items(zones.orEmpty(), key = { it.zoneId }) { zone ->
                ZoneCard(zone, rootGranted, modifier = Modifier.animateItem())
            }

            if (rootGranted && coolingDevices.isNotEmpty()) {
                item {
                    ExpandableSection(title = "Cooling devices", count = coolingDevices.size) {
                        coolingDevices.forEachIndexed { i, device ->
                            CoolingDeviceRow(device)
                            if (i != coolingDevices.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            if (rootGranted && toggles.isNotEmpty()) {
                item {
                    ExpandableSection(title = "Throttle toggles", count = toggles.size) {
                        toggles.forEachIndexed { i, path ->
                            ToggleRow(path)
                            if (i != toggles.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsed-by-default card with a tappable header (title + count badge +
 * chevron). Groups of secondary controls (toggles, cooling devices) live
 * inside one of these instead of rendering every control up front, so the
 * tab reads as a clean status list until you actually want to dig in.
 */
@Composable
private fun ExpandableSection(
    title: String,
    count: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(140))
            ) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(path: String) {
    var checked by remember(path) { mutableStateOf(ThermalTweaks.currentToggleState(path) ?: true) }
    val scope = rememberCoroutineScope()

    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            path,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Switch(
            checked = checked,
            onCheckedChange = { value ->
                checked = value
                scope.launch(Dispatchers.IO) { ThermalTweaks.setToggle(path, value) }
            }
        )
    }
}

@Composable
private fun CoolingDeviceRow(device: CoolingDevice) {
    val maxState = device.maxState
    val scope = rememberCoroutineScope()
    var state by remember(device.deviceId) { mutableStateOf((device.curState ?: 0).toFloat()) }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                device.type ?: device.deviceId,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${state.toInt()} / ${maxState ?: "?"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (maxState != null && maxState > 0) {
            Slider(
                value = state,
                onValueChange = { state = it },
                onValueChangeFinished = {
                    scope.launch(Dispatchers.IO) { ThermalTweaks.setCoolingState(device.deviceId, state.toInt()) }
                },
                valueRange = 0f..maxState.toFloat(),
                steps = (maxState - 1).coerceIn(0, 30)
            )
        } else {
            ErrorHint("No max_state exposed — throttle level can't be set here.")
        }
    }
}

@Composable
private fun ZoneCard(zone: ThermalZone, rootGranted: Boolean, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var trips by remember(zone.zoneId) { mutableStateOf<List<ThermalTrip>?>(null) }

    LaunchedEffect(expanded) {
        if (expanded && rootGranted && trips == null) {
            trips = withContext(Dispatchers.IO) { ThermalTweaks.listTripPoints(zone.zoneId) }
        }
    }

    val tempC = zone.tempMilliC?.let { it / 1000.0 }
    val color = tempColor(tempC, MaterialTheme.colorScheme)
    val containerColor = tempContainerColor(tempC, MaterialTheme.colorScheme)
    val onContainerColor = tempOnContainerColor(tempC, MaterialTheme.colorScheme)
    // 0-100°C mapped to a 0f-1f fill, clamped, used for the AOSP-battery-style temp bar.
    val fraction = ((tempC ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()

    ElevatedCard(
        onClick = { if (rootGranted) expanded = !expanded },
        enabled = rootGranted,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon = Icons.Filled.Thermostat, containerColor = containerColor, contentColor = onContainerColor, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        zone.type ?: zone.zoneId,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(zone.zoneId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    tempC?.let { "%.1f°C".format(it) } ?: "n/a",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (rootGranted) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(MaterialTheme.shapes.small)
                        .background(color)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    val t = trips
                    when {
                        t == null -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            WavyLoader(size = 24.dp)
                        }
                        t.isEmpty() -> Text(
                            "No writable trip points on this zone.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> t.forEach { trip -> TripRow(trip) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: ThermalTrip) {
    var tempMilliC by remember(trip.zoneId, trip.index) { mutableStateOf(trip.tempMilliC) }
    val scope = rememberCoroutineScope()

    fun adjust(deltaMilliC: Int) {
        val next = (tempMilliC ?: return) + deltaMilliC
        tempMilliC = next
        scope.launch(Dispatchers.IO) { ThermalTweaks.setTripTemp(trip.zoneId, trip.index, next) }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(trip.type ?: "trip ${trip.index}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tempMilliC?.let { "%.1f°C".format(it / 1000.0) } ?: "n/a",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { adjust(-1000) }, enabled = tempMilliC != null) {
            Icon(Icons.Filled.Remove, contentDescription = "lower by 1°C")
        }
        IconButton(onClick = { adjust(1000) }, enabled = tempMilliC != null) {
            Icon(Icons.Filled.Add, contentDescription = "raise by 1°C")
        }
    }
}

@Composable
fun InfoTab() {
    var infoItems by remember { mutableStateOf<List<InfoItem>?>(null) }

    LaunchedEffect(Unit) {
        infoItems = withContext(Dispatchers.IO) { KernelInfoTweaks.collect() }
    }

    LoadingOrEmptyOrContent(
        loading = infoItems == null,
        empty = infoItems?.isEmpty() == true,
        emptyMessage = "No info available."
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(infoItems.orEmpty(), key = { it.label }) { info ->
                ElevatedCard(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Filled.Info,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 32.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(info.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                info.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-system frequency scan: walks /sys for every available_frequencies
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
        emptyMessage = "No available_frequencies nodes found under /sys."
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
                        WavyLoaderSmall()
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
                        else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item { SectionLabel("Discovered nodes — tap a value to lock min+max there") }
            items(nodes.orEmpty(), key = { it.dir }) { node ->
                ElevatedCard(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            node.dir,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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

/** Shared loading / empty-state / content switcher so tabs don't repeat it, with a soft crossfade between states instead of a hard cut. */
@Composable
private fun LoadingOrEmptyOrContent(
    loading: Boolean,
    empty: Boolean,
    emptyMessage: String,
    content: @Composable () -> Unit
) {
    val state = when {
        loading -> "loading"
        empty -> "empty"
        else -> "content"
    }
    Crossfade(targetState = state, animationSpec = tween(220), label = "loadState") { s ->
        when (s) {
            "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WavyLoader(size = 48.dp)
            }
            "empty" -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            else -> content()
        }
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

/**
 * Single-row, horizontally-scrollable governor picker instead of a
 * wrapping wall of chips — governor lists are short (usually 3-6) but a
 * multi-row FlowRow still made the card feel busy. Scrolling one row is
 * the same pattern Play Store / Settings use for filter chips.
 */
@Composable
private fun FlowChips(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

/**
 * Min/max frequency picker as a single two-thumb slider instead of a wall
 * of tappable frequency chips (which could mean 15-20+ chips per card).
 * The slider snaps to indices into the sorted available-frequency table,
 * so every position it can land on is a value the hardware actually
 * supports — writes only fire on release, not per drag frame, to avoid
 * hammering root with writes while dragging.
 */
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
    if (values.size < 2) return
    val lastIdx = values.size - 1

    fun closestIndex(target: Long?, fallback: Int): Int {
        if (target == null) return fallback
        return values.indices.minByOrNull { i -> kotlin.math.abs(values[i] - target) } ?: fallback
    }

    var minIdx by remember(values, minValue) { mutableStateOf(closestIndex(minValue, 0).toFloat()) }
    var maxIdx by remember(values, maxValue) { mutableStateOf(closestIndex(maxValue, lastIdx).toFloat()) }

    SectionLabel(label)
    Text(
        "${values[minIdx.toInt()]} – ${values[maxIdx.toInt()]} MHz" + (current?.let { "  ·  now $it" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp)
    )
    RangeSlider(
        value = minIdx..maxIdx,
        onValueChange = { range ->
            minIdx = range.start
            maxIdx = range.endInclusive
        },
        onValueChangeFinished = {
            onSetMin(values[minIdx.toInt()])
            onSetMax(values[maxIdx.toInt()])
        },
        valueRange = 0f..lastIdx.toFloat(),
        steps = (values.size - 2).coerceAtLeast(0)
    )
}