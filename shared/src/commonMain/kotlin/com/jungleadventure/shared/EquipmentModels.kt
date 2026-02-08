package com.jungleadventure.shared

import com.jungleadventure.shared.loot.EquipmentSlot
import com.jungleadventure.shared.loot.StatType
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBaseStats(
    val hpMax: Int,
    val mpMax: Int = 30,
    val atk: Int,
    val def: Int,
    val speed: Int,
    val strength: Int = 0,
    val intelligence: Int = 0,
    val agility: Int = 0
)

@Serializable
data class EquipmentAffix(
    val id: String,
    val type: StatType,
    val value: Int
)

@Serializable
data class EquipmentItem(
    val uid: String,
    val templateId: String,
    val name: String,
    val slot: EquipmentSlot,
    val rarityId: String,
    val rarityName: String,
    val rarityTier: Int,
    val level: Int,
    val stats: Map<StatType, Int> = emptyMap(),
    val affixes: List<EquipmentAffix> = emptyList(),
    val score: Int = 0,
    val source: String = "",
    val obtainedAtTurn: Int = 0
)

@Serializable
data class EquipmentLoadout(
    val slots: Map<EquipmentSlot, EquipmentItem> = emptyMap()
) {
    fun equipped(slot: EquipmentSlot): EquipmentItem? = slots[slot]
}

@Serializable
data class InventoryState(
    val capacity: Int = 20,
    val items: List<EquipmentItem> = emptyList()
)

fun EquipmentItem.totalStats(): Map<StatType, Int> {
    val totals = stats.toMutableMap()
    affixes.forEach { affix ->
        totals[affix.type] = (totals[affix.type] ?: 0) + affix.value
    }
    return totals
}

fun estimateEquipmentSellValue(item: EquipmentItem): Int {
    return (4 + item.rarityTier * 4 + item.level).coerceAtLeast(1)
}

fun formatEquipmentSourceLabel(source: String): String {
    val trimmed = source.trim()
    if (trimmed.isBlank()) return ""
    val tierMatch = Regex("^(enemy|event|shop)_tier_(\\d+)$").find(trimmed)
    if (tierMatch != null) {
        val type = tierMatch.groupValues[1]
        val tier = tierMatch.groupValues[2]
        val prefix = when (type) {
            "enemy" -> "敌人掉落"
            "event" -> "事件掉落"
            "shop" -> "商店补给"
            else -> trimmed
        }
        return "$prefix（层级$tier）"
    }
    if (trimmed.startsWith("auto_shop")) return "商店补给"
    if (trimmed.startsWith("stage_guardian")) return "守卫战掉落"
    if (trimmed.startsWith("battle")) return "战斗掉落"
    return trimmed
}
