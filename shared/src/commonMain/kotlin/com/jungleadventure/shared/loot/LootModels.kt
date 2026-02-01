package com.jungleadventure.shared.loot

import kotlinx.serialization.Serializable

@Serializable
data class IntRangeDef(val min: Int, val max: Int) {
    fun clamp(value: Int): Int = value.coerceIn(min, max)
}

@Serializable
data class DoubleRangeDef(val min: Double, val max: Double)

@Serializable
data class LootData(
    val rarities: List<RarityDefinition>,
    val affixes: List<AffixDefinition>,
    val equipments: List<EquipmentDefinition>,
    val lootTables: List<LootTable>
)

@Serializable
data class RarityDefinition(
    val id: String,
    val name: String,
    val tier: Int,
    val mainStatMultiplierRange: DoubleRangeDef,
    val affixCountRange: IntRangeDef,
    val dropWeight: Int
)

@Serializable
data class AffixDefinition(
    val id: String,
    val type: StatType,
    val valueRange: IntRangeDef,
    val weight: Int,
    val rarityGate: Int,
    val stackRule: StackRule
)

@Serializable
data class EquipmentDefinition(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val rarity: String,
    val levelReq: Int,
    val classReq: String? = null,
    val baseStats: Map<StatType, Int>,
    val statScale: Map<StatType, Double> = emptyMap(),
    val affixCount: IntRangeDef,
    val affixPool: List<String>,
    val enhanceMax: Int,
    val setId: String? = null,
    val sellValue: Int,
    val salvageYield: Int
)

@Serializable
data class LootTable(
    val id: String,
    val sourceType: LootSourceType,
    val tier: Int,
    val guarantee: LootGuarantee? = null,
    val weightedPool: List<LootEntry>
)

@Serializable
data class LootGuarantee(
    val counterKey: String,
    val threshold: Int,
    val grantRarityMin: Int
)

@Serializable
data class LootEntry(
    val type: LootEntryType,
    val refId: String,
    val weight: Int,
    val min: Int = 1,
    val max: Int = 1
)

@Serializable
enum class LootSourceType {
    ENEMY,
    EVENT,
    CHEST,
    SHOP
}

@Serializable
enum class LootEntryType {
    EQUIPMENT,
    MATERIAL,
    GOLD,
    CONSUMABLE
}

@Serializable
enum class EquipmentSlot {
    WEAPON,
    ARMOR,
    HELM,
    ACCESSORY
}

@Serializable
enum class StatType {
    HP,
    ATK,
    DEF,
    SPEED,
    HIT,
    EVADE,
    CRIT,
    CRIT_RESIST
}

@Serializable
enum class StackRule {
    UNIQUE,
    STACK
}

data class EquipmentInstance(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val rarity: RarityDefinition,
    val level: Int,
    val stats: Map<StatType, Int>,
    val affixes: List<AffixInstance>,
    val enhanceLevel: Int = 0
)

data class AffixInstance(
    val definition: AffixDefinition,
    val value: Int
)
