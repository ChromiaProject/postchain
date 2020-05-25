package net.postchain.core

import net.postchain.base.data.BlockData
import net.postchain.base.data.TableDefinition
import net.postchain.base.data.TxData
import net.postchain.base.gtv.RowData
import net.postchain.gtv.*
import org.junit.Test
import java.io.*
import java.nio.file.Paths
import kotlin.test.assertEquals

class SnapshotTrieTest {

    private fun sameTree(t1: Tree, t2: Tree): Boolean {
        fun haveSameElms(n1: TNode, n2: TNode): Boolean {
            for (i in n1.elms.indices) {
                if (n1.elms[i] == null) return n2.elms[i] == null
                else if (!sameTree(n1.elms[i]!!.element!!, n2.elms[i]!!.element!!)) return false
            }
            return true
        }

        return when (t1) {
            null -> (t2 == null)
            is TLeaf -> (t2 is TLeaf) && (t1.prefix == t2.prefix) && (t1.content == t2.content)
            is TNode -> (t2 is TNode) && (t1.prefix == t2.prefix)
                    &&  haveSameElms(t1, t2)
        }
    }

    private fun data(): List<RowData> {
        val cols = listOf(
                TableDefinition("name", "text", false, null),
                TableDefinition("age", "integer", true, null),
                TableDefinition("city", "integer", false, "Paris")
        )
        val defs = cols.map { it.toGtv() }.toTypedArray()
        val data = mutableMapOf<String, Gtv>()
        data["definition"] = GtvArray(defs)
        data["name"] = GtvString("ChromaWay")
        data["age"] = GtvInteger(20L)
        data["city"] = GtvString("Stockholm")
        return listOf(
                RowData(GtvInteger(18 ), GtvString("transactions"), TxData(18L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(3  ), GtvString("blocks"), BlockData(3L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(342), GtvString("transactions"), TxData(342L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(17 ), GtvString("blocks"), BlockData(17L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(15 ), GtvString("blocks"), BlockData(15L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(22 ), GtvString("transactions"), TxData(22L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(86 ), GtvString("transactions"), TxData(86L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv()),
                RowData(GtvInteger(23 ), GtvString("users"), GtvFactory.gtv(GtvDictionary.build(data)))
        )
    }

    @Test
    fun testBuildTree() {
        val rows = data().sorted()

        // Verify row data can be gtv encoded & decoded properly
        val b = GtvEncoder.encodeGtv(rows[0].toGtv())
        val row = RowData.fromGtv(GtvDecoder.decodeGtv(b) as GtvArray)
        require(row == rows[0])

        val leafs = rows.map { r ->
            TLeaf(longToKey(r.id.asInteger()), r)
        }
        for (l in leafs) printTree(l)

        val t1 = mergeTrees(leafs)

        // If the size of input data equal to a chunk size then t2 will be the chunk root
        val t2 =  buildChunked(
                SimpleChunkAccess( leafs )
        )
        assertEquals(sameTree(t1, t2), true)
    }

    @Test
    fun testBuildChunk() {
        val data = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )
        val blocks = listOf(
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L))))
        )

        val txs = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )

        val rows = data.sorted()

        // Verify row data can be gtv encoded & decoded properly
        val b = GtvEncoder.encodeGtv(rows[0].toGtv())
        val row = RowData.fromGtv(GtvDecoder.decodeGtv(b) as GtvArray)
        require(row == rows[0])

        val leafs = rows.map { r ->
            TLeaf(longToKey(r.id.asInteger()), r)
        }
        for (l in leafs) printTree(l)
        val t1 = mergeTrees(leafs)

        val blks = blocks.map { block ->
            TLeaf(longToKey(block.id.asInteger()), block)
        }
        val ts = txs.map { tx ->
            TLeaf(longToKey(tx.id.asInteger()), tx)
        }
        val tb =  buildChunked(
                SimpleChunkAccess( blks )
        )
        val tt = buildChunked(
                SimpleChunkAccess(ts))

        val trees = listOf(tb as TreeElement, tt as TreeElement)

        val t2 = mergeTrees(trees)

        assertEquals(t1!!.hash(), t2!!.hash())
        assertEquals(sameTree(t1, t2), true)
    }

    @Test
    fun testHashMerge() {
        val data = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )
        val blocks = listOf(
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L))))
        )

        val txs = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )

        val rows = data.sorted()

        // Verify row data can be gtv encoded & decoded properly
        val b = GtvEncoder.encodeGtv(rows[0].toGtv())
        val row = RowData.fromGtv(GtvDecoder.decodeGtv(b) as GtvArray)
        require(row == rows[0])

        val leafs = rows.map { r ->
            TLeaf(longToKey(r.id.asInteger()), r)
        }
        for (l in leafs) printTree(l)
        val t1 = mergeTrees(leafs)

        val blks = blocks.map { block ->
            TLeaf(longToKey(block.id.asInteger()), block)
        }
        val ts = txs.map { tx ->
            TLeaf(longToKey(tx.id.asInteger()), tx)
        }
        val tb =  buildChunked(
                SimpleChunkAccess( blks )
        )
        val tt = buildChunked(
                SimpleChunkAccess(ts))

        val ttt = mergeHashTrees(listOf(tt as TreeElement))

        val trees = listOf(tb as TreeElement, ttt as TreeElement)

        val t2 = mergeHashTrees(trees)

        printTree(t1)
        printTree(t2)
        assertEquals(t1!!.hash(), t2!!.hash())
    }

    @Test
    fun testTreeElementSerialization() {
        // Build tree chunk from data
        val rows = data().sorted()
        val leafs = rows.map { row ->
            TLeaf(longToKey(row.id.asInteger()), row)
        }
        val tree = buildChunked(
                SimpleChunkAccess( leafs )
        )

        // Serialize tree to file
        val path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator + tree!!.hash()
        val file = File(path)
        file.parentFile.mkdirs()
        file.createNewFile()
        val filePath = file.absolutePath
        ObjectOutputStream(FileOutputStream(file)).use {
            it.writeObject(tree)
            it.flush()
            it.close()
        }

        // Deserialize tree from object file
        val fileIn = FileInputStream(filePath)
        val objIn = ObjectInputStream(fileIn)
        val trie = objIn.readObject() as Tree
        println("--- serialize trie to file -----")
        printTree(tree)
        println("--- deserialize trie from file -----")
        printTree(trie)

        // Ensure that trees after serialize/deserialize are the same
        assertEquals(sameTree(tree, trie), true)

        // Delete file after running test
        fileIn.close()
        File(filePath).deleteRecursively()
    }

    @Test
    fun testBuildTreeDeserialization() {
        val data = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )
        val blocks = listOf(
                RowData(GtvInteger(1  ), GtvString("blocks"), BlockData(1L, "b3".toByteArray(), 1L, 1L, "h3".toByteArray(), "w3".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(2  ), GtvString("blocks"), BlockData(2L, "b1".toByteArray(), 1L, 2L, "h1".toByteArray(), "w1".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(3 ), GtvString("blocks"), BlockData(3L, "b17".toByteArray(), 1L, 3L, "h17".toByteArray(), "w17".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L)))),
                RowData(GtvInteger(4 ), GtvString("blocks"), BlockData(4L, "b15".toByteArray(), 1L, 4L, "h15".toByteArray(), "w15".toByteArray(), 10000L).toGtv(), GtvArray(arrayOf(GtvFactory.gtv(5L))))
        )

        val txs = listOf(
                RowData(GtvInteger(5 ), GtvString("transactions"), TxData(5L, 1L, "t18".toByteArray(), "t18".toByteArray(), "t18".toByteArray(), 1L).toGtv()),
                RowData(GtvInteger(6), GtvString("transactions"), TxData(6L, 1L, "t342".toByteArray(), "t342".toByteArray(), "t342".toByteArray(), 3L).toGtv()),
                RowData(GtvInteger(7 ), GtvString("transactions"), TxData(7L,  1L, "t22".toByteArray(), "t22".toByteArray(), "t22".toByteArray(), 15L).toGtv()),
                RowData(GtvInteger(8 ), GtvString("transactions"), TxData(8L,1L, "t86".toByteArray(), "t86".toByteArray(), "t86".toByteArray(), 12L).toGtv())
        )

        val rows = data.sorted()
        val leafs = rows.map { row ->
            TLeaf(longToKey(row.id.asInteger()), row)
        }
        val tree = buildChunked(
                SimpleChunkAccess( leafs )
        )

        val blks = blocks.map { block ->
            TLeaf(longToKey(block.id.asInteger()), block)
        }
        val ts = txs.map { tx ->
            TLeaf(longToKey(tx.id.asInteger()), tx)
        }

        val treeBlock = buildChunked(
                SimpleChunkAccess( blks )
        )

        val treeTx = buildChunked(
                SimpleChunkAccess( ts )
        )

        // Serialize trees to file
        var path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator + treeBlock!!.hash()
        var file = File(path)
        file.parentFile.mkdirs()
        file.createNewFile()
        val fileBlock = file.absolutePath
        ObjectOutputStream(FileOutputStream(file)).use {
            it.writeObject(treeBlock)
            it.flush()
            it.close()
        }

        path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator + treeTx!!.hash()
        file = File(path)
        file.parentFile.mkdirs()
        file.createNewFile()
        val fileTx = file.absolutePath
        ObjectOutputStream(FileOutputStream(file)).use {
            it.writeObject(treeTx)
            it.flush()
            it.close()
        }

        // Deserialize tree from object file
        val fileBlockIn = FileInputStream(fileBlock)
        val blockIn = ObjectInputStream(fileBlockIn)
        val trieBlock = blockIn.readObject() as Tree

        val fileTxIn = FileInputStream(fileTx)
        val txIn = ObjectInputStream(fileTxIn)
        val trieTx = txIn.readObject() as Tree

        val trees = listOf(trieBlock as TreeElement, trieTx as TreeElement)

        val treeD = mergeTrees(trees)

        assertEquals(tree!!.hash(), treeD!!.hash())
        assertEquals(sameTree(tree, treeD), true)

        // Delete file after running test
        blockIn.close()
        File(fileBlock).deleteRecursively()
        txIn.close()
        File(fileTx).deleteRecursively()
    }
}