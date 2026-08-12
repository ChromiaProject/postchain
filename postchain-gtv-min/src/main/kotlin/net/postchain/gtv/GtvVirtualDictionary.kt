package net.postchain.gtv

import net.postchain.gtv.merkle.proof.MerkleProofElement

/**
 * The virtual version of [GtvDictionary] only implements a few of the methods defined in [Gtv].
 *
 * @property dict holds sub-elements. Mostly empty; asking for a key that isn't there explodes, because we have
 *           no way of knowing whether that key existed in the original dict.
 * @property size is the number of elements in the original dictionary (sometimes we don't know this).
 */
public data class GtvVirtualDictionary(
    val proofElement: MerkleProofElement,
    val dict: Map<String, Gtv>,
    val size: Int? = null,
) : GtvVirtual(proofElement) {
    override val type: GtvType
        get() = GtvType.DICT // The virtual dict pretends to be a normal GtvDictionary

    override fun get(key: String): Gtv? =
        dict[key] ?: throw GtvTypeException("The virtual dictionary doesn't keep the value for key = $key")

    override fun getSize(): Int = size ?: dict.keys.size

    public fun isKeyPresent(key: String): Boolean = dict[key] != null

    // ----------- These methods will explode -----------

    override fun asDict(): Map<String, Gtv> = throw GtvTypeException("Don't call this method on a virtual object")

    override fun asPrimitive(): Any = throw GtvTypeException("Don't call this method on a virtual object")

    override fun nrOfBytes(): Int = throw GtvTypeException("Don't call this method on a virtual object")

    override fun shortString(): Nothing = throw GtvTypeException("Don't call this method on a virtual object")

    override fun equals(other: Any?): Boolean =
        throw GtvTypeException("You cannot compare a virtual object with something else.")

    override fun hashCode(): Int = throw GtvTypeException("Don't call this method on a virtual object")
}
