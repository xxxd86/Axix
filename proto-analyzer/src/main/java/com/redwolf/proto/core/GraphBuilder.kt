package com.redwolf.proto.core

class GraphBuilder {
    val nodeSize: MutableMap<String, Int> = linkedMapOf()
    val edges: MutableMap<String, MutableMap<String, Int>> = linkedMapOf()
    val used: MutableMap<String, MutableMap<String, MutableSet<String>>> = linkedMapOf()

    fun ensureNode(m: String) { nodeSize.putIfAbsent(m, 0) }

    fun addEdge(u: String, v: String) {
        edges.computeIfAbsent(u) { linkedMapOf() }.merge(v, 1, Int::plus)
        nodeSize.merge(u, 1, Int::plus)
    }
    fun addUsed(u: String, v: String, methodSig: String) {
        used.computeIfAbsent(u) { linkedMapOf() }
            .computeIfAbsent(v) { linkedSetOf() }
            .add(methodSig)
    }
}