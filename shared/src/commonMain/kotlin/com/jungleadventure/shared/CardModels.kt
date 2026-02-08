package com.jungleadventure.shared

import com.jungleadventure.shared.loot.StatType
import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class CardQuality {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGEND
}

@Serializable
data class CardEffect(
    val stat: StatType,
    val value: Int
)

@Serializable
data class CardDefinition(
    val id: String,
    val name: String,
    val quality: CardQuality,
    val description: String,
    val effects: List<CardEffect>,
    val isGood: Boolean
)

@Serializable
data class CardInstance(
    val uid: String,
    val name: String,
    val quality: CardQuality,
    val description: String,
    val effects: List<CardEffect>,
    val isGood: Boolean
)

class CardRepository {
    private val pool = listOf(
        CardDefinition(
            id = "card_common_good_1",
            name = "嫩叶祝福",
            quality = CardQuality.COMMON,
            description = "草木轻拂，生命更稳。",
            effects = listOf(CardEffect(StatType.HP, 8)),
            isGood = true
        ),
        CardDefinition(
            id = "card_common_good_2",
            name = "迅捷步伐",
            quality = CardQuality.COMMON,
            description = "步伐更灵动，速度略升。",
            effects = listOf(CardEffect(StatType.AGI, 2)),
            isGood = true
        ),
        CardDefinition(
            id = "card_common_bad_1",
            name = "破烂绑腿",
            quality = CardQuality.COMMON,
            description = "动作受限，速度下降。",
            effects = listOf(CardEffect(StatType.AGI, -2)),
            isGood = false
        ),
        CardDefinition(
            id = "card_common_bad_2",
            name = "迟钝之眼",
            quality = CardQuality.COMMON,
            description = "命中降低。",
            effects = listOf(CardEffect(StatType.HIT, -2)),
            isGood = false
        ),
        CardDefinition(
            id = "card_uncommon_good_1",
            name = "铁皮护腕",
            quality = CardQuality.UNCOMMON,
            description = "防御略有提升。",
            effects = listOf(CardEffect(StatType.DEF, 3)),
            isGood = true
        ),
        CardDefinition(
            id = "card_uncommon_good_2",
            name = "猎手专注",
            quality = CardQuality.UNCOMMON,
            description = "攻击更加稳定。",
            effects = listOf(CardEffect(StatType.ATK, 4)),
            isGood = true
        ),
        CardDefinition(
            id = "card_uncommon_bad_1",
            name = "裂纹护符",
            quality = CardQuality.UNCOMMON,
            description = "防御下滑。",
            effects = listOf(CardEffect(StatType.DEF, -3)),
            isGood = false
        ),
        CardDefinition(
            id = "card_uncommon_bad_2",
            name = "麻木肩甲",
            quality = CardQuality.UNCOMMON,
            description = "攻击下降。",
            effects = listOf(CardEffect(StatType.ATK, -4)),
            isGood = false
        ),
        CardDefinition(
            id = "card_rare_good_1",
            name = "回声徽章",
            quality = CardQuality.RARE,
            description = "命中与暴击同时提升。",
            effects = listOf(
                CardEffect(StatType.HIT, 4),
                CardEffect(StatType.CRIT, 3)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_rare_good_2",
            name = "灵巧护符",
            quality = CardQuality.RARE,
            description = "闪避提升，速度小幅提高。",
            effects = listOf(
                CardEffect(StatType.EVADE, 4),
                CardEffect(StatType.AGI, 3)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_rare_bad_1",
            name = "阴霾誓约",
            quality = CardQuality.RARE,
            description = "暴击与命中下降。",
            effects = listOf(
                CardEffect(StatType.CRIT, -3),
                CardEffect(StatType.HIT, -4)
            ),
            isGood = false
        ),
        CardDefinition(
            id = "card_rare_bad_2",
            name = "沉重负担",
            quality = CardQuality.RARE,
            description = "速度与闪避下降。",
            effects = listOf(
                CardEffect(StatType.AGI, -3),
                CardEffect(StatType.EVADE, -4)
            ),
            isGood = false
        ),
        CardDefinition(
            id = "card_epic_good_1",
            name = "古林纹章",
            quality = CardQuality.EPIC,
            description = "生命与防御显著提升。",
            effects = listOf(
                CardEffect(StatType.HP, 18),
                CardEffect(StatType.DEF, 6)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_epic_good_2",
            name = "赤焰脉动",
            quality = CardQuality.EPIC,
            description = "攻击与暴击大幅提升。",
            effects = listOf(
                CardEffect(StatType.ATK, 9),
                CardEffect(StatType.CRIT, 5)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_epic_bad_1",
            name = "虚弱锁链",
            quality = CardQuality.EPIC,
            description = "生命与攻击显著下降。",
            effects = listOf(
                CardEffect(StatType.HP, -16),
                CardEffect(StatType.ATK, -7)
            ),
            isGood = false
        ),
        CardDefinition(
            id = "card_epic_bad_2",
            name = "迟缓枷锁",
            quality = CardQuality.EPIC,
            description = "速度与闪避大幅下降。",
            effects = listOf(
                CardEffect(StatType.AGI, -5),
                CardEffect(StatType.EVADE, -6)
            ),
            isGood = false
        ),
        CardDefinition(
            id = "card_legend_good_1",
            name = "王者图腾",
            quality = CardQuality.LEGEND,
            description = "全属性提升。",
            effects = listOf(
                CardEffect(StatType.HP, 28),
                CardEffect(StatType.ATK, 10),
                CardEffect(StatType.DEF, 8),
                CardEffect(StatType.AGI, 6)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_legend_good_2",
            name = "苍穹遗愿",
            quality = CardQuality.LEGEND,
            description = "爆发与命中大幅提升。",
            effects = listOf(
                CardEffect(StatType.ATK, 12),
                CardEffect(StatType.CRIT, 8),
                CardEffect(StatType.HIT, 8)
            ),
            isGood = true
        ),
        CardDefinition(
            id = "card_legend_bad_1",
            name = "诅咒王冠",
            quality = CardQuality.LEGEND,
            description = "全属性大幅下降。",
            effects = listOf(
                CardEffect(StatType.HP, -26),
                CardEffect(StatType.ATK, -10),
                CardEffect(StatType.DEF, -8),
                CardEffect(StatType.AGI, -6)
            ),
            isGood = false
        ),
        CardDefinition(
            id = "card_legend_bad_2",
            name = "破碎誓言",
            quality = CardQuality.LEGEND,
            description = "命中与暴击大幅下降。",
            effects = listOf(
                CardEffect(StatType.HIT, -10),
                CardEffect(StatType.CRIT, -8)
            ),
            isGood = false
        )
    )

    fun drawCard(rng: Random): CardDefinition {
        val quality = rollQuality(rng)
        val good = rng.nextBoolean()
        val candidates = pool.filter { it.quality == quality && it.isGood == good }
        val fallback = pool.filter { it.quality == quality }
        return (if (candidates.isNotEmpty()) candidates else fallback).random(rng)
    }

    private fun rollQuality(rng: Random): CardQuality {
        val total = 45 + 25 + 15 + 10 + 5
        val roll = rng.nextInt(total)
        return when {
            roll < 45 -> CardQuality.COMMON
            roll < 70 -> CardQuality.UNCOMMON
            roll < 85 -> CardQuality.RARE
            roll < 95 -> CardQuality.EPIC
            else -> CardQuality.LEGEND
        }
    }
}
