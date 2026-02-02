package com.jungleadventure.shared

import kotlin.random.Random

class StageEngine(
    stages: List<StageDefinition>,
    nodes: List<NodeDefinition>,
    private val eventEngine: EventEngine
) {
    private val logTag = "关卡引擎"
    private val stageMap = stages.associateBy { it.id }
    private val nodeMap = nodes.associateBy { it.id }
    private val stageList = stages

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
            guardianGroupId = null
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
            guardianGroupId = guardianGroupId
        )
    }

    fun currentNode(runtime: StageRuntime): NodeDefinition? {
        val node = nodeMap[runtime.currentNodeId]
        if (node == null) {
            GameLogger.warn(logTag, "节点不存在：节点编号=${runtime.currentNodeId}")
        }
        return node
    }

    fun moveToNextNode(runtime: StageRuntime, rng: Random): StageRuntime {
        if (runtime.completed) {
            GameLogger.info(logTag, "关卡已完成，保持当前节点：关卡编号=${runtime.stage.id}")
            return runtime
        }
        val node = currentNode(runtime) ?: return runtime.copy(completed = true)
        if (node.neighbors.isEmpty()) {
            GameLogger.info(logTag, "节点无后继，标记完成：节点编号=${node.id}")
            return runtime.copy(completed = true)
        }
        val nextNodeId = node.neighbors.firstOrNull { !runtime.visited.contains(it) }
            ?: node.neighbors[rng.nextInt(node.neighbors.size)]
        val updatedVisited = runtime.visited + nextNodeId
        val isCompleted = nextNodeId == runtime.stage.exit
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
            guardianGroupId = runtime.guardianGroupId
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
            guardianGroupId = runtime.guardianGroupId
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
}
