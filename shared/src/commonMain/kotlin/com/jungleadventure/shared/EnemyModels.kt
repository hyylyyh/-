package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class EnemyFile(
    val enemies: List<EnemyDefinition> = emptyList()
)

@Serializable
data class EnemyGroupFile(
    val groups: List<EnemyGroupDefinition> = emptyList()
)

@Serializable
data class EnemyDefinition(
    val id: String,
    val name: String,
    val type: String,
    val level: Int,
    val stats: EnemyStats,
    val skills: List<EnemySkillDefinition> = emptyList(),
    val notes: String = ""
)

@Serializable
data class EnemyStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val strength: Int = 0,
    val intelligence: Int = 0,
    val agility: Int = 0,
    val hit: Int = 75,
    val eva: Int = 8,
    val crit: Int = 8,
    val critDmg: Double = 1.5
)

@Serializable
data class EnemySkillDefinition(
    val id: String,
    val name: String,
    val cooldown: Int = 2,
    val chance: Double = 0.35,
    val damageMultiplier: Double? = null,
    val healRate: Double = 0.0,
    val statusType: String? = null,
    val statusTurns: Int = 0,
    val statusStacks: Int = 1,
    val statusPotency: Double = 0.0,
    val note: String = ""
)

@Serializable
data class EnemyGroupDefinition(
    val id: String,
    val enemyId: String,
    val count: Int = 1,
    val note: String = ""
)

class EnemyRepository(
    enemyFile: EnemyFile,
    groupFile: EnemyGroupFile
) {
    private val enemies = enemyFile.enemies.associateBy { it.id }
    private val groups = groupFile.groups.associateBy { it.id }
    private val groupList = groupFile.groups

    fun findEnemy(enemyId: String?): EnemyDefinition? {
        if (enemyId.isNullOrBlank()) return null
        return enemies[enemyId]
    }

    fun findGroup(groupId: String?): EnemyGroupDefinition? {
        if (groupId.isNullOrBlank()) return null
        return groups[groupId]
    }

    fun allGroups(): List<EnemyGroupDefinition> {
        return groupList
    }
}

fun defaultEnemyFile(): EnemyFile {
    return EnemyFile(
        enemies = listOf(
            EnemyDefinition(
                id = "e_jungle_scout",
                name = "丛林斥候",
                type = "普通",
                level = 1,
                stats = EnemyStats(hp = 36, atk = 9, def = 3, hit = 78, eva = 10, crit = 8, critDmg = 1.5),
                skills = listOf(
                    EnemySkillDefinition(
                        id = "es_quick_claw",
                        name = "迅爪",
                        cooldown = 2,
                        chance = 0.35,
                        damageMultiplier = 1.1,
                        note = "小幅提升伤害"
                    )
                ),
                notes = "行动灵活的小怪。"
            ),
            EnemyDefinition(
                id = "e_jungle_viper",
                name = "剧毒蜥蜴",
                type = "普通",
                level = 1,
                stats = EnemyStats(hp = 34, atk = 8, def = 4, hit = 76, eva = 12, crit = 7, critDmg = 1.5),
                skills = listOf(
                    EnemySkillDefinition(
                        id = "es_venom_bite",
                        name = "毒咬",
                        cooldown = 3,
                        chance = 0.3,
                        damageMultiplier = 0.9,
                        statusType = "POISON",
                        statusTurns = 2,
                        statusPotency = 0.03,
                        note = "附带中毒"
                    )
                ),
                notes = "带毒的小型爬行怪。"
            ),
            EnemyDefinition(
                id = "e_jungle_elite",
                name = "丛林精英",
                type = "精英",
                level = 2,
                stats = EnemyStats(hp = 70, atk = 16, def = 7, hit = 82, eva = 12, crit = 10, critDmg = 1.6),
                skills = listOf(
                    EnemySkillDefinition(
                        id = "es_heavy_strike",
                        name = "重击",
                        cooldown = 3,
                        chance = 0.4,
                        damageMultiplier = 1.35,
                        note = "高伤害重击"
                    ),
                    EnemySkillDefinition(
                        id = "es_stun_smash",
                        name = "震慑",
                        cooldown = 4,
                        chance = 0.25,
                        damageMultiplier = 0.9,
                        statusType = "STUN",
                        statusTurns = 1,
                        note = "附带眩晕"
                    )
                ),
                notes = "比普通怪更强的精英单位。"
            ),
            EnemyDefinition(
                id = "e_jungle_boss",
                name = "丛林巨兽",
                type = "首领",
                level = 3,
                stats = EnemyStats(hp = 130, atk = 24, def = 12, hit = 86, eva = 12, crit = 14, critDmg = 1.8),
                skills = listOf(
                    EnemySkillDefinition(
                        id = "es_frenzy_rend",
                        name = "狂暴撕裂",
                        cooldown = 4,
                        chance = 0.35,
                        damageMultiplier = 1.45,
                        statusType = "BLEED",
                        statusTurns = 2,
                        statusPotency = 0.04,
                        note = "高伤害并造成流血"
                    ),
                    EnemySkillDefinition(
                        id = "es_boss_guard",
                        name = "护体",
                        cooldown = 5,
                        chance = 0.25,
                        statusType = "SHIELD",
                        statusTurns = 2,
                        statusStacks = 1,
                        note = "获得护盾"
                    ),
                    EnemySkillDefinition(
                        id = "es_boss_recover",
                        name = "自愈",
                        cooldown = 5,
                        chance = 0.2,
                        healRate = 0.15,
                        note = "恢复生命"
                    )
                ),
                notes = "关卡最终首领。"
            )
        )
    )
}

fun defaultEnemyGroupFile(): EnemyGroupFile {
    return EnemyGroupFile(
        groups = listOf(
            EnemyGroupDefinition(id = "eg_1_1", enemyId = "e_jungle_scout", count = 1, note = "1-1"),
            EnemyGroupDefinition(id = "eg_1_2", enemyId = "e_jungle_viper", count = 1, note = "1-2"),
            EnemyGroupDefinition(id = "eg_1_3", enemyId = "e_jungle_scout", count = 1, note = "1-3"),
            EnemyGroupDefinition(id = "eg_1_4", enemyId = "e_jungle_viper", count = 1, note = "1-4"),
            EnemyGroupDefinition(id = "eg_1_5", enemyId = "e_jungle_elite", count = 1, note = "1-5 精英"),
            EnemyGroupDefinition(id = "eg_1_6", enemyId = "e_jungle_scout", count = 2, note = "1-6"),
            EnemyGroupDefinition(id = "eg_1_7", enemyId = "e_jungle_viper", count = 2, note = "1-7"),
            EnemyGroupDefinition(id = "eg_1_8", enemyId = "e_jungle_scout", count = 1, note = "1-8"),
            EnemyGroupDefinition(id = "eg_1_9", enemyId = "e_jungle_viper", count = 1, note = "1-9"),
            EnemyGroupDefinition(id = "eg_1_10", enemyId = "e_jungle_boss", count = 1, note = "1-10 首领"),
            EnemyGroupDefinition(id = "eg_default", enemyId = "e_jungle_scout", count = 1, note = "默认敌人")
        )
    )
}
