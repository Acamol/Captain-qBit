package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The filter drawer (Compose port of `ServerDrawerController`). Status / category / tracker / tag
 * sections with absolute per-row counts. Categories with "/" group into a collapsible tree; a path
 * segment that is only a synthetic parent still filters its whole subtree (see [matchesCategory]).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ServerDrawer(
    state: ServerScreenState,
    collapsedPaths: MutableList<String>,
    onServerPicker: () -> Unit,
    onStats: () -> Unit,
    onLogs: () -> Unit,
    onFilter: (StateFilter) -> Unit,
    onCategory: (String?) -> Unit,
    onCategoryLongPress: (String) -> Unit,
    onManageCategories: () -> Unit,
    onTracker: (String?) -> Unit,
    onFilterUntagged: (Boolean) -> Unit,
    onToggleTag: (String) -> Unit,
    onTagLongPress: (String) -> Unit,
    onManageTags: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleSpeedLimits: () -> Unit,
    onGlobalLimits: () -> Unit,
    onAltLimits: () -> Unit,
    onSettings: () -> Unit,
) {
    val torrents = state.data?.torrents?.values?.toList() ?: emptyList()
    val total = torrents.size
    val tree = remember(state.availableCategories) { buildCategoryTree(state.availableCategories) }
    val flat = flattenCategoryTree(tree, collapsedPaths.toSet())

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)
        ) {
            // Header: server switcher + statistics
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .combinedClickable(onClick = onServerPicker)
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.serverName ?: "Servers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(" ▾", fontSize = 12.sp)
                }
                TooltipIconButton(
                    label = "Server logs",
                    icon = Icons.Filled.Description,
                    onClick = onLogs,
                    position = TooltipAnchorPosition.Below,
                )
                TooltipIconButton(
                    label = "Statistics",
                    icon = Icons.Filled.BarChart,
                    onClick = onStats,
                    position = TooltipAnchorPosition.Below,
                )
            }

            // Status
            SectionHeader("Status")
            StateFilter.entries.forEach { filter ->
                SidebarItem(
                    text = filter.label,
                    selected = filter == state.selectedFilter,
                    count = torrents.count { it.matchesFilter(filter) },
                    onClick = {
                        onFilter(if (filter == state.selectedFilter) StateFilter.ALL else filter)
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Categories
            SectionHeaderWithAction("Categories", onManageCategories)
            SidebarItem(
                text = "All",
                selected = state.selectedCategory == null,
                count = total,
                onClick = { onCategory(null) },
            )
            flat.forEach { node ->
                val isReal = node.path in state.availableCategories
                SidebarItem(
                    text = node.name,
                    selected = node.path == state.selectedCategory,
                    count = torrents.count { it.matchesCategory(node.path) },
                    indent = node.depth,
                    chevron = if (node.children.isNotEmpty()) node.path in collapsedPaths else null,
                    onChevron = {
                        if (!collapsedPaths.remove(node.path)) collapsedPaths.add(node.path)
                    },
                    onClick = {
                        onCategory(if (node.path == state.selectedCategory) null else node.path)
                    },
                    onLongClick = if (isReal) ({ onCategoryLongPress(node.path) }) else null,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Tags
            SectionHeaderWithAction("Tags", onManageTags)
            val noneSelected = !state.filterUntagged && state.selectedTags.isEmpty()
            SidebarItem(text = "All", selected = noneSelected, count = total) {
                onFilterUntagged(false)
            }
            SidebarItem(
                text = "Untagged",
                selected = state.filterUntagged,
                count = torrents.count { it.tags.isEmpty() },
                onClick = { onFilterUntagged(!state.filterUntagged) },
            )
            state.availableTags.forEach { tag ->
                SidebarItem(
                    text = tag,
                    selected = state.selectedTags.contains(tag),
                    count = torrents.count { it.tags.contains(tag) },
                    onClick = { onToggleTag(tag) },
                    onLongClick = { onTagLongPress(tag) },
                )
            }

            // Trackers
            if (state.availableTrackers.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Trackers")
                SidebarItem(
                    text = "All",
                    selected = state.selectedTracker == null,
                    count = total,
                    onClick = { onTracker(null) },
                )
                state.availableTrackers.forEach { host ->
                    SidebarItem(
                        text = host,
                        selected = host == state.selectedTracker,
                        count = torrents.count { it.matchesTracker(host) },
                        onClick = { onTracker(if (host == state.selectedTracker) null else host) },
                    )
                }
            }

            // Footer
            FilledTonalButton(
                onClick = onClearFilters,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Clear all")
            }
            HorizontalDivider()
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = onToggleSpeedLimits)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use alternate speed limits", Modifier.weight(1f))
                Switch(
                    checked = state.speedLimitMode != 0,
                    onCheckedChange = { onToggleSpeedLimits() },
                )
            }
            Text(
                "Global speed limits…",
                fontSize = 14.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = onGlobalLimits)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
            )
            Text(
                "Alternate speed limits…",
                fontSize = 14.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = onAltLimits)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
            )
            Text(
                "Settings",
                fontSize = 14.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = onSettings)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeaderWithAction(title: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TooltipIconButton(
            label = "Manage $title",
            icon = Icons.Filled.Edit,
            onClick = onAction,
            iconModifier = Modifier.width(20.dp),
            position = TooltipAnchorPosition.Below,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarItem(
    text: String,
    selected: Boolean,
    count: Int,
    indent: Int = 0,
    chevron: Boolean? = null,
    onChevron: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val seed = MaterialTheme.colorScheme.primary
    val textColor = if (selected) seed else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp)
                .fillMaxHeight()
                .background(if (selected) seed else Color.Transparent)
        )
        Spacer(Modifier.width(12.dp))
        if (indent > 0) Spacer(Modifier.width((indent * 16).dp))
        if (chevron != null) {
            Text(
                if (chevron) "▸" else "▾",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.width(20.dp)
                        .then(
                            if (onChevron != null) Modifier.combinedClickable { onChevron() }
                            else Modifier
                        ),
            )
        }
        Text(
            text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            count.toString(),
            color = textColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

// --- Category tree (ported from ServerDrawerController) ---

class CategoryTreeNode(val path: String, val name: String, val depth: Int) {
    val children = mutableListOf<CategoryTreeNode>()
}

fun buildCategoryTree(categories: List<String>): List<CategoryTreeNode> {
    val nodesByPath = LinkedHashMap<String, CategoryTreeNode>()
    for (category in categories) {
        if (category.isBlank()) continue
        var path = ""
        category.split("/").forEachIndexed { depth, segment ->
            val parentPath = path
            path = if (path.isEmpty()) segment else "$path/$segment"
            nodesByPath.getOrPut(path) {
                CategoryTreeNode(path, segment, depth).also { node ->
                    if (parentPath.isNotEmpty()) nodesByPath.getValue(parentPath).children.add(node)
                }
            }
        }
    }
    for (node in nodesByPath.values) node.children.sortBy { it.name.lowercase() }
    return nodesByPath.values.filter { it.depth == 0 }.sortedBy { it.name.lowercase() }
}

fun flattenCategoryTree(
    roots: List<CategoryTreeNode>,
    collapsedPaths: Set<String>,
): List<CategoryTreeNode> {
    val out = mutableListOf<CategoryTreeNode>()
    fun visit(nodes: List<CategoryTreeNode>) {
        for (node in nodes) {
            out.add(node)
            if (node.children.isNotEmpty() && node.path !in collapsedPaths) visit(node.children)
        }
    }
    visit(roots)
    return out
}
