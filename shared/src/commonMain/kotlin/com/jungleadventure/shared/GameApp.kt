package com.jungleadventure.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jungleadventure.shared.loot.EquipmentSlot
import com.jungleadventure.shared.loot.StatType

@Composable
fun GameApp(
    resourceReader: ResourceReader,
    saveStore: SaveStore = defaultSaveStore(),
    viewModel: GameViewModel = remember {
        GameViewModel(resourceReader, saveStore)
    }
) {
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1A14))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderBar(state)
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
                    onOpenStatus = viewModel::onOpenStatus,
                    onOpenEquipment = viewModel::onOpenEquipment,
                    onOpenInventory = viewModel::onOpenInventory,
                    onOpenCards = viewModel::onOpenCards,
                    showSkillFormula = state.showSkillFormula,
                    onToggleShowSkillFormula = viewModel::onToggleShowSkillFormula
                )
                SidePanel(
                    modifier = Modifier.weight(0.8f),
                    state = state,
                    onChoice = viewModel::onSelectChoice,
                    onOpenStatus = viewModel::onOpenStatus,
                    onOpenEquipment = viewModel::onOpenEquipment,
                    onOpenInventory = viewModel::onOpenInventory,
                    onOpenCards = viewModel::onOpenCards,
                    onOpenSkills = viewModel::onOpenSkills,
                    onEquipItem = viewModel::onEquipItem,
                    onUnequipSlot = viewModel::onUnequipSlot,
                    onSave = viewModel::onSave,
                    onLoad = viewModel::onLoad,
                    onReturnToMain = viewModel::onReturnToMain,
                    onOpenChapterSelect = viewModel::onOpenChapterSelect,
                    showSkillFormula = state.showSkillFormula,
                    onToggleShowSkillFormula = viewModel::onToggleShowSkillFormula
                )
            }
        }
        if (state.showDialog) {
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
private fun HeaderBar(state: GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = state.title,
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
        Text(
            text = state.lastAction.ifBlank { "准备行动" },
            color = Color(0xFF8DB38B)
        )
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
    onOpenStatus: () -> Unit,
    onOpenEquipment: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCards: () -> Unit,
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
            GameScreen.ADVENTURE -> {
                if (state.battle != null) {
                    BattleInfoPanel(
                        player = state.player,
                        battle = state.battle,
                        enemyPreview = state.enemyPreview
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "当前事件", fontWeight = FontWeight.Bold)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        if (state.currentEvent == null) {
                            Text("事件加载中...")
                        } else {
                            Text(state.currentEvent.title, fontWeight = FontWeight.SemiBold)
                            Text("类型 ${state.currentEvent.type}  难度 ${state.currentEvent.difficulty}")
                            if (state.stage.id.isNotBlank()) {
                                Text("节点 ${state.stage.nodeId}  类型 ${nodeTypeLabel(state.stage.nodeType)}")
                                if (state.stage.guardian.isNotBlank()) {
                                    Text("守卫：${state.stage.guardian}", color = Color(0xFFE8C07D))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            state.battle?.let { battle ->
                                Text("战斗回合 ${battle.round} | ${battle.enemyName}")
                                Text("敌方生命 ${battle.enemyHp}  能量 ${battle.enemyMp}  |  装备 ${battle.equipmentMode}")
                                Text("我方生命 ${battle.playerHp}  能量 ${battle.playerMp}  技能冷却 ${battle.skillCooldown}")
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(state.currentEvent.introText)
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "事件日志", fontWeight = FontWeight.Bold)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        val listState = rememberLazyListState()
                        LaunchedEffect(state.log.size) {
                            if (state.log.isNotEmpty()) {
                                try {
                                    listState.animateScrollToItem(state.log.size - 1)
                                } catch (_: CancellationException) {
                                }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(state.log) { line ->
                                Text(text = "- $line")
                            }
                        }
                    }
                }
                if (state.battle == null) {
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

        Divider()
        Text(text = "成长（每级）", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleStat(label = "生命", value = selectedRole.growth.hpMax)
            RoleStat(label = "能量", value = selectedRole.growth.mpMax)
            RoleStat(label = "攻击", value = selectedRole.growth.atk)
            RoleStat(label = "防御", value = selectedRole.growth.def)
            RoleStat(label = "速度", value = selectedRole.growth.speed)
        }

        Divider()
        Text(text = "技能组合", fontWeight = FontWeight.SemiBold)
        SkillDetailCard(
            title = "被动技能",
            skill = selectedRole.passiveSkill,
            showFormula = showSkillFormula
        )
        if (selectedRole.activeSkills.isEmpty()) {
            Text(text = "主动技能：暂无", color = Color(0xFFB8B2A6))
        } else {
            selectedRole.activeSkills.forEachIndexed { index, skill ->
                SkillDetailCard(
                    title = "主动技能 ${index + 1}",
                    skill = skill,
                    showFormula = showSkillFormula
                )
            }
        }
        SkillDetailCard(
            title = "终极技能",
            skill = selectedRole.ultimateSkill,
            showFormula = showSkillFormula
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
    onChoice: (String) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenEquipment: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSkills: () -> Unit,
    onEquipItem: (String) -> Unit,
    onUnequipSlot: (EquipmentSlot) -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onReturnToMain: () -> Unit,
    onOpenChapterSelect: () -> Unit,
    showSkillFormula: Boolean,
    onToggleShowSkillFormula: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "主界面", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text("返回主界面可以重新选择存档与角色。", color = Color(0xFFB8B2A6))
                Button(onClick = onReturnToMain, modifier = Modifier.fillMaxWidth()) {
                    Text("返回主界面")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("需要切换章节时可进入关卡选择。", color = Color(0xFFB8B2A6))
                Button(onClick = onOpenChapterSelect, modifier = Modifier.fillMaxWidth()) {
                    Text("关卡选择")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "快捷面板", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenStatus) { Text("状态") }
                    Button(onClick = onOpenEquipment) { Text("装备") }
                    Button(onClick = onOpenInventory) { Text("背包") }
                    Button(onClick = onOpenCards) { Text("卡牌") }
                    Button(onClick = onOpenSkills) { Text("技能") }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = when (state.activePanel) {
                    GamePanel.STATUS -> "角色状态"
                    GamePanel.EQUIPMENT -> "当前装备"
                    GamePanel.INVENTORY -> "背包物品"
                    GamePanel.CARDS -> "卡牌收藏"
                    GamePanel.SKILLS -> "角色技能"
                }, fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                when (state.activePanel) {
                    GamePanel.STATUS -> StatusPanel(state.player)
                    GamePanel.EQUIPMENT -> EquipmentPanel(
                        player = state.player,
                        onUnequip = onUnequipSlot
                    )
                    GamePanel.INVENTORY -> InventoryPanel(
                        player = state.player,
                        onEquipItem = onEquipItem
                    )
                    GamePanel.CARDS -> CardPanel(player = state.player)
                    GamePanel.SKILLS -> SkillPanel(
                        role = state.roles.firstOrNull { it.id == state.selectedRoleId }
                            ?: state.roles.firstOrNull { it.unlocked },
                        showSkillFormula = showSkillFormula
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.battle == null) {
                    Text(text = "敌人情报", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    if (state.enemyPreview == null) {
                        PlaceholderPanel("暂无敌人情报")
                    } else {
                        EnemyPreviewPanel(preview = state.enemyPreview)
                    }
                } else {
                    BattleOperationPanel(
                        choices = state.choices,
                        onChoice = onChoice,
                        onOpenStatus = onOpenStatus,
                        onOpenEquipment = onOpenEquipment,
                        onOpenInventory = onOpenInventory,
                        onOpenCards = onOpenCards,
                        onOpenSkills = onOpenSkills
                    )
                }
            }
        }
        SettingsPanelCard(
            showSkillFormula = showSkillFormula,
            onToggleShowSkillFormula = onToggleShowSkillFormula
        )
    }
}

@Composable
private fun StatusPanel(player: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${player.name}  等级 ${player.level}")
        Text("经验 ${player.exp}/${player.expToNext}")
        Text("生命 ${player.hp}/${player.hpMax}")
        Text("能量 ${player.mp}/${player.mpMax}")
        Text("攻击 ${player.atk}  防御 ${player.def}")
        Text("速度 ${player.speed}")
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
                Text(text = "${slotLabel(slot)}：${item?.name ?: "空"}", fontWeight = FontWeight.SemiBold)
                if (item != null) {
                    Text(
                        text = "稀有度 ${item.rarityName} | 等级 ${item.level} | 评分 ${item.score}",
                        color = Color(0xFFB8B2A6)
                    )
                    Text(text = "属性 ${formatStats(item.totalStats())}", color = Color(0xFFB8B2A6))
                    if (item.affixes.isNotEmpty()) {
                        Text(text = "词条 ${formatAffixes(item.affixes)}", color = Color(0xFF8DB38B))
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
private fun InventoryPanel(
    player: PlayerStats,
    onEquipItem: (String) -> Unit
) {
    val inventory = player.inventory
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("容量 ${inventory.items.size}/${inventory.capacity}", color = Color(0xFFB8B2A6))
        if (inventory.items.isEmpty()) {
            PlaceholderPanel("背包空空如也")
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(inventory.items, key = { it.uid }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "${item.name}（${item.rarityName}）", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "部位 ${slotLabel(item.slot)} | 等级 ${item.level} | 评分 ${item.score}",
                            color = Color(0xFFB8B2A6)
                        )
                        Text(text = "属性 ${formatStats(item.totalStats())}", color = Color(0xFFB8B2A6))
                        if (item.affixes.isNotEmpty()) {
                            Text(text = "词条 ${formatAffixes(item.affixes)}", color = Color(0xFF8DB38B))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onEquipItem(item.uid) }) { Text("装备") }
                        }
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
        if (cards.isEmpty()) {
            PlaceholderPanel("暂无卡牌")
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards, key = { it.uid }) { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "${card.name}（${cardQualityLabel(card.quality)}）", fontWeight = FontWeight.SemiBold)
                        Text(text = card.description, color = Color(0xFFB8B2A6))
                        Text(
                            text = "类型 ${if (card.isGood) "厉害" else "垃圾"} | 效果 ${formatCardEffects(card.effects)}",
                            color = Color(0xFF8DB38B)
                        )
                    }
                }
            }
        }
    }
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
                        Button(onClick = { onChoice(choice.id) }, modifier = Modifier.fillMaxWidth()) {
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
private fun BattleInfoPanel(
    player: PlayerStats,
    battle: BattleUiState?,
    enemyPreview: EnemyPreviewUiState?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "战斗面板", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(
                    title = "我方情报",
                    lines = listOf(
                        "${player.name}  等级 ${player.level}",
                        "生命 ${player.hp}/${player.hpMax}  能量 ${player.mp}/${player.mpMax}",
                        "攻击 ${player.atk}  防御 ${player.def}  速度 ${player.speed}"
                    )
                )
                val enemyLines = buildEnemyInfoLines(battle, enemyPreview)
                InfoBlock(
                    title = "敌人情报",
                    lines = enemyLines
                )
            }
        }
    }
}

@Composable
private fun BattleOperationPanel(
    choices: List<GameChoice>,
    onChoice: (String) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenEquipment: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSkills: () -> Unit
) {
    val choiceMap = choices.associateBy { it.id }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "战斗操作", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionIconButton(
                    label = "攻击",
                    enabled = choiceMap.containsKey("battle_attack"),
                    onClick = { onChoice("battle_attack") }
                )
                ActionIconButton(
                    label = "技能",
                    enabled = choiceMap.containsKey("battle_skill"),
                    onClick = { onChoice("battle_skill") }
                )
                ActionIconButton(
                    label = "药丸",
                    enabled = choiceMap.containsKey("battle_item"),
                    onClick = { onChoice("battle_item") }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionIconButton(
                    label = "换装",
                    enabled = choiceMap.containsKey("battle_equip"),
                    onClick = { onChoice("battle_equip") }
                )
                ActionIconButton(
                    label = "撤离",
                    enabled = choiceMap.containsKey("battle_flee"),
                    onClick = { onChoice("battle_flee") }
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = "快捷面板", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionIconButton(label = "状态", enabled = true, onClick = onOpenStatus)
                ActionIconButton(label = "装备", enabled = true, onClick = onOpenEquipment)
                ActionIconButton(label = "背包", enabled = true, onClick = onOpenInventory)
                ActionIconButton(label = "卡牌", enabled = true, onClick = onOpenCards)
                ActionIconButton(label = "技能", enabled = true, onClick = onOpenSkills)
            }
        }
    }
}

@Composable
private fun RowScope.ActionIconButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(96.dp)
            .height(44.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun RowScope.InfoBlock(title: String, lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182720)),
        modifier = Modifier.weight(1f)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(text = line, color = Color(0xFFB8B2A6))
            }
        }
    }
}

