package com.redwolf.proto.core

import org.objectweb.asm.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

class AsmScanner(private val mapper: ModuleMapper, private val g: GraphBuilder) {
    fun scanClassBytes(bytes: ByteArray) {
        var ownerMod = ":unknown"
        val cr = ClassReader(bytes)
        cr.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                val owner = name.replace('/', '.')
                ownerMod = mapper.moduleOf(owner)
                g.ensureNode(ownerMod)
            }
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        val calleeOwner = owner.replace('/', '.')
                        val calleeMod = mapper.moduleOf(calleeOwner)
                        if (calleeMod != ownerMod) {
                            g.addEdge(ownerMod, calleeMod)
                            g.addUsed(ownerMod, calleeMod, "$calleeOwner->$name$descriptor")
                        }
                    }
                    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any) {
                        bsmArgs.forEach { arg ->
                            if (arg is Handle) {
                                val co = arg.owner.replace('/', '.')
                                val cm = mapper.moduleOf(co)
                                if (cm != ownerMod) {
                                    g.addEdge(ownerMod, cm)
                                    g.addUsed(ownerMod, cm, "$co->${arg.name}${arg.desc}")
                                }
                            }
                        }
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    fun scanPaths(inputs: Collection<Path>) {
        inputs.forEach { p ->
            val s = p.toString()
            if (Files.isDirectory(p)) {
                Files.walk(p).use { stream ->
                    stream.filter { it.toString().endsWith(".class") }.forEach { scanClassBytes(Files.readAllBytes(it)) }
                }
            } else if (s.endsWith(".jar")) {
                ZipInputStream(Files.newInputStream(p)).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && e.name.endsWith(".class")) {
                            val buf = ByteArrayOutputStream()
                            zis.copyTo(buf)
                            scanClassBytes(buf.toByteArray())
                        }
                        e = zis.nextEntry
                    }
                }
            }
        }
    }
}