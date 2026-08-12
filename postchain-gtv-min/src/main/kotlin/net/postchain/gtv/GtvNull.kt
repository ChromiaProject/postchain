package net.postchain.gtv

public data object GtvNull : GtvPrimitive() {
    override val type: GtvType
        get() = GtvType.NULL

    override fun isNull(): Boolean = true
    override fun asPrimitive(): Any? = null
    override fun nrOfBytes(): Int = 0
    override fun shortString(): String = toString()
    override fun toString(): String = "null"
}
