package ru.ama.whereme.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.retainedComponent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.ama.whereme.BuildConfig
import ru.ama.whereme.R
import ru.ama.whereme.domain.entity.ThemeMode
import ru.ama.whereme.domain.state.LoadState
import ru.ama.whereme.navigation.RootFactory
import ru.ama.whereme.ui.RootScreen
import ru.ama.whereme.ui.theme.WhereMeTheme
import ru.ama.whereme.ui.theme.isDark

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var rootFactory: RootFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = retainedComponent(factory = rootFactory::create)
        setContent {
            val theme by root.theme.collectAsStateWithLifecycle()
            val dark = ((theme as? LoadState.Ready)?.value ?: ThemeMode.SYSTEM).isDark()
            SideEffect {
                val bars =
                    if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.rgb(16, 25, 20))
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            WhereMeTheme(dark) {
                Surface(Modifier.fillMaxSize()) {
                    if (theme == LoadState.Loading)
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    else if (theme == LoadState.Failed)
                        Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                            Text(
                                stringResource(R.string.storage_read_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = root::retryStorage) {
                                Text(stringResource(R.string.storage_retry))
                            }
                            RootScreen(
                                root,
                                BuildConfig.VERSION_NAME,
                                R.raw.aboutlibraries,
                                Modifier.weight(1f),
                            )
                        }
                    else RootScreen(root, BuildConfig.VERSION_NAME, R.raw.aboutlibraries)
                }
            }
        }
    }
}
