package net.postchain.gtv.merkle.path

public open class Path<T : PathElement>(public val pathElements: List<T>) {

    /** @return the path element we are at now. */
    public fun getCurrentPathElement(): T = pathElements.first()

    public fun size(): Int = pathElements.size
}
