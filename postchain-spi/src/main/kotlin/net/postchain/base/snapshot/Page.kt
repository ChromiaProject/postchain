package net.postchain.base.snapshot

import net.postchain.common.data.Hash
import net.postchain.common.data.TreeHasher

/**
 * Represents a page containing data corresponding to one or several nodes of a Merkle tree.
 *
 * A binary Merkle tree is organized into levels:
 * - **Level 0** corresponds to the leafs of the tree.
 * - **Level 1** corresponds to internal nodes, each of which is logically linked to two leafs.
 * - **Level 2** corresponds to internal nodes, which are linked to two nodes of level 1, and so on.
 *
 * - **Hash of the leaf node**: The hash of the data (normally stored in the `LeafStore`),
 *   but essentially, it is just an opaque byte array from the perspective of the tree
 *   with the same size as the hash.
 * - **Hash of an internal node**: A hash of the concatenation of the hashes of its two children,
 *   as computed by `TreeHasher`.
 *
 * The **EMPTY_HASH** is used to represent missing leafs in a sparse tree. If a node has two
 * EMPTY_HASHes as children, its hash is also EMPTY_HASH. This means that any node with
 * only empty leafs beneath it has an EMPTY_HASH, which makes the sparse tree representation
 * more efficient.
 *
 * To reduce the number of database reads and writes, data is organized into pages. The
 * configurable parameter `levelsPerPage` determines how many levels of the tree are stored in one page.
 *
 * ### Example:
 * For `levelsPerPage = 1`:
 * - A page at level 0 stores 2 leaf nodes, corresponding to 1 intermediate node at level 1.
 *
 * For `levelsPerPage = 2`:
 * - A page at level 0 stores 4 leaf nodes, corresponding to 2 intermediate nodes at level 1,
 *   and thus 1 intermediate node at level 2.
 *
 * The field `left` identifies the position of the page in the tree. It is measured by the number
 * of 'nodes' at the level of the page in the tree.
 *
 * ### Indexing:
 * Visualize the tree as a triangle with `levelsPerPage = 2`:
 * - Levels marked with `[]` are stored explicitly.
 * - Levels marked with `<>` are not explicitly stored but are calculated using `getChildHash`.
 *
 * ```
 * Level 0: [0, 1, 2, 3]  [4, 5, 6, 7]
 * Level 1: <0, 1>  <2, 3>
 * Level 2: [0, 1, 2*, 3*]
 * Level 3: <0, 1*>
 * Level 4: <0>
 * ```
 * In this example:
 * - The page at level 0 with `left = 0` stores leafs 0, 1, 2, and 3.
 * - Level 1 is not explicitly stored but can be computed using `getChildHash` on pages of level 0.
 *   - For example, if we want to access node with index 1 on level 1, we use the page on level 0 with `left = 0`
 *   and supply `relLevel = 1`, and `childIndex = 1`.
 *   - To access node number 3 on level 1, we need to get the level 0 page with `left = 4`,
 *   and give it `relLevel = 1`, and `childIndex = 1`.
 * - On level 2, we have a single page.
 *
 * Entries of the level 2 page can also be computed using `getChildHash`:
 * `N_L2_0 = page(level=0, left=0).getChildHash(relLevel=2, ..., childIndex=0)`
 * `N_L2_1 = page(level=0, left=4).getChildHash(relLevel=2, ..., childIndex=0)`
 *
 * ### Root Hash:
 * - The root hash of the tree is the highest hash that can be computed, such as:
 *   `root_hash = N_L4_0 = page(level=2, left=0).getChildHash(relLevel=2, ..., childIndex=0)`
 *
 * - While `levelsPerPage` does not affect Merkle proof construction, the root of the tree
 *   is considered to be at the level `highestPageLevel + levelsPerPage`.
 * - The size of the tree is always `2 ^ (n * levelsPerPage) = 2 ^ (highestPageLevel + levelsPerPage)`.
 *
 * In the code, page entries correspond to nodes of the tree. The terms "page entry" and "node" are interchangeable,
 * and the above indexing scheme applies to both contexts.
 */
class Page(
        val blockHeight: Long, val level: Int, val left: Long,
        val childHashes: Array<Hash>
) {


    /**
     * Retrieves the hash of a node (not just a "child") within the page using relative coordinates.
     *
     * The name of this function is a misnomer because it provides the hash of the node itself,
     * rather than just a "child." The node is selected using the following relative coordinates:
     *
     * - **relLevel**: The level of the node relative to the level of the page itself.
     * - **childIndex**: The index of the node at that level, within the page.
     *
     * The absolute position of the node is calculated as:
     * `(left shr relLevel) + childIndex`.
     *
     * - If `relLevel = 0`, it returns `childHashes`.
     * - Otherwise, it computes the hash of the node at the specified level above it.
     */
    fun getChildHash(relLevel: Int, treeHasher: TreeHasher, childIndex: Int): Hash {
        return if (relLevel == 0) childHashes[childIndex]
        else {
            val leftHash = getChildHash(relLevel - 1, treeHasher, 2 * childIndex)
            val rightHash = getChildHash(relLevel - 1, treeHasher, 2 * childIndex + 1)
            treeHasher(leftHash, rightHash)
        }
    }
}