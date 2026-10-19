package ru.ama.whereme.feature.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ama.whereme.domain.state.LoadState

@Composable
fun ProfileScreen(component: ProfileComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareChooser = stringResource(R.string.profile_share_chooser)
    var share by rememberSaveable { mutableStateOf(false) }
    var logout by rememberSaveable { mutableStateOf(false) }
    var externalError by rememberSaveable { mutableStateOf(false) }
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
            externalError = false
        } catch (_: ActivityNotFoundException) {
            externalError = true
        }
    }
    val profile =
        (state.session as? LoadState.Ready)?.value?.profile?.takeIf { it.tokenJwt.isNotBlank() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error ->
            Text(
                stringResource(
                    when (error) {
                        ProfileError.CODE -> R.string.profile_invalid_code
                        ProfileError.REQUEST -> R.string.profile_request_failed
                        ProfileError.SAVE -> R.string.profile_save_failed
                    }
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (externalError)
            Text(
                stringResource(R.string.profile_no_handler),
                color = MaterialTheme.colorScheme.error,
            )
        if (state.session == LoadState.Loading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.profile_loading))
        } else {
            if (state.session == LoadState.Failed) {
                Text(
                    stringResource(R.string.profile_read_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { component.accept(ProfileIntent.Retry) }) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
            if (profile == null) {
                Text(
                    stringResource(R.string.profile_local),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/myTestInfoBot")))
                    }
                ) {
                    Text(stringResource(R.string.profile_bot))
                }
                OutlinedTextField(
                    state.code,
                    { component.accept(ProfileIntent.Code(it)) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.profile_code)) },
                    singleLine = true,
                    isError = state.error == ProfileError.CODE,
                )
                Button(
                    onClick = { component.accept(ProfileIntent.Check) },
                    enabled = !state.busy && state.code.isNotBlank(),
                ) {
                    Text(stringResource(R.string.profile_check))
                }
            } else {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(profile.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(
                                if (profile.isActivate) R.string.profile_active
                                else R.string.profile_inactive
                            )
                        )
                    }
                }
                Button(onClick = { share = true }, enabled = !profile.url.isNullOrBlank()) {
                    Text(stringResource(R.string.profile_share))
                }
                OutlinedButton(onClick = { logout = true }, enabled = !state.busy) {
                    Text(stringResource(R.string.profile_logout))
                }
            }
        }
    }
    state.confirmation?.let { confirmed ->
        AlertDialog(
            onDismissRequest = { component.accept(ProfileIntent.DismissConfirmation) },
            title = { Text(stringResource(R.string.profile_confirm_title)) },
            text = { Text(confirmed.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { component.accept(ProfileIntent.Confirm) }) {
                    Text(stringResource(R.string.profile_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { component.accept(ProfileIntent.DismissConfirmation) }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            },
        )
    }
    if (share && profile != null)
        AlertDialog(
            onDismissRequest = { share = false },
            title = { Text(stringResource(R.string.profile_share_title)) },
            text = { Text(stringResource(R.string.profile_share_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        share = false
                        val intent =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://kol.hhos.ru/gkk/map.php?wm=${profile.url}",
                                )
                        open(
                            Intent.createChooser(
                                intent,
                                shareChooser,
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.profile_share))
                }
            },
            dismissButton = {
                TextButton(onClick = { share = false }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            },
        )
    if (logout && profile != null)
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        logout = false
                        component.accept(ProfileIntent.LogOut)
                    }
                ) {
                    Text(stringResource(R.string.profile_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { logout = false }) {
                    Text(stringResource(R.string.profile_cancel))
                }
            },
        )
}
