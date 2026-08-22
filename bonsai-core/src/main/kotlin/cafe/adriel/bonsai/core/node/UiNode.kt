package cafe.adriel.bonsai.core.node

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import cafe.adriel.bonsai.core.BonsaiScope

@Composable
internal fun <T> BonsaiScope<T>.Node(node: Node<T>) {
    val offsetY = style.nodeOffsetY(node)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.zIndex(if (offsetY != 0f) 1f else 0f)
                .graphicsLayer { translationY = offsetY }
                .fillMaxWidth()
                .padding(vertical = style.nodeSpacing)
                .padding(start = node.depth * style.toggleIconSize),
    ) {
        ToggleIcon(node)
        NodeContent(node, this)
    }
}

@Composable
private fun <T> BonsaiScope<T>.ToggleIcon(node: Node<T>) {
    val toggleIcon = style.toggleIcon(node) ?: return

    if (node is BranchNode) {
        // The base icon points toward the reading direction's "forward" side (right in LTR) when
        // collapsed, then rotates to point down when expanded. In RTL that base direction is
        // mirrored (left instead of right), and the rotation to "pointing down" runs the opposite
        // way, so both the resting angle and the rotation sign flip together.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val restingDegrees = if (isRtl) 180f else 0f
        val expandRotation =
            if (isRtl) -style.toggleIconRotationDegrees else style.toggleIconRotationDegrees
        val rotationDegrees by
            animateFloatAsState(restingDegrees + if (node.isExpanded) expandRotation else 0f)

        Image(
            painter = toggleIcon,
            contentDescription = if (node.isExpanded) "Collapse node" else "Expand node",
            colorFilter = style.toggleIconColorFilter,
            modifier =
                Modifier.clip(style.toggleShape)
                    .clickable { expandableManager.toggleExpansion(node) }
                    .size(style.nodeIconSize)
                    .requiredSize(style.toggleIconSize)
                    .rotate(rotationDegrees),
        )
    } else {
        Spacer(Modifier.size(style.nodeIconSize))
    }
}

@Composable
private fun <T> BonsaiScope<T>.NodeContent(node: Node<T>, rowScope: RowScope) {
    val backgroundColor =
        if (node.isSelected) style.nodeSelectedBackgroundColor else style.nodeBackgroundColor(node)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            with(rowScope) { Modifier.weight(1f) }
                .clip(style.nodeShape)
                .run {
                    if (backgroundColor.isSpecified) background(backgroundColor, style.nodeShape)
                    else this
                }
                .then(clickableNode(node))
                .padding(style.nodePadding)
                .defaultMinSize(minHeight = style.nodeIconSize),
    ) {
        with(node) {
            iconComponent(node)
            nameComponent(node)
        }
    }
}

@SuppressLint("ModifierFactoryExtensionFunction", "ComposableModifierFactory")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> BonsaiScope<T>.clickableNode(node: Node<T>) =
    if (onLongClick == null && onDoubleClick == null) {
        Modifier.clickable { onClick?.invoke(node) }
    } else {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke(node) },
            onDoubleClick = { onDoubleClick?.invoke(node) },
            onLongClick = { onLongClick?.invoke(node) },
        )
    }

@Composable
internal fun <T> BonsaiScope<T>.DefaultNodeIcon(node: Node<T>) {
    val (icon, colorFilter) =
        if (node is BranchNode && node.isExpanded) {
            style.nodeExpandedIcon(node) to style.nodeExpandedIconColorFilter
        } else {
            style.nodeCollapsedIcon(node) to style.nodeCollapsedIconColorFilter
        }

    if (icon != null) {
        Image(
            painter = icon,
            colorFilter = colorFilter,
            contentDescription = node.name,
        )
    }
}

@Composable
internal fun <T> BonsaiScope<T>.DefaultNodeName(node: Node<T>) {
    BasicText(
        text = node.name,
        style = style.nodeNameTextStyle,
        modifier = Modifier.padding(start = style.nodeNameStartPadding),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
