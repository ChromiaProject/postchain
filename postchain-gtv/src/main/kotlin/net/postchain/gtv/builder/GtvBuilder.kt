package net.postchain.gtv.builder

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType

class GtvBuilder(private var value: Gtv = gtv(mapOf())) {

    fun update(gtv: Gtv, vararg path: String) {
        val gtvNode = GtvNode.decode(gtv)
        update(gtvNode, *path)
    }

    fun update(gtv: GtvNode, vararg path: String) {
        val pathGtv = makeGtvPath(gtv, *path)
        value = pathGtv.merge(value, listOf())
    }

    fun build(): Gtv = value

    private fun makeGtvPath(value: GtvNode, vararg path: String): GtvNode {
        var res: GtvNode = value
        for (key in path.reversed()) {
            val elems = mapOf(key to GtvDictEntry(res, GtvDictMerge.KEEP_NEW))
            res = GtvDictNode(elems, GtvDictMerge.KEEP_NEW)
        }
        return res
    }

    enum class GtvArrayMerge {
        REPLACE,
        APPEND,
        PREPEND;

        companion object {
            fun parse(s: String) = when (s) {
                "replace" -> REPLACE
                "append" -> APPEND
                "prepend" -> PREPEND
                else -> null
            }
        }
    }

    enum class GtvDictMerge {
        REPLACE,
        KEEP_OLD,
        KEEP_NEW,
        STRICT;

        companion object {
            fun parseDict(s: String) = when (s) {
                "replace" -> REPLACE
                "keep-old" -> KEEP_OLD
                "keep-new" -> KEEP_NEW
                "strict" -> STRICT
                else -> null
            }

            fun parseEntry(s: String) = when (s) {
                "keep-old" -> KEEP_OLD
                "keep-new" -> KEEP_NEW
                "strict" -> STRICT
                else -> null
            }
        }
    }

    sealed class GtvNode {
        abstract fun type(): GtvType
        abstract fun toGtv(): Gtv

        open fun asArray(): GtvArrayNode = errBadType(GtvType.ARRAY)
        open fun asDict(): GtvDictNode = errBadType(GtvType.DICT)

        private fun errBadType(expected: GtvType): Nothing {
            throw IllegalStateException("expected $expected actual ${type()}")
        }

        abstract fun merge(old: Gtv, path: List<String>): Gtv

        companion object {
            fun decode(gtv: Gtv): GtvNode {
                return when (gtv.type) {
                    GtvType.ARRAY -> {
                        val elems = gtv.asArray().map { decode(it) }
                        GtvArrayNode(elems, GtvArrayMerge.APPEND)
                    }

                    GtvType.DICT -> {
                        val elems = gtv.asDict().mapValues {
                            GtvDictEntry(decode(it.value), GtvDictMerge.KEEP_NEW)
                        }
                        GtvDictNode(elems, GtvDictMerge.KEEP_NEW)
                    }

                    else -> GtvTermNode(gtv)
                }
            }
        }
    }

    class GtvTermNode(private val gtv: Gtv) : GtvNode() {
        init {
            val type = gtv.type
            check(type != GtvType.DICT && type != GtvType.ARRAY) { type }
        }

        override fun type() = gtv.type
        override fun toGtv() = gtv

        override fun merge(old: Gtv, path: List<String>): Gtv {
            return gtv
        }
    }

    class GtvArrayNode private constructor(val values: Array<out Gtv>, private val merge: GtvArrayMerge) : GtvNode() {
        constructor(values: List<GtvNode>, merge: GtvArrayMerge) : this(GtvArray(values.map { it.toGtv() }.toTypedArray()), merge)
        constructor(values: GtvArray, merge: GtvArrayMerge) : this(values.array, merge)

        override fun type() = GtvType.ARRAY
        override fun toGtv() = GtvArray(values)
        override fun asArray() = this

        override fun merge(old: Gtv, path: List<String>): Gtv {
            checkUpdateType(type(), old.type, path)

            val oldValues = old.asArray()
            val newValues = values

            if (merge == GtvArrayMerge.REPLACE) {
                return toGtv()
            }

            if (newValues.isEmpty()) {
                return old
            } else if (oldValues.isEmpty()) {
                return toGtv()
            }

            val updateElems = newValues.map { it }

            val resElems = when (merge) {
                GtvArrayMerge.REPLACE -> updateElems
                GtvArrayMerge.APPEND -> oldValues.toList() + updateElems
                GtvArrayMerge.PREPEND -> updateElems + oldValues.toList()
            }

            return gtv(resElems)
        }
    }

    class GtvDictEntry(val value: GtvNode, val merge: GtvDictMerge)

    class GtvDictNode(val values: Map<String, GtvDictEntry>, private val merge: GtvDictMerge) : GtvNode() {
        constructor(gtv: GtvDictionary, merge: GtvDictMerge) :
                this(gtv.dict.mapValues { GtvDictEntry(decode(it.value), merge) }, merge)

        override fun type() = GtvType.DICT
        override fun toGtv() = gtv(values.mapValues { it.value.value.toGtv() })
        override fun asDict() = this

        override fun merge(old: Gtv, path: List<String>): Gtv {
            checkUpdateType(type(), old.type, path)

            val oldMap = old.asDict()
            val newMap = values

            if (merge == GtvDictMerge.REPLACE) {
                return toGtv()
            }

            if (newMap.isEmpty()) {
                return old
            } else if (oldMap.isEmpty()) {
                return toGtv()
            }

            val res = mutableMapOf<String, Gtv>()
            res.putAll(oldMap)

            for ((key, updEntry) in newMap) {
                val oldValue = res[key]
                val resValue = if (oldValue == null) updEntry.value.toGtv() else {
                    when (updEntry.merge) {
                        GtvDictMerge.KEEP_OLD -> oldValue
                        GtvDictMerge.KEEP_NEW, GtvDictMerge.REPLACE -> {
                            updEntry.value.merge(oldValue, path + key)
                        }

                        GtvDictMerge.STRICT -> {
                            failUpdate(path, "Gtv dict key conflict: '$key'")
                        }
                    }
                }
                res[key] = resValue
            }

            return gtv(res)
        }
    }

    companion object {
        private fun checkUpdateType(actualType: GtvType, expectedType: GtvType, path: List<String>) {
            checkUpdate(actualType == expectedType, path) { "cannot merge $actualType to $expectedType" }
        }

        private fun checkUpdate(b: Boolean, path: List<String>, msgCode: () -> String) {
            if (!b) {
                val msg = msgCode()
                failUpdate(path, msg)
            }
        }

        private fun failUpdate(path: List<String>, msg: String): Nothing {
            val pathStr = path.joinToString("/")
            throw IllegalStateException("$msg [path: $pathStr]")
        }
    }
}
