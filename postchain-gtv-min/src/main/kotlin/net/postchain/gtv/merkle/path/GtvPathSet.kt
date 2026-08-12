package net.postchain.gtv.merkle.path

/**
 * A collection of [GtvPath]s. Order among the paths is not important.
 *
 * @property paths is a set of all paths relevant for a specific proof
 */
public class GtvPathSet(public val paths: Set<GtvPath>) : PathSet {

    override fun isEmpty(): Boolean = paths.isEmpty()

    override fun getPathLeafOrElseAnyCurrentPathElement(): PathElement? {
        var leafElem: GtvPathLeafElement? = null
        var currElem: GtvPathElement? = null
        var prev: Pair<GtvPath?, GtvPathElement?> = Pair(null, null)

        for (path in paths) {
            currElem = path.getCurrentPathElement()
            if (currElem is GtvPathLeafElement) leafElem = currElem
            prev = errorCheckUnequalParent(path, currElem, prev.first, prev.second)
        }

        // It doesn't matter which one we return (next step we will get the "previous" from this one)
        return leafElem ?: currElem
    }

    /**
     * Guards against an impossible state where two paths in the same set don't have the same parent.
     * Since we usually only have one path in a path set, this check should be cheap.
     */
    private fun errorCheckUnequalParent(
        currPath: GtvPath,
        currElem: GtvPathElement,
        prevPath: GtvPath?,
        prevElem: GtvPathElement?,
    ): Pair<GtvPath, GtvPathElement> {
        if (prevElem != null && currElem.previous != prevElem.previous) {
            throw IllegalStateException("Something is wrong, these paths do not have the same parent. ($currPath) ($prevPath")
        }
        return Pair(currPath, currElem)
    }

    // ----------- Filter on type of next path element ---------

    public fun keepOnlyArrayPaths(): GtvPathSet =
        GtvPathSet(paths.filter { it.pathElements.first() is ArrayGtvPathElement }.toSet())

    public fun keepOnlyDictPaths(): GtvPathSet =
        GtvPathSet(paths.filter { it.pathElements.first() is DictGtvPathElement }.toSet())

    // ----------- Filter on index/key ---------

    /**
     * @return A new path set, where all [GtvPath] without a match has been filtered out
     *         and the ones that remain only hold the tail.
     */
    public fun getTailIfFirstElementIsArrayOfThisIndexFromList(arrayIndex: Int): GtvPathSet =
        genericGetTailFormList(arrayIndex, GtvPath.GtvPathList::getTailIfFirstElementIsArrayOfThisIndex)

    /**
     * @return A new path set, where all [GtvPath] without a match has been filtered out
     *         and the ones that remain only hold the tail.
     */
    public fun getTailIfFirstElementIsDictOfThisKeyFromList(dictKey: String): GtvPathSet =
        genericGetTailFormList(dictKey, GtvPath.GtvPathList::getTailIfFirstElementIsDictOfThisKey)

    private inline fun <T> genericGetTailFormList(searchKey: T, filterFun: (T, GtvPath) -> GtvPath?): GtvPathSet =
        GtvPathSet(paths.mapNotNull { filterFun(searchKey, it) }.toSet())
}
