package ru.ama.whereme.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.children.ChildNavState
import com.arkivanov.decompose.router.pages.*
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import ru.ama.whereme.domain.repository.SettingsRepository
import ru.ama.whereme.domain.state.*
import ru.ama.whereme.feature.about.AboutComponent
import ru.ama.whereme.feature.map.MapComponent
import ru.ama.whereme.feature.profile.ProfileComponent
import ru.ama.whereme.feature.settings.SettingsComponent

@Serializable
enum class Tab {
    MAP,
    SETTINGS,
    PROFILE,
    ABOUT,
}

sealed interface Screen {
    data class Map(val component: MapComponent) : Screen

    data class Settings(val component: SettingsComponent) : Screen

    data class Profile(val component: ProfileComponent) : Screen

    data class About(val component: AboutComponent) : Screen
}

class RootComponent(
    context: ComponentContext,
    settings: SettingsRepository,
    createScreen: (Tab, ComponentContext) -> Screen,
) : ComponentContext by context {
    private val scope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val retry = MutableStateFlow(0)
    val theme = settings.themeMode.loadState(scope, retry)
    private val navigation = PagesNavigation<Tab>()
    private val back = BackCallback(isEnabled = false) { select(Tab.MAP) }

    init {
        backHandler.register(back)
    }

    val pages =
        childPages(
            source = navigation,
            serializer = Tab.serializer(),
            initialPages = { Pages(items = Tab.entries.toList(), selectedIndex = Tab.MAP.ordinal) },
            // Four fixed tabs retain their components; only the selected tab is resumed.
            pageStatus = { index, pages ->
                if (index == pages.selectedIndex) ChildNavState.Status.RESUMED
                else ChildNavState.Status.CREATED
            },
            childFactory = createScreen,
        )

    init {
        val subscription = pages.subscribe { back.isEnabled = it.selectedIndex != Tab.MAP.ordinal }
        lifecycle.doOnDestroy { subscription.cancel() }
    }

    fun select(tab: Tab) {
        navigation.select(tab.ordinal)
    }

    fun retryStorage() {
        retry.value++
    }
}
