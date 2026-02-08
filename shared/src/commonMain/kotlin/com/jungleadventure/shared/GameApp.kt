package com.jungleadventure.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import com.jungleadventure.shared.loot.EquipmentSlot
import com.jungleadventure.shared.loot.StatType
import kotlin.math.roundToInt

private val LocalResourceReader = staticCompositionLocalOf<ResourceReader> {
    error("未提供ResourceReader")
}

private const val BattleSkillSlotCount = 5
private const val BattlePotionSlotCount = 2
private const val InventoryGridColumns = 6
private const val InventoryGridRows = 9
private const val InventoryGridSize = InventoryGridColumns * InventoryGridRows
private val BattleBaseTileWidth = 112.dp
private val BattleBaseTileHeight = 56.dp
private val BattleSkillTileWidth = 124.dp
private val BattleSkillTileHeight = 64.dp
private val BattleUtilityTileWidth = 120.dp
private val BattleUtilityTileHeight = 44.dp

@Composable
fun GameApp(
    resourceReader: ResourceReader,
    saveStore: SaveStore = defaultSaveStore(),
    viewModel: GameViewModel = remember {
        GameViewModel(resourceReader, saveStore)
    }
) {
    val state by viewModel.state.collectAsState()
    CompositionLocalProvider(LocalResourceReader provides resourceReader) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1A14))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeaderBar(
                    state = state,
                    onToggleRoleDetail = viewModel::onToggleRoleDetail,
                    onToggleSettings = viewModel::onToggleSettings
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainPanel(
                        modifier = Modifier.weight(1.2f),
                        state = state,
                        onChoice = viewModel::onSelectChoice,
                        onAdvance = viewModel::onAdvance,
                        onSelectRole = viewModel::onSelectRole,
                        onConfirmRole = viewModel::onConfirmRole,
                        onSelectChapter = viewModel::onSelectChapter,
                        onSelectDifficulty = viewModel::onSelectDifficulty,
                        onConfirmChapterSelection = viewModel::onConfirmChapterSelection,
                        onCreateNewSave = viewModel::onCreateNewSave,
                        onLoadSave = viewModel::onLoad,
                        onToggleShopOfferSelection = viewModel::onToggleShopOfferSelection,
                        onToggleShopSellSelection = viewModel::onToggleShopSellSelection,
                        onShopBuySelected = viewModel::onShopBuySelected,
                        onShopBuyPotion = viewModel::onShopBuyPotion,
                        onShopSellSelected = viewModel::onShopSellSelected,
                        onShopLeave = viewModel::onShopLeave,
                        onSelectCodexTab = viewModel::onSelectCodexTab,
                        onReturnToMain = viewModel::onReturnToMain,
                        onOpenChapterSelect = viewModel::onOpenChapterSelect,
                        onAssignBattleSkill = viewModel::onAssignBattleSkill,
                        onClearBattleSkill = viewModel::onClearBattleSkill,
                        showSkillFormula = state.showSkillFormula,
                        onToggleShowSkillFormula = viewModel::onToggleShowSkillFormula
                    )
                    SidePanel(
                        modifier = Modifier.weight(0.8f),
                        state = state,
                        onEquipItem = viewModel::onEquipItem
                    )
                }
            }
        }
        if (state.showCardDialog) {
            CardSelectDialog(
                level = state.cardDialogLevel,
                options = state.cardOptions,
                onSelect = viewModel::onSelectCardOption
            )
        } else if (state.showDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissDialog,
                title = { Text(state.dialogTitle.ifBlank { "提示" }) },
                text = { Text(state.dialogMessage) },
                confirmButton = {
                    Button(onClick = viewModel::onDismissDialog) {
                        Text("知道了")
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderBar(
    state: GameUiState,
    onToggleRoleDetail: () -> Unit,
    onToggleSettings: () -> Unit
) {
    val selectedRole = state.roles.firstOrNull { it.id == state.selectedRoleId && it.unlocked }
    val canOpenRoleDetail = selectedRole != null &&
        state.screen != GameScreen.SAVE_SELECT &&
        state.screen != GameScreen.SETTINGS
    val canOpenSettings = state.screen == GameScreen.ADVENTURE ||
        state.screen == GameScreen.ROLE_DETAIL ||
        state.screen == GameScreen.SETTINGS
    val roleIconLabel = if (state.screen == GameScreen.ROLE_DETAIL) "返" else "角"
    val settingsSelected = state.screen == GameScreen.SETTINGS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            val titleStageProgress = if (state.stage.total > 0) {
                "${state.stage.visited}/${state.stage.total}"
            } else {
                "--"
            }
            val titleChapterLabel = "第 ${state.chapter} 章"
            val titleProgressLabel = "关卡进度 $titleStageProgress"
            val titleText = "${state.title} · $titleChapterLabel · $titleProgressLabel"
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFFECE8D9),
                fontWeight = FontWeight.Bold
            )
            val stageLabel = if (state.stage.id.isNotBlank()) {
                " | 关卡 ${state.stage.name} ${state.stage.visited}/${state.stage.total}"
            } else {
                ""
            }
            Text(
                text = "回合 ${state.turn}  |  章节 ${state.chapter}$stageLabel",
                color = Color(0xFFB8B2A6)
            )
            if (state.stage.command.isNotBlank()) {
                Text(
                    text = "关卡口令：${state.stage.command}",
                    color = Color(0xFF8DB38B)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.lastAction.ifBlank { "准备行动" },
                color = Color(0xFF8DB38B)
            )
            HeaderIconButton(
                label = roleIconLabel,
                enabled = canOpenRoleDetail,
                selected = state.screen == GameScreen.ROLE_DETAIL,
                onClick = {
                    GameLogger.info(
                        "HeaderBar",
                        "点击角色详情图标：screen=${state.screen} roleId=${state.selectedRoleId}"
                    )
                    onToggleRoleDetail()
                }
            )
            HeaderIconButton(
                label = "设",
                enabled = canOpenSettings,
                selected = settingsSelected,
                onClick = onToggleSettings
            )
        }
    }
}

@Composable
private fun MainPanel(
    modifier: Modifier,
    state: GameUiState,
    onChoice: (String) -> Unit,
    onAdvance: () -> Unit,
    onSelectRole: (String) -> Unit,
    onConfirmRole: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectDifficulty: (Int) -> Unit,
    onConfirmChapterSelection: () -> Unit,
    onCreateNewSave: (Int) -> Unit,
    onLoadSave: (Int) -> Unit,
    onToggleShopOfferSelection: (String) -> Unit,
    onToggleShopSellSelection: (String) -> Unit,
    onShopBuySelected: () -> Unit,
    onShopBuyPotion: () -> Unit,
    onShopSellSelected: () -> Unit,
    onShopLeave: () -> Unit,
    onSelectCodexTab: (CodexTab) -> Unit,
    onReturnToMain: () -> Unit,
    onOpenChapterSelect: () -> Unit,
    onAssignBattleSkill: (Int, String) -> Unit,
    onClearBattleSkill: (Int) -> Unit,
    showSkillFormula: Boolean,
    onToggleShowSkillFormula: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.activePanel) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state.screen) {
            GameScreen.SAVE_SELECT -> {
                SaveSelectPanel(
                    state = state,
                    onCreateNewSave = onCreateNewSave,
                    onLoadSave = onLoadSave
                )
            }
            GameScreen.ROLE_SELECT -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "选择角色", fontWeight = FontWeight.Bold)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        RoleSelectionPanel(
                            state = state,
                            onSelectRole = onSelectRole,
                            showSkillFormula = showSkillFormula,
                            onToggleShowSkillFormula = onToggleShowSkillFormula
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onConfirmRole,
                            enabled = state.selectedRoleId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("确认角色并开始")
                        }
                    }
                }
            }
            GameScreen.CHAPTER_SELECT -> {
                ChapterSelectPanel(
                    state = state,
                    onSelectChapter = onSelectChapter,
                    onSelectDifficulty = onSelectDifficulty,
                    onConfirmChapterSelection = onConfirmChapterSelection
                )
            }
            GameScreen.ROLE_DETAIL -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "角色详情", fontWeight = FontWeight.Bold)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        RoleDetailPanel(
                            state = state,
                            showSkillFormula = showSkillFormula,
                            onAssignBattleSkill = onAssignBattleSkill,
                            onClearBattleSkill = onClearBattleSkill
                        )
                    }
                }
            }
            GameScreen.SETTINGS -> {
                SettingsPanelCard(
                    showSkillFormula = showSkillFormula,
                    onToggleShowSkillFormula = onToggleShowSkillFormula,
                    codexState = state,
                    onSelectCodexTab = onSelectCodexTab,
                    onReturnToMain = onReturnToMain,
                    onOpenChapterSelect = onOpenChapterSelect
                )
            }
            GameScreen.ADVENTURE -> {
                if (state.battle != null) {
                    BattleInfoPanel(
                        player = state.player,
                        battle = state.battle,
                        enemyPreview = state.enemyPreview
                    )
                }
                StageInfoPanel(state = state)
                if (state.battle != null) {
                    BattleOperationPanel(
                        player = state.player,
                        choices = state.choices,
                        onChoice = onChoice
                    )
                } else if (isShopEventUi(state.currentEvent)) {
                    ShopPanel(
                        state = state,
                        onToggleShopOfferSelection = onToggleShopOfferSelection,
                        onToggleShopSellSelection = onToggleShopSellSelection,
                        onShopBuySelected = onShopBuySelected,
                        onShopBuyPotion = onShopBuyPotion,
                        onShopSellSelected = onShopSellSelected,
                        onShopLeave = onShopLeave
                    )
                } else {
                    EventActionPanel(
                        choices = state.choices,
                        onChoice = onChoice,
                        onAdvance = onAdvance
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSelectPanel(
    state: GameUiState,
    onCreateNewSave: (Int) -> Unit,
    onLoadSave: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "存档选择", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            Text(text = "开始游戏前必须选择读取存档或创建新存档。", color = Color(0xFFB8B2A6))
            if (state.saveSlots.isEmpty()) {
                Text("存档信息加载中...")
            } else {
                state.saveSlots.forEach { slot ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = slot.title, fontWeight = FontWeight.SemiBold)
                        Text(text = slot.detail, color = Color(0xFF7B756B))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCreateNewSave(slot.slot) }) {
                                Text("新建此槽位")
                            }
                            Button(onClick = { onLoadSave(slot.slot) }, enabled = slot.hasData) {
                                Text("读取此存档")
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionPanel(
    state: GameUiState,
    onSelectRole: (String) -> Unit,
    showSkillFormula: Boolean,
    onToggleShowSkillFormula: (Boolean) -> Unit
) {
    if (state.roles.isEmpty()) {
        Text("角色数据加载中...")
        return
    }

    val selectedRole = state.roles.firstOrNull { it.id == state.selectedRoleId }
        ?: state.roles.firstOrNull { it.unlocked }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "候选角色", fontWeight = FontWeight.SemiBold)
            Text(
                text = "当前：${selectedRole?.name ?: "无"}",
                color = Color(0xFF8DB38B)
            )
        }
        state.selectedSaveSlot?.let { slot ->
            Text(text = "新存档槽位：$slot", color = Color(0xFFB8B2A6))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.weight(0.48f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.roles, key = { it.id }) { role ->
                        val isSelected = role.id == selectedRole?.id
                        RoleCard(role = role, isSelected = isSelected, onSelectRole = onSelectRole)
                    }
                }
                if (state.roles.size > 4) {
                    Text(text = "角色列表可滚动查看更多", color = Color(0xFF7B756B))
                }
            }
            Column(
                modifier = Modifier.weight(0.52f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleDetailPanel(
                    selectedRole = selectedRole,
                    onSelectRole = onSelectRole,
                    showSkillFormula = showSkillFormula
                )
                SettingsPanelCard(
                    showSkillFormula = showSkillFormula,
                    onToggleShowSkillFormula = onToggleShowSkillFormula
                )
            }
        }
    }
}

