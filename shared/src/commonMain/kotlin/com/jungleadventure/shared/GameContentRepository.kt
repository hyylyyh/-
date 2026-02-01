package com.jungleadventure.shared

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GameContentRepository(private val resourceReader: ResourceReader) {
    private val json = Json { ignoreUnknownKeys = true }
    private val logTag = "内容仓库"

    fun loadEvents(): List<EventDefinition> {
        GameLogger.log(logTag, "开始读取事件配置 data/events.json")
        val raw = resourceReader.readText("data/events.json")
        val events: List<EventDefinition> = json.decodeFromString(raw)
        GameLogger.log(logTag, "事件配置读取完成，数量=${events.size}")
        return events
    }

    fun loadStages(): StageFile {
        GameLogger.log(logTag, "开始读取关卡配置 data/stages.json")
        val raw = resourceReader.readText("data/stages.json")
        val stages: StageFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "关卡配置读取完成，数量=${stages.stages.size}")
        return stages
    }

    fun loadNodes(): NodeFile {
        GameLogger.log(logTag, "开始读取节点配置 data/nodes.json")
        val raw = resourceReader.readText("data/nodes.json")
        val nodes: NodeFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "节点配置读取完成，数量=${nodes.nodes.size}")
        return nodes
    }

    fun loadRuins(): RuinsFile {
        GameLogger.log(logTag, "开始读取遗迹配置 data/ruins.json")
        val raw = resourceReader.readText("data/ruins.json")
        val ruins: RuinsFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "遗迹配置读取完成，数量=${ruins.ruins.size}")
        return ruins
    }

    fun loadCharacters(): CharacterFile {
        GameLogger.log(logTag, "开始读取角色配置 cp/cp.json")
        val raw = resourceReader.readText("cp/cp.json")
        val file: CharacterFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "角色配置读取完成，数量=${file.characters.size}")
        return file
    }

    fun loadSkills(): SkillFile {
        GameLogger.log(logTag, "开始读取技能配置 cp/ski.json")
        val raw = resourceReader.readText("cp/ski.json")
        val file: SkillFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "技能配置读取完成，数量=${file.skills.size}")
        return file
    }

    fun loadEnemies(): EnemyFile {
        GameLogger.log(logTag, "开始读取敌人配置 data/enemies.json")
        val raw = resourceReader.readText("data/enemies.json")
        val file: EnemyFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "敌人配置读取完成，数量=${file.enemies.size}")
        return file
    }

    fun loadEnemyGroups(): EnemyGroupFile {
        GameLogger.log(logTag, "开始读取敌群配置 data/enemy_groups.json")
        val raw = resourceReader.readText("data/enemy_groups.json")
        val file: EnemyGroupFile = json.decodeFromString(raw)
        GameLogger.log(logTag, "敌群配置读取完成，数量=${file.groups.size}")
        return file
    }
}
