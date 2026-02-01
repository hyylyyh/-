package com.jungleadventure.shared

import kotlinx.serialization.Serializable

@Serializable
data class StageFile(
    val schema: String,
    val stages: List<StageDefinition>
)

@Serializable
data class StageDefinition(
    val id: String,
    val name: String,
    val chapter: Int,
    val difficulty: Int,
    val floors: Int,
    val nodes: List<String>,
    val entry: String,
    val exit: String,
    val command: String = "",
    val guardianPool: List<String> = emptyList()
)

@Serializable
data class NodeFile(
    val schema: String,
    val nodes: List<NodeDefinition>
)

@Serializable
data class NodeDefinition(
    val id: String,
    val type: String,
    val neighbors: List<String>,
    val eventId: String? = null,
    val conditions: List<String> = emptyList()
)

data class StageRuntime(
    val stage: StageDefinition,
    val currentNodeId: String,
    val visited: Set<String>,
    val completed: Boolean,
    val command: String,
    val guardianGroupId: String?
)

fun defaultStages(): List<StageDefinition> {
    return listOf(
        StageDefinition(
            id = "stage_default",
            name = "默认试炼",
            chapter = 1,
            difficulty = 1,
            floors = 1,
            nodes = listOf("default_node"),
            entry = "default_node",
            exit = "default_node",
            command = "JUNGLE-TEST",
            guardianPool = emptyList()
        )
    )
}

fun defaultNodes(): List<NodeDefinition> {
    return listOf(
        NodeDefinition(
            id = "default_node",
            type = "EVENT",
            neighbors = emptyList(),
            eventId = null,
            conditions = emptyList()
        )
    )
}
