package net.postchain.base.data

import net.postchain.core.*
import java.io.FileOutputStream
import java.io.ObjectOutputStream

class BaseSnapshotBuilder(
        ectx: EContext,
        private val store: BlockStore,
        private val chunkSize : Int = 1024
): AbstractSnapshotBuilder(ectx) {

    override fun buildSnapshot(): Tree {
        var idx = 0L
        var offset = 0L
        var listOfChunk = listOf<TreeElement?>()
        while (true) {
            var rows = store.getChunkData(ectx, chunkSize, offset)
            if (rows.isEmpty()) {
                break
            }
            val leafs = rows.map { row ->
                TLeaf(longToKey(row.id.asInteger()), row)
            }
            val data = buildChunked(SimpleChunkAccess(leafs))

            // Serialize tree chunk object into file with name is root hash of the tree chunk
            if (data != null) {
//                printTree(data)
                ObjectOutputStream(FileOutputStream(data.hash())).use { it.writeObject(data) }
            }

            listOfChunk = listOfChunk.plus(data)
            if (rows.size < chunkSize) {
                break
            }
            idx++
            offset = idx * chunkSize
        }
        finalized = true

        // TODO: Should return snapshot root tree with hash ref only, then we can get the root hash of the snapshot tree
        // Using mergeTrees for full list of the chunk tree to make snapshot root still face problem here.
        // Need to figure out the solution to reduce the usage of memory.
        val root = mergeTrees(listOfChunk as List<TreeElement>)
        snapshotData = root
        return root
    }
}