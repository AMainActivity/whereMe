package ru.ama.whereme.feature.profile

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.AuthRepository
import ru.ama.whereme.domain.repository.SessionRepository
import ru.ama.whereme.domain.usecase.CheckSignInCode
import ru.ama.whereme.domain.usecase.RefreshSessionUseCase
import ru.ama.whereme.domain.usecase.SignOut

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileComponentTest {
    private val dispatcher = StandardTestDispatcher()
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val profile = JsonJwt(false, "1", "test-token", 1, 2, "Name", null, 1)
    private var requests = 0
    private var writes = 0
    private var request: suspend () -> ResponseJwtEntity = { ResponseJwtEntity(profile, true, 200) }
    private var save: suspend (SettingsUserInfoDomModel) -> Unit = {}
    private val auth =
        object : AuthRepository {
            override suspend fun checkKod(code: String): ResponseJwtEntity {
                requests++
                return request()
            }

            override suspend fun checkWmJwToken(token: String): ResponseEntity =
                error("Unexpected refresh")

            override suspend fun logOut(token: String): ResponseEntity = error("Unexpected logout")
        }
    private val sessions =
        object : SessionRepository {
            override val session =
                MutableStateFlow(SessionSnapshot(SettingsUserInfoDomModel.EMPTY, "initial"))

            override suspend fun signIn(profile: SettingsUserInfoDomModel) {
                writes++
                save(profile)
            }

            override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean =
                error("Unexpected logout")

            override suspend fun updateActivation(
                expected: SessionSnapshot,
                active: Boolean,
            ): Boolean = error("Unexpected refresh")

            override suspend fun <T> withCurrent(
                expected: SessionSnapshot,
                action: suspend () -> T,
            ): T? = error("Unexpected ACK")
        }

    private fun model(): ProfileComponent {
        val lifecycle =
            LifecycleRegistry().also {
                it.resume()
                lifecycles.add(it)
            }
        return ProfileComponent(
            DefaultComponentContext(lifecycle),
            sessions,
            CheckSignInCode(auth),
            SignOut(auth, sessions),
            RefreshSessionUseCase(auth, sessions),
        )
    }

    private fun ProfileComponent.checkKod(code: String) {
        accept(ProfileIntent.Code(code))
        accept(ProfileIntent.Check)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        lifecycles.forEach { it.destroy() }
        Dispatchers.resetMain()
    }

    @Test
    fun rejectedConfirmationDoesNotReappearForANewCollector() =
        runTest(dispatcher) {
            val vm = model()
            vm.checkKod("code")
            runCurrent()
            assertNotNull(vm.state.first().confirmation)
            vm.accept(ProfileIntent.DismissConfirmation)
            assertNull(vm.state.first().confirmation)
            assertEquals(0, writes)
        }

    @Test
    fun confirmationWaitsForUserEvenWithoutACollector() =
        runTest(dispatcher) {
            val vm = model()
            vm.checkKod("code")
            runCurrent()
            assertEquals(profile.tokenJwt, vm.state.first().confirmation?.tokenJwt)
            assertEquals(profile.tokenJwt, vm.state.first().confirmation?.tokenJwt)
            assertEquals(0, writes)
        }

    @Test
    fun confirmationIsConsumedBeforeSavingAndCannotBeSubmittedTwice() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            save = {
                assertEquals("test-token", it.tokenJwt)
                gate.await()
            }
            val vm = model()
            vm.checkKod("code")
            runCurrent()
            vm.accept(ProfileIntent.Confirm)
            vm.accept(ProfileIntent.Confirm)
            runCurrent()
            assertTrue(vm.state.first().busy)
            assertNull(vm.state.value.confirmation)
            assertEquals(1, writes)
            gate.complete(Unit)
            runCurrent()
            assertFalse(vm.state.first().busy)
            assertNull(vm.state.value.confirmation)
            vm.accept(ProfileIntent.Confirm)
            runCurrent()
            assertEquals(1, writes)
        }

    @Test
    fun onlyOneCodeRequestCanBeActive() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<ResponseJwtEntity>()
            request = { gate.await() }
            val vm = model()
            vm.checkKod("first")
            vm.checkKod("second")
            runCurrent()
            assertEquals(1, requests)
            assertTrue(vm.state.value.busy)
            gate.complete(ResponseJwtEntity(profile, true, 200))
            runCurrent()
            vm.checkKod("third")
            runCurrent()
            assertEquals(1, requests)
        }

    @Test
    fun failedSaveDoesNotReplayConfirmationAndAllowsAnotherAttempt() =
        runTest(dispatcher) {
            save = { throw IOException("storage unavailable") }
            val vm = model()
            vm.checkKod("code")
            runCurrent()
            vm.accept(ProfileIntent.Confirm)
            runCurrent()
            assertEquals(ProfileError.SAVE, vm.state.first().error)
            assertNull(vm.state.value.confirmation)
            vm.checkKod("code")
            runCurrent()
            assertNotNull(vm.state.value.confirmation)
            assertEquals(2, requests)
        }

    @Test
    fun invalidCodeIsStateAndCanBeRetried() =
        runTest(dispatcher) {
            request = { ResponseJwtEntity(profile.copy(message = "0"), true, 200) }
            val vm = model()
            vm.checkKod("wrong")
            runCurrent()
            assertEquals(ProfileError.CODE, vm.state.first().error)
            request = { ResponseJwtEntity(profile, true, 200) }
            vm.checkKod("right")
            runCurrent()
            assertEquals(profile.tokenJwt, vm.state.value.confirmation?.tokenJwt)
        }

    @Test
    fun serverFailureShowsRequestErrorAndAllowsRetry() =
        runTest(dispatcher) {
            val component = model()
            for (reply in
                listOf(
                    ResponseJwtEntity(null, false, 500),
                    ResponseJwtEntity(null, false, 503),
                    ResponseJwtEntity(null, true, 200),
                    ResponseJwtEntity(profile.copy(tokenJwt = ""), true, 200),
                )) {
                request = { reply }
                component.checkKod("code")
                runCurrent()
                assertEquals(ProfileError.REQUEST, component.state.value.error)
                assertNull(component.state.value.confirmation)
                assertFalse(component.state.value.busy)
                assertEquals(0, writes)
            }
            request = { ResponseJwtEntity(profile, true, 200) }
            component.checkKod("code")
            runCurrent()
            assertNull(component.state.value.error)
            assertNotNull(component.state.value.confirmation)
        }

    @Test
    fun destroyedComponentCancelsTheNetworkRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<ResponseJwtEntity>()
            var finished = false
            request = {
                try {
                    gate.await()
                } finally {
                    finished = true
                }
            }
            val vm = model()
            vm.checkKod("code")
            runCurrent()
            lifecycles.forEach { it.destroy() }
            runCurrent()
            assertTrue(finished)
            assertEquals(0, writes)
        }
}
