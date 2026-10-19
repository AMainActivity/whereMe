package ru.ama.whereme.feature.about

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import org.junit.Assert.*
import org.junit.Test

class AboutComponentTest {
    @Test
    fun backClosesLicensesAndThenBelongsToParent() {
        val back = BackDispatcher()
        val component =
            AboutComponent(DefaultComponentContext(LifecycleRegistry(), backHandler = back))
        assertFalse(back.isEnabled)
        component.showLicenses(true)
        assertTrue(back.isEnabled)
        back.back()
        assertFalse(component.licenses.value)
        assertFalse(back.isEnabled)
    }

    @Test
    fun licensePageSurvivesSavedStateRestoration() {
        val keeper = StateKeeperDispatcher()
        AboutComponent(DefaultComponentContext(LifecycleRegistry(), stateKeeper = keeper))
            .showLicenses(true)
        val restored =
            AboutComponent(
                DefaultComponentContext(
                    LifecycleRegistry(),
                    stateKeeper = StateKeeperDispatcher(keeper.save()),
                )
            )
        assertTrue(restored.licenses.value)
    }
}
