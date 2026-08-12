package net.postchain.gtv

public data class GtvArray(val array: Array<out Gtv>) : GtvCollection() {
    override val type: GtvType
        get() = GtvType.ARRAY

    override fun get(index: Int): Gtv = array[index]
    override fun asArray(): Array<out Gtv> = array
    override fun getSize(): Int = array.size
    override fun asPrimitive(): Any = array.map { it.asPrimitive() }.toTypedArray()

    // This could be expensive since it will go all the way down to the leaf
    override fun nrOfBytes(): Int = array.fold(0) { acc, elem -> acc + elem.nrOfBytes() }

    override fun equals(other: Any?): Boolean =
        this === other || (other is GtvArray && array.contentEquals(other.array))

    override fun hashCode(): Int = 31 * array.contentHashCode() + type.hashCode()

    override fun shortString(): String = if (array.size > 16) "long_array" else "${array.map { it.shortString() }}"

    override fun toString(): String = "${array.map { it.toString() }}"
}
