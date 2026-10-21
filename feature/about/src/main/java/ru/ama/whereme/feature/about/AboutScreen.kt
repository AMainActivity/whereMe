package ru.ama.whereme.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
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
        LicenseList(licensesResource, modifier.fillMaxSize())
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
            Text(
                stringResource(R.string.about_information),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(Modifier.fillMaxWidth()) {
                AboutLink(
                    title = stringResource(R.string.about_licenses),
                    supporting = stringResource(R.string.about_licenses_hint),
                    onClick = { component.showLicenses(true) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AboutLink(stringResource(R.string.about_privacy)) { link(PRIVACY_URL) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AboutLink(stringResource(R.string.about_agreement)) { link(AGREEMENT_URL) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AboutLink(stringResource(R.string.about_contact)) { link(DEVELOPER_EMAIL_URI) }
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
                name = { Text(it, style = MaterialTheme.typography.titleMedium) },
                libraryModifier =
                    Modifier.clip(MaterialTheme.shapes.medium)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        ),
                colors =
                    LibraryDefaults.libraryColors(
                        libraryBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        libraryContentColor = MaterialTheme.colorScheme.onSurface,
                        dialogBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        dialogContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                divider = { Spacer(Modifier.height(12.dp)) },
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

@Composable
private fun AboutLink(title: String, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private const val PRIVACY_URL = "https://kol.hhos.ru/gkk/privacy.html"

private const val AGREEMENT_URL = "https://kol.hhos.ru/gkk/user.html"

private const val DEVELOPER_EMAIL_URI = "mailto:10ama@mail.ru"
