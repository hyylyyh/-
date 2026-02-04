package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class SaveGame(
    val version: Int = 8,
    val turn: Int,
    val chapter: Int,
    val rngSeed: Long = 0,
    val selectedRoleId: String,
    val player: PlayerStats,
    val log: List<String>,
    val lastAction: String,
    val activePanel: GamePanel,
    val showSkillFormula: Boolean = false,
    val selectedDifficulty: Int = 1,
    val completedChapters: List<Int> = emptyList(),
    val currentEventId: String? = null,
    val stageId: String? = null,
    val nodeId: String? = null,
    val visitedNodes: List<String> = emptyList(),
    val stageCompleted: Boolean = false,
    val guardianGroupId: String? = null,
    val showCardDialog: Boolean = false,
    val cardOptions: List<CardInstance> = emptyList(),
    val cardDialogLevel: Int = 0,
    val cardDialogReason: String = "",
    val pendingCardLevels: List<Int> = emptyList(),
    val pendingCardReasons: List<String> = emptyList()
)

data class SaveSlotSummary(
    val slot: Int,
    val title: String,
    val detail: String,
    val hasData: Boolean
)
