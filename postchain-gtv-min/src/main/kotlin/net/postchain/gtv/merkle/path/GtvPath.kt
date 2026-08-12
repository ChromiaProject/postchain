package net.postchain.gtv.merkle.path

import mu.KLogging
import net.postchain.gtv.Gtv

/**
 * A [GtvPath] is a list of instructions how to navigate to the next element in the structure of arrays and
 * dictionaries. If the path is a "leaf" we are at the very bottom and should not go any deeper.
 */
public class GtvPath(pathElements: List<GtvPathElement>) : Path<GtvPathElement>(pathElements) {
    /** @return a new [GtvPath] with the tail of the path */
    public fun tail(): GtvPath {
        if (pathElements.isEmpty()) throw IllegalArgumentException("Impossible to tail this array")
        return GtvPath(pathElements.subList(1, pathElements.size))
    }

    public fun debugString(): String = buildString {
        for (elem in pathElements) {
            when (elem) {
                is SearchableGtvPathElement -> append("-> " + elem.getSearchKey())
                is GtvPathLeafElement -> append("-> Leaf")
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is GtvPath && pathElements == other.pathElements)

    override fun hashCode(): Int = GtvPath::class.hashCode()

    public companion object GtvPathList : KLogging() {

        /** Please don't add anything to this list, it is supposed to be empty :-D */
        public val NO_PATHS: GtvPathSet = GtvPathSet(setOf())

        public fun debugRerpresentation(paths: List<GtvPath>): String = buildString {
            for (path in paths) append(path.debugString() + "\n")
        }

        /**
         * @return If the first element of [GtvPath] matches the given "next" [Gtv], then return the tail.
         *  Else return nothing.
         */
        public fun getTailIfFirstElementIsArrayOfThisIndex(arrayIndex: Int, gtxPath: GtvPath): GtvPath? =
            genericGetTail(arrayIndex, gtxPath)

        /**
         * @return If the first element of [GtvPath] matches the given "next" [Gtv], then return the tail.
         *  Else return nothing.
         */
        public fun getTailIfFirstElementIsDictOfThisKey(dictKey: String, gtvPath: GtvPath): GtvPath? =
            genericGetTail(dictKey, gtvPath)

        private fun <T> genericGetTail(searchKey: T, gtvPath: GtvPath): GtvPath? {
            if (searchKey == null) throw IllegalArgumentException("Have to provide a search key")

            val firstElement = gtvPath.pathElements.firstOrNull() ?: return null

            if (firstElement is SearchableGtvPathElement && firstElement.getSearchKey().toString() == searchKey.toString()) {
                return gtvPath.tail()
            }
            return null
        }
    }
}
