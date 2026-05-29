package com.ai.assistance.operit.data.model.graph

data class Edge(
    val sourceId: Long,
    val targetId: Long,
    val weight: Float,
    val label: String = ""
)

data class Node(
    val id: Long,
    val label: String,
    val type: String = "memory",
    val group: Int = 0
)

data class Graph(
    val nodes: List<Node>,
    val edges: List<Edge>
)
