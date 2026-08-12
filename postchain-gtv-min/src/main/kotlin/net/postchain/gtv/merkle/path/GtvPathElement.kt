package net.postchain.gtv.merkle.path

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString

/**
 * @property previous is needed because we need to find out how we got here
 */
public sealed class GtvPathElement(public val previous: SearchableGtvPathElement?) : PathElement

/**
 * This element will hold an index/key to the next element in the path.
 *
 * Typically, a path looks like this:
 *
 *   Searchable... ->  Searchable... -> Searchable... -> Leaf...
 */
public abstract class SearchableGtvPathElement(previous: SearchableGtvPathElement?) : GtvPathElement(previous) {

    public abstract fun getSearchKey(): Any

    public abstract fun buildGtv(): Gtv
}

/** Represents an index position in a [GtvArray] */
public class ArrayGtvPathElement(
    previous: SearchableGtvPathElement?,
    public val index: Int,
) : SearchableGtvPathElement(previous) {

    override fun getSearchKey(): Any = index

    override fun buildGtv(): Gtv = GtvInteger(index.toLong())

    // We deliberately do not care about the "previous" in this implementation
    override fun equals(other: Any?): Boolean =
        this === other || (other is ArrayGtvPathElement && index == other.index)

    override fun hashCode(): Int = index

    override fun toString(): String = "ArrayGtvPathElement(index=$index)"
}

/** Represents what key to use in a [GtvDictionary] */
public class DictGtvPathElement(
    previous: SearchableGtvPathElement?,
    public val key: String,
) : SearchableGtvPathElement(previous) {

    override fun getSearchKey(): Any = key

    override fun buildGtv(): Gtv = GtvString(key)

    // We deliberately do not care about the "previous" in this implementation
    override fun equals(other: Any?): Boolean =
        this === other || (other is DictGtvPathElement && key == other.key)

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "DictGtvPathElement(key='$key')"
}

/**
 * The last element of the path.
 *
 * @property previous here we don't allow a null (since a path with just a dummy leaf is meaningless).
 */
public class GtvPathLeafElement(
    previous: SearchableGtvPathElement,
) : GtvPathElement(previous), PathLeafElement {

    // All instances are considered the same (this is a dummy element anyway)
    override fun equals(other: Any?): Boolean = other is GtvPathLeafElement

    override fun hashCode(): Int = GtvPathLeafElement::class.hashCode()

    override fun toString(): String = "GtvPathLeafElement()"
}
