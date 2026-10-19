package ru.ama.whereme.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.ama.whereme.R
import ru.ama.whereme.feature.about.AboutScreen
import ru.ama.whereme.feature.map.MapScreen
import ru.ama.whereme.feature.profile.ProfileScreen
import ru.ama.whereme.feature.settings.SettingsScreen
import ru.ama.whereme.navigation.*

@Composable
fun RootScreen(
    root: RootComponent,
    version: String,
    licensesResource: Int,
    modifier: Modifier = Modifier,
) {
    val pages by root.pages.subscribeAsState()
    val selected = pages.selectedIndex
    val holder = rememberSaveableStateHolder()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            bottomBar = {
                if (!wide)
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selected == tab.ordinal,
                                onClick = { root.select(tab) },
                                icon = { TabIcon(tab) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide)
                    NavigationRail {
                        Tab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selected == tab.ordinal,
                                onClick = { root.select(tab) },
                                icon = { TabIcon(tab) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                holder.SaveableStateProvider(pages.items[selected].configuration.name) {
                    Box(Modifier.weight(1f).fillMaxHeight().imePadding()) {
                        when (val screen = pages.items[selected].instance) {
                            is Screen.Map -> MapScreen(screen.component)
                            is Screen.Settings -> SettingsScreen(screen.component)
                            is Screen.Profile -> ProfileScreen(screen.component)
                            is Screen.About ->
                                AboutScreen(
                                    screen.component,
                                    stringResource(R.string.app_name),
                                    version,
                                    licensesResource,
                                )
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab) {
    Icon(
        painterResource(
            when (tab) {
                Tab.MAP -> R.drawable.ic_baseline_map_24
                Tab.SETTINGS -> R.drawable.ic_baseline_settings_24
                Tab.PROFILE -> R.drawable.ic_baseline_person_24
                Tab.ABOUT -> R.drawable.ic_baseline_info_24
            }
        ),
        contentDescription = null,
    )
}

private val Tab.label
    get() =
        when (this) {
            Tab.MAP -> R.string.menu_map
            Tab.SETTINGS -> R.string.menu_settings
            Tab.PROFILE -> R.string.menu_profile
            Tab.ABOUT -> R.string.menu_about
        }
