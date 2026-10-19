package ru.ama.whereme.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ama.whereme.domain.reportFailure
import ru.ama.whereme.domain.state.LoadState

@Composable
fun AboutScreen(
    component: AboutComponent,
    appName: String,
    version: String,
    @RawRes licensesResource: Int,
    modifier: Modifier = Modifier,
) {
    val licenses by component.licenses.collectAsStateWithLifecycle()
    if (licenses) {
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = { component.showLicenses(false) }) {
                Text(stringResource(R.string.about_back))
            }
            Text(
                stringResource(R.string.about_licenses),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            LicenseList(licensesResource, Modifier.weight(1f))
        }
    } else {
        val context = LocalContext.current
        var failed by remember { mutableStateOf(false) }
        fun link(uri: String) {
            try {
                context.startActivity(
                    Intent(
                        if (uri.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW,
                        Uri.parse(uri),
                    )
                )
                failed = false
            } catch (_: ActivityNotFoundException) {
                failed = true
            }
        }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(appName, style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.about_version, version))
                }
            }
            Text(stringResource(R.string.about_description))
            Text(
                stringResource(R.string.about_network),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Button(onClick = { component.showLicenses(true) }) {
                Text(stringResource(R.string.about_licenses))
            }
            Text(
                stringResource(R.string.about_licenses_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            TextButton(onClick = { link("https://kol.hhos.ru/gkk/privacy.html") }) {
                Text(stringResource(R.string.about_privacy))
            }
            TextButton(onClick = { link("https://kol.hhos.ru/gkk/user.html") }) {
                Text(stringResource(R.string.about_agreement))
            }
            TextButton(onClick = { link("mailto:10ama@mail.ru") }) {
                Text(stringResource(R.string.about_contact))
            }
            if (failed)
                Text(
                    stringResource(R.string.about_link_error),
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }
}

@Composable
private fun LicenseList(@RawRes resource: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val state by
        produceState<LoadState<Libs>>(LoadState.Loading, resource, retry) {
            value = LoadState.Loading
            value =
                try {
                    LoadState.Ready(
                        withContext(Dispatchers.IO) {
                            Libs.Builder().withJson(context, resource).build()
                        }
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    reportFailure("Read licenses", failure)
                    LoadState.Failed
                }
        }
    when (val current = state) {
        is LoadState.Ready ->
            LibrariesContainer(
                libraries = current.value,
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                licenseDialogConfirmText = stringResource(R.string.about_license_close),
            )
        LoadState.Loading -> Box(modifier.padding(24.dp)) { CircularProgressIndicator() }
        LoadState.Failed ->
            Column(modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.about_license_error),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.about_retry)) }
            }
    }
}
