package ru.ama.whereme.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.ama.whereme.R
import ru.ama.whereme.feature.about.AboutScreen
import ru.ama.whereme.feature.about.R as AboutR
import ru.ama.whereme.feature.map.MapScreen
import ru.ama.whereme.feature.map.MapToolbarActions
import ru.ama.whereme.feature.profile.ProfileScreen
import ru.ama.whereme.feature.profile.ProfileToolbarActions
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
        val wide = maxWidth >= NavigationRailMinWidth
        Scaffold(
            topBar = {
                key(pages.items[selected].configuration) {
                    pages.items[selected].instance?.let { ScreenToolbar(it) }
                }
            },
            bottomBar = {
                if (!wide)
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selected == tab.ordinal,
                                onClick = { root.select(tab) },
                                icon = { TabIcon(tab) },
                                label = {
                                    Text(
                                        stringResource(tab.label),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide)
                    NavigationRail {
                        Tab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selected == tab.ordinal,
                                onClick = { root.select(tab) },
                                icon = { TabIcon(tab) },
                                label = {
                                    Text(
                                        stringResource(tab.label),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
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
            Tab.ABOUT -> R.string.menu_about_short
        }

@Composable
internal fun ScreenToolbar(screen: Screen) {
    when (screen) {
        is Screen.Map -> {
            val state by screen.component.state.collectAsStateWithLifecycle()
            AppToolbar(stringResource(R.string.menu_map), subtitle = state.date) {
                MapToolbarActions(screen.component)
            }
        }
        is Screen.Settings -> AppToolbar(stringResource(R.string.menu_settings))
        is Screen.Profile ->
            AppToolbar(stringResource(R.string.menu_profile)) {
                ProfileToolbarActions(screen.component)
            }
        is Screen.About -> {
            val licenses by screen.component.licenses.collectAsStateWithLifecycle()
            AppToolbar(
                title =
                    stringResource(
                        if (licenses) AboutR.string.about_licenses else R.string.menu_about
                    ),
                navigationIcon = {
                    if (licenses)
                        IconButton(onClick = { screen.component.showLicenses(false) }) {
                            Icon(
                                painterResource(R.drawable.ic_back),
                                stringResource(AboutR.string.about_back),
                            )
                        }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppToolbar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
    )
}

private val NavigationRailMinWidth = 600.dp
