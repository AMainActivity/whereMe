package ru.ama.whereme.feature.about

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.serializer

class AboutComponent(context: ComponentContext) : ComponentContext by context {
    private val mutable =
        MutableStateFlow(stateKeeper.consume("licenses", Boolean.serializer()) ?: false)
    val licenses = mutable.asStateFlow()
    private val back = BackCallback(isEnabled = mutable.value) { showLicenses(false) }

    init {
        stateKeeper.register("licenses", Boolean.serializer()) { mutable.value }
        backHandler.register(back)
    }

    fun showLicenses(show: Boolean) {
        mutable.value = show
        back.isEnabled = show
    }
}
