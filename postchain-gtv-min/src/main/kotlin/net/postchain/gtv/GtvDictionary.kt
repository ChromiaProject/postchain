package net.postchain.gtv

@ConsistentCopyVisibility
public data class GtvDictionary private constructor(val dict: Map<String, Gtv>) : GtvCollection() {
    override val type: GtvType
        get() = GtvType.DICT

    public companion object {
        /**
         * Note: We use this constructor instead of the main constructor to make sure we never create an unsorted dict
         *
         * @return sorts the keys and return a dict
         */
        public fun build(unsorted: Map<String, Gtv>): GtvDictionary =
            GtvDictionary(unsorted.keys.sorted().associateWithTo(LinkedHashMap()) { unsorted.getValue(it) })
    }

    override fun get(key: String): Gtv? = dict[key]
    override fun getSize(): Int = dict.keys.size
    override fun asDict(): Map<String, Gtv> = dict

    override fun asPrimitive(): Any = dict.mapValues { it.value.asPrimitive() }

    // This could be expensive since it will go all the way down to the leaf
    override fun nrOfBytes(): Int =
        dict.keys.fold(0) { acc, key -> acc + key.length * 2 + dict.getValue(key).nrOfBytes() }

    override fun shortString(): String = when {
        dict.isEmpty() -> "[:]"
        dict.size > 16 -> "long_dict"
        else -> dict.asIterable().joinToString(prefix = "[", postfix = "]") {
            "\"${escapeGtv(it.key.take(64))}\": ${it.value.shortString()}"
        }
    }

    override fun toString(): String = if (dict.isEmpty()) {
        "[:]"
    } else {
        dict.asIterable().joinToString(prefix = "[", postfix = "]") { "\"${escapeGtv(it.key)}\": ${it.value}" }
    }
}
