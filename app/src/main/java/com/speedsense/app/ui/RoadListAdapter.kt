package com.speedsense.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.speedsense.app.R
import com.speedsense.app.data.Road
import com.speedsense.app.databinding.ItemRoadListEmptyBinding
import com.speedsense.app.databinding.ItemRoadListHeaderBinding
import com.speedsense.app.databinding.ItemRoadListRowBinding

sealed interface RoadListRow {
    data class Header(val title: String) : RoadListRow
    data class Empty(val message: String) : RoadListRow
    data class RoadEntry(
        val road: Road,
        val effectiveRoadName: String,
        val roadName: String,
        val roadId: String,
        val effectiveSpeedLimit: Int,
        val sourceLabel: String,
        val note: String?,
        val isBundled: Boolean,
    ) : RoadListRow
}

class RoadListAdapter(
    private val onEditRoad: (RoadListRow.RoadEntry) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<RoadListRow> = emptyList()

    fun submitList(newItems: List<RoadListRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is RoadListRow.Header -> VIEW_TYPE_HEADER
            is RoadListRow.Empty -> VIEW_TYPE_EMPTY
            is RoadListRow.RoadEntry -> VIEW_TYPE_ROAD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemRoadListHeaderBinding.inflate(inflater, parent, false),
            )

            VIEW_TYPE_EMPTY -> EmptyViewHolder(
                ItemRoadListEmptyBinding.inflate(inflater, parent, false),
            )

            else -> RoadViewHolder(
                ItemRoadListRowBinding.inflate(inflater, parent, false),
                onEditRoad,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RoadListRow.Header -> (holder as HeaderViewHolder).bind(item)
            is RoadListRow.Empty -> (holder as EmptyViewHolder).bind(item)
            is RoadListRow.RoadEntry -> (holder as RoadViewHolder).bind(item)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemRoadListHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RoadListRow.Header) {
            binding.headerText.text = item.title
        }
    }

    private class EmptyViewHolder(
        private val binding: ItemRoadListEmptyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RoadListRow.Empty) {
            binding.emptyText.text = item.message
        }
    }

    private class RoadViewHolder(
        private val binding: ItemRoadListRowBinding,
        private val onEditRoad: (RoadListRow.RoadEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RoadListRow.RoadEntry) {
            binding.roadNameText.text = item.effectiveRoadName.ifBlank {
                itemView.context.getString(R.string.road_list_unknown_name)
            }
            binding.roadIdText.text = itemView.context.getString(R.string.road_list_road_id, item.roadId)
            binding.sourceChip.text = item.sourceLabel
            binding.speedLimitChip.text = itemView.context.getString(
                R.string.road_list_limit_chip,
                item.effectiveSpeedLimit,
            )
            binding.roadNoteText.text = item.note
            binding.roadNoteText.visibility = if (item.note.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.editRoadButton.setOnClickListener {
                onEditRoad(item)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMPTY = 1
        private const val VIEW_TYPE_ROAD = 2
    }
}
