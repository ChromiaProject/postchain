package net.postchain.gtv.yaml

import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeId
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.math.BigInteger

class GtvConstructor(theRoot: Class<*>) : Constructor(theRoot) {
    init {
        yamlConstructors[Tag.BINARY] = ConstructYamlBinary()
        yamlConstructors[Tag.INT] = ConstructYamlIntGtv()
        yamlClassConstructors[NodeId.scalar] = ConstructScalarByteArray()
        yamlClassConstructors[NodeId.mapping] = ConstructMappingGtv()
        yamlClassConstructors[NodeId.sequence] = ConstructSequenceGtv()
    }

    private inner class ConstructYamlBinary : SafeConstructor.ConstructYamlBinary() {
        override fun construct(node: Node) = prepareByteArray(node)
                .run {
                    when (node.type) {
                        WrappedByteArray::class.java -> hexStringToWrappedByteArray()
                        else -> hexStringToByteArray()
                    }
                }
    }

    private inner class ConstructMappingGtv : ConstructMapping() {
        override fun construct(node: Node): Any {
            if (node.type != Gtv::class.java) return super.construct(node)
            node as MappingNode
            val ma = constructMapping(node) as Map<*, *>
            val m = ma.map { it.key as String to toGtv(it.value!!) as Gtv }.toMap()
            return gtv(m)
        }
    }

    private inner class ConstructSequenceGtv : ConstructSequence() {
        override fun construct(node: Node): Any {
            if (node.type != Gtv::class.java) super.construct(node)
            val c = constructSequence(node as SequenceNode)
            return gtv(c.map { toGtv(it) })
        }
    }

    private inner class ConstructYamlIntGtv : ConstructYamlInt() {
        override fun construct(node: Node): Any {
            node as ScalarNode
            if (node.value.startsWith("0x")) return node.value.substringAfter("0x").hexStringToByteArray()
            return super.construct(node)
        }
    }

    private open inner class ConstructScalarByteArray : ConstructScalar() {
        override fun construct(node: Node): Any {
            return when (node.type) {
                Gtv::class.java -> toGtv(constructScalar(node as ScalarNode))
                ByteArray::class.java -> prepareByteArray(node).hexStringToByteArray()
                WrappedByteArray::class.java -> prepareByteArray(node).hexStringToWrappedByteArray()
                else -> super.construct(node)
            }
        }
    }

    private fun prepareByteArray(node: Node) = (node as ScalarNode).value
            .substringAfter("0x")
            .replace("\\s".toRegex(), "")

    private fun toGtv(obj: Any): Gtv = when (obj) {
        is String -> gtv(obj)
        is Int -> gtv(obj.toLong())
        is Long -> gtv(obj)
        is Boolean -> gtv(obj)
        is Double -> gtv(obj.toString())
        is ByteArray -> gtv(obj)
        is BigInteger -> gtv(obj)
        is Collection<*> -> gtv(obj.map { toGtv(it!!) })
        is Map<*, *> -> gtv(obj.map { it.key as String to toGtv(it.value!!) }.toMap())
        else -> throw IllegalArgumentException("Cannot parse value $obj to gtv")
    }
}
