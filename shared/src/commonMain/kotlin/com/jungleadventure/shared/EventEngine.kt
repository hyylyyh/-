package com.jungleadventure.shared

import kotlin.random.Random

class EventEngine(private val events: List<EventDefinition>) {
    private val logTag = "事件引擎"
    private val eventMap = events.associateBy { it.eventId }

    fun nextEvent(chapter: Int, rng: Random): EventDefinition {
        val candidates = events.filter { it.chapter == chapter }.ifEmpty { events }
        val result = weightedPick(candidates, rng)
        GameLogger.info(logTag, "按章节抽取事件：章节=$chapter 事件编号=${result.eventId}")
        return result
    }

    fun eventById(eventId: String): EventDefinition? {
        val event = eventMap[eventId]
        if (event == null) {
            GameLogger.warn(logTag, "未找到事件：事件编号=$eventId")
        }
        return event
    }

    fun nextEventForNode(chapter: Int, nodeType: String, rng: Random): EventDefinition? {
        val filtered = filterByNodeType(chapter, nodeType)
        val typeLabel = nodeTypeLabel(nodeType)
        return if (filtered.isEmpty()) {
            GameLogger.warn(logTag, "节点类型无可用事件，降级按章节抽取：节点类型=$typeLabel 章节=$chapter")
            if (events.isEmpty()) null else nextEvent(chapter, rng)
        } else {
            val result = weightedPick(filtered, rng)
            GameLogger.info(logTag, "按节点类型抽取事件：节点类型=$typeLabel 章节=$chapter 事件编号=${result.eventId}")
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
            "BATTLE" -> { event ->
                event.type.contains("battle", ignoreCase = true) || event.type.contains("战斗")
            }
            "TRAP" -> { event ->
                event.type.contains("trap", ignoreCase = true) || event.type.contains("陷阱")
            }
            "SHOP" -> { event ->
                event.type.contains("shop", ignoreCase = true) || event.type.contains("商店")
            }
            "REST" -> { event ->
                event.type.contains("rest", ignoreCase = true) || event.type.contains("休息")
            }
            "STORY" -> { event ->
                event.type.contains("story", ignoreCase = true) ||
                    event.type.contains("dialog", ignoreCase = true) ||
                    event.type.contains("剧情") ||
                    event.type.contains("对话")
            }
            else -> { _ -> true }
        }
        val pool = events.filter { it.chapter == chapter && predicate(it) }
        if (pool.isNotEmpty()) {
            return pool
        }
        return events.filter { predicate(it) }
    }

    private fun nodeTypeLabel(nodeType: String): String {
        return when (nodeType.uppercase()) {
            "BATTLE" -> "战斗"
            "TRAP" -> "陷阱"
            "SHOP" -> "商店"
            "REST" -> "休息"
            "STORY" -> "剧情"
            else -> "未知"
        }
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
