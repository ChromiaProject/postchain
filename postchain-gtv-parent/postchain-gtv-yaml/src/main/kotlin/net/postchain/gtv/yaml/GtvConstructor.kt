package net.postchain.gtv.yaml

import net.postchain.common.hexStringToByteArray
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
        yamlConstructors[Tag.BINARY] = BinaryConstructor()
        yamlConstructors[Tag.INT] = ConstructYamlIntGtv()
        yamlClassConstructors[NodeId.scalar] = GtvConstr()
        yamlClassConstructors[NodeId.mapping] = ConstructMappingGtv()
        yamlClassConstructors[NodeId.sequence] = ConstructSequenceGtv()
    }

    private inner class BinaryConstructor : SafeConstructor.ConstructYamlBinary() {
        override fun construct(node: Node) = (node as ScalarNode).value
                .substringAfter("0x")
                .replace("\\s".toRegex(), "")
                .hexStringToByteArray()
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

    private inner class ConstructSequenceGtv: ConstructSequence() {
        override fun construct(node: Node): Any {
            if (node.type != Gtv::class.java) super.construct(node)
            val c = constructSequence(node as SequenceNode)
            return gtv(c.map { toGtv(it) })
        }
    }

    private inner class ConstructYamlIntGtv: ConstructYamlInt() {
        override fun construct(node: Node): Any {
            node as ScalarNode
            if (node.value.startsWith("0x")) return node.value.substringAfter("0x").hexStringToByteArray()
            return super.construct(node)
        }
    }

    private inner class GtvConstr : ScalarConstructor() {
        override fun construct(nnode: Node): Any {
            if (nnode.type == Gtv::class.java) return toGtv(super.construct(nnode))
            return super.construct(nnode)
        }
    }

    private open inner class ScalarConstructor : ConstructScalar() {
        override fun construct(nnode: Node): Any {
            if (nnode.type != ByteArray::class.java) return super.construct(nnode)
            return (nnode as ScalarNode).value
                    .substringAfter("0x")
                    .replace("\\s".toRegex(), "")
                    .hexStringToByteArray()
        }
    }

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
