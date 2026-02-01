package com.jungleadventure.shared

data class GameUiState(
    val title: String = "丛林大冒险",
    val turn: Int = 1,
    val chapter: Int = 1,
    val player: PlayerStats = PlayerStats(),
    val roles: List<RoleProfile> = emptyList(),
    val selectedRoleId: String = "",
    val currentEvent: EventDefinition? = null,
    val log: List<String> = listOf("冒险开始"),
    val choices: List<GameChoice> = listOf(),
    val activePanel: GamePanel = GamePanel.STATUS,
    val lastAction: String = ""
)

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

enum class GamePanel {
    STATUS,
    EQUIPMENT,
    INVENTORY
}
