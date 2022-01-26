package net.postchain.base.merkle

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtv.merkleHash
import net.postchain.gtv.path.GtvPath
import net.postchain.gtv.path.GtvPathFactory
import net.postchain.gtv.path.GtvPathSet
import org.junit.Test
import kotlin.test.assertEquals

class PostchainMerkleProofTest {
    val cryptoSystem = SECP256K1CryptoSystem()

    @Test
    fun testMerkleProofForGtvDictionary() {

        // create postchain block header extra data that be used in EL2 and other extensions
        val gtvExtra = GtvDictionary.build(mapOf(
            "b" to GtvByteArray("11".hexStringToByteArray()),
            "i" to GtvByteArray("12".hexStringToByteArray()),
            "e" to GtvByteArray("13".hexStringToByteArray()),
            "a" to GtvByteArray("14".hexStringToByteArray()),
            "o" to GtvByteArray("15".hexStringToByteArray()),
            "u" to GtvByteArray("16".hexStringToByteArray()),
        ))

        val path: Array<Any> = arrayOf("e")
        val leaf = gtvExtra["e"]!!
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val calculator = GtvMerkleHashCalculator(cryptoSystem)
        val extraProofTree = gtvExtra.generateProof(gtvPaths, calculator)
        assertEquals(gtvExtra.merkleHash(calculator).toHex(), extraProofTree.merkleHash(calculator).toHex())
        val printer = TreePrinter()
        val printableBinaryTree = PrintableTreeFactory.buildPrintableTreeFromProofTree(extraProofTree)
        val treePrintout = printer.printNode(printableBinaryTree)
        println(treePrintout)
        val proofs = printer.getMerkleProof(printableBinaryTree)
        val treeProofs = proofs.first
        val root = printer.verifyMerkleProof(treeProofs, proofs.second, leaf)
        assertEquals(root.toHex(), gtvExtra.merkleHash(calculator).toHex())
    }
}