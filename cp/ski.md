技能设计说明（已落地到 ski.json）：

字段说明：
- id：技能唯一标识
- type：PASSIVE/ACTIVE
- target：SELF/ENEMY/EVENT
- cost：消耗（体力/能量等）
- cooldown：冷却回合
- effects：效果数组（类型/数值/注释）

技能列表（按角色）：

探险家
- 发现遗迹（PASSIVE）：掉落+10%，隐藏路径触发+15%
- 勘探（ACTIVE）：显示下一节点风险，命中+10%持续2回合

猎人
- 标记弱点（PASSIVE）：对精英/首领伤害+10%
- 束缚陷阱（ACTIVE）：80%ATK伤害，束缚1回合

祭司
- 神谕护佑（PASSIVE）：负面状态持续回合-1
- 净化（ACTIVE）：移除1个debuff并回复15%最大HP

工匠
- 拆解回收（PASSIVE）：材料收益+20%
- 机关破解（ACTIVE）：解除陷阱判定并提高材料收益

盗贼
- 潜行（PASSIVE）：遇敌率-15%
- 速开（ACTIVE）：开箱不触发陷阱概率+70%，成功额外金币

战士
- 硬化（PASSIVE）：受到伤害-8%
- 破墙（ACTIVE）：120%ATK伤害，破除护盾/障碍效果
