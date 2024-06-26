// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.virtual

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtualArray
import net.postchain.gtv.merkle.proof.MerkleProofElement

/**
 * @property index is the
 * @property value is the [Gtv] value we want to keep in the
 */
data class ArrayIndexAndGtv(var index: Int, val value: Gtv)


data class ArrayIndexAndGtvList(val innerSet: MutableList<ArrayIndexAndGtv>) {

    /**
     * Sometimes we need an empty list
     */
    constructor(): this(mutableListOf())

    /**
     * Usually we begin with a list of only one value
     */
    constructor(index: Int, value: Gtv): this(mutableListOf(ArrayIndexAndGtv(index, value)))

    fun addAll(otherSet: ArrayIndexAndGtvList) {
        innerSet.addAll(otherSet.innerSet)
    }

    /**
     * Turns the elements into a virtual array (put "null" in all empty positions)
     */
    fun buildGtvVirtualArray(proofElement: MerkleProofElement, arrSize: Int): GtvVirtualArray {
        val retArr: Array<Gtv?> = Array(arrSize){null}
        for (element in innerSet) {
            retArr[element.index] = element.value
        }
        return GtvVirtualArray(proofElement, retArr)
    }

}