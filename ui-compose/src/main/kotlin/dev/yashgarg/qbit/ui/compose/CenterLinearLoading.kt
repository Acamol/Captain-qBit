package dev.yashgarg.qbit.ui.compose

import androidx.annotation.ColorRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLinearLoading(modifier: Modifier, @ColorRes color: Int) {
    Center(modifier) { LinearWavyProgressIndicator(color = colorResource(color)) }
}
