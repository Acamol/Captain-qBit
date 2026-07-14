package dev.yashgarg.qbit.ui.version

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.BuildConfig
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(appNavigator: AppNavigator, viewModel: VersionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        AboutView(state, Modifier.padding(padding))
    }
}

@Composable
fun AboutView(state: VersionState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val secondary = colorResource(R.color.grey)
    val accent = colorResource(R.color.md_theme_dark_seed)

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Text(
            text = "Captain qBit",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(fontSize = 14.sp, color = secondary),
        )

        if (!state.loading) {
            Spacer(Modifier.height(24.dp))
            state.serverVersion?.let {
                Text(
                    text = "qBittorrent $it",
                    style = TextStyle(fontSize = 14.sp, color = secondary),
                )
            }
            state.apiVersion?.let {
                Text(
                    text = "Web API v$it",
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(fontSize = 14.sp, color = secondary),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "A fork of qBittorrent Manager by Yash Garg.",
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 14.sp, color = secondary),
        )
        Text(
            text = "Free and open source · GPL-3.0",
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 14.sp, color = secondary),
        )

        Spacer(Modifier.height(24.dp))
        val linkStyle =
            TextStyle(fontSize = 16.sp, color = accent, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Source code",
            modifier =
                Modifier.padding(vertical = 8.dp).clickable {
                    open("https://github.com/Acamol/Captain-qBit")
                },
            style = linkStyle,
        )
        Text(
            text = "Report an issue",
            modifier =
                Modifier.padding(vertical = 8.dp).clickable {
                    open("https://github.com/Acamol/Captain-qBit/issues")
                },
            style = linkStyle,
        )
    }
}
