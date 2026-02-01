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
    val growth: GrowthProfile? = null,
    val passiveSkillId: String,
    val activeSkillIds: List<String> = emptyList(),
    val ultimateSkillId: String = ""
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
data class GrowthProfile(
    val hpMax: Int,
    val mpMax: Int,
    val atk: Int,
    val def: Int,
    val speed: Int
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
    val growth: GrowthProfile,
    val passiveSkill: RoleSkill,
    val activeSkills: List<RoleSkill>,
    val ultimateSkill: RoleSkill,
    val starting: Boolean,
    val unlock: String,
    val unlocked: Boolean
)

data class RoleSkill(
    val name: String,
    val type: String,
    val description: String,
    val cost: String,
    val cooldown: String,
    val target: String,
    val effectLines: List<String>,
    val formulaLines: List<String>
)

fun defaultRoles(): List<RoleProfile> = listOf(
    RoleProfile(
        id = "fallback_explorer",
        name = "探险者",
        role = "探索/侦察",
        stats = CharacterStats(hp = 120, atk = 22, def = 14, speed = 18, perception = 28),
        growth = defaultGrowthProfile(),
        passiveSkill = RoleSkill(
            name = "发现遗迹",
            type = "PASSIVE",
            description = "掉落+10%，隐藏路径触发+15%。",
            cost = "-",
            cooldown = "-",
            target = "自身",
            effectLines = listOf("掉落概率+10%", "隐藏路径触发+15%"),
            formulaLines = emptyList()
        ),
        activeSkills = listOf(
            RoleSkill(
                name = "勘探",
                type = "ACTIVE",
                description = "显示下一节点风险，命中+10%持续2回合。",
                cost = "6",
                cooldown = "3 回合",
                target = "敌方",
                effectLines = listOf("显示下一节点风险", "命中率+10%，持续2回合"),
                formulaLines = listOf("60%ATK伤害")
            ),
            RoleSkill(
                name = "观察",
                type = "ACTIVE",
                description = "观察战场并快速调整节奏。",
                cost = "5",
                cooldown = "2 回合",
                target = "敌方",
                effectLines = listOf("命中率+5%，持续1回合"),
                formulaLines = listOf("50%ATK伤害")
            )
        ),
        ultimateSkill = RoleSkill(
            name = "探路先锋",
            type = "ULTIMATE",
            description = "短时间内最大化侦察与突袭效率。",
            cost = "18",
            cooldown = "6 回合",
            target = "敌方",
            effectLines = listOf("命中率+15%，持续2回合", "下一节点风险揭示"),
            formulaLines = listOf("110%ATK伤害")
        ),
        starting = true,
        unlock = "初始可选",
        unlocked = true
    )
)

fun defaultGrowthProfile(): GrowthProfile {
    return GrowthProfile(
        hpMax = 12,
        mpMax = 4,
        atk = 3,
        def = 2,
        speed = 1
    )
}
