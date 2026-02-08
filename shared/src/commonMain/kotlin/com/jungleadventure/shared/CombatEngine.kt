package com.jungleadventure.shared

import kotlin.math.max
import kotlin.random.Random

class CombatEngine(private val rng: Random) {
    fun simulateBattle(
        player: CombatActor,
        enemy: CombatActor,
        config: CombatConfig,
        enemyDamageMultiplier: Double = 1.0
    ): CombatOutcome {
        val logLines = mutableListOf<String>()
        logLines += "战斗开始：${player.name} 对 ${enemy.name}"
        GameLogger.log("战斗", "初始化：玩家生命 ${player.hp}/${player.stats.hpMax}，敌人生命 ${enemy.hp}/${enemy.stats.hpMax}")
        GameLogger.log("战斗", "先手规则=${firstStrikeLabel(config.firstStrike)}，回合上限=${config.roundLimit ?: "无限"}")

        var currentPlayer = player
        var currentEnemy = enemy
        var round = 1

        while (currentPlayer.hp > 0 && currentEnemy.hp > 0) {
            if (config.roundLimit != null && round > config.roundLimit) {
                logLines += "回合上限到达（${config.roundLimit}），判定失败。"
                GameLogger.log("战斗", "达到回合上限，玩家失败。")
                return CombatOutcome(
                    victory = false,
                    rounds = round - 1,
                    playerRemainingHp = currentPlayer.hp,
                    enemyRemainingHp = currentEnemy.hp,
                    logLines = logLines
                )
            }

            logLines += "回合 $round 开始"
            GameLogger.log("战斗", "回合 $round，玩家生命=${currentPlayer.hp}，敌人生命=${currentEnemy.hp}")

            val turnOrder = determineTurnOrder(currentPlayer, currentEnemy, config.firstStrike)
            for (actor in turnOrder) {
                if (currentPlayer.hp <= 0 || currentEnemy.hp <= 0) break

                if (actor.type == CombatActorType.PLAYER) {
                    val start = applyStartOfTurn(currentPlayer)
                    currentPlayer = start.actor
                    logLines += start.logs
                    if (start.skipTurn) {
                        continue
                    }

                    val attack = resolveAttack(currentPlayer, currentEnemy, 1.0)
                    currentEnemy = attack.target
                    logLines += attack.logs

                    val end = applyEndOfTurn(currentPlayer)
                    currentPlayer = end.actor
                    logLines += end.logs
                } else {
                    val start = applyStartOfTurn(currentEnemy)
                    currentEnemy = start.actor
                    logLines += start.logs
                    if (start.skipTurn) {
                        continue
                    }

                    val attack = resolveAttack(currentEnemy, currentPlayer, enemyDamageMultiplier)
                    currentPlayer = attack.target
                    logLines += attack.logs

                    val end = applyEndOfTurn(currentEnemy)
                    currentEnemy = end.actor
                    logLines += end.logs
                }
            }

            round += 1
        }

        val victory = currentEnemy.hp <= 0
        val summary = if (victory) "战斗胜利" else "战斗失败"
        logLines += summary
        GameLogger.log("战斗", summary)
        return CombatOutcome(
            victory = victory,
            rounds = round - 1,
            playerRemainingHp = currentPlayer.hp,
            enemyRemainingHp = currentEnemy.hp,
            logLines = logLines
        )
    }

    private fun determineTurnOrder(
        player: CombatActor,
        enemy: CombatActor,
        rule: FirstStrikeRule
    ): List<CombatActor> {
        return when (rule) {
            FirstStrikeRule.PLAYER -> listOf(player, enemy)
            FirstStrikeRule.ENEMY -> listOf(enemy, player)
            FirstStrikeRule.RANDOM -> if (rng.nextBoolean()) listOf(player, enemy) else listOf(enemy, player)
            FirstStrikeRule.AGILITY -> {
                val playerAgi = effectiveAgility(player)
                val enemyAgi = effectiveAgility(enemy)
                when {
                    playerAgi > enemyAgi -> listOf(player, enemy)
                    enemyAgi > playerAgi -> listOf(enemy, player)
                    else -> if (rng.nextBoolean()) listOf(player, enemy) else listOf(enemy, player)
                }
            }
        }
    }

    private fun firstStrikeLabel(rule: FirstStrikeRule): String {
        return when (rule) {
            FirstStrikeRule.PLAYER -> "玩家"
            FirstStrikeRule.ENEMY -> "敌人"
            FirstStrikeRule.RANDOM -> "随机"
            FirstStrikeRule.AGILITY -> "敏捷"
        }
    }

    private fun effectiveAgility(actor: CombatActor): Int {
        val hasteStacks = actor.statuses.filter { it.type == StatusType.HASTE }.sumOf { it.stacks }
        val slowStacks = actor.statuses.filter { it.type == StatusType.SLOW }.sumOf { it.stacks }
        val multiplier = (1.0 + 0.2 * hasteStacks - 0.2 * slowStacks).coerceAtLeast(0.5)
        return max(1, (actor.stats.agility * multiplier).toInt())
    }

