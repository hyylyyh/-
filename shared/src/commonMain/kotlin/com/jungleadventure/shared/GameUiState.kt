package com.jungleadventure.shared

import kotlinx.serialization.Serializable

data class GameUiState(
    val title: String = "丛林大冒险",
    val turn: Int = 1,
    val chapter: Int = 1,
    val stage: StageUiState = StageUiState(),
    val player: PlayerStats = PlayerStats(),
    val roles: List<RoleProfile> = emptyList(),
    val selectedRoleId: String = "",
    val currentEvent: EventDefinition? = null,
    val log: List<String> = listOf("冒险开始"),
    val choices: List<GameChoice> = listOf(),
    val activePanel: GamePanel = GamePanel.STATUS,
    val lastAction: String = "",
    val saveSlots: List<SaveSlotSummary> = emptyList()
)

data class StageUiState(
    val id: String = "",
    val name: String = "未命名关卡",
    val chapter: Int = 1,
    val nodeId: String = "",
    val nodeType: String = "",
    val visited: Int = 0,
    val total: Int = 0,
    val isCompleted: Boolean = false
)

@Serializable
data class PlayerStats(
    val name: String = "探险者",
    val hp: Int = 100,
    val hpMax: Int = 100,
    val mp: Int = 30,
    val mpMax: Int = 30,
    val atk: Int = 15,
    val def: Int = 8,
    val speed: Int = 10,
    val level: Int = 1,
    val gold: Int = 0,
    val materials: Int = 0
)

data class GameChoice(
    val id: String,
    val label: String
)

@Serializable
enum class GamePanel {
    STATUS,
    EQUIPMENT,
    INVENTORY
}