@Composable
private fun ChapterSelectPanel(
    state: GameUiState,
    onSelectChapter: (Int) -> Unit,
    onSelectDifficulty: (Int) -> Unit,
    onConfirmChapterSelection: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "关卡选择", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            Text(text = "选择章节与难度后进入冒险。未通关章节不可选择。", color = Color(0xFFB8B2A6))
            val completedLabel = if (state.completedChapters.isEmpty()) {
                "暂无通关记录"
            } else {
                "已通关：${state.completedChapters.joinToString("、") { "第 $it 章" }}"
            }
            Text(text = completedLabel, color = Color(0xFF8DB38B))
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            Text(text = "选择章节", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..state.totalChapters).forEach { chapter ->
                    val unlocked = chapter == 1 ||
                        state.completedChapters.contains(chapter) ||
                        state.completedChapters.contains(chapter - 1)
                    val isSelected = chapter == state.selectedChapter
                    val label = when {
                        state.completedChapters.contains(chapter) -> "已通关"
                        unlocked -> "可进入"
                        else -> "未解锁"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "第 $chapter 章", fontWeight = FontWeight.SemiBold)
                            Text(text = label, color = Color(0xFF7B756B))
                        }
                        if (isSelected) {
                            Button(
                                onClick = { onSelectChapter(chapter) },
                                enabled = unlocked
                            ) {
                                Text("已选择")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectChapter(chapter) },
                                enabled = unlocked
                            ) {
                                Text("选择")
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Text(text = "选择难度", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { difficulty ->
                    val isSelected = difficulty == state.selectedDifficulty
                    val label = "难度 $difficulty"
                    if (isSelected) {
                        Button(onClick = { onSelectDifficulty(difficulty) }) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelectDifficulty(difficulty) }) {
                            Text(label)
                        }
                    }
                }
            }
            Button(
                onClick = onConfirmChapterSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认进入第 ${state.selectedChapter} 章")
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: RoleProfile,
    isSelected: Boolean,
    onSelectRole: (String) -> Unit
) {
    val containerColor = when {
        isSelected -> Color(0xFF1C3B30)
        role.unlocked -> Color(0xFF182720)
        else -> Color(0xFF29231D)
    }
    val contentColor = if (role.unlocked) Color(0xFFECE8D9) else Color(0xFF9B9587)
    Card(
        onClick = { onSelectRole(role.id) },
        enabled = role.unlocked,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleAvatar(
                        name = role.name,
                        size = 36.dp,
                        background = if (role.unlocked) Color(0xFF27473A) else Color(0xFF3A2F22),
                        textColor = if (role.unlocked) Color(0xFFECE8D9) else Color(0xFFB1AB9F)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = role.name, fontWeight = FontWeight.Bold)
                        Text(text = role.role, color = Color(0xFFB8B2A6))
                    }
                }
                RoleTag(
                    text = when {
                        isSelected -> "已选择"
                        role.unlocked -> "可选"
                        else -> "未解锁"
                    },
                    background = when {
                        isSelected -> Color(0xFF3A7A5F)
                        role.unlocked -> Color(0xFF2B4E41)
                        else -> Color(0xFF4A3B2A)
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleStat(label = "生命", value = role.stats.hp)
                RoleStat(label = "攻击", value = role.stats.atk)
                RoleStat(label = "防御", value = role.stats.def)
                RoleStat(label = "速度", value = role.stats.speed)
            }
            if (!role.unlocked && role.unlock.isNotBlank()) {
                Text(text = "解锁条件：${role.unlock}", color = Color(0xFFD6B36A))
            }
        }
    }
}

@Composable
private fun RoleDetailPanel(
    selectedRole: RoleProfile?,
    onSelectRole: (String) -> Unit,
    showSkillFormula: Boolean
) {
    if (selectedRole == null) {
        Text("暂无可用角色")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleAvatar(
                    name = selectedRole.name,
                    size = 44.dp,
                    background = Color(0xFF27473A),
                    textColor = Color(0xFFECE8D9)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "角色详情", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${selectedRole.name} · ${selectedRole.role}",
                        color = Color(0xFFB8B2A6)
                    )
                }
            }
            OutlinedButton(
                onClick = { onSelectRole(selectedRole.id) },
                enabled = selectedRole.unlocked,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFECE8D9)
                )
            ) {
                Text("使用角色")
            }
        }

        Divider()
        Text(text = "基础属性", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "生命", value = selectedRole.stats.hp)
            RoleStat(label = "攻击", value = selectedRole.stats.atk)
            RoleStat(label = "防御", value = selectedRole.stats.def)
            RoleStat(label = "速度", value = selectedRole.stats.speed)
            RoleStat(label = "感知", value = selectedRole.stats.perception)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "力量", value = selectedRole.stats.strength)
            RoleStat(label = "智力", value = selectedRole.stats.intelligence)
            RoleStat(label = "敏捷", value = selectedRole.stats.agility)
        }

        Divider()
        Text(text = "成长（每级）", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "生命", value = selectedRole.growth.hpMax)
            RoleStat(label = "能量", value = selectedRole.growth.mpMax)
            RoleStat(label = "攻击", value = selectedRole.growth.atk)
            RoleStat(label = "防御", value = selectedRole.growth.def)
            RoleStat(label = "速度", value = selectedRole.growth.speed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "力量", value = selectedRole.growth.strength)
            RoleStat(label = "智力", value = selectedRole.growth.intelligence)
            RoleStat(label = "敏捷", value = selectedRole.growth.agility)
        }

        Divider()
        Text(text = "技能组合", fontWeight = FontWeight.SemiBold)
        RoleSkillIconSection(
            role = selectedRole,
            showSkillFormula = showSkillFormula
        )

        if (!selectedRole.unlocked) {
            Divider()
            Text(text = "解锁条件", fontWeight = FontWeight.SemiBold)
            Text(text = selectedRole.unlock.ifBlank { "暂无说明" }, color = Color(0xFFD6B36A))
        }
    }
}

@Composable
private fun RoleStat(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = Color(0xFF7B756B))
        Text(text = value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoleTag(text: String, background: Color) {
    Box(
        modifier = Modifier
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color(0xFFECE8D9))
    }
}

@Composable
private fun EnemyPreviewPanel(preview: EnemyPreviewUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = preview.name, fontWeight = FontWeight.SemiBold)
                Text(text = "${preview.type}  等级 ${preview.level}  数量 ${preview.count}", color = Color(0xFFB8B2A6))
            }
            RoleTag(
                text = preview.threat,
                background = when (preview.threat) {
                    "优势" -> Color(0xFF2F6B52)
                    "均势" -> Color(0xFF5B5A3A)
                    "偏难" -> Color(0xFF7B5A2E)
                    else -> Color(0xFF7A3A2E)
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "生命", value = preview.hp)
            RoleStat(label = "攻击", value = preview.atk)
            RoleStat(label = "防御", value = preview.def)
            RoleStat(label = "速度", value = preview.speed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "力量", value = preview.strength)
            RoleStat(label = "智力", value = preview.intelligence)
            RoleStat(label = "敏捷", value = preview.agility)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "命中", value = preview.hit)
            RoleStat(label = "闪避", value = preview.eva)
            RoleStat(label = "暴击", value = preview.crit)
            RoleStat(label = "抗暴", value = preview.resist)
        }

        Text(text = "先手规则：${preview.firstStrike}", color = Color(0xFFB8B2A6))
        if (preview.roundLimit != null) {
            Text(text = "回合上限：${preview.roundLimit}", color = Color(0xFFB8B2A6))
        }
        if (preview.note.isNotBlank()) {
            Text(text = "备注：${preview.note}", color = Color(0xFFB8B2A6))
        }
        if (preview.dropTableId.isNotBlank()) {
            Text(text = "掉落预览（${preview.dropTableId}）", color = Color(0xFFE8C07D))
            preview.dropPreview.forEach { line ->
                Text(text = "- $line", color = Color(0xFFB8B2A6))
            }
        }
        Text(text = "战斗评估：${preview.tip}", color = Color(0xFF8DB38B))
        Text(text = preview.summary, color = Color(0xFF8DB38B))
    }
}

@Composable
private fun RoleAvatar(name: String, size: Dp, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val label = name.take(1).ifBlank { "?" }
        Text(text = label, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun SidePanel(
    modifier: Modifier,
    state: GameUiState,
    onEquipItem: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.screen == GameScreen.ROLE_DETAIL) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "装备面板", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    EquipmentOverviewPanel(player = state.player)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "背包面板", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    InventoryPanel(
                        player = state.player,
                        onEquipItem = onEquipItem
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "卡牌面板", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    CardGridPanel(cards = state.player.cards)
                }
            }
            return
        }
        if (state.screen == GameScreen.SETTINGS) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "设置提示", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(text = "已进入设置界面，可在左侧调整游戏设置。", color = Color(0xFFB8B2A6))
                    Text(text = "点击顶部“设”返回上一个界面。", color = Color(0xFF7B756B))
                }
            }
            return
        }
        if (state.screen != GameScreen.ADVENTURE) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "提示", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("请先在左侧完成存档选择与角色确认。", color = Color(0xFFB8B2A6))
                }
            }
            return
        }
    }
}

@Composable
private fun RoleDetailPanel(
    state: GameUiState,
    showSkillFormula: Boolean,
    onAssignBattleSkill: (Int, String) -> Unit,
    onClearBattleSkill: (Int) -> Unit
) {
    val player = state.player
    val role = state.roles.firstOrNull { it.id == state.selectedRoleId }
        ?: state.roles.firstOrNull { it.unlocked }
    val roleId = role?.id ?: state.selectedRoleId
    val availableSkills = if (roleId.isBlank()) {
        emptyList()
    } else {
        state.skillCatalog.filter { entry ->
            entry.sourceRoleIds.contains(roleId) && !entry.type.equals("PASSIVE", true)
        }
    }
    val hit = (70 + player.speed + player.hitBonus).coerceIn(50, 98)
    val eva = (8 + player.speed / 2 + player.evaBonus).coerceIn(5, 45)
    val crit = (6 + player.speed / 3 + player.critBonus).coerceIn(5, 40)
    val resist = (3 + player.resistBonus).coerceIn(0, 50)
    val critDmg = 1.5

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "基础属性", fontWeight = FontWeight.SemiBold)
        InfoGrid(
            items = listOf(
                "生命" to "${player.hp}/${player.hpMax}",
                "能量" to "${player.mp}/${player.mpMax}",
                "攻击" to "${player.atk}",
                "防御" to "${player.def}",
                "速度" to "${player.speed}",
                "命中" to "${hit}%",
                "闪避" to "${eva}%",
                "暴击率" to "${crit}%"
            )
        )
        Divider(modifier = Modifier.padding(vertical = 4.dp))
        Text(text = "战斗属性与技能", fontWeight = FontWeight.SemiBold)
        InfoGrid(
            items = listOf(
                "力量" to "${player.strength}",
                "智力" to "${player.intelligence}",
                "敏捷" to "${player.agility}",
                "法术强度" to "${player.intelligence}",
                "法术抗性" to "${resist}",
                "暴击伤害" to "${(critDmg * 100).toInt()}%",
                "抗暴" to "${resist}%"
            )
        )
        SkillCatalogSummary(role = role, showSkillFormula = showSkillFormula)
        Divider(modifier = Modifier.padding(vertical = 4.dp))
        Text(text = "战斗选项配置", fontWeight = FontWeight.SemiBold)
        BattleOptionConfigPanel(
            player = player,
            availableSkills = availableSkills,
            onAssignBattleSkill = onAssignBattleSkill,
            onClearBattleSkill = onClearBattleSkill
        )
        Divider(modifier = Modifier.padding(vertical = 4.dp))
        Text(text = "状态与效果", fontWeight = FontWeight.SemiBold)
        if (state.playerStatuses.isEmpty()) {
            Text(text = "暂无状态", color = Color(0xFF7B756B))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.playerStatuses.forEach { status ->
                    StatusLine(status = status)
                }
            }
        }
    }
}

