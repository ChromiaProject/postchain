// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.messages.RawGtv
import java.util.*


data class GtvArray<out T: Gtv>(val array: Array<out T>) : GtvCollection() {

    override val type = GtvType.ARRAY

    override operator fun get(index: Int): T {
        return array[index]
    }

    override fun asArray(): Array<out T> {
        return array
    }

    override fun getSize(): Int {
        return array.size
    }

    override fun getRawGtv(): RawGtv {
        return RawGtv(null, null, null, null, null, RawGtv.Array(array.map { it.getRawGtv() }))
    }

    override fun asPrimitive(): Any? {
        return array.map{ it.asPrimitive() }.toTypedArray()
    }

    override fun nrOfBytes(): Int {
        // This could be expensive since it will go all the way down to the leaf
        var sumNrOfBytes =0
        for (elem in array) {
            sumNrOfBytes += elem.nrOfBytes()
        }
        return sumNrOfBytes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GtvArray<*>

        if (!array.contentEquals(other.array)) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = array.contentHashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}