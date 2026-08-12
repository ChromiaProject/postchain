package net.postchain.gtv

public data class GtvInteger(val integer: Long) : GtvPrimitive() {
    override val type: GtvType
        get() = GtvType.INTEGER

    override fun asInteger(): Long = integer
    override fun asBoolean(): Boolean = integer != 0L
    override fun asPrimitive(): Any = integer
    override fun nrOfBytes(): Int = Long.SIZE_BYTES
    override fun shortString(): String = toString()
    override fun toString(): String = integer.toString()
}
