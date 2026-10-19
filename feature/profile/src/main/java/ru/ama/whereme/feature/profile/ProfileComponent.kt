package ru.ama.whereme.feature.profile

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import ru.ama.whereme.domain.entity.SessionSnapshot
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel
import ru.ama.whereme.domain.reportFailure
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.state.*
import ru.ama.whereme.domain.usecase.CheckSignInCode
import ru.ama.whereme.domain.usecase.RefreshSessionUseCase
import ru.ama.whereme.domain.usecase.SignOut

class ProfileComponent(
    context: ComponentContext,
    private val sessions: SessionRepository,
    private val checkCode: CheckSignInCode,
    private val signOut: SignOut,
    refresh: RefreshSessionUseCase,
) : ComponentContext by context {
    private val scope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val retry = MutableStateFlow(0)
    private val mutable =
        MutableStateFlow(
            ProfileState(code = stateKeeper.consume("code", String.serializer()).orEmpty())
        )
    val state = mutable.asStateFlow()

    private val session = sessions.session.loadState(scope, retry)

    init {
        stateKeeper.register("code", String.serializer()) { state.value.code }
        scope.launch {
            session.collect { session ->
                mutable.update { it.copy(session = session) }
            }
        }
        scope.launch {
            session
                .filterIsInstance<LoadState.Ready<SessionSnapshot>>()
                .distinctUntilChangedBy { it.value.generation }
                .collectLatest { refresh(it.value) }
        }
    }

    fun accept(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Code ->
                if (!state.value.busy) mutable.update { it.copy(code = intent.text, error = null) }
            ProfileIntent.Check -> {
                val current = state.value
                if (current.busy || current.confirmation != null || current.code.isBlank()) return
                operate(ProfileError.REQUEST) {
                    val profile = checkCode(current.code.trim())
                    mutable.update {
                        it.copy(
                            confirmation = profile,
                            error = if (profile == null) ProfileError.CODE else null,
                        )
                    }
                }
            }
            ProfileIntent.Confirm -> {
                if (state.value.busy) return
                val profile = state.value.confirmation ?: return
                mutable.update { it.copy(confirmation = null) }
                operate(ProfileError.SAVE) {
                    sessions.signIn(profile)
                    mutable.update { it.copy(code = "") }
                    retry.value++
                }
            }
            ProfileIntent.DismissConfirmation ->
                if (!state.value.busy) mutable.update { it.copy(confirmation = null) }
            ProfileIntent.LogOut -> {
                if (state.value.busy) return
                val expected = (state.value.session as? LoadState.Ready)?.value ?: return
                if (expected.profile.tokenJwt.isBlank()) return
                operate(ProfileError.REQUEST) {
                    if (!signOut(expected)) mutable.update { it.copy(error = ProfileError.REQUEST) }
                }
            }
            ProfileIntent.Retry -> {
                mutable.update { it.copy(error = null) }
                retry.value++
            }
        }
    }

    private fun operate(error: ProfileError, action: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportFailure("Profile action", failure)
                mutable.update { it.copy(error = error) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}

data class ProfileState(
    val session: LoadState<SessionSnapshot> = LoadState.Loading,
    val code: String = "",
    val busy: Boolean = false,
    val confirmation: SettingsUserInfoDomModel? = null,
    val error: ProfileError? = null,
)

enum class ProfileError {
    CODE,
    REQUEST,
    SAVE,
}

sealed interface ProfileIntent {
    data class Code(val text: String) : ProfileIntent

    data object Check : ProfileIntent

    data object Confirm : ProfileIntent

    data object DismissConfirmation : ProfileIntent

    data object LogOut : ProfileIntent

    data object Retry : ProfileIntent
}
