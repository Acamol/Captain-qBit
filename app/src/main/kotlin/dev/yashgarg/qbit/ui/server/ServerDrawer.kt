package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yashgarg.qbit.common.R as CommonR

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
) {
    val torrents = state.data?.torrents?.values?.toList() ?: emptyList()
    val total = torrents.size
    val tree = remember(state.availableCategories) { buildCategoryTree(state.availableCategories) }
    val flat = flattenCategoryTree(tree, collapsedPaths.toSet())

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(300.dp),
        // The drawer's own bounds are already correctly confined above the persistent
        // NavigationBar (via the outer Scaffold in QbitNavHost), so the default windowInsets here
        // would reserve the bottom system-nav-bar inset a second time, leaving a blank gap below
        // the pinned "Clear all" button. The top status-bar inset is still needed though - nothing
        // else protects the drawer's own header from it - so keep that one.
        windowInsets = WindowInsets.statusBars,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Header: server switcher + statistics
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
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
                            state.serverName ?: stringResource(CommonR.string.servers_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(" ▾", fontSize = 12.sp)
                    }
                    TooltipIconButton(
                        label = stringResource(CommonR.string.server_logs_action),
                        icon = Icons.Filled.Description,
                        onClick = onLogs,
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = stringResource(CommonR.string.statistics_action),
                        icon = Icons.Filled.BarChart,
                        onClick = onStats,
                        position = TooltipAnchorPosition.Below,
                    )
                }

                // Status
                SectionHeader(stringResource(CommonR.string.status_section_title))
                val allLabel = stringResource(CommonR.string.all_label)
                StateFilter.entries
                    // The Queued filter is only meaningful when the server has torrent queueing on.
                    .filter { it != StateFilter.QUEUED || state.queueingEnabled }
                    .forEach { filter ->
                        SidebarItem(
                            text = stringResource(filter.labelRes),
                            selected = filter == state.selectedFilter,
                            count = torrents.count { it.matchesFilter(filter) },
                            onClick = {
                                onFilter(
                                    if (filter == state.selectedFilter) StateFilter.ALL else filter
                                )
                            },
                        )
                    }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Categories
                SectionHeaderWithAction(
                    stringResource(CommonR.string.categories_section_title),
                    onManageCategories,
                )
                SidebarItem(
                    text = allLabel,
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
                        chevron =
                            if (node.children.isNotEmpty()) node.path in collapsedPaths else null,
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
                SectionHeaderWithAction(
                    stringResource(CommonR.string.tags_section_title),
                    onManageTags,
                )
                val noneSelected = !state.filterUntagged && state.selectedTags.isEmpty()
                SidebarItem(text = allLabel, selected = noneSelected, count = total) {
                    onFilterUntagged(false)
                }
                SidebarItem(
                    text = stringResource(CommonR.string.untagged_label),
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
                    SectionHeader(stringResource(CommonR.string.trackers_section_title))
                    SidebarItem(
                        text = allLabel,
                        selected = state.selectedTracker == null,
                        count = total,
                        onClick = { onTracker(null) },
                    )
                    state.availableTrackers.forEach { host ->
                        SidebarItem(
                            text = host,
                            selected = host == state.selectedTracker,
                            count = torrents.count { it.matchesTracker(host) },
                            onClick = {
                                onTracker(if (host == state.selectedTracker) null else host)
                            },
                        )
                    }
                }
            }
            // Footer, pinned to the bottom of the drawer instead of scrolling with the filters -
            // always reachable, and doesn't leave a blank gap when the filter list is short.
            HorizontalDivider()
            FilledTonalButton(
                onClick = onClearFilters,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(CommonR.string.clear_all_action))
            }
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
            label = stringResource(CommonR.string.manage_named_action, title),
            icon = Icons.Filled.Edit,
            onClick = onAction,
            modifier = Modifier.width(20.dp),
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
