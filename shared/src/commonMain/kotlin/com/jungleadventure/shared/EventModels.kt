package com.jungleadventure.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EventDefinition(
    val eventId: String,
    val chapter: Int,
    val title: String,
    val type: String,
    val difficulty: Int,
    val weight: Int,
    val cooldown: Int,
    val introText: String,
    val successText: String,
    val failText: String,
    val logText: String,
    val conditions: List<String> = emptyList(),
    val options: List<EventOption> = emptyList(),
    val result: EventResult? = null,
    val enemyGroupId: String? = null,
    val roundLimit: Int? = null,
    val firstStrike: String? = null,
    val battleModifiers: BattleModifiers? = null,
    val dropTableId: String? = null,
    val guarantee: Int = 0,
    val nextEventId: String? = null,
    val failEventId: String? = null
)

@Serializable
data class EventOption(
    val optionId: String,
    val text: String,
    val cost: JsonElement? = null,
    val require: JsonElement? = null,
    val result: EventResult? = null
)

@Serializable
data class EventResult(
    val hpDelta: Int = 0,
    val mpDelta: Int = 0,
    val statusAdd: List<String> = emptyList(),
    val goldDelta: Int = 0,
    val expDelta: Int = 0,
    val dropTableId: String? = null,
    val nextEventId: String? = null,
    val nextNodeId: String? = null
)

@Serializable
data class BattleModifiers(
    val enemyHpMultiplier: Double = 1.0,
    val enemyDamageMultiplier: Double = 1.0
)
