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
    val notes: String = ""
)

@Serializable
data class EnemyStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val hit: Int = 75,
    val eva: Int = 8,
    val crit: Int = 8,
    val critDmg: Double = 1.5,
    val resist: Int = 0
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
                id = "e_grass_cat",
                name = "草丛猫",
                type = "普通",
                level = 1,
                stats = EnemyStats(hp = 30, atk = 8, def = 2, spd = 9, hit = 78, eva = 10, crit = 8, critDmg = 1.5, resist = 2),
                notes = "爱摸鱼的草丛猫。"
            ),
            EnemyDefinition(
                id = "e_elite_badge",
                name = "徽章精英",
                type = "精英",
                level = 2,
                stats = EnemyStats(hp = 55, atk = 14, def = 6, spd = 10, hit = 80, eva = 12, crit = 10, critDmg = 1.6, resist = 4),
                notes = "背着考勤机的精英怪。"
            ),
            EnemyDefinition(
                id = "e_temp_worker",
                name = "临时工怪物",
                type = "普通",
                level = 2,
                stats = EnemyStats(hp = 42, atk = 12, def = 5, spd = 9, hit = 78, eva = 10, crit = 9, critDmg = 1.5, resist = 3),
                notes = "一边吐槽一边出手。"
            ),
            EnemyDefinition(
                id = "e_overtime_pack",
                name = "加班怪小队",
                type = "精英",
                level = 3,
                stats = EnemyStats(hp = 65, atk = 16, def = 7, spd = 11, hit = 82, eva = 13, crit = 12, critDmg = 1.6, resist = 4),
                notes = "三人小队，输出密集。"
            ),
            EnemyDefinition(
                id = "e_mini_boss",
                name = "小首领",
                type = "精英",
                level = 3,
                stats = EnemyStats(hp = 80, atk = 18, def = 8, spd = 12, hit = 83, eva = 13, crit = 12, critDmg = 1.7, resist = 5),
                notes = "章节小首领。"
            ),
            EnemyDefinition(
                id = "e_sponsor_gladiator",
                name = "赞助商挑战者",
                type = "精英",
                level = 4,
                stats = EnemyStats(hp = 95, atk = 22, def = 10, spd = 12, hit = 85, eva = 14, crit = 14, critDmg = 1.7, resist = 6),
                notes = "限定回合的强敌。"
            ),
            EnemyDefinition(
                id = "e_outpost_guard",
                name = "前哨守卫",
                type = "精英",
                level = 4,
                stats = EnemyStats(hp = 110, atk = 24, def = 11, spd = 12, hit = 86, eva = 14, crit = 14, critDmg = 1.7, resist = 6),
                notes = "负责守门的强力精英。"
            ),
            EnemyDefinition(
                id = "e_final_boss",
                name = "终极首领",
                type = "首领",
                level = 5,
                stats = EnemyStats(hp = 150, atk = 30, def = 14, spd = 13, hit = 88, eva = 15, crit = 16, critDmg = 1.8, resist = 8),
                notes = "最终关卡首领。"
            )
        )
    )
}

fun defaultEnemyGroupFile(): EnemyGroupFile {
    return EnemyGroupFile(
        groups = listOf(
            EnemyGroupDefinition(id = "eg_ch1_grass_cat_1", enemyId = "e_grass_cat", count = 1, note = "草丛猫"),
            EnemyGroupDefinition(id = "eg_ch2_elite_1", enemyId = "e_elite_badge", count = 1, note = "精英怪"),
            EnemyGroupDefinition(id = "eg_ch2_temp_worker", enemyId = "e_temp_worker", count = 1, note = "临时工怪物"),
            EnemyGroupDefinition(id = "eg_ch3_group_1", enemyId = "e_overtime_pack", count = 3, note = "群怪战"),
            EnemyGroupDefinition(id = "eg_ch3_mini_boss", enemyId = "e_mini_boss", count = 1, note = "小首领"),
            EnemyGroupDefinition(id = "eg_ch4_sponsor_1", enemyId = "e_sponsor_gladiator", count = 1, note = "赞助商挑战"),
            EnemyGroupDefinition(id = "eg_ch4_outpost", enemyId = "e_outpost_guard", count = 1, note = "首领前哨"),
            EnemyGroupDefinition(id = "eg_ch5_final_boss", enemyId = "e_final_boss", count = 1, note = "最终首领"),
            EnemyGroupDefinition(id = "eg_default", enemyId = "e_grass_cat", count = 1, note = "默认敌人")
        )
    )
}
