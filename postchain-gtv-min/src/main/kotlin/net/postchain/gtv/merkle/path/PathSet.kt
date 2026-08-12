package net.postchain.gtv.merkle.path

/**
 * A collection of proof paths.
 */
public interface PathSet {

    /** @return true if there are no paths left */
    public fun isEmpty(): Boolean

    /**
     * In the general case we have many paths, so we try to find any path that "is on" a leaf.
     * If there is no leaf, we will pick the current element from any path.
     */
    public fun getPathLeafOrElseAnyCurrentPathElement(): PathElement?
}
