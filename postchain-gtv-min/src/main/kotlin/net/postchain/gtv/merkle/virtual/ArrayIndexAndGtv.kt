package net.postchain.gtv.merkle.virtual

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtualArray
import net.postchain.gtv.merkle.proof.MerkleProofElement

public data class ArrayIndexAndGtv(var index: Int, val value: Gtv)

public data class ArrayIndexAndGtvList(val innerSet: MutableList<ArrayIndexAndGtv>) {

    public constructor() : this(mutableListOf())

    public constructor(index: Int, value: Gtv) : this(mutableListOf(ArrayIndexAndGtv(index, value)))

    public fun addAll(otherSet: ArrayIndexAndGtvList) {
        innerSet += otherSet.innerSet
    }

    /** Turns the elements into a virtual array, putting "null" in all empty positions. */
    public fun buildGtvVirtualArray(proofElement: MerkleProofElement, arrSize: Int): GtvVirtualArray {
        val retArr: Array<Gtv?> = arrayOfNulls(arrSize)
        for ((index, value) in innerSet) retArr[index] = value
        return GtvVirtualArray(proofElement, retArr)
    }
}
