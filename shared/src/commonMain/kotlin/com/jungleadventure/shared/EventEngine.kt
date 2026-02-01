package com.jungleadventure.shared

import kotlin.random.Random

class EventEngine(private val events: List<EventDefinition>) {
    fun nextEvent(chapter: Int, rng: Random): EventDefinition {
        val candidates = events.filter { it.chapter == chapter }.ifEmpty { events }
        return weightedPick(candidates, rng)
    }

    fun toChoices(event: EventDefinition): List<GameChoice> {
        return if (event.options.isEmpty()) {
            listOf(GameChoice("advance", "¼ÌÐø"))
        } else {
            event.options.map { option ->
                GameChoice(option.optionId, option.text)
            }
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
