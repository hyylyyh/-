package com.jungleadventure.shared

const val BATTLE_SKILL_CHOICE_PREFIX = "battle_skill_"
const val BATTLE_CHOICE_ATTACK = "battle_attack"
const val BATTLE_CHOICE_POTION_1 = "battle_potion_1"
const val BATTLE_CHOICE_POTION_2 = "battle_potion_2"
const val BATTLE_CHOICE_ITEM = "battle_item"
const val BATTLE_CHOICE_EQUIP = "battle_equip"
const val BATTLE_CHOICE_FLEE = "battle_flee"
const val POTION_NAME = "治疗药水"
const val POTION_PRICE = 25

fun buildBattleSkillChoiceId(skillId: String): String {
    return "$BATTLE_SKILL_CHOICE_PREFIX$skillId"
}
