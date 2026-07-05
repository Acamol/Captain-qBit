package dev.yashgarg.qbit.ui.version

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.BuildConfig
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.VersionFragmentBinding
import dev.yashgarg.qbit.utils.viewBinding

@AndroidEntryPoint
class VersionFragment : Fragment(R.layout.version_fragment) {
    private val binding by viewBinding(VersionFragmentBinding::bind)
    private val viewModel by viewModels<VersionViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbar.setNavigationOnClickListener { it.findNavController().navigateUp() }
            composeView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            composeView.setContent {
                val state by viewModel.uiState.collectAsState()

                Mdc3Theme(setDefaultFontFamily = true) { AboutView(state) }
            }
        }
    }
}

@Composable
fun AboutView(state: VersionState) {
    val context = LocalContext.current
    val secondary = colorResource(R.color.grey)
    val accent = colorResource(R.color.md_theme_dark_seed)

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                    style = TextStyle(fontSize = 14.sp, color = secondary)
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
