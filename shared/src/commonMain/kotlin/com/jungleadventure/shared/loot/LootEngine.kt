package com.jungleadventure.shared.loot

import kotlin.random.Random
import kotlinx.serialization.decodeFromString

class LootRepository(
    private val loader: ResourceLoader = DefaultResourceLoader
) {
    private val data: LootData = loadAll()

    private val rarityById = data.rarities.associateBy { it.id }
    private val equipmentById = data.equipments.associateBy { it.id }
    private val affixById = data.affixes.associateBy { it.id }

    fun getLootTableById(id: String): LootTable? {
        return data.lootTables.firstOrNull { it.id == id }
    }

    fun getLootTable(sourceType: LootSourceType, tier: Int): LootTable {
        return data.lootTables
            .filter { it.sourceType == sourceType }
            .minByOrNull { kotlin.math.abs(it.tier - tier) }
            ?: error("No loot table for $sourceType tier $tier")
    }

    fun generateLoot(
        sourceType: LootSourceType,
        tier: Int,
        rng: Random,
        pityCounters: MutableMap<String, Int>
    ): LootOutcome {
        val table = getLootTable(sourceType, tier)
        val entry = weightedPick(table.weightedPool, rng)
        val result = when (entry.type) {
            LootEntryType.EQUIPMENT -> {
                val pityMinTier = table.guarantee?.let { guarantee ->
                    val current = pityCounters.getOrDefault(guarantee.counterKey, 0) + 1
                    pityCounters[guarantee.counterKey] = current
                    if (current >= guarantee.threshold) guarantee.grantRarityMin else null
                }
                val equipment = generateEquipment(entry.refId, tier, pityMinTier, rng)
                if (equipment != null && table.guarantee != null) {
                    val minTier = table.guarantee.grantRarityMin
                    if (equipment.rarity.tier >= minTier) {
                        pityCounters[table.guarantee.counterKey] = 0
                    }
                }
                LootOutcome(equipment = equipment)
            }
            LootEntryType.GOLD -> LootOutcome(gold = rng.nextInt(entry.min, entry.max + 1))
            LootEntryType.MATERIAL -> LootOutcome(materials = rng.nextInt(entry.min, entry.max + 1))
            LootEntryType.CONSUMABLE -> LootOutcome()
        }
        return result
    }

    fun scoreEquipment(equipment: EquipmentInstance): Int {
        val base = equipment.stats.values.sum()
        val affix = equipment.affixes.sumOf { it.value }
        return base + affix + equipment.rarity.tier * 5
    }

    fun equipmentName(id: String): String {
        return equipmentById[id]?.name ?: id
    }

    private fun generateEquipment(
        equipmentId: String,
        tier: Int,
        pityMinTier: Int?,
        rng: Random
    ): EquipmentInstance? {
        val definition = equipmentById[equipmentId] ?: return null
        val minTier = pityMinTier ?: 1
        val rarity = pickRarity(minTier, rng)
        val level = tier.coerceAtLeast(1)
        val multiplier = rng.nextDouble(
            rarity.mainStatMultiplierRange.min,
            rarity.mainStatMultiplierRange.max
        )
        val stats = definition.baseStats.mapValues { (type, baseValue) ->
            val scale = definition.statScale[type] ?: 0.0
            val scaled = baseValue * multiplier + level * scale
            scaled.toInt().coerceAtLeast(1)
        }
        val affixCount = rng.nextInt(definition.affixCount.min, definition.affixCount.max + 1)
        val affixes = rollAffixes(definition, rarity, affixCount, rng)
        return EquipmentInstance(
            id = definition.id,
            name = definition.name,
            slot = definition.slot,
            rarity = rarity,
            level = level,
            stats = stats,
            affixes = affixes
        )
    }

    private fun pickRarity(minTier: Int, rng: Random): RarityDefinition {
        val filtered = data.rarities.filter { it.tier >= minTier }
        return weightedPick(filtered, rng) { it.dropWeight }
    }

    private fun rollAffixes(
        definition: EquipmentDefinition,
        rarity: RarityDefinition,
        count: Int,
        rng: Random
    ): List<AffixInstance> {
        if (count <= 0) return emptyList()
        val candidates = definition.affixPool.mapNotNull { affixById[it] }
            .filter { it.rarityGate <= rarity.tier }
        if (candidates.isEmpty()) return emptyList()
        val picked = mutableListOf<AffixInstance>()
        val usedTypes = mutableSetOf<StatType>()
        repeat(count) {
            val available = candidates.filter { affix ->
                when (affix.stackRule) {
                    StackRule.UNIQUE -> !usedTypes.contains(affix.type)
                    StackRule.STACK -> true
                }
            }
            if (available.isEmpty()) return@repeat
            val affix = weightedPick(available, rng) { it.weight }
            val value = rng.nextInt(affix.valueRange.min, affix.valueRange.max + 1)
            picked += AffixInstance(affix, value)
            usedTypes += affix.type
        }
        return picked
    }

    private fun loadAll(): LootData {
        val raritiesText = loader.loadText("data/rarities.json") ?: error("Missing rarities.json")
        val affixesText = loader.loadText("data/affixes.json") ?: error("Missing affixes.json")
        val equipmentsText = loader.loadText("data/equipments.json") ?: error("Missing equipments.json")
        val tablesText = loader.loadText("data/loot_tables.json") ?: error("Missing loot_tables.json")

        val rarities = LootJson.parser.decodeFromString<RarityList>(raritiesText).rarities
        val affixes = LootJson.parser.decodeFromString<AffixList>(affixesText).affixes
        val equipments = LootJson.parser.decodeFromString<EquipmentList>(equipmentsText).equipments
        val lootTables = LootJson.parser.decodeFromString<LootTableList>(tablesText).lootTables
        return LootData(
            rarities = rarities,
            affixes = affixes,
            equipments = equipments,
            lootTables = lootTables
        )
    }

    private fun <T> weightedPick(items: List<T>, rng: Random, weightOf: (T) -> Int): T {
        val total = items.sumOf(weightOf)
        val roll = rng.nextInt(total)
        var cumulative = 0
        for (item in items) {
            cumulative += weightOf(item)
            if (roll < cumulative) return item
        }
        return items.last()
    }

    private fun weightedPick(items: List<LootEntry>, rng: Random): LootEntry {
        return weightedPick(items, rng) { it.weight }
    }
}

data class LootOutcome(
    val equipment: EquipmentInstance? = null,
    val gold: Int = 0,
    val materials: Int = 0
)

@kotlinx.serialization.Serializable
private data class RarityList(val rarities: List<RarityDefinition>)

@kotlinx.serialization.Serializable
private data class AffixList(val affixes: List<AffixDefinition>)

@kotlinx.serialization.Serializable
private data class EquipmentList(val equipments: List<EquipmentDefinition>)

@kotlinx.serialization.Serializable
private data class LootTableList(val lootTables: List<LootTable>)
