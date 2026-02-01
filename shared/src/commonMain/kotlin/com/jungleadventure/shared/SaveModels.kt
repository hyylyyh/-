package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class SaveGame(
    val version: Int = 2,
    val turn: Int,
    val chapter: Int,
    val selectedRoleId: String,
    val player: PlayerStats,
    val log: List<String>,
    val lastAction: String,
    val activePanel: GamePanel,
    val currentEventId: String? = null,
    val stageId: String? = null,
    val nodeId: String? = null,
    val visitedNodes: List<String> = emptyList(),
    val stageCompleted: Boolean = false
)

data class SaveSlotSummary(
    val slot: Int,
    val title: String,
    val detail: String,
    val hasData: Boolean
)
