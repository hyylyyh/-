package com.jungleadventure.shared

import kotlinx.serialization.Serializable

data class GameUiState(
    val title: String = "丛林大冒险",
    val screen: GameScreen = GameScreen.SAVE_SELECT,
    val turn: Int = 1,
    val chapter: Int = 1,
    val totalChapters: Int = 10,
    val selectedChapter: Int = 1,
    val selectedDifficulty: Int = 1,
    val completedChapters: List<Int> = emptyList(),
    val stage: StageUiState = StageUiState(),
    val player: PlayerStats = PlayerStats(),
    val roles: List<RoleProfile> = emptyList(),
    val selectedRoleId: String = "",
    val selectedSaveSlot: Int? = null,
    val currentEvent: EventDefinition? = null,
    val enemyPreview: EnemyPreviewUiState? = null,
    val battle: BattleUiState? = null,
    val log: List<String> = listOf("请选择存档：读取已有存档或创建新存档"),
    val choices: List<GameChoice> = listOf(),
    val activePanel: GamePanel = GamePanel.STATUS,
    val lastAction: String = "",
    val saveSlots: List<SaveSlotSummary> = emptyList(),
    val showSkillFormula: Boolean = false,
    val showDialog: Boolean = false,
    val dialogTitle: String = "",
    val dialogMessage: String = "",
    val showCardDialog: Boolean = false,
    val cardOptions: List<CardInstance> = emptyList(),
    val cardDialogLevel: Int = 0,
    val cardDialogReason: String = "",
    val pendingCardLevels: List<Int> = emptyList(),
    val pendingCardReasons: List<String> = emptyList()
)

data class BattleUiState(
    val round: Int,
    val playerHp: Int,
    val playerMp: Int,
    val enemyHp: Int,
    val enemyMp: Int,
    val enemyName: String,
    val equipmentMode: String,
    val skillCooldown: Int
)

data class StageUiState(
    val id: String = "",
    val name: String = "未命名关卡",
    val chapter: Int = 1,
    val nodeId: String = "",
    val nodeType: String = "",
    val command: String = "",
    val guardian: String = "",
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
    val strength: Int = 0,
    val intelligence: Int = 0,
    val agility: Int = 0,
    val level: Int = 1,
    val exp: Int = 0,
    val expToNext: Int = 30,
    val gold: Int = 0,
    val materials: Int = 0,
    val baseStats: PlayerBaseStats = PlayerBaseStats(
        hpMax = hpMax,
        mpMax = mpMax,
        atk = atk,
        def = def,
        speed = speed,
        strength = strength,
        intelligence = intelligence,
        agility = agility
    ),
    val hitBonus: Int = 0,
    val evaBonus: Int = 0,
    val critBonus: Int = 0,
    val resistBonus: Int = 0,
    val equipment: EquipmentLoadout = EquipmentLoadout(),
    val inventory: InventoryState = InventoryState(),
    val cards: List<CardInstance> = emptyList(),
    val pityCounters: Map<String, Int> = emptyMap()
)

data class GameChoice(
    val id: String,
    val label: String
)

data class EnemyPreviewUiState(
    val name: String,
    val type: String,
    val level: Int,
    val count: Int,
    val hp: Int,
    val atk: Int,
    val def: Int,
    val speed: Int,
    val strength: Int,
    val intelligence: Int,
    val agility: Int,
    val hit: Int,
    val eva: Int,
    val crit: Int,
    val critDmg: Double,
    val resist: Int,
    val note: String,
    val threat: String,
    val tip: String,
    val winRate: Int,
    val summary: String,
    val roundLimit: Int?,
    val firstStrike: String,
    val dropTableId: String,
    val dropPreview: List<String>
)

@Serializable
enum class GamePanel {
    STATUS,
    EQUIPMENT,
    INVENTORY,
    CARDS,
    SKILLS
}

enum class GameScreen {
    SAVE_SELECT,
    ROLE_SELECT,
    CHAPTER_SELECT,
    ADVENTURE
}
