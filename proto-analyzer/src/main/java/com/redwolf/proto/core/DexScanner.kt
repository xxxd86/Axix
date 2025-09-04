package com.redwolf.proto.core

import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

class DexScanner(private val mapper: ModuleMapper, private val g: GraphBuilder) {
    fun scanApk(apk: Path) {
        ZipInputStream(Files.newInputStream(apk)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.endsWith(".dex")) {
                    val dexBytes = zis.readAllBytes()
                    scanDex(dexBytes)
                }
                e = zis.nextEntry
            }
        }
    }
    private fun scanDex(dexBytes: ByteArray) {
        val df = DexBackedDexFile.fromInputStream(
            Opcodes.getDefault(),
            ByteArrayInputStream(dexBytes)
        )
        for (cls in df.classes) {
            val owner = cls.type.replace('/', '.').removePrefix("L").removeSuffix(";")
            val ownerMod = mapper.moduleOf(owner)
            g.ensureNode(ownerMod)
            for (m in cls.methods) {
                val impl = m.implementation ?: continue
                for (ins in impl.instructions) {
                    val op = ins.opcode.name
                    if (op.startsWith("INVOKE_") && ins is ReferenceInstruction) {
                        val mr = ins.reference as? org.jf.dexlib2.iface.reference.MethodReference ?: continue
                        val calleeOwner = mr.definingClass.replace('/', '.').removePrefix("L").removeSuffix(";")
                        val calleeMod = mapper.moduleOf(calleeOwner)
                        if (calleeMod != ownerMod) {
                            val desc = buildString {
                                append('('); mr.parameterTypes.joinTo(this) { it }; append(')'); append(mr.returnType)
                            }
                            g.addEdge(ownerMod, calleeMod)
                            g.addUsed(ownerMod, calleeMod, "$calleeOwner->${mr.name}$desc")
                        }
                    }
                }
            }
        }
    }
}