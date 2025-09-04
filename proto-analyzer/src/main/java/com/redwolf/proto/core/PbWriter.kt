package com.redwolf.proto.core
import dev.analysis.v1.*
import java.io.File

object PbWriter {
    private fun meta(mode: String) = AnalysisMeta.newBuilder()
        .setToolVersion("shell-analyzer/1.0")
        .setGeneratedAtMs(System.currentTimeMillis())
        .setMode(mode).build()

    private fun kind(m: String) = when {
        m == ":app" -> "app"
        m.startsWith(":feature") -> "feature"
        m == ":androidx" || m == ":kotlin" -> "thirdparty"
        else -> "module"
    }

    fun writeModuleGraph(out: File, g: GraphBuilder, mode: String) {
        val b = ModuleGraph.newBuilder().setMeta(meta(mode))
        g.nodeSize.forEach { (mod, sz) -> b.addNodes(ModuleNode.newBuilder().setId(mod).setType(kind(mod)).setSize(sz)) }
        g.edges.forEach { (u, mp) -> mp.forEach { (v, w) -> b.addEdges(ModuleEdge.newBuilder().setSource(u).setTarget(v).setWeight(w)) } }
        out.parentFile?.mkdirs(); out.outputStream().use { b.build().writeTo(it) }
    }

    fun writeUsed(out: File, g: GraphBuilder, mode: String) {
        val b = UsedMethodsByModule.newBuilder().setMeta(meta(mode))
        g.used.forEach { (caller, cmap) ->
            val cu = CallerUsedMethods.newBuilder().setCallerModule(caller)
            cmap.forEach { (callee, ms) -> cu.addCallees(CalleeMethods.newBuilder().setCalleeModule(callee).addAllMethods(ms)) }
            b.addEntries(cu)
        }
        out.parentFile?.mkdirs(); out.outputStream().use { b.build().writeTo(it) }
    }
}