package dev.yashgarg.qbit.ui.server.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.common.R as CommonR
import dev.yashgarg.qbit.utils.toHumanReadable
import dev.yashgarg.qbit.utils.toTime
import javax.inject.Inject
import qbittorrent.models.Torrent

class TorrentListAdapter @Inject constructor() :
    ListAdapter<Torrent, TorrentListAdapter.TorrentItemViewHolder>(TorrentComparator()) {

    private var selectionTracker: SelectionTracker<String>? = null
    var onItemClick: ((String) -> Unit)? = null

    // App-local category -> color map (see ServerPreferences.categoryColors). Reassigned from a
    // ServerFragment collector; rebind so the category chips repaint.
    var categoryColors: Map<String, Int> = emptyMap()
        @android.annotation.SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun clearSelection() {
        selectionTracker?.clearSelection()
    }

    fun makeSelectable(recyclerView: RecyclerView, onSelection: (Selection<String>) -> Unit) {
        selectionTracker =
            SelectionTracker.Builder(
                    "SelectableTorrentListAdapter",
                    recyclerView,
                    itemKeyProvider,
                    TorrentItemDetailsLookup(recyclerView),
                    StorageStrategy.createStringStorage()
                )
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build()
                .apply {
                    addObserver(
                        object : SelectionTracker.SelectionObserver<String>() {
                            override fun onSelectionChanged() {
                                super.onSelectionChanged()
                                onSelection(selection)
                            }
                        }
                    )
                }
    }

    inner class TorrentItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView: MaterialCardView = view.findViewById(R.id.torrent_card)
        private val title: TextView = view.findViewById(R.id.torrentTitle)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val peers: TextView = view.findViewById(R.id.peers_tv)
        private val speed: TextView = view.findViewById(R.id.speed_tv)
        private val downloaded: TextView = view.findViewById(R.id.downloaded_percent)
        private val eta: TextView = view.findViewById(R.id.eta_tv)
        private val chipGroup: ChipGroup = view.findViewById(R.id.chip_group)

        val itemDetails: ItemDetailsLookup.ItemDetails<String> =
            object : ItemDetailsLookup.ItemDetails<String>() {
                override fun getPosition(): Int = absoluteAdapterPosition

                override fun getSelectionKey(): String = getItem(position).hash
            }

        fun bind(torrent: Torrent) {
            with(itemView) {
                title.text = torrent.name
                bindChips(torrent)
                speed.text =
                    String.format(
                        context.getString(CommonR.string.speed_status),
                        torrent.dlspeed.toHumanReadable(),
                        torrent.uploadSpeed.toHumanReadable(),
                    )
                progressBar.progress = (torrent.progress * 100).toInt()
                downloaded.text =
                    String.format(
                        context.getString(CommonR.string.percent_done),
                        torrent.downloaded.toLong().toHumanReadable(),
                        torrent.size.toHumanReadable(),
                        (torrent.progress * 100).toInt(),
                    )
                eta.text = if (torrent.eta == 8640000L) null else torrent.eta.toTime()

                cardView.apply {
                    selectionTracker?.let {
                        setOnClickListener { _ ->
                            if (!it.hasSelection()) onItemClick?.invoke(torrent.hash)
                        }

                        isChecked = it.isSelected(torrent.hash)
                    }
                }

                val progressColor: Int
                when (torrent.state) {
                    Torrent.State.PAUSED_DL,
                    Torrent.State.STOPPED_DL -> {
                        peers.text = context.getString(CommonR.string.stopped)
                        peers.setTextColor(context.getColor(R.color.grey))
                        speed.visibility = View.GONE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.grey)
                    }
                    Torrent.State.UPLOADING,
                    Torrent.State.FORCED_UP,
                    // A completed torrent with no active peers is still seeding — qBittorrent
                    // labels stalledUP "Seeding", not "Stalled".
                    Torrent.State.STALLED_UP -> {
                        peers.text = context.getString(CommonR.string.seeding)
                        peers.setTextColor(context.getColor(R.color.green))
                        speed.visibility = View.VISIBLE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.green)
                    }
                    Torrent.State.DOWNLOADING,
                    Torrent.State.FORCED_DL -> {
                        peers.text =
                            String.format(
                                context.getString(CommonR.string.seed_status),
                                torrent.connectedSeeds,
                                torrent.seedsInSwarm,
                            )
                        peers.setTextColor(
                            MaterialColors.getColor(
                                peers,
                                com.google.android.material.R.attr.colorPrimary,
                            )
                        )
                        speed.visibility = View.VISIBLE
                        eta.visibility = View.VISIBLE
                        progressColor =
                            MaterialColors.getColor(
                                context,
                                com.google.android.material.R.attr.colorPrimary,
                                context.getColor(R.color.md_theme_dark_seed),
                            )
                    }
                    Torrent.State.STALLED_DL -> {
                        peers.text = context.getString(CommonR.string.stalled)
                        peers.setTextColor(context.getColor(R.color.red))
                        speed.visibility = View.GONE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.red)
                    }
                    Torrent.State.PAUSED_UP,
                    Torrent.State.STOPPED_UP -> {
                        // A stopped, fully-downloaded torrent. Show it as paused/inactive (grey),
                        // not "Completed" in the accent colour — the full progress bar already
                        // conveys that it finished, and this keeps it distinct from seeding.
                        peers.text = context.getString(CommonR.string.stopped)
                        peers.setTextColor(context.getColor(R.color.grey))
                        speed.visibility = View.GONE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.grey)
                    }
                    Torrent.State.ERROR,
                    Torrent.State.MISSING_FILES -> {
                        peers.text =
                            if (torrent.state == Torrent.State.MISSING_FILES)
                                context.getString(CommonR.string.state_missing_files)
                            else context.getString(CommonR.string.state_errored)
                        peers.setTextColor(context.getColor(R.color.red))
                        speed.visibility = View.GONE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.red)
                    }
                    // Transient/working states: metadata download, allocation, moving files, and
                    // the various consistency checks. No transfer to show, so speed/eta are hidden.
                    Torrent.State.META_DL,
                    Torrent.State.FORCED_META_DL,
                    Torrent.State.ALLOCATING,
                    Torrent.State.MOVING,
                    Torrent.State.CHECKING_DL,
                    Torrent.State.CHECKING_UP,
                    Torrent.State.CHECKING_RESUME_DATA,
                    Torrent.State.QUEUED_DL,
                    Torrent.State.QUEUED_UP,
                    Torrent.State.UNKNOWN -> {
                        peers.text =
                            when (torrent.state) {
                                Torrent.State.META_DL,
                                Torrent.State.FORCED_META_DL ->
                                    context.getString(CommonR.string.state_downloading_metadata)
                                Torrent.State.ALLOCATING ->
                                    context.getString(CommonR.string.state_allocating)
                                Torrent.State.MOVING ->
                                    context.getString(CommonR.string.state_moving)
                                Torrent.State.CHECKING_DL,
                                Torrent.State.CHECKING_UP ->
                                    context.getString(CommonR.string.state_checking)
                                Torrent.State.CHECKING_RESUME_DATA ->
                                    context.getString(CommonR.string.state_checking_resume_data)
                                Torrent.State.QUEUED_DL,
                                Torrent.State.QUEUED_UP ->
                                    context.getString(CommonR.string.state_queued)
                                else -> context.getString(CommonR.string.state_unknown)
                            }
                        peers.setTextColor(context.getColor(R.color.yellow))
                        speed.visibility = View.GONE
                        eta.visibility = View.GONE
                        progressColor = context.getColor(R.color.yellow)
                    }
                }
                progressBar.progressTintList = ColorStateList.valueOf(progressColor)
            }
        }

        private fun bindChips(torrent: Torrent) {
            chipGroup.removeAllViews()

            val hasCategory = torrent.category.isNotBlank()
            if (hasCategory) {
                val userColor = categoryColors[torrent.category]
                if (userColor != null) {
                    addChip(
                        torrent.category,
                        userColor,
                        contrastColorOn(userColor),
                        outlined = false
                    )
                } else {
                    addChip(
                        torrent.category,
                        MaterialColors.getColor(
                            chipGroup,
                            com.google.android.material.R.attr.colorSecondaryContainer,
                        ),
                        MaterialColors.getColor(
                            chipGroup,
                            com.google.android.material.R.attr.colorOnSecondaryContainer,
                        ),
                        outlined = false,
                    )
                }
            }

            torrent.tags.forEach { tag ->
                addChip(
                    "#$tag",
                    null,
                    MaterialColors.getColor(
                        chipGroup,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ),
                    outlined = true,
                )
            }

            chipGroup.visibility =
                if (hasCategory || torrent.tags.isNotEmpty()) View.VISIBLE else View.GONE
        }

        private fun addChip(label: String, background: Int?, foreground: Int, outlined: Boolean) {
            val context = chipGroup.context
            val density = context.resources.displayMetrics.density
            val chip =
                Chip(context).apply {
                    text = label
                    isClickable = false
                    isCheckable = false
                    isCloseIconVisible = false
                    setEnsureMinTouchTargetSize(false)
                    chipMinHeight = 24 * density
                    chipStartPadding = 8 * density
                    chipEndPadding = 8 * density
                    textStartPadding = 0f
                    textEndPadding = 0f
                    setTextColor(foreground)
                    if (outlined) {
                        chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                        chipStrokeWidth = density
                        chipStrokeColor =
                            ColorStateList.valueOf(
                                MaterialColors.getColor(
                                    chipGroup,
                                    com.google.android.material.R.attr.colorOutline,
                                )
                            )
                    } else {
                        chipStrokeWidth = 0f
                        if (background != null) {
                            chipBackgroundColor = ColorStateList.valueOf(background)
                        }
                    }
                }
            chipGroup.addView(chip)
        }

        private fun contrastColorOn(background: Int): Int =
            if (ColorUtils.calculateLuminance(background) > 0.5) Color.BLACK else Color.WHITE
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.torrent_item, parent, false)

        return TorrentItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: TorrentItemViewHolder, position: Int) {
        val torrent = currentList.elementAt(position)

        holder.bind(torrent)
    }

    override fun getItemCount(): Int = currentList.size

    private class TorrentComparator : DiffUtil.ItemCallback<Torrent>() {
        override fun areItemsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.hash == newItem.hash
        }
    }

    private class TorrentItemDetailsLookup(private val recyclerView: RecyclerView) :
        ItemDetailsLookup<String>() {
        override fun getItemDetails(event: MotionEvent): ItemDetails<String>? {
            val view = recyclerView.findChildViewUnder(event.x, event.y)

            return if (view != null) {
                return (recyclerView.getChildViewHolder(view)
                        as TorrentListAdapter.TorrentItemViewHolder)
                    .itemDetails
            } else null
        }
    }

    private val itemKeyProvider =
        object : ItemKeyProvider<String>(SCOPE_CACHED) {
            override fun getKey(position: Int) = getItem(position).hash

            override fun getPosition(key: String): Int {
                return currentList.indexOfFirst { it.hash == key }
            }
        }
}
