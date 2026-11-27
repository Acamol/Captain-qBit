package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashgarg.qbit.common.R as CommonR

/** Container and content colors of a category chip for [seed], tonal to the current theme. */
@Composable
fun categoryChipColors(seed: Int?): Pair<Color, Color> {
    if (seed == null) {
        return MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chip = remember(seed, dark) { CategoryColors.chip(seed, dark) }
    return Color(chip.container) to Color(chip.content)
}

@Composable
fun CategoryChip(text: String, seed: Int?, modifier: Modifier = Modifier) {
    val (container, content) = categoryChipColors(seed)
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Text(
            text,
            color = content,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Color choices for one category: None (the default chip), Auto (app-picked, as distinct as
 * possible from the other categories), the preset palette, or a Custom hue/intensity/hex color.
 */
@Composable
fun CategoryColorPicker(
    name: String,
    color: Int?,
    auto: Boolean,
    onNone: () -> Unit,
    onAuto: (another: Boolean) -> Unit,
    onColor: (Int) -> Unit,
) {
    val custom = !auto && color != null && color !in CategoryColors.presets
    var editing by remember(name) { mutableStateOf(custom) }
    val start = remember(name) { CategoryColors.toLch(color ?: CategoryColors.presets[7]) }
    var hue by remember(name) { mutableFloatStateOf(start.h.toFloat()) }
    var chroma by
        remember(name) {
            mutableFloatStateOf(start.c.coerceAtMost(CategoryColors.MAX_CHROMA).toFloat())
        }
    var lightness by
        remember(name) {
            mutableFloatStateOf(
                start.l
                    .coerceIn(CategoryColors.MIN_LIGHTNESS, CategoryColors.MAX_LIGHTNESS)
                    .toFloat(),
            )
        }
    var draft by remember(name) { mutableIntStateOf(color ?: CategoryColors.presets[7]) }
    var hex by remember(name) { mutableStateOf(CategoryColors.toHex(draft)) }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(CommonR.string.category_color_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            CategoryChip(name, if (editing) draft else color)
        }
        FlowRow(Modifier.selectableGroup()) {
            Swatch(
                label = stringResource(CommonR.string.none_option),
                fill = null,
                icon = Icons.Outlined.Block,
                selected = color == null && !editing,
                onClick = {
                    editing = false
                    onNone()
                },
            )
            Swatch(
                label = stringResource(CommonR.string.category_color_auto),
                fill = if (auto) CategoryColors.swatch(color!!) else null,
                icon = Icons.Outlined.AutoAwesome,
                selected = auto && !editing,
                onClick = {
                    editing = false
                    if (!auto) onAuto(false)
                },
            )
            CategoryColors.presets.forEach { preset ->
                Swatch(
                    label =
                        "${stringResource(CommonR.string.category_color_title)} ${CategoryColors.toHex(preset)}",
                    fill = preset,
                    selected = !auto && !editing && color == preset,
                    onClick = {
                        editing = false
                        onColor(preset)
                    },
                )
            }
            Swatch(
                label = stringResource(CommonR.string.category_color_custom),
                fill = if (custom) CategoryColors.swatch(color!!) else null,
                icon = Icons.Outlined.Tune,
                selected = editing || custom,
                onClick = { editing = true },
            )
        }
        if (auto && !editing) {
            TextButton(onClick = { onAuto(true) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(
                    stringResource(CommonR.string.category_color_pick_another),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
        if (editing) {
            fun fromSliders() {
                draft =
                    CategoryColors.fromLch(
                        lightness.toDouble(),
                        chroma.toDouble(),
                        hue.toDouble(),
                    )
                hex = CategoryColors.toHex(draft)
            }
            Text(
                stringResource(CommonR.string.category_color_hue),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            HueBar()
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    fromSliders()
                },
                valueRange = 0f..359f,
            )
            Text(
                stringResource(CommonR.string.category_color_intensity),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = chroma,
                onValueChange = {
                    chroma = it
                    fromSliders()
                },
                valueRange = 0f..CategoryColors.MAX_CHROMA.toFloat(),
            )
            Text(
                stringResource(CommonR.string.category_color_brightness),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = lightness,
                onValueChange = {
                    lightness = it
                    fromSliders()
                },
                valueRange =
                    CategoryColors.MIN_LIGHTNESS.toFloat()..CategoryColors.MAX_LIGHTNESS.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        CategoryColors.parseHex(text)?.let {
                            draft = it
                            val lch = CategoryColors.toLch(it)
                            hue = lch.h.toFloat()
                            chroma = lch.c.coerceAtMost(CategoryColors.MAX_CHROMA).toFloat()
                            lightness =
                                lch.l
                                    .coerceIn(
                                        CategoryColors.MIN_LIGHTNESS,
                                        CategoryColors.MAX_LIGHTNESS,
                                    )
                                    .toFloat()
                        }
                    },
                    label = { Text(stringResource(CommonR.string.category_color_hex)) },
                    isError = CategoryColors.parseHex(hex) == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = CategoryColors.parseHex(hex) != null,
                    onClick = {
                        editing = false
                        onColor(draft)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(CommonR.string.apply))
                }
            }
        }
    }
}

@Composable
private fun HueBar() {
    val stops = remember {
        (0..360 step 30).map {
            Color(CategoryColors.fromLch(CategoryColors.SWATCH_LIGHTNESS, 0.14, it.toDouble()))
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(stops)),
    )
}

@Composable
private fun Swatch(
    label: String,
    fill: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val fillColor = fill?.let { Color(it) }
    Box(
        Modifier.size(48.dp)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .padding(6.dp)
            .clip(CircleShape)
            .let { if (fillColor != null) it.background(fillColor) else it }
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) onSurface else outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint =
                    when {
                        fillColor == null -> outline
                        fillColor.luminance() > 0.4f -> Color.Black
                        else -> Color.White
                    },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
