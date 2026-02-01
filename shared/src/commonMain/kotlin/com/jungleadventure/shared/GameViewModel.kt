package com.jungleadventure.shared

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GameViewModel(
    resourceReader: ResourceReader,
    private val saveStore: SaveStore = defaultSaveStore()
) {
    private val logTag = "GameViewModel"
    private val repository = GameContentRepository(resourceReader)
    private val rng = Random.Default
    private val events = runCatching { repository.loadEvents() }.getOrElse { emptyList() }
    private val engine = EventEngine(events)
    private val maxChapter = events.maxOfOrNull { it.chapter } ?: 1
    private val roles = loadRoleProfiles()
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    init {
        GameLogger.info(
            logTag,
            "初始化完成：事件数=${events.size}，最大章节=$maxChapter，角色数=${roles.size}"
        )
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            current.copy(
                roles = roles,
                selectedRoleId = initialRole?.id ?: ""
            )
        }
        if (initialRole != null) {
            GameLogger.info(logTag, "自动选择初始角色：${initialRole.name}")
            applyRole(initialRole, reason = "初始角色")
        } else {
            GameLogger.warn(logTag, "未找到可用的初始角色")
        }
        advanceToNextEvent(incrementTurn = false)
        refreshSaveSlots()
    }

    fun onSelectRole(roleId: String) {
        GameLogger.info(logTag, "收到角色选择请求：roleId=$roleId")
        val role = roles.firstOrNull { it.id == roleId } ?: return
        if (!role.unlocked) {
            GameLogger.warn(logTag, "角色未解锁：${role.name}，解锁条件=${role.unlock}")
            _state.update { it.copy(lastAction = "角色未解锁") }
            return
        }
        applyRole(role, reason = "选择角色")
    }

    fun onSelectChoice(choiceId: String) {
        GameLogger.info(logTag, "收到事件选项：choiceId=$choiceId")
        val currentEvent = _state.value.currentEvent
        if (currentEvent == null) {
            GameLogger.warn(logTag, "当前事件为空，直接进入下一个事件")
            advanceToNextEvent(incrementTurn = true)
            return
        }

        if (choiceId == "advance") {
            GameLogger.info(logTag, "选择继续前进")
            advanceToNextEvent(incrementTurn = true)
            return
        }

        val option = currentEvent.options.firstOrNull { it.optionId == choiceId }
        val result = option?.result ?: currentEvent.result
        GameLogger.info(
            logTag,
            "结算选项：事件=${currentEvent.title}，选项=${option?.text ?: "默认"}"
        )

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
        GameLogger.info(logTag, "切换面板：状态")
        _state.update { it.copy(activePanel = GamePanel.STATUS, lastAction = "查看状态") }
    }

    fun onOpenEquipment() {
        GameLogger.info(logTag, "切换面板：装备")
        _state.update { it.copy(activePanel = GamePanel.EQUIPMENT, lastAction = "查看装备") }
    }

    fun onOpenInventory() {
        GameLogger.info(logTag, "切换面板：背包")
        _state.update { it.copy(activePanel = GamePanel.INVENTORY, lastAction = "查看背包") }
    }

    fun onAdvance() {
        GameLogger.info(logTag, "点击继续前进")
        advanceToNextEvent(incrementTurn = true)
    }

    fun onSave(slot: Int) {
        GameLogger.info("SaveSystem", "准备存档，槽位=$slot，回合=${_state.value.turn}")
        val snapshot = _state.value
        val saveGame = SaveGame(
            turn = snapshot.turn,
            chapter = snapshot.chapter,
            selectedRoleId = snapshot.selectedRoleId,
            player = snapshot.player,
            log = snapshot.log,
            lastAction = snapshot.lastAction,
            activePanel = snapshot.activePanel,
            currentEventId = snapshot.currentEvent?.eventId
        )
        runCatching {
            val payload = json.encodeToString(saveGame)
            saveStore.save(slot, payload)
        }.onFailure { error ->
            GameLogger.error("SaveSystem", "存档失败，槽位=$slot", error)
            _state.update { current ->
                current.copy(
                    lastAction = "存档失败：槽位 $slot",
                    log = current.log + "存档失败：槽位 $slot"
                )
            }
            return
        }

        _state.update { current ->
            current.copy(
                lastAction = "已存档到槽位 $slot",
                log = current.log + "已存档到槽位 $slot"
            )
        }
        refreshSaveSlots()
        GameLogger.info("SaveSystem", "存档完成，槽位=$slot")
    }

    fun onLoad(slot: Int) {
        GameLogger.info("SaveSystem", "准备读档，槽位=$slot")
        val payload = runCatching { saveStore.load(slot) }
            .onFailure { error ->
                GameLogger.error("SaveSystem", "读取存档失败，槽位=$slot", error)
                _state.update { current ->
                    current.copy(
                        lastAction = "读档失败：槽位 $slot",
                        log = current.log + "读档失败：槽位 $slot"
                    )
                }
                return
            }
            .getOrNull()

        if (payload.isNullOrBlank()) {
            _state.update { current ->
                current.copy(
                    lastAction = "存档槽 $slot 为空",
                    log = current.log + "存档槽 $slot 为空"
                )
            }
            GameLogger.warn("SaveSystem", "存档槽为空，槽位=$slot")
            refreshSaveSlots()
            return
        }

        val saveGame = runCatching { json.decodeFromString<SaveGame>(payload) }
            .onFailure { error ->
                GameLogger.error("SaveSystem", "解析存档失败，槽位=$slot", error)
                _state.update { current ->
                    current.copy(
                        lastAction = "读档失败：槽位 $slot",
                        log = current.log + "读档失败：槽位 $slot"
                    )
                }
                return
            }
            .getOrNull()
            ?: return

        applySaveGame(slot, saveGame)
        refreshSaveSlots()
        GameLogger.info("SaveSystem", "读档完成，槽位=$slot，回合=${saveGame.turn}")
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

            GameLogger.info(
                logTag,
                "推进事件：turn=$nextTurn，chapter=$chapter，event=${nextEvent?.eventId ?: "无"}，选项数=${nextChoices.size}"
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

    private fun applySaveGame(slot: Int, saveGame: SaveGame) {
        val event = saveGame.currentEventId?.let { id ->
            events.firstOrNull { it.eventId == id }
        }
        if (saveGame.currentEventId != null && event == null) {
            GameLogger.warn("SaveSystem", "存档事件未找到，eventId=${saveGame.currentEventId}")
        }

        val choices = event?.let { engine.toChoices(it) } ?: listOf(
            GameChoice("advance", "继续")
        )

        val validRoleId = if (roles.any { it.id == saveGame.selectedRoleId }) {
            saveGame.selectedRoleId
        } else {
            roles.firstOrNull { it.unlocked }?.id ?: ""
        }

        _state.update { current ->
            current.copy(
                turn = saveGame.turn,
                chapter = saveGame.chapter,
                selectedRoleId = validRoleId,
                player = saveGame.player,
                currentEvent = event,
                choices = choices,
                activePanel = saveGame.activePanel,
                lastAction = "已读取槽位 $slot",
                log = saveGame.log + "已读取槽位 $slot",
                saveSlots = current.saveSlots
            )
        }
    }

    private fun chapterForTurn(turn: Int): Int {
        val raw = ((turn - 1) / 3) + 1
        return min(maxChapter, max(1, raw))
    }

    private fun applyRole(role: RoleProfile, reason: String) {
        GameLogger.info(
            logTag,
            "应用角色：${role.name}，原因=$reason，属性=HP${role.stats.hp}/ATK${role.stats.atk}/DEF${role.stats.def}/SPD${role.stats.speed}"
        )
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
        GameLogger.info(
            logTag,
            "结算结果：HP${signed(result.hpDelta)} MP${signed(result.mpDelta)} 金币${signed(result.goldDelta)} 经验${signed(result.expDelta)}"
        )
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
        GameLogger.info(logTag, "开始加载角色与技能配置")
        val characters = runCatching { repository.loadCharacters().characters }.getOrElse { emptyList() }
        val skills = runCatching { repository.loadSkills().skills }.getOrElse { emptyList() }
        if (characters.isEmpty()) {
            GameLogger.warn(logTag, "角色配置为空，使用默认角色")
            return defaultRoles()
        }
        val skillMap = skills.associateBy { it.id }
        val profiles = characters.map { character ->
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
        GameLogger.info(logTag, "角色加载完成，数量=${profiles.size}")
        return profiles
    }

    private fun SkillDefinition?.toRoleSkill(): RoleSkill {
        if (this == null) {
            GameLogger.warn(logTag, "技能配置缺失，使用默认技能占位")
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

    private fun refreshSaveSlots() {
        val summaries = (1..8).map { slot ->
            val payload = runCatching { saveStore.load(slot) }.getOrNull()
            if (payload.isNullOrBlank()) {
                SaveSlotSummary(
                    slot = slot,
                    title = "槽位 $slot：空",
                    detail = "暂无存档",
                    hasData = false
                )
            } else {
                val saveGame = runCatching { json.decodeFromString<SaveGame>(payload) }.getOrNull()
                if (saveGame == null) {
                    SaveSlotSummary(
                        slot = slot,
                        title = "槽位 $slot：损坏",
                        detail = "存档解析失败",
                        hasData = true
                    )
                } else {
                    SaveSlotSummary(
                        slot = slot,
                        title = "槽位 $slot：第 ${saveGame.turn} 回合",
                        detail = "角色 ${saveGame.player.name} | 章节 ${saveGame.chapter}",
                        hasData = true
                    )
                }
            }
        }

        _state.update { current ->
            current.copy(saveSlots = summaries)
        }
    }
}
