package com.jungleadventure.shared

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class GameViewModel(resourceReader: ResourceReader) {
    private val repository = GameContentRepository(resourceReader)
    private val rng = Random.Default
    private val events = runCatching { repository.loadEvents() }.getOrElse { emptyList() }
    private val engine = EventEngine(events)
    private val maxChapter = events.maxOfOrNull { it.chapter } ?: 1
    private val roles = loadRoleProfiles()

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    init {
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            current.copy(
                roles = roles,
                selectedRoleId = initialRole?.id ?: ""
            )
        }
        if (initialRole != null) {
            applyRole(initialRole, reason = "初始角色")
        }
        advanceToNextEvent(incrementTurn = false)
    }

    fun onSelectRole(roleId: String) {
        val role = roles.firstOrNull { it.id == roleId } ?: return
        if (!role.unlocked) {
            _state.update { it.copy(lastAction = "角色未解锁") }
            return
        }
        applyRole(role, reason = "选择角色")
    }

    fun onSelectChoice(choiceId: String) {
        val currentEvent = _state.value.currentEvent
        if (currentEvent == null) {
            advanceToNextEvent(incrementTurn = true)
            return
        }

        if (choiceId == "advance") {
            advanceToNextEvent(incrementTurn = true)
            return
        }

        val option = currentEvent.options.firstOrNull { it.optionId == choiceId }
        val result = option?.result ?: currentEvent.result

        _state.update { current ->
            val updatedPlayer = result?.let { applyResult(current.player, it) } ?: current.player
            current.copy(
                player = updatedPlayer,
                lastAction = "选择: ${option?.text ?: choiceId}",
                log = current.log + listOfNotNull(
                    currentEvent.logText.ifBlank { null },
                    result?.let { summarizeResult(it) }
                )
            )
        }

        advanceToNextEvent(incrementTurn = true)
    }

    fun onOpenStatus() {
        _state.update { it.copy(activePanel = GamePanel.STATUS, lastAction = "查看状态") }
    }

    fun onOpenEquipment() {
        _state.update { it.copy(activePanel = GamePanel.EQUIPMENT, lastAction = "查看装备") }
    }

    fun onOpenInventory() {
        _state.update { it.copy(activePanel = GamePanel.INVENTORY, lastAction = "查看背包") }
    }

    fun onAdvance() {
        advanceToNextEvent(incrementTurn = true)
    }

    private fun advanceToNextEvent(incrementTurn: Boolean) {
        _state.update { current ->
            val nextTurn = if (incrementTurn) current.turn + 1 else current.turn
            val chapter = chapterForTurn(nextTurn)
            val nextEvent = if (events.isNotEmpty()) {
                engine.nextEvent(chapter, rng)
            } else {
                null
            }

            val nextChoices = nextEvent?.let { engine.toChoices(it) } ?: listOf(
                GameChoice("advance", "继续")
            )

            current.copy(
                turn = nextTurn,
                chapter = chapter,
                currentEvent = nextEvent,
                choices = nextChoices,
                log = current.log + listOfNotNull(nextEvent?.introText)
            )
        }
    }

    private fun chapterForTurn(turn: Int): Int {
        val raw = ((turn - 1) / 3) + 1
        return min(maxChapter, max(1, raw))
    }

    private fun applyRole(role: RoleProfile, reason: String) {
        _state.update { current ->
            current.copy(
                player = toPlayerStats(role),
                selectedRoleId = role.id,
                lastAction = "$reason: ${role.name}",
                log = current.log + "已选择角色：${role.name}"
            )
        }
    }

    private fun toPlayerStats(role: RoleProfile): PlayerStats {
        return PlayerStats(
            name = role.name,
            hp = role.stats.hp,
            hpMax = role.stats.hp,
            mp = 30,
            mpMax = 30,
            atk = role.stats.atk,
            def = role.stats.def,
            speed = role.stats.speed,
            level = 1,
            gold = 0,
            materials = 0
        )
    }

    private fun applyResult(player: PlayerStats, result: EventResult): PlayerStats {
        val nextHp = (player.hp + result.hpDelta).coerceIn(0, player.hpMax)
        val nextMp = (player.mp + result.mpDelta).coerceIn(0, player.mpMax)
        return player.copy(
            hp = nextHp,
            mp = nextMp,
            gold = player.gold + result.goldDelta
        )
    }

    private fun summarizeResult(result: EventResult): String {
        val parts = mutableListOf<String>()
        if (result.hpDelta != 0) parts += "HP ${signed(result.hpDelta)}"
        if (result.mpDelta != 0) parts += "MP ${signed(result.mpDelta)}"
        if (result.goldDelta != 0) parts += "金币 ${signed(result.goldDelta)}"
        if (result.expDelta != 0) parts += "经验 ${signed(result.expDelta)}"
        if (result.dropTableId != null) parts += "掉落表 ${result.dropTableId}"
        return if (parts.isEmpty()) "没有额外变化" else parts.joinToString("，")
    }

    private fun signed(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    private fun loadRoleProfiles(): List<RoleProfile> {
        val characters = runCatching { repository.loadCharacters().characters }.getOrElse { emptyList() }
        val skills = runCatching { repository.loadSkills().skills }.getOrElse { emptyList() }
        if (characters.isEmpty()) {
            return defaultRoles()
        }
        val skillMap = skills.associateBy { it.id }
        return characters.map { character ->
            val passiveSkill = skillMap[character.passiveSkillId].toRoleSkill()
            val activeSkill = skillMap[character.activeSkillIds.firstOrNull()].toRoleSkill()
            RoleProfile(
                id = character.id,
                name = character.name,
                role = character.role,
                stats = character.stats,
                passiveSkill = passiveSkill,
                activeSkill = activeSkill,
                starting = character.starting,
                unlock = character.unlock,
                unlocked = character.starting
            )
        }
    }

    private fun SkillDefinition?.toRoleSkill(): RoleSkill {
        if (this == null) {
            return RoleSkill(
                name = "未知技能",
                type = "PASSIVE",
                description = "未配置技能。",
                cost = "-",
                cooldown = "-"
            )
        }
        val costLabel = if (cost <= 0) "-" else "消耗 $cost"
        val cooldownLabel = if (cooldown <= 0) "-" else "$cooldown 回合"
        return RoleSkill(
            name = name,
            type = type,
            description = desc,
            cost = costLabel,
            cooldown = cooldownLabel
        )
    }
}