private data class BattleDragSkill(
    val id: String,
    val name: String
)

@Composable
private fun BattleOptionConfigPanel(
    player: PlayerStats,
    availableSkills: List<SkillCatalogEntry>,
    onAssignBattleSkill: (Int, String) -> Unit,
    onClearBattleSkill: (Int) -> Unit
) {
    val slotIds = remember(player.battleSkillSlots) {
        normalizeBattleSkillSlotsForUi(player.battleSkillSlots)
    }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingSkill by remember { mutableStateOf<BattleDragSkill?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    val potionCount = player.potionCount.coerceAtLeast(0)
    val sortedSkills = remember(availableSkills) {
        availableSkills.sortedWith(compareBy<SkillCatalogEntry> { it.type }.thenBy { it.name })
    }

    fun handleDrop() {
        val dragging = draggingSkill ?: return
        val target = slotBounds.entries.firstOrNull { it.value.contains(dragPosition) }?.key
        if (target != null) {
            GameLogger.info(
                "战斗配置",
                "拖动技能命中槽位：技能=${dragging.name} 槽位=${target + 1}"
            )
            onAssignBattleSkill(target, dragging.id)
        } else {
            GameLogger.info("战斗配置", "拖动技能未命中槽位：技能=${dragging.name}")
        }
        draggingSkill = null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                containerOffset = coords.positionInRoot()
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "拖动技能到槽位（${BattleSkillSlotCount} 个）",
                color = Color(0xFF7B756B)
            )
            val topSlotSize = 96.dp
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
                    modifier = Modifier.size(topSlotSize, 48.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "普通攻击", fontWeight = FontWeight.SemiBold)
                    }
                }
                repeat(BattlePotionSlotCount) { index ->
                    val enabled = potionCount >= index + 1
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (enabled) Color(0xFF182720) else Color(0xFF222B26)
                        ),
                        border = BorderStroke(1.dp, if (enabled) Color(0xFF3A5C4C) else Color(0xFF2C3B33)),
                        modifier = Modifier.size(topSlotSize, 48.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "药水${index + 1}", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "剩余 $potionCount",
                                color = if (enabled) Color(0xFF8DB38B) else Color(0xFF7B756B)
                            )
                        }
                    }
                }
            }
            Text(text = "技能槽位", fontWeight = FontWeight.SemiBold)
            val slotSize = 76.dp
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slotIds.forEachIndexed { slotIndex, skillId ->
                    val entry = sortedSkills.firstOrNull { it.id == skillId }
                    val label = entry?.name ?: "空"
                    val isEmpty = skillId.isBlank() || entry == null
                    val highlight = draggingSkill != null &&
                        slotBounds[slotIndex]?.contains(dragPosition) == true
                    val borderColor = if (highlight) Color(0xFF8DB38B) else Color(0xFF2C3B33)
                    Box(
                        modifier = Modifier
                            .size(slotSize, 48.dp)
                            .onGloballyPositioned { coords ->
                                slotBounds[slotIndex] = coords.boundsInRoot()
                            }
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (highlight) Color(0xFF1D2D26) else Color(0xFF182720)
                            ),
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEmpty) Color(0xFF7B756B) else Color(0xFFECE8D9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isEmpty) {
                            Text(
                                text = "×",
                                color = Color(0xFFD6B36A),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clickable {
                                        GameLogger.info("战斗配置", "点击清空技能槽位：槽位=${slotIndex + 1}")
                                        onClearBattleSkill(slotIndex)
                                    }
                            )
                        }
                    }
                }
            }
            Text(text = "技能池", fontWeight = FontWeight.SemiBold)
            if (sortedSkills.isEmpty()) {
                PlaceholderPanel("暂无可配置技能")
            } else {
                val skillRows = sortedSkills.chunked(3)
                skillRows.forEach { rowSkills ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowSkills.forEach { skill ->
                            var coordinates by remember(skill.id) { mutableStateOf<LayoutCoordinates?>(null) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
                                border = BorderStroke(1.dp, Color(0xFF2C3B33)),
                                modifier = Modifier
                                    .size(112.dp, 52.dp)
                                    .onGloballyPositioned { coords -> coordinates = coords }
                                    .pointerInput(skill.id) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val rootOffset = coordinates?.localToRoot(offset) ?: offset
                                                draggingSkill = BattleDragSkill(skill.id, skill.name)
                                                dragPosition = rootOffset
                                                GameLogger.info("战斗配置", "开始拖动技能：${skill.name}")
                                            },
                                            onDragCancel = {
                                                GameLogger.info("战斗配置", "拖动技能取消：${skill.name}")
                                                draggingSkill = null
                                            },
                                            onDragEnd = {
                                                handleDrop()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragPosition += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = skill.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "消耗${skill.cost} 冷却${skill.cooldown}",
                                        color = Color(0xFF7B756B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        val missing = 3 - rowSkills.size
                        repeat(missing) {
                            Spacer(modifier = Modifier.size(112.dp, 52.dp))
                        }
                    }
                }
            }
        }
        if (draggingSkill != null) {
            val localOffset = dragPosition - containerOffset + Offset(12f, 12f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                    .background(Color(0xFF26362E), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF8DB38B), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = draggingSkill?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun normalizeBattleSkillSlotsForUi(slots: List<String>): List<String> {
    val normalized = MutableList(BattleSkillSlotCount) { "" }
    slots.take(BattleSkillSlotCount).forEachIndexed { index, skillId ->
        normalized[index] = skillId
    }
    return normalized
}

@Composable
private fun EquipmentInfoRow(
    slot: EquipmentSlot,
    item: EquipmentItem?,
    onShowDetail: () -> Unit
) {
    val rarityColor = item?.let { equipmentRarityColor(it.rarityTier, it.rarityId) } ?: Color(0xFF8F8F8F)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${slotLabel(slot)}：${item?.name ?: "空"}",
                fontWeight = FontWeight.SemiBold,
                color = if (item == null) Color(0xFFB8B2A6) else rarityColor
            )
            if (item != null) {
                Text(
                    text = "稀有度 ${item.rarityName} | 等级 ${item.level} | 评分 ${item.score}",
                    color = rarityColor
                )
            }
        }
        OutlinedButton(onClick = onShowDetail, enabled = item != null) {
            Text("详情")
        }
    }
}

@Composable
private fun EquipmentOverviewPanel(
    player: PlayerStats
) {
    val entries = buildEquipmentIconEntries(player)
    if (entries.isEmpty()) {
        Text(text = "暂无装备槽位", color = Color(0xFF7B756B))
        return
    }
    EquipmentIconGrid(entries = entries)
}

@Composable
private fun InventoryOverviewPanel(
    player: PlayerStats,
    onShowEquipmentDetail: (EquipmentItem?) -> Unit
) {
    val inventory = player.inventory
    Text("容量 ${inventory.items.size}/${inventory.capacity}", color = Color(0xFFB8B2A6))
    if (inventory.items.isEmpty()) {
        PlaceholderPanel("背包空空如也")
        return
    }
    Text(text = "悬浮查看详情，点击图标查看装备详情", color = Color(0xFF7B756B))
    InventoryIconGrid(
        items = inventory.items,
        columns = 4,
        iconSize = 40.dp,
        logTag = "背包面板"
    ) { item ->
        onShowEquipmentDetail(item)
    }
}

private data class EquipmentIconEntry(
    val id: String,
    val slot: EquipmentSlot,
    val item: EquipmentItem?
)

private fun buildEquipmentIconEntries(player: PlayerStats): List<EquipmentIconEntry> {
    return EquipmentSlot.values().map { slot ->
        EquipmentIconEntry(
            id = "slot_${slot.name}",
            slot = slot,
            item = player.equipment.slots[slot]
        )
    }
}

@Composable
private fun EquipmentIconGrid(
    entries: List<EquipmentIconEntry>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "悬浮查看详情", color = Color(0xFF7B756B))
        entries.chunked(4).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowEntries.forEach { entry ->
                    EquipmentIconCard(
                        entry = entry,
                        modifier = Modifier.weight(1f)
                    )
                }
                val missing = 4 - rowEntries.size
                if (missing > 0) {
                    Spacer(modifier = Modifier.weight(missing.toFloat()))
                }
            }
        }
    }
}

