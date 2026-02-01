# 丛林大冒险配置指南（角色 / 技能 / 装备）

面向游戏开发者的 JSON 配置说明。所有配置文件统一使用 UTF-8 编码。

## 配置文件位置

角色与技能：
- `shared/src/commonMain/resources/cp/cp.json`
- `shared/src/commonMain/resources/cp/ski.json`

装备：
- `shared/src/commonMain/resources/data/equipments.json`

Android 资源有一份镜像（需要同步）：
- `androidApp/src/main/assets/cp/cp.json`
- `androidApp/src/main/assets/cp/ski.json`
- `androidApp/src/main/assets/data/equipments.json`

> 建议：修改 `shared/` 后同步到 `androidApp/`，保持两端一致。

## 一、创建新角色（Character）

文件：`cp.json`，在 `characters` 数组中新增一个对象。

必填字段说明：
- `id`：角色唯一 ID（推荐 `ch_xxx`）
- `name`：显示名称
- `role`：定位描述
- `starting`：是否默认解锁
- `unlock`：解锁条件描述（未解锁时显示）
- `stats`：初始属性（`hp/atk/def/speed/perception`）
- `growth`：成长属性（每级增加，`hpMax/mpMax/atk/def/speed`）
- `passiveSkillId`：被动技能 ID（来自 `ski.json`）
- `activeSkillIds`：主动技能 ID 列表（来自 `ski.json`）
- `ultimateSkillId`：终极技能 ID（来自 `ski.json`，可为空字符串）

示例：
```json
{
  "id": "ch_ranger",
  "name": "游侠",
  "role": "远程/机动",
  "starting": false,
  "unlock": "通关第2章解锁",
  "stats": {
    "hp": 108,
    "atk": 24,
    "def": 10,
    "speed": 26,
    "perception": 26
  },
  "growth": {
    "hpMax": 10,
    "mpMax": 4,
    "atk": 3,
    "def": 1,
    "speed": 2
  },
  "passiveSkillId": "sk_pass_keen_eye",
  "activeSkillIds": ["sk_act_double_shot", "sk_act_smoke"],
  "ultimateSkillId": "sk_ult_rain_arrow"
}
```

## 二、创建新技能（Skill）

文件：`ski.json`，在 `skills` 数组中新增一个对象。

必填字段说明：
- `id`：技能唯一 ID（推荐 `sk_pass_xxx` / `sk_act_xxx` / `sk_ult_xxx`）
- `name`：技能名称
- `type`：`PASSIVE` / `ACTIVE` / `ULTIMATE`
- `target`：`SELF` / `ENEMY` / `ALLY` / `ALL_ENEMY` / `ALL_ALLY`
- `cost`：消耗（整数）
- `cooldown`：冷却回合（整数）
- `effects`：效果数组
- `desc`：技能描述文本

`effects` 子项字段：
- `type`：效果类型（如 `DAMAGE`、`HEAL_MAX_HP`、`HIT_UP` 等）
- `value`：数值（小数表示百分比，如 `0.8` = 80%）
- `scaling`：伤害/治疗系数来源（如 `ATK`）
- `note`：人类可读说明，会用于界面展示（建议填写）

示例：
```json
{
  "id": "sk_act_double_shot",
  "name": "双发射击",
  "type": "ACTIVE",
  "target": "ENEMY",
  "cost": 7,
  "cooldown": 2,
  "effects": [
    { "type": "DAMAGE", "value": 0.75, "scaling": "ATK", "note": "75%ATK伤害" },
    { "type": "HIT_UP", "value": 0.10, "note": "命中率+10%" }
  ],
  "desc": "连续射击并提升命中。"
}
```

## 三、创建新装备（Equipment）

文件：`equipments.json`，在 `equipments` 数组中新增一个对象。

必填字段说明：
- `id`：装备唯一 ID
- `name`：显示名称
- `slot`：`WEAPON` / `ARMOR` / `HELM` / `ACCESSORY`
- `rarity`：`common` / `uncommon` / `rare` / `epic` / `legend`
- `levelReq`：等级需求
- `baseStats`：基础属性（键为 `ATK/DEF/HP/SPEED/HIT/EVADE/CRIT/CRIT_RESIST`）
- `statScale`：成长系数（同上）
- `affixCount`：词条数量范围 `{ "min": x, "max": y }`
- `affixPool`：可抽取词条 ID 列表（参考词条配置）
- `enhanceMax`：强化上限
- `sellValue`：售出金币
- `salvageYield`：分解材料

示例：
```json
{
  "id": "wind_bow",
  "name": "疾风长弓",
  "slot": "WEAPON",
  "rarity": "rare",
  "levelReq": 3,
  "baseStats": { "ATK": 9, "SPEED": 2 },
  "statScale": { "ATK": 0.9, "SPEED": 0.3 },
  "affixCount": { "min": 1, "max": 3 },
  "affixPool": ["atk_flat", "speed", "crit"],
  "enhanceMax": 8,
  "sellValue": 14,
  "salvageYield": 3
}
```

## 四、常见注意事项

- 角色技能 ID 必须存在于 `ski.json`。
- `effects.note` 会直接展示在技能详情中，建议填写清晰的可读文本。
- 修改 `shared/` 后同步到 `androidApp/` 资源目录，避免端侧读取不同配置。
- JSON 语法请保持严格合法（逗号、引号、数组等）。

## 五、效果类型 / 词条类型对照表

### 1) 技能效果类型（`effects.type`）

以下为当前项目内已有或已识别的常用类型（建议优先使用这些）：
- `DAMAGE`：伤害（建议配 `value` + `scaling`，并填写 `note`）
- `HEAL_MAX_HP`：回复最大生命百分比
- `HIT_UP`：命中率提升
- `DAMAGE_TAKEN_DOWN`：受到伤害降低
- `DAMAGE_VS_ELITE_BOSS`：对精英/首领伤害提升
- `CRIT_UP`：暴击率提升
- `EVADE_UP`：闪避提升
- `DROP_RATE`：掉落概率提升
- `HIDDEN_PATH`：隐藏路径触发提升
- `MATERIAL_GAIN`：材料收益提升
- `ENCOUNTER_RATE_DOWN`：遇敌率降低
- `ROOT`：束缚
- `REMOVE_DEBUFF`：移除负面状态
- `DEBUFF_DURATION_MINUS`：负面持续回合减少
- `REVEAL_NEXT_NODE`：显示下一节点风险
- `DISARM_TRAP`：解除陷阱判定
- `MATERIAL_BONUS`：事件材料收益提升
- `CHEST_NO_TRAP`：开箱不触发陷阱概率提升
- `GOLD_BONUS`：金币收益提升
- `SHIELD_BREAK`：破除护盾/障碍

> 注意：界面优先展示 `note`。如果不写 `note`，会使用内置兜底文案，但可能不够精确。

### 2) 伤害/数值缩放字段（`effects.scaling`）

可用取值（与属性一致）：`ATK` / `DEF` / `HP` / `SPD`

### 3) 装备词条类型（`affixes.type`）

当前词条类型来自 `data/affixes.json`：
- `ATK` / `DEF` / `HP`
- `CRIT` / `SPEED` / `HIT`

### 4) 装备基础属性键（`baseStats` / `statScale`）

可用键来自装备系统的属性类型：
- `ATK` / `DEF` / `HP` / `SPEED`
- `HIT` / `EVADE` / `CRIT` / `CRIT_RESIST`

如需扩展效果类型或词条类型，请同步更新逻辑侧与配置描述。
