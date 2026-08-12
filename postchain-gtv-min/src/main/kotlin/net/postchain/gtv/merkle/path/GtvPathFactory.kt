package net.postchain.gtv.merkle.path

public object GtvPathFactory {
    /**
     * Use this to convert a weakly typed path to a [GtvPath].
     *
     * @param inputArr is just an array with Ints and Strings representing the path
     */
    public fun buildFromArrayOfPointers(inputArr: Array<Any>): GtvPath {
        val pathElementList = ArrayList<GtvPathElement>(inputArr.size + 1)
        var lastPathElem: SearchableGtvPathElement? = null
        for (item in inputArr) {
            val newElem = when (item) {
                is Int -> ArrayGtvPathElement(lastPathElem, item)
                is String -> DictGtvPathElement(lastPathElem, item)
                else -> throw IllegalArgumentException(
                    "A path structure must only consist of Ints and Strings, not $item"
                )
            }
            pathElementList += newElem
            lastPathElem = newElem
        }
        // The last one must have a previous element, or else the proof is trivial.
        pathElementList += GtvPathLeafElement(lastPathElem!!)
        return GtvPath(pathElementList)
    }
}
