package net.postchain.gtv.yaml

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvType
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.io.Writer
import java.math.BigInteger
import java.util.regex.Pattern

val BIG_INTEGER_TAG = Tag("!biginteger")
val BIG_INTEGER_FORMAT: Pattern = Pattern.compile("""^[-+]?[0-9]+L$""")
const val BIG_INTEGER_START = "-+0123456789"

val BYTE_ARRAY_TAG = Tag("!bytearray")
val BYTE_ARRAY_FORMAT: Pattern = Pattern.compile("""^x"[0-9a-fA-F]*"$""")
const val BYTE_ARRAY_START = "x"

class GtvConstructor : Constructor(LoaderOptions()) {
    init {
        yamlConstructors[BIG_INTEGER_TAG] = ConstructBigInteger()
        yamlConstructors[BYTE_ARRAY_TAG] = ConstructByteArray()
    }

    private inner class ConstructBigInteger : AbstractConstruct() {
        override fun construct(node: Node): Any = constructScalar(node as ScalarNode).dropLast(1).toBigInteger()
    }

    private inner class ConstructByteArray : AbstractConstruct() {
        override fun construct(node: Node): Any = constructScalar(node as ScalarNode).drop(2).dropLast(1).hexStringToByteArray()
    }
}

class GtvRepresenter : Representer(DumperOptions()) {
    init {
        representers[BigInteger::class.java] = RepresentBigInteger()
        representers[ByteArray::class.java] = RepresentByteArray()
    }

    private inner class RepresentBigInteger : Represent {
        override fun representData(data: Any): Node =
                representScalar(BIG_INTEGER_TAG, (data as BigInteger).toString() + "L")
    }

    private inner class RepresentByteArray : Represent {
        override fun representData(data: Any): Node =
                representScalar(BYTE_ARRAY_TAG, "x\"" + (data as ByteArray).toHex() + "\"")
    }
}

class GtvResolver : Resolver() {
    override fun addImplicitResolvers() {
        addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO", 10)
        addImplicitResolver(Tag.INT, INT, "-+0123456789")
        addImplicitResolver(Tag.MERGE, MERGE, "<", 10)
        addImplicitResolver(Tag.NULL, NULL, "~nN\u0000", 10)
        addImplicitResolver(Tag.NULL, EMPTY, null, 10)

        addImplicitResolver(BIG_INTEGER_TAG, BIG_INTEGER_FORMAT, BIG_INTEGER_START)
        addImplicitResolver(BYTE_ARRAY_TAG, BYTE_ARRAY_FORMAT, BYTE_ARRAY_START)
    }
}

class GtvYaml(init: Yaml.() -> Unit = {}) {
    val yaml = Yaml(GtvConstructor(), GtvRepresenter(), DumperOptions().apply {
        isExplicitStart = true
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    }, GtvResolver()).also(init)

    fun load(content: String): Gtv = mapListToGtv(yaml.load(content))
    fun load(src: File): Gtv = src.inputStream().use { load(it) }
    fun load(src: InputStream): Gtv = mapListToGtv(yaml.load(src))
    fun load(src: Reader): Gtv = mapListToGtv(yaml.load(src))

    fun dump(gtv: Gtv): String = yaml.dump(gtvToListMap(gtv))
    fun dump(gtv: Gtv, dst: File) = dst.writer().use { dump(gtv, it) }
    fun dump(gtv: Gtv, dst: Writer) = yaml.dump(gtvToListMap(gtv), dst)

    fun mapListToGtv(obj: Any?): Gtv = when (obj) {
        is List<*> -> gtv(obj.map { mapListToGtv(it) })
        is Map<*, *> -> gtv(obj.map { (it.key as String) to mapListToGtv(it.value) }.toMap())
        is Boolean -> gtv(obj)
        is Int -> gtv(obj.toLong())
        is Long -> gtv(obj)
        is BigInteger -> gtv(obj)
        is ByteArray -> gtv(obj)
        is WrappedByteArray -> gtv(obj)
        is String -> gtv(obj)
        null -> GtvNull
        else -> throw IllegalArgumentException("Cannot convert object of type ${obj::class.simpleName} to GTV")
    }

    fun gtvToListMap(gtv: Gtv): Any? = when (gtv.type) {
        GtvType.ARRAY -> gtv.asArray().map { gtvToListMap(it) }
        GtvType.DICT -> gtv.asDict().mapValues { gtvToListMap(it.value) }
        GtvType.INTEGER -> gtv.asInteger()
        GtvType.BIGINTEGER -> gtv.asBigInteger()
        GtvType.BYTEARRAY -> gtv.asByteArray()
        GtvType.STRING -> gtv.asString()
        GtvType.NULL -> null
    }
}