    private fun resolveAttack(
        attacker: CombatActor,
        target: CombatActor,
        damageMultiplier: Double
    ): AttackResult {
        val hitRate = hitChance(attacker.stats.hit, target.stats.eva)
        val hitRoll = rng.nextDouble()
        if (hitRoll > hitRate) {
            val log = "${attacker.name} 攻击落空（命中率 ${(hitRate * 100).toInt()}%，判定 ${"%.2f".format(hitRoll)}）"
            GameLogger.log("战斗", log)
            return AttackResult(target = target, logs = listOf(log))
        }

        val critRate = critChance(attacker.stats.crit)
        val critRoll = rng.nextDouble()
        val isCrit = critRoll <= critRate
        val baseDamage = max(1, attacker.stats.atk - target.stats.def)
        val scaled = (baseDamage * damageMultiplier)
        val critMultiplier = if (isCrit) attacker.stats.critDmg else 1.0
        var finalDamage = max(1, (scaled * critMultiplier).toInt())

        val shielded = target.statuses.firstOrNull { it.type == StatusType.SHIELD }
        if (shielded != null) {
            val reduction = (0.3 * shielded.stacks).coerceAtMost(0.7)
            finalDamage = max(1, (finalDamage * (1.0 - reduction)).toInt())
        }

        val nextHp = max(0, target.hp - finalDamage)
        var nextTarget = target.withHp(nextHp)
        val logs = mutableListOf<String>()
        val mainLog = buildString {
            append("${attacker.name} 命中${target.name}，伤害 $finalDamage")
            append("（基础 $baseDamage，倍率 ${"%.2f".format(damageMultiplier)}")
            if (isCrit) {
                append("，暴击 ${"%.2f".format(attacker.stats.critDmg)}x")
            }
            if (shielded != null) {
                append("，护盾减伤 ${(0.3 * shielded.stacks * 100).toInt()}%")
            }
            append("），${target.name} 生命 ${nextHp}/${target.stats.hpMax}")
            append("（命中率 ${(hitRate * 100).toInt()}%，判定 ${"%.2f".format(hitRoll)}；暴击率 ${(critRate * 100).toInt()}%，判定 ${"%.2f".format(critRoll)}）")
        }
        logs += mainLog
        GameLogger.log("战斗", mainLog)

        val damageLog = when (attacker.type) {
            CombatActorType.PLAYER -> "你对${target.name}造成${finalDamage}点伤害"
            CombatActorType.ENEMY -> "${attacker.name}对你造成${finalDamage}点伤害"
        }
        logs += damageLog
        GameLogger.log("战斗", damageLog)

        if (isCrit && rng.nextDouble() < 0.3) {
            val bleed = StatusInstance(type = StatusType.BLEED, remainingTurns = 2, stacks = 1, potency = 0.04, sourceId = attacker.id)
            nextTarget = addStatus(nextTarget, bleed)
            val bleedLog = "${target.name} 触发流血（2回合，强度4%）"
            logs += bleedLog
            GameLogger.log("战斗", bleedLog)
        }

        return AttackResult(target = nextTarget, logs = logs)
    }

    private fun applyStartOfTurn(actor: CombatActor): TurnResult {
        var nextActor = actor
        val logs = mutableListOf<String>()
        val stun = actor.statuses.firstOrNull { it.type == StatusType.STUN }
        if (stun != null && stun.remainingTurns > 0) {
            val log = "${actor.name} 眩晕中，无法行动（剩余${stun.remainingTurns}回合）"
            logs += log
            GameLogger.log("战斗", log)
            return TurnResult(actor = nextActor, logs = logs, skipTurn = true)
        }
        return TurnResult(actor = nextActor, logs = logs, skipTurn = false)
    }

    private fun applyEndOfTurn(actor: CombatActor): TurnResult {
        var nextActor = actor
        val logs = mutableListOf<String>()
        val dotStatuses = actor.statuses.filter { it.type == StatusType.POISON || it.type == StatusType.BLEED }
        if (dotStatuses.isNotEmpty()) {
            var totalDamage = 0
            dotStatuses.forEach { status ->
                val damage = max(1, (actor.stats.hpMax * status.potency * status.stacks).toInt())
                totalDamage += damage
            }
            val nextHp = max(0, actor.hp - totalDamage)
            nextActor = nextActor.withHp(nextHp)
            val log = "${actor.name} 受到持续伤害 $totalDamage，生命 ${nextHp}/${actor.stats.hpMax}"
            logs += log
            GameLogger.log("战斗", log)
        }
        nextActor = nextActor.withStatuses(tickStatuses(nextActor.statuses))
        return TurnResult(actor = nextActor, logs = logs, skipTurn = false)
    }

    private fun addStatus(target: CombatActor, status: StatusInstance): CombatActor {
        val existing = target.statuses.firstOrNull { it.type == status.type && it.sourceId == status.sourceId }
        val updated = if (existing == null) {
            target.statuses + status
        } else {
            target.statuses.map {
                if (it == existing) it.copy(
                    remainingTurns = max(it.remainingTurns, status.remainingTurns),
                    stacks = it.stacks + status.stacks,
                    potency = max(it.potency, status.potency)
                ) else it
            }
        }
        return target.withStatuses(updated)
    }

    private fun tickStatuses(statuses: List<StatusInstance>): List<StatusInstance> {
        return statuses.map { status ->
            status.copy(remainingTurns = status.remainingTurns - 1)
        }.filter { it.remainingTurns > 0 }
    }

    private fun hitChance(hit: Int, eva: Int): Double {
        val raw = hit.toDouble() / (hit + eva).coerceAtLeast(1)
        return raw.coerceIn(0.2, 0.95)
    }

    private fun critChance(crit: Int): Double {
        return (crit / 100.0).coerceIn(0.0, 0.6)
    }

    private data class AttackResult(
        val target: CombatActor,
        val logs: List<String>
    )

    private data class TurnResult(
        val actor: CombatActor,
        val logs: List<String>,
        val skipTurn: Boolean
    )
}
