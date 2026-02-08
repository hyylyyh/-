package com.jungleadventure.shared

enum class CombatActorType {
    PLAYER,
    ENEMY
}

data class CombatActor(
    val id: String,
    val name: String,
    val type: CombatActorType,
    val stats: CombatStats,
    val hp: Int,
    val mp: Int,
    val skills: List<EnemySkillDefinition> = emptyList(),
    val statuses: List<StatusInstance> = emptyList()
) {
    fun withHp(nextHp: Int): CombatActor {
        return copy(hp = nextHp.coerceAtLeast(0))
    }

    fun withMp(nextMp: Int): CombatActor {
        return copy(mp = nextMp.coerceAtLeast(0))
    }

    fun withStatuses(nextStatuses: List<StatusInstance>): CombatActor {
        return copy(statuses = nextStatuses)
    }
}

data class CombatStats(
    val hpMax: Int,
    val atk: Int,
    val def: Int,
    val agility: Int,
    val hit: Int,
    val eva: Int,
    val crit: Int,
    val critDmg: Double
)

data class CombatConfig(
    val roundLimit: Int? = null,
    val firstStrike: FirstStrikeRule = FirstStrikeRule.AGILITY
)

enum class FirstStrikeRule {
    AGILITY,
    RANDOM,
    PLAYER,
    ENEMY
}

data class CombatOutcome(
    val victory: Boolean,
    val rounds: Int,
    val playerRemainingHp: Int,
    val enemyRemainingHp: Int,
    val logLines: List<String>
)

enum class StatusType {
    POISON,
    BLEED,
    STUN,
    SHIELD,
    HASTE,
    SLOW
}

data class StatusInstance(
    val type: StatusType,
    val remainingTurns: Int,
    val stacks: Int = 1,
    val potency: Double = 0.0,
    val sourceId: String = ""
)
