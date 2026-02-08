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
