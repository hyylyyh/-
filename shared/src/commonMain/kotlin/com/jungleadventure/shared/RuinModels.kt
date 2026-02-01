package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class RuinsFile(
    val schema: String,
    val ruins: List<RuinDefinition>
)

@Serializable
data class RuinDefinition(
    val id: String,
    val name: String,
    val biome: String,
    val levelRange: List<Int>,
    val rarity: String,
    val tags: List<String>,
    val entrance: RuinEntrance,
    val lootTable: List<LootEntry>,
    val events: List<RuinEventRef>,
    val coords: RuinCoords
)

@Serializable
data class RuinEntrance(
    val locked: Boolean,
    val lockType: String,
    val timeToOpenMs: Int
)

@Serializable
data class LootEntry(
    val itemId: String,
    val weight: Int
)

@Serializable
data class RuinEventRef(
    val type: String,
    val id: String,
    val chance: Double
)

@Serializable
data class RuinCoords(
    val x: Int,
    val y: Int
)
