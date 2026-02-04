package com.jungleadventure.shared

import kotlin.random.Random

data class StageContext(
    val chapter: Int,
    val difficulty: Int,
    val turn: Int
)

data class StageValidationResult(
    val stageId: String,
    val isValid: Boolean,
    val issues: List<String>,
    val reachable: Set<String>
)

class StageEngine(
    stages: List<StageDefinition>,
    nodes: List<NodeDefinition>,
    private val eventEngine: EventEngine
) {
    private val logTag = "关卡引擎"
    private val nodeMap = nodes.associateBy { it.id }
    private val stageList = stages.map { normalizeStage(it) }
    private val stageMap = stageList.associateBy { it.id }
    private val stageNodeIdSets = stageList.associate { stage ->
        stage.id to stage.nodes.toSet()
    }

    init {
        validateAllStages()
    }

    fun startStageForChapter(
        chapter: Int,
        rng: Random,
        difficulty: Int? = null
    ): StageRuntime {
        val chapterCandidates = stageList.filter { it.chapter == chapter }
        if (chapterCandidates.isEmpty()) {
            GameLogger.warn(logTag, "章节无对应关卡，降级随机关卡：章节=$chapter")
        }
        val difficultyCandidates = if (difficulty == null) {
            chapterCandidates
        } else {
            chapterCandidates.filter { it.difficulty == difficulty }
        }
        if (difficulty != null && chapterCandidates.isNotEmpty() && difficultyCandidates.isEmpty()) {
            GameLogger.warn(logTag, "章节匹配但难度无关卡，降级随机章节关卡：章节=$chapter 难度=$difficulty")
        }
        val candidates = when {
            difficultyCandidates.isNotEmpty() -> difficultyCandidates
            chapterCandidates.isNotEmpty() -> chapterCandidates
            else -> stageList
        }
        val stage = if (candidates.isNotEmpty()) {
            candidates[rng.nextInt(candidates.size)]
        } else {
            defaultStages().first()
        }
        val difficultyLabel = difficulty?.toString() ?: "自动"
        GameLogger.info(
            logTag,
            "选择关卡：章节=$chapter 难度=$difficultyLabel 关卡编号=${stage.id} 名称=${stage.name}"
        )
        return StageRuntime(
            stage = stage,
            currentNodeId = stage.entry,
            visited = setOf(stage.entry),
            completed = stage.entry == stage.exit,
            command = stage.command,
            guardianGroupId = null,
            path = listOf(stage.entry),
            lastMoveReason = "进入关卡"
        )
    }

    fun restoreStage(
        stageId: String?,
        nodeId: String?,
        visitedNodes: List<String>,
        completed: Boolean,
        chapter: Int,
        rng: Random,
        guardianGroupId: String?
    ): StageRuntime {
        val stage = stageId?.let { stageMap[it] } ?: run {
            GameLogger.warn(logTag, "存档关卡不存在，改用章节关卡：关卡编号=$stageId 章节=$chapter")
            return startStageForChapter(chapter, rng)
        }
        val validNodeId = nodeId?.takeIf { nodeMap.containsKey(it) } ?: stage.entry
        val filteredVisited = visitedNodes.filter { stage.nodes.contains(it) && nodeMap.containsKey(it) }.toMutableSet()
        filteredVisited.add(validNodeId)
        val isCompleted = completed || validNodeId == stage.exit
        val restoredPath = if (visitedNodes.isNotEmpty()) {
            visitedNodes.filter { stage.nodes.contains(it) && nodeMap.containsKey(it) }
        } else {
            listOf(validNodeId)
        }
        GameLogger.info(
            logTag,
            "恢复关卡：关卡编号=${stage.id} 节点编号=$validNodeId 已访问=${filteredVisited.size}/${stage.nodes.size} 已完成=$isCompleted"
        )
        return StageRuntime(
            stage = stage,
            currentNodeId = validNodeId,
            visited = filteredVisited,
            completed = isCompleted,
            command = stage.command,
            guardianGroupId = guardianGroupId,
            path = restoredPath,
            lastMoveReason = "读取存档"
        )
    }

    fun currentNode(runtime: StageRuntime): NodeDefinition? {
        val node = nodeMap[runtime.currentNodeId]
        if (node == null) {
            GameLogger.warn(logTag, "节点不存在：节点编号=${runtime.currentNodeId}")
        }
        return node
    }

    fun moveToNextNode(runtime: StageRuntime, rng: Random, context: StageContext? = null): StageRuntime {
        if (runtime.completed) {
            GameLogger.info(logTag, "关卡已完成，保持当前节点：关卡编号=${runtime.stage.id}")
            return runtime
        }
        val node = currentNode(runtime) ?: return runtime.copy(completed = true)
        val neighbors = resolveNeighbors(runtime.stage, node)
        if (neighbors.isEmpty()) {
            GameLogger.info(logTag, "节点无后继，标记完成：节点编号=${node.id}")
            return runtime.copy(completed = true, lastMoveReason = "无后继节点")
        }
        val selectable = neighbors.filter { nodeAvailable(it, runtime, context, rng) }
        val unvisited = selectable.filterNot { runtime.visited.contains(it.id) }
        val selected = when {
            unvisited.isNotEmpty() -> weightedPickNode(unvisited, rng)
            selectable.isNotEmpty() -> weightedPickNode(selectable, rng)
            else -> {
                GameLogger.warn(logTag, "节点条件均不满足，强制随机：节点编号=${node.id}")
                weightedPickNode(neighbors, rng)
            }
        }
        val nextNodeId = selected.id
        val updatedVisited = runtime.visited + nextNodeId
        val isCompleted = nextNodeId == runtime.stage.exit
        val reason = buildString {
            append("常规推进")
            if (unvisited.isNotEmpty()) append("·优先未访问")
            if (selected.hidden) append("·隐藏路径")
        }
        GameLogger.info(
            logTag,
            "移动节点：${runtime.currentNodeId} -> $nextNodeId 已访问=${updatedVisited.size}/${runtime.stage.nodes.size}"
        )
        return StageRuntime(
            stage = runtime.stage,
            currentNodeId = nextNodeId,
            visited = updatedVisited,
            completed = isCompleted,
            command = runtime.command,
            guardianGroupId = runtime.guardianGroupId,
            path = runtime.path + nextNodeId,
            lastMoveReason = reason
        )
    }

    fun moveToNode(runtime: StageRuntime, nodeId: String): StageRuntime? {
        if (!nodeMap.containsKey(nodeId)) {
            GameLogger.warn(logTag, "强制节点不存在：节点编号=$nodeId")
            return null
        }
        if (!runtime.stage.nodes.contains(nodeId)) {
            GameLogger.warn(logTag, "强制节点不属于关卡：关卡编号=${runtime.stage.id} 节点编号=$nodeId")
            return null
        }
        val updatedVisited = runtime.visited + nodeId
        val isCompleted = nodeId == runtime.stage.exit
        GameLogger.info(
            logTag,
            "强制移动节点：${runtime.currentNodeId} -> $nodeId 已访问=${updatedVisited.size}/${runtime.stage.nodes.size}"
        )
        return StageRuntime(
            stage = runtime.stage,
            currentNodeId = nodeId,
            visited = updatedVisited,
            completed = isCompleted,
            command = runtime.command,
            guardianGroupId = runtime.guardianGroupId,
            path = runtime.path + nodeId,
            lastMoveReason = "强制跳转"
        )
    }

    fun eventForNode(runtime: StageRuntime, chapter: Int, rng: Random): EventDefinition? {
        val node = currentNode(runtime) ?: return null
        val event = node.eventId?.let { eventEngine.eventById(it) }
            ?: eventEngine.nextEventForNode(chapter, node.type, rng)
        if (event == null) {
            GameLogger.warn(logTag, "节点事件为空：节点编号=${node.id}")
        } else {
            GameLogger.info(logTag, "节点事件：节点编号=${node.id} 事件编号=${event.eventId}")
        }
        return event
    }

    private fun resolveNeighbors(stage: StageDefinition, node: NodeDefinition): List<NodeDefinition> {
        val stageNodes = stageNodeIdSets[stage.id] ?: stage.nodes.toSet()
        val neighborIds = node.neighbors.filter { stageNodes.contains(it) }
        val neighbors = neighborIds.mapNotNull { nodeMap[it] }
        if (neighborIds.size != neighbors.size) {
            GameLogger.warn(
                logTag,
                "节点邻接缺失：节点编号=${node.id} 配置=${neighborIds.size} 实际=${neighbors.size}"
            )
        }
        return neighbors
    }

    private fun nodeAvailable(
        node: NodeDefinition,
        runtime: StageRuntime,
        context: StageContext?,
        rng: Random
    ): Boolean {
        if (node.conditions.isEmpty()) return true
        for (condition in node.conditions) {
            if (!checkCondition(condition, runtime, context, rng)) {
                GameLogger.info(
                    logTag,
                    "节点条件未通过：节点编号=${node.id} 条件=$condition"
                )
                return false
            }
        }
        return true
    }

    private fun checkCondition(
        raw: String,
        runtime: StageRuntime,
        context: StageContext?,
        rng: Random
    ): Boolean {
        val condition = raw.trim()
        if (condition.isEmpty()) return true
        return when {
            condition.startsWith("visited:") -> {
                val nodeId = condition.removePrefix("visited:").trim()
                val result = runtime.visited.contains(nodeId)
                GameLogger.info(logTag, "条件检查 visited：节点=$nodeId 结果=$result")
                result
            }
            condition.startsWith("not_visited:") -> {
                val nodeId = condition.removePrefix("not_visited:").trim()
                val result = !runtime.visited.contains(nodeId)
                GameLogger.info(logTag, "条件检查 not_visited：节点=$nodeId 结果=$result")
                result
            }
            condition.startsWith("turn>=") -> {
                val threshold = condition.removePrefix("turn>=").trim().toIntOrNull()
                val turn = context?.turn
                val result = threshold != null && turn != null && turn >= threshold
                GameLogger.info(logTag, "条件检查 turn>=：阈值=$threshold 回合=$turn 结果=$result")
                result
            }
            condition.startsWith("turn<=") -> {
                val threshold = condition.removePrefix("turn<=").trim().toIntOrNull()
                val turn = context?.turn
                val result = threshold != null && turn != null && turn <= threshold
                GameLogger.info(logTag, "条件检查 turn<=：阈值=$threshold 回合=$turn 结果=$result")
                result
            }
            condition.startsWith("chapter>=") -> {
                val threshold = condition.removePrefix("chapter>=").trim().toIntOrNull()
                val chapter = context?.chapter
                val result = threshold != null && chapter != null && chapter >= threshold
                GameLogger.info(logTag, "条件检查 chapter>=：阈值=$threshold 章节=$chapter 结果=$result")
                result
            }
            condition.startsWith("chapter<=") -> {
                val threshold = condition.removePrefix("chapter<=").trim().toIntOrNull()
                val chapter = context?.chapter
                val result = threshold != null && chapter != null && chapter <= threshold
                GameLogger.info(logTag, "条件检查 chapter<=：阈值=$threshold 章节=$chapter 结果=$result")
                result
            }
            condition.startsWith("difficulty>=") -> {
                val threshold = condition.removePrefix("difficulty>=").trim().toIntOrNull()
                val difficulty = context?.difficulty
                val result = threshold != null && difficulty != null && difficulty >= threshold
                GameLogger.info(logTag, "条件检查 difficulty>=：阈值=$threshold 难度=$difficulty 结果=$result")
                result
            }
            condition.startsWith("difficulty<=") -> {
                val threshold = condition.removePrefix("difficulty<=").trim().toIntOrNull()
                val difficulty = context?.difficulty
                val result = threshold != null && difficulty != null && difficulty <= threshold
                GameLogger.info(logTag, "条件检查 difficulty<=：阈值=$threshold 难度=$difficulty 结果=$result")
                result
            }
            condition.startsWith("chance:") || condition.startsWith("chance<=") -> {
                val value = condition.substringAfter(':').trim()
                val rawRate = value.toDoubleOrNull()
                if (rawRate == null) {
                    GameLogger.warn(logTag, "条件 chance 解析失败：$condition")
                    true
                } else {
                    val rate = if (rawRate > 1.0) rawRate / 100.0 else rawRate
                    val roll = rng.nextDouble()
                    val result = roll <= rate
                    GameLogger.info(
                        logTag,
                        "条件检查 chance：阈值=$rate 掷骰=$roll 结果=$result"
                    )
                    result
                }
            }
            else -> {
                GameLogger.warn(logTag, "未知条件，默认通过：$condition")
                true
            }
        }
    }

    private fun weightedPickNode(pool: List<NodeDefinition>, rng: Random): NodeDefinition {
        val total = pool.sumOf { it.weight.coerceAtLeast(0) }
        if (total <= 0) return pool.first()
        var roll = rng.nextInt(total)
        for (node in pool) {
            roll -= node.weight.coerceAtLeast(0)
            if (roll < 0) return node
        }
        return pool.last()
    }

    private fun normalizeStage(stage: StageDefinition): StageDefinition {
        val validNodes = stage.nodes.filter { nodeMap.containsKey(it) }
        if (validNodes.isEmpty()) {
            GameLogger.warn(logTag, "关卡节点全无效：关卡编号=${stage.id}，保留原配置")
            return stage
        }
        val entry = if (validNodes.contains(stage.entry)) stage.entry else validNodes.first()
        val exit = if (validNodes.contains(stage.exit)) stage.exit else validNodes.last()
        if (entry != stage.entry || exit != stage.exit || validNodes.size != stage.nodes.size) {
            GameLogger.warn(
                logTag,
                "关卡节点已归一化：关卡编号=${stage.id} entry=${stage.entry}->$entry exit=${stage.exit}->$exit 有效节点=${validNodes.size}/${stage.nodes.size}"
            )
        }
        return stage.copy(nodes = validNodes, entry = entry, exit = exit)
    }

    private fun validateAllStages() {
        if (stageList.isEmpty()) {
            GameLogger.warn(logTag, "关卡列表为空，无法进行校验")
            return
        }
        stageList.forEach { stage ->
            val result = validateStage(stage)
            if (result.isValid) {
                GameLogger.info(
                    logTag,
                    "关卡校验通过：关卡编号=${stage.id} 可达节点=${result.reachable.size}/${stage.nodes.size}"
                )
            } else {
                GameLogger.warn(
                    logTag,
                    "关卡校验失败：关卡编号=${stage.id} 问题=${result.issues.joinToString("；")}"
                )
            }
        }
    }

    private fun validateStage(stage: StageDefinition): StageValidationResult {
        val issues = mutableListOf<String>()
        val stageNodes = stage.nodes.toSet()
        if (!stageNodes.contains(stage.entry)) {
            issues.add("入口节点缺失")
        }
        if (!stageNodes.contains(stage.exit)) {
            issues.add("出口节点缺失")
        }
        val missingNodes = stage.nodes.filterNot { nodeMap.containsKey(it) }
        if (missingNodes.isNotEmpty()) {
            issues.add("节点不存在=${missingNodes.joinToString(",")}")
        }
        stage.nodes.forEach { nodeId ->
            val node = nodeMap[nodeId] ?: return@forEach
            val outOfStage = node.neighbors.filterNot { stageNodes.contains(it) }
            if (outOfStage.isNotEmpty()) {
                issues.add("节点邻接越界:${node.id}->${outOfStage.joinToString(",")}")
            }
            if (node.neighbors.isEmpty() && node.id != stage.exit) {
                issues.add("非出口死路:${node.id}")
            }
        }
        val reachable = reachableNodes(stage)
        if (!reachable.contains(stage.exit)) {
            issues.add("出口不可达")
        }
        return StageValidationResult(
            stageId = stage.id,
            isValid = issues.isEmpty(),
            issues = issues,
            reachable = reachable
        )
    }

    private fun reachableNodes(stage: StageDefinition): Set<String> {
        val stageNodes = stage.nodes.toSet()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(stage.entry)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val node = nodeMap[current] ?: continue
            node.neighbors.filter { stageNodes.contains(it) }.forEach { queue.add(it) }
        }
        return visited
    }
}
