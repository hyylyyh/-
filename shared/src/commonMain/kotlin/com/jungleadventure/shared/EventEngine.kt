package com.jungleadventure.shared

import kotlin.random.Random

class EventEngine(private val events: List<EventDefinition>) {
    private val logTag = "EventEngine"
    private val eventMap = events.associateBy { it.eventId }

    fun nextEvent(chapter: Int, rng: Random): EventDefinition {
        val candidates = events.filter { it.chapter == chapter }.ifEmpty { events }
        val result = weightedPick(candidates, rng)
        GameLogger.info(logTag, "按章节抽取事件：chapter=$chapter eventId=${result.eventId}")
        return result
    }

    fun eventById(eventId: String): EventDefinition? {
        val event = eventMap[eventId]
        if (event == null) {
            GameLogger.warn(logTag, "未找到事件：eventId=$eventId")
        }
        return event
    }

    fun nextEventForNode(chapter: Int, nodeType: String, rng: Random): EventDefinition? {
        val filtered = filterByNodeType(chapter, nodeType)
        return if (filtered.isEmpty()) {
            GameLogger.warn(logTag, "节点类型无可用事件，降级按章节抽取：nodeType=$nodeType chapter=$chapter")
            if (events.isEmpty()) null else nextEvent(chapter, rng)
        } else {
            val result = weightedPick(filtered, rng)
            GameLogger.info(logTag, "按节点类型抽取事件：nodeType=$nodeType chapter=$chapter eventId=${result.eventId}")
            result
        }
    }

    fun toChoices(event: EventDefinition): List<GameChoice> {
        return if (event.options.isEmpty()) {
            listOf(GameChoice("advance", "继续"))
        } else {
            event.options.map { option ->
                GameChoice(option.optionId, option.text)
            }
        }
    }

    private fun filterByNodeType(chapter: Int, nodeType: String): List<EventDefinition> {
        val normalized = nodeType.uppercase()
        val predicate: (EventDefinition) -> Boolean = when (normalized) {
            "BATTLE" -> { event -> event.type.contains("battle", ignoreCase = true) }
            "TRAP" -> { event -> event.type.contains("trap", ignoreCase = true) }
            "SHOP" -> { event -> event.type.contains("shop", ignoreCase = true) }
            "REST" -> { event -> event.type.contains("rest", ignoreCase = true) }
            "STORY" -> { event ->
                event.type.contains("story", ignoreCase = true) ||
                    event.type.contains("dialog", ignoreCase = true)
            }
            else -> { _ -> true }
        }
        val pool = events.filter { it.chapter == chapter && predicate(it) }
        if (pool.isNotEmpty()) {
            return pool
        }
        return events.filter { predicate(it) }
    }

    private fun weightedPick(pool: List<EventDefinition>, rng: Random): EventDefinition {
        val total = pool.sumOf { it.weight.coerceAtLeast(0) }
        if (total <= 0) return pool.first()

        var roll = rng.nextInt(total)
        for (event in pool) {
            roll -= event.weight.coerceAtLeast(0)
            if (roll < 0) return event
        }
        return pool.last()
    }
}
