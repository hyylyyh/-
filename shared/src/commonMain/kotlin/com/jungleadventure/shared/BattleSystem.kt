package com.jungleadventure.shared

import kotlin.math.roundToInt
import kotlin.random.Random

class BattleSystem(
    private val enemyRepository: EnemyRepository,
    private val rng: Random
) {
    private val engine = CombatEngine(rng)

    fun resolveBattle(player: PlayerStats, event: EventDefinition): BattleResolution {
        val group = enemyRepository.findGroup(event.enemyGroupId) ?: run {
            GameLogger.warn("战斗", "未找到敌群配置：${event.enemyGroupId}，使用默认敌群")
            defaultEnemyGroupFile().groups.first()
        }
        val enemyDef = enemyRepository.findEnemy(group.enemyId) ?: run {
            GameLogger.warn("战斗", "未找到敌人配置：${group.enemyId}，使用默认敌人")
            defaultEnemyFile().enemies.first()
        }
        val playerActor = player.toCombatActor()
        val enemyActor = enemyDef.toCombatActor(group.count)
        val config = CombatConfig(
            roundLimit = event.roundLimit,
            firstStrike = when (event.firstStrike?.lowercase()) {
                "player" -> FirstStrikeRule.PLAYER
                "enemy" -> FirstStrikeRule.ENEMY
                "random" -> FirstStrikeRule.RANDOM
                else -> FirstStrikeRule.SPEED
            }
        )
        val damageMultiplier = event.battleModifiers?.enemyDamageMultiplier ?: 1.0
        val hpMultiplier = event.battleModifiers?.enemyHpMultiplier ?: 1.0
        val scaledHp = (enemyActor.hp * hpMultiplier).roundToInt().coerceAtLeast(1)
        val scaledEnemy = enemyActor.copy(
            stats = enemyActor.stats.copy(hpMax = scaledHp),
            hp = scaledHp
        )

        GameLogger.log("战斗", "准备战斗：敌人=${enemyDef.name}，数量=${group.count}，生命倍率=$hpMultiplier，伤害倍率=$damageMultiplier")

        val outcome = engine.simulateBattle(
            player = playerActor,
            enemy = scaledEnemy,
            config = config,
            enemyDamageMultiplier = damageMultiplier
        )
        return BattleResolution(
            victory = outcome.victory,
            playerRemainingHp = outcome.playerRemainingHp,
            enemyRemainingHp = outcome.enemyRemainingHp,
            rounds = outcome.rounds,
            logLines = outcome.logLines,
            enemyName = enemyDef.name,
            groupId = group.id
        )
    }
}

data class BattleResolution(
    val victory: Boolean,
    val playerRemainingHp: Int,
    val enemyRemainingHp: Int,
    val rounds: Int,
    val logLines: List<String>,
    val enemyName: String,
    val groupId: String
)

private fun PlayerStats.toCombatActor(): CombatActor {
    val hit = (70 + speed).coerceIn(60, 95)
    val eva = (8 + speed / 2).coerceIn(5, 35)
    val crit = (6 + speed / 3).coerceIn(5, 30)
    val stats = CombatStats(
        hpMax = hpMax,
        atk = atk,
        def = def,
        speed = speed,
        hit = hit,
        eva = eva,
        crit = crit,
        critDmg = 1.5,
        resist = 3
    )
    GameLogger.log("战斗", "玩家转化战斗属性：命中=$hit 闪避=$eva 暴击=$crit")
    return CombatActor(
        id = "player",
        name = name,
        type = CombatActorType.PLAYER,
        stats = stats,
        hp = hp,
        mp = mp
    )
}

private fun EnemyDefinition.toCombatActor(count: Int): CombatActor {
    val multiplier = if (count <= 1) 1.0 else 1.0 + 0.35 * (count - 1)
    val scaledHp = (stats.hp * multiplier).roundToInt().coerceAtLeast(1)
    val scaledAtk = (stats.atk * (1.0 + 0.2 * (count - 1))).roundToInt().coerceAtLeast(1)
    val scaledDef = (stats.def * (1.0 + 0.15 * (count - 1))).roundToInt().coerceAtLeast(1)
    val scaledSpeed = (stats.spd * (1.0 + 0.05 * (count - 1))).roundToInt().coerceAtLeast(1)
    val stats = CombatStats(
        hpMax = scaledHp,
        atk = scaledAtk,
        def = scaledDef,
        speed = scaledSpeed,
        hit = stats.hit,
        eva = stats.eva,
        crit = stats.crit,
        critDmg = stats.critDmg,
        resist = stats.resist
    )
    GameLogger.log("战斗", "敌人转化战斗属性：$name 数量=$count，生命=$scaledHp 攻击=$scaledAtk 防御=$scaledDef 速度=$scaledSpeed")
    return CombatActor(
        id = id,
        name = if (count <= 1) name else "${name}($count)",
        type = CombatActorType.ENEMY,
        stats = stats,
        hp = scaledHp,
        mp = 0
    )
}
