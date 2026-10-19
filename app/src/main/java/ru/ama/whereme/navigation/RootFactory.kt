package ru.ama.whereme.navigation

import com.arkivanov.decompose.ComponentContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import ru.ama.whereme.data.di.ApplicationScope
import ru.ama.whereme.data.di.DefaultDispatcher
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.domain.usecase.*
import ru.ama.whereme.feature.about.AboutComponent
import ru.ama.whereme.feature.map.MapComponent
import ru.ama.whereme.feature.profile.ProfileComponent
import ru.ama.whereme.feature.settings.SettingsComponent
import ru.ama.whereme.tracking.AndroidTrackingControl

class RootFactory
@Inject
constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val history: LocationHistoryRepository,
    private val checkCode: CheckSignInCode,
    private val signOut: SignOut,
    private val refresh: RefreshSessionUseCase,
    private val tracking: AndroidTrackingControl,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    fun create(context: ComponentContext) =
        RootComponent(context, settings) { tab, child ->
            when (tab) {
                Tab.MAP -> Screen.Map(MapComponent(child, history, defaultDispatcher))
                Tab.SETTINGS ->
                    Screen.Settings(SettingsComponent(child, settings, tracking, applicationScope))
                Tab.PROFILE ->
                    Screen.Profile(ProfileComponent(child, sessions, checkCode, signOut, refresh))
                Tab.ABOUT -> Screen.About(AboutComponent(child))
            }
        }
}
