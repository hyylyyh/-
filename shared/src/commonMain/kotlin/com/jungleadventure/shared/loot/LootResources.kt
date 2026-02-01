package com.jungleadventure.shared.loot

import kotlinx.serialization.json.Json

interface ResourceLoader {
    fun loadText(path: String): String?
}

expect fun loadResourceText(path: String): String?

object DefaultResourceLoader : ResourceLoader {
    override fun loadText(path: String): String? {
        return loadResourceText(path) ?: EmbeddedLootData.get(path)
    }
}

object EmbeddedLootData {
    private val files: Map<String, String> = mapOf(
        "data/rarities.json" to EmbeddedJson.rarities,
        "data/affixes.json" to EmbeddedJson.affixes,
        "data/equipments.json" to EmbeddedJson.equipments,
        "data/loot_tables.json" to EmbeddedJson.lootTables
    )

    fun get(path: String): String? = files[path]
}

object LootJson {
    val parser: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

private object EmbeddedJson {
    val rarities = """
        {
          "rarities": [
            {
              "id": "common",
              "name": "普通",
              "tier": 1,
              "mainStatMultiplierRange": { "min": 0.8, "max": 1.0 },
              "affixCountRange": { "min": 0, "max": 1 },
              "dropWeight": 50
            },
            {
              "id": "uncommon",
              "name": "优秀",
              "tier": 2,
              "mainStatMultiplierRange": { "min": 1.0, "max": 1.15 },
              "affixCountRange": { "min": 1, "max": 2 },
              "dropWeight": 30
            },
            {
              "id": "rare",
              "name": "稀有",
              "tier": 3,
              "mainStatMultiplierRange": { "min": 1.15, "max": 1.3 },
              "affixCountRange": { "min": 2, "max": 3 },
              "dropWeight": 15
            },
            {
              "id": "epic",
              "name": "史诗",
              "tier": 4,
              "mainStatMultiplierRange": { "min": 1.3, "max": 1.5 },
              "affixCountRange": { "min": 3, "max": 4 },
              "dropWeight": 4
            },
            {
              "id": "legendary",
              "name": "传说",
              "tier": 5,
              "mainStatMultiplierRange": { "min": 1.5, "max": 1.75 },
              "affixCountRange": { "min": 4, "max": 5 },
              "dropWeight": 1
            }
          ]
        }
    """.trimIndent()

    val affixes = """
        {
          "affixes": [
            {
              "id": "atk_flat",
              "type": "ATK",
              "valueRange": { "min": 2, "max": 6 },
              "weight": 30,
              "rarityGate": 1,
              "stackRule": "UNIQUE"
            },
            {
              "id": "def_flat",
              "type": "DEF",
              "valueRange": { "min": 2, "max": 6 },
              "weight": 30,
              "rarityGate": 1,
              "stackRule": "UNIQUE"
            },
            {
              "id": "hp_flat",
              "type": "HP",
              "valueRange": { "min": 8, "max": 18 },
              "weight": 25,
              "rarityGate": 1,
              "stackRule": "UNIQUE"
            },
            {
              "id": "crit",
              "type": "CRIT",
              "valueRange": { "min": 1, "max": 4 },
              "weight": 15,
              "rarityGate": 2,
              "stackRule": "UNIQUE"
            },
            {
              "id": "speed",
              "type": "SPEED",
              "valueRange": { "min": 1, "max": 3 },
              "weight": 15,
              "rarityGate": 2,
              "stackRule": "UNIQUE"
            },
            {
              "id": "hit",
              "type": "HIT",
              "valueRange": { "min": 1, "max": 3 },
              "weight": 12,
              "rarityGate": 3,
              "stackRule": "UNIQUE"
            }
          ]
        }
    """.trimIndent()

