package com.jungleadventure.shared

import kotlin.math.roundToInt
import kotlin.random.Random

class BattleSystem(
    private val enemyRepository: EnemyRepository,
    private val rng: Random
) {
    private val engine = CombatEngine(rng)

    fun buildBattleContext(player: PlayerStats, event: EventDefinition): BattleContext {
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
            firstStrike = when (event.firstStrike) {
                "玩家" -> FirstStrikeRule.PLAYER
                "敌人" -> FirstStrikeRule.ENEMY
                "随机" -> FirstStrikeRule.RANDOM
                "速度" -> FirstStrikeRule.SPEED
                else -> FirstStrikeRule.SPEED
            }
        )
        val modifiers = event.battleModifiers ?: BattleModifiers()
        val damageMultiplier = modifiers.enemyDamageMultiplier
        val scaledEnemy = applyEnemyModifiers(enemyActor, modifiers)

        GameLogger.info(
            "战斗",
            "准备回合制战斗：敌人=${enemyDef.name} 数量=${group.count} 生命倍率=${modifiers.enemyHpMultiplier} 攻击倍率=${modifiers.enemyAtkMultiplier} 防御倍率=${modifiers.enemyDefMultiplier} 速度倍率=${modifiers.enemySpdMultiplier} 伤害倍率=$damageMultiplier"
        )

        return BattleContext(
            player = playerActor,
            enemy = scaledEnemy,
            config = config,
            enemyDamageMultiplier = damageMultiplier,
            enemyName = enemyDef.name,
            groupId = group.id,
            count = group.count
        )
    }

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
            firstStrike = when (event.firstStrike) {
                "玩家" -> FirstStrikeRule.PLAYER
                "敌人" -> FirstStrikeRule.ENEMY
                "随机" -> FirstStrikeRule.RANDOM
                "速度" -> FirstStrikeRule.SPEED
                else -> FirstStrikeRule.SPEED
            }
        )
        val modifiers = event.battleModifiers ?: BattleModifiers()
        val damageMultiplier = modifiers.enemyDamageMultiplier
        val scaledEnemy = applyEnemyModifiers(enemyActor, modifiers)

        GameLogger.log(
            "战斗",
            "准备战斗：敌人=${enemyDef.name}，数量=${group.count}，生命倍率=${modifiers.enemyHpMultiplier} 攻击倍率=${modifiers.enemyAtkMultiplier} 防御倍率=${modifiers.enemyDefMultiplier} 速度倍率=${modifiers.enemySpdMultiplier} 伤害倍率=$damageMultiplier"
        )

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

data class BattleContext(
    val player: CombatActor,
    val enemy: CombatActor,
    val config: CombatConfig,
    val enemyDamageMultiplier: Double,
    val enemyName: String,
    val groupId: String,
    val count: Int
)

fun PlayerStats.toCombatActor(): CombatActor {
    val hit = (70 + speed + hitBonus).coerceIn(50, 98)
    val eva = (8 + speed / 2 + evaBonus).coerceIn(5, 45)
    val crit = (6 + speed / 3 + critBonus).coerceIn(5, 40)
    val resist = (3 + resistBonus).coerceIn(0, 50)
    val stats = CombatStats(
        hpMax = hpMax,
        atk = atk,
        def = def,
        speed = speed,
        hit = hit,
        eva = eva,
        crit = crit,
        critDmg = 1.5,
        resist = resist
    )
    GameLogger.log("战斗", "玩家转化战斗属性：命中=$hit 闪避=$eva 暴击=$crit 抗暴=$resist")
    return CombatActor(
        id = "玩家",
        name = name,
        type = CombatActorType.PLAYER,
        stats = stats,
        hp = hp,
        mp = mp
    )
}

private fun applyEnemyModifiers(actor: CombatActor, modifiers: BattleModifiers): CombatActor {
    val scaledHpMax = (actor.stats.hpMax * modifiers.enemyHpMultiplier).roundToInt().coerceAtLeast(1)
    val scaledAtk = (actor.stats.atk * modifiers.enemyAtkMultiplier).roundToInt().coerceAtLeast(1)
    val scaledDef = (actor.stats.def * modifiers.enemyDefMultiplier).roundToInt().coerceAtLeast(1)
    val scaledSpd = (actor.stats.speed * modifiers.enemySpdMultiplier).roundToInt().coerceAtLeast(1)
    val nextStats = actor.stats.copy(
        hpMax = scaledHpMax,
        atk = scaledAtk,
        def = scaledDef,
        speed = scaledSpd
    )
    val nextHp = actor.hp.coerceAtMost(scaledHpMax)
    GameLogger.info(
        "战斗",
        "敌人属性倍率应用：生命 ${actor.stats.hpMax} -> $scaledHpMax 攻击 ${actor.stats.atk} -> $scaledAtk 防御 ${actor.stats.def} -> $scaledDef 速度 ${actor.stats.speed} -> $scaledSpd"
    )
    return actor.copy(stats = nextStats, hp = nextHp)
}

fun EnemyDefinition.toCombatActor(count: Int): CombatActor {
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
        mp = 0,
        skills = skills
    )
}
