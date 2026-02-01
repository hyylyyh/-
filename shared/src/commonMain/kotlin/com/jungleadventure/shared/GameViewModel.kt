package com.jungleadventure.shared

import com.jungleadventure.shared.loot.EquipmentInstance
import com.jungleadventure.shared.loot.EquipmentSlot
import com.jungleadventure.shared.loot.LootOutcome
import com.jungleadventure.shared.loot.LootRepository
import com.jungleadventure.shared.loot.LootSourceType
import com.jungleadventure.shared.loot.StatType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val logTag = "界面模型"
    private val levelMax = 50
    private val expBase = 30
    private val expGrowth = 20
    private val repository = GameContentRepository(resourceReader)
    private val rng = Random.Default
    private val characterDefinitions = runCatching { repository.loadCharacters().characters }.getOrElse { emptyList() }
    private val skillDefinitions = runCatching { repository.loadSkills().skills }.getOrElse { emptyList() }
    private val roleActiveSkillMap = characterDefinitions.associate { it.id to it.activeSkillIds.firstOrNull() }
    private val events = runCatching { repository.loadEvents() }.getOrElse { emptyList() }
    private val engine = EventEngine(events)
    private val stageBundle = loadStageBundle()
    private val stageEngine = StageEngine(stageBundle.stages, stageBundle.nodes, engine)
    private var stageRuntime: StageRuntime? = null
    private var rngSeed: Long = Random.Default.nextLong()
    private val maxChapter = events.maxOfOrNull { it.chapter } ?: 1
    private val roles = loadRoleProfiles()
    private val enemyRepository = loadEnemyRepository()
    private val battleSystem = BattleSystem(enemyRepository, rng)
    private val turnEngine = TurnBasedCombatEngine(rng)
    private val lootRepository = LootRepository()
    private var battleSession: BattleSession? = null
    private var battleEventId: String? = null
    private var pendingNewSaveSlot: Int? = null
    private val autoSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val autoSaveIntervalMs = 10_000L
    private var autoSaveSlot: Int? = null
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
            "初始化完成：事件数量=${events.size}，最大章节=$maxChapter，角色数量=${roles.size}"
        )
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            current.copy(
                roles = roles,
                selectedRoleId = initialRole?.id ?: "",
                screen = GameScreen.SAVE_SELECT,
                lastAction = "请选择存档",
                log = listOf("请选择存档：读取已有存档或创建新存档")
            )
        }
        if (initialRole != null) {
            GameLogger.info(logTag, "初始角色候选：${initialRole.name}")
        } else {
            GameLogger.warn(logTag, "未找到可用的初始角色")
        }
        refreshSaveSlots()
        startAutoSaveLoop()
    }

    fun onSelectRole(roleId: String) {
        GameLogger.info(logTag, "收到角色选择请求：角色编号=$roleId")
        if (_state.value.screen != GameScreen.ROLE_SELECT) {
            GameLogger.warn(logTag, "当前界面不允许切换角色")
            return
        }
        val role = roles.firstOrNull { it.id == roleId } ?: return
        if (!role.unlocked) {
            GameLogger.warn(logTag, "角色未解锁：${role.name}，解锁条件=${role.unlock}")
            _state.update { it.copy(lastAction = "角色未解锁") }
            return
        }
        _state.update { it.copy(selectedRoleId = role.id, lastAction = "已选择角色：${role.name}") }
    }

    fun onConfirmRole() {
        GameLogger.info(logTag, "确认角色并进入冒险")
        if (_state.value.screen != GameScreen.ROLE_SELECT) {
            GameLogger.warn(logTag, "当前界面不允许确认角色")
            return
        }
        val role = roles.firstOrNull { it.id == _state.value.selectedRoleId }
        if (role == null) {
            _state.update { it.copy(lastAction = "请先选择角色", log = it.log + "请先选择角色") }
            return
        }
        if (!role.unlocked) {
            _state.update { it.copy(lastAction = "角色未解锁", log = it.log + "角色未解锁") }
            return
        }
        applyRole(role, reason = "确认角色")
        startStageForTurn(1, reason = "新建存档")
        val slot = pendingNewSaveSlot
        if (slot != null) {
            autoSaveSlot = slot
            _state.update { current ->
                current.copy(
                    screen = GameScreen.ADVENTURE,
                    selectedSaveSlot = slot,
                    lastAction = "已确认角色，进入冒险"
                )
            }
            onSave(slot)
        } else {
            _state.update { current ->
                current.copy(
                    screen = GameScreen.ADVENTURE,
                    lastAction = "已确认角色，进入冒险"
                )
            }
        }
    }

    fun onSelectChoice(choiceId: String) {
        if (_state.value.screen != GameScreen.ADVENTURE) {
            GameLogger.warn(logTag, "未进入冒险界面，无法选择行动")
            return
        }
        GameLogger.info(logTag, "收到事件选项：选项编号=$choiceId")
        val currentEvent = _state.value.currentEvent
        if (currentEvent == null) {
            GameLogger.warn(logTag, "当前事件为空，直接进入下一个事件")
            advanceToNextNode(incrementTurn = true)
            return
        }

        if (isBattleEvent(currentEvent)) {
            handleBattleChoice(choiceId, currentEvent)
            return
        }

        if (choiceId == "继续") {
            GameLogger.info(logTag, "选择继续前进")
            advanceToNextNode(incrementTurn = true)
            return
        }

        val option = currentEvent.options.firstOrNull { it.optionId == choiceId }
        val result = option?.result ?: currentEvent.result
        GameLogger.info(
            logTag,
            "结算选项：事件=${currentEvent.title}，选项=${option?.text ?: "默认"}"
        )
        val nextEventId = result?.nextEventId
        val nextNodeId = result?.nextNodeId

        _state.update { current ->
            val applied = applyEventResult(current.player, result, currentEvent, "事件结算")
            current.copy(
                player = applied.player,
                lastAction = "选择: ${option?.text ?: choiceId}",
                log = current.log + listOfNotNull(
                    currentEvent.logText.ifBlank { null },
                    result?.let { summarizeResult(it) }
                ) + applied.logs
            )
        }

        advanceToNextNode(
            incrementTurn = true,
            forcedEventId = nextEventId,
            forcedNodeId = nextNodeId
        )
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

    fun onToggleShowSkillFormula(enabled: Boolean) {
        val status = if (enabled) "显示" else "隐藏"
        GameLogger.info(logTag, "设置变更：技能伤害公式=$status")
        _state.update { current ->
            current.copy(
                showSkillFormula = enabled,
                lastAction = "技能伤害公式已$status",
                log = current.log + "设置：技能伤害公式已$status"
            )
        }
    }

    fun onEquipItem(itemId: String) {
        val current = _state.value
        if (current.screen != GameScreen.ADVENTURE) {
            GameLogger.warn("装备系统", "未进入冒险界面，无法装备")
            return
        }
        if (current.battle != null) {
            GameLogger.warn("装备系统", "战斗中禁止更换装备")
            _state.update { it.copy(lastAction = "战斗中无法更换装备", log = it.log + "战斗中无法更换装备") }
            return
        }
        val inventory = current.player.inventory
        val target = inventory.items.firstOrNull { it.uid == itemId }
        if (target == null) {
            GameLogger.warn("装备系统", "未找到待装备物品：$itemId")
            return
        }
        val loadout = current.player.equipment
        val equipped = loadout.equipped(target.slot)
        val remaining = inventory.items.filterNot { it.uid == itemId }
        val newInventory = if (equipped == null) remaining else remaining + equipped
        val nextLoadout = loadout.copy(slots = loadout.slots + (target.slot to target))
        val updatedPlayer = recalculatePlayerStats(
            current.player.copy(
                equipment = nextLoadout,
                inventory = inventory.copy(items = newInventory)
            ),
            "装备 ${target.name}"
        )
        GameLogger.info(
            "装备系统",
            "装备完成：${target.name} 槽位=${target.slot} 替换=${equipped?.name ?: "无"}"
        )
        val logLine = if (equipped == null) {
            "装备 ${target.name}（${target.rarityName}）"
        } else {
            "装备 ${target.name}（${target.rarityName}），替换 ${equipped.name}"
        }
        _state.update {
            it.copy(
                player = updatedPlayer,
                lastAction = "已装备 ${target.name}",
                log = it.log + logLine
            )
        }
    }

    fun onUnequipSlot(slot: EquipmentSlot) {
        val current = _state.value
        if (current.screen != GameScreen.ADVENTURE) {
            GameLogger.warn("装备系统", "未进入冒险界面，无法卸下装备")
            return
        }
        if (current.battle != null) {
            GameLogger.warn("装备系统", "战斗中禁止更换装备")
            _state.update { it.copy(lastAction = "战斗中无法更换装备", log = it.log + "战斗中无法更换装备") }
            return
        }
        val loadout = current.player.equipment
        val item = loadout.equipped(slot)
        if (item == null) {
            GameLogger.warn("装备系统", "该槽位暂无装备：$slot")
            return
        }
        val inventory = current.player.inventory
        if (inventory.items.size >= inventory.capacity) {
            GameLogger.warn("装备系统", "背包已满，无法卸下装备：${item.name}")
            _state.update { it.copy(lastAction = "背包已满，无法卸下装备", log = it.log + "背包已满，无法卸下装备") }
            return
        }
        val nextLoadout = loadout.copy(slots = loadout.slots - slot)
        val nextInventory = inventory.copy(items = inventory.items + item)
        val updatedPlayer = recalculatePlayerStats(
            current.player.copy(equipment = nextLoadout, inventory = nextInventory),
            "卸下 ${item.name}"
        )
        GameLogger.info("装备系统", "卸下装备：${item.name} 槽位=$slot")
        _state.update {
            it.copy(
                player = updatedPlayer,
                lastAction = "已卸下 ${item.name}",
                log = it.log + "卸下 ${item.name}"
            )
        }
    }

    fun onAdvance() {
        GameLogger.info(logTag, "点击继续前进")
        if (_state.value.screen != GameScreen.ADVENTURE) {
            GameLogger.warn(logTag, "未进入冒险界面，无法继续前进")
            return
        }
        val currentEvent = _state.value.currentEvent
        if (currentEvent != null && isBattleEvent(currentEvent)) {
            GameLogger.info(logTag, "战斗事件中不支持继续前进")
        } else {
            advanceToNextNode(incrementTurn = true)
        }
    }

    fun onSave(slot: Int) {
        if (_state.value.screen != GameScreen.ADVENTURE) {
            GameLogger.warn("存档系统", "未进入冒险界面，无法存档")
            return
        }
        GameLogger.info("存档系统", "准备存档，槽位=$slot，回合=${_state.value.turn}")
        val saveGame = buildSaveGameSnapshot()
        runCatching {
            val payload = json.encodeToString(saveGame)
            saveStore.save(slot, payload)
        }.onFailure { error ->
            GameLogger.error("存档系统", "存档失败，槽位=$slot", error)
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
        autoSaveSlot = slot
        GameLogger.info("存档系统", "自动存档槽位已更新为 $slot")
        GameLogger.info("存档系统", "存档完成，槽位=$slot")
    }

    fun onLoad(slot: Int) {
        GameLogger.info("存档系统", "准备读档，槽位=$slot")
        val payload = runCatching { saveStore.load(slot) }
            .onFailure { error ->
                GameLogger.error("存档系统", "读取存档失败，槽位=$slot", error)
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
            GameLogger.warn("存档系统", "存档槽为空，槽位=$slot")
            refreshSaveSlots()
            return
        }

        val saveGame = runCatching { json.decodeFromString<SaveGame>(payload) }
            .onFailure { error ->
                GameLogger.error("存档系统", "解析存档失败，槽位=$slot", error)
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
        if (saveGame.rngSeed > 0) {
            rngSeed = saveGame.rngSeed
            GameLogger.info("存档系统", "恢复随机种子：随机种子=$rngSeed")
        }
        applySaveGame(slot, saveGame)
        autoSaveSlot = slot
        GameLogger.info("存档系统", "自动存档槽位已更新为 $slot")
        _state.update { current ->
            current.copy(
                screen = GameScreen.ADVENTURE,
                selectedSaveSlot = slot,
                lastAction = "已读取槽位 $slot"
            )
        }
        refreshSaveSlots()
        GameLogger.info("存档系统", "读档完成，槽位=$slot，回合=${saveGame.turn}")
    }

    fun onCreateNewSave(slot: Int) {
        GameLogger.info("存档系统", "创建新存档：槽位=$slot")
        pendingNewSaveSlot = slot
        battleSession = null
        battleEventId = null
        stageRuntime = null
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            current.copy(
                screen = GameScreen.ROLE_SELECT,
                selectedSaveSlot = slot,
                selectedRoleId = initialRole?.id ?: "",
                turn = 1,
                chapter = 1,
                stage = StageUiState(),
                player = PlayerStats(),
                currentEvent = null,
                enemyPreview = null,
                battle = null,
                choices = emptyList(),
                lastAction = "已选择新存档槽位 $slot",
                log = current.log + "已选择新存档槽位 $slot，请选择角色"
            )
        }
    }

    private fun advanceToNextNode(
        incrementTurn: Boolean,
        forcedEventId: String? = null,
        forcedNodeId: String? = null
    ) {
        battleSession = null
        battleEventId = null
        val current = _state.value
        val nextTurn = if (incrementTurn) current.turn + 1 else current.turn
        val chapter = chapterForTurn(nextTurn)
        val runtime = stageRuntime ?: assignGuardian(stageEngine.startStageForChapter(chapter, rng))
        val shouldRestartStage = runtime.completed || runtime.stage.chapter != chapter
        val nextRuntime = if (shouldRestartStage) {
            assignGuardian(stageEngine.startStageForChapter(chapter, rng))
        } else if (!forcedNodeId.isNullOrBlank()) {
            stageEngine.moveToNode(runtime, forcedNodeId) ?: stageEngine.moveToNextNode(runtime, rng)
        } else {
            stageEngine.moveToNextNode(runtime, rng)
        }
        stageRuntime = nextRuntime
        val node = stageEngine.currentNode(nextRuntime)
        val forcedEvent = if (!forcedEventId.isNullOrBlank()) {
            engine.eventById(forcedEventId)
        } else {
            null
        }
        val nextEvent = forcedEvent ?: resolveEventForNode(nextRuntime, node, chapter)
        val nextChoices = nextEvent?.let { engine.toChoices(it) } ?: listOf(
            GameChoice("继续", "继续")
        )

        val stageLog = if (shouldRestartStage) {
            "进入关卡：${nextRuntime.stage.name}"
        } else {
            val nodeId = node?.id ?: nextRuntime.currentNodeId
            if (nodeId.contains("hidden", ignoreCase = true)) {
                "发现隐藏路径：$nodeId"
            } else {
                "移动到节点：$nodeId"
            }
        }
        val commandLog = if (shouldRestartStage && nextRuntime.command.isNotBlank()) {
            "关卡口令：${nextRuntime.command}"
        } else {
            null
        }
        val guardianLog = if (shouldRestartStage && nextRuntime.guardianGroupId != null) {
            "守卫已就位：${guardianNameForGroup(nextRuntime.guardianGroupId)}"
        } else {
            null
        }

        GameLogger.info(
            logTag,
            "推进关卡：回合=$nextTurn，章节=$chapter，关卡编号=${nextRuntime.stage.id} 节点编号=${node?.id ?: "无"} 事件编号=${nextEvent?.eventId ?: "无"} 强制事件编号=${forcedEventId ?: "无"}"
        )

        _state.update { state ->
            val enemyPreview = buildEnemyPreview(nextEvent, state.player)
            state.copy(
                turn = nextTurn,
                chapter = chapter,
                stage = nextRuntime.toUiState(node),
                currentEvent = nextEvent,
                enemyPreview = enemyPreview,
                battle = null,
                choices = nextChoices,
                log = state.log + listOf(stageLog) + listOfNotNull(commandLog, guardianLog, nextEvent?.introText)
            )
        }
        if (nextEvent != null && isBattleEvent(nextEvent)) {
            startBattleSession(nextEvent)
        }
    }

    private fun applySaveGame(slot: Int, saveGame: SaveGame) {
        battleSession = null
        battleEventId = null
        pendingNewSaveSlot = null
        val runtime = stageEngine.restoreStage(
            stageId = saveGame.stageId,
            nodeId = saveGame.nodeId,
            visitedNodes = saveGame.visitedNodes,
            completed = saveGame.stageCompleted,
            chapter = saveGame.chapter,
            rng = rng,
            guardianGroupId = saveGame.guardianGroupId
        )
        val assignedRuntime = if (runtime.guardianGroupId == null) {
            assignGuardian(runtime)
        } else {
            runtime
        }
        stageRuntime = assignedRuntime

        val node = stageEngine.currentNode(assignedRuntime)
        val event = saveGame.currentEventId?.let { id ->
            engine.eventById(id)
        } ?: resolveEventForNode(assignedRuntime, node, saveGame.chapter)
        if (saveGame.currentEventId != null && event == null) {
            GameLogger.warn("存档系统", "存档事件未找到：事件编号=${saveGame.currentEventId}")
        }

        val choices = event?.let { engine.toChoices(it) } ?: listOf(
            GameChoice("继续", "继续")
        )
        val runtimeNode = node

        val validRoleId = if (roles.any { it.id == saveGame.selectedRoleId }) {
            saveGame.selectedRoleId
        } else {
            roles.firstOrNull { it.unlocked }?.id ?: ""
        }
        val normalizedPlayer = normalizePlayerStats(saveGame.player, "读取存档")

        _state.update { current ->
            val enemyPreview = buildEnemyPreview(event, normalizedPlayer)
            current.copy(
                turn = saveGame.turn,
                chapter = saveGame.chapter,
                stage = assignedRuntime.toUiState(runtimeNode),
                selectedRoleId = validRoleId,
                player = normalizedPlayer,
                currentEvent = event,
                enemyPreview = enemyPreview,
                battle = null,
                choices = choices,
                activePanel = saveGame.activePanel,
                showSkillFormula = saveGame.showSkillFormula,
                lastAction = "已读取槽位 $slot",
                log = saveGame.log + "已读取槽位 $slot",
                saveSlots = current.saveSlots
            )
        }
        if (event != null && isBattleEvent(event)) {
            startBattleSession(event)
        }
    }

    private fun startStageForTurn(turn: Int, reason: String) {
        if (_state.value.screen == GameScreen.SAVE_SELECT) {
            GameLogger.info(logTag, "尚未完成存档选择，跳过关卡初始化")
            return
        }
        val chapter = chapterForTurn(turn)
        val runtime = assignGuardian(stageEngine.startStageForChapter(chapter, rng))
        stageRuntime = runtime
        val node = stageEngine.currentNode(runtime)
        val event = resolveEventForNode(runtime, node, chapter)
        val choices = event?.let { engine.toChoices(it) } ?: listOf(
            GameChoice("继续", "继续")
        )
        val stageLog = if (reason.isBlank()) {
            "进入关卡：${runtime.stage.name}"
        } else {
            "$reason：${runtime.stage.name}"
        }
        val commandLog = if (runtime.command.isBlank()) null else "关卡口令：${runtime.command}"
        val guardianLog = runtime.guardianGroupId?.let { groupId ->
            "守卫已就位：${guardianNameForGroup(groupId)}"
        }
        GameLogger.info(
            logTag,
            "初始化关卡：回合=$turn 章节=$chapter 关卡编号=${runtime.stage.id} 节点编号=${node?.id ?: "无"} 随机种子=$rngSeed"
        )
        _state.update { current ->
            val enemyPreview = buildEnemyPreview(event, current.player)
            current.copy(
                turn = turn,
                chapter = chapter,
                stage = runtime.toUiState(node),
                currentEvent = event,
                enemyPreview = enemyPreview,
                battle = null,
                choices = choices,
                log = current.log + listOf(stageLog) + listOfNotNull(commandLog, guardianLog, event?.introText)
            )
        }
        if (event != null && isBattleEvent(event)) {
            startBattleSession(event)
        }
    }

    private fun chapterForTurn(turn: Int): Int {
        val raw = ((turn - 1) / 3) + 1
        return min(maxChapter, max(1, raw))
    }

    private fun applyRole(role: RoleProfile, reason: String) {
        GameLogger.info(
            logTag,
            "应用角色：${role.name}，原因=$reason，属性=生命${role.stats.hp}/攻击${role.stats.atk}/防御${role.stats.def}/速度${role.stats.speed}"
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
        val base = PlayerBaseStats(
            hpMax = role.stats.hp,
            atk = role.stats.atk,
            def = role.stats.def,
            speed = role.stats.speed
        )
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
            exp = 0,
            expToNext = expRequiredFor(1),
            gold = 0,
            materials = 0,
            baseStats = base,
            equipment = EquipmentLoadout(),
            inventory = InventoryState()
        )
    }

    private data class ResultApplication(
        val player: PlayerStats,
        val logs: List<String>
    )

    private data class LootApplication(
        val player: PlayerStats,
        val logs: List<String>
    )

    private fun applyEventResult(
        player: PlayerStats,
        result: EventResult?,
        event: EventDefinition?,
        reason: String
    ): ResultApplication {
        if (result == null) {
            val dropId = event?.dropTableId
            if (!dropId.isNullOrBlank()) {
                val loot = applyLoot(player, dropId, event, reason)
                return ResultApplication(loot.player, loot.logs)
            }
            return ResultApplication(player, emptyList())
        }
        val nextHp = (player.hp + result.hpDelta).coerceIn(0, player.hpMax)
        val nextMp = (player.mp + result.mpDelta).coerceIn(0, player.mpMax)
        GameLogger.info(
            logTag,
            "结算结果：生命${signed(result.hpDelta)} 能量${signed(result.mpDelta)} 金币${signed(result.goldDelta)} 经验${signed(result.expDelta)}，原因=$reason"
        )
        var updated = player.copy(
            hp = nextHp,
            mp = nextMp,
            gold = player.gold + result.goldDelta
        )

        val logs = mutableListOf<String>()
        if (result.expDelta != 0) {
            val expApplied = applyExperience(updated, result.expDelta, reason)
            updated = expApplied.player
            logs += expApplied.logs
        }
        val dropId = result.dropTableId ?: event?.dropTableId
        if (!dropId.isNullOrBlank()) {
            val loot = applyLoot(updated, dropId, event, reason)
            updated = loot.player
            logs += loot.logs
        }
        return ResultApplication(updated, logs)
    }

    private fun applyExperience(
        player: PlayerStats,
        expDelta: Int,
        reason: String
    ): ResultApplication {
        val nextExp = (player.exp + expDelta).coerceAtLeast(0)
        var current = player.copy(exp = nextExp)
        val logs = mutableListOf<String>()
        val growth = growthForRole(_state.value.selectedRoleId)
        logs += "经验变化 ${signed(expDelta)}（当前 ${current.exp}/${current.expToNext}）"
        GameLogger.info(
            logTag,
            "经验结算：变化=${signed(expDelta)} 当前=${current.exp}/${current.expToNext} 原因=$reason"
        )
        while (current.exp >= current.expToNext && current.level < levelMax) {
            val beforeLevel = current.level
            val nextLevel = current.level + 1
            val remainingExp = current.exp - current.expToNext
            val nextBase = current.baseStats.copy(
                hpMax = current.baseStats.hpMax + growth.hpMax,
                atk = current.baseStats.atk + growth.atk,
                def = current.baseStats.def + growth.def,
                speed = current.baseStats.speed + growth.speed
            )
            val boosted = current.copy(
                level = nextLevel,
                exp = remainingExp,
                expToNext = expRequiredFor(nextLevel),
                baseStats = nextBase,
                hp = current.hp + growth.hpMax,
                mp = current.mp + growth.mpMax,
                mpMax = current.mpMax + growth.mpMax
            )
            current = recalculatePlayerStats(boosted, "升级")
            val log = "升级到 Lv$nextLevel：生命上限+${growth.hpMax} 能量上限+${growth.mpMax} 攻击+${growth.atk} 防御+${growth.def} 速度+${growth.speed}"
            logs += log
            GameLogger.info(
                logTag,
                "角色升级：Lv$beforeLevel -> Lv$nextLevel 经验=${current.exp}/${current.expToNext} 原因=$reason"
            )
        }
        return ResultApplication(current, logs)
    }

    private fun growthForRole(roleId: String): GrowthProfile {
        val role = roles.firstOrNull { it.id == roleId }
        if (role == null) {
            GameLogger.warn(logTag, "未找到角色成长配置，使用默认成长：角色编号=$roleId")
            return defaultGrowthProfile()
        }
        return role.growth
    }

    private fun applyLoot(
        player: PlayerStats,
        dropTableId: String,
        event: EventDefinition?,
        reason: String
    ): LootApplication {
        val tier = resolveLootTier(dropTableId, event?.difficulty ?: 1)
        val sourceType = if (event != null && isBattleEvent(event)) {
            LootSourceType.ENEMY
        } else {
            LootSourceType.EVENT
        }
        val pity = player.pityCounters.toMutableMap()
        val outcome = lootRepository.generateLoot(sourceType, tier, rng, pity)
        GameLogger.info(
            "掉落系统",
            "生成掉落：来源=$sourceType 层级=$tier 掉落表=$dropTableId 结果=$outcome 原因=$reason"
        )
        val updatedPlayer = player.copy(pityCounters = pity)
        return applyLootOutcome(updatedPlayer, outcome, dropTableId)
    }

    private fun applyLootOutcome(
        player: PlayerStats,
        outcome: LootOutcome,
        dropTableId: String
    ): LootApplication {
        var updated = player.copy(
            gold = player.gold + outcome.gold,
            materials = player.materials + outcome.materials
        )
        val logs = mutableListOf<String>()
        if (outcome.gold > 0) {
            logs += "获得金币 +${outcome.gold}"
        }
        if (outcome.materials > 0) {
            logs += "获得材料 +${outcome.materials}"
        }
        if (outcome.equipment != null) {
            val item = buildEquipmentItem(outcome.equipment, dropTableId, _state.value.turn)
            val added = addEquipmentToInventory(updated, item)
            updated = added.player
            logs += added.logs
        }
        if (logs.isEmpty()) {
            logs += "掉落为空（掉落表 $dropTableId）"
        }
        return LootApplication(updated, logs)
    }

    private fun buildEquipmentItem(
        equipment: EquipmentInstance,
        source: String,
        turn: Int
    ): EquipmentItem {
        val uid = "eq_${equipment.id}_${equipment.rarity.tier}_${rng.nextInt(10000)}_$turn"
        return EquipmentItem(
            uid = uid,
            templateId = equipment.id,
            name = equipment.name,
            slot = equipment.slot,
            rarityId = equipment.rarity.id,
            rarityName = equipment.rarity.name,
            rarityTier = equipment.rarity.tier,
            level = equipment.level,
            stats = equipment.stats,
            affixes = equipment.affixes.map { affix ->
                EquipmentAffix(
                    id = affix.definition.id,
                    type = affix.definition.type,
                    value = affix.value
                )
            },
            score = lootRepository.scoreEquipment(equipment),
            source = source,
            obtainedAtTurn = turn
        )
    }

    private fun addEquipmentToInventory(
        player: PlayerStats,
        item: EquipmentItem
    ): LootApplication {
        val inventory = player.inventory
        return if (inventory.items.size >= inventory.capacity) {
            val gold = estimateSellValue(item)
            GameLogger.warn(
                "掉落系统",
                "背包已满，装备自动折算：${item.name} -> 金币+$gold"
            )
            LootApplication(
                player = player.copy(gold = player.gold + gold),
                logs = listOf("背包已满，${item.name} 已折算金币 +$gold")
            )
        } else {
            GameLogger.info("掉落系统", "新增装备到背包：${item.name}（${item.rarityName}）")
            LootApplication(
                player = player.copy(
                    inventory = inventory.copy(items = inventory.items + item)
                ),
                logs = listOf("获得装备：${item.name}（${item.rarityName}）")
            )
        }
    }

    private fun estimateSellValue(item: EquipmentItem): Int {
        return (4 + item.rarityTier * 4 + item.level).coerceAtLeast(1)
    }

    private fun resolveLootTier(dropTableId: String, difficulty: Int): Int {
        val chapterTier = when {
            dropTableId.contains("ch1") -> 1
            dropTableId.contains("ch2") -> 2
            dropTableId.contains("ch3") -> 3
            dropTableId.contains("ch4") -> 3
            dropTableId.contains("ch5") -> 3
            else -> difficulty
        }
        val bonus = when {
            dropTableId.contains("boss") -> 2
            dropTableId.contains("elite") -> 1
            else -> 0
        }
        return (chapterTier + bonus).coerceIn(1, 3)
    }

    private fun summarizeResult(result: EventResult): String {
        val parts = mutableListOf<String>()
        if (result.hpDelta != 0) parts += "生命 ${signed(result.hpDelta)}"
        if (result.mpDelta != 0) parts += "能量 ${signed(result.mpDelta)}"
        if (result.goldDelta != 0) parts += "金币 ${signed(result.goldDelta)}"
        if (result.expDelta != 0) parts += "经验 ${signed(result.expDelta)}"
        if (result.dropTableId != null) parts += "掉落表 ${result.dropTableId}"
        return if (parts.isEmpty()) "没有额外变化" else parts.joinToString("，")
    }

    private fun expRequiredFor(level: Int): Int {
        val safeLevel = level.coerceAtLeast(1)
        return expBase + (safeLevel - 1) * expGrowth
    }

    private fun normalizePlayerStats(player: PlayerStats, reason: String): PlayerStats {
        val base = if (player.baseStats.hpMax <= 0 || player.baseStats.atk <= 0) {
            PlayerBaseStats(
                hpMax = player.hpMax.coerceAtLeast(1),
                atk = player.atk.coerceAtLeast(1),
                def = player.def.coerceAtLeast(0),
                speed = player.speed.coerceAtLeast(1)
            )
        } else {
            player.baseStats
        }
        val expToNext = if (player.expToNext <= 0) {
            expRequiredFor(player.level)
        } else {
            player.expToNext
        }
        val normalized = player.copy(
            baseStats = base,
            exp = player.exp.coerceAtLeast(0),
            expToNext = expToNext
        )
        return recalculatePlayerStats(normalized, reason)
    }

    private fun recalculatePlayerStats(player: PlayerStats, reason: String): PlayerStats {
        val base = player.baseStats
        val bonus = collectEquipmentStats(player.equipment)
        val nextHpMax = (base.hpMax + (bonus[StatType.HP] ?: 0)).coerceAtLeast(1)
        val nextAtk = (base.atk + (bonus[StatType.ATK] ?: 0)).coerceAtLeast(1)
        val nextDef = (base.def + (bonus[StatType.DEF] ?: 0)).coerceAtLeast(0)
        val nextSpeed = (base.speed + (bonus[StatType.SPEED] ?: 0)).coerceAtLeast(1)
        val nextHp = player.hp.coerceIn(0, nextHpMax)
        val nextHitBonus = bonus[StatType.HIT] ?: 0
        val nextEvaBonus = bonus[StatType.EVADE] ?: 0
        val nextCritBonus = bonus[StatType.CRIT] ?: 0
        val nextResistBonus = bonus[StatType.CRIT_RESIST] ?: 0
        if (player.hpMax != nextHpMax || player.atk != nextAtk || player.def != nextDef || player.speed != nextSpeed) {
            GameLogger.info(
                "装备系统",
                "刷新角色属性：生命上限${player.hpMax}->$nextHpMax 攻击${player.atk}->$nextAtk 防御${player.def}->$nextDef 速度${player.speed}->$nextSpeed 原因=$reason"
            )
        }
        return player.copy(
            hp = nextHp,
            hpMax = nextHpMax,
            atk = nextAtk,
            def = nextDef,
            speed = nextSpeed,
            hitBonus = nextHitBonus,
            evaBonus = nextEvaBonus,
            critBonus = nextCritBonus,
            resistBonus = nextResistBonus
        )
    }

    private fun collectEquipmentStats(loadout: EquipmentLoadout): Map<StatType, Int> {
        val totals = mutableMapOf<StatType, Int>()
        loadout.slots.values.forEach { item ->
            item.totalStats().forEach { (type, value) ->
                totals[type] = (totals[type] ?: 0) + value
            }
        }
        return totals
    }

    private fun signed(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    private fun loadRoleProfiles(): List<RoleProfile> {
        GameLogger.info(logTag, "开始加载角色与技能配置")
        val characters = characterDefinitions
        val skills = skillDefinitions
        if (characters.isEmpty()) {
            GameLogger.warn(logTag, "角色配置为空，使用默认角色")
            return defaultRoles()
        }
        val skillMap = skills.associateBy { it.id }
        val profiles = characters.map { character ->
            val passiveSkill = skillMap[character.passiveSkillId].toRoleSkill()
            val activeSkills = if (character.activeSkillIds.isEmpty()) {
                listOf<SkillDefinition?>(null).map { it.toRoleSkill() }
            } else {
                character.activeSkillIds.map { skillId ->
                    skillMap[skillId].toRoleSkill()
                }
            }
            val ultimateSkill = skillMap[character.ultimateSkillId].toRoleSkill()
            val growth = character.growth ?: defaultGrowthProfile()
            RoleProfile(
                id = character.id,
                name = character.name,
                role = character.role,
                stats = character.stats,
                growth = growth,
                passiveSkill = passiveSkill,
                activeSkills = activeSkills,
                ultimateSkill = ultimateSkill,
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
                cooldown = "-",
                target = "未知",
                effectLines = listOf("暂无效果说明"),
                formulaLines = emptyList()
            )
        }
        val costLabel = if (cost <= 0) "-" else cost.toString()
        val cooldownLabel = if (cooldown <= 0) "-" else "$cooldown 回合"
        val effectLines = buildSkillEffectLines(this)
        val formulaLines = buildSkillFormulaLines(this)
        return RoleSkill(
            name = name,
            type = type,
            description = desc,
            cost = costLabel,
            cooldown = cooldownLabel,
            target = skillTargetLabel(target),
            effectLines = effectLines,
            formulaLines = formulaLines
        )
    }

    private fun buildSkillEffectLines(skill: SkillDefinition): List<String> {
        if (skill.effects.isEmpty()) return listOf("暂无效果说明")
        return skill.effects.map { effect ->
            effect.note?.takeIf { it.isNotBlank() } ?: buildEffectFallback(effect)
        }
    }

    private fun buildSkillFormulaLines(skill: SkillDefinition): List<String> {
        val lines = mutableListOf<String>()
        skill.effects.forEach { effect ->
            if (effect.type.uppercase().startsWith("DAMAGE")) {
                val note = effect.note?.takeIf { it.isNotBlank() }
                if (note != null) {
                    lines += note
                } else {
                    val scaling = effect.scaling?.let { scalingLabel(it) }.orEmpty().ifBlank { "攻击" }
                    val valueText = formatPercent(effect.value)
                    lines += "${valueText}${scaling}伤害"
                }
            }
        }
        return lines
    }

    private fun buildEffectFallback(effect: SkillEffect): String {
        val valueText = formatPercent(effect.value)
        return when (effect.type.uppercase()) {
            "DAMAGE" -> "${valueText}${scalingLabel(effect.scaling)}伤害"
            "HEAL_MAX_HP" -> "回复最大生命${valueText}"
            "HIT_UP" -> "命中率+${valueText}"
            "DAMAGE_TAKEN_DOWN" -> "受到伤害-${valueText}"
            "DAMAGE_VS_ELITE_BOSS" -> "对精英/首领伤害+${valueText}"
            "CRIT_UP" -> "暴击率+${valueText}"
            "EVADE_UP" -> "闪避+${valueText}"
            "DROP_RATE" -> "掉落概率+${valueText}"
            "HIDDEN_PATH" -> "隐藏路径触发+${valueText}"
            "MATERIAL_GAIN" -> "材料收益+${valueText}"
            "ENCOUNTER_RATE_DOWN" -> "遇敌率-${valueText}"
            else -> "效果 ${effect.type}${effect.value?.let { " ${valueText}" } ?: ""}"
        }
    }

    private fun scalingLabel(raw: String?): String {
        return when (raw?.uppercase()) {
            "ATK" -> "攻击"
            "DEF" -> "防御"
            "HP" -> "生命"
            "SPD" -> "速度"
            else -> raw?.uppercase() ?: ""
        }
    }

    private fun formatPercent(value: Double?): String {
        if (value == null) return ""
        val percent = value * 100.0
        val display = if (kotlin.math.abs(percent - percent.toInt()) < 0.01) {
            percent.toInt().toString()
        } else {
            String.format("%.1f", percent)
        }
        return "${display}%"
    }

    private fun skillTargetLabel(raw: String): String {
        return when (raw.uppercase()) {
            "SELF" -> "自身"
            "ENEMY" -> "敌方"
            "ALLY" -> "友方"
            "ALL_ENEMY" -> "敌方全体"
            "ALL_ALLY" -> "友方全体"
            else -> raw
        }
    }

    private fun handleBattleChoice(choiceId: String, event: EventDefinition) {
        val currentSession = battleSession
        if (currentSession == null || battleEventId != event.eventId) {
            GameLogger.info(logTag, "初始化回合制战斗：事件编号=${event.eventId}")
            startBattleSession(event)
            return
        }

        val action = when (choiceId) {
            "battle_attack" -> PlayerBattleAction(PlayerBattleActionType.BASIC_ATTACK)
            "battle_skill" -> PlayerBattleAction(PlayerBattleActionType.SKILL, activeSkillDefinition())
            "battle_item" -> PlayerBattleAction(PlayerBattleActionType.ITEM)
            "battle_equip" -> PlayerBattleAction(PlayerBattleActionType.EQUIP)
            "battle_flee" -> PlayerBattleAction(PlayerBattleActionType.FLEE)
            else -> PlayerBattleAction(PlayerBattleActionType.BASIC_ATTACK)
        }

        val beforeSize = currentSession.logLines.size
        var step = turnEngine.applyPlayerAction(currentSession, action)
        var sessionAfter = step.session
        var newLogs = sessionAfter.logLines.drop(beforeSize)

        if (step.outcome == null) {
            val beforeEnemy = sessionAfter.logLines.size
            val enemyStep = turnEngine.applyEnemyTurn(sessionAfter)
            sessionAfter = enemyStep.session
            newLogs = newLogs + sessionAfter.logLines.drop(beforeEnemy)
            step = enemyStep
        }

        updateBattleState(sessionAfter, newLogs)

        val outcome = step.outcome
        if (outcome != null) {
            finishBattle(event, outcome)
        }
    }

    private fun startBattleSession(event: EventDefinition) {
        val context = battleSystem.buildBattleContext(_state.value.player, event)
        val session = turnEngine.startSession(
            player = context.player,
            enemy = context.enemy,
            config = context.config,
            enemyDamageMultiplier = context.enemyDamageMultiplier,
            initialCooldown = 0
        )
        battleEventId = event.eventId

        val firstStrike = turnEngine.determineFirstStrike(session.player, session.enemy, session.config.firstStrike)
        val updatedSession = if (firstStrike == CombatActorType.ENEMY) {
            val enemyStep = turnEngine.applyEnemyTurn(session, advanceRound = false)
            enemyStep.session
        } else {
            session
        }

        battleSession = updatedSession
        updateBattleState(updatedSession, updatedSession.logLines)

        if (updatedSession.player.hp <= 0 || updatedSession.enemy.hp <= 0) {
            finishBattle(
                event,
                BattleOutcome(
                    victory = updatedSession.enemy.hp <= 0,
                    escaped = updatedSession.escaped,
                    rounds = updatedSession.round,
                    playerRemainingHp = updatedSession.player.hp,
                    enemyRemainingHp = updatedSession.enemy.hp,
                    logLines = updatedSession.logLines
                )
            )
        }
    }

    private fun updateBattleState(session: BattleSession, newLogs: List<String>) {
        battleSession = session
        val battleState = BattleUiState(
            round = session.round,
            playerHp = session.player.hp,
            playerMp = session.player.mp,
            enemyHp = session.enemy.hp,
            enemyName = session.enemy.name,
            equipmentMode = equipmentModeLabel(session.equipmentMode),
            skillCooldown = session.skillCooldown
        )
        val choices = buildBattleChoices(session)
        _state.update { current ->
            val syncedPlayer = syncBattlePlayerStats(current.player, session)
            current.copy(
                player = syncedPlayer,
                battle = battleState,
                enemyPreview = buildEnemyPreview(current.currentEvent, syncedPlayer),
                choices = choices,
                log = current.log + newLogs
            )
        }
    }

    private fun syncBattlePlayerStats(current: PlayerStats, session: BattleSession): PlayerStats {
        val actor = session.player
        val nextHpMax = actor.stats.hpMax
        val nextHp = actor.hp.coerceIn(0, nextHpMax)
        val nextMp = actor.mp.coerceIn(0, current.mpMax)
        if (current.hp != nextHp || current.mp != nextMp || current.hpMax != nextHpMax) {
            GameLogger.info(
                logTag,
                "同步战斗状态面板：生命${current.hp}/${current.hpMax} -> ${nextHp}/${nextHpMax}，能量${current.mp}/${current.mpMax} -> ${nextMp}/${current.mpMax}"
            )
        }
        return current.copy(
            hp = nextHp,
            hpMax = nextHpMax,
            mp = nextMp
        )
    }

    private fun finishBattle(event: EventDefinition, outcome: BattleOutcome) {
        val escaped = outcome.escaped
        val victory = outcome.victory
        val outcomeText = when {
            escaped -> "成功撤离战斗"
            victory -> event.successText
            else -> event.failText
        }
        val basePlayer = _state.value.player.copy(
            hp = outcome.playerRemainingHp.coerceAtLeast(0),
            mp = battleSession?.player?.mp ?: _state.value.player.mp
        )
        val applied = if (victory) {
            applyEventResult(basePlayer, event.result, event, "战斗奖励")
        } else {
            ResultApplication(basePlayer, emptyList())
        }
        val rewardPlayer = applied.player
        val resultSummary = if (victory) {
            event.result?.let { summarizeResult(it) } ?: "战斗无额外奖励"
        } else if (escaped) {
            "撤离成功，未获得奖励"
        } else {
            "战斗失败，保留部分资源后继续"
        }

        battleSession = null
        battleEventId = null

        _state.update { state ->
            state.copy(
                player = rewardPlayer,
                battle = null,
                lastAction = outcomeText.ifBlank { "战斗结束" },
                log = state.log + listOfNotNull(
                    outcomeText.ifBlank { null },
                    event.logText.ifBlank { null },
                    resultSummary
                ) + applied.logs
            )
        }

        val nextEventId = if (victory) {
            event.result?.nextEventId ?: event.nextEventId
        } else {
            event.failEventId
        }
        val nextNodeId = if (victory) event.result?.nextNodeId else null
        advanceToNextNode(
            incrementTurn = true,
            forcedEventId = nextEventId,
            forcedNodeId = nextNodeId
        )
    }

    private fun buildBattleChoices(session: BattleSession): List<GameChoice> {
        val skill = activeSkillDefinition()
        val skillLabel = if (skill == null) {
            "技能（未配置）"
        } else {
            val tip = when {
                session.skillCooldown > 0 -> "冷却${session.skillCooldown}"
                session.player.mp < skill.cost -> "能量不足"
                else -> "可用"
            }
            "技能：${skill.name}（$tip）"
        }
        val equipLabel = "换装备（${equipmentModeLabel(session.equipmentMode)}）"
        return listOf(
            GameChoice("battle_attack", "普通攻击"),
            GameChoice("battle_skill", skillLabel),
            GameChoice("battle_item", "使用药丸"),
            GameChoice("battle_equip", equipLabel),
            GameChoice("battle_flee", "撤离战斗")
        )
    }

    private fun activeSkillDefinition(): SkillDefinition? {
        val roleId = _state.value.selectedRoleId
        val skillId = roleActiveSkillMap[roleId]
        return skillDefinitions.firstOrNull { it.id == skillId }
    }

    private fun equipmentModeLabel(mode: EquipmentMode): String {
        return when (mode) {
            EquipmentMode.NORMAL -> "默认"
            EquipmentMode.OFFENSE -> "进攻"
            EquipmentMode.DEFENSE -> "防御"
        }
    }

    private fun resolveBattleAndAdvance(event: EventDefinition) {
        GameLogger.info(logTag, "进入战斗：事件编号=${event.eventId} 标题=${event.title}")
        val current = _state.value
        val battle = battleSystem.resolveBattle(current.player, event)
        val outcomeText = if (battle.victory) event.successText else event.failText
        val safeHp = if (battle.victory) battle.playerRemainingHp else max(1, battle.playerRemainingHp)
        val postBattlePlayer = current.player.copy(hp = safeHp)
        val applied = if (battle.victory) {
            applyEventResult(postBattlePlayer, event.result, event, "战斗奖励")
        } else {
            ResultApplication(postBattlePlayer, emptyList())
        }
        val rewardPlayer = applied.player

        val resultSummary = if (battle.victory) {
            event.result?.let { summarizeResult(it) } ?: "战斗无额外奖励"
        } else {
            "战斗失败，保留部分资源后继续"
        }

        _state.update { state ->
            state.copy(
                player = rewardPlayer,
                lastAction = outcomeText.ifBlank { "战斗结束" },
                log = state.log + battle.logLines + listOfNotNull(
                    outcomeText.ifBlank { null },
                    event.logText.ifBlank { null },
                    resultSummary
                ) + applied.logs
            )
        }

        val nextEventId = if (battle.victory) {
            event.result?.nextEventId ?: event.nextEventId
        } else {
            event.failEventId
        }
        val nextNodeId = if (battle.victory) event.result?.nextNodeId else null
        advanceToNextNode(
            incrementTurn = true,
            forcedEventId = nextEventId,
            forcedNodeId = nextNodeId
        )
    }

    private fun isBattleEvent(event: EventDefinition): Boolean {
        if (!event.enemyGroupId.isNullOrBlank()) return true
        val type = event.type.lowercase()
        return type.startsWith("battle") || event.type.contains("战斗")
    }

    private fun buildEnemyPreview(
        event: EventDefinition?,
        player: PlayerStats
    ): EnemyPreviewUiState? {
        if (event == null || !isBattleEvent(event)) return null
        val group = enemyRepository.findGroup(event.enemyGroupId) ?: run {
            GameLogger.warn(logTag, "敌群配置缺失，使用默认敌群预览：敌群编号=${event.enemyGroupId}")
            defaultEnemyGroupFile().groups.first()
        }
        val enemyDef = enemyRepository.findEnemy(group.enemyId) ?: run {
            GameLogger.warn(logTag, "敌人配置缺失，使用默认敌人预览：敌人编号=${group.enemyId}")
            defaultEnemyFile().enemies.first()
        }

        val count = group.count.coerceAtLeast(1)
        val hpMultiplier = event.battleModifiers?.enemyHpMultiplier ?: 1.0
        val damageMultiplier = event.battleModifiers?.enemyDamageMultiplier ?: 1.0
        val groupHpMultiplier = if (count <= 1) 1.0 else 1.0 + 0.35 * (count - 1)
        val groupAtkMultiplier = if (count <= 1) 1.0 else 1.0 + 0.2 * (count - 1)
        val groupDefMultiplier = if (count <= 1) 1.0 else 1.0 + 0.15 * (count - 1)
        val groupSpdMultiplier = if (count <= 1) 1.0 else 1.0 + 0.05 * (count - 1)

        val scaledHp = (enemyDef.stats.hp * groupHpMultiplier * hpMultiplier).toInt().coerceAtLeast(1)
        val scaledAtk = (enemyDef.stats.atk * groupAtkMultiplier * damageMultiplier).toInt().coerceAtLeast(1)
        val scaledDef = (enemyDef.stats.def * groupDefMultiplier).toInt().coerceAtLeast(1)
        val scaledSpd = (enemyDef.stats.spd * groupSpdMultiplier).toInt().coerceAtLeast(1)

        val playerScore = player.hp + player.atk * 2.0 + player.def * 1.5 + player.speed * 0.8
        val enemyScore = scaledHp + scaledAtk * 2.0 + scaledDef * 1.5 + scaledSpd * 0.8
        var ratio = if (enemyScore <= 0.0) 1.0 else playerScore / enemyScore

        when (event.firstStrike) {
            "玩家" -> ratio *= 1.05
            "敌人" -> ratio *= 0.9
        }
        val roundLimit = event.roundLimit
        if (roundLimit != null) {
            ratio *= when {
                roundLimit <= 3 -> 0.85
                roundLimit <= 5 -> 0.93
                else -> 1.0
            }
        }

        val (threat, tip) = when {
            ratio >= 1.2 -> "优势" to "你在属性上占优，主动进攻更稳。"
            ratio >= 0.95 -> "均势" to "胜负接近，优先保证生命与命中。"
            ratio >= 0.8 -> "偏难" to "建议先恢复或消耗型技能再战。"
            else -> "危险" to "差距较大，谨慎进入或等待更好机会。"
        }
        val winRate = estimateWinRate(ratio, roundLimit)
        val summary = buildString {
            append("预计胜率约 ")
            append(winRate)
            append("%，评估为「")
            append(threat)
            append("」。")
        }

        val firstStrikeLabel = when (event.firstStrike) {
            "玩家" -> "玩家先手"
            "敌人" -> "敌人先手"
            "随机" -> "随机先手"
            "速度" -> "速度先手"
            else -> "速度先手"
        }

        val dropTableId = event.result?.dropTableId ?: event.dropTableId ?: ""
        val dropPreview = if (dropTableId.isNotBlank()) {
            buildDropPreview(dropTableId, event)
        } else {
            emptyList()
        }

        GameLogger.info(
            logTag,
            "生成敌人预览：敌人=${enemyDef.name} 数量=$count 生命=$scaledHp 攻击=$scaledAtk 防御=$scaledDef 速度=$scaledSpd 评估=$threat 胜率=${winRate}% 掉落表=$dropTableId"
        )

        return EnemyPreviewUiState(
            name = enemyDef.name,
            type = enemyTypeLabel(enemyDef.type),
            level = enemyDef.level,
            count = count,
            hp = scaledHp,
            atk = scaledAtk,
            def = scaledDef,
            speed = scaledSpd,
            hit = enemyDef.stats.hit,
            eva = enemyDef.stats.eva,
            crit = enemyDef.stats.crit,
            critDmg = enemyDef.stats.critDmg,
            resist = enemyDef.stats.resist,
            note = enemyDef.notes.ifBlank { group.note },
            threat = threat,
            tip = tip,
            winRate = winRate,
            summary = summary,
            roundLimit = roundLimit,
            firstStrike = firstStrikeLabel,
            dropTableId = dropTableId,
            dropPreview = dropPreview
        )
    }

    private fun buildDropPreview(dropTableId: String, event: EventDefinition): List<String> {
        val table = lootRepository.getLootTableById(dropTableId) ?: run {
            val tier = resolveLootTier(dropTableId, event.difficulty)
            val sourceType = if (isBattleEvent(event)) LootSourceType.ENEMY else LootSourceType.EVENT
            GameLogger.warn(
                "掉落系统",
                "未找到掉落表编号=$dropTableId，改用来源=$sourceType 层级=$tier"
            )
            runCatching { lootRepository.getLootTable(sourceType, tier) }.getOrNull()
        }
        if (table == null) {
            GameLogger.warn("掉落系统", "掉落表为空，无法生成预览：$dropTableId")
            return listOf("掉落表缺失：$dropTableId")
        }
        val totalWeight = table.weightedPool.sumOf { it.weight }.coerceAtLeast(1)
        return table.weightedPool.map { entry ->
            val label = when (entry.type) {
                com.jungleadventure.shared.loot.LootEntryType.EQUIPMENT -> "装备 ${lootRepository.equipmentName(entry.refId)}"
                com.jungleadventure.shared.loot.LootEntryType.GOLD -> "金币 ${entry.min}-${entry.max}"
                com.jungleadventure.shared.loot.LootEntryType.MATERIAL -> "材料 ${entry.min}-${entry.max}"
                com.jungleadventure.shared.loot.LootEntryType.CONSUMABLE -> "消耗品 ${entry.refId}"
            }
            val percent = entry.weight * 100.0 / totalWeight
            val rate = String.format("%.1f", percent)
            "$label（$rate%）"
        }
    }

    private fun enemyTypeLabel(raw: String): String {
        return when (raw.uppercase()) {
            "NORMAL" -> "普通"
            "ELITE" -> "精英"
            "BOSS" -> "首领"
            else -> raw
        }
    }

    private fun estimateWinRate(ratio: Double, roundLimit: Int?): Int {
        val base = when {
            ratio >= 1.6 -> 92
            ratio >= 1.4 -> 85
            ratio >= 1.2 -> 75
            ratio >= 1.05 -> 62
            ratio >= 0.95 -> 50
            ratio >= 0.85 -> 38
            ratio >= 0.7 -> 28
            ratio >= 0.55 -> 18
            else -> 10
        }
        val roundPenalty = when {
            roundLimit == null -> 0
            roundLimit <= 3 -> 12
            roundLimit <= 5 -> 7
            else -> 3
        }
        val adjusted = (base - roundPenalty).coerceIn(5, 95)
        return adjusted
    }

    private fun buildSaveGameSnapshot(): SaveGame {
        val snapshot = _state.value
        val runtime = stageRuntime
        return SaveGame(
            turn = snapshot.turn,
            chapter = snapshot.chapter,
            rngSeed = rngSeed,
            selectedRoleId = snapshot.selectedRoleId,
            player = snapshot.player,
            log = snapshot.log,
            lastAction = snapshot.lastAction,
            activePanel = snapshot.activePanel,
            showSkillFormula = snapshot.showSkillFormula,
            currentEventId = snapshot.currentEvent?.eventId,
            stageId = runtime?.stage?.id,
            nodeId = runtime?.currentNodeId,
            visitedNodes = runtime?.visited?.toList() ?: emptyList(),
            stageCompleted = runtime?.completed ?: false,
            guardianGroupId = runtime?.guardianGroupId
        )
    }

    private fun startAutoSaveLoop() {
        GameLogger.info(
            "存档系统",
            "启动自动存档：间隔=${autoSaveIntervalMs}ms，等待玩家读档/手动存档后自动存档"
        )
        autoSaveScope.launch {
            while (isActive) {
                delay(autoSaveIntervalMs)
                val slot = autoSaveSlot
                if (slot == null) {
                    GameLogger.info("存档系统", "自动存档跳过：尚未选择存档槽位")
                    continue
                }
                val saveGame = buildSaveGameSnapshot()
                runCatching {
                    val payload = json.encodeToString(saveGame)
                    saveStore.save(slot, payload)
                }.onFailure { error ->
                    GameLogger.error("存档系统", "自动存档失败，槽位=$slot", error)
                    return@onFailure
                }
                GameLogger.info(
                    "存档系统",
                    "自动存档完成，槽位=$slot，回合=${saveGame.turn}"
                )
                refreshSaveSlots()
            }
        }
    }

    private fun loadEnemyRepository(): EnemyRepository {
        val enemyFile = runCatching { repository.loadEnemies() }.getOrElse { defaultEnemyFile() }
        val groupFile = runCatching { repository.loadEnemyGroups() }.getOrElse { defaultEnemyGroupFile() }
        GameLogger.info(
            logTag,
            "敌人数据已加载：敌人=${enemyFile.enemies.size}，敌群=${groupFile.groups.size}"
        )
        return EnemyRepository(enemyFile, groupFile)
    }

    private data class StageBundle(
        val stages: List<StageDefinition>,
        val nodes: List<NodeDefinition>
    )

    private fun loadStageBundle(): StageBundle {
        val stages = runCatching { repository.loadStages().stages }.getOrElse { emptyList() }
        val nodes = runCatching { repository.loadNodes().nodes }.getOrElse { emptyList() }
        if (stages.isEmpty() || nodes.isEmpty()) {
            GameLogger.warn(logTag, "关卡或节点配置为空，使用默认关卡")
            return StageBundle(defaultStages(), defaultNodes())
        }
        val referencedNodes = nodes.filter { node -> stages.any { it.nodes.contains(node.id) } }
        if (referencedNodes.isEmpty()) {
            GameLogger.warn(logTag, "节点未被关卡引用，使用默认关卡")
            return StageBundle(defaultStages(), defaultNodes())
        }
        GameLogger.info(
            logTag,
            "关卡数据已加载：关卡=${stages.size} 节点=${referencedNodes.size}"
        )
        return StageBundle(stages, referencedNodes)
    }

    private fun assignGuardian(runtime: StageRuntime): StageRuntime {
        if (runtime.guardianGroupId != null) return runtime
        val groupId = pickGuardianGroupId(runtime.stage)
        GameLogger.info(
            logTag,
            "关卡守卫抽取：关卡编号=${runtime.stage.id} 口令=${runtime.command.ifBlank { "无" }} 守卫=${groupId ?: "无"} 随机种子=$rngSeed"
        )
        return runtime.copy(guardianGroupId = groupId)
    }

    private fun pickGuardianGroupId(stage: StageDefinition): String? {
        if (stage.guardianPool.isEmpty()) return null
        val key = (rngSeed xor stage.id.hashCode().toLong()) and Long.MAX_VALUE
        val index = (key % stage.guardianPool.size).toInt()
        return stage.guardianPool[index]
    }

    private fun resolveEventForNode(
        runtime: StageRuntime,
        node: NodeDefinition?,
        chapter: Int
    ): EventDefinition? {
        if (node == null) return null
        val isExitNode = node.id == runtime.stage.exit
        return if (isExitNode && runtime.guardianGroupId != null) {
            buildGuardianEvent(runtime, chapter)
        } else {
            stageEngine.eventForNode(runtime, chapter, rng)
        }
    }

    private fun buildGuardianEvent(runtime: StageRuntime, chapter: Int): EventDefinition {
        val groupId = runtime.guardianGroupId ?: "eg_default"
        val guardianName = guardianNameForGroup(groupId)
        val rewardGold = 8 + chapter * 4
        val rewardExp = 6 + chapter * 6
        return EventDefinition(
            eventId = "stage_guardian_${runtime.stage.id}_$groupId",
            chapter = chapter,
            title = "${runtime.stage.name}守卫 · $guardianName",
            type = "battle_guardian",
            difficulty = runtime.stage.difficulty,
            weight = 1,
            cooldown = 0,
            introText = "你触发了关卡守卫：$guardianName。",
            successText = "守卫倒下，关卡通道打开。",
            failText = "守卫逼退你，但仍允许继续前进。",
            logText = "守卫战结束：$guardianName",
            result = EventResult(
                hpDelta = 0,
                mpDelta = 0,
                goldDelta = rewardGold,
                expDelta = rewardExp
            ),
            enemyGroupId = groupId,
            firstStrike = "speed"
        )
    }

    private fun guardianNameForGroup(groupId: String): String {
        val group = enemyRepository.findGroup(groupId)
        val enemy = enemyRepository.findEnemy(group?.enemyId)
        return enemy?.name ?: group?.note ?: groupId
    }

    private fun StageRuntime.toUiState(node: NodeDefinition?): StageUiState {
        val guardianName = guardianGroupId?.let { guardianNameForGroup(it) }.orEmpty()
        return StageUiState(
            id = stage.id,
            name = stage.name,
            chapter = stage.chapter,
            nodeId = node?.id ?: currentNodeId,
            nodeType = node?.type ?: "UNKNOWN",
            command = command,
            guardian = guardianName,
            visited = visited.size,
            total = stage.nodes.size,
            isCompleted = completed
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
                    val stageName = saveGame.stageId?.let { stageId ->
                        stageBundle.stages.firstOrNull { it.id == stageId }?.name
                    } ?: "未知关卡"
                    SaveSlotSummary(
                        slot = slot,
                        title = "槽位 $slot：第 ${saveGame.turn} 回合",
                        detail = "角色 ${saveGame.player.name} | 章节 ${saveGame.chapter} | 关卡 $stageName",
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
