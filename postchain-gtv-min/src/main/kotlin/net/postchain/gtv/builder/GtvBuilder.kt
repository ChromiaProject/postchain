package net.postchain.gtv.builder

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType

/**
 * Merges GTV structures into one, with per-node control over how conflicts are resolved.
 */
public class GtvBuilder(private var value: Gtv = gtv(mapOf())) {

    public fun update(gtv: Gtv, vararg path: String) {
        update(GtvNode.decode(gtv), *path)
    }

    public fun update(gtv: GtvNode, vararg path: String) {
        value = makeGtvPath(gtv, *path).merge(value, listOf())
    }

    public fun build(): Gtv = value

    private fun makeGtvPath(value: GtvNode, vararg path: String): GtvNode = path.reversed().fold(value) { acc, key ->
        GtvDictNode(mapOf(key to GtvDictEntry(acc, GtvDictMerge.KEEP_NEW)), GtvDictMerge.KEEP_NEW)
    }

    public enum class GtvArrayMerge {
        REPLACE,
        APPEND,
        PREPEND;

        public companion object {
            public fun parse(s: String): GtvArrayMerge? = when (s) {
                "replace" -> REPLACE
                "append" -> APPEND
                "prepend" -> PREPEND
                else -> null
            }
        }
    }

    public enum class GtvDictMerge {
        REPLACE,
        KEEP_OLD,
        KEEP_NEW,
        STRICT;

        public companion object {
            public fun parseDict(s: String): GtvDictMerge? = when (s) {
                "replace" -> REPLACE
                "keep-old" -> KEEP_OLD
                "keep-new" -> KEEP_NEW
                "strict" -> STRICT
                else -> null
            }

            public fun parseEntry(s: String): GtvDictMerge? = when (s) {
                "keep-old" -> KEEP_OLD
                "keep-new" -> KEEP_NEW
                "strict" -> STRICT
                else -> null
            }
        }
    }

    public sealed class GtvNode {
        public abstract fun type(): GtvType
        public abstract fun toGtv(): Gtv

        public open fun asArray(): GtvArrayNode = errBadType(GtvType.ARRAY)
        public open fun asDict(): GtvDictNode = errBadType(GtvType.DICT)

        private fun errBadType(expected: GtvType): Nothing {
            throw IllegalStateException("expected $expected actual ${type()}")
        }

        public abstract fun merge(old: Gtv, path: List<String>): Gtv

        public companion object {
            public fun decode(gtv: Gtv): GtvNode = when (gtv.type) {
                GtvType.ARRAY -> GtvArrayNode(gtv.asArray().map { decode(it) }, GtvArrayMerge.APPEND)

                GtvType.DICT -> GtvDictNode(
                    gtv.asDict().mapValues { GtvDictEntry(decode(it.value), GtvDictMerge.KEEP_NEW) },
                    GtvDictMerge.KEEP_NEW,
                )

                else -> GtvTermNode(gtv)
            }
        }
    }

    public class GtvTermNode(private val gtv: Gtv) : GtvNode() {
        init {
            val type = gtv.type
            check(type != GtvType.DICT && type != GtvType.ARRAY) { type }
        }

        override fun type(): GtvType = gtv.type
        override fun toGtv(): Gtv = gtv

        override fun merge(old: Gtv, path: List<String>): Gtv = gtv
    }

    public class GtvArrayNode private constructor(
        public val values: Array<out Gtv>,
        private val merge: GtvArrayMerge,
    ) : GtvNode() {
        public constructor(values: List<GtvNode>, merge: GtvArrayMerge) :
            this(GtvArray(values.map { it.toGtv() }.toTypedArray()), merge)

        public constructor(values: GtvArray, merge: GtvArrayMerge) : this(values.array, merge)

        override fun type(): GtvType = GtvType.ARRAY
        override fun toGtv(): GtvArray = GtvArray(values)
        override fun asArray(): GtvArrayNode = this

        override fun merge(old: Gtv, path: List<String>): Gtv {
            checkUpdateType(type(), old.type, path)

            val oldValues = old.asArray()

            if (merge == GtvArrayMerge.REPLACE) return toGtv()
            if (values.isEmpty()) return old
            if (oldValues.isEmpty()) return toGtv()

            val updateElems = values.map { it }

            return gtv(
                when (merge) {
                    GtvArrayMerge.APPEND -> oldValues.toList() + updateElems
                    GtvArrayMerge.PREPEND -> updateElems + oldValues.toList()
                }
            )
        }
    }

    public class GtvDictEntry(public val value: GtvNode, public val merge: GtvDictMerge)

    public class GtvDictNode(
        public val values: Map<String, GtvDictEntry>,
        private val merge: GtvDictMerge,
    ) : GtvNode() {
        public constructor(gtv: GtvDictionary, merge: GtvDictMerge) :
            this(gtv.dict.mapValues { GtvDictEntry(decode(it.value), merge) }, merge)

        override fun type(): GtvType = GtvType.DICT
        override fun toGtv(): GtvDictionary = gtv(values.mapValues { it.value.value.toGtv() })
        override fun asDict(): GtvDictNode = this

        override fun merge(old: Gtv, path: List<String>): Gtv {
            checkUpdateType(type(), old.type, path)

            val oldMap = old.asDict()

            if (merge == GtvDictMerge.REPLACE) return toGtv()
            if (values.isEmpty()) return old
            if (oldMap.isEmpty()) return toGtv()

            val res = oldMap.toMutableMap()

            for ((key, updEntry) in values) {
                val oldValue = res[key]
                res[key] = if (oldValue == null) {
                    updEntry.value.toGtv()
                } else {
                    when (updEntry.merge) {
                        GtvDictMerge.KEEP_OLD -> oldValue
                        GtvDictMerge.KEEP_NEW, GtvDictMerge.REPLACE -> updEntry.value.merge(oldValue, path + key)
                        GtvDictMerge.STRICT -> failUpdate(path, "Gtv dict key conflict: '$key'")
                    }
                }
            }

            return gtv(res)
        }
    }

    public companion object {
        private fun checkUpdateType(actualType: GtvType, expectedType: GtvType, path: List<String>) {
            checkUpdate(actualType == expectedType, path) { "cannot merge $actualType to $expectedType" }
        }

        private fun checkUpdate(b: Boolean, path: List<String>, msgCode: () -> String) {
            if (!b) failUpdate(path, msgCode())
        }

        private fun failUpdate(path: List<String>, msg: String): Nothing {
            throw IllegalStateException("$msg [path: ${path.joinToString("/")}]")
        }
    }
}
