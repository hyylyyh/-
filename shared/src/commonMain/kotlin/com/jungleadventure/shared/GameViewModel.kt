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
    private val skillDefinitionMap = skillDefinitions.associateBy { it.id }
    private val events = runCatching { repository.loadEvents() }.getOrElse { emptyList() }
    private val engine = EventEngine(events)
    private val stageBundle = loadStageBundle()
    private val stageEngine = StageEngine(stageBundle.stages, stageBundle.nodes, engine)
    private var stageRuntime: StageRuntime? = null
    private var rngSeed: Long = Random.Default.nextLong()
    private val totalChapters = 10
    private val maxBattleSkillSlots = 5
    private val maxChapter = max(totalChapters, events.maxOfOrNull { it.chapter } ?: 1)
    private val shopOfferCount = 6
    private val monsterOnlyMode = true
    private val roles = loadRoleProfiles()
    private val enemyRepository = loadEnemyRepository()
    private val battleSystem = BattleSystem(enemyRepository, rng)
    private val turnEngine = TurnBasedCombatEngine(rng)
    private val lootRepository = LootRepository()
    private val cardRepository = CardRepository()
    private val equipmentCatalog = buildEquipmentCatalog()
    private val skillCatalog = buildSkillCatalog()
    private val monsterCatalog = buildMonsterCatalog()
    private var battleSession: BattleSession? = null
    private var battleEventId: String? = null
    private var shopEventId: String? = null
    private var shopOffers: List<ShopOffer> = emptyList()
    private var roleDetailReturnScreen: GameScreen = GameScreen.ADVENTURE
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
            "初始化完成：事件数量=${events.size}，最大章节=$maxChapter，角色数量=${roles.size}，纯怪物模式=$monsterOnlyMode"
        )
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            current.copy(
                roles = roles,
                selectedRoleId = initialRole?.id ?: "",
                totalChapters = totalChapters,
                selectedChapter = 1,
                selectedDifficulty = 1,
                completedChapters = emptyList(),
                screen = GameScreen.SAVE_SELECT,
                equipmentCatalog = equipmentCatalog,
                skillCatalog = skillCatalog,
                monsterCatalog = monsterCatalog,
                codexTab = CodexTab.EQUIPMENT,
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
        _state.update { current ->
            current.copy(
                screen = GameScreen.CHAPTER_SELECT,
                selectedChapter = 1,
                selectedDifficulty = current.selectedDifficulty.coerceAtLeast(1),
                lastAction = "已确认角色，请选择章节与难度",
                log = current.log + "已确认角色，请选择章节与难度"
            )
        }
        GameLogger.info(logTag, "进入关卡选择界面：章节=1 难度=${_state.value.selectedDifficulty}")
    }

    fun onOpenChapterSelect() {
        val current = _state.value
        if (current.screen == GameScreen.CHAPTER_SELECT) {
            GameLogger.info(logTag, "已在关卡选择界面，忽略重复打开")
            return
        }
        if (current.screen == GameScreen.ADVENTURE && current.battle != null) {
            GameLogger.warn(logTag, "战斗中禁止切换关卡")
            _state.update { it.copy(lastAction = "战斗中无法切换关卡", log = it.log + "战斗中无法切换关卡") }
            return
        }
        GameLogger.info(logTag, "打开关卡选择界面")
        _state.update {
            it.copy(
                screen = GameScreen.CHAPTER_SELECT,
                lastAction = "打开关卡选择界面",
                log = it.log + "打开关卡选择界面"
            )
        }
    }

    fun onSelectChapter(chapter: Int) {
        if (_state.value.screen != GameScreen.CHAPTER_SELECT) {
            GameLogger.warn(logTag, "当前界面不允许选择章节")
            return
        }
        val normalized = chapter.coerceIn(1, totalChapters)
        if (!isChapterUnlocked(normalized, _state.value.completedChapters)) {
            GameLogger.warn(logTag, "章节未解锁：章节=$normalized")
            _state.update { it.copy(lastAction = "章节 $normalized 未解锁") }
            return
        }
        GameLogger.info(logTag, "选择章节：章节=$normalized")
        _state.update { it.copy(selectedChapter = normalized, lastAction = "已选择第 $normalized 章") }
    }

    fun onSelectDifficulty(difficulty: Int) {
        if (_state.value.screen != GameScreen.CHAPTER_SELECT) {
            GameLogger.warn(logTag, "当前界面不允许选择难度")
            return
        }
        val normalized = difficulty.coerceIn(1, 5)
        GameLogger.info(logTag, "选择难度：难度=$normalized")
        _state.update { it.copy(selectedDifficulty = normalized, lastAction = "已选择难度 $normalized") }
    }

    fun onConfirmChapterSelection() {
        val current = _state.value
        if (current.screen != GameScreen.CHAPTER_SELECT) {
            GameLogger.warn(logTag, "当前界面不允许确认章节")
            return
        }
        val chapter = current.selectedChapter.coerceIn(1, totalChapters)
        if (!isChapterUnlocked(chapter, current.completedChapters)) {
            GameLogger.warn(logTag, "章节未解锁，禁止进入：章节=$chapter")
            _state.update { it.copy(lastAction = "章节 $chapter 未解锁") }
            return
        }
        val difficulty = current.selectedDifficulty.coerceIn(1, 5)
        val turn = chapterStartTurn(chapter)
        startStageForSelection(turn, chapter, difficulty, reason = "关卡选择")
        val slot = pendingNewSaveSlot
        if (slot != null) {
            autoSaveSlot = slot
            pendingNewSaveSlot = null
            _state.update { state ->
                state.copy(
                    screen = GameScreen.ADVENTURE,
                    selectedSaveSlot = slot,
                    lastAction = "已进入第 $chapter 章"
                )
            }
            onSave(slot)
        } else {
            _state.update { state ->
                state.copy(
                    screen = GameScreen.ADVENTURE,
                    lastAction = "已进入第 $chapter 章"
                )
            }
        }
        GameLogger.info(logTag, "确认关卡选择：章节=$chapter 难度=$difficulty 回合=$turn")
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

        if (isShopEvent(currentEvent)) {
            handleShopChoice(choiceId, currentEvent)
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
                ) + applied.logs,
                pendingCardLevels = current.pendingCardLevels + applied.pendingCardLevels,
                pendingCardReasons = current.pendingCardReasons + applied.pendingCardReasons
            )
        }

        advanceToNextNode(
            incrementTurn = true,
            forcedEventId = nextEventId,
            forcedNodeId = nextNodeId
        )
        openNextCardDialogIfNeeded()
    }

    fun onOpenStatus() {
        GameLogger.info(logTag, "切换面板：状态")
        _state.update { it.copy(activePanel = GamePanel.STATUS, lastAction = "查看状态") }
    }

    fun onOpenEquipment() {
        GameLogger.info(logTag, "切换面板：装备")
        _state.update { it.copy(activePanel = GamePanel.EQUIPMENT, lastAction = "查看装备") }
    }

    fun onOpenEquipmentCatalog() {
        GameLogger.info(logTag, "切换面板：游戏图鉴")
        _state.update {
            it.copy(
                activePanel = GamePanel.EQUIPMENT_CATALOG,
                codexTab = CodexTab.EQUIPMENT,
                lastAction = "查看游戏图鉴"
            )
        }
    }

    fun onSelectCodexTab(tab: CodexTab) {
        GameLogger.info(logTag, "切换图鉴标签：${tab.name}")
        _state.update { it.copy(codexTab = tab, lastAction = "切换图鉴：${codexTabLabel(tab)}") }
    }

    fun onToggleRoleDetail() {
        val current = _state.value
        val roleId = current.selectedRoleId
        val roleSelected = roleId.isNotBlank() && roles.any { it.id == roleId && it.unlocked }
        if (!roleSelected) {
            GameLogger.warn(logTag, "角色详情按钮不可用：未选择角色或角色未解锁")
            return
        }
        if (current.screen == GameScreen.ROLE_DETAIL) {
            val target = roleDetailReturnScreen
            GameLogger.info(logTag, "关闭角色详情，返回界面：${target.name}")
            _state.update { state ->
                state.copy(
                    screen = target,
                    lastAction = "返回${screenLabel(target)}",
                    log = state.log + "返回${screenLabel(target)}"
                )
            }
            return
        }
        roleDetailReturnScreen = current.screen
        GameLogger.info(logTag, "进入角色详情界面")
        _state.update { state ->
            state.copy(
                screen = GameScreen.ROLE_DETAIL,
                lastAction = "打开角色详情",
                log = state.log + "打开角色详情"
            )
        }
    }

    fun onShowEquipmentDetail(item: EquipmentItem?) {
        if (item == null) {
            GameLogger.warn(logTag, "请求装备详情失败：装备为空")
            return
        }
        val message = buildEquipmentDetailMessage(item)
        val sourceLabel = formatEquipmentSourceLabel(item.source).ifBlank { "未知" }
        GameLogger.info(
            logTag,
            "展示装备详情：${item.name} 模板=${item.templateId} 来源=$sourceLabel"
        )
        _state.update { state ->
            state.copy(
                showDialog = true,
                dialogTitle = "装备详情",
                dialogMessage = message,
                lastAction = "查看装备详情：${item.name}"
            )
        }
    }

    fun onOpenInventory() {
        GameLogger.info(logTag, "切换面板：背包")
        _state.update { it.copy(activePanel = GamePanel.INVENTORY, lastAction = "查看背包") }
    }

    fun onOpenCards() {
        GameLogger.info(logTag, "切换面板：卡牌")
        _state.update { it.copy(activePanel = GamePanel.CARDS, lastAction = "查看卡牌") }
    }

    fun onOpenSkills() {
        GameLogger.info(logTag, "切换面板：技能")
        _state.update { it.copy(activePanel = GamePanel.SKILLS, lastAction = "查看技能") }
    }

    fun onAssignBattleSkill(slotIndex: Int, skillId: String) {
        val current = _state.value
        if (slotIndex !in 0 until maxBattleSkillSlots) {
            GameLogger.warn(logTag, "战斗技能槽位超出范围：槽位=$slotIndex")
            return
        }
        val availableIds = roleSkillIds(current.selectedRoleId).toSet()
        if (!availableIds.contains(skillId)) {
            GameLogger.warn(logTag, "战斗技能不可用：技能编号=$skillId")
            _state.update { it.copy(lastAction = "战斗技能不可用") }
            return
        }
        val normalized = normalizeBattleSkillSlots(
            slots = current.player.battleSkillSlots,
            roleId = current.selectedRoleId,
            reason = "拖动技能配置"
        )
        val updatedSlots = normalized.toMutableList()
        val previous = updatedSlots[slotIndex]
        updatedSlots.indices.forEach { index ->
            if (index != slotIndex && updatedSlots[index] == skillId) {
                updatedSlots[index] = ""
            }
        }
        updatedSlots[slotIndex] = skillId
        val skillName = skillDefinitionMap[skillId]?.name ?: skillId
        GameLogger.info(
            logTag,
            "战斗技能槽位更新：槽位=${slotIndex + 1} 技能=$skillName 原本=${if (previous.isBlank()) "空" else previous}"
        )
        _state.update { state ->
            state.copy(
                player = state.player.copy(battleSkillSlots = updatedSlots),
                lastAction = "战斗技能配置：${slotIndex + 1}号槽位放入$skillName",
                log = state.log + "战斗技能配置：${slotIndex + 1}号槽位放入$skillName"
            )
        }
    }

    fun onClearBattleSkill(slotIndex: Int) {
        val current = _state.value
        if (slotIndex !in 0 until maxBattleSkillSlots) {
            GameLogger.warn(logTag, "战斗技能槽位超出范围：槽位=$slotIndex")
            return
        }
        val normalized = normalizeBattleSkillSlots(
            slots = current.player.battleSkillSlots,
            roleId = current.selectedRoleId,
            reason = "清空技能槽位"
        )
        if (normalized[slotIndex].isBlank()) {
            GameLogger.info(logTag, "战斗技能槽位已为空，无需清空：槽位=${slotIndex + 1}")
            return
        }
        val updatedSlots = normalized.toMutableList()
        val removedId = updatedSlots[slotIndex]
        val removedName = skillDefinitionMap[removedId]?.name ?: removedId
        updatedSlots[slotIndex] = ""
        GameLogger.info(logTag, "战斗技能槽位清空：槽位=${slotIndex + 1} 技能=$removedName")
        _state.update { state ->
            state.copy(
                player = state.player.copy(battleSkillSlots = updatedSlots),
                lastAction = "清空战斗技能：${slotIndex + 1}号槽位",
                log = state.log + "清空战斗技能：${slotIndex + 1}号槽位移除$removedName"
            )
        }
    }

    fun onToggleShopOfferSelection(offerId: String) {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略商品选择")
            return
        }
        val offer = shopOffers.firstOrNull { it.offerId == offerId }
        if (offer == null) {
            GameLogger.warn("商店系统", "未找到商店商品：$offerId")
            return
        }
        if (offer.stock <= 0) {
            GameLogger.warn("商店系统", "商品已售罄，无法选择：${offer.item.name}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "商品已售罄",
                    log = updated.log + "商品已售罄：${offer.item.name}"
                )
            }
            return
        }
        if (offer.lockedReason.isNotBlank()) {
            GameLogger.warn("商店系统", "商品不可购买：${offer.item.name} 原因=${offer.lockedReason}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "商品不可购买",
                    log = updated.log + "商品不可购买：${offer.item.name}（${offer.lockedReason}）"
                )
            }
            return
        }
        val selection = current.shopSelectedOfferIds.toMutableSet()
        val selecting = if (selection.contains(offerId)) {
            selection.remove(offerId)
            false
        } else {
            selection.add(offerId)
            true
        }
        GameLogger.info("商店系统", "商品选择切换：${offer.item.name} 选中=$selecting")
        _state.update { state ->
            val updated = applyShopUiState(state, state.player, selection, state.shopSelectedSellIds)
            updated.copy(
                lastAction = if (selecting) "已选中 ${offer.item.name}" else "已取消 ${offer.item.name}"
            )
        }
    }

    fun onToggleShopSellSelection(itemId: String) {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略出售选择")
            return
        }
        val item = current.player.inventory.items.firstOrNull { it.uid == itemId }
        if (item == null) {
            GameLogger.warn("商店系统", "未找到背包物品：$itemId")
            return
        }
        val selection = current.shopSelectedSellIds.toMutableSet()
        val selecting = if (selection.contains(itemId)) {
            selection.remove(itemId)
            false
        } else {
            selection.add(itemId)
            true
        }
        GameLogger.info("商店系统", "出售选择切换：${item.name} 选中=$selecting")
        _state.update { state ->
            val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, selection)
            updated.copy(
                lastAction = if (selecting) "已勾选 ${item.name}" else "已取消 ${item.name}"
            )
        }
    }

    fun onShopBuySelected() {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略购买")
            return
        }
        val selectedIds = current.shopSelectedOfferIds
        if (selectedIds.isEmpty()) {
            GameLogger.warn("商店系统", "未选择商品，无法购买")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "未选择商品",
                    log = updated.log + "未选择商品，无法购买"
                )
            }
            return
        }
        val selectedOffers = shopOffers.filter { selectedIds.contains(it.offerId) }
        if (selectedOffers.isEmpty()) {
            GameLogger.warn("商店系统", "选中的商品已失效，无法购买")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, emptySet(), state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "选中的商品已失效",
                    log = updated.log + "选中的商品已失效，已清空选择"
                )
            }
            return
        }
        val availableOffers = selectedOffers.filter { it.stock > 0 && it.lockedReason.isBlank() }
        if (availableOffers.isEmpty()) {
            GameLogger.warn("商店系统", "选中的商品不可购买")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, emptySet(), state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "选中的商品不可购买",
                    log = updated.log + "选中的商品不可购买"
                )
            }
            return
        }
        val duplicateOffers = availableOffers.filter { isDuplicateEquipment(current.player, it.item) }
        if (duplicateOffers.isNotEmpty()) {
            val duplicateIds = duplicateOffers.map { it.offerId }.toSet()
            GameLogger.warn("商店系统", "选中商品包含重复装备，已标记不可购买：数量=${duplicateIds.size}")
            shopOffers = shopOffers.map { offer ->
                if (duplicateIds.contains(offer.offerId)) {
                    offer.copy(lockedReason = "已拥有同类装备")
                } else {
                    offer
                }
            }
            _state.update { state ->
                val updated = applyShopUiState(
                    state,
                    state.player,
                    selectedIds - duplicateIds,
                    state.shopSelectedSellIds
                )
                updated.copy(
                    lastAction = "已拥有同类装备",
                    log = updated.log + "已拥有同类装备，相关商品已标记不可购买"
                )
            }
            return
        }
        val totalPrice = availableOffers.sumOf { it.price }
        val inventory = current.player.inventory
        val capacityNeeded = availableOffers.size
        if (inventory.items.size + capacityNeeded > inventory.capacity) {
            GameLogger.warn("商店系统", "背包容量不足，无法购买：需要=$capacityNeeded 容量=${inventory.capacity}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "背包容量不足",
                    log = updated.log + "背包容量不足，无法购买选中商品"
                )
            }
            return
        }
        if (current.player.gold < totalPrice) {
            GameLogger.warn("商店系统", "金币不足，无法购买：需要=$totalPrice 当前=${current.player.gold}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "金币不足",
                    log = updated.log + "金币不足，无法购买选中商品"
                )
            }
            return
        }
        var player = current.player.copy(gold = current.player.gold - totalPrice)
        val logs = mutableListOf<String>()
        val purchasedIds = mutableSetOf<String>()
        availableOffers.forEach { offer ->
            val added = addEquipmentToInventoryFromShop(player, offer.item)
            player = added.player
            purchasedIds += offer.offerId
            logs += "购买 ${offer.item.name}（${offer.item.rarityName}） -${offer.price} 金币"
            logs += added.logs
            GameLogger.info(
                "商店系统",
                "购买完成：${offer.item.name} 价格=${offer.price} 剩余金币=${player.gold}"
            )
        }
        shopOffers = shopOffers.map { offer ->
            if (purchasedIds.contains(offer.offerId)) {
                offer.copy(stock = (offer.stock - 1).coerceAtLeast(0))
            } else {
                offer
            }
        }
        val remainingSelection = selectedIds - purchasedIds
        val summary = "购买完成：${purchasedIds.size} 件，花费 $totalPrice 金币"
        logs += summary
        val newChoices = buildShopChoices(
            event,
            current.chapter,
            current.selectedDifficulty,
            current.turn,
            player
        )
        _state.update { state ->
            val updated = applyShopUiState(
                state.copy(player = player, choices = newChoices),
                player,
                remainingSelection,
                state.shopSelectedSellIds
            )
            updated.copy(
                lastAction = summary,
                log = updated.log + logs
            )
        }
    }

    fun onShopBuyPotion() {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略药水购买")
            return
        }
        val price = POTION_PRICE
        if (current.player.gold < price) {
            GameLogger.warn("商店系统", "金币不足，无法购买药水：金币=${current.player.gold} 价格=$price")
            _state.update { state ->
                state.copy(
                    lastAction = "金币不足，无法购买药水",
                    log = state.log + "金币不足，无法购买${POTION_NAME}"
                )
            }
            return
        }
        val nextGold = current.player.gold - price
        val nextPotion = current.player.potionCount + 1
        GameLogger.info(
            "商店系统",
            "购买药水：名称=$POTION_NAME 价格=$price 金币 剩余金币=$nextGold 药水数量=$nextPotion"
        )
        _state.update { state ->
            state.copy(
                player = state.player.copy(
                    gold = nextGold,
                    potionCount = nextPotion
                ),
                lastAction = "购买${POTION_NAME}",
                log = state.log + "购买${POTION_NAME} -$price 金币（剩余药水 $nextPotion）"
            )
        }
    }

    fun onShopSellSelected() {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略出售")
            return
        }
        val selectedIds = current.shopSelectedSellIds
        if (selectedIds.isEmpty()) {
            GameLogger.warn("商店系统", "未勾选物品，无法出售")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "未勾选物品",
                    log = updated.log + "未勾选物品，无法出售"
                )
            }
            return
        }
        val inventory = current.player.inventory
        val sellItems = inventory.items.filter { selectedIds.contains(it.uid) }
        if (sellItems.isEmpty()) {
            GameLogger.warn("商店系统", "选中的物品已不存在，出售取消")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, emptySet())
                updated.copy(
                    lastAction = "选中的物品已不存在",
                    log = updated.log + "选中的物品已不存在，已清空勾选"
                )
            }
            return
        }
        val total = sellItems.sumOf { estimateEquipmentSellValue(it) }
        val remaining = inventory.items.filterNot { selectedIds.contains(it.uid) }
        val updatedPlayer = current.player.copy(
            gold = current.player.gold + total,
            inventory = inventory.copy(items = remaining)
        )
        val logs = sellItems.map { item ->
            val value = estimateEquipmentSellValue(item)
            "卖出 ${item.name}（${item.rarityName}） +$value 金币"
        }.toMutableList()
        val summary = "卖出完成：${sellItems.size} 件，获得 $total 金币"
        logs += summary
        GameLogger.info("商店系统", summary)
        _state.update { state ->
            val updated = applyShopUiState(state.copy(player = updatedPlayer), updatedPlayer, state.shopSelectedOfferIds, emptySet())
            updated.copy(
                lastAction = summary,
                log = updated.log + logs
            )
        }
    }

    fun onShopLeave() {
        val current = _state.value
        val event = current.currentEvent
        if (event == null || !isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略离开")
            return
        }
        GameLogger.info("商店系统", "离开商店")
        shopEventId = null
        shopOffers = emptyList()
        _state.update { state ->
            val cleared = clearShopUiState(state)
            cleared.copy(
                lastAction = "离开商店",
                log = cleared.log + "离开商店"
            )
        }
        advanceToNextNode(incrementTurn = true)
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
        if (current.screen != GameScreen.ADVENTURE && current.screen != GameScreen.ROLE_DETAIL) {
            GameLogger.warn("装备系统", "当前界面无法装备：${current.screen}")
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
        val battleSessionSnapshot = battleSession
        val preparedPlayer = if (battleSessionSnapshot != null) {
            current.player.copy(hp = battleSessionSnapshot.player.hp, mp = battleSessionSnapshot.player.mp)
        } else {
            current.player
        }
        val updatedPlayer = recalculatePlayerStats(
            preparedPlayer.copy(
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
        if (battleSessionSnapshot != null) {
            val refreshedSession = refreshBattleSessionForEquipmentChange(
                session = battleSessionSnapshot,
                updatedPlayer = updatedPlayer,
                reason = "战斗中装备 ${target.name}"
            )
            battleSession = refreshedSession
            updateBattleStateAfterEquipmentChange(
                refreshedSession,
                updatedPlayer,
                logLine,
                "已装备 ${target.name}"
            )
        } else {
            _state.update {
                it.copy(
                    player = updatedPlayer,
                    lastAction = "已装备 ${target.name}",
                    log = it.log + logLine
                )
            }
        }
    }

    fun onUnequipSlot(slot: EquipmentSlot) {
        val current = _state.value
        if (current.screen != GameScreen.ADVENTURE && current.screen != GameScreen.ROLE_DETAIL) {
            GameLogger.warn("装备系统", "当前界面无法卸下装备：${current.screen}")
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
        val battleSessionSnapshot = battleSession
        val preparedPlayer = if (battleSessionSnapshot != null) {
            current.player.copy(hp = battleSessionSnapshot.player.hp, mp = battleSessionSnapshot.player.mp)
        } else {
            current.player
        }
        val updatedPlayer = recalculatePlayerStats(
            preparedPlayer.copy(equipment = nextLoadout, inventory = nextInventory),
            "卸下 ${item.name}"
        )
        GameLogger.info("装备系统", "卸下装备：${item.name} 槽位=$slot")
        val logLine = "卸下 ${item.name}"
        if (battleSessionSnapshot != null) {
            val refreshedSession = refreshBattleSessionForEquipmentChange(
                session = battleSessionSnapshot,
                updatedPlayer = updatedPlayer,
                reason = "战斗中卸下 ${item.name}"
            )
            battleSession = refreshedSession
            updateBattleStateAfterEquipmentChange(
                refreshedSession,
                updatedPlayer,
                logLine,
                "已卸下 ${item.name}"
            )
        } else {
            _state.update {
                it.copy(
                    player = updatedPlayer,
                    lastAction = "已卸下 ${item.name}",
                    log = it.log + logLine
                )
            }
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
        shopEventId = null
        shopOffers = emptyList()
        stageRuntime = null
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            val base = current.copy(
                screen = GameScreen.ROLE_SELECT,
                selectedSaveSlot = slot,
                selectedRoleId = initialRole?.id ?: "",
                turn = 1,
                chapter = 1,
                selectedChapter = 1,
                selectedDifficulty = 1,
                completedChapters = emptyList(),
                stage = StageUiState(),
                player = PlayerStats(),
                currentEvent = null,
                enemyPreview = null,
                battle = null,
                playerStatuses = emptyList(),
                choices = emptyList(),
                lastAction = "已选择新存档槽位 $slot",
                log = current.log + "已选择新存档槽位 $slot，请选择角色",
                showCardDialog = false,
                cardOptions = emptyList(),
                cardDialogLevel = 0,
                cardDialogReason = "",
                pendingCardLevels = emptyList(),
                pendingCardReasons = emptyList()
            )
            clearShopUiState(base)
        }
    }

    fun onReturnToMain() {
        GameLogger.info(logTag, "返回主界面并重新选择角色")
        battleSession = null
        battleEventId = null
        shopEventId = null
        shopOffers = emptyList()
        stageRuntime = null
        pendingNewSaveSlot = null
        val initialRole = roles.firstOrNull { it.unlocked }
        _state.update { current ->
            val base = current.copy(
                screen = GameScreen.SAVE_SELECT,
                selectedSaveSlot = null,
                selectedRoleId = initialRole?.id ?: "",
                turn = 1,
                chapter = 1,
                selectedChapter = 1,
                selectedDifficulty = current.selectedDifficulty.coerceAtLeast(1),
                completedChapters = emptyList(),
                stage = StageUiState(),
                player = PlayerStats(),
                currentEvent = null,
                enemyPreview = null,
                battle = null,
                playerStatuses = emptyList(),
                choices = emptyList(),
                lastAction = "已返回主界面",
                log = current.log + "返回主界面，重新选择存档与角色",
                showDialog = false,
                dialogTitle = "",
                dialogMessage = "",
                showCardDialog = false,
                cardOptions = emptyList(),
                cardDialogLevel = 0,
                cardDialogReason = "",
                pendingCardLevels = emptyList(),
                pendingCardReasons = emptyList()
            )
            clearShopUiState(base)
        }
        refreshSaveSlots()
    }

    fun onDismissDialog() {
        _state.update { current ->
            if (!current.showDialog) current else current.copy(showDialog = false, dialogTitle = "", dialogMessage = "")
        }
    }

    fun onSelectCardOption(uid: String) {
        val current = _state.value
        if (!current.showCardDialog) {
            GameLogger.warn(logTag, "卡牌选择已关闭，忽略选择：$uid")
            return
        }
        val option = current.cardOptions.firstOrNull { it.uid == uid }
        if (option == null) {
            GameLogger.warn(logTag, "未找到卡牌选项：$uid")
            return
        }
        val qualityLabel = cardQualityLabel(option.quality)
        val goodLabel = if (option.isGood) "厉害" else "垃圾"
        val effectText = formatCardEffects(option.effects)
        val logLine = "选择卡牌：Lv${current.cardDialogLevel} ${option.name}（$qualityLabel/$goodLabel）效果：$effectText"
        GameLogger.info(
            logTag,
            "卡牌选择完成：等级=${current.cardDialogLevel} 品质=$qualityLabel 类型=$goodLabel 名称=${option.name} 原因=${current.cardDialogReason}"
        )
        val nextPlayer = recalculatePlayerStats(
            current.player.copy(cards = current.player.cards + option),
            "选择卡牌"
        )
        _state.update { state ->
            state.copy(
                player = nextPlayer,
                showCardDialog = false,
                cardOptions = emptyList(),
                cardDialogLevel = 0,
                cardDialogReason = "",
                lastAction = "已选择卡牌",
                log = state.log + logLine
            )
        }
        openNextCardDialogIfNeeded()
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
        val difficulty = current.selectedDifficulty.coerceIn(1, 5)
        val stageContext = StageContext(
            chapter = chapter,
            difficulty = difficulty,
            turn = nextTurn
        )
        val previousRuntime = stageRuntime
        val runtime = stageRuntime ?: assignGuardian(stageEngine.startStageForChapter(chapter, rng, difficulty))
        val shouldRestartStage = runtime.completed || runtime.stage.chapter != chapter
        val nextRuntime = if (shouldRestartStage) {
            assignGuardian(stageEngine.startStageForChapter(chapter, rng, difficulty))
        } else if (!forcedNodeId.isNullOrBlank()) {
            stageEngine.moveToNode(runtime, forcedNodeId) ?: stageEngine.moveToNextNode(runtime, rng, stageContext)
        } else {
            stageEngine.moveToNextNode(runtime, rng, stageContext)
        }
        stageRuntime = nextRuntime
        val node = stageEngine.currentNode(nextRuntime)
        val forcedEvent = buildForcedEvent(forcedEventId, chapter, difficulty, nextTurn)
        val nextEvent = forcedEvent ?: resolveEventForNode(nextRuntime, node, chapter)
        val nextChoices = buildEventChoices(nextEvent, chapter, difficulty, nextTurn, current.player)

        val stageLog = if (shouldRestartStage) {
            "进入关卡：${nextRuntime.stage.name}"
        } else {
            val nodeId = node?.id ?: nextRuntime.currentNodeId
            val isHidden = node?.hidden == true || nodeId.contains("hidden", ignoreCase = true)
            val reason = nextRuntime.lastMoveReason.ifBlank { "推进" }
            if (isHidden) {
                "发现隐藏路径：$nodeId（$reason）"
            } else {
                "移动到节点：$nodeId（$reason）"
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

        val completionUpdate = computeCompletionUpdate(
            current.completedChapters,
            previousRuntime,
            nextRuntime,
            chapter
        )

        _state.update { state ->
            val enemyPreview = buildEnemyPreview(nextEvent, state.player)
            val base = state.copy(
                turn = nextTurn,
                chapter = chapter,
                completedChapters = completionUpdate.first,
                stage = nextRuntime.toUiState(node),
                currentEvent = nextEvent,
                enemyPreview = enemyPreview,
                battle = null,
                playerStatuses = emptyList(),
                choices = nextChoices,
                log = state.log + listOf(stageLog) +
                    listOfNotNull(commandLog, guardianLog, nextEvent?.introText) +
                    completionUpdate.second
            )
            if (nextEvent != null && isShopEvent(nextEvent)) {
                applyShopUiState(base, base.player, emptySet(), emptySet())
            } else {
                clearShopUiState(base)
            }
        }
        if (nextEvent != null && isBattleEvent(nextEvent)) {
            startBattleSession(nextEvent)
        }
    }

    private fun applySaveGame(slot: Int, saveGame: SaveGame) {
        battleSession = null
        battleEventId = null
        shopEventId = null
        shopOffers = emptyList()
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
        val event = if (monsterOnlyMode) {
            val savedEventId = saveGame.currentEventId
            if (savedEventId?.startsWith("auto_shop") == true) {
                GameLogger.info("存档系统", "纯怪物模式读取商店事件：事件编号=$savedEventId")
                buildShopEvent(saveGame.chapter, saveGame.selectedDifficulty, saveGame.turn, savedEventId)
            } else {
                GameLogger.info("存档系统", "纯怪物模式开启，忽略存档事件编号=${savedEventId ?: "无"}")
                resolveEventForNode(assignedRuntime, node, saveGame.chapter)
            }
        } else {
            saveGame.currentEventId?.let { id ->
                engine.eventById(id)
            } ?: resolveEventForNode(assignedRuntime, node, saveGame.chapter)
        }
        if (!monsterOnlyMode && saveGame.currentEventId != null && event == null) {
            GameLogger.warn("存档系统", "存档事件未找到：事件编号=${saveGame.currentEventId}")
        }

        val validRoleId = if (roles.any { it.id == saveGame.selectedRoleId }) {
            saveGame.selectedRoleId
        } else {
            roles.firstOrNull { it.unlocked }?.id ?: ""
        }
        val normalizedPlayer = normalizePlayerStats(saveGame.player, validRoleId, "读取存档")
        val choices = buildEventChoices(
            event,
            saveGame.chapter,
            saveGame.selectedDifficulty,
            saveGame.turn,
            normalizedPlayer
        )
        val runtimeNode = node

        _state.update { current ->
            val enemyPreview = buildEnemyPreview(event, normalizedPlayer)
            val base = current.copy(
                turn = saveGame.turn,
                chapter = saveGame.chapter,
                selectedChapter = saveGame.chapter,
                stage = assignedRuntime.toUiState(runtimeNode),
                selectedRoleId = validRoleId,
                selectedDifficulty = saveGame.selectedDifficulty.coerceIn(1, 5),
                completedChapters = saveGame.completedChapters.distinct().sorted(),
                player = normalizedPlayer,
                currentEvent = event,
                enemyPreview = enemyPreview,
                battle = null,
                playerStatuses = emptyList(),
                choices = choices,
                activePanel = saveGame.activePanel,
                showSkillFormula = saveGame.showSkillFormula,
                showCardDialog = saveGame.showCardDialog,
                cardOptions = saveGame.cardOptions,
                cardDialogLevel = saveGame.cardDialogLevel,
                cardDialogReason = saveGame.cardDialogReason,
                pendingCardLevels = saveGame.pendingCardLevels,
                pendingCardReasons = saveGame.pendingCardReasons,
                lastAction = "已读取槽位 $slot",
                log = saveGame.log + "已读取槽位 $slot",
                saveSlots = current.saveSlots
            )
            if (event != null && isShopEvent(event)) {
                applyShopUiState(base, normalizedPlayer, emptySet(), emptySet())
            } else {
                clearShopUiState(base)
            }
        }
        if (event != null && isBattleEvent(event)) {
            startBattleSession(event)
        }
        if (!saveGame.showCardDialog && saveGame.pendingCardLevels.isNotEmpty()) {
            GameLogger.info(logTag, "存档包含未处理卡牌抉择，准备恢复弹窗")
            openNextCardDialogIfNeeded()
        }
    }

    private fun startStageForTurn(turn: Int, reason: String) {
        if (_state.value.screen == GameScreen.SAVE_SELECT) {
            GameLogger.info(logTag, "尚未完成存档选择，跳过关卡初始化")
            return
        }
        val chapter = chapterForTurn(turn)
        val difficulty = _state.value.selectedDifficulty.coerceIn(1, 5)
        startStageForSelection(turn, chapter, difficulty, reason)
    }

    private fun startStageForSelection(
        turn: Int,
        chapter: Int,
        difficulty: Int,
        reason: String
    ) {
        shopEventId = null
        shopOffers = emptyList()
        val runtime = assignGuardian(stageEngine.startStageForChapter(chapter, rng, difficulty))
        stageRuntime = runtime
        val node = stageEngine.currentNode(runtime)
        val event = resolveEventForNode(runtime, node, chapter)
        val choices = buildEventChoices(event, chapter, difficulty, turn, _state.value.player)
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
            "初始化关卡：回合=$turn 章节=$chapter 难度=$difficulty 关卡编号=${runtime.stage.id} 节点编号=${node?.id ?: "无"} 随机种子=$rngSeed"
        )
        _state.update { current ->
            val enemyPreview = buildEnemyPreview(event, current.player)
            val base = current.copy(
                turn = turn,
                chapter = chapter,
                selectedDifficulty = difficulty,
                selectedChapter = chapter,
                stage = runtime.toUiState(node),
                currentEvent = event,
                enemyPreview = enemyPreview,
                battle = null,
                playerStatuses = emptyList(),
                choices = choices,
                log = current.log + listOf(stageLog) + listOfNotNull(commandLog, guardianLog, event?.introText)
            )
            if (event != null && isShopEvent(event)) {
                applyShopUiState(base, base.player, emptySet(), emptySet())
            } else {
                clearShopUiState(base)
            }
        }
        if (event != null && isBattleEvent(event)) {
            startBattleSession(event)
        }
    }

    private fun chapterForTurn(turn: Int): Int {
        if (monsterOnlyMode) {
            return _state.value.selectedChapter.coerceIn(1, maxChapter)
        }
        val raw = ((turn - 1) / 3) + 1
        return min(maxChapter, max(1, raw))
    }

    private fun chapterStartTurn(chapter: Int): Int {
        val normalized = chapter.coerceIn(1, maxChapter)
        return ((normalized - 1) * 3) + 1
    }

    private fun isChapterUnlocked(chapter: Int, completedChapters: List<Int>): Boolean {
        if (chapter <= 1) return true
        return completedChapters.contains(chapter) || completedChapters.contains(chapter - 1)
    }

    private fun computeCompletionUpdate(
        currentCompleted: List<Int>,
        previousRuntime: StageRuntime?,
        nextRuntime: StageRuntime?,
        logicalChapter: Int
    ): Pair<List<Int>, List<String>> {
        val updated = currentCompleted.toMutableSet()
        val logs = mutableListOf<String>()
        if (previousRuntime?.completed == true) {
            val chapterToMark = logicalChapter.coerceAtLeast(1)
            if (updated.add(chapterToMark)) {
                logs += "章节通关：第 $chapterToMark 章"
                GameLogger.info(logTag, "章节通关已记录：章节=$chapterToMark")
            }
        }
        if (previousRuntime != null && nextRuntime != null) {
            val justCompleted = !previousRuntime.completed && nextRuntime.completed && previousRuntime.stage.id == nextRuntime.stage.id
            if (justCompleted) {
                val chapterToMark = logicalChapter.coerceAtLeast(1)
                if (updated.add(chapterToMark)) {
                    logs += "章节通关：第 $chapterToMark 章"
                    GameLogger.info(logTag, "章节通关已记录：章节=$chapterToMark")
                }
            }
        }
        return updated.toList().sorted() to logs
    }

    private fun applyRole(role: RoleProfile, reason: String) {
        val normalizedStats = normalizeCharacterStats(role.stats)
        GameLogger.info(
            logTag,
            "应用角色：${role.name}，原因=$reason，属性=生命${normalizedStats.hp}/攻击${normalizedStats.atk}/防御${normalizedStats.def}/速度${normalizedStats.speed} 力量${normalizedStats.strength} 智力${normalizedStats.intelligence} 敏捷${normalizedStats.agility}"
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
        val normalizedStats = normalizeCharacterStats(role.stats)
        val base = PlayerBaseStats(
            hpMax = normalizedStats.hp,
            mpMax = 30,
            atk = normalizedStats.atk,
            def = normalizedStats.def,
            speed = normalizedStats.speed,
            strength = normalizedStats.strength,
            intelligence = normalizedStats.intelligence,
            agility = normalizedStats.agility
        )
        val raw = PlayerStats(
            name = role.name,
            hp = normalizedStats.hp,
            hpMax = normalizedStats.hp,
            mp = 30,
            mpMax = 30,
            atk = normalizedStats.atk,
            def = normalizedStats.def,
            speed = normalizedStats.speed,
            strength = normalizedStats.strength,
            intelligence = normalizedStats.intelligence,
            agility = normalizedStats.agility,
            level = 1,
            exp = 0,
            expToNext = expRequiredFor(1),
            gold = 0,
            materials = 0,
            baseStats = base,
            equipment = EquipmentLoadout(),
            inventory = InventoryState(),
            potionCount = 0,
            battleSkillSlots = normalizeBattleSkillSlots(emptyList(), role.id, "应用角色")
        )
        return recalculatePlayerStats(raw, "应用角色")
    }

    private data class ResultApplication(
        val player: PlayerStats,
        val logs: List<String>,
        val pendingCardLevels: List<Int> = emptyList(),
        val pendingCardReasons: List<String> = emptyList()
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
        val pendingLevels = mutableListOf<Int>()
        val pendingReasons = mutableListOf<String>()
        if (result.expDelta != 0) {
            val expApplied = applyExperience(updated, result.expDelta, reason)
            updated = expApplied.player
            logs += expApplied.logs
            pendingLevels += expApplied.pendingCardLevels
            pendingReasons += expApplied.pendingCardReasons
        }
        val dropId = result.dropTableId ?: event?.dropTableId
        if (!dropId.isNullOrBlank()) {
            val loot = applyLoot(updated, dropId, event, reason)
            updated = loot.player
            logs += loot.logs
        }
        return ResultApplication(updated, logs, pendingLevels, pendingReasons)
    }

    private fun applyExperience(
        player: PlayerStats,
        expDelta: Int,
        reason: String
    ): ResultApplication {
        val nextExp = (player.exp + expDelta).coerceAtLeast(0)
        var current = player.copy(exp = nextExp)
        val logs = mutableListOf<String>()
        val pendingLevels = mutableListOf<Int>()
        val pendingReasons = mutableListOf<String>()
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
                mpMax = current.baseStats.mpMax + growth.mpMax,
                atk = current.baseStats.atk + growth.atk,
                def = current.baseStats.def + growth.def,
                speed = current.baseStats.speed + growth.speed,
                strength = current.baseStats.strength + growth.strength,
                intelligence = current.baseStats.intelligence + growth.intelligence,
                agility = current.baseStats.agility + growth.agility
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
            val log = "升级到 Lv$nextLevel：生命上限+${growth.hpMax} 能量上限+${growth.mpMax} 攻击+${growth.atk} 防御+${growth.def} 速度+${growth.speed} 力量+${growth.strength} 智力+${growth.intelligence} 敏捷+${growth.agility}"
            logs += log
            GameLogger.info(
                logTag,
                "角色升级：Lv$beforeLevel -> Lv$nextLevel 经验=${current.exp}/${current.expToNext} 原因=$reason"
            )
            if (nextLevel % 3 == 0) {
                pendingLevels += nextLevel
                pendingReasons += reason
                logs += "触发卡牌抉择：Lv$nextLevel"
                GameLogger.info(logTag, "触发卡牌抉择：等级=$nextLevel 原因=$reason")
            }
        }
        return ResultApplication(current, logs, pendingLevels, pendingReasons)
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
        val bonusChance = bonusLootChance(event)
        val bonusRoll = rng.nextDouble() < bonusChance
        val totalRolls = if (bonusRoll) 2 else 1
        if (bonusRoll) {
            GameLogger.info(
                "掉落系统",
                "触发额外掉落判定：来源=$sourceType 层级=$tier 掉落表=$dropTableId 额外概率=${"%.2f".format(bonusChance)} 原因=$reason"
            )
        }
        var updatedPlayer = player.copy(pityCounters = pity)
        val logs = mutableListOf<String>()
        repeat(totalRolls) { rollIndex ->
            val outcome = lootRepository.generateLoot(sourceType, tier, rng, pity)
            GameLogger.info(
                "掉落系统",
                "生成掉落：来源=$sourceType 层级=$tier 掉落表=$dropTableId 结果=$outcome 轮次=${rollIndex + 1}/$totalRolls 原因=$reason"
            )
            if (rollIndex > 0) {
                logs += "高难度追加掉落触发"
            }
            val applied = applyLootOutcome(updatedPlayer, outcome, dropTableId)
            updatedPlayer = applied.player
            logs += applied.logs
        }
        return LootApplication(updatedPlayer, logs)
    }

    private fun bonusLootChance(event: EventDefinition?): Double {
        if (event == null) return 0.0
        val difficulty = event.difficulty.coerceIn(1, 5)
        val base = when {
            difficulty >= 5 -> 0.35
            difficulty >= 4 -> 0.25
            difficulty >= 3 -> 0.15
            else -> 0.0
        }
        val tagBonus = when {
            event.type.contains("boss", ignoreCase = true) -> 0.15
            event.type.contains("elite", ignoreCase = true) -> 0.1
            else -> 0.0
        }
        val chapterBonus = (event.chapter - 1).coerceAtLeast(0) * 0.02
        val difficultyBonus = (difficulty - 1).coerceAtLeast(0) * 0.02
        val battleBonus = if (event.type.contains("battle", ignoreCase = true)) 0.05 else 0.0
        val chance = (base + tagBonus + chapterBonus + difficultyBonus + battleBonus).coerceIn(0.0, 0.75)
        GameLogger.info(
            "掉落系统",
            "额外掉落概率计算：章节=${event.chapter} 难度=$difficulty 类型=${event.type} 基准=${"%.2f".format(base)} 标签=${"%.2f".format(tagBonus)} 章节加成=${"%.2f".format(chapterBonus)} 难度加成=${"%.2f".format(difficultyBonus)} 战斗加成=${"%.2f".format(battleBonus)} 概率=${"%.2f".format(chance)}"
        )
        return chance
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
        if (isDuplicateEquipment(player, item)) {
            val gold = estimateEquipmentSellValue(item)
            GameLogger.warn(
                "掉落系统",
                "已拥有同类装备，自动折算：${item.name} 模板=${item.templateId} -> 金币+$gold"
            )
            val discovery = recordEquipmentDiscovery(player, item, "掉落重复装备折算")
            return LootApplication(
                player = discovery.player.copy(gold = discovery.player.gold + gold),
                logs = discovery.logs + "已有同类装备，${item.name} 已折算金币 +$gold"
            )
        }
        return if (inventory.items.size >= inventory.capacity) {
            val gold = estimateEquipmentSellValue(item)
            GameLogger.warn(
                "掉落系统",
                "背包已满，装备自动折算：${item.name} -> 金币+$gold"
            )
            val discovery = recordEquipmentDiscovery(player, item, "掉落背包满折算")
            LootApplication(
                player = discovery.player.copy(gold = discovery.player.gold + gold),
                logs = discovery.logs + "背包已满，${item.name} 已折算金币 +$gold"
            )
        } else {
            GameLogger.info("掉落系统", "新增装备到背包：${item.name}（${item.rarityName}）")
            val discovery = recordEquipmentDiscovery(player, item, "掉落获得装备")
            LootApplication(
                player = discovery.player.copy(
                    inventory = inventory.copy(items = inventory.items + item)
                ),
                logs = discovery.logs + "获得装备：${item.name}（${item.rarityName}）"
            )
        }
    }

    private fun addEquipmentToInventoryFromShop(
        player: PlayerStats,
        item: EquipmentItem
    ): LootApplication {
        val inventory = player.inventory
        if (isDuplicateEquipment(player, item)) {
            GameLogger.warn(
                "商店系统",
                "商店购买出现重复装备，拒绝入包：${item.name} 模板=${item.templateId}"
            )
            return LootApplication(
                player = player,
                logs = listOf("已拥有同类装备，${item.name} 未加入背包")
            )
        }
        if (inventory.items.size >= inventory.capacity) {
            GameLogger.warn("商店系统", "商店购买背包已满，无法入包：${item.name}")
            return LootApplication(
                player = player,
                logs = listOf("背包已满，${item.name} 未加入背包")
            )
        }
        GameLogger.info("商店系统", "商店购买入包：${item.name}（${item.rarityName}）")
        val discovery = recordEquipmentDiscovery(player, item, "商店购买")
        return LootApplication(
            player = discovery.player.copy(
                inventory = inventory.copy(items = inventory.items + item)
            ),
            logs = discovery.logs + "获得装备：${item.name}（${item.rarityName}）"
        )
    }

    private fun ownedEquipmentTemplateIds(player: PlayerStats): Set<String> {
        val equipped = player.equipment.slots.values.map { it.templateId }
        val inventory = player.inventory.items.map { it.templateId }
        return (equipped + inventory).toSet()
    }

    private fun isDuplicateEquipment(player: PlayerStats, item: EquipmentItem): Boolean {
        return ownedEquipmentTemplateIds(player).contains(item.templateId)
    }

    private data class DiscoveryUpdate(
        val player: PlayerStats,
        val logs: List<String> = emptyList()
    )

    private fun recordEquipmentDiscovery(
        player: PlayerStats,
        item: EquipmentItem,
        reason: String
    ): DiscoveryUpdate {
        if (player.discoveredEquipmentIds.contains(item.templateId)) {
            return DiscoveryUpdate(player)
        }
        val updated = player.copy(
            discoveredEquipmentIds = (player.discoveredEquipmentIds + item.templateId).distinct()
        )
        GameLogger.info("装备图鉴", "图鉴解锁：装备=${item.name} 模板=${item.templateId} 原因=$reason")
        return DiscoveryUpdate(
            player = updated,
            logs = listOf("装备图鉴解锁：${item.name}")
        )
    }

    private fun recordEnemyDiscovery(
        player: PlayerStats,
        enemyId: String,
        enemyName: String,
        reason: String
    ): DiscoveryUpdate {
        if (player.discoveredEnemyIds.contains(enemyId)) {
            return DiscoveryUpdate(player)
        }
        val updated = player.copy(
            discoveredEnemyIds = (player.discoveredEnemyIds + enemyId).distinct()
        )
        GameLogger.info("怪物图鉴", "图鉴解锁：怪物=$enemyName 编号=$enemyId 原因=$reason")
        return DiscoveryUpdate(
            player = updated,
            logs = listOf("怪物图鉴解锁：$enemyName")
        )
    }

    private fun buildEquipmentDetailMessage(item: EquipmentItem): String {
        val baseStats = formatStatsForDetail(item.stats)
        val totalStats = formatStatsForDetail(item.totalStats())
        val affixLines = if (item.affixes.isEmpty()) {
            "无"
        } else {
            item.affixes.joinToString("，") { affix ->
                "${statLabelForDetail(affix.type)}${signed(affix.value)}"
            }
        }
        val sourceLabel = formatEquipmentSourceLabel(item.source)
        return buildString {
            append("名称：${item.name}\n")
            append("稀有度：${item.rarityName}\n")
            append("部位：${equipmentSlotLabelForDetail(item.slot)}\n")
            append("等级：${item.level}\n")
            append("评分：${item.score}\n")
            append("基础属性：$baseStats\n")
            append("词条：$affixLines\n")
            append("总属性：$totalStats\n")
            if (sourceLabel.isNotBlank()) {
                append("来源：$sourceLabel\n")
            }
            if (item.obtainedAtTurn > 0) {
                append("获取回合：${item.obtainedAtTurn}\n")
            }
        }
    }

    private fun formatStatsForDetail(stats: Map<StatType, Int>): String {
        if (stats.isEmpty()) return "无"
        return stats.entries.joinToString("，") { (type, value) ->
            "${statLabelForDetail(type)}${signed(value)}"
        }
    }

    private fun statLabelForDetail(type: StatType): String {
        return when (type) {
            StatType.HP -> "生命"
            StatType.ATK -> "攻击"
            StatType.DEF -> "防御"
            StatType.SPEED -> "速度"
            StatType.STR -> "力量"
            StatType.INT -> "智力"
            StatType.AGI -> "敏捷"
            StatType.HIT -> "命中"
            StatType.EVADE -> "闪避"
            StatType.CRIT -> "暴击"
            StatType.CRIT_RESIST -> "抗暴"
        }
    }

    private fun equipmentSlotLabelForDetail(slot: com.jungleadventure.shared.loot.EquipmentSlot): String {
        return when (slot) {
            com.jungleadventure.shared.loot.EquipmentSlot.WEAPON -> "武器"
            com.jungleadventure.shared.loot.EquipmentSlot.ARMOR -> "护甲"
            com.jungleadventure.shared.loot.EquipmentSlot.HELM -> "头盔"
            com.jungleadventure.shared.loot.EquipmentSlot.ACCESSORY -> "饰品"
        }
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
        val difficultyBonus = when {
            difficulty >= 5 -> 2
            difficulty >= 4 -> 1
            else -> 0
        }
        val bonus = when {
            dropTableId.contains("boss") -> 2
            dropTableId.contains("elite") -> 1
            else -> 0
        }
        val tier = (chapterTier + bonus + difficultyBonus).coerceIn(1, 3)
        GameLogger.info(
            "掉落系统",
            "掉落层级计算：掉落表=$dropTableId 难度=$difficulty 基准=$chapterTier 奖励=$bonus 难度加成=$difficultyBonus -> $tier"
        )
        return tier
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

    private fun battleRewardSummary(
        before: PlayerStats,
        after: PlayerStats
    ): String {
        val goldGain = after.gold - before.gold
        val materialGain = after.materials - before.materials
        val equipGain = (after.inventory.items.size - before.inventory.items.size).coerceAtLeast(0)
        val expGain = (totalExpValue(after) - totalExpValue(before)).coerceAtLeast(0)
        return "本次战斗获得：金币${signed(goldGain)} 经验${signed(expGain)} 材料${signed(materialGain)} 装备+$equipGain"
    }

    private fun totalExpValue(player: PlayerStats): Int {
        var total = 0
        for (level in 1 until player.level) {
            total += expRequiredFor(level)
        }
        return total + player.exp
    }

    private fun logBattleRewardSummary(
        before: PlayerStats,
        after: PlayerStats,
        victory: Boolean,
        reason: String
    ) {
        val goldGain = after.gold - before.gold
        val materialGain = after.materials - before.materials
        val equipGain = (after.inventory.items.size - before.inventory.items.size).coerceAtLeast(0)
        val expGain = (totalExpValue(after) - totalExpValue(before)).coerceAtLeast(0)
        GameLogger.info(
            logTag,
            "战斗奖励汇总：胜利=$victory 金币${signed(goldGain)} 经验${signed(expGain)} 材料${signed(materialGain)} 装备+$equipGain 原因=$reason"
        )
        GameLogger.info(
            logTag,
            "战斗奖励对比：金币${before.gold}->${after.gold} 经验总值${totalExpValue(before)}->${totalExpValue(after)} 材料${before.materials}->${after.materials} 背包${before.inventory.items.size}->${after.inventory.items.size}"
        )
    }

    private fun expRequiredFor(level: Int): Int {
        val safeLevel = level.coerceAtLeast(1)
        return expBase + (safeLevel - 1) * expGrowth
    }

    private fun normalizePlayerStats(player: PlayerStats, roleId: String, reason: String): PlayerStats {
        val base = if (player.baseStats.hpMax <= 0 || player.baseStats.atk <= 0) {
            PlayerBaseStats(
                hpMax = player.hpMax.coerceAtLeast(1),
                mpMax = player.mpMax.coerceAtLeast(1),
                atk = player.atk.coerceAtLeast(1),
                def = player.def.coerceAtLeast(0),
                speed = player.speed.coerceAtLeast(1),
                strength = player.strength.coerceAtLeast(0),
                intelligence = player.intelligence.coerceAtLeast(0),
                agility = player.agility.coerceAtLeast(0)
            )
        } else {
            player.baseStats
        }
        val expToNext = if (player.expToNext <= 0) {
            expRequiredFor(player.level)
        } else {
            player.expToNext
        }
        val safeHp = if (player.hp <= 0) {
            GameLogger.warn(logTag, "读取角色状态时生命为0，已自动修正为1，原因=$reason")
            1
        } else {
            player.hp
        }
        val normalized = player.copy(
            baseStats = base,
            hp = safeHp,
            exp = player.exp.coerceAtLeast(0),
            expToNext = expToNext
        )
        val battleReady = normalizeBattleConfig(normalized, roleId, reason)
        val synced = syncDiscoveredEquipment(battleReady, reason)
        val sanitized = sanitizeDuplicateEquipments(synced, reason)
        return recalculatePlayerStats(sanitized, reason)
    }

    private fun normalizeBattleConfig(player: PlayerStats, roleId: String, reason: String): PlayerStats {
        val normalizedSlots = normalizeBattleSkillSlots(
            slots = player.battleSkillSlots,
            roleId = roleId,
            reason = reason
        )
        val safePotion = player.potionCount.coerceAtLeast(0)
        if (normalizedSlots != player.battleSkillSlots || safePotion != player.potionCount) {
            GameLogger.info(
                logTag,
                "战斗配置修正：药水=${player.potionCount}->$safePotion 槽位=${player.battleSkillSlots.joinToString()} -> ${normalizedSlots.joinToString()} 原因=$reason"
            )
        }
        return player.copy(
            potionCount = safePotion,
            battleSkillSlots = normalizedSlots
        )
    }

    private fun syncDiscoveredEquipment(player: PlayerStats, reason: String): PlayerStats {
        val current = player.discoveredEquipmentIds.toMutableSet()
        val fromEquipped = player.equipment.slots.values.map { it.templateId }
        val fromInventory = player.inventory.items.map { it.templateId }
        val before = current.size
        current += fromEquipped
        current += fromInventory
        if (current.size != before) {
            GameLogger.info(
                "装备图鉴",
                "同步已发现装备：新增=${current.size - before} 原因=$reason"
            )
        }
        return player.copy(discoveredEquipmentIds = current.toList().distinct())
    }

    private fun sanitizeDuplicateEquipments(player: PlayerStats, reason: String): PlayerStats {
        if (player.inventory.items.isEmpty()) return player
        val equippedTemplates = player.equipment.slots.values.map { it.templateId }.toMutableSet()
        val grouped = player.inventory.items.groupBy { it.templateId }
        val keptItems = mutableListOf<EquipmentItem>()
        var goldGain = 0
        grouped.forEach { (templateId, items) ->
            if (equippedTemplates.contains(templateId)) {
                items.forEach { item ->
                    val gold = estimateEquipmentSellValue(item)
                    goldGain += gold
                    GameLogger.warn(
                        "装备系统",
                        "存档重复装备整理：已装备同类，自动折算 ${item.name} 模板=$templateId -> 金币+$gold 原因=$reason"
                    )
                }
                return@forEach
            }
            val best = items.maxByOrNull { it.score } ?: items.first()
            keptItems += best
            equippedTemplates += templateId
            items.filter { it.uid != best.uid }.forEach { item ->
                val gold = estimateEquipmentSellValue(item)
                goldGain += gold
                GameLogger.warn(
                    "装备系统",
                    "存档重复装备整理：保留高评分=${best.name} 折算 ${item.name} 模板=$templateId -> 金币+$gold 原因=$reason"
                )
            }
        }
        if (goldGain > 0) {
            GameLogger.info("装备系统", "重复装备整理完成：折算金币+$goldGain 原因=$reason")
        }
        if (goldGain == 0 && keptItems.size == player.inventory.items.size) {
            return player
        }
        return player.copy(
            gold = player.gold + goldGain,
            inventory = player.inventory.copy(items = keptItems)
        )
    }

    private fun recalculatePlayerStats(player: PlayerStats, reason: String): PlayerStats {
        val base = player.baseStats
        val bonus = collectEquipmentStats(player.equipment)
        val cardBonus = collectCardStats(player.cards)
        val baseStr = base.strength
        val baseInt = base.intelligence
        val baseAgi = base.agility
        val totalStr = (baseStr + (bonus[StatType.STR] ?: 0) + (cardBonus[StatType.STR] ?: 0)).coerceAtLeast(0)
        val totalInt = (baseInt + (bonus[StatType.INT] ?: 0) + (cardBonus[StatType.INT] ?: 0)).coerceAtLeast(0)
        val totalAgi = (baseAgi + (bonus[StatType.AGI] ?: 0) + (cardBonus[StatType.AGI] ?: 0)).coerceAtLeast(0)

        val hpFromStr = totalStr * 2
        val atkFromStr = totalStr / 2
        val mpFromInt = totalInt * 2
        val hitFromInt = totalInt / 3
        val defFromAgi = totalAgi / 2
        val speedFromAgi = totalAgi / 3
        val critFromAgi = totalAgi / 3
        val evaFromAgi = totalAgi / 4

        val nextHpMax = (base.hpMax + hpFromStr + (bonus[StatType.HP] ?: 0) + (cardBonus[StatType.HP] ?: 0)).coerceAtLeast(1)
        val nextMpMax = (base.mpMax + mpFromInt).coerceAtLeast(1)
        val nextAtk = (base.atk + atkFromStr + (bonus[StatType.ATK] ?: 0) + (cardBonus[StatType.ATK] ?: 0)).coerceAtLeast(1)
        val nextDef = (base.def + defFromAgi + (bonus[StatType.DEF] ?: 0) + (cardBonus[StatType.DEF] ?: 0)).coerceAtLeast(0)
        val nextSpeed = (base.speed + speedFromAgi + (bonus[StatType.SPEED] ?: 0) + (cardBonus[StatType.SPEED] ?: 0)).coerceAtLeast(1)
        val nextHp = player.hp.coerceIn(0, nextHpMax)
        val nextMp = player.mp.coerceIn(0, nextMpMax)
        val nextHitBonus = (bonus[StatType.HIT] ?: 0) + (cardBonus[StatType.HIT] ?: 0) + hitFromInt
        val nextEvaBonus = (bonus[StatType.EVADE] ?: 0) + (cardBonus[StatType.EVADE] ?: 0) + evaFromAgi
        val nextCritBonus = (bonus[StatType.CRIT] ?: 0) + (cardBonus[StatType.CRIT] ?: 0) + critFromAgi
        val nextResistBonus = (bonus[StatType.CRIT_RESIST] ?: 0) + (cardBonus[StatType.CRIT_RESIST] ?: 0)
        if (
            player.hpMax != nextHpMax ||
            player.mpMax != nextMpMax ||
            player.atk != nextAtk ||
            player.def != nextDef ||
            player.speed != nextSpeed ||
            player.strength != totalStr ||
            player.intelligence != totalInt ||
            player.agility != totalAgi
        ) {
            GameLogger.info(
                "装备系统",
                "刷新角色属性：生命上限${player.hpMax}->$nextHpMax 能量上限${player.mpMax}->$nextMpMax 攻击${player.atk}->$nextAtk 防御${player.def}->$nextDef 速度${player.speed}->$nextSpeed 力量${player.strength}->$totalStr 智力${player.intelligence}->$totalInt 敏捷${player.agility}->$totalAgi 原因=$reason"
            )
        }
        return player.copy(
            hp = nextHp,
            hpMax = nextHpMax,
            mp = nextMp,
            mpMax = nextMpMax,
            atk = nextAtk,
            def = nextDef,
            speed = nextSpeed,
            strength = totalStr,
            intelligence = totalInt,
            agility = totalAgi,
            hitBonus = nextHitBonus,
            evaBonus = nextEvaBonus,
            critBonus = nextCritBonus,
            resistBonus = nextResistBonus
        )
    }

    private fun collectCardStats(cards: List<CardInstance>): Map<StatType, Int> {
        val totals = mutableMapOf<StatType, Int>()
        cards.forEach { card ->
            card.effects.forEach { effect ->
                totals[effect.stat] = (totals[effect.stat] ?: 0) + effect.value
            }
        }
        return totals
    }

    private fun openNextCardDialogIfNeeded() {
        val current = _state.value
        if (current.showCardDialog) {
            GameLogger.info(logTag, "卡牌抉择已打开，等待玩家选择")
            return
        }
        if (current.pendingCardLevels.isEmpty()) return
        val level = current.pendingCardLevels.first()
        val reason = current.pendingCardReasons.firstOrNull().orEmpty().ifBlank { "升级" }
        val options = buildCardOptions(level, reason)
        val optionLabels = options.joinToString(" / ") { option ->
            val qualityLabel = cardQualityLabel(option.quality)
            val goodLabel = if (option.isGood) "厉害" else "垃圾"
            "${option.name}（$qualityLabel/$goodLabel）"
        }
        val optionLogs = options.mapIndexed { index, option ->
            val qualityLabel = cardQualityLabel(option.quality)
            val goodLabel = if (option.isGood) "厉害" else "垃圾"
            val effectText = formatCardEffects(option.effects)
            "候选卡牌${index + 1}：Lv$level ${option.name}（$qualityLabel/$goodLabel）效果：$effectText"
        }
        GameLogger.info(
            logTag,
            "卡牌抉择开启：等级=$level 原因=$reason 选项=$optionLabels"
        )
        _state.update { state ->
            state.copy(
                showCardDialog = true,
                cardOptions = options,
                cardDialogLevel = level,
                cardDialogReason = reason,
                pendingCardLevels = state.pendingCardLevels.drop(1),
                pendingCardReasons = state.pendingCardReasons.drop(1),
                lastAction = "卡牌抉择开启",
                log = state.log + listOf("卡牌抉择：Lv$level 出现 3 张候选卡牌") + optionLogs
            )
        }
    }

    private fun buildCardOptions(level: Int, reason: String): List<CardInstance> {
        val options = mutableListOf<CardInstance>()
        repeat(3) { index ->
            val definition = cardRepository.drawCard(rng)
            val uid = "card_option_${level}_${index}_${kotlin.math.abs(rng.nextInt())}"
            val instance = CardInstance(
                uid = uid,
                name = definition.name,
                quality = definition.quality,
                description = definition.description,
                effects = definition.effects,
                isGood = definition.isGood
            )
            options += instance
            val qualityLabel = cardQualityLabel(definition.quality)
            val goodLabel = if (definition.isGood) "厉害" else "垃圾"
            GameLogger.info(
                logTag,
                "卡牌候选生成：等级=$level 序号=${index + 1} 品质=$qualityLabel 类型=$goodLabel 名称=${definition.name} 原因=$reason"
            )
        }
        return options
    }

    private fun cardQualityLabel(quality: CardQuality): String {
        return when (quality) {
            CardQuality.COMMON -> "普通"
            CardQuality.UNCOMMON -> "优秀"
            CardQuality.RARE -> "稀有"
            CardQuality.EPIC -> "史诗"
            CardQuality.LEGEND -> "传说"
        }
    }

    private fun formatCardEffects(effects: List<CardEffect>): String {
        if (effects.isEmpty()) return "无"
        return effects.joinToString("，") { effect ->
            val value = if (effect.value >= 0) "+${effect.value}" else effect.value.toString()
            "${cardStatLabel(effect.stat)}$value"
        }
    }

    private fun cardStatLabel(stat: StatType): String {
        return when (stat) {
            StatType.HP -> "生命"
            StatType.ATK -> "攻击"
            StatType.DEF -> "防御"
            StatType.SPEED -> "速度"
            StatType.STR -> "力量"
            StatType.INT -> "智力"
            StatType.AGI -> "敏捷"
            StatType.HIT -> "命中"
            StatType.EVADE -> "闪避"
            StatType.CRIT -> "暴击"
            StatType.CRIT_RESIST -> "抗暴"
        }
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

    private fun normalizeCharacterStats(stats: CharacterStats): CharacterStats {
        val strength = if (stats.strength > 0) stats.strength else ((stats.atk + stats.def) / 4).coerceAtLeast(1)
        val agility = if (stats.agility > 0) stats.agility else (stats.speed / 2).coerceAtLeast(1)
        val intelligence = if (stats.intelligence > 0) stats.intelligence else (stats.perception / 4).coerceAtLeast(1)
        if (stats.strength == 0 || stats.intelligence == 0 || stats.agility == 0) {
            GameLogger.info(
                logTag,
                "角色三围补全：力量$strength 智力$intelligence 敏捷$agility（原始 力量${stats.strength} 智力${stats.intelligence} 敏捷${stats.agility}）"
            )
        }
        return stats.copy(
            strength = strength,
            intelligence = intelligence,
            agility = agility
        )
    }

    private fun normalizeGrowthProfile(growth: GrowthProfile): GrowthProfile {
        val strength = if (growth.strength > 0) growth.strength else max(1, growth.atk / 2)
        val intelligence = if (growth.intelligence > 0) growth.intelligence else max(1, growth.mpMax / 2)
        val agility = if (growth.agility > 0) growth.agility else max(1, growth.speed)
        return growth.copy(
            strength = strength,
            intelligence = intelligence,
            agility = agility
        )
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
            val normalizedStats = normalizeCharacterStats(character.stats)
            val passiveSkill = skillMap[character.passiveSkillId].toRoleSkill()
            val activeSkills = character.activeSkillIds.mapNotNull { skillId ->
                skillMap[skillId].toRoleSkill()
            }.toMutableList()
            while (activeSkills.size < 2) {
                activeSkills += defaultFallbackActive(activeSkills.size + 1)
            }
            val ultimateSkill = if (character.ultimateSkillId.isBlank()) {
                defaultFallbackUltimate()
            } else {
                skillMap[character.ultimateSkillId].toRoleSkill()
            }
            val growth = normalizeGrowthProfile(character.growth ?: defaultGrowthProfile())
            RoleProfile(
                id = character.id,
                name = character.name,
                role = character.role,
                stats = normalizedStats,
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

    private fun defaultFallbackActive(index: Int): RoleSkill {
        return RoleSkill(
            name = "基础技能$index",
            type = "ACTIVE",
            description = "未配置技能，使用基础攻击。",
            cost = "-",
            cooldown = "-",
            target = "敌方",
            effectLines = listOf("造成基础伤害"),
            formulaLines = listOf("100%ATK伤害")
        )
    }

    private fun defaultFallbackUltimate(): RoleSkill {
        return RoleSkill(
            name = "未配置大招",
            type = "ULTIMATE",
            description = "未配置终极技能。",
            cost = "-",
            cooldown = "-",
            target = "敌方",
            effectLines = listOf("暂无效果说明"),
            formulaLines = emptyList()
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

        val skillId = parseBattleSkillChoiceId(choiceId)
        if (skillId != null) {
            val skill = skillDefinitionMap[skillId]
            if (skill == null) {
                val message = "技能未找到，无法使用：$skillId"
                GameLogger.warn(logTag, message)
                _state.update { it.copy(lastAction = message, log = it.log + message) }
                return
            }
            val cooldownRemaining = currentSession.playerSkillCooldowns[skill.id] ?: 0
            if (cooldownRemaining > 0) {
                val message = "技能 ${skill.name} 冷却中（剩余 $cooldownRemaining 回合）"
                GameLogger.warn(logTag, message)
                _state.update { it.copy(lastAction = message, log = it.log + message) }
                return
            }
            if (currentSession.player.mp < skill.cost) {
                val message = "技能 ${skill.name} 能量不足（需要 ${skill.cost}）"
                GameLogger.warn(logTag, message)
                _state.update { it.copy(lastAction = message, log = it.log + message) }
                return
            }
        }

        val potionSlotIndex = when (choiceId) {
            BATTLE_CHOICE_POTION_1 -> 1
            BATTLE_CHOICE_POTION_2 -> 2
            BATTLE_CHOICE_ITEM -> 1
            else -> 0
        }
        if (potionSlotIndex > 0) {
            val potionCount = _state.value.player.potionCount
            if (potionCount < potionSlotIndex) {
                val message = "药水数量不足，无法使用（当前 $potionCount）"
                GameLogger.warn(logTag, message)
                _state.update { it.copy(lastAction = message, log = it.log + message) }
                return
            }
        }

        val action = when {
            choiceId == BATTLE_CHOICE_ATTACK -> PlayerBattleAction(PlayerBattleActionType.BASIC_ATTACK)
            skillId != null -> PlayerBattleAction(PlayerBattleActionType.SKILL, skillDefinitionMap[skillId])
            potionSlotIndex > 0 -> PlayerBattleAction(PlayerBattleActionType.ITEM)
            choiceId == BATTLE_CHOICE_EQUIP -> PlayerBattleAction(PlayerBattleActionType.EQUIP)
            choiceId == BATTLE_CHOICE_FLEE -> PlayerBattleAction(PlayerBattleActionType.FLEE)
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

        val potionPlayer = if (potionSlotIndex > 0) {
            val beforeCount = _state.value.player.potionCount
            val nextCount = (beforeCount - 1).coerceAtLeast(0)
            GameLogger.info(
                logTag,
                "消耗药水：槽位=$potionSlotIndex 之前=$beforeCount 之后=$nextCount"
            )
            newLogs = newLogs + "消耗${POTION_NAME} x1，剩余 $nextCount"
            _state.value.player.copy(potionCount = nextCount)
        } else {
            null
        }
        updateBattleState(sessionAfter, newLogs, potionPlayer)

        val outcome = step.outcome
        if (outcome != null) {
            finishBattle(event, outcome)
        }
    }

    private fun startBattleSession(event: EventDefinition) {
        val current = _state.value
        val group = enemyRepository.findGroup(event.enemyGroupId)
        val enemyDef = enemyRepository.findEnemy(group?.enemyId)
        val discovery = if (enemyDef != null) {
            recordEnemyDiscovery(current.player, enemyDef.id, enemyDef.name, "进入战斗")
        } else {
            DiscoveryUpdate(current.player)
        }
        if (discovery.logs.isNotEmpty()) {
            _state.update { state ->
                state.copy(
                    player = discovery.player,
                    log = state.log + discovery.logs
                )
            }
        }
        val context = battleSystem.buildBattleContext(discovery.player, event)
        if (context.player.hp <= 0) {
            GameLogger.warn(logTag, "战斗前生命为0，直接判定失败并进行结算：事件编号=${event.eventId}")
            val outcome = BattleOutcome(
                victory = false,
                escaped = false,
                rounds = 0,
                playerRemainingHp = context.player.hp,
                enemyRemainingHp = context.enemy.hp,
                logLines = listOf("生命为0，无法进入战斗，判定为失败")
            )
            finishBattle(event, outcome)
            return
        }
        val playerSkills = resolveBattleSkillDefinitions(current.player)
        val playerCooldowns = buildPlayerSkillCooldowns(playerSkills)
        GameLogger.info(
            logTag,
            "进入战斗技能准备：角色=${_state.value.selectedRoleId} 技能数量=${playerSkills.size} 槽位=${current.player.battleSkillSlots.joinToString()}"
        )
        val session = turnEngine.startSession(
            player = context.player,
            enemy = context.enemy,
            config = context.config,
            enemyDamageMultiplier = context.enemyDamageMultiplier,
            playerSkillCooldowns = playerCooldowns
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

    private fun updateBattleState(
        session: BattleSession,
        newLogs: List<String>,
        playerOverride: PlayerStats? = null
    ) {
        battleSession = session
        val basePlayer = playerOverride ?: _state.value.player
        val skillSummary = buildSkillCooldownSummary(session, basePlayer)
        val battleState = BattleUiState(
            round = session.round,
            playerHp = session.player.hp,
            playerMp = session.player.mp,
            enemyHp = session.enemy.hp,
            enemyMp = session.enemy.mp,
            enemyName = session.enemy.name,
            equipmentMode = equipmentModeLabel(session.equipmentMode),
            skillCooldownSummary = skillSummary,
            playerStatuses = session.player.statuses,
            enemyStatuses = session.enemy.statuses
        )
        val choices = buildBattleChoices(session, basePlayer)
        _state.update { current ->
            val syncedPlayer = syncBattlePlayerStats(playerOverride ?: current.player, session)
            current.copy(
                player = syncedPlayer,
                battle = battleState,
                enemyPreview = buildEnemyPreview(current.currentEvent, syncedPlayer),
                playerStatuses = session.player.statuses,
                choices = choices,
                log = current.log + newLogs
            )
        }
    }

    private fun updateBattleStateAfterEquipmentChange(
        session: BattleSession,
        updatedPlayer: PlayerStats,
        logLine: String,
        lastAction: String
    ) {
        val skillSummary = buildSkillCooldownSummary(session, updatedPlayer)
        val battleState = BattleUiState(
            round = session.round,
            playerHp = session.player.hp,
            playerMp = session.player.mp,
            enemyHp = session.enemy.hp,
            enemyMp = session.enemy.mp,
            enemyName = session.enemy.name,
            equipmentMode = equipmentModeLabel(session.equipmentMode),
            skillCooldownSummary = skillSummary,
            playerStatuses = session.player.statuses,
            enemyStatuses = session.enemy.statuses
        )
        val choices = buildBattleChoices(session, updatedPlayer)
        _state.update { current ->
            current.copy(
                player = updatedPlayer,
                battle = battleState,
                enemyPreview = buildEnemyPreview(current.currentEvent, updatedPlayer),
                playerStatuses = session.player.statuses,
                choices = choices,
                lastAction = lastAction,
                log = current.log + logLine
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

    private fun refreshBattleSessionForEquipmentChange(
        session: BattleSession,
        updatedPlayer: PlayerStats,
        reason: String
    ): BattleSession {
        val baseActor = updatedPlayer.toCombatActor()
        val baseStats = baseActor.stats
        val adjustedStats = applyEquipmentMode(baseStats, session.equipmentMode)
        val nextHp = session.player.hp.coerceIn(0, adjustedStats.hpMax)
        val nextMp = session.player.mp.coerceIn(0, updatedPlayer.mpMax)
        val refreshedPlayer = baseActor.copy(
            stats = adjustedStats,
            hp = nextHp,
            mp = nextMp,
            statuses = session.player.statuses
        )
        GameLogger.info(
            "装备系统",
            "战斗中更新装备属性：模式=${equipmentModeLabel(session.equipmentMode)} 生命${session.player.hp}/${session.player.stats.hpMax}->${refreshedPlayer.hp}/${refreshedPlayer.stats.hpMax} 攻击${session.player.stats.atk}->${refreshedPlayer.stats.atk} 防御${session.player.stats.def}->${refreshedPlayer.stats.def} 速度${session.player.stats.speed}->${refreshedPlayer.stats.speed} 原因=$reason"
        )
        return session.copy(player = refreshedPlayer, basePlayerStats = baseStats)
    }

    private fun applyEquipmentMode(baseStats: CombatStats, mode: EquipmentMode): CombatStats {
        return when (mode) {
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
    }

    private fun finishBattle(event: EventDefinition, outcome: BattleOutcome) {
        val defeated = outcome.playerRemainingHp <= 0
        val escaped = outcome.escaped && !defeated
        val victory = outcome.victory && !defeated && !escaped
        val outcomeText = when {
            escaped -> "成功撤离战斗"
            victory -> event.successText
            else -> event.failText
        }
        if (defeated) {
            GameLogger.warn(logTag, "战斗结算判定失败：生命为0")
        }
        val safeHp = if (!victory && !escaped && outcome.playerRemainingHp <= 0) {
            GameLogger.warn(logTag, "战斗失败后生命为0，按失败惩罚规则保留1点生命以继续推进")
            1
        } else {
            outcome.playerRemainingHp.coerceAtLeast(0)
        }
        val basePlayer = _state.value.player.copy(
            hp = safeHp,
            mp = battleSession?.player?.mp ?: _state.value.player.mp
        )
        val applied = if (victory) {
            applyEventResult(basePlayer, event.result, event, "战斗奖励")
        } else {
            ResultApplication(basePlayer, emptyList())
        }
        val rewardPlayer = applied.player
        val rewardSummaryLine = battleRewardSummary(basePlayer, rewardPlayer)
        logBattleRewardSummary(basePlayer, rewardPlayer, victory, "战斗结算")
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
                playerStatuses = emptyList(),
                lastAction = outcomeText.ifBlank { "战斗结束" },
                log = state.log + listOfNotNull(
                    outcomeText.ifBlank { null },
                    event.logText.ifBlank { null },
                    resultSummary
                ) + outcome.logLines + listOf(rewardSummaryLine) + applied.logs,
                pendingCardLevels = state.pendingCardLevels + applied.pendingCardLevels,
                pendingCardReasons = state.pendingCardReasons + applied.pendingCardReasons
            )
        }
        openNextCardDialogIfNeeded()

        if (!victory && !escaped) {
            val chapter = _state.value.selectedChapter.coerceIn(1, totalChapters)
            val difficulty = _state.value.selectedDifficulty.coerceIn(1, 5)
            val turn = chapterStartTurn(chapter)
            val dialogMessage = buildString {
                append("战斗失败，已返回章节起点。\n")
                append(resultSummary)
                append("\n")
                append(rewardSummaryLine)
            }
            GameLogger.warn(logTag, "战斗失败回退：章节=$chapter 难度=$difficulty 回合=$turn")
            startStageForSelection(turn, chapter, difficulty, reason = "战斗失败回退")
            _state.update { state ->
                state.copy(
                    screen = GameScreen.ADVENTURE,
                    showDialog = true,
                    dialogTitle = "战斗失败",
                    dialogMessage = dialogMessage,
                    lastAction = "战斗失败，已返回章节起点",
                    log = state.log + "战斗失败，已返回章节起点"
                )
            }
            return
        }

        if (escaped) {
            GameLogger.info(logTag, "玩家撤离战斗，返回关卡选择界面")
            _state.update { state ->
                state.copy(
                    screen = GameScreen.CHAPTER_SELECT,
                    lastAction = "已撤离战斗，返回关卡选择",
                    log = state.log + "已撤离战斗，返回关卡选择"
                )
            }
            return
        }

        if (victory && shouldTriggerShop(event)) {
            GameLogger.info(logTag, "精英/首领战后触发商店")
            openShopEventAfterBattle(event)
            return
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

    private fun buildBattleChoices(session: BattleSession, player: PlayerStats = _state.value.player): List<GameChoice> {
        val skills = resolveBattleSkillDefinitions(player)
        if (skills.isNotEmpty() && session.playerSkillCooldowns.isEmpty()) {
            GameLogger.warn(logTag, "战斗技能冷却为空，技能数量=${skills.size}")
        }
        val skillChoices = skills.map { skill ->
            val cooldownRemaining = session.playerSkillCooldowns[skill.id] ?: 0
            val mpEnough = session.player.mp >= skill.cost
            val cooldownLabel = if (cooldownRemaining > 0) "冷却$cooldownRemaining" else "就绪"
            val costLabel = if (skill.cost > 0) "能量${skill.cost}" else "无消耗"
            val label = "技能：${skill.name}（$costLabel / $cooldownLabel）"
            val enabled = cooldownRemaining <= 0 && mpEnough
            GameChoice(buildBattleSkillChoiceId(skill.id), label, enabled = enabled)
        }
        if (skillChoices.isNotEmpty()) {
            val enabledCount = skillChoices.count { it.enabled }
            GameLogger.info(logTag, "战斗技能选项生成：总数=${skillChoices.size} 可用=$enabledCount")
        }
        val equipLabel = "换装备（${equipmentModeLabel(session.equipmentMode)}）"
        val potionCount = player.potionCount.coerceAtLeast(0)
        val potionLabel1 = "药水1（剩余$potionCount）"
        val potionLabel2 = "药水2（剩余$potionCount）"
        val baseChoices = listOf(
            GameChoice(BATTLE_CHOICE_ATTACK, "普通攻击"),
            GameChoice(BATTLE_CHOICE_POTION_1, potionLabel1, enabled = potionCount >= 1),
            GameChoice(BATTLE_CHOICE_POTION_2, potionLabel2, enabled = potionCount >= 2),
            GameChoice(BATTLE_CHOICE_EQUIP, equipLabel),
            GameChoice(BATTLE_CHOICE_FLEE, "撤离战斗")
        )
        return baseChoices + skillChoices
    }

    private fun buildSkillCooldownSummary(session: BattleSession, player: PlayerStats = _state.value.player): String {
        val skills = resolveBattleSkillDefinitions(player)
        if (skills.isEmpty()) return "无技能"
        val cooldowns = session.playerSkillCooldowns
        return skills.joinToString(" / ") { skill ->
            val remaining = cooldowns[skill.id] ?: 0
            if (remaining > 0) "${skill.name}冷却$remaining" else "${skill.name}就绪"
        }
    }

    private fun roleSkillIds(roleId: String): List<String> {
        val character = characterDefinitions.firstOrNull { it.id == roleId }
        if (character == null) {
            GameLogger.warn(logTag, "未找到角色技能配置：角色编号=$roleId")
            return emptyList()
        }
        return buildList {
            addAll(character.activeSkillIds)
            if (character.ultimateSkillId.isNotBlank()) {
                add(character.ultimateSkillId)
            }
        }
    }

    private fun roleSkillDefinitions(roleId: String): List<SkillDefinition> {
        val skillIds = roleSkillIds(roleId)
        val skillList = mutableListOf<SkillDefinition>()
        skillIds.forEach { skillId ->
            val definition = skillDefinitionMap[skillId]
            if (definition == null) {
                GameLogger.warn(logTag, "技能缺失：角色=$roleId 技能编号=$skillId")
            } else {
                skillList += definition
            }
        }
        if (skillList.isEmpty()) {
            GameLogger.warn(logTag, "角色无可用技能：角色编号=$roleId")
        }
        return skillList.distinctBy { it.id }
    }

    private fun currentRoleSkillDefinitions(): List<SkillDefinition> {
        return roleSkillDefinitions(_state.value.selectedRoleId)
    }

    private fun resolveBattleSkillDefinitions(player: PlayerStats): List<SkillDefinition> {
        val roleId = _state.value.selectedRoleId
        val available = roleSkillDefinitions(roleId)
        if (available.isEmpty()) return emptyList()
        val normalizedSlots = normalizeBattleSkillSlots(
            slots = player.battleSkillSlots,
            roleId = roleId,
            reason = "战斗技能解析"
        )
        val availableMap = available.associateBy { it.id }
        val selected = normalizedSlots
            .filter { it.isNotBlank() }
            .mapNotNull { availableMap[it] }
        if (selected.isEmpty()) {
            GameLogger.info(logTag, "战斗技能槽为空，当前无可用技能")
        }
        return selected
    }

    private fun normalizeBattleSkillSlots(
        slots: List<String>,
        roleId: String,
        reason: String
    ): List<String> {
        val availableIds = roleSkillIds(roleId).toSet()
        if (availableIds.isEmpty()) {
            return List(maxBattleSkillSlots) { "" }
        }
        val baseSlots = if (slots.isEmpty()) {
            val defaults = roleSkillIds(roleId).distinct().take(maxBattleSkillSlots)
            defaults + List(maxBattleSkillSlots - defaults.size) { "" }
        } else {
            List(maxBattleSkillSlots) { index ->
                slots.getOrNull(index).orEmpty()
            }
        }
        val occupied = mutableSetOf<String>()
        val sanitized = baseSlots.map { skillId ->
            val trimmed = skillId.trim()
            if (trimmed.isBlank()) return@map ""
            if (!availableIds.contains(trimmed)) {
                return@map ""
            }
            if (occupied.contains(trimmed)) {
                return@map ""
            }
            occupied += trimmed
            trimmed
        }
        if (sanitized != baseSlots) {
            GameLogger.info(
                logTag,
                "战斗技能槽位已规范化：原因=$reason 原始=${baseSlots.joinToString()} 结果=${sanitized.joinToString()}"
            )
        }
        return sanitized
    }

    private fun buildPlayerSkillCooldowns(skills: List<SkillDefinition>): Map<String, Int> {
        if (skills.isEmpty()) return emptyMap()
        val cooldowns = skills.associate { it.id to 0 }
        GameLogger.info(logTag, "初始化玩家技能冷却映射：${cooldowns.keys.joinToString(",")}")
        return cooldowns
    }

    private fun parseBattleSkillChoiceId(choiceId: String): String? {
        return if (choiceId.startsWith(BATTLE_SKILL_CHOICE_PREFIX)) {
            choiceId.removePrefix(BATTLE_SKILL_CHOICE_PREFIX)
        } else {
            null
        }
    }

    private fun equipmentModeLabel(mode: EquipmentMode): String {
        return when (mode) {
            EquipmentMode.NORMAL -> "默认"
            EquipmentMode.OFFENSE -> "进攻"
            EquipmentMode.DEFENSE -> "防御"
        }
    }

    private fun codexTabLabel(tab: CodexTab): String {
        return when (tab) {
            CodexTab.SKILL -> "技能"
            CodexTab.EQUIPMENT -> "装备"
            CodexTab.MONSTER -> "怪物"
        }
    }

    private fun screenLabel(screen: GameScreen): String {
        return when (screen) {
            GameScreen.SAVE_SELECT -> "存档选择"
            GameScreen.ROLE_SELECT -> "角色选择"
            GameScreen.CHAPTER_SELECT -> "关卡选择"
            GameScreen.ROLE_DETAIL -> "角色详情"
            GameScreen.ADVENTURE -> "冒险"
        }
    }

    private fun resolveBattleAndAdvance(event: EventDefinition) {
        GameLogger.info(logTag, "进入战斗：事件编号=${event.eventId} 标题=${event.title}")
        val current = _state.value
        if (current.player.hp <= 0) {
            GameLogger.warn(logTag, "战斗前生命为0，直接判定失败并结算：事件编号=${event.eventId}")
        }
        val battle = battleSystem.resolveBattle(current.player, event)
        val defeated = battle.playerRemainingHp <= 0 || current.player.hp <= 0
        val victory = battle.victory && !defeated
        val outcomeText = if (victory) event.successText else event.failText
        val safeHp = if (victory) battle.playerRemainingHp else max(1, battle.playerRemainingHp)
        val postBattlePlayer = current.player.copy(hp = safeHp)
        val applied = if (victory) {
            applyEventResult(postBattlePlayer, event.result, event, "战斗奖励")
        } else {
            ResultApplication(postBattlePlayer, emptyList())
        }
        val rewardPlayer = applied.player
        val rewardSummaryLine = battleRewardSummary(postBattlePlayer, rewardPlayer)
        logBattleRewardSummary(postBattlePlayer, rewardPlayer, victory, "自动战斗结算")

        val resultSummary = if (victory) {
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
                ) + listOf(rewardSummaryLine) + applied.logs,
                pendingCardLevels = state.pendingCardLevels + applied.pendingCardLevels,
                pendingCardReasons = state.pendingCardReasons + applied.pendingCardReasons
            )
        }
        openNextCardDialogIfNeeded()

        if (victory && shouldTriggerShop(event)) {
            GameLogger.info(logTag, "自动战斗后触发商店")
            openShopEventAfterBattle(event)
            return
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

    private fun isBattleEvent(event: EventDefinition): Boolean {
        if (!event.enemyGroupId.isNullOrBlank()) return true
        val type = event.type.lowercase()
        return type.startsWith("battle") || event.type.contains("战斗")
    }

    private fun isShopEvent(event: EventDefinition): Boolean {
        val type = event.type.lowercase()
        return type.contains("shop") || event.type.contains("商店")
    }

    private fun shouldTriggerShop(event: EventDefinition): Boolean {
        val type = event.type.lowercase()
        return type.contains("elite") || type.contains("boss") || event.type.contains("精英") || event.type.contains("首领")
    }

    private data class ShopOffer(
        val offerId: String,
        val item: EquipmentItem,
        val price: Int,
        val stock: Int = 1,
        val lockedReason: String = ""
    )

    private fun buildForcedEvent(
        forcedEventId: String?,
        chapter: Int,
        difficulty: Int,
        turn: Int
    ): EventDefinition? {
        if (forcedEventId.isNullOrBlank()) return null
        if (forcedEventId.startsWith("auto_shop")) {
            return buildShopEvent(chapter, difficulty, turn, forcedEventId)
        }
        return engine.eventById(forcedEventId)
    }

    private fun buildEventChoices(
        event: EventDefinition?,
        chapter: Int,
        difficulty: Int,
        turn: Int,
        player: PlayerStats
    ): List<GameChoice> {
        if (event == null) {
            return listOf(GameChoice("继续", "继续"))
        }
        return if (isShopEvent(event)) {
            buildShopChoices(event, chapter, difficulty, turn, player)
        } else {
            engine.toChoices(event)
        }
    }

    private fun buildShopEvent(
        chapter: Int,
        difficulty: Int,
        turn: Int,
        eventId: String = "auto_shop_turn_$turn"
    ): EventDefinition {
        val title = "补给商店"
        val intro = "精英/首领战后出现商店，你可以使用金币购买装备。"
        return EventDefinition(
            eventId = eventId,
            chapter = chapter,
            title = title,
            type = "SHOP",
            difficulty = difficulty,
            weight = 1,
            cooldown = 0,
            introText = intro,
            successText = "你结束了商店采购。",
            failText = "你离开了商店。",
            logText = "商店结算完成。",
            conditions = emptyList(),
            options = emptyList(),
            result = null,
            enemyGroupId = null,
            roundLimit = null,
            firstStrike = null,
            battleModifiers = null,
            dropTableId = null,
            guarantee = 0,
            nextEventId = null,
            failEventId = null
        )
    }

    private fun buildShopChoices(
        event: EventDefinition,
        chapter: Int,
        difficulty: Int,
        turn: Int,
        player: PlayerStats
    ): List<GameChoice> {
        ensureShopOffers(event, chapter, difficulty, turn, player)
        val choices = mutableListOf<GameChoice>()
        if (shopOffers.isEmpty()) {
            choices += GameChoice("shop_leave", "商店暂无商品，离开")
            return choices
        }
        val totalPrice = shopOffers
            .filter { it.stock > 0 && it.lockedReason.isBlank() }
            .sumOf { it.price }
        choices += GameChoice("shop_buy_all", "一键购买可买装备（总价 $totalPrice 金币）")
        shopOffers.forEachIndexed { index, offer ->
            val stockLabel = if (offer.stock > 0) "库存 ${offer.stock}" else "已售罄"
            val reasonLabel = if (offer.lockedReason.isBlank()) "" else "（${offer.lockedReason}）"
            val label = "购买 ${offer.item.name}（${offer.item.rarityName}） 价格 ${offer.price} 金币 | $stockLabel$reasonLabel"
            val enabled = offer.stock > 0 && offer.lockedReason.isBlank()
            choices += GameChoice("shop_buy_$index", label, enabled = enabled)
        }
        choices += GameChoice("shop_leave", "离开商店")
        return choices
    }

    private fun ensureShopOffers(
        event: EventDefinition,
        chapter: Int,
        difficulty: Int,
        turn: Int,
        player: PlayerStats
    ) {
        if (shopEventId == event.eventId && shopOffers.isNotEmpty()) return
        shopEventId = event.eventId
        val exclude = ownedEquipmentTemplateIds(player)
        shopOffers = generateShopOffers(
            chapter = chapter,
            difficulty = difficulty,
            turn = turn,
            count = shopOfferCount,
            excludeTemplateIds = exclude
        )
        GameLogger.info(
            "商店系统",
            "商店商品生成：事件=${event.eventId} 章节=$chapter 难度=$difficulty 数量=${shopOffers.size}"
        )
    }

    private fun generateShopOffers(
        chapter: Int,
        difficulty: Int,
        turn: Int,
        count: Int,
        excludeTemplateIds: Set<String>
    ): List<ShopOffer> {
        val offers = mutableListOf<ShopOffer>()
        val blockedTemplates = excludeTemplateIds.toMutableSet()
        val tier = resolveShopTier(chapter, difficulty)
        repeat(count) {
            val item = generateShopEquipment(tier, turn, blockedTemplates)
            if (item != null) {
                val price = estimateShopPrice(item)
                val offer = ShopOffer(
                    offerId = item.uid,
                    item = item,
                    price = price,
                    stock = 1,
                    lockedReason = ""
                )
                offers += offer
                blockedTemplates += item.templateId
                GameLogger.info(
                    "商店系统",
                    "商品上架：${item.name}（${item.rarityName}） 价格=$price 金币 库存=${offer.stock}"
                )
            }
        }
        return offers
    }

    private fun resolveShopTier(chapter: Int, difficulty: Int): Int {
        val chapterTier = when {
            chapter >= 7 -> 3
            chapter >= 4 -> 2
            else -> 1
        }
        val difficultyBonus = when {
            difficulty >= 5 -> 2
            difficulty >= 4 -> 1
            else -> 0
        }
        val tier = (chapterTier + difficultyBonus).coerceIn(1, 3)
        GameLogger.info("商店系统", "商店层级计算：章节=$chapter 难度=$difficulty -> $tier")
        return tier
    }

    private fun generateShopEquipment(
        tier: Int,
        turn: Int,
        excludeTemplateIds: Set<String>
    ): EquipmentItem? {
        val pityCounters = mutableMapOf<String, Int>()
        repeat(20) { attempt ->
            val outcome = lootRepository.generateLoot(
                LootSourceType.EVENT,
                tier,
                rng,
                pityCounters
            )
            if (outcome.equipment != null) {
                val item = buildEquipmentItem(outcome.equipment, "shop_tier_$tier", turn)
                if (excludeTemplateIds.contains(item.templateId)) {
                    GameLogger.info("商店系统", "商店抽取到重复模板，跳过：${item.name} 模板=${item.templateId}")
                    return@repeat
                }
                return item
            }
            GameLogger.info("商店系统", "商店抽取未命中装备，继续尝试：轮次=${attempt + 1}")
        }
        GameLogger.warn("商店系统", "商店抽取装备失败，层级=$tier")
        return null
    }

    private fun estimateShopPrice(item: EquipmentItem): Int {
        val base = estimateEquipmentSellValue(item)
        val price = base * 4 + item.rarityTier * 8 + item.level * 2
        return price.coerceAtLeast(10)
    }

    private fun mapShopOffersToUi(): List<ShopOfferUiState> {
        return shopOffers.map { offer ->
            ShopOfferUiState(
                id = offer.offerId,
                item = offer.item,
                price = offer.price,
                stock = offer.stock,
                lockedReason = offer.lockedReason
            )
        }
    }

    private fun sanitizeShopOfferSelections(selectedOfferIds: Set<String>): Set<String> {
        val validIds = shopOffers
            .filter { it.stock > 0 && it.lockedReason.isBlank() }
            .map { it.offerId }
            .toSet()
        return selectedOfferIds.intersect(validIds)
    }

    private fun sanitizeShopSellSelections(
        selectedSellIds: Set<String>,
        player: PlayerStats
    ): Set<String> {
        val validIds = player.inventory.items.map { it.uid }.toSet()
        return selectedSellIds.intersect(validIds)
    }

    private fun computeShopBuyTotal(selectedOfferIds: Set<String>): Int {
        return shopOffers
            .filter { selectedOfferIds.contains(it.offerId) && it.stock > 0 && it.lockedReason.isBlank() }
            .sumOf { it.price }
    }

    private fun computeShopSellTotal(
        selectedSellIds: Set<String>,
        player: PlayerStats
    ): Int {
        val itemMap = player.inventory.items.associateBy { it.uid }
        var total = 0
        selectedSellIds.forEach { id ->
            val item = itemMap[id]
            if (item != null) {
                total += estimateEquipmentSellValue(item)
            }
        }
        return total
    }

    private fun applyShopUiState(
        state: GameUiState,
        player: PlayerStats,
        offerSelection: Set<String>,
        sellSelection: Set<String>
    ): GameUiState {
        val sanitizedOffers = sanitizeShopOfferSelections(offerSelection)
        val sanitizedSell = sanitizeShopSellSelections(sellSelection, player)
        return state.copy(
            shopOffers = mapShopOffersToUi(),
            shopSelectedOfferIds = sanitizedOffers,
            shopSelectedSellIds = sanitizedSell,
            shopBuyTotal = computeShopBuyTotal(sanitizedOffers),
            shopSellTotal = computeShopSellTotal(sanitizedSell, player)
        )
    }

    private fun clearShopUiState(state: GameUiState): GameUiState {
        return state.copy(
            shopOffers = emptyList(),
            shopSelectedOfferIds = emptySet(),
            shopSelectedSellIds = emptySet(),
            shopBuyTotal = 0,
            shopSellTotal = 0
        )
    }

    private fun handleShopChoice(choiceId: String, event: EventDefinition) {
        val current = _state.value
        if (!isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略选择")
            return
        }
        if (choiceId == "shop_leave") {
            onShopLeave()
            return
        }
        if (choiceId == "shop_buy_all") {
            handleShopBuyAll(event)
            return
        }
        if (!choiceId.startsWith("shop_buy_")) {
            GameLogger.warn("商店系统", "未知商店选项：$choiceId")
            return
        }
        val index = choiceId.removePrefix("shop_buy_").toIntOrNull()
        if (index == null || index !in shopOffers.indices) {
            GameLogger.warn("商店系统", "商店商品索引无效：$choiceId")
            return
        }
        val offer = shopOffers[index]
        if (offer.stock <= 0) {
            GameLogger.warn("商店系统", "商品已售罄，无法购买：${offer.item.name}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "商品已售罄",
                    log = updated.log + "商品已售罄，无法购买 ${offer.item.name}"
                )
            }
            return
        }
        if (offer.lockedReason.isNotBlank()) {
            GameLogger.warn("商店系统", "商品不可购买：${offer.item.name} 原因=${offer.lockedReason}")
            _state.update { state ->
                val updated = applyShopUiState(state, state.player, state.shopSelectedOfferIds, state.shopSelectedSellIds)
                updated.copy(
                    lastAction = "商品不可购买",
                    log = updated.log + "商品不可购买：${offer.item.name}（${offer.lockedReason}）"
                )
            }
            return
        }
        if (isDuplicateEquipment(current.player, offer.item)) {
            GameLogger.warn("商店系统", "已拥有同类装备，无法重复购买：${offer.item.name}")
            shopOffers = shopOffers.mapIndexed { i, existing ->
                if (i == index) {
                    existing.copy(lockedReason = "已拥有同类装备")
                } else {
                    existing
                }
            }
            val newChoices = buildShopChoices(
                event,
                current.chapter,
                current.selectedDifficulty,
                current.turn,
                current.player
            )
            _state.update { state ->
                val base = state.copy(
                    choices = newChoices,
                    lastAction = "已拥有同类装备，无法重复购买",
                    log = state.log + "已拥有同类装备，${offer.item.name} 已标记不可购买"
                )
                applyShopUiState(
                    base,
                    base.player,
                    state.shopSelectedOfferIds - offer.offerId,
                    state.shopSelectedSellIds
                )
            }
            return
        }
        if (current.player.inventory.items.size >= current.player.inventory.capacity) {
            GameLogger.warn("商店系统", "背包已满，无法购买：${offer.item.name}")
            _state.update { state ->
                state.copy(
                    lastAction = "背包已满，无法购买",
                    log = state.log + "背包已满，无法购买 ${offer.item.name}"
                )
            }
            return
        }
        if (current.player.gold < offer.price) {
            GameLogger.warn("商店系统", "金币不足，无法购买：${offer.item.name}")
            _state.update { state ->
                state.copy(
                    lastAction = "金币不足，无法购买",
                    log = state.log + "金币不足，无法购买 ${offer.item.name}"
                )
            }
            return
        }
        val paidPlayer = current.player.copy(gold = current.player.gold - offer.price)
        val added = addEquipmentToInventoryFromShop(paidPlayer, offer.item)
        shopOffers = shopOffers.mapIndexed { i, existing ->
            if (i == index) {
                existing.copy(stock = (existing.stock - 1).coerceAtLeast(0))
            } else {
                existing
            }
        }
        val newChoices = buildShopChoices(
            event,
            current.chapter,
            current.selectedDifficulty,
            current.turn,
            added.player
        )
        GameLogger.info(
            "商店系统",
            "购买完成：${offer.item.name} 价格=${offer.price} 剩余金币=${added.player.gold}"
        )
        _state.update { state ->
            val base = state.copy(
                player = added.player,
                choices = newChoices,
                lastAction = "已购买 ${offer.item.name}",
                log = state.log + "购买 ${offer.item.name}（${offer.item.rarityName}） -${offer.price} 金币" + added.logs
            )
            applyShopUiState(
                base,
                added.player,
                state.shopSelectedOfferIds - offer.offerId,
                state.shopSelectedSellIds
            )
        }
    }

    private fun handleShopBuyAll(event: EventDefinition) {
        val current = _state.value
        if (!isShopEvent(event)) {
            GameLogger.warn("商店系统", "当前事件不是商店，忽略批量购买")
            return
        }
        if (shopOffers.isEmpty()) {
            GameLogger.warn("商店系统", "商店暂无商品，批量购买取消")
            _state.update { state ->
                state.copy(
                    lastAction = "商店暂无商品",
                    log = state.log + "商店暂无商品，无法批量购买"
                )
            }
            return
        }
        var player = current.player
        val logs = mutableListOf<String>()
        val purchasedIds = mutableSetOf<String>()
        val lockedIds = mutableSetOf<String>()
        var spent = 0
        for (offer in shopOffers) {
            if (offer.stock <= 0 || offer.lockedReason.isNotBlank()) {
                continue
            }
            if (isDuplicateEquipment(player, offer.item)) {
                GameLogger.warn("商店系统", "批量购买跳过重复装备：${offer.item.name}")
                logs += "已有同类装备，${offer.item.name} 已跳过"
                lockedIds += offer.offerId
                continue
            }
            if (player.inventory.items.size >= player.inventory.capacity) {
                GameLogger.warn("商店系统", "批量购买中断：背包已满")
                logs += "背包已满，批量购买中断"
                break
            }
            if (player.gold < offer.price) {
                GameLogger.warn("商店系统", "批量购买跳过：金币不足 ${offer.item.name}")
                logs += "金币不足，跳过 ${offer.item.name}"
                continue
            }
            val paidPlayer = player.copy(gold = player.gold - offer.price)
            val added = addEquipmentToInventoryFromShop(paidPlayer, offer.item)
            player = added.player
            purchasedIds += offer.offerId
            spent += offer.price
            logs += "购买 ${offer.item.name}（${offer.item.rarityName}） -${offer.price} 金币"
            logs += added.logs
            GameLogger.info(
                "商店系统",
                "批量购买完成单件：${offer.item.name} 价格=${offer.price} 剩余金币=${player.gold}"
            )
        }
        if (purchasedIds.isEmpty()) {
            GameLogger.warn("商店系统", "批量购买未成交：金币不足或重复装备过多")
            logs += "批量购买未成交"
        }
        shopOffers = shopOffers.map { offer ->
            when {
                purchasedIds.contains(offer.offerId) ->
                    offer.copy(stock = (offer.stock - 1).coerceAtLeast(0))
                lockedIds.contains(offer.offerId) ->
                    offer.copy(lockedReason = "已拥有同类装备")
                else -> offer
            }
        }
        val newChoices = buildShopChoices(
            event,
            current.chapter,
            current.selectedDifficulty,
            current.turn,
            player
        )
        val summary = if (purchasedIds.isNotEmpty()) {
            "批量购买完成：${purchasedIds.size} 件，花费 $spent 金币"
        } else {
            "批量购买未成交"
        }
        GameLogger.info("商店系统", summary)
        logs += summary
        _state.update { state ->
            val base = state.copy(
                player = player,
                choices = newChoices,
                lastAction = summary,
                log = state.log + logs
            )
            applyShopUiState(base, player, emptySet(), state.shopSelectedSellIds)
        }
    }

    private fun openShopEventAfterBattle(event: EventDefinition) {
        val current = _state.value
        val shopEvent = buildShopEvent(current.chapter, current.selectedDifficulty, current.turn)
        val choices = buildShopChoices(
            shopEvent,
            current.chapter,
            current.selectedDifficulty,
            current.turn,
            current.player
        )
        GameLogger.info(
            "商店系统",
            "商店出现：章节=${current.chapter} 回合=${current.turn} 事件=${shopEvent.eventId}"
        )
        _state.update { state ->
            val base = state.copy(
                currentEvent = shopEvent,
                enemyPreview = null,
                battle = null,
                playerStatuses = emptyList(),
                choices = choices,
                lastAction = "商店出现",
                log = state.log + listOf("精英/首领战后出现商店", shopEvent.introText)
            )
            applyShopUiState(base, base.player, emptySet(), emptySet())
        }
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
        val levelBoost = computeMonsterLevelBoostForEvent(event)
        val previewLevel = (enemyDef.level + levelBoost).coerceAtLeast(1)
        val modifiers = event.battleModifiers ?: BattleModifiers()
        val hpMultiplier = modifiers.enemyHpMultiplier
        val damageMultiplier = modifiers.enemyDamageMultiplier
        val atkMultiplier = modifiers.enemyAtkMultiplier
        val defMultiplier = modifiers.enemyDefMultiplier
        val spdMultiplier = modifiers.enemySpdMultiplier
        val groupHpMultiplier = if (count <= 1) 1.0 else 1.0 + 0.35 * (count - 1)
        val groupAtkMultiplier = if (count <= 1) 1.0 else 1.0 + 0.2 * (count - 1)
        val groupDefMultiplier = if (count <= 1) 1.0 else 1.0 + 0.15 * (count - 1)
        val groupSpdMultiplier = if (count <= 1) 1.0 else 1.0 + 0.05 * (count - 1)

        val scaledHp = (enemyDef.stats.hp * groupHpMultiplier * hpMultiplier).toInt().coerceAtLeast(1)
        val scaledAtk = (enemyDef.stats.atk * groupAtkMultiplier * atkMultiplier).toInt().coerceAtLeast(1)
        val scaledDef = (enemyDef.stats.def * groupDefMultiplier * defMultiplier).toInt().coerceAtLeast(1)
        val scaledSpd = (enemyDef.stats.spd * groupSpdMultiplier * spdMultiplier).toInt().coerceAtLeast(1)
        val (enemyStr, enemyInt, enemyAgi) = resolveEnemyAttributes(enemyDef.stats)
        val hpFromStr = enemyStr * 2
        val atkFromStr = enemyStr / 2
        val defFromAgi = enemyAgi / 2
        val spdFromAgi = enemyAgi / 3
        val hitFromInt = enemyInt / 3
        val critFromAgi = enemyAgi / 3
        val evaFromAgi = enemyAgi / 4
        val previewHp = (scaledHp + hpFromStr).coerceAtLeast(1)
        val previewAtk = (scaledAtk + atkFromStr).coerceAtLeast(1)
        val previewDef = (scaledDef + defFromAgi).coerceAtLeast(0)
        val previewSpd = (scaledSpd + spdFromAgi).coerceAtLeast(1)
        val previewHit = (enemyDef.stats.hit + hitFromInt).coerceIn(50, 98)
        val previewEva = (enemyDef.stats.eva + evaFromAgi).coerceIn(5, 45)
        val previewCrit = (enemyDef.stats.crit + critFromAgi).coerceIn(5, 40)

        val playerScore = player.hp + player.atk * 2.0 + player.def * 1.5 + player.speed * 0.8
        val effectiveAtk = previewAtk * damageMultiplier
        val enemyScore = previewHp + effectiveAtk * 2.0 + previewDef * 1.5 + previewSpd * 0.8
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
            "生成敌人预览：敌人=${enemyDef.name} 等级=$previewLevel 数量=$count 生命=$previewHp 攻击=$previewAtk 防御=$previewDef 速度=$previewSpd 力量=$enemyStr 智力=$enemyInt 敏捷=$enemyAgi 评估=$threat 胜率=${winRate}% 掉落表=$dropTableId"
        )

        return EnemyPreviewUiState(
            name = enemyDef.name,
            type = enemyTypeLabel(enemyDef.type),
            level = previewLevel,
            count = count,
            hp = previewHp,
            atk = previewAtk,
            def = previewDef,
            speed = previewSpd,
            strength = enemyStr,
            intelligence = enemyInt,
            agility = enemyAgi,
            hit = previewHit,
            eva = previewEva,
            crit = previewCrit,
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

    private fun resolveEnemyAttributes(stats: EnemyStats): Triple<Int, Int, Int> {
        val strength = if (stats.strength > 0) stats.strength else ((stats.atk + stats.def) / 4).coerceAtLeast(1)
        val agility = if (stats.agility > 0) stats.agility else (stats.spd / 2).coerceAtLeast(1)
        val intelligence = if (stats.intelligence > 0) stats.intelligence else (stats.hit / 20).coerceAtLeast(1)
        if (stats.strength == 0 || stats.intelligence == 0 || stats.agility == 0) {
            GameLogger.info(
                logTag,
                "敌人三围补全：力量$strength 智力$intelligence 敏捷$agility（原始 力量${stats.strength} 智力${stats.intelligence} 敏捷${stats.agility}）"
            )
        }
        return Triple(strength, intelligence, agility)
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
            selectedDifficulty = snapshot.selectedDifficulty,
            completedChapters = snapshot.completedChapters,
            currentEventId = snapshot.currentEvent?.eventId,
            stageId = runtime?.stage?.id,
            nodeId = runtime?.currentNodeId,
            visitedNodes = runtime?.visited?.toList() ?: emptyList(),
            stageCompleted = runtime?.completed ?: false,
            guardianGroupId = runtime?.guardianGroupId,
            showCardDialog = snapshot.showCardDialog,
            cardOptions = snapshot.cardOptions,
            cardDialogLevel = snapshot.cardDialogLevel,
            cardDialogReason = snapshot.cardDialogReason,
            pendingCardLevels = snapshot.pendingCardLevels,
            pendingCardReasons = snapshot.pendingCardReasons
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

    private fun buildEquipmentCatalog(): List<EquipmentCatalogEntry> {
        val definitions = lootRepository.allEquipmentDefinitions()
        if (definitions.isEmpty()) {
            GameLogger.warn("装备图鉴", "装备配置为空，图鉴列表为空")
            return emptyList()
        }
        val entries = definitions.map { definition ->
            val rarity = lootRepository.rarityDefinition(definition.rarity)
            EquipmentCatalogEntry(
                id = definition.id,
                name = definition.name,
                slot = definition.slot,
                rarityId = definition.rarity,
                rarityName = rarity?.name ?: definition.rarity,
                rarityTier = rarity?.tier ?: 1,
                levelReq = definition.levelReq,
                baseStats = definition.baseStats,
                affixMin = definition.affixCount.min,
                affixMax = definition.affixCount.max,
                enhanceMax = definition.enhanceMax,
                sellValue = definition.sellValue,
                salvageYield = definition.salvageYield
            )
        }.sortedWith(
            compareBy<EquipmentCatalogEntry> { it.slot.ordinal }
                .thenBy { it.rarityTier }
                .thenBy { it.levelReq }
                .thenBy { it.name }
        )
        GameLogger.info("装备图鉴", "装备图鉴加载完成：数量=${entries.size}")
        return entries
    }

    private fun buildSkillCatalog(): List<SkillCatalogEntry> {
        if (skillDefinitions.isEmpty()) {
            GameLogger.warn("技能图鉴", "技能配置为空，图鉴列表为空")
            return emptyList()
        }
        val roleMap = characterDefinitions.associateBy { it.id }
        val skillToRoleIds = mutableMapOf<String, MutableList<String>>()
        characterDefinitions.forEach { role ->
            val allSkillIds = buildList {
                if (role.passiveSkillId.isNotBlank()) add(role.passiveSkillId)
                addAll(role.activeSkillIds)
                if (role.ultimateSkillId.isNotBlank()) add(role.ultimateSkillId)
            }
            allSkillIds.forEach { skillId ->
                val list = skillToRoleIds.getOrPut(skillId) { mutableListOf() }
                list += role.id
            }
        }
        val entries = skillDefinitions.map { skill ->
            val roleIds = skillToRoleIds[skill.id].orEmpty()
            val roleNames = roleIds.mapNotNull { roleMap[it]?.name }
            SkillCatalogEntry(
                id = skill.id,
                name = skill.name,
                type = skill.type,
                target = skill.target,
                cost = skill.cost,
                cooldown = skill.cooldown,
                description = skill.desc,
                effects = skill.effects,
                sourceRoleIds = roleIds,
                sourceRoleNames = roleNames
            )
        }.sortedWith(compareBy<SkillCatalogEntry> { it.type }.thenBy { it.name })
        GameLogger.info("技能图鉴", "技能图鉴加载完成：数量=${entries.size}")
        return entries
    }

    private fun buildMonsterCatalog(): List<MonsterCatalogEntry> {
        val enemyFile = runCatching { repository.loadEnemies() }.getOrElse { defaultEnemyFile() }
        val groupFile = runCatching { repository.loadEnemyGroups() }.getOrElse { defaultEnemyGroupFile() }
        if (enemyFile.enemies.isEmpty()) {
            GameLogger.warn("怪物图鉴", "怪物配置为空，图鉴列表为空")
            return emptyList()
        }
        val entries = enemyFile.enemies.map { enemy ->
            val appearances = groupFile.groups
                .filter { it.enemyId == enemy.id }
                .mapNotNull { it.note.ifBlank { null } }
            MonsterCatalogEntry(
                id = enemy.id,
                name = enemy.name,
                type = enemy.type,
                level = enemy.level,
                stats = enemy.stats,
                skills = enemy.skills,
                notes = enemy.notes,
                appearances = appearances,
                dropHint = "掉落随章节与难度浮动"
            )
        }.sortedWith(compareBy<MonsterCatalogEntry> { it.type }.thenBy { it.level }.thenBy { it.name })
        GameLogger.info("怪物图鉴", "怪物图鉴加载完成：数量=${entries.size}")
        return entries
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
        if (monsterOnlyMode) {
            return buildMonsterOnlyEvent(runtime, node, chapter, isExitNode)
        }
        return if (isExitNode && runtime.guardianGroupId != null) {
            buildGuardianEvent(runtime, chapter)
        } else {
            stageEngine.eventForNode(runtime, chapter, rng)
        }
    }

    private fun buildMonsterOnlyEvent(
        runtime: StageRuntime,
        node: NodeDefinition,
        chapter: Int,
        isExitNode: Boolean
    ): EventDefinition {
        if (isExitNode && runtime.guardianGroupId != null) {
            GameLogger.info(
                logTag,
                "纯怪物模式：出口节点使用守卫战：关卡=${runtime.stage.id} 节点=${node.id} 守卫=${runtime.guardianGroupId}"
            )
            return buildGuardianEvent(runtime, chapter)
        }
        val difficulty = _state.value.selectedDifficulty.coerceIn(1, 5)
        val nodeIndex = parseNodeIndex(node.id)
        val eliteNodes = setOf(3, 6, 9)
        val isElite = nodeIndex != null && eliteNodes.contains(nodeIndex)
        val isBoss = nodeIndex == 10 || isExitNode
        val groupId = pickMonsterGroupId(runtime, node, chapter, nodeIndex)
        val enemyName = guardianNameForGroup(groupId)
        val progress = stageProgressRatio(runtime, nodeIndex)
        val levelBoost = computeMonsterLevelBoost(chapter, difficulty, progress, isElite, isBoss)
        val rewardMultiplier = computeMonsterRewardMultiplier(chapter, difficulty, progress, isElite, isBoss)
        val baseGold = 5 + chapter * 3 + difficulty * 2
        val baseExp = 5 + chapter * 4 + difficulty * 2
        val rewardGold = (baseGold * rewardMultiplier).toInt().coerceAtLeast(1)
        val rewardExp = (baseExp * rewardMultiplier).toInt().coerceAtLeast(1)
        val dropTableId = dropTableIdForMonster(difficulty, chapter, progress, isElite, isBoss)
        val battleModifiers = battleModifiersForDifficulty(difficulty, chapter, progress, isElite, isBoss)
        val typeLabel = when {
            isBoss -> "battle_boss"
            isElite -> "battle_elite"
            else -> "battle_normal"
        }
        val titlePrefix = when {
            isBoss -> "首领战"
            isElite -> "精英战"
            else -> "遭遇战"
        }
        GameLogger.info(
            logTag,
            "纯怪物模式：生成战斗 关卡=${runtime.stage.id} 节点=${node.id} 敌群=$groupId 敌人=$enemyName 章节=$chapter 难度=$difficulty 进度=${"%.2f".format(progress)} 精英=$isElite 首领=$isBoss 等级提升=$levelBoost 奖励倍率=${"%.2f".format(rewardMultiplier)} 掉落表=$dropTableId"
        )
        return EventDefinition(
            eventId = "battle_only_${runtime.stage.id}_${node.id}_$groupId",
            chapter = chapter,
            title = "$titlePrefix · $enemyName",
            type = typeLabel,
            difficulty = difficulty,
            weight = 1,
            cooldown = 0,
            introText = "你在 ${node.id} 遇到了 $enemyName，战斗在所难免。",
            successText = "敌人被击退，你可以继续前进。",
            failText = "你被逼退，但仍可继续探索。",
            logText = "战斗结束：$enemyName",
            result = EventResult(
                hpDelta = 0,
                mpDelta = 0,
                goldDelta = rewardGold,
                expDelta = rewardExp,
                dropTableId = dropTableId
            ),
            enemyGroupId = groupId,
            battleModifiers = battleModifiers,
            firstStrike = "speed"
        )
    }

    private fun pickMonsterGroupId(
        runtime: StageRuntime,
        node: NodeDefinition,
        chapter: Int,
        nodeIndex: Int?
    ): String {
        val allGroups = enemyRepository.allGroups()
        if (allGroups.isEmpty()) {
            GameLogger.warn(logTag, "敌群配置为空，改用默认敌群")
            return "eg_default"
        }
        val preferred = nodeIndex?.let { index ->
            val id = "eg_${chapter}_$index"
            allGroups.firstOrNull { it.id == id }
        }
        if (preferred != null) {
            GameLogger.info(
                logTag,
                "纯怪物模式：节点直取敌群 关卡=${runtime.stage.id} 节点=${node.id} 章节=$chapter 选中=${preferred.id}"
            )
            return preferred.id
        }
        val chapterKey = "${chapter}_"
        val chapterGroups = allGroups.filter { it.id.startsWith("eg_${chapterKey}") }
        val candidates = if (chapterGroups.isEmpty()) allGroups else chapterGroups
        val selected = candidates[rng.nextInt(candidates.size)]
        GameLogger.info(
            logTag,
            "纯怪物模式：抽取敌群 关卡=${runtime.stage.id} 节点=${node.id} 章节=$chapter 候选=${candidates.size} 选中=${selected.id}"
        )
        return selected.id
    }

    private fun parseNodeIndex(nodeId: String): Int? {
        val parts = nodeId.split("-")
        if (parts.size != 2) return null
        val index = parts[1].toIntOrNull()
        if (index == null) {
            GameLogger.warn(logTag, "节点编号解析失败：$nodeId")
        }
        return index
    }

    private fun stageProgressRatio(runtime: StageRuntime, nodeIndex: Int?): Double {
        if (nodeIndex == null) return 0.0
        val total = runtime.stage.nodes.size.coerceAtLeast(1)
        if (total <= 1) return 0.0
        val normalized = (nodeIndex - 1).coerceAtLeast(0).toDouble() / (total - 1).toDouble()
        return normalized.coerceIn(0.0, 1.0)
    }

    private fun computeMonsterLevelBoost(
        chapter: Int,
        difficulty: Int,
        progress: Double,
        isElite: Boolean,
        isBoss: Boolean
    ): Int {
        val chapterBoost = (chapter - 1).coerceAtLeast(0) / 2
        val difficultyBoost = (difficulty - 1).coerceAtLeast(0)
        val progressBoost = (progress * 2.0).toInt()
        val eliteBoost = if (isElite) 1 else 0
        val bossBoost = if (isBoss) 2 else 0
        val total = chapterBoost + difficultyBoost + progressBoost + eliteBoost + bossBoost
        GameLogger.info(
            logTag,
            "怪物等级提升：章节=$chapter 难度=$difficulty 进度=${"%.2f".format(progress)} 精英=$isElite 首领=$isBoss 等级提升=$total"
        )
        return total
    }

    private fun computeMonsterLevelBoostForEvent(event: EventDefinition): Int {
        val isBoss = event.type.contains("boss", ignoreCase = true)
        val isElite = event.type.contains("elite", ignoreCase = true)
        val progress = when {
            isBoss -> 1.0
            isElite -> 0.6
            else -> 0.3
        }
        val chapter = event.chapter.coerceAtLeast(1)
        val difficulty = event.difficulty.coerceIn(1, 5)
        val boost = computeMonsterLevelBoost(chapter, difficulty, progress, isElite, isBoss)
        GameLogger.info(
            logTag,
            "敌人预览等级提升：章节=$chapter 难度=$difficulty 类型=${event.type} 推算进度=${"%.2f".format(progress)} 等级提升=$boost"
        )
        return boost
    }

    private fun computeMonsterRewardMultiplier(
        chapter: Int,
        difficulty: Int,
        progress: Double,
        isElite: Boolean,
        isBoss: Boolean
    ): Double {
        val chapterBonus = (chapter - 1).coerceAtLeast(0) * 0.05
        val difficultyBonus = (difficulty - 1).coerceAtLeast(0) * 0.06
        val progressBonus = progress * 0.25
        val eliteBonus = if (isElite) 0.25 else 0.0
        val bossBonus = if (isBoss) 0.55 else 0.0
        val multiplier = (1.0 + chapterBonus + difficultyBonus + progressBonus + eliteBonus + bossBonus).coerceAtMost(3.0)
        GameLogger.info(
            logTag,
            "怪物奖励倍率：章节=$chapter 难度=$difficulty 进度=${"%.2f".format(progress)} 精英=$isElite 首领=$isBoss 倍率=${"%.2f".format(multiplier)}"
        )
        return multiplier
    }

    private fun battleModifiersForDifficulty(
        difficulty: Int,
        chapter: Int,
        progress: Double,
        isElite: Boolean,
        isBoss: Boolean
    ): BattleModifiers {
        val baseHp = 1.0 + (difficulty - 1) * 0.08
        val baseAtk = 1.0 + (difficulty - 1) * 0.07
        val baseDef = 1.0 + (difficulty - 1) * 0.06
        val baseSpd = 1.0 + (difficulty - 1) * 0.03
        val chapterBonus = (chapter - 1).coerceAtLeast(0) * 0.03
        val progressBonus = progress * 0.08
        val eliteBonus = if (isElite) 0.12 else 0.0
        val bossBonus = if (isBoss) 0.22 else 0.0
        val hpMultiplier = baseHp + chapterBonus + progressBonus + eliteBonus + bossBonus
        val atkMultiplier = baseAtk + chapterBonus + progressBonus + eliteBonus + bossBonus
        val defMultiplier = baseDef + chapterBonus * 0.8 + progressBonus * 0.6 + eliteBonus * 0.7 + bossBonus * 0.8
        val spdMultiplier = baseSpd + chapterBonus * 0.4 + progressBonus * 0.4 + eliteBonus * 0.4 + bossBonus * 0.5
        val damageMultiplier = 1.0 + (difficulty - 1) * 0.05 + chapterBonus * 0.6 + progressBonus * 0.5 + if (isBoss) 0.08 else 0.0
        GameLogger.info(
            logTag,
            "难度倍率：章节=$chapter 难度=$difficulty 进度=${"%.2f".format(progress)} 精英=$isElite 首领=$isBoss 生命=$hpMultiplier 攻击=$atkMultiplier 防御=$defMultiplier 速度=$spdMultiplier 伤害=$damageMultiplier"
        )
        return BattleModifiers(
            enemyHpMultiplier = hpMultiplier,
            enemyDamageMultiplier = damageMultiplier,
            enemyAtkMultiplier = atkMultiplier,
            enemyDefMultiplier = defMultiplier,
            enemySpdMultiplier = spdMultiplier
        )
    }

    private fun dropTableIdForMonster(
        difficulty: Int,
        chapter: Int,
        progress: Double,
        isElite: Boolean,
        isBoss: Boolean
    ): String {
        val baseTier = when {
            difficulty >= 5 -> 3
            difficulty >= 3 -> 2
            else -> 1
        }
        val chapterBonus = when {
            chapter >= 6 -> 1
            chapter >= 4 -> 1
            else -> 0
        }
        val progressBonus = if (progress >= 0.6) 1 else 0
        val tagBonus = when {
            isBoss -> 1
            isElite -> 1
            else -> 0
        }
        val tier = (baseTier + chapterBonus + progressBonus + tagBonus).coerceIn(1, 3)
        GameLogger.info(
            logTag,
            "掉落层级：章节=$chapter 难度=$difficulty 进度=${"%.2f".format(progress)} 精英=$isElite 首领=$isBoss -> 层级=$tier"
        )
        return "enemy_tier_$tier"
    }

    private fun buildGuardianEvent(runtime: StageRuntime, chapter: Int): EventDefinition {
        val groupId = runtime.guardianGroupId ?: "eg_default"
        val guardianName = guardianNameForGroup(groupId)
        val difficulty = runtime.stage.difficulty.coerceIn(1, 5)
        val progress = 1.0
        val rewardMultiplier = computeMonsterRewardMultiplier(chapter, difficulty, progress, isElite = false, isBoss = true)
        val baseGold = 6 + chapter * 4 + difficulty * 2
        val baseExp = 6 + chapter * 5 + difficulty * 2
        val rewardGold = (baseGold * rewardMultiplier).toInt().coerceAtLeast(1)
        val rewardExp = (baseExp * rewardMultiplier).toInt().coerceAtLeast(1)
        val dropTableId = dropTableIdForMonster(difficulty, chapter, progress, isElite = false, isBoss = true)
        val battleModifiers = battleModifiersForDifficulty(difficulty, chapter, progress, isElite = false, isBoss = true)
        GameLogger.info(
            logTag,
            "守卫战奖励：章节=$chapter 难度=$difficulty 倍率=${"%.2f".format(rewardMultiplier)} 金币=$rewardGold 经验=$rewardExp 掉落表=$dropTableId"
        )
        return EventDefinition(
            eventId = "stage_guardian_${runtime.stage.id}_$groupId",
            chapter = chapter,
            title = "${runtime.stage.name}守卫 · $guardianName",
            type = "battle_guardian",
            difficulty = difficulty,
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
                expDelta = rewardExp,
                dropTableId = dropTableId
            ),
            enemyGroupId = groupId,
            battleModifiers = battleModifiers,
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
                        detail = "角色 ${saveGame.player.name} | 章节 ${saveGame.chapter} | 难度 ${saveGame.selectedDifficulty} | 关卡 $stageName",
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
