// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.beanit.jasn1.ber.types.string.BerUTF8String
import net.postchain.gtv.gtvmessages.DictPair
import net.postchain.gtv.gtvmessages.RawGtv

data class GtvDictionary private constructor(val dict: Map<String, Gtv>) : GtvCollection() {

    override val type = GtvType.DICT

    /*
    lateinit var dict: Map<String, Gtv>

    constructor(unsorted: Map<String, Gtv>) : this() {
        dict = makeSortedDict(unsorted)
    }
    */

    companion object {

        /**
         * Note: We use this constructor instead of the main constructor to make sure we never create an unsorted dict
         *
         * @return sorts the keys and return a dict
         */
        fun build(unsorted: Map<String, Gtv>): GtvDictionary {
            val retMap = LinkedHashMap<String, Gtv>()

            for (key in unsorted.keys.sorted()) {
                retMap[key] = unsorted[key]!!
            }

            return GtvDictionary(retMap)
        }
    }

    override operator fun get(key: String): Gtv? {
        return dict[key]
    }

    override fun getSize(): Int {
        return dict.keys.size
    }

    override fun asDict(): Map<String, Gtv> {
        return dict
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv().apply {
            dict = RawGtv.Dict(this@GtvDictionary.dict.entries.map {
                DictPair(BerUTF8String(it.key), it.value.getRawGtv())
            })
        }
    }

    override fun asPrimitive(): Any? {
        return dict.mapValues {
            it.value.asPrimitive()
        }
    }

    override fun nrOfBytes(): Int {
        // This could be expensive since it will go all the way down to the leaf
        var sumNrOfBytes = 0
        for (key in dict.keys) {
            sumNrOfBytes += (key.length * 2)
            sumNrOfBytes += dict.getValue(key).nrOfBytes()
        }
        return sumNrOfBytes
    }

    override fun toString() = if (dict.isEmpty())
        "[:]"
    else
        dict.asIterable().joinToString(prefix = "[", postfix = "]") { entity -> "\"${entity.key}\": ${entity.value}" }
}
