package com.jungleadventure.shared

import kotlin.math.max
import kotlin.random.Random

class TurnBasedCombatEngine(private val rng: Random) {
    fun startSession(
        player: CombatActor,
        enemy: CombatActor,
        config: CombatConfig,
        enemyDamageMultiplier: Double,
        initialCooldown: Int
    ): BattleSession {
        val logs = mutableListOf<String>()
        logs += "战斗开始：${player.name} 对 ${enemy.name}"
        GameLogger.info("战斗", "初始化战斗会话：玩家生命=${player.hp}/${player.stats.hpMax} 敌人生命=${enemy.hp}/${enemy.stats.hpMax}")
        return BattleSession(
            player = player,
            enemy = enemy,
            round = 1,
            config = config,
            enemyDamageMultiplier = enemyDamageMultiplier,
            logLines = logs,
            skillCooldown = initialCooldown,
            equipmentMode = EquipmentMode.NORMAL,
            basePlayerStats = player.stats
        )
    }

    fun applyPlayerAction(
        session: BattleSession,
        action: PlayerBattleAction
    ): BattleStepResult {
        var current = session
        val logs = mutableListOf<String>()
        val round = current.round
        logs += "回合 $round 开始"
        val startResult = applyStartOfTurn(current.player)
        var player = startResult.actor
        logs += startResult.logs
        if (startResult.skipTurn) {
            return finishPlayerTurn(
                current = current.copy(player = player),
                logs = logs
            )
        }

        val actionResult = when (action.type) {
            PlayerBattleActionType.BASIC_ATTACK -> resolveAttack(
                attacker = player,
                target = current.enemy,
                damageMultiplier = 1.0
            )
            PlayerBattleActionType.SKILL -> resolveSkillAction(
                actor = player,
                target = current.enemy,
                skill = action.skill,
                cooldownRemaining = current.skillCooldown
            )
            PlayerBattleActionType.ITEM -> resolveItemAction(player, current.enemy)
            PlayerBattleActionType.EQUIP -> resolveEquipAction(
                player,
                current.enemy,
                current.equipmentMode,
                current.basePlayerStats
            )
            PlayerBattleActionType.FLEE -> resolveFleeAction(player, current.enemy)
        }

        logs += actionResult.logs
        player = actionResult.player
        var enemy = actionResult.enemy
        var cooldown = actionResult.cooldown
        val mode = actionResult.mode ?: current.equipmentMode
        val escaped = actionResult.escaped

        if (enemy.hp <= 0 || escaped) {
            return finishBattle(
                session = current.copy(
                    player = player,
                    enemy = enemy,
                    skillCooldown = cooldown,
                    equipmentMode = mode,
                    basePlayerStats = current.basePlayerStats,
                    logLines = current.logLines + logs,
                    escaped = escaped
                ),
                logs = logs
            )
        }

        return finishPlayerTurn(
            current = current.copy(
                player = player,
                enemy = enemy,
                skillCooldown = cooldown,
                equipmentMode = mode,
                basePlayerStats = current.basePlayerStats,
                logLines = current.logLines + logs,
                escaped = escaped
            ),
            logs = logs
        )
    }

    fun applyEnemyTurn(session: BattleSession, advanceRound: Boolean = true): BattleStepResult {
        val logs = mutableListOf<String>()
        var player = session.player
        var enemy = session.enemy
        val start = applyStartOfTurn(enemy)
        enemy = start.actor
        logs += start.logs
        if (!start.skipTurn) {
            val attack = resolveAttack(
                attacker = enemy,
                target = player,
                damageMultiplier = session.enemyDamageMultiplier
            )
            player = attack.player
            enemy = attack.enemy
            logs += attack.logs
        }

        val endEnemy = applyEndOfTurn(enemy)
        enemy = endEnemy.actor
        logs += endEnemy.logs
        val endPlayer = applyEndOfTurn(player)
        player = endPlayer.actor
        logs += endPlayer.logs

        val nextRound = if (advanceRound) session.round + 1 else session.round
        val nextCooldown = if (advanceRound) {
            (session.skillCooldown - 1).coerceAtLeast(0)
        } else {
            session.skillCooldown
        }
        val updatedSession = session.copy(
            player = player,
            enemy = enemy,
            round = nextRound,
            skillCooldown = nextCooldown,
            basePlayerStats = session.basePlayerStats,
            logLines = session.logLines + logs
        )

        return finishBattle(updatedSession, logs)
    }

