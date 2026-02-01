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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GameApp(resourceReader: ResourceReader, viewModel: GameViewModel = remember {
    GameViewModel(resourceReader)
}) {
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
                    onSelectRole = viewModel::onSelectRole
                )
                SidePanel(
                    modifier = Modifier.weight(0.8f),
                    state = state,
                    onOpenStatus = viewModel::onOpenStatus,
                    onOpenEquipment = viewModel::onOpenEquipment,
                    onOpenInventory = viewModel::onOpenInventory
                )
            }
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
            Text(
                text = "回合 ${state.turn}  |  章节 ${state.chapter}",
                color = Color(0xFFB8B2A6)
            )
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
    onSelectRole: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "选择角色", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                RoleSelectionPanel(state = state, onSelectRole = onSelectRole)
            }
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(state.currentEvent.introText)
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "事件日志", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(state.log) { line ->
                        Text(text = "- $line")
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "可选行动", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.choices.forEach { choice ->
                        Button(onClick = { onChoice(choice.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(choice.label)
                        }
                    }
                    Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                        Text("继续前进")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionPanel(state: GameUiState, onSelectRole: (String) -> Unit) {
    if (state.roles.isEmpty()) {
        Text("角色数据加载中...")
        return
    }

    val selectedRole = state.roles.firstOrNull { it.id == state.selectedRoleId }
        ?: state.roles.firstOrNull { it.unlocked }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.weight(0.45f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "可选名单", fontWeight = FontWeight.SemiBold)
            state.roles.forEach { role ->
                val isSelected = role.id == state.selectedRoleId
                val label = when {
                    !role.unlocked -> "${role.name} · 未解锁"
                    isSelected -> "${role.name} · 已选择"
                    else -> role.name
                }
                Button(
                    onClick = { onSelectRole(role.id) },
                    enabled = role.unlocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label)
                }
                Text(text = role.title, color = Color(0xFF7B756B))
            }
        }
        Column(
            modifier = Modifier.weight(0.55f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedRole == null) {
                Text("暂无可用角色")
            } else {
                Text(text = "角色特长", fontWeight = FontWeight.SemiBold)
                Text(selectedRole.specialty)

                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text(text = "等级属性成长", fontWeight = FontWeight.SemiBold)
                selectedRole.growth.forEach { stat ->
                    Text("${stat.label} +${stat.perLevel}/级")
                }

                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text(text = "装备特长", fontWeight = FontWeight.SemiBold)
                Text(selectedRole.equipmentTrait)

                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text(text = "角色技能特长", fontWeight = FontWeight.SemiBold)
                Text(selectedRole.skillTrait)

                Divider(modifier = Modifier.padding(vertical = 6.dp))
                Text(text = "技能详情", fontWeight = FontWeight.SemiBold)
                Text("被动：${selectedRole.passiveSkill.name}")
                Text(selectedRole.passiveSkill.description)
                Text("主动：${selectedRole.activeSkill.name}")
                Text(
                    "${selectedRole.activeSkill.description}（${selectedRole.activeSkill.cost}，${selectedRole.activeSkill.cooldown}）"
                )
            }
        }
    }
}

@Composable
private fun SidePanel(
    modifier: Modifier,
    state: GameUiState,
    onOpenStatus: () -> Unit,
    onOpenEquipment: () -> Unit,
    onOpenInventory: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "快捷面板", fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenStatus) { Text("状态") }
                    Button(onClick = onOpenEquipment) { Text("装备") }
                    Button(onClick = onOpenInventory) { Text("背包") }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = when (state.activePanel) {
                    GamePanel.STATUS -> "角色状态"
                    GamePanel.EQUIPMENT -> "当前装备"
                    GamePanel.INVENTORY -> "背包物品"
                }, fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                when (state.activePanel) {
                    GamePanel.STATUS -> StatusPanel(state.player)
                    GamePanel.EQUIPMENT -> PlaceholderPanel("暂无装备")
                    GamePanel.INVENTORY -> PlaceholderPanel("暂无物品")
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(player: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${player.name}  Lv.${player.level}")
        Text("HP ${player.hp}/${player.hpMax}")
        Text("MP ${player.mp}/${player.mpMax}")
        Text("ATK ${player.atk}  DEF ${player.def}")
        Text("速度 ${player.speed}")
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Text("金币 ${player.gold}")
            Spacer(modifier = Modifier.width(12.dp))
            Text("材料 ${player.materials}")
        }
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