private fun buildEnemyInfoLines(
    battle: BattleUiState?,
    enemyPreview: EnemyPreviewUiState?
): List<String> {
    val lines = mutableListOf<String>()
    if (battle != null) {
        lines += "${battle.enemyName}"
        lines += "生命 ${battle.enemyHp}  能量 ${battle.enemyMp}"
    }
    if (enemyPreview != null) {
        lines += "等级 ${enemyPreview.level}  数量 ${enemyPreview.count}"
        lines += "攻击 ${enemyPreview.atk}  防御 ${enemyPreview.def}  速度 ${enemyPreview.speed}"
    }
    if (lines.isEmpty()) {
        lines += "暂无敌人情报"
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

@Composable
private fun SettingsPanelCard(
    showSkillFormula: Boolean,
    onToggleShowSkillFormula: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "设置面板", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
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

private fun formatStats(stats: Map<StatType, Int>): String {
    if (stats.isEmpty()) return "无"
    return stats.entries.joinToString(" ") { "${statLabel(it.key)}+${it.value}" }
}

private fun formatAffixes(affixes: List<EquipmentAffix>): String {
    if (affixes.isEmpty()) return "无"
    return affixes.joinToString(" ") { "${statLabel(it.type)}+${it.value}" }
}

private fun formatCardEffects(effects: List<CardEffect>): String {
    if (effects.isEmpty()) return "无"
    return effects.joinToString("，") { effect ->
        val value = if (effect.value >= 0) "+${effect.value}" else effect.value.toString()
        "${statLabel(effect.stat)}$value"
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
