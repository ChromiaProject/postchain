package net.postchain.base.data

import net.postchain.core.*
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.nio.file.Paths

open class BaseSnapshotBuilder(
        ectx: EContext,
        private val store: BlockStore,
        private val chunkSize : Int = 1024,
        private val snapshotFolder: String = "snapshot"
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
                val path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator + snapshotFolder + File.separator + data.hash()
                val file = File(path)
                file.parentFile.mkdirs()
                file.createNewFile()
                ObjectOutputStream(FileOutputStream(file)).use {
                    it.writeObject(data)
                    it.flush()
                    it.close()
                }
            }

            // convert raw data snapshot tree into hash snapshot tree to reduce snapshot size
            listOfChunk = listOfChunk.plus(mergeHashTrees(listOf(data as TreeElement)))
            if (rows.size < chunkSize) {
                break
            }
            idx++
            offset = idx * chunkSize
        }
        finalized = true

        // only need to return hash snapshot tree
        val root = mergeHashTrees(listOfChunk as List<TreeElement>)
        val height = store.getLastBlockHeight(ectx)
        store.commitSnapshot(ectx, root!!.hash().toByteArray(), height)
        snapshotData = root

        return root
    }
}