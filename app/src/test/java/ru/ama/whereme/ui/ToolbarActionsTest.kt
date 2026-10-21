package ru.ama.whereme.ui

import java.time.Instant
import java.time.LocalDate
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.*
import ru.ama.whereme.domain.usecase.*
import ru.ama.whereme.feature.map.*
import ru.ama.whereme.feature.profile.*
import ru.ama.whereme.navigation.Screen
import ru.ama.whereme.ui.theme.WhereMeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ToolbarActionsTest {
    @get:Rule val compose = createComposeRule()
    private val lifecycle = LifecycleRegistry()
    private var logouts = 0
    private val profile =
        SettingsUserInfoDomModel("test-token", 1, 2, "Тестовый профиль", "test-link", true)
    private val sessions =
        object : SessionRepository {
            override val session = MutableStateFlow(SessionSnapshot(profile, "test-session"))

            override suspend fun signIn(profile: SettingsUserInfoDomModel) =
                error("Unexpected sign-in")

            override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean {
                if (session.value != expected) return false
                session.value = SessionSnapshot(SettingsUserInfoDomModel.EMPTY, "signed-out")
                return true
            }

            override suspend fun updateActivation(expected: SessionSnapshot, active: Boolean) =
                session.value == expected

            override suspend fun <T> withCurrent(
                expected: SessionSnapshot,
                action: suspend () -> T,
            ): T? = if (session.value == expected) action() else null
        }
    private val auth =
        object : AuthRepository {
            override suspend fun checkKod(code: String): ResponseJwtEntity =
                error("Unexpected code check")

            override suspend fun checkWmJwToken(token: String) =
                ResponseEntity(JsonEntity(false, "1"), true, 200)

            override suspend fun logOut(token: String): ResponseEntity {
                logouts++
                return ResponseEntity(JsonEntity(false, "1"), true, 200)
            }
        }

    @After
    fun after() {
        compose.runOnUiThread { lifecycle.destroy() }
    }

    private fun showProfile() {
        lateinit var component: ProfileComponent
        compose.runOnUiThread {
            lifecycle.resume()
            component =
                ProfileComponent(
                    DefaultComponentContext(lifecycle),
                    sessions,
                    CheckSignInCode(auth),
                    SignOut(auth, sessions),
                    RefreshSessionUseCase(auth, sessions),
                )
        }
        compose.setContent {
            WhereMeTheme(dark = false) {
                Scaffold(topBar = { ScreenToolbar(Screen.Profile(component)) }) { padding ->
                    ProfileScreen(component, Modifier.padding(padding))
                }
            }
        }
    }

    @Test
    fun logoutRequiresConfirmationAndRemovesToolbarActionsAfterSuccess() {
        showProfile()
        compose.onNodeWithText("Выйти").assertDoesNotExist()
        compose.onNodeWithContentDescription("Выйти").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithContentDescription("Выйти").performClick()
        compose.onNodeWithText("Выйти").performClick()
        compose.onNodeWithContentDescription("Выйти").assertDoesNotExist()
        compose.onNodeWithContentDescription("Поделиться ссылкой").assertDoesNotExist()
        compose.onNodeWithText("Получить код в Telegram").assertExists()
        compose.runOnIdle { assertEquals(1, logouts) }
    }

    @Test
    fun shareIconKeepsConfirmationAndOpensSystemChooser() {
        showProfile()
        compose.onNodeWithText("Поделиться ссылкой").assertDoesNotExist()
        compose.onNodeWithContentDescription("Поделиться ссылкой").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle {
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        }
        compose.onNodeWithContentDescription("Поделиться ссылкой").performClick()
        compose.onNodeWithText("Поделиться ссылкой").performClick()
        compose.runOnIdle {
            val chooser = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertEquals(Intent.ACTION_SEND, send?.action)
            assertEquals(
                "https://kol.hhos.ru/gkk/map.php?wm=test-link",
                send?.getStringExtra(Intent.EXTRA_TEXT),
            )
        }
    }

    @Test
    fun calendarIconOpensPickerAndCancellationPreservesSelectedDay() {
        lateinit var component: MapComponent
        compose.runOnUiThread {
            lifecycle.resume()
            component =
                MapComponent(
                    DefaultComponentContext(lifecycle),
                    object : LocationHistoryRepository {
                        override val lastRecordedTime = kotlinx.coroutines.flow.flowOf<Instant?>(null)
                        override fun getLocationById(date: LocalDate) = flowOf(emptyList<LocationDb>())
                    },
                )
            component.accept(MapIntent.SelectDate(LocalDate.parse("2026-10-10")))
        }
        compose.setContent {
            WhereMeTheme(dark = true) {
                Scaffold(topBar = { ScreenToolbar(Screen.Map(component)) }) { padding ->
                    MapScreen(component, Modifier.padding(padding))
                }
            }
        }
        compose.onNodeWithText("10.10.2026").assertExists()
        compose.onNodeWithContentDescription("Выбрать дату").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(LocalDate.parse("2026-10-10"), component.state.value.date) }
    }
}
