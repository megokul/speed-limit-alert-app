package com.speedsense.app.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.speedsense.app.R
import com.speedsense.app.data.JsonLoader
import com.speedsense.app.data.Road
import com.speedsense.app.data.RoadRepository
import com.speedsense.app.data.SpeedLimitOverrides
import com.speedsense.app.data.UserRoadStorage
import com.speedsense.app.databinding.ActivityRoadListBinding
import com.speedsense.app.service.LocationService
import com.speedsense.app.service.MonitoringStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoadListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoadListBinding
    private val adapter = RoadListAdapter(::showEditRoadDialog)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoadListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.roadsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.roadsRecyclerView.adapter = adapter

        refreshRoadList()
    }

    private fun refreshRoadList() {
        lifecycleScope.launch {
            val inventory = withContext(Dispatchers.Default) {
                buildRoadInventory()
            }
            binding.summaryText.text = getString(
                R.string.road_list_summary,
                inventory.bundledRoads.size,
                inventory.userRoads.size,
            )
            adapter.submitList(inventory.toListItems(this@RoadListActivity))
        }
    }

    private fun buildRoadInventory(): RoadInventory {
        val bundledRoads = JsonLoader.loadRoads(applicationContext)
            .sortedWith(compareBy<Road>({ it.roadName.lowercase() }, { it.roadId }))
        val userRoads = UserRoadStorage.loadUserRoads()
            .sortedWith(compareBy<Road>({ it.roadName.lowercase() }, { it.roadId }))

        val bundledIds = bundledRoads.map { it.roadId }.toSet()
        val userIds = userRoads.map { it.roadId }.toSet()

        val bundledEntries = bundledRoads.map { road ->
            val overrideLimit = SpeedLimitOverrides.getOverride(road.roadId)
            RoadListRow.RoadEntry(
                road = road,
                effectiveRoadName = road.roadName,
                roadName = road.roadName,
                roadId = road.roadId,
                effectiveSpeedLimit = overrideLimit ?: road.speedLimit,
                sourceLabel = getString(R.string.road_list_source_bundled),
                isBundled = true,
                note = when {
                    road.roadId in userIds -> getString(R.string.road_list_bundled_replaced_note)
                    overrideLimit != null -> getString(
                        R.string.road_list_override_note,
                        road.speedLimit,
                        overrideLimit,
                    )
                    else -> null
                },
            )
        }

        val userEntries = userRoads.map { road ->
            val overrideLimit = SpeedLimitOverrides.getOverride(road.roadId)
            RoadListRow.RoadEntry(
                road = road,
                effectiveRoadName = road.roadName,
                roadName = road.roadName,
                roadId = road.roadId,
                effectiveSpeedLimit = overrideLimit ?: road.speedLimit,
                sourceLabel = getString(R.string.road_list_source_user),
                isBundled = false,
                note = when {
                    road.roadId in bundledIds -> getString(R.string.road_list_user_replacement_note)
                    overrideLimit != null -> getString(
                        R.string.road_list_override_note,
                        road.speedLimit,
                        overrideLimit,
                    )
                    else -> null
                },
            )
        }

        return RoadInventory(bundledEntries, userEntries)
    }

    private fun showEditRoadDialog(entry: RoadListRow.RoadEntry) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }

        val roadNameInput = EditText(this).apply {
            hint = getString(R.string.record_road_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(entry.effectiveRoadName)
        }

        val speedLimitInput = EditText(this).apply {
            hint = getString(R.string.record_road_speed_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(entry.effectiveSpeedLimit.toString())
        }

        container.addView(roadNameInput)
        container.addView(speedLimitInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_road_title))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val speedLimit = speedLimitInput.text.toString().toIntOrNull()
                if (speedLimit == null || speedLimit <= 0) {
                    Toast.makeText(this, getString(R.string.recording_invalid_input), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    saveEditedRoad(
                        entry = entry,
                        updatedName = roadNameInput.text.toString().trim(),
                        updatedSpeedLimit = speedLimit,
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun saveEditedRoad(
        entry: RoadListRow.RoadEntry,
        updatedName: String,
        updatedSpeedLimit: Int,
    ) {
        val savedRoad = entry.road.copy(
            roadName = updatedName,
            speedLimit = updatedSpeedLimit,
        )

        withContext(Dispatchers.IO) {
            UserRoadStorage.saveRoad(savedRoad)
        }
        SpeedLimitOverrides.removeOverride(entry.roadId)
        RoadRepository.reload(applicationContext)
        if (MonitoringStateStore.state.value.isMonitoring) {
            LocationService.reloadRoads(this)
        }
        refreshRoadList()
        Toast.makeText(
            this,
            getString(
                R.string.edit_road_saved,
                updatedName.ifBlank { getString(R.string.road_list_unknown_name) },
            ),
            Toast.LENGTH_LONG,
        ).show()
    }

    private data class RoadInventory(
        val bundledRoads: List<RoadListRow.RoadEntry>,
        val userRoads: List<RoadListRow.RoadEntry>,
    ) {
        fun toListItems(activity: RoadListActivity): List<RoadListRow> {
            return buildList {
                add(RoadListRow.Header(activity.getString(R.string.road_list_bundled_header)))
                if (bundledRoads.isEmpty()) {
                    add(RoadListRow.Empty(activity.getString(R.string.road_list_empty_bundled)))
                } else {
                    addAll(bundledRoads)
                }

                add(RoadListRow.Header(activity.getString(R.string.road_list_user_header)))
                if (userRoads.isEmpty()) {
                    add(RoadListRow.Empty(activity.getString(R.string.road_list_empty_user)))
                } else {
                    addAll(userRoads)
                }
            }
        }
    }
}
