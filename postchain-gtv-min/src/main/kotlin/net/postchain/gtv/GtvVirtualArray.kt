package net.postchain.gtv

import net.postchain.gtv.merkle.proof.MerkleProofElement

/**
 * The virtual version of [GtvArray] only implements a few of the methods defined in [Gtv].
 *
 * Using it like a real [Gtv] explodes on purpose, because using a virtual object for anything but data access
 * could cause bugs.
 *
 * @property array holds sub elements. Mostly "null"; asking for one of those explodes, since we don't know
 *           what the value would have been in the original [GtvArray].
 */
public data class GtvVirtualArray(
    val proofElement: MerkleProofElement,
    val array: Array<out Gtv?>,
) : GtvVirtual(proofElement) {
    override val type: GtvType
        get() = GtvType.ARRAY // The virtual array pretends to be a normal GtvArray

    override fun get(index: Int): Gtv =
        array[index] ?: throw GtvTypeException("The virtual array doesn't keep the value at position $index")

    override fun getSize(): Int = array.size

    public fun isKeyPresent(index: Int): Boolean = array[index] != null

    // ----------- These methods will explode -----------

    override fun asArray(): Array<out Gtv> = throw GtvTypeException("Don't call this method on a virtual object")

    override fun asPrimitive(): Any = throw GtvTypeException("Don't call this method on a virtual object")

    override fun nrOfBytes(): Int = throw GtvTypeException("Don't call this method on a virtual object")

    override fun shortString(): Nothing = throw GtvTypeException("Don't call this method on a virtual object")

    override fun equals(other: Any?): Boolean =
        throw GtvTypeException("You cannot compare a virtual object with something else.")

    override fun hashCode(): Int = throw GtvTypeException("Don't call this method on a virtual object")
}
