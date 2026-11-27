package dev.yashgarg.qbit.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.ui.compose.ModalBottomSheet

/**
 * Torrent-only filters with live counts. Categories with "/" group into a collapsible tree; a path
 * segment that is only a synthetic parent still filters its whole subtree (see [matchesCategory]).
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun TorrentFilterSheet(
    state: ServerScreenState,
    collapsedPaths: MutableList<String>,
    onDismissRequest: () -> Unit,
    onFilter: (StateFilter) -> Unit,
    onCategory: (String?) -> Unit,
    onTracker: (String?) -> Unit,
    onFilterUntagged: (Boolean) -> Unit,
    onToggleTag: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val torrents =
        remember(state.data?.torrents) {
            state.data?.torrents?.values?.toList().orEmpty()
        }
    val total = torrents.size
    val tree = remember(state.availableCategories) { buildCategoryTree(state.availableCategories) }
    val collapsed = collapsedPaths.toSet()
    val flat = remember(tree, collapsed) { flattenCategoryTree(tree, collapsed) }
    val allLabel = stringResource(CommonR.string.all_label)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text(
                stringResource(CommonR.string.filters_label),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "status") {
                    val counts =
                        remember(torrents) {
                            StateFilter.entries.associateWith { filter ->
                                torrents.count { it.matchesFilter(filter) }
                            }
                        }
                    SectionHeader(stringResource(CommonR.string.status_section_title))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StateFilter.entries
                            .filter { it != StateFilter.QUEUED || state.queueingEnabled }
                            .forEach { filter ->
                                FilterChip(
                                    label = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(stringResource(filter.labelRes))
                                            Text(counts.getValue(filter).toString())
                                        }
                                    },
                                    selected = filter == state.selectedFilter,
                                    onClick = {
                                        onFilter(
                                            if (filter == state.selectedFilter) StateFilter.ALL
                                            else filter,
                                        )
                                    },
                                )
                            }
                    }
                }

                item(key = "categories") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    SectionHeader(stringResource(CommonR.string.categories_section_title))
                    SidebarItem(
                        text = allLabel,
                        selected = state.selectedCategory == null,
                        count = total,
                        onClick = { onCategory(null) },
                    )
                }
                items(flat, key = { "category:${it.path}" }, contentType = { "filter" }) { node ->
                    val count =
                        remember(torrents, node.path) {
                            torrents.count { it.matchesCategory(node.path) }
                        }
                    SidebarItem(
                        text = node.name,
                        selected = node.path == state.selectedCategory,
                        count = count,
                        indent = node.depth,
                        chevron =
                            if (node.children.isNotEmpty()) node.path in collapsedPaths else null,
                        onChevron = {
                            if (!collapsedPaths.remove(node.path)) collapsedPaths.add(node.path)
                        },
                        onClick = {
                            onCategory(if (node.path == state.selectedCategory) null else node.path)
                        },
                    )
                }

                item(key = "tags") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    SectionHeader(stringResource(CommonR.string.tags_section_title))
                    val noneSelected = !state.filterUntagged && state.selectedTags.isEmpty()
                    SidebarItem(text = allLabel, selected = noneSelected, count = total) {
                        onFilterUntagged(false)
                    }
                    val untagged = remember(torrents) { torrents.count { it.tags.isEmpty() } }
                    SidebarItem(
                        text = stringResource(CommonR.string.untagged_label),
                        selected = state.filterUntagged,
                        count = untagged,
                        onClick = { onFilterUntagged(!state.filterUntagged) },
                    )
                }
                items(state.availableTags, key = { "tag:$it" }, contentType = { "filter" }) { tag ->
                    val count = remember(torrents, tag) { torrents.count { it.tags.contains(tag) } }
                    SidebarItem(
                        text = tag,
                        selected = state.selectedTags.contains(tag),
                        count = count,
                        onClick = { onToggleTag(tag) },
                    )
                }

                if (state.availableTrackers.isNotEmpty()) {
                    item(key = "trackers") {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        SectionHeader(stringResource(CommonR.string.trackers_section_title))
                        SidebarItem(
                            text = allLabel,
                            selected = state.selectedTracker == null,
                            count = total,
                            onClick = { onTracker(null) },
                        )
                    }
                    items(
                        state.availableTrackers,
                        key = { "tracker:$it" },
                        contentType = { "filter" },
                    ) { host ->
                        SidebarItem(
                            text = host,
                            selected = host == state.selectedTracker,
                            count = state.trackerCounts.getValue(host),
                            onClick = {
                                onTracker(if (host == state.selectedTracker) null else host)
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(CommonR.string.clear_all_action))
                }
                Button(onClick = onDismissRequest) {
                    Text(stringResource(CommonR.string.filters_done_action))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarItem(
    text: String,
    selected: Boolean,
    count: Int,
    indent: Int = 0,
    chevron: Boolean? = null,
    onChevron: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val textColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                )
                .heightIn(min = 48.dp)
                .semantics { this.selected = selected }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (indent > 0) Spacer(Modifier.width((indent * 16).dp))
        Text(
            text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (chevron != null) {
            IconButton(onClick = { onChevron?.invoke() }) {
                Icon(
                    if (chevron) Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else Icons.Filled.ExpandMore,
                    contentDescription = text,
                    tint = textColor,
                )
            }
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
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
