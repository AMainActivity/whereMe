package ru.ama.whereme.presentation

import androidx.lifecycle.ViewModelStore
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
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.*
import ru.ama.whereme.domain.repository.AuthRepository
import ru.ama.whereme.domain.repository.SessionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val profile = JsonJwt(false, "1", "test-token", 1, 2, "Name", null, 1)
    private var requests = 0
    private var writes = 0
    private var request: suspend () -> ResponseJwtEntity = { ResponseJwtEntity(profile, true, 200) }
    private var save: suspend (SettingsUserInfoDomModel) -> Unit = {}
    private val auth = object : AuthRepository {
        override suspend fun checkKod(code: String): ResponseJwtEntity { requests++; return request() }
        override suspend fun checkWmJwToken(token: String): ResponseEntity = error("Unexpected refresh")
        override suspend fun logOut(token: String): ResponseEntity = error("Unexpected logout")
    }
    private val sessions = object : SessionRepository {
        override val session = MutableStateFlow(SessionSnapshot(SettingsUserInfoDomModel.EMPTY, "initial"))
        override suspend fun signIn(profile: SettingsUserInfoDomModel) { writes++; save(profile) }
        override suspend fun clearIfCurrent(expected: SessionSnapshot): Boolean = error("Unexpected logout")
        override suspend fun updateActivation(expected: SessionSnapshot, active: Boolean): Boolean = error("Unexpected refresh")
        override suspend fun <T> withCurrent(expected: SessionSnapshot, action: suspend () -> T): T? = error("Unexpected ACK")
    }
    private fun model() = ProfileInViewModel(auth, sessions).also { models.put("login", it) }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    @Test fun rejectedConfirmationDoesNotReappearForANewCollector() = runTest(dispatcher) {
        val vm = model()
        vm.checkKod("code"); runCurrent()
        assertTrue(vm.state.first() is SignInState.Confirmation)
        vm.dismissConfirmation()
        assertEquals(SignInState.Editing, vm.state.first())
        assertEquals(0, writes)
    }

    @Test fun confirmationWaitsForUserEvenWithoutACollector() = runTest(dispatcher) {
        val vm = model()
        vm.checkKod("code"); runCurrent()
        assertEquals(SignInState.Confirmation(profile), vm.state.first())
        assertEquals(SignInState.Confirmation(profile), vm.state.first())
        assertEquals(0, writes)
    }

    @Test fun confirmationIsConsumedBeforeSavingAndCannotBeSubmittedTwice() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        save = { assertEquals("test-token", it.tokenJwt); gate.await() }
        val vm = model()
        vm.checkKod("code"); runCurrent()
        vm.confirmSignIn(); vm.confirmSignIn(); runCurrent()
        assertEquals(SignInState.Saving, vm.state.first())
        assertEquals(1, writes)
        gate.complete(Unit); runCurrent()
        assertEquals(SignInState.SignedIn, vm.state.first())
        vm.confirmSignIn(); runCurrent()
        assertEquals(1, writes)
    }

    @Test fun onlyOneCodeRequestCanBeActive() = runTest(dispatcher) {
        val gate = CompletableDeferred<ResponseJwtEntity>()
        request = { gate.await() }
        val vm = model()
        vm.checkKod("first"); vm.checkKod("second"); runCurrent()
        assertEquals(1, requests)
        assertEquals(SignInState.Checking, vm.state.value)
        gate.complete(ResponseJwtEntity(profile, true, 200)); runCurrent()
        vm.checkKod("third"); runCurrent()
        assertEquals(1, requests)
    }

    @Test fun failedSaveDoesNotReplayConfirmationAndAllowsAnotherAttempt() = runTest(dispatcher) {
        save = { throw IOException("storage unavailable") }
        val vm = model()
        vm.checkKod("code"); runCurrent()
        vm.confirmSignIn(); runCurrent()
        assertEquals(SignInState.Failed(R.string.storage_write_failed), vm.state.first())
        vm.checkKod("code"); runCurrent()
        assertTrue(vm.state.value is SignInState.Confirmation)
        assertEquals(2, requests)
    }

    @Test fun invalidCodeIsStateAndCanBeRetried() = runTest(dispatcher) {
        request = { ResponseJwtEntity(null, false, 403) }
        val vm = model()
        vm.checkKod("wrong"); runCurrent()
        assertEquals(SignInState.Failed(R.string.profile_in_error_kod), vm.state.first())
        request = { ResponseJwtEntity(profile, true, 200) }
        vm.checkKod("right"); runCurrent()
        assertEquals(SignInState.Confirmation(profile), vm.state.value)
    }

    @Test fun clearedViewModelCancelsTheNetworkRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<ResponseJwtEntity>()
        var finished = false
        request = { try { gate.await() } finally { finished = true } }
        val vm = model()
        vm.checkKod("code"); runCurrent()
        models.clear(); runCurrent()
        assertTrue(finished)
        assertEquals(0, writes)
    }
}
