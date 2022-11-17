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
import org.yaml.snakeyaml.nodes.Tag
import java.math.BigInteger

class GtvConstructor(theRoot: Class<*>) : Constructor(theRoot) {
    init {
        yamlConstructors[Tag.BINARY] = BinaryConstructor()
        yamlClassConstructors[NodeId.scalar] = GtvConstr()
        yamlClassConstructors[NodeId.mapping] = ConstructMappingGtv()
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

    private fun toGtv(obj: Any) = when (obj) {
        is String -> gtv(obj)
        is Int -> gtv(obj.toLong())
        is Long -> gtv(obj)
        is Boolean -> gtv(obj)
        is Double -> gtv(obj.toString())
        is ByteArray -> gtv(obj)
        is BigInteger -> gtv(obj)
        else -> throw IllegalArgumentException("Cannot parse value $obj to gtv")
    }
}