    fun determineFirstStrike(player: CombatActor, enemy: CombatActor, rule: FirstStrikeRule): CombatActorType {
        return when (rule) {
            FirstStrikeRule.PLAYER -> CombatActorType.PLAYER
            FirstStrikeRule.ENEMY -> CombatActorType.ENEMY
            FirstStrikeRule.RANDOM -> if (rng.nextBoolean()) CombatActorType.PLAYER else CombatActorType.ENEMY
            FirstStrikeRule.SPEED -> {
                val playerSpeed = effectiveSpeed(player)
                val enemySpeed = effectiveSpeed(enemy)
                when {
                    playerSpeed > enemySpeed -> CombatActorType.PLAYER
                    enemySpeed > playerSpeed -> CombatActorType.ENEMY
                    else -> if (rng.nextBoolean()) CombatActorType.PLAYER else CombatActorType.ENEMY
                }
            }
        }
    }

    private fun finishPlayerTurn(current: BattleSession, logs: List<String>): BattleStepResult {
        if (current.config.roundLimit != null && current.round > current.config.roundLimit) {
            val log = "回合上限到达（${current.config.roundLimit}），判定失败。"
            GameLogger.info("战斗", log)
            return BattleStepResult(
                session = current.copy(logLines = current.logLines + log),
                outcome = BattleOutcome(
                    victory = false,
                    escaped = false,
                    rounds = current.round,
                    playerRemainingHp = current.player.hp,
                    enemyRemainingHp = current.enemy.hp,
                    logLines = current.logLines + logs + log
                )
            )
        }
        return BattleStepResult(session = current, outcome = null)
    }

    private fun finishBattle(session: BattleSession, logs: List<String>): BattleStepResult {
        if (session.enemy.hp > 0 && session.player.hp > 0 && !session.escaped) {
            return BattleStepResult(session = session, outcome = null)
        }
        val victory = session.enemy.hp <= 0
        val summary = when {
            session.escaped -> "你成功撤离战斗。"
            victory -> "战斗胜利"
            else -> "战斗失败"
        }
        val combinedLogs = session.logLines + summary
        GameLogger.info("战斗", summary)
        return BattleStepResult(
            session = session.copy(logLines = combinedLogs),
            outcome = BattleOutcome(
                victory = victory,
                escaped = session.escaped,
                rounds = session.round,
                playerRemainingHp = session.player.hp,
                enemyRemainingHp = session.enemy.hp,
                logLines = combinedLogs
            )
        )
    }

    private fun resolveAttack(
        attacker: CombatActor,
        target: CombatActor,
        damageMultiplier: Double
    ): ActionResult {
        val hitRate = hitChance(attacker.stats.hit, target.stats.eva)
        val hitRoll = rng.nextDouble()
        if (hitRoll > hitRate) {
            val log = "${attacker.name} 攻击落空（命中率 ${(hitRate * 100).toInt()}%，判定 ${"%.2f".format(hitRoll)}）"
            GameLogger.info("战斗", log)
            return ActionResult(
                player = if (attacker.type == CombatActorType.PLAYER) attacker else target,
                enemy = if (attacker.type == CombatActorType.ENEMY) attacker else target,
                logs = listOf(log)
            )
        }

        val critRate = critChance(attacker.stats.crit, target.stats.resist)
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
        val nextTarget = target.withHp(nextHp)
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
        }
        logs += mainLog
        GameLogger.info("战斗", mainLog)

        val damageLog = when (attacker.type) {
            CombatActorType.PLAYER -> "你对${target.name}造成${finalDamage}点伤害"
            CombatActorType.ENEMY -> "${attacker.name}对你造成${finalDamage}点伤害"
        }
        logs += damageLog
        GameLogger.info("战斗", damageLog)

