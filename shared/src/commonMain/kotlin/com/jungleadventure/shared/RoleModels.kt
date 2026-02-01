package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class CharacterFile(
    val characters: List<CharacterDefinition> = emptyList()
)

@Serializable
data class CharacterDefinition(
    val id: String,
    val name: String,
    val role: String,
    val starting: Boolean = false,
    val unlock: String = "",
    val stats: CharacterStats,
    val passiveSkillId: String,
    val activeSkillIds: List<String> = emptyList()
)

@Serializable
data class CharacterStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val speed: Int,
    val perception: Int
)

@Serializable
data class SkillFile(
    val skills: List<SkillDefinition> = emptyList()
)

@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val type: String,
    val target: String,
    val cost: Int,
    val cooldown: Int,
    val effects: List<SkillEffect> = emptyList(),
    val desc: String
)

@Serializable
data class SkillEffect(
    val type: String,
    val value: Double? = null,
    val scaling: String? = null,
    val note: String? = null
)

data class RoleProfile(
    val id: String,
    val name: String,
    val role: String,
    val stats: CharacterStats,
    val passiveSkill: RoleSkill,
    val activeSkill: RoleSkill,
    val starting: Boolean,
    val unlock: String,
    val unlocked: Boolean
)

data class RoleSkill(
    val name: String,
    val type: String,
    val description: String,
    val cost: String,
    val cooldown: String
)

fun defaultRoles(): List<RoleProfile> = listOf(
    RoleProfile(
        id = "fallback_explorer",
        name = "探险家",
        role = "探索/侦察",
        stats = CharacterStats(hp = 120, atk = 22, def = 14, speed = 18, perception = 28),
        passiveSkill = RoleSkill(
            name = "发现遗迹",
            type = "PASSIVE",
            description = "掉落+10%，隐藏路径触发+15%。",
            cost = "-",
            cooldown = "-"
        ),
        activeSkill = RoleSkill(
            name = "勘探",
            type = "ACTIVE",
            description = "显示下一节点风险，命中+10%持续2回合。",
            cost = "消耗 6",
            cooldown = "3 回合"
        ),
        starting = true,
        unlock = "初始可选",
        unlocked = true
    )
)