@Composable
private fun EquipmentIconCard(
    entry: EquipmentIconEntry,
    modifier: Modifier = Modifier
) {
    val item = entry.item
    val slot = entry.slot
    val slotName = slotLabel(slot)
    val baseColor = item?.let { equipmentRarityColor(it.rarityTier, it.rarityId) }
        ?: equipmentSlotColor(slot)
    val fallbackText = equipmentSlotShortLabel(slot)
    val label = item?.name ?: "${slotName}空"
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HoverTooltipBox(
            logTag = "装备图标",
            logName = item?.name ?: slotName,
            tooltip = {
                EquipmentTooltipCard(
                    slot = slot,
                    item = item
                )
            }
        ) { baseModifier ->
            EquipmentRarityIcon(
                label = fallbackText,
                color = baseColor,
                size = 60.dp,
                modifier = baseModifier
            )
        }
        Text(
            text = label,
            color = if (item == null) Color(0xFF7B756B) else Color(0xFFB8B2A6),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InventoryIconGrid(
    items: List<EquipmentItem>,
    columns: Int,
    iconSize: Dp,
    logTag: String,
    onItemClick: (EquipmentItem) -> Unit
) {
    val rows = items.chunked(columns)
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows.size) { rowIndex ->
            val rowItems = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    InventoryIconCell(
                        item = item,
                        iconSize = iconSize,
                        logTag = logTag,
                        onItemClick = onItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryIconCell(
    item: EquipmentItem,
    iconSize: Dp,
    logTag: String,
    onItemClick: (EquipmentItem) -> Unit
) {
    val rarityColor = equipmentRarityColor(item.rarityTier, item.rarityId)
    HoverTooltipBox(
        logTag = logTag,
        logName = item.name,
        tooltip = {
            EquipmentTooltipCard(
                slot = item.slot,
                item = item
            )
        }
    ) { baseModifier ->
        val clickableModifier = baseModifier.clickable {
            GameLogger.info(logTag, "点击背包物品图标：${item.name} uid=${item.uid} 槽位=${item.slot}")
            onItemClick(item)
        }
        EquipmentRarityIcon(
            label = equipmentSlotShortLabel(item.slot),
            color = rarityColor,
            size = iconSize,
            modifier = clickableModifier
        )
    }
}

@Composable
private fun ShopInventoryIconGrid(
    items: List<EquipmentItem>,
    selectedIds: Set<String>,
    columns: Int,
    iconSize: Dp,
    onToggleSelected: (String) -> Unit
) {
    val rows = items.chunked(columns)
    LazyColumn(
        modifier = Modifier.heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows.size) { rowIndex ->
            val rowItems = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    val selected = selectedIds.contains(item.uid)
                    ShopInventoryIconCell(
                        item = item,
                        selected = selected,
                        iconSize = iconSize,
                        onToggleSelected = onToggleSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopInventoryIconCell(
    item: EquipmentItem,
    selected: Boolean,
    iconSize: Dp,
    onToggleSelected: (String) -> Unit
) {
    val rarityColor = equipmentRarityColor(item.rarityTier, item.rarityId)
    val borderColor = if (selected) Color(0xFF8DB38B) else Color(0xFF2C3B33)
    val borderWidth = if (selected) 2.dp else 1.dp
    HoverTooltipBox(
        logTag = "商店背包",
        logName = item.name,
        tooltip = {
            EquipmentTooltipCard(
                slot = item.slot,
                item = item
            )
        }
    ) { baseModifier ->
        Box(
            modifier = baseModifier
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .padding(2.dp)
                .clickable {
                    GameLogger.info(
                        "商店背包",
                        "切换出售选择：${item.name} uid=${item.uid} 选中=${!selected}"
                    )
                    onToggleSelected(item.uid)
                }
        ) {
            EquipmentRarityIcon(
                label = equipmentSlotShortLabel(item.slot),
                color = rarityColor,
                size = iconSize
            )
        }
    }
}

@Composable
private fun EquipmentRarityIcon(
    label: String,
    color: Color,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        listOf(
            color.copy(alpha = 0.45f),
            color.copy(alpha = 0.18f)
        )
    )
    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, color.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .background(gradient, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EquipmentTooltipCard(
    slot: EquipmentSlot,
    item: EquipmentItem?
) {
    val slotName = slotLabel(slot)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "$slotName 装备详情", fontWeight = FontWeight.SemiBold)
            if (item == null) {
                Text(text = "该槽位暂无装备", color = Color(0xFF7B756B))
                return@Column
            }
            val rarityColor = equipmentRarityColor(item.rarityTier, item.rarityId)
            Text(
                text = "${item.name}（${item.rarityName}）",
                fontWeight = FontWeight.SemiBold,
                color = rarityColor
            )
            Text(text = "等级 ${item.level} | 评分 ${item.score}", color = Color(0xFFB8B2A6))
            Text(text = "基础属性 ${formatStats(item.stats)}", color = Color(0xFFB8B2A6))
            val totalStats = formatStats(item.totalStats())
            Text(text = "总属性 $totalStats", color = Color(0xFF7B756B))
            if (item.affixes.isNotEmpty()) {
                Text(text = "词条 ${formatAffixes(item.affixes)}", color = Color(0xFF8DB38B))
            }
            val sourceLabel = formatEquipmentSourceLabel(item.source)
            if (sourceLabel.isNotBlank()) {
                Text(text = "来源 $sourceLabel", color = Color(0xFF7B756B))
            }
        }
    }
}

@Composable
private fun EquipmentCatalogTooltipCard(
    entry: EquipmentCatalogEntry,
    unlocked: Boolean
) {
    val rarityColor = equipmentRarityColor(entry.rarityTier, entry.rarityId)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "装备图鉴详情", fontWeight = FontWeight.SemiBold)
            if (!unlocked) {
                Text(text = "该装备尚未解锁", color = Color(0xFF7B756B))
                Text(text = "解锁条件：获得该装备", color = Color(0xFF7B756B))
                return@Column
            }
            Text(
                text = "${entry.name}（${entry.rarityName}）",
                fontWeight = FontWeight.SemiBold,
                color = rarityColor
            )
            Text(
                text = "部位 ${slotLabel(entry.slot)} | 等级需求 ${entry.levelReq} | 强化上限 ${entry.enhanceMax}",
                color = Color(0xFFB8B2A6)
            )
            Text(text = "基础属性 ${formatStats(entry.baseStats)}", color = Color(0xFFB8B2A6))
            Text(
                text = "词条数量 ${entry.affixMin}-${entry.affixMax} | 卖价 ${entry.sellValue} | 分解产出 ${entry.salvageYield}",
                color = Color(0xFF7B756B)
            )
        }
    }
}

private fun equipmentSlotShortLabel(slot: EquipmentSlot): String {
    return when (slot) {
        EquipmentSlot.WEAPON -> "武"
        EquipmentSlot.ARMOR -> "甲"
        EquipmentSlot.HELM -> "盔"
        EquipmentSlot.ACCESSORY -> "饰"
    }
}

private fun equipmentSlotColor(slot: EquipmentSlot): Color {
    return when (slot) {
        EquipmentSlot.WEAPON -> Color(0xFFE67E22)
        EquipmentSlot.ARMOR -> Color(0xFF5DADE2)
        EquipmentSlot.HELM -> Color(0xFF6FBF73)
        EquipmentSlot.ACCESSORY -> Color(0xFFF5C542)
    }
}

private data class SkillIconEntry(
    val id: String,
    val title: String,
    val skill: RoleSkill
)

@Composable
private fun RoleSkillIconSection(
    role: RoleProfile,
    showSkillFormula: Boolean
) {
    val entries = buildSkillIconEntries(role)
    if (entries.isEmpty()) {
        Text(text = "暂无技能信息", color = Color(0xFF7B756B))
        return
    }
    SkillIconGrid(
        entries = entries,
        showSkillFormula = showSkillFormula
    )
}

private fun buildSkillIconEntries(role: RoleProfile): List<SkillIconEntry> {
    val entries = mutableListOf<SkillIconEntry>()
    if (role.passiveSkill.name.isNotBlank()) {
        entries += SkillIconEntry(
            id = "passive_${role.passiveSkill.name}",
            title = "被动技能",
            skill = role.passiveSkill
        )
    }
    role.activeSkills.forEachIndexed { index, skill ->
        if (skill.name.isNotBlank()) {
            entries += SkillIconEntry(
                id = "active_${index}_${skill.name}",
                title = "主动技能 ${index + 1}",
                skill = skill
            )
        }
    }
    if (role.ultimateSkill.name.isNotBlank()) {
        entries += SkillIconEntry(
            id = "ultimate_${role.ultimateSkill.name}",
            title = "终极技能",
            skill = role.ultimateSkill
        )
    }
    return entries
}

@Composable
private fun SkillIconGrid(
    entries: List<SkillIconEntry>,
    showSkillFormula: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "悬浮查看详情", color = Color(0xFF7B756B))
        entries.chunked(4).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowEntries.forEach { entry ->
                    SkillIconCard(
                        entry = entry,
                        selected = false,
                        showSkillFormula = showSkillFormula,
                        modifier = Modifier.weight(1f)
                    )
                }
                val missing = 4 - rowEntries.size
                if (missing > 0) {
                    Spacer(modifier = Modifier.weight(missing.toFloat()))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkillIconCard(
    entry: SkillIconEntry,
    selected: Boolean,
    showSkillFormula: Boolean,
    modifier: Modifier = Modifier
) {
    val typeLabel = skillTypeLabel(entry.skill.type)
    val baseColor = skillTypeColor(entry.skill.type)
    val borderColor = if (selected) Color(0xFFE8C07D) else baseColor.copy(alpha = 0.7f)
    val backgroundColor = baseColor.copy(alpha = if (selected) 0.25f else 0.18f)
    val iconPath = skillIconPath(entry.skill.type, locked = false)
    val painter = rememberSkillIconPainter(iconPath, "技能图标")
    val fallbackText = entry.skill.name.take(1).ifBlank {
        when (typeLabel) {
            "被动" -> "被"
            "主动" -> "主"
            "终极" -> "终"
            else -> typeLabel.take(1)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HoverTooltipBox(
            logTag = "技能图标",
            logName = entry.skill.name,
            tooltip = {
                SkillDetailCard(
                    title = entry.title,
                    skill = entry.skill,
                    showFormula = showSkillFormula
                )
            }
        ) { baseModifier ->
            Box(
                modifier = baseModifier
                    .size(60.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                    .background(backgroundColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (painter == null) {
                    Text(
                        text = fallbackText,
                        fontWeight = FontWeight.Bold,
                        color = baseColor
                    )
                } else {
                    Image(
                        painter = painter,
                        contentDescription = entry.skill.name,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
        Text(
            text = entry.skill.name,
            color = Color(0xFFB8B2A6),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SkillCatalogSummary(role: RoleProfile?, showSkillFormula: Boolean) {
    if (role == null) {
        Text(text = "暂无技能信息", color = Color(0xFF7B756B))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "已解锁技能", fontWeight = FontWeight.SemiBold)
        RoleSkillIconSection(
            role = role,
            showSkillFormula = showSkillFormula
        )
    }
}

@Composable
private fun StatusLine(status: StatusInstance) {
    val color = statusColor(status.type)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(text = formatStatusLine(status), color = Color(0xFFB8B2A6))
    }
}

@Composable
private fun StatusPanel(player: PlayerStats, battle: BattleUiState?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${player.name}  等级 ${player.level}")
        Text("经验 ${player.exp}/${player.expToNext}")
        Text("生命 ${player.hp}/${player.hpMax}")
        Text("能量 ${player.mp}/${player.mpMax}")
        Text("攻击 ${player.atk}  防御 ${player.def}")
        Text("速度 ${player.speed}")
        Text("力量 ${player.strength}  智力 ${player.intelligence}  敏捷 ${player.agility}")
        if (player.hitBonus != 0 || player.evaBonus != 0 || player.critBonus != 0 || player.resistBonus != 0) {
            Text(
                text = "命中+${player.hitBonus} 闪避+${player.evaBonus} 暴击+${player.critBonus} 抗暴+${player.resistBonus}",
                color = Color(0xFFB8B2A6)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Text("金币 ${player.gold}")
            Spacer(modifier = Modifier.width(12.dp))
            Text("材料 ${player.materials}")
        }
        if (battle != null) {
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            Text(text = "战斗状态", fontWeight = FontWeight.SemiBold)
            StatusList(title = "我方状态", statuses = battle.playerStatuses)
            StatusList(title = "敌方状态", statuses = battle.enemyStatuses)
        }
    }
}

@Composable
private fun StatusList(title: String, statuses: List<StatusInstance>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, fontWeight = FontWeight.SemiBold, color = Color(0xFF8DB38B))
        if (statuses.isEmpty()) {
            Text(text = "暂无状态", color = Color(0xFF7B756B))
        } else {
            statuses.forEach { status ->
                Text(text = "- ${formatStatusLine(status)}", color = Color(0xFFB8B2A6))
            }
        }
    }
}

@Composable
private fun SkillPanel(role: RoleProfile?, showSkillFormula: Boolean) {
    if (role == null) {
        PlaceholderPanel("暂无角色技能")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "${role.name} / ${role.role}", color = Color(0xFFB8B2A6))
        SkillDetailCard(
            title = "被动技能",
            skill = role.passiveSkill,
            showFormula = showSkillFormula
        )
        if (role.activeSkills.isEmpty()) {
            Text(text = "主动技能：暂无", color = Color(0xFFB8B2A6))
        } else {
            role.activeSkills.forEachIndexed { index, skill ->
                SkillDetailCard(
                    title = "主动技能 ${index + 1}",
                    skill = skill,
                    showFormula = showSkillFormula
                )
            }
        }
        SkillDetailCard(
            title = "终极技能",
            skill = role.ultimateSkill,
            showFormula = showSkillFormula
        )
    }
}

@Composable
private fun EquipmentPanel(
    player: PlayerStats,
    onUnequip: (EquipmentSlot) -> Unit
) {
    val loadout = player.equipment
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EquipmentSlot.values().forEach { slot ->
            val item = loadout.slots[slot]
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val rarityColor = item?.let { equipmentRarityColor(it.rarityTier, it.rarityId) }
                    ?: Color(0xFF8F8F8F)
                val levelColor = item?.let { equipmentLevelColor(it.level) } ?: Color(0xFFB8B2A6)
                Text(
                    text = "${slotLabel(slot)}：${item?.name ?: "空"}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (item == null) Color(0xFFB8B2A6) else rarityColor
                )
                if (item != null) {
                    Text(
                        text = "稀有度 ${item.rarityName} | 等级 ${item.level} | 评分 ${item.score}",
                        color = rarityColor
                    )
                    Text(text = "等级 ${item.level}", color = levelColor)
                    Text(text = "属性 ${formatStats(item.totalStats())}", color = Color(0xFFB8B2A6))
                    if (item.affixes.isNotEmpty()) {
                        Text(text = "词条", fontWeight = FontWeight.SemiBold)
                        item.affixes.forEach { affix ->
                            Text(
                                text = formatAffixLine(affix),
                                color = equipmentAffixColor(item.rarityTier, item.rarityId)
                            )
                        }
                    }
                    OutlinedButton(onClick = { onUnequip(slot) }) {
                        Text("卸下")
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun CodexPanel(
    state: GameUiState,
    onSelectCodexTab: (CodexTab) -> Unit
) {
    val equipmentIds = state.equipmentCatalog.map { it.id }.toSet()
    val monsterIds = state.monsterCatalog.map { it.id }.toSet()
    val unlockedEquipment = state.player.discoveredEquipmentIds.filter { equipmentIds.contains(it) }.toSet()
    val unlockedMonsters = state.player.discoveredEnemyIds.filter { monsterIds.contains(it) }.toSet()
    val unlockedRoles = state.roles.filter { it.unlocked }.map { it.id }.toSet()
    val unlockedSkills = state.skillCatalog.filter { entry ->
        entry.sourceRoleIds.isEmpty() || entry.sourceRoleIds.any { unlockedRoles.contains(it) }
    }.map { it.id }.toSet()

    val equipmentLabel = "装备 ${unlockedEquipment.size}/${equipmentIds.size}"
    val skillLabel = "技能 ${unlockedSkills.size}/${state.skillCatalog.size}"
    val monsterLabel = "怪物 ${unlockedMonsters.size}/${monsterIds.size}"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CodexTabButton(
                label = skillLabel,
                selected = state.codexTab == CodexTab.SKILL,
                onClick = { onSelectCodexTab(CodexTab.SKILL) }
            )
            CodexTabButton(
                label = equipmentLabel,
                selected = state.codexTab == CodexTab.EQUIPMENT,
                onClick = { onSelectCodexTab(CodexTab.EQUIPMENT) }
            )
            CodexTabButton(
                label = monsterLabel,
                selected = state.codexTab == CodexTab.MONSTER,
                onClick = { onSelectCodexTab(CodexTab.MONSTER) }
            )
        }
        Text(
            text = "图鉴会随着探索逐步解锁，未解锁条目会隐藏细节。",
            color = Color(0xFF7B756B)
        )
        when (state.codexTab) {
            CodexTab.SKILL -> SkillCatalogPanel(
                entries = state.skillCatalog,
                unlockedRoleIds = unlockedRoles
            )
            CodexTab.EQUIPMENT -> EquipmentCatalogPanel(
                entries = state.equipmentCatalog,
                unlockedIds = unlockedEquipment
            )
            CodexTab.MONSTER -> MonsterCatalogPanel(
                entries = state.monsterCatalog,
                unlockedIds = unlockedMonsters
            )
        }
    }
}

@Composable
private fun CodexTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun EquipmentCatalogPanel(
    entries: List<EquipmentCatalogEntry>,
    unlockedIds: Set<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已收录 ${entries.size} 件装备", color = Color(0xFFB8B2A6))
        if (entries.isEmpty()) {
            PlaceholderPanel("暂无装备图鉴数据")
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                val rarityColor = equipmentRarityColor(entry.rarityTier, entry.rarityId)
                val unlocked = unlockedIds.contains(entry.id)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HoverTooltipBox(
                            logTag = "装备图鉴",
                            logName = entry.name,
                            tooltip = {
                                EquipmentCatalogTooltipCard(
                                    entry = entry,
                                    unlocked = unlocked
                                )
                            }
                        ) { baseModifier ->
                            EquipmentRarityIcon(
                                label = if (unlocked) equipmentSlotShortLabel(entry.slot) else "?",
                                color = rarityColor.copy(alpha = if (unlocked) 1f else 0.35f),
                                size = 40.dp,
                                modifier = baseModifier
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (unlocked) "${entry.name}（${entry.rarityName}）" else "未解锁装备",
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) rarityColor else Color(0xFF8F8F8F)
                            )
                            if (unlocked) {
                                Text(
                                    text = "部位 ${slotLabel(entry.slot)} | 等级需求 ${entry.levelReq} | 强化上限 ${entry.enhanceMax}",
                                    color = Color(0xFFB8B2A6)
                                )
                                Text(
                                    text = "基础属性 ${formatStats(entry.baseStats)}",
                                    color = Color(0xFFB8B2A6)
                                )
                                Text(
                                    text = "词条数量 ${entry.affixMin}-${entry.affixMax} | 卖价 ${entry.sellValue} | 分解产出 ${entry.salvageYield}",
                                    color = Color(0xFF7B756B)
                                )
                            } else {
                                Text(text = "解锁条件：获得该装备", color = Color(0xFF7B756B))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCatalogPanel(
    entries: List<SkillCatalogEntry>,
    unlockedRoleIds: Set<String>
) {
    val unlockedSkillIds = entries.filter { entry ->
        entry.sourceRoleIds.isEmpty() || entry.sourceRoleIds.any { unlockedRoleIds.contains(it) }
    }.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已收录 ${entries.size} 个技能", color = Color(0xFFB8B2A6))
        if (entries.isEmpty()) {
            PlaceholderPanel("暂无技能图鉴数据")
            return
        }
        Text(text = "悬浮查看详情", color = Color(0xFF7B756B))
        val rows = entries.chunked(4)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rows) { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowEntries.forEach { entry ->
                        val unlocked = unlockedSkillIds.contains(entry.id)
                        SkillCatalogIconCard(
                            entry = entry,
                            unlocked = unlocked,
                            selected = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val missing = 4 - rowEntries.size
                    if (missing > 0) {
                        Spacer(modifier = Modifier.weight(missing.toFloat()))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SkillCatalogIconCard(
    entry: SkillCatalogEntry,
    unlocked: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val baseColor = if (unlocked) skillTypeColor(entry.type) else Color(0xFF8F8F8F)
    val borderColor = if (selected) Color(0xFFE8C07D) else baseColor.copy(alpha = 0.7f)
    val backgroundColor = baseColor.copy(alpha = if (selected) 0.25f else 0.18f)
    val iconPath = skillIconPath(entry.type, locked = !unlocked)
    val painter = rememberSkillIconPainter(iconPath, "技能图鉴")
    val fallbackText = if (unlocked) {
        entry.name.take(1).ifBlank { "技" }
    } else {
        "锁"
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HoverTooltipBox(
            logTag = "技能图鉴",
            logName = entry.name,
            tooltip = {
                SkillCatalogDetailCard(entry = entry, unlocked = unlocked)
            }
        ) { baseModifier ->
            Box(
                modifier = baseModifier
                    .size(56.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                    .background(backgroundColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (painter == null) {
                    Text(
                        text = fallbackText,
                        fontWeight = FontWeight.Bold,
                        color = baseColor
                    )
                } else {
                    Image(
                        painter = painter,
                        contentDescription = entry.name,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Text(
            text = if (unlocked) entry.name else "未解锁",
            color = Color(0xFFB8B2A6),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SkillCatalogDetailCard(entry: SkillCatalogEntry, unlocked: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (unlocked) "${entry.name}（${skillTypeLabel(entry.type)}）" else "未解锁技能",
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) skillTypeColor(entry.type) else Color(0xFF8F8F8F)
            )
            if (unlocked) {
                val costText = if (entry.cost <= 0) "无" else entry.cost.toString()
                val cooldownText = if (entry.cooldown <= 0) "无" else "${entry.cooldown} 回合"
                Text(
                    text = "目标 ${entry.target} | 消耗 $costText | 冷却 $cooldownText",
                    color = Color(0xFFB8B2A6)
                )
                if (entry.description.isNotBlank()) {
                    Text(text = entry.description, color = Color(0xFFB8B2A6))
                }
                if (entry.effects.isNotEmpty()) {
                    Text(
                        text = "效果 ${formatSkillEffects(entry.effects)}",
                        color = Color(0xFF7B756B)
                    )
                }
                if (entry.sourceRoleNames.isNotEmpty()) {
                    Text(
                        text = "所属角色 ${entry.sourceRoleNames.joinToString("、")}",
                        color = Color(0xFF7B756B)
                    )
                }
            } else {
                val roleHint = if (entry.sourceRoleNames.isEmpty()) {
                    "解锁条件：解锁对应角色"
                } else {
                    "解锁条件：解锁 ${entry.sourceRoleNames.joinToString("、")}"
                }
                Text(text = roleHint, color = Color(0xFF7B756B))
            }
        }
    }
}

@Composable
private fun MonsterCatalogPanel(
    entries: List<MonsterCatalogEntry>,
    unlockedIds: Set<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已收录 ${entries.size} 个怪物", color = Color(0xFFB8B2A6))
        if (entries.isEmpty()) {
            PlaceholderPanel("暂无怪物图鉴数据")
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                val unlocked = unlockedIds.contains(entry.id)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (unlocked) "${entry.name}（${entry.type}）" else "未解锁怪物",
                            fontWeight = FontWeight.SemiBold,
                            color = if (unlocked) Color(0xFFE8C07D) else Color(0xFF8F8F8F)
                        )
                        if (unlocked) {
                            Text(
                                text = "等级 ${entry.level} | 属性 ${formatEnemyStats(entry.stats)}",
                                color = Color(0xFFB8B2A6)
                            )
                            if (entry.skills.isNotEmpty()) {
                                Text(text = "技能", fontWeight = FontWeight.SemiBold)
                                entry.skills.forEach { skill ->
                                    Text(
                                        text = formatEnemySkill(skill),
                                        color = Color(0xFF7B756B)
                                    )
                                }
                            }
                            if (entry.appearances.isNotEmpty()) {
                                Text(
                                    text = "出没记录 ${entry.appearances.joinToString("、")}",
                                    color = Color(0xFF7B756B)
                                )
                            }
                            if (entry.notes.isNotBlank()) {
                                Text(text = "备注 ${entry.notes}", color = Color(0xFF7B756B))
                            }
                            if (entry.dropHint.isNotBlank()) {
                                Text(text = "掉落 ${entry.dropHint}", color = Color(0xFF7B756B))
                            }
                        } else {
                            Text(text = "解锁条件：击败该怪物 1 次", color = Color(0xFF7B756B))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryPanel(
    player: PlayerStats,
    onEquipItem: (String) -> Unit
) {
    val inventory = player.inventory
    val slots = buildGridSlots(inventory.items)
    val rows = slots.chunked(InventoryGridColumns)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "容量 ${inventory.items.size}/${inventory.capacity} | 网格 ${InventoryGridColumns}x${InventoryGridRows}",
            color = Color(0xFFB8B2A6)
        )
        Text(text = "悬浮查看详情，点击图标即可装备", color = Color(0xFF7B756B))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows.size) { rowIndex ->
                val rowItems = rows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        InventoryGridCell(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onEquipItem = onEquipItem
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPanel(player: PlayerStats) {
    val cards = player.cards
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("已获得卡牌 ${cards.size}", color = Color(0xFFB8B2A6))
        CardGridPanel(cards = cards)
    }
}

private fun buildGridSlots(items: List<EquipmentItem>): List<EquipmentItem?> {
    val trimmed = items.take(InventoryGridSize).map { it as EquipmentItem? }
    if (trimmed.size >= InventoryGridSize) return trimmed
    val slots = trimmed.toMutableList()
    repeat(InventoryGridSize - slots.size) {
        slots.add(null)
    }
    return slots
}

private fun buildCardGridSlots(cards: List<CardInstance>): List<CardInstance?> {
    val trimmed = cards.take(InventoryGridSize).map { it as CardInstance? }
    if (trimmed.size >= InventoryGridSize) return trimmed
    val slots = trimmed.toMutableList()
    repeat(InventoryGridSize - slots.size) {
        slots.add(null)
    }
    return slots
}

@Composable
private fun InventoryGridCell(
    item: EquipmentItem?,
    modifier: Modifier = Modifier,
    onEquipItem: (String) -> Unit
) {
    val color = item?.let { equipmentRarityColor(it.rarityTier, it.rarityId) } ?: Color(0xFF4A4A4A)
    val label = item?.let { equipmentSlotShortLabel(it.slot) } ?: "空"
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (item == null) {
            EquipmentRarityIcon(
                label = label,
                color = color,
                size = 40.dp
            )
        } else {
            HoverTooltipBox(
                logTag = "背包网格",
                logName = item.name,
                tooltip = {
                    EquipmentTooltipCard(
                        slot = item.slot,
                        item = item
                    )
                }
            ) { baseModifier ->
                val clickableModifier = baseModifier.clickable {
                    GameLogger.info("背包网格", "点击装备格：${item.name} uid=${item.uid}")
                    onEquipItem(item.uid)
                }
                EquipmentRarityIcon(
                    label = label,
                    color = color,
                    size = 40.dp,
                    modifier = clickableModifier
                )
            }
        }
    }
}

@Composable
private fun CardGridPanel(cards: List<CardInstance>) {
    val slots = buildCardGridSlots(cards)
    val rows = slots.chunked(InventoryGridColumns)
    Text(
        text = "网格 ${InventoryGridColumns}x${InventoryGridRows}，悬浮查看卡牌详情",
        color = Color(0xFF7B756B)
    )
    if (cards.isEmpty()) {
        Text(text = "暂无卡牌", color = Color(0xFF7B756B))
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows.size) { rowIndex ->
            val rowItems = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { card ->
                    CardGridCell(
                        card = card,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardGridCell(
    card: CardInstance?,
    modifier: Modifier = Modifier
) {
    val color = card?.let { cardQualityColor(it.quality) } ?: Color(0xFF4A4A4A)
    val label = card?.name?.take(1)?.ifBlank { "卡" } ?: "空"
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (card == null) {
            EquipmentRarityIcon(
                label = label,
                color = color,
                size = 40.dp
            )
        } else {
            HoverTooltipBox(
                logTag = "卡牌网格",
                logName = card.name,
                tooltip = {
                    CardTooltipCard(card = card)
                }
            ) { baseModifier ->
                EquipmentRarityIcon(
                    label = label,
                    color = color,
                    size = 40.dp,
                    modifier = baseModifier
                )
            }
        }
    }
}

@Composable
private fun CardTooltipCard(card: CardInstance) {
    val qualityColor = cardQualityColor(card.quality)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${card.name}（${cardQualityLabel(card.quality)}）",
                fontWeight = FontWeight.SemiBold,
                color = qualityColor
            )
            Text(
                text = "类型 ${if (card.isGood) "厉害" else "垃圾"}",
                color = Color(0xFFB8B2A6)
            )
            if (card.description.isNotBlank()) {
                Text(text = card.description, color = Color(0xFFB8B2A6))
            }
            val effectText = formatCardEffects(card.effects)
            if (effectText.isNotBlank()) {
                Text(text = "效果 $effectText", color = Color(0xFF8DB38B))
            }
        }
    }
}

@Composable
private fun CardSelectDialog(
    level: Int,
    options: List<CardInstance>,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "卡牌抉择 Lv$level") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "请选择 1 张卡牌，选择后立即生效。",
                    color = Color(0xFFB8B2A6)
                )
                options.forEach { card ->
                    val qualityColor = cardQualityColor(card.quality)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
                        border = BorderStroke(1.dp, qualityColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${card.name}（${cardQualityLabel(card.quality)}）",
                                fontWeight = FontWeight.SemiBold,
                                color = qualityColor
                            )
                            Text(
                                text = "类型 ${if (card.isGood) "厉害" else "垃圾"}",
                                color = Color(0xFFB8B2A6)
                            )
                            Text(text = card.description, color = Color(0xFFB8B2A6))
                            Text(
                                text = "效果 ${formatCardEffects(card.effects)}",
                                color = Color(0xFF8DB38B)
                            )
                            Button(onClick = { onSelect(card.uid) }, modifier = Modifier.fillMaxWidth()) {
                                Text("选择这张")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun EventActionPanel(
    choices: List<GameChoice>,
    onChoice: (String) -> Unit,
    onAdvance: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "事件选项", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            if (choices.isEmpty()) {
                PlaceholderPanel("暂无可选行动")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { choice ->
                        Button(
                            onClick = { onChoice(choice.id) },
                            enabled = choice.enabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(choice.label)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                Text("继续前进")
            }
        }
    }
}

@Composable
private fun ShopPanel(
    state: GameUiState,
    onToggleShopOfferSelection: (String) -> Unit,
    onToggleShopSellSelection: (String) -> Unit,
    onShopBuySelected: () -> Unit,
    onShopBuyPotion: () -> Unit,
    onShopSellSelected: () -> Unit,
    onShopLeave: () -> Unit
) {
    val offers = state.shopOffers
    val selectedOfferIds = state.shopSelectedOfferIds
    val selectedSellIds = state.shopSelectedSellIds
    val inventory = state.player.inventory
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "商品展示区", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                if (offers.isEmpty()) {
                    PlaceholderPanel("暂无可购买商品")
                } else {
                    val rows = offers.chunked(2)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows) { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { offer ->
                                    val selected = selectedOfferIds.contains(offer.id)
                                    ShopOfferCard(
                                        offer = offer,
                                        selected = selected,
                                        onToggle = { onToggleShopOfferSelection(offer.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.weight(0.9f)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "交易操作区", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(text = "购买合计：${state.shopBuyTotal} 金币", color = Color(0xFFB8B2A6))
                Text(text = "出售合计：${state.shopSellTotal} 金币", color = Color(0xFFB8B2A6))
                Text(text = "当前金币：${state.player.gold}", color = Color(0xFF8DB38B))
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(text = "补给区", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "药水数量：${state.player.potionCount}",
                    color = Color(0xFFB8B2A6)
                )
                Text(
                    text = "单价：${POTION_PRICE} 金币",
                    color = Color(0xFF7B756B)
                )
                Button(
                    onClick = onShopBuyPotion,
                    enabled = state.player.gold >= POTION_PRICE,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("购买${POTION_NAME}")
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Button(onClick = onShopBuySelected, modifier = Modifier.fillMaxWidth()) {
                    Text("购买选中")
                }
                Button(onClick = onShopSellSelected, modifier = Modifier.fillMaxWidth()) {
                    Text("卖出选中")
                }
                OutlinedButton(onClick = onShopLeave, modifier = Modifier.fillMaxWidth()) {
                    Text("离开商店")
                }
            }
        }
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "背包物品区", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "容量 ${inventory.items.size}/${inventory.capacity}",
                    color = Color(0xFFB8B2A6)
                )
                if (inventory.items.isEmpty()) {
                    PlaceholderPanel("背包空空如也")
                } else {
                    Text(text = "点击图标选择要出售的装备", color = Color(0xFF7B756B))
                    ShopInventoryIconGrid(
                        items = inventory.items,
                        selectedIds = selectedSellIds,
                        columns = 4,
                        iconSize = 36.dp,
                        onToggleSelected = onToggleShopSellSelection
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopOfferCard(
    offer: ShopOfferUiState,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val available = offer.stock > 0 && offer.lockedReason.isBlank()
    val borderColor = if (selected) Color(0xFF8DB38B) else Color(0xFF2C3B33)
    val rarityColor = equipmentRarityColor(offer.item.rarityTier, offer.item.rarityId)
    val cardAlpha = if (available) 1f else 0.5f
    Card(
        modifier = modifier
            .alpha(cardAlpha)
            .clickable(enabled = available) { onToggle() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .background(Color.Transparent)
                .heightIn(min = 84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoverTooltipBox(
                logTag = "商店装备",
                logName = offer.item.name,
                tooltip = {
                    EquipmentTooltipCard(
                        slot = offer.item.slot,
                        item = offer.item
                    )
                }
            ) { baseModifier ->
                EquipmentRarityIcon(
                    label = equipmentSlotShortLabel(offer.item.slot),
                    color = rarityColor,
                    size = 40.dp,
                    modifier = baseModifier
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${offer.item.name}（${offer.item.rarityName}）",
                    fontWeight = FontWeight.SemiBold,
                    color = rarityColor.copy(alpha = cardAlpha)
                )
                Text(
                    text = "价格 ${offer.price} 金币",
                    color = Color(0xFFB8B2A6).copy(alpha = cardAlpha)
                )
                val stockLabel = if (offer.stock > 0) "库存 ${offer.stock}" else "已售罄"
                Text(
                    text = stockLabel,
                    color = Color(0xFF7B756B).copy(alpha = cardAlpha)
                )
                if (offer.lockedReason.isNotBlank()) {
                    Text(
                        text = offer.lockedReason,
                        color = Color(0xFFD6B36A).copy(alpha = cardAlpha)
                    )
                }
                if (selected) {
                    Text(
                        text = "已选中",
                        color = Color(0xFF8DB38B)
                    )
                }
            }
        }
    }
}

@Composable
private fun BattleInfoPanel(
    player: PlayerStats,
    battle: BattleUiState?,
    enemyPreview: EnemyPreviewUiState?
) {
    val hit = (70 + player.speed + player.hitBonus).coerceIn(50, 98)
    val eva = (8 + player.speed / 2 + player.evaBonus).coerceIn(5, 45)
    val crit = (6 + player.speed / 3 + player.critBonus).coerceIn(5, 40)
    val resist = (3 + player.resistBonus).coerceIn(0, 50)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "战斗面板", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(
                    title = "我方情报",
                    lines = listOf(
                        "角色" to "${player.name}（Lv${player.level}）",
                        "生命" to "${player.hp}/${player.hpMax}",
                        "能量" to "${player.mp}/${player.mpMax}",
                        "攻击" to "${player.atk}",
                        "防御" to "${player.def}",
                        "速度" to "${player.speed}",
                        "命中" to "${hit}%",
                        "闪避" to "${eva}%",
                        "暴击" to "${crit}%",
                        "抗暴" to "${resist}%"
                    )
                )
                val enemyLines = buildEnemyInfoLines(battle, enemyPreview)
                InfoBlock(title = "敌人情报", lines = enemyLines)
            }
        }
    }
}

@Composable
private fun StageInfoPanel(
    state: GameUiState
) {
    val stage = state.stage
    val logPreview = state.log.takeLast(3).map { trimStageLog(it) }
    LaunchedEffect(
        stage.id,
        stage.nodeId,
        stage.visited,
        stage.total,
        state.log.size
    ) {
        GameLogger.info(
            "关卡信息",
            "刷新关卡信息：关卡=${stage.id} 节点=${stage.nodeId} 进度=${stage.visited}/${stage.total} 日志条数=${state.log.size}"
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "关卡信息", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            val stageName = if (stage.name.isNotBlank()) stage.name else "未知关卡"
            Text(
                text = "章节 ${state.chapter}/${state.totalChapters}  关卡 $stageName  进度 ${stage.visited}/${stage.total}",
                color = Color(0xFFB8B2A6)
            )
            if (stage.id.isNotBlank()) {
                Text(text = "关卡编号 ${stage.id}", color = Color(0xFF7B756B))
            }
            if (stage.nodeId.isNotBlank()) {
                Text(
                    text = "节点 ${stage.nodeId}  类型 ${nodeTypeLabel(stage.nodeType)}",
                    color = Color(0xFF7B756B)
                )
            }
            if (stage.command.isNotBlank()) {
                Text(text = "口令 ${stage.command}", color = Color(0xFF8DB38B))
            }
            if (stage.guardian.isNotBlank()) {
                Text(text = "守卫 ${stage.guardian}", color = Color(0xFFE8C07D))
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text(text = "日志摘要", fontWeight = FontWeight.SemiBold)
            if (logPreview.isEmpty()) {
                Text(text = "暂无日志", color = Color(0xFF7B756B))
            } else {
                logPreview.forEach { line ->
                    Text(text = "• $line", color = Color(0xFFB8B2A6))
                }
            }
        }
    }
}

private fun trimStageLog(text: String, maxLength: Int = 26): String {
    val normalized = text.replace("\n", " ").trim()
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
}

@Composable
private fun BattleOperationPanel(
    player: PlayerStats,
    choices: List<GameChoice>,
    onChoice: (String) -> Unit
) {
    val choiceMap = choices.associateBy { it.id }
    val logTag = "BattleOperationPanel"
    val slotIds = normalizeBattleSkillSlotsForUi(player.battleSkillSlots)
    LaunchedEffect(choices, player.potionCount, player.battleSkillSlots) {
        val attackEnabled = choiceMap[BATTLE_CHOICE_ATTACK]?.enabled == true
        val potion1Enabled = choiceMap[BATTLE_CHOICE_POTION_1]?.enabled == true
        val potion2Enabled = choiceMap[BATTLE_CHOICE_POTION_2]?.enabled == true
        val skillCount = slotIds.count { it.isNotBlank() }
        GameLogger.info(
            logTag,
            "战斗选项刷新：攻击=$attackEnabled 药水1=$potion1Enabled 药水2=$potion2Enabled 药水剩余=${player.potionCount} 技能槽=$skillCount"
        )
    }
    val onBattleChoiceClick: (GameChoice) -> Unit = { choice ->
        GameLogger.info(logTag, "点击战斗选项：id=${choice.id} label=${choice.label}")
        onChoice(choice.id)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "战斗操作", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = "基础动作", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val attack = choiceMap[BATTLE_CHOICE_ATTACK]
                BattleOptionTile(
                    title = "攻击",
                    subtitle = "普通攻击",
                    enabled = attack?.enabled == true,
                    accent = Color(0xFFE0A25E),
                    modifier = Modifier.size(BattleBaseTileWidth, BattleBaseTileHeight),
                    onClick = {
                        attack?.let(onBattleChoiceClick)
                    }
                )
                val potion1 = choiceMap[BATTLE_CHOICE_POTION_1]
                val (potion1Title, potion1Detail) = splitChoiceLabel(potion1?.label ?: "药水1")
                BattleOptionTile(
                    title = potion1Title.ifBlank { "药水1" },
                    subtitle = potion1Detail ?: "剩余${player.potionCount}",
                    enabled = potion1?.enabled == true,
                    accent = Color(0xFF6FBF73),
                    modifier = Modifier.size(BattleBaseTileWidth, BattleBaseTileHeight),
                    onClick = {
                        potion1?.let(onBattleChoiceClick)
                    }
                )
                val potion2 = choiceMap[BATTLE_CHOICE_POTION_2]
                val (potion2Title, potion2Detail) = splitChoiceLabel(potion2?.label ?: "药水2")
                BattleOptionTile(
                    title = potion2Title.ifBlank { "药水2" },
                    subtitle = potion2Detail ?: "剩余${player.potionCount}",
                    enabled = potion2?.enabled == true,
                    accent = Color(0xFF5DADE2),
                    modifier = Modifier.size(BattleBaseTileWidth, BattleBaseTileHeight),
                    onClick = {
                        potion2?.let(onBattleChoiceClick)
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val equipChoice = choiceMap[BATTLE_CHOICE_EQUIP]
                val (_, equipDetail) = splitChoiceLabel(equipChoice?.label ?: "换装备")
                BattleOptionTile(
                    title = "换装",
                    subtitle = equipDetail?.let { "模式$it" } ?: "装备切换",
                    enabled = equipChoice?.enabled == true,
                    accent = Color(0xFF8DB38B),
                    modifier = Modifier.size(BattleUtilityTileWidth, BattleUtilityTileHeight),
                    onClick = {
                        equipChoice?.let(onBattleChoiceClick)
                    }
                )
                val fleeChoice = choiceMap[BATTLE_CHOICE_FLEE]
                BattleOptionTile(
                    title = "撤离",
                    subtitle = "退出战斗",
                    enabled = fleeChoice?.enabled == true,
                    accent = Color(0xFFD16A6A),
                    modifier = Modifier.size(BattleUtilityTileWidth, BattleUtilityTileHeight),
                    onClick = {
                        fleeChoice?.let(onBattleChoiceClick)
                    }
                )
            }
            Text(text = "技能栏", fontWeight = FontWeight.SemiBold)
            if (slotIds.all { it.isBlank() }) {
                PlaceholderPanel("未配置战斗技能")
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slotIds.forEach { skillId ->
                        val choice = if (skillId.isNotBlank()) {
                            choiceMap[buildBattleSkillChoiceId(skillId)]
                        } else {
                            null
                        }
                        val (skillTitle, skillDetail) = if (choice != null) {
                            splitSkillChoiceLabel(choice.label)
                        } else {
                            "空" to "未配置"
                        }
                        val detailText = skillDetail ?: if (choice == null) "未配置" else "就绪"
                        BattleSkillTile(
                            title = skillTitle.ifBlank { "空" },
                            detail = detailText,
                            enabled = choice?.enabled == true,
                            modifier = Modifier.size(BattleSkillTileWidth, BattleSkillTileHeight),
                            onClick = {
                                choice?.let(onBattleChoiceClick)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BattleOptionTile(
    title: String,
    subtitle: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (enabled) Color(0xFF1A2A22) else Color(0xFF151C18)
    val borderColor = if (enabled) accent else Color(0xFF3A3A3A)
    val titleColor = if (enabled) Color(0xFFECE8D9) else Color(0xFF7B756B)
    val subtitleColor = if (enabled) Color(0xFFB8B2A6) else Color(0xFF5D5D5D)
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accent.copy(alpha = if (enabled) 0.9f else 0.35f))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BattleSkillTile(
    title: String,
    detail: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (enabled) Color(0xFF192B30) else Color(0xFF151C18)
    val borderColor = if (enabled) Color(0xFF5DADE2) else Color(0xFF3A3A3A)
    val titleColor = if (enabled) Color(0xFFE6EFF7) else Color(0xFF7B756B)
    val detailColor = if (enabled) Color(0xFFB8B2A6) else Color(0xFF5D5D5D)
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                color = detailColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        selected -> Color(0xFF8DB38B)
        enabled -> Color(0xFF315241)
        else -> Color(0xFF2C3B33)
    }
    val backgroundColor = if (enabled) Color(0xFF1A2520) else Color(0xFF1A2520).copy(alpha = 0.5f)
    val labelColor = if (enabled) Color(0xFFECE8D9) else Color(0xFF7B756B)
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, color = labelColor)
    }
}

@Composable
private fun RowScope.InfoBlock(title: String, lines: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
        modifier = Modifier.weight(1f)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            lines.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = Color(0xFF7B756B),
                        modifier = Modifier.width(52.dp)
                    )
                    Text(text = value, color = Color(0xFFB8B2A6))
                }
            }
        }
    }
}

private fun buildEnemyInfoLines(
    battle: BattleUiState?,
    enemyPreview: EnemyPreviewUiState?
): List<Pair<String, String>> {
    val lines = mutableListOf<Pair<String, String>>()
    if (battle != null) {
        lines += "名称" to battle.enemyName
        lines += "生命" to "${battle.enemyHp}"
        lines += "能量" to "${battle.enemyMp}"
    }
    if (enemyPreview != null) {
        lines += "等级" to "${enemyPreview.level}"
        lines += "数量" to "${enemyPreview.count}"
        lines += "攻击" to "${enemyPreview.atk}"
        lines += "防御" to "${enemyPreview.def}"
        lines += "速度" to "${enemyPreview.speed}"
    }
    if (lines.isEmpty()) {
        lines += "提示" to "暂无敌人情报"
    }
    return lines
}

@Composable
private fun SkillDetailCard(title: String, skill: RoleSkill, showFormula: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "$title：${skill.name}", fontWeight = FontWeight.SemiBold)
            Text(
                text = "类型 ${skillTypeLabel(skill.type)}  目标 ${skill.target}",
                color = Color(0xFFB8B2A6)
            )
            if (skill.cost != "-" || skill.cooldown != "-") {
                val costText = if (skill.cost == "-") "无" else skill.cost
                val cooldownText = if (skill.cooldown == "-") "无" else skill.cooldown
                Text(text = "消耗 $costText  冷却 $cooldownText", color = Color(0xFFB8B2A6))
            }
            Text(text = skill.description, color = Color(0xFFB8B2A6))
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text(text = "效果", fontWeight = FontWeight.SemiBold)
            skill.effectLines.forEach { line ->
                Text(text = "• $line", color = Color(0xFF8DB38B))
            }
            if (showFormula && skill.formulaLines.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(text = "伤害公式", fontWeight = FontWeight.SemiBold)
                skill.formulaLines.forEach { line ->
                    Text(text = "• $line", color = Color(0xFFD6B36A))
                }
            }
        }
    }
}

private fun formatStatusLine(status: StatusInstance): String {
    val stackLabel = if (status.stacks > 1) "x${status.stacks}" else ""
    val turnsLabel = "剩余${status.remainingTurns}回合"
    val potencyLabel = if (status.potency > 0.0) {
        val percent = (status.potency * 100).toInt()
        "强度${percent}%"
    } else {
        ""
    }
    return listOf("${statusTypeLabel(status.type)}$stackLabel", turnsLabel, potencyLabel)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun statusTypeLabel(type: StatusType): String {
    return when (type) {
        StatusType.POISON -> "中毒"
        StatusType.BLEED -> "流血"
        StatusType.STUN -> "眩晕"
        StatusType.SHIELD -> "护盾"
        StatusType.HASTE -> "加速"
        StatusType.SLOW -> "减速"
    }
}

private fun statusColor(type: StatusType): Color {
    return when (type) {
        StatusType.POISON -> Color(0xFF6FBF73)
        StatusType.BLEED -> Color(0xFFD16A6A)
        StatusType.STUN -> Color(0xFFF0C36A)
        StatusType.SHIELD -> Color(0xFF5DADE2)
        StatusType.HASTE -> Color(0xFF8DB38B)
        StatusType.SLOW -> Color(0xFF8F8F8F)
    }
}

@Composable
private fun SettingsPanelCard(
    showSkillFormula: Boolean,
    onToggleShowSkillFormula: (Boolean) -> Unit,
    codexState: GameUiState? = null,
    onSelectCodexTab: ((CodexTab) -> Unit)? = null,
    onReturnToMain: (() -> Unit)? = null,
    onOpenChapterSelect: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "设置面板", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            if (onReturnToMain != null && onOpenChapterSelect != null) {
                Text(text = "主界面操作", fontWeight = FontWeight.SemiBold)
                Text("返回主界面可重新选择存档与角色。", color = Color(0xFFB8B2A6))
                Button(onClick = onReturnToMain, modifier = Modifier.fillMaxWidth()) {
                    Text("返回主界面")
                }
                Text("切换章节时可进入关卡选择。", color = Color(0xFFB8B2A6))
                Button(onClick = onOpenChapterSelect, modifier = Modifier.fillMaxWidth()) {
                    Text("关卡选择")
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "技能描述显示伤害公式", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (showSkillFormula) "已开启，显示伤害计算提示" else "已关闭，仅显示效果描述",
                        color = Color(0xFFB8B2A6)
                    )
                }
                Switch(
                    checked = showSkillFormula,
                    onCheckedChange = onToggleShowSkillFormula
                )
            }
            if (codexState != null && onSelectCodexTab != null) {
                var showCodex by remember { mutableStateOf(false) }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(text = "图鉴面板", fontWeight = FontWeight.SemiBold)
                Text(text = "图鉴已移入设置面板，可在此展开查看。", color = Color(0xFFB8B2A6))
                Button(onClick = { showCodex = !showCodex }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (showCodex) "收起图鉴" else "展开图鉴")
                }
                if (showCodex) {
                    CodexPanel(
                        state = codexState,
                        onSelectCodexTab = onSelectCodexTab
                    )
                }
            }
        }
    }
}

private fun skillTypeLabel(raw: String): String {
    return when (raw.uppercase()) {
        "PASSIVE" -> "被动"
        "ACTIVE" -> "主动"
        "ULTIMATE" -> "终极"
        else -> raw
    }
}

private fun skillTypeColor(raw: String): Color {
    return when (raw.uppercase()) {
        "PASSIVE" -> Color(0xFF6FBF73)
        "ACTIVE" -> Color(0xFF5DADE2)
        "ULTIMATE" -> Color(0xFFF39C12)
        else -> Color(0xFF8DB38B)
    }
}

private fun skillIconPath(rawType: String, locked: Boolean): String {
    if (locked) {
        return "icons/skills/locked.png"
    }
    return when (rawType.uppercase()) {
        "PASSIVE" -> "icons/skills/passive.png"
        "ACTIVE" -> "icons/skills/active.png"
        "ULTIMATE" -> "icons/skills/ultimate.png"
        else -> "icons/skills/active.png"
    }
}

@Composable
private fun rememberSkillIconPainter(path: String, logTag: String): Painter? {
    val reader = LocalResourceReader.current
    val bytes = remember(path) {
        try {
            val data = reader.readBytes(path)
            GameLogger.info(logTag, "读取图标资源：$path 字节=${data.size}")
            data
        } catch (e: Exception) {
            GameLogger.warn(logTag, "读取图标资源失败：$path 异常=${e.message}")
            null
        }
    }
    val image = remember(path, bytes) { bytes?.let { decodeImageBitmap(it) } }
    return image?.let { remember(path) { BitmapPainter(it) } }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HoverTooltipBox(
    logTag: String,
    logName: String,
    tooltip: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var cursorOffset by remember { mutableStateOf(Offset.Zero) }
    val positionProvider = remember(cursorOffset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val offsetX = anchorBounds.left + cursorOffset.x.roundToInt() + 12
                val offsetY = anchorBounds.top + cursorOffset.y.roundToInt() + 12
                return IntOffset(offsetX, offsetY)
            }
        }
    }
    Box(
        modifier = Modifier
            .pointerMoveFilter(
                onMove = { offset ->
                    cursorOffset = offset
                    false
                },
                onEnter = {
                    if (!hovered) {
                        GameLogger.info(logTag, "鼠标进入：$logName")
                    }
                    hovered = true
                    false
                },
                onExit = {
                    if (hovered) {
                        GameLogger.info(logTag, "鼠标离开：$logName")
                    }
                    hovered = false
                    false
                }
            )
    ) {
        content(Modifier)
    }
    if (hovered) {
        Box(modifier = Modifier.size(0.dp)) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    tooltip()
                }
            }
        }
    }
}

private fun nodeTypeLabel(raw: String): String {
    return when (raw.uppercase()) {
        "EVENT" -> "事件"
        "BATTLE" -> "战斗"
        "SHOP" -> "商店"
        "TRAP" -> "陷阱"
        "REST" -> "休息"
        else -> raw
    }
}

private fun isShopEventUi(event: EventDefinition?): Boolean {
    if (event == null) return false
    val type = event.type.lowercase()
    return type.contains("shop") || event.type.contains("商店")
}

private fun splitChoiceLabel(raw: String): Pair<String, String?> {
    val label = raw.trim()
    val openIndex = label.indexOf('（')
    val closeIndex = label.lastIndexOf('）')
    if (openIndex >= 0 && closeIndex > openIndex) {
        val title = label.substring(0, openIndex).trim()
        val detail = label.substring(openIndex + 1, closeIndex).trim()
        return title to detail
    }
    return label to null
}

private fun splitSkillChoiceLabel(raw: String): Pair<String, String?> {
    val cleaned = raw.removePrefix("技能：").trim()
    return splitChoiceLabel(cleaned)
}

private fun slotLabel(slot: EquipmentSlot): String {
    return when (slot) {
        EquipmentSlot.WEAPON -> "武器"
        EquipmentSlot.ARMOR -> "护甲"
        EquipmentSlot.HELM -> "头盔"
        EquipmentSlot.ACCESSORY -> "饰品"
    }
}

private fun statLabel(stat: StatType): String {
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

private fun cardQualityLabel(quality: CardQuality): String {
    return when (quality) {
        CardQuality.COMMON -> "普通"
        CardQuality.UNCOMMON -> "优秀"
        CardQuality.RARE -> "稀有"
        CardQuality.EPIC -> "史诗"
        CardQuality.LEGEND -> "传说"
    }
}

private fun cardQualityColor(quality: CardQuality): Color {
    return when (quality) {
        CardQuality.COMMON -> Color(0xFF8F8F8F)
        CardQuality.UNCOMMON -> Color(0xFF6FBF73)
        CardQuality.RARE -> Color(0xFF5DADE2)
        CardQuality.EPIC -> Color(0xFFF39C12)
        CardQuality.LEGEND -> Color(0xFFF5C542)
    }
}

private fun formatStats(stats: Map<StatType, Int>): String {
    if (stats.isEmpty()) return "无"
    return stats.entries.joinToString(" ") { "${statLabel(it.key)}+${it.value}" }
}

private fun formatAffixes(affixes: List<EquipmentAffix>): String {
    if (affixes.isEmpty()) return "无"
    return affixes.joinToString(" ") { "${statLabel(it.type)}+${it.value}" }
}

private fun formatAffixLine(affix: EquipmentAffix): String {
    val value = if (affix.value >= 0) "+${affix.value}" else affix.value.toString()
    return "${statLabel(affix.type)}$value"
}

private fun formatCardEffects(effects: List<CardEffect>): String {
    if (effects.isEmpty()) return "无"
    return effects.joinToString("，") { effect ->
        val value = if (effect.value >= 0) "+${effect.value}" else effect.value.toString()
        "${statLabel(effect.stat)}$value"
    }
}

private fun formatSkillEffects(effects: List<SkillEffect>): String {
    if (effects.isEmpty()) return "无"
    return effects.joinToString("，") { effect ->
        val value = effect.value?.let { num -> if (num >= 0) "+$num" else num.toString() } ?: ""
        val scaling = effect.scaling?.let { "(${it})" } ?: ""
        val note = effect.note?.let { "[$it]" } ?: ""
        "${effect.type}$value$scaling$note"
    }
}

private fun formatEnemyStats(stats: EnemyStats): String {
    return "生命${stats.hp} 攻击${stats.atk} 防御${stats.def} 速度${stats.spd} 命中${stats.hit} 闪避${stats.eva} 暴击${stats.crit} 抗暴${stats.resist}"
}

private fun formatEnemySkill(skill: EnemySkillDefinition): String {
    val chancePercent = (skill.chance * 100).toInt()
    val parts = mutableListOf<String>()
    parts += "${skill.name} 冷却${skill.cooldown} 触发${chancePercent}%"
    skill.damageMultiplier?.let { parts += "伤害倍率 ${"%.2f".format(it)}" }
    if (skill.healRate > 0.0) {
        parts += "治疗 ${(skill.healRate * 100).toInt()}%"
    }
    if (!skill.statusType.isNullOrBlank()) {
        val statusLabel = skill.statusType
        val statusLine = "附带${statusLabel} ${skill.statusTurns}回合"
        parts += statusLine
    }
    if (skill.note.isNotBlank()) {
        parts += skill.note
    }
    return parts.joinToString(" | ")
}

private fun equipmentRarityColor(rarityTier: Int, rarityId: String): Color {
    val id = rarityId.lowercase()
    return when {
        id.contains("legend") || rarityTier >= 5 -> Color(0xFFF5C542)
        id.contains("epic") || rarityTier == 4 -> Color(0xFFF39C12)
        id.contains("rare") || rarityTier == 3 -> Color(0xFF5DADE2)
        id.contains("uncommon") || rarityTier == 2 -> Color(0xFF6FBF73)
        else -> Color(0xFF8F8F8F)
    }
}

private fun equipmentAffixColor(rarityTier: Int, rarityId: String): Color {
    val base = equipmentRarityColor(rarityTier, rarityId)
    return base.copy(alpha = 0.9f)
}

private fun equipmentLevelColor(level: Int): Color {
    return when {
        level >= 20 -> Color(0xFFD35400)
        level >= 12 -> Color(0xFFB9770E)
        level >= 8 -> Color(0xFF2E86C1)
        level >= 4 -> Color(0xFF27AE60)
        else -> Color(0xFFB8B2A6)
    }
}

@Composable
private fun PlaceholderPanel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF7B756B))
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { (label, value) ->
                    InfoCell(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(76.dp),
            color = Color(0xFF7B756B)
        )
        Text(text = value, color = Color(0xFFB8B2A6))
    }
}
