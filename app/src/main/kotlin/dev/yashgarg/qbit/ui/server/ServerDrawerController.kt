package dev.yashgarg.qbit.ui.server

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.databinding.ServerFragmentBinding

/**
 * Builds and updates the navigation drawer's status/category/tracker/tags sections. Categories with
 * "/" in their name (e.g. "books/audiobooks") are grouped into a directory-like tree. A path
 * segment that never appears as an actual category (e.g. "books" when only "books/audiobooks"
 * exists) still becomes a synthetic, clickable parent node - selecting it filters to that whole
 * subtree (see matchesCategory in ServerViewModel).
 */
class ServerDrawerController(
    private val fragment: Fragment,
    private val binding: ServerFragmentBinding,
    private val viewModel: ServerViewModel,
    private val onCategoryLongPress: (String) -> Unit,
    private val onTagLongPress: (String) -> Unit,
) {
    private var lastDrawerTrackers: List<String?> = emptyList()
    private var lastDrawerTags: List<String> = emptyList()
    private var lastDrawerState: ServerScreenState? = null
    private val collapsedCategoryPaths = mutableSetOf<String>()

    // Clicking an already-selected exclusive filter reverts to `default`. Always reads the live
    // selection from the ViewModel rather than a closure-captured ServerScreenState: the tracker
    // drawer section only recolors existing rows on selection change instead of rebuilding them,
    // which would otherwise leave click listeners capturing a stale selection.
    private fun <T> toggleSelection(clicked: T, default: T, current: () -> T): T =
        if (clicked == current()) default else clicked

    private class CategoryTreeNode(val path: String, val name: String, val depth: Int) {
        val children = mutableListOf<CategoryTreeNode>()
    }

    private fun buildCategoryTree(categories: List<String>): List<CategoryTreeNode> {
        val nodesByPath = LinkedHashMap<String, CategoryTreeNode>()
        for (category in categories) {
            if (category.isBlank()) continue
            var path = ""
            category.split("/").forEachIndexed { depth, segment ->
                val parentPath = path
                path = if (path.isEmpty()) segment else "$path/$segment"
                nodesByPath.getOrPut(path) {
                    CategoryTreeNode(path, segment, depth).also { node ->
                        if (parentPath.isNotEmpty()) {
                            nodesByPath.getValue(parentPath).children.add(node)
                        }
                    }
                }
            }
        }
        for (node in nodesByPath.values) {
            node.children.sortBy { it.name.lowercase() }
        }
        return nodesByPath.values.filter { it.depth == 0 }.sortedBy { it.name.lowercase() }
    }

    private fun flattenCategoryTree(
        roots: List<CategoryTreeNode>,
        collapsedPaths: Set<String>,
    ): List<CategoryTreeNode> {
        val out = mutableListOf<CategoryTreeNode>()
        fun visit(nodes: List<CategoryTreeNode>) {
            for (node in nodes) {
                out.add(node)
                if (node.children.isNotEmpty() && node.path !in collapsedPaths) {
                    visit(node.children)
                }
            }
        }
        visit(roots)
        return out
    }

    private val Int.dpPx
        get() = (this * fragment.resources.displayMetrics.density).toInt()

    private fun sidebarItem(
        text: String,
        selected: Boolean,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): View {
        val ctx = fragment.requireContext()
        val density = fragment.resources.displayMetrics.density
        val indicatorW = (3 * density).toInt()
        val itemPadV = (10 * density).toInt()
        val gapH = (12 * density).toInt()
        val seedColor = ctx.getColor(R.color.md_theme_dark_seed)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }

            addView(
                View(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(indicatorW, MATCH_PARENT).also {
                            it.marginEnd = gapH
                        }
                    setBackgroundColor(if (selected) seedColor else Color.TRANSPARENT)
                }
            )
            addView(
                TextView(ctx).apply {
                    this.text = text
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    setPadding(0, itemPadV, 16.dpPx, itemPadV)
                    textSize = 14f
                    setTextColor(if (selected) Color.WHITE else 0x80FFFFFF.toInt())
                    if (selected) setTypeface(typeface, Typeface.BOLD)
                }
            )
        }
    }

    private fun categoryTreeItem(
        node: CategoryTreeNode,
        selected: Boolean,
        collapsed: Boolean,
        onClick: () -> Unit,
        onToggleCollapse: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val ctx = fragment.requireContext()
        val density = fragment.resources.displayMetrics.density
        val indicatorW = (3 * density).toInt()
        val itemPadV = (10 * density).toInt()
        val gapH = (12 * density).toInt()
        val indentPerDepth = (16 * density).toInt()
        val chevronW = (20 * density).toInt()
        val seedColor = ctx.getColor(R.color.md_theme_dark_seed)
        val hasChildren = node.children.isNotEmpty()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }

            addView(
                View(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(indicatorW, MATCH_PARENT).also {
                            it.marginEnd = gapH
                        }
                    setBackgroundColor(if (selected) seedColor else Color.TRANSPARENT)
                }
            )
            addView(
                View(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(node.depth * indentPerDepth, MATCH_PARENT)
                }
            )
            addView(
                TextView(ctx).apply {
                    text = if (hasChildren) (if (collapsed) "▸" else "▾") else ""
                    layoutParams = LinearLayout.LayoutParams(chevronW, MATCH_PARENT)
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(0x80FFFFFF.toInt())
                    if (hasChildren) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onToggleCollapse() }
                    }
                }
            )
            addView(
                TextView(ctx).apply {
                    text = node.name
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    setPadding(0, itemPadV, 16.dpPx, itemPadV)
                    textSize = 14f
                    setTextColor(if (selected) Color.WHITE else 0x80FFFFFF.toInt())
                    if (selected) setTypeface(typeface, Typeface.BOLD)
                }
            )
        }
    }

    private fun rebuildSidebarItemSelection(itemView: View, itemValue: Any?, selectedValue: Any?) {
        val selected = itemValue == selectedValue
        val seedColor = fragment.requireContext().getColor(R.color.md_theme_dark_seed)
        (itemView as? LinearLayout)?.let { row ->
            row.getChildAt(0)?.setBackgroundColor(if (selected) seedColor else Color.TRANSPARENT)
            (row.getChildAt(1) as? TextView)?.apply {
                setTextColor(if (selected) Color.WHITE else 0x80FFFFFF.toInt())
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    fun update(state: ServerScreenState) {
        with(binding) {
            val trackers = listOf<String?>(null) + state.availableTrackers
            val tags = state.availableTags

            // Status — always rebuilt when selection changes (small fixed list)
            statusItemsContainer.removeAllViews()
            StateFilter.entries.forEach { filter ->
                statusItemsContainer.addView(
                    sidebarItem(filter.label, filter == state.selectedFilter) {
                        val next =
                            toggleSelection(filter, StateFilter.ALL) {
                                viewModel.uiState.value.selectedFilter
                            }
                        viewModel.setFilter(next)
                    }
                )
            }

            // Category — grouped into a tree by "/"; always rebuilt (small list, and collapse
            // state or selection can change the set of visible rows on any redraw). Header and
            // divider stay visible even with no categories so the manage (pencil) button - the
            // only way to create the first category - remains reachable.
            drawerCategoryHeader.visibility = View.VISIBLE
            drawerCategoryDivider.visibility = View.VISIBLE
            categoryItemsContainer.removeAllViews()
            categoryItemsContainer.addView(
                sidebarItem("All", state.selectedCategory == null) { viewModel.setCategory(null) }
            )
            val categoryTree = buildCategoryTree(state.availableCategories)
            flattenCategoryTree(categoryTree, collapsedCategoryPaths).forEach { node ->
                categoryItemsContainer.addView(
                    categoryTreeItem(
                        node = node,
                        selected = node.path == state.selectedCategory,
                        collapsed = node.path in collapsedCategoryPaths,
                        onClick = {
                            val next =
                                toggleSelection(node.path, null) {
                                    viewModel.uiState.value.selectedCategory
                                }
                            viewModel.setCategory(next)
                        },
                        onToggleCollapse = {
                            if (!collapsedCategoryPaths.add(node.path)) {
                                collapsedCategoryPaths.remove(node.path)
                            }
                            update(viewModel.uiState.value)
                        },
                        // Only real categories can be renamed/deleted - a "/" path segment can be
                        // a synthetic, non-existent parent node purely for tree grouping.
                        onLongClick =
                            if (node.path in state.availableCategories) {
                                { onCategoryLongPress(node.path) }
                            } else null,
                    )
                )
            }

            // Tracker
            if (trackers != lastDrawerTrackers) {
                lastDrawerTrackers = trackers
                val hasTrackers = state.availableTrackers.isNotEmpty()
                drawerTrackerHeader.visibility = if (hasTrackers) View.VISIBLE else View.GONE
                drawerTagsDivider.visibility = if (hasTrackers) View.VISIBLE else View.GONE
                trackerItemsContainer.removeAllViews()
                trackers.forEach { tracker ->
                    val label = tracker ?: "All"
                    trackerItemsContainer.addView(
                        sidebarItem(label, tracker == state.selectedTracker) {
                            val next =
                                toggleSelection(tracker, null) {
                                    viewModel.uiState.value.selectedTracker
                                }
                            viewModel.setTracker(next)
                        }
                    )
                }
            } else if (state.selectedTracker != lastDrawerState?.selectedTracker) {
                for (i in 0 until trackerItemsContainer.childCount) {
                    rebuildSidebarItemSelection(
                        trackerItemsContainer.getChildAt(i),
                        trackers.getOrNull(i),
                        state.selectedTracker,
                    )
                }
            }

            // Tags — always visible; "All" and "Untagged" are permanent entries
            val tagsStateChanged =
                tags != lastDrawerTags ||
                    state.selectedTags != lastDrawerState?.selectedTags ||
                    state.filterUntagged != lastDrawerState?.filterUntagged
            if (tagsStateChanged) {
                lastDrawerTags = tags
                drawerTagsHeader.visibility = View.VISIBLE
                tagsContainer.removeAllViews()
                val noneSelected = !state.filterUntagged && state.selectedTags.isEmpty()
                tagsContainer.addView(
                    sidebarItem("All", noneSelected) { viewModel.setFilterUntagged(false) }
                )
                tagsContainer.addView(
                    sidebarItem("Untagged", state.filterUntagged) {
                        val next =
                            toggleSelection(true, false) { viewModel.uiState.value.filterUntagged }
                        viewModel.setFilterUntagged(next)
                    }
                )
                tags.forEach { tag ->
                    tagsContainer.addView(
                        sidebarItem(
                            tag,
                            state.selectedTags.contains(tag),
                            onLongClick = { onTagLongPress(tag) },
                        ) {
                            viewModel.toggleTag(tag)
                        }
                    )
                }
            }

            lastDrawerState = state
        }
    }
}
