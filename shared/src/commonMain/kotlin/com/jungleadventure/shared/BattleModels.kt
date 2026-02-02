package com.jungleadventure.shared

data class BattleSession(
    val player: CombatActor,
    val enemy: CombatActor,
    val round: Int,
    val config: CombatConfig,
    val enemyDamageMultiplier: Double,
    val logLines: List<String>,
    val skillCooldown: Int,
    val enemySkillCooldowns: Map<String, Int>,
    val equipmentMode: EquipmentMode,
    val basePlayerStats: CombatStats,
    val escaped: Boolean = false
)

data class BattleStepResult(
    val session: BattleSession,
    val outcome: BattleOutcome? = null
)

data class BattleOutcome(
    val victory: Boolean,
    val escaped: Boolean,
    val rounds: Int,
    val playerRemainingHp: Int,
    val enemyRemainingHp: Int,
    val logLines: List<String>
)

enum class EquipmentMode {
    NORMAL,
    OFFENSE,
    DEFENSE
}

enum class PlayerBattleActionType {
    BASIC_ATTACK,
    SKILL,
    ITEM,
    EQUIP,
    FLEE
}

data class PlayerBattleAction(
    val type: PlayerBattleActionType,
    val skill: SkillDefinition? = null
)
