package com.jungleadventure.shared

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GameContentRepository(private val resourceReader: ResourceReader) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadEvents(): List<EventDefinition> {
        val raw = resourceReader.readText("data/events.json")
        return json.decodeFromString(raw)
    }

    fun loadRuins(): RuinsFile {
        val raw = resourceReader.readText("data/ruins.json")
        return json.decodeFromString(raw)
    }

    fun loadCharacters(): CharacterFile {
        val raw = resourceReader.readText("cp/cp.json")
        return json.decodeFromString(raw)
    }

    fun loadSkills(): SkillFile {
        val raw = resourceReader.readText("cp/ski.json")
        return json.decodeFromString(raw)
    }
}
