package net.postchain.gtv

/**
 * A supertype for all types that can hold other [Gtv]
 *
 * Typically a collection has a size
 */
public abstract class GtvCollection : AbstractGtv() {
    public abstract fun getSize(): Int
}
