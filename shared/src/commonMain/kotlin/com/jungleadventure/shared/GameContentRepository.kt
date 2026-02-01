package com.jungleadventure.shared

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GameContentRepository(private val resourceReader: ResourceReader) {
    private val json = Json { ignoreUnknownKeys = true }
    private val logTag = "GameContentRepository"

    fun loadEvents(): List<EventDefinition> {
        GameLogger.info(logTag, "开始读取事件配置 data/events.json")
        val raw = resourceReader.readText("data/events.json")
        val events: List<EventDefinition> = json.decodeFromString(raw)
        GameLogger.info(logTag, "事件配置读取完成，数量=${events.size}")
        return events
    }

    fun loadRuins(): RuinsFile {
        GameLogger.info(logTag, "开始读取遗迹配置 data/ruins.json")
        val raw = resourceReader.readText("data/ruins.json")
        val ruins: RuinsFile = json.decodeFromString(raw)
        GameLogger.info(logTag, "遗迹配置读取完成，数量=${ruins.ruins.size}")
        return ruins
    }

    fun loadCharacters(): CharacterFile {
        GameLogger.info(logTag, "开始读取角色配置 cp/cp.json")
        val raw = resourceReader.readText("cp/cp.json")
        val file: CharacterFile = json.decodeFromString(raw)
        GameLogger.info(logTag, "角色配置读取完成，数量=${file.characters.size}")
        return file
    }

    fun loadSkills(): SkillFile {
        GameLogger.info(logTag, "开始读取技能配置 cp/ski.json")
        val raw = resourceReader.readText("cp/ski.json")
        val file: SkillFile = json.decodeFromString(raw)
        GameLogger.info(logTag, "技能配置读取完成，数量=${file.skills.size}")
        return file
    }
}
