package ru.ama.whereme.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.*
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.feature.about.AboutComponent

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val settings =
        object : SettingsRepository {
            override val settings = MutableStateFlow(SettingsDomModel.DEFAULT)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

            override suspend fun updateSettings(transform: (SettingsDomModel) -> SettingsDomModel) =
                transform(settings.value).also { settings.value = it }

            override suspend fun setThemeMode(mode: ThemeMode) {
                themeMode.value = mode
            }
        }

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun after() {
        lifecycles.forEach { it.destroy() }
        Dispatchers.resetMain()
    }

    private fun root(
        saved: StateKeeperDispatcher = StateKeeperDispatcher(),
        back: BackDispatcher = BackDispatcher(),
    ): RootComponent {
        val lifecycle =
            LifecycleRegistry().also {
                it.resume()
                lifecycles.add(it)
            }
        return RootComponent(
            DefaultComponentContext(lifecycle, stateKeeper = saved, backHandler = back),
            settings,
        ) { _, child ->
            Screen.About(AboutComponent(child))
        }
    }

    @Test
    fun switchingTabsRetainsTheirComponentsAndResumesOnlySelected() =
        runTest(dispatcher) {
            val root = root()
            val originals = root.pages.value.items.map { requireNotNull(it.instance) }
            root.select(Tab.ABOUT)
            assertEquals(3, root.pages.value.selectedIndex)
            root.pages.value.items.forEachIndexed { index, child ->
                assertSame(originals[index], child.instance)
                assertEquals(
                    if (index == 3) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
                    (child.instance as Screen.About).component.lifecycle.state,
                )
            }
        }

    @Test
    fun backClosesChildPageThenReturnsToMap() =
        runTest(dispatcher) {
            val back = BackDispatcher()
            val root = root(back = back)
            root.select(Tab.ABOUT)
            val about = (root.pages.value.items[3].instance as Screen.About).component
            about.showLicenses(true)
            back.back()
            assertFalse(about.licenses.value)
            assertEquals(3, root.pages.value.selectedIndex)
            back.back()
            assertEquals(0, root.pages.value.selectedIndex)
            assertFalse(back.isEnabled)
        }

    @Test
    fun processRestorationKeepsSelectedTabAndChildPage() =
        runTest(dispatcher) {
            val keeper = StateKeeperDispatcher()
            val root = root(keeper)
            root.select(Tab.ABOUT)
            (root.pages.value.items[3].instance as Screen.About).component.showLicenses(true)
            val restored = root(StateKeeperDispatcher(keeper.save()))
            assertEquals(3, restored.pages.value.selectedIndex)
            assertTrue(
                (restored.pages.value.items[3].instance as Screen.About).component.licenses.value
            )
        }
}
