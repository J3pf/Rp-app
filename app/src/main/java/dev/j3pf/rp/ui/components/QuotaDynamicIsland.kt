package dev.j3pf.rp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.QuotaWindow
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.util.parseColor
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Compact, optional quota island. Machines without a configured provider are omitted entirely. */
@Composable
fun QuotaDynamicIsland(
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
    selectedMachineId: String? = null,
) {
    val machinesWithQuotas =
        remember(machines) {
            machines.mapNotNull { machine ->
                val configured = machine.quotas.orEmpty().filterNot { it.notConfigured }
                configured.takeIf { it.isNotEmpty() }?.let { machine to it }
            }
        }
    if (machinesWithQuotas.isEmpty()) return

    val machineIds = machinesWithQuotas.map { it.first.machineId }
    val selectedPage = machineIds.indexOf(selectedMachineId).takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { machinesWithQuotas.size })
    var selectedQuota by remember { mutableStateOf<SelectedQuota?>(null) }

    LaunchedEffect(machineIds, selectedMachineId) {
        val targetPage = machineIds.indexOf(selectedMachineId)
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            key = { page -> machineIds[page] },
            pageSpacing = 0.dp,
        ) { page ->
            val (machine, quotas) = machinesWithQuotas[page]
            QuotaMachinePill(
                machine = machine,
                quotas = quotas,
                modifier = Modifier.fillMaxWidth(),
                onQuotaClick = { quota -> selectedQuota = SelectedQuota(machine, quota) },
            )
        }

        if (machinesWithQuotas.size > 1) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                machinesWithQuotas.indices.forEach { page ->
                    Box(
                        Modifier
                            .size(if (pagerState.currentPage == page) 6.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == page) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
    }

    selectedQuota?.let { selection ->
        QuotaDetailSheet(
            machine = selection.machine,
            quota = selection.quota,
            onDismiss = { selectedQuota = null },
        )
    }
}

@Composable
private fun QuotaMachinePill(
    machine: MachineSummary,
    quotas: List<MachineQuota>,
    modifier: Modifier = Modifier,
    onQuotaClick: (MachineQuota) -> Unit,
) {
    val accentColor = parseColor(machine.color)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RpShapes.pill,
                color = accentColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "⚡",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = machine.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            quotas.forEach { quota ->
                QuotaProviderPill(quota = quota, onClick = { onQuotaClick(quota) })
            }
        }
    }
}

@Composable
private fun QuotaProviderPill(
    quota: MachineQuota,
    onClick: () -> Unit,
) {
    val primaryWindow = quota.primaryWindow()
    val statusColor = quota.statusColor(primaryWindow?.usedPercent)
    val targetProgress = ((primaryWindow?.usedPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetProgress,
        animationSpec =
            androidx.compose.animation.core
                .tween(durationMillis = 350),
        label = "quotaProgress",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 32.dp),
        shape = RpShapes.pill,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = quota.displayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = primaryWindow?.let { formatPercent(it.usedPercent) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.width(64.dp).height(2.5.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotaDetailSheet(
    machine: MachineSummary,
    quota: MachineQuota,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(machine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text =
                        quota.displayLabel() +
                            quota.plan
                                ?.takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val errorMessage = quota.error
            if (!quota.ok && errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (quota.windows.isEmpty()) {
                Text(
                    text = if (!quota.ok) "No se pudieron cargar los detalles de cuota." else "No hay ventanas de uso disponibles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                quota.windows.forEach { window -> QuotaWindowDetail(window, quota) }
            }
        }
    }
}

@Composable
private fun QuotaWindowDetail(
    window: QuotaWindow,
    quota: MachineQuota,
) {
    val statusColor = quota.statusColor(window.usedPercent)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(window.displayLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    formatPercent(window.usedPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { (window.usedPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Text(
                text = "Usado ${formatPercent(window.usedPercent)} · Disponible ${formatPercent(window.remainingPercent)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            formatResetCountdown(window)?.let { countdown ->
                Text(
                    "Resetea en $countdown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            window.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class SelectedQuota(
    val machine: MachineSummary,
    val quota: MachineQuota,
)

private fun MachineQuota.primaryWindow(): QuotaWindow? = windows.firstOrNull { it.id == primaryWindowId } ?: windows.firstOrNull()

private fun MachineQuota.displayLabel(): String =
    label.takeIf { it.isNotBlank() } ?: when (family.lowercase(Locale.ROOT)) {
        "claude" -> "Claude"
        "codex" -> "Codex"
        "antigravity" -> "Antigravity"
        "opencode" -> "OpenCode"
        "github" -> "GitHub"
        else -> family.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private fun QuotaWindow.displayLabel(): String =
    label?.takeIf { it.isNotBlank() } ?: id.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun MachineQuota.statusColor(usedPercent: Double?): Color =
    when {
        !ok -> MaterialTheme.colorScheme.error
        (usedPercent ?: 0.0) > 90.0 -> RpColors.danger
        (usedPercent ?: 0.0) >= 70.0 -> RpColors.warning
        else -> RpColors.success
    }

private fun formatPercent(value: Double): String {
    val rounded = value.coerceAtLeast(0.0)
    return if (rounded % 1.0 == 0.0) "${rounded.roundToInt()}%" else "${"%.1f".format(Locale.getDefault(), rounded)}%"
}

private fun formatResetCountdown(window: QuotaWindow): String? {
    val remainingSeconds =
        window.resetsInSec?.takeIf { it >= 0 } ?: window.resetsAt?.let { resetAt ->
            runCatching { (Instant.parse(resetAt).epochSecond - Instant.now().epochSecond).coerceAtLeast(0) }.getOrNull()
        } ?: return null
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
