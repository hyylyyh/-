package com.jungleadventure.shared

import com.jungleadventure.shared.loot.EquipmentSlot
import com.jungleadventure.shared.loot.StatType

enum class CodexTab {
    SKILL,
    EQUIPMENT,
    MONSTER
}

data class SkillCatalogEntry(
    val id: String,
    val name: String,
    val type: String,
    val target: String,
    val cost: Int,
    val cooldown: Int,
    val description: String,
    val effects: List<SkillEffect> = emptyList(),
    val sourceRoleIds: List<String> = emptyList(),
    val sourceRoleNames: List<String> = emptyList()
)

data class MonsterCatalogEntry(
    val id: String,
    val name: String,
    val type: String,
    val level: Int,
    val stats: EnemyStats,
    val skills: List<EnemySkillDefinition> = emptyList(),
    val notes: String = "",
    val appearances: List<String> = emptyList(),
    val dropHint: String = ""
)

data class EquipmentCatalogEntry(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val rarityId: String,
    val rarityName: String,
    val rarityTier: Int,
    val levelReq: Int,
    val baseStats: Map<StatType, Int>,
    val affixMin: Int,
    val affixMax: Int,
    val enhanceMax: Int,
    val sellValue: Int,
    val salvageYield: Int
)