    val equipments = """
        {
          "equipments": [
            {
              "id": "rusty_spear",
              "name": "锈蚀长矛",
              "slot": "WEAPON",
              "rarity": "common",
              "levelReq": 1,
              "baseStats": { "ATK": 6 },
              "statScale": { "ATK": 0.8 },
              "affixCount": { "min": 0, "max": 1 },
              "affixPool": ["atk_flat", "crit", "speed"],
              "enhanceMax": 5,
              "sellValue": 6,
              "salvageYield": 1
            },
            {
              "id": "hunter_armor",
              "name": "猎人皮甲",
              "slot": "ARMOR",
              "rarity": "uncommon",
              "levelReq": 1,
              "baseStats": { "DEF": 5, "HP": 10 },
              "statScale": { "DEF": 0.6, "HP": 1.2 },
              "affixCount": { "min": 1, "max": 2 },
              "affixPool": ["def_flat", "hp_flat", "speed"],
              "enhanceMax": 5,
              "sellValue": 8,
              "salvageYield": 2
            },
            {
              "id": "jade_helm",
              "name": "碧玉头盔",
              "slot": "HELM",
              "rarity": "rare",
              "levelReq": 2,
              "baseStats": { "DEF": 6, "HP": 12 },
              "statScale": { "DEF": 0.7, "HP": 1.4 },
              "affixCount": { "min": 2, "max": 3 },
              "affixPool": ["def_flat", "hp_flat", "hit"],
              "enhanceMax": 7,
              "sellValue": 12,
              "salvageYield": 3
            },
            {
              "id": "shadow_charm",
              "name": "幽影护符",
              "slot": "ACCESSORY",
              "rarity": "epic",
              "levelReq": 3,
              "baseStats": { "CRIT": 3, "SPEED": 1 },
              "statScale": { "CRIT": 0.4, "SPEED": 0.2 },
              "affixCount": { "min": 3, "max": 4 },
              "affixPool": ["crit", "speed", "hit"],
              "enhanceMax": 9,
              "sellValue": 18,
              "salvageYield": 4
            }
          ]
        }
    """.trimIndent()

    val lootTables = """
        {
          "lootTables": [
            {
              "id": "enemy_tier_1",
              "sourceType": "ENEMY",
              "tier": 1,
              "guarantee": { "counterKey": "enemy_basic", "threshold": 5, "grantRarityMin": 2 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "rusty_spear", "weight": 18, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "hunter_armor", "weight": 12, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 50, "min": 4, "max": 8 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 20, "min": 1, "max": 2 }
              ]
            },
            {
              "id": "enemy_tier_2",
              "sourceType": "ENEMY",
              "tier": 2,
              "guarantee": { "counterKey": "enemy_basic", "threshold": 5, "grantRarityMin": 3 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "hunter_armor", "weight": 18, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "jade_helm", "weight": 10, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 50, "min": 6, "max": 12 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 22, "min": 1, "max": 3 }
              ]
            },
            {
              "id": "enemy_tier_3",
              "sourceType": "ENEMY",
              "tier": 3,
              "guarantee": { "counterKey": "enemy_basic", "threshold": 4, "grantRarityMin": 4 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "jade_helm", "weight": 16, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "shadow_charm", "weight": 6, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 50, "min": 8, "max": 16 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 28, "min": 2, "max": 4 }
              ]
            },
            {
              "id": "event_tier_1",
              "sourceType": "EVENT",
              "tier": 1,
              "guarantee": { "counterKey": "event_basic", "threshold": 4, "grantRarityMin": 2 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "rusty_spear", "weight": 16, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "hunter_armor", "weight": 10, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 54, "min": 3, "max": 7 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 20, "min": 1, "max": 2 }
              ]
            },
            {
              "id": "event_tier_2",
              "sourceType": "EVENT",
              "tier": 2,
              "guarantee": { "counterKey": "event_basic", "threshold": 4, "grantRarityMin": 3 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "hunter_armor", "weight": 16, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "jade_helm", "weight": 8, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 54, "min": 5, "max": 11 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 22, "min": 1, "max": 3 }
              ]
            },
            {
              "id": "event_tier_3",
              "sourceType": "EVENT",
              "tier": 3,
              "guarantee": { "counterKey": "event_basic", "threshold": 3, "grantRarityMin": 4 },
              "weightedPool": [
                { "type": "EQUIPMENT", "refId": "jade_helm", "weight": 14, "min": 1, "max": 1 },
                { "type": "EQUIPMENT", "refId": "shadow_charm", "weight": 6, "min": 1, "max": 1 },
                { "type": "GOLD", "refId": "gold", "weight": 56, "min": 7, "max": 14 },
                { "type": "MATERIAL", "refId": "fiber", "weight": 24, "min": 2, "max": 4 }
              ]
            }
          ]
        }
    """.trimIndent()
}
