package net.postchain.gtv.yaml

import net.postchain.common.hexStringToByteArray
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeId
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag

class GtvConstructor(theRoot: Class<*> ) : Constructor(theRoot) {
    init {
        yamlConstructors[Tag.BINARY] = BinaryConstructor()
        yamlClassConstructors[NodeId.scalar] = ScalarConstructor()
    }

    private inner class BinaryConstructor : SafeConstructor.ConstructYamlBinary() {
        override fun construct(node: Node) = (node as ScalarNode).value
                .substringAfter("0x")
                .replace("\\s".toRegex(), "")
                .hexStringToByteArray()
    }

    private inner class ScalarConstructor : ConstructScalar() {
        override fun construct(nnode: Node): Any {
            if (nnode.type != ByteArray::class.java) return super.construct(nnode)
            return (nnode as ScalarNode).value
                    .substringAfter("0x")
                    .replace("\\s".toRegex(), "")
                    .hexStringToByteArray()
        }
    }
}