        return ActionResult(
            player = if (attacker.type == CombatActorType.PLAYER) attacker else nextTarget,
            enemy = if (attacker.type == CombatActorType.ENEMY) attacker else nextTarget,
            logs = logs
        )
    }

    private fun resolveSkillAction(
        actor: CombatActor,
        target: CombatActor,
        skill: SkillDefinition?,
        cooldownRemaining: Int
    ): ActionResult {
        if (skill == null) {
            val log = "未装备可用技能，行动无效。"
            GameLogger.info("战斗", log)
            return ActionResult(
                player = actor,
                enemy = target,
                logs = listOf(log),
                cooldown = cooldownRemaining
            )
        }
        if (cooldownRemaining > 0) {
            val log = "技能 ${skill.name} 冷却中（剩余 $cooldownRemaining 回合）。"
            GameLogger.info("战斗", log)
            return ActionResult(
                player = actor,
                enemy = target,
                logs = listOf(log),
                cooldown = cooldownRemaining
            )
        }
        if (actor.mp < skill.cost) {
            val log = "技能 ${skill.name} 能量不足（需要 ${skill.cost}）。"
            GameLogger.info("战斗", log)
            return ActionResult(
                player = actor,
                enemy = target,
                logs = listOf(log),
                cooldown = cooldownRemaining
            )
        }

        var nextPlayer = actor.withMp(actor.mp - skill.cost)
        var nextEnemy = target
        val logs = mutableListOf<String>()
        logs += "${actor.name} 使用技能：${skill.name}"
        var applied = false

        skill.effects.forEach { effect ->
            when (effect.type.uppercase()) {
                "DAMAGE" -> {
                    val multiplier = effect.value ?: 1.0
                    val result = resolveAttack(nextPlayer, nextEnemy, multiplier)
                    nextPlayer = result.player
                    nextEnemy = result.enemy
                    logs += result.logs
                    applied = true
                }
                "HEAL_MAX_HP" -> {
                    val rate = effect.value ?: 0.1
                    val amount = max(1, (nextPlayer.stats.hpMax * rate).toInt())
                    val nextHp = (nextPlayer.hp + amount).coerceAtMost(nextPlayer.stats.hpMax)
                    nextPlayer = nextPlayer.withHp(nextHp)
                    val log = "${nextPlayer.name} 回复生命 $amount，生命 ${nextHp}/${nextPlayer.stats.hpMax}"
                    logs += log
                    GameLogger.info("战斗", log)
                    applied = true
                }
                "ROOT" -> {
                    val duration = (effect.value ?: 1.0).toInt().coerceAtLeast(1)
                    val stun = StatusInstance(type = StatusType.STUN, remainingTurns = duration, stacks = 1, sourceId = actor.id)
                    nextEnemy = addStatus(nextEnemy, stun)
                    val log = "${nextEnemy.name} 被束缚，无法行动 ${duration} 回合"
                    logs += log
                    GameLogger.info("战斗", log)
                    applied = true
                }
            }
        }

        if (!applied) {
            val log = "技能 ${skill.name} 在战斗中暂时没有效果。"
            logs += log
            GameLogger.info("战斗", log)
        }

        return ActionResult(
            player = nextPlayer,
            enemy = nextEnemy,
            logs = logs,
            cooldown = skill.cooldown.coerceAtLeast(0)
        )
    }

    private fun resolveItemAction(actor: CombatActor, enemy: CombatActor): ActionResult {
        val healRate = 0.25
        val amount = max(1, (actor.stats.hpMax * healRate).toInt())
        val nextHp = (actor.hp + amount).coerceAtMost(actor.stats.hpMax)
        val nextActor = actor.withHp(nextHp)
        val log = "${actor.name} 使用药丸，回复生命 $amount（${nextHp}/${actor.stats.hpMax}）"
        GameLogger.info("战斗", log)
        return ActionResult(
            player = nextActor,
            enemy = enemy,
            logs = listOf(log)
        )
    }

    private fun resolveEquipAction(
        actor: CombatActor,
        enemy: CombatActor,
        currentMode: EquipmentMode,
        baseStats: CombatStats
    ): ActionResult {
        val nextMode = when (currentMode) {
            EquipmentMode.NORMAL, EquipmentMode.DEFENSE -> EquipmentMode.OFFENSE
            EquipmentMode.OFFENSE -> EquipmentMode.DEFENSE
        }
        val adjusted = applyEquipmentMode(actor, nextMode, baseStats)
        val log = when (nextMode) {
            EquipmentMode.OFFENSE -> "切换进攻装备：攻击提升，防御下降。"
            EquipmentMode.DEFENSE -> "切换防御装备：防御提升，攻击下降。"
            EquipmentMode.NORMAL -> "恢复默认装备配置。"
        }
        GameLogger.info("战斗", log)
        return ActionResult(
            player = adjusted,
            enemy = enemy,
            logs = listOf(log),
            mode = nextMode
        )
    }

    private fun resolveFleeAction(player: CombatActor, enemy: CombatActor): ActionResult {
        val deltaSpeed = player.stats.speed - enemy.stats.speed
        val baseChance = 0.6 + deltaSpeed * 0.02
        val chance = baseChance.coerceIn(0.2, 0.9)
        val roll = rng.nextDouble()
        val success = roll <= chance
        val log = if (success) {
            "尝试撤离成功（成功率 ${(chance * 100).toInt()}%，判定 ${"%.2f".format(roll)}）"
        } else {
            "撤离失败（成功率 ${(chance * 100).toInt()}%，判定 ${"%.2f".format(roll)}）"
        }
        GameLogger.info("战斗", log)
        return ActionResult(
            player = player,
            enemy = enemy,
            logs = listOf(log),
            escaped = success
        )
    }

    private fun applyEquipmentMode(actor: CombatActor, mode: EquipmentMode, baseStats: CombatStats): CombatActor {
        val stats = when (mode) {
            EquipmentMode.NORMAL -> baseStats
            EquipmentMode.OFFENSE -> baseStats.copy(
                atk = max(1, (baseStats.atk * 1.2).toInt()),
                def = max(1, (baseStats.def * 0.9).toInt())
            )
            EquipmentMode.DEFENSE -> baseStats.copy(
                atk = max(1, (baseStats.atk * 0.9).toInt()),
                def = max(1, (baseStats.def * 1.2).toInt())
            )
        }
        return actor.copy(stats = stats)
    }

    private fun applyStartOfTurn(actor: CombatActor): TurnResult {
        val logs = mutableListOf<String>()
        val stun = actor.statuses.firstOrNull { it.type == StatusType.STUN }
        if (stun != null && stun.remainingTurns > 0) {
            val log = "${actor.name} 眩晕中，无法行动（剩余${stun.remainingTurns}回合）"
            logs += log
            GameLogger.info("战斗", log)
            return TurnResult(actor = actor, logs = logs, skipTurn = true)
        }
        return TurnResult(actor = actor, logs = logs, skipTurn = false)
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
            GameLogger.info("战斗", log)
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

    private fun critChance(crit: Int, resist: Int): Double {
        val effective = (crit - resist).coerceAtLeast(0)
        return (effective / 100.0).coerceIn(0.0, 0.6)
    }

    private fun effectiveSpeed(actor: CombatActor): Int {
        val hasteStacks = actor.statuses.filter { it.type == StatusType.HASTE }.sumOf { it.stacks }
        val slowStacks = actor.statuses.filter { it.type == StatusType.SLOW }.sumOf { it.stacks }
        val multiplier = (1.0 + 0.2 * hasteStacks - 0.2 * slowStacks).coerceAtLeast(0.5)
        return max(1, (actor.stats.speed * multiplier).toInt())
    }

    private data class ActionResult(
        val player: CombatActor,
        val enemy: CombatActor,
        val logs: List<String>,
        val cooldown: Int = 0,
        val mode: EquipmentMode? = null,
        val escaped: Boolean = false
    )

    private data class TurnResult(
        val actor: CombatActor,
        val logs: List<String>,
        val skipTurn: Boolean
    )
}
