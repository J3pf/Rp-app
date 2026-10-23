package dev.j3pf.rp.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.ui.theme.RpColors

enum class AppTab(
    val route: Any,
    val label: String,
) {
    AGENTS(AgentsRoute, "Agentes"),
    MESH(MeshRoute, "Red"),
    PENDING(PendingRoute, "Pendientes"),
    SETTINGS(SettingsRoute, "Ajustes"),
}

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    modifier: Modifier = Modifier,
    pending: Int = 0,
    onSelectTab: (AppTab) -> Unit = {},
) {
    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    Surface(
        modifier =
            modifier.drawBehind {
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
        tonalElevation = 4.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
                    selected = selected,
                    alwaysShowLabel = true,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        when (tab) {
                            AppTab.PENDING -> {
                                BadgedBox(
                                    badge = {
                                        if (pending > 0) {
                                            Badge(containerColor = RpColors.warning) {
                                                Text("$pending", color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Inbox, contentDescription = tab.label)
                                }
                            }

                            AppTab.AGENTS -> {
                                Icon(Icons.Filled.SmartToy, contentDescription = tab.label)
                            }

                            AppTab.MESH -> {
                                Icon(Icons.Filled.Hub, contentDescription = tab.label)
                            }

                            AppTab.SETTINGS -> {
                                Icon(Icons.Filled.Settings, contentDescription = tab.label)
                            }
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/** Pantalla de bloqueo biométrico de la app (fase 4). */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    message: String? = null,
) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RP bloqueada", style = MaterialTheme.typography.headlineMedium)
                message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                Button(onClick = onUnlock, Modifier.padding(top = 24.dp)) { Text("Desbloquear") }
            }
        }
    }
}
