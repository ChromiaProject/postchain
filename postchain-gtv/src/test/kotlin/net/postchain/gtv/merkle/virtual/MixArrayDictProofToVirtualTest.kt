// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.virtual

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.MerkleHashCalculatorDummy
import net.postchain.gtv.merkle.MixArrayDictToGtvBinaryTreeHelper
import net.postchain.gtv.merkle.TreeHelper
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail

class MixArrayDictProofToVirtualTest {


    @Test
    fun test_dictWithArr_where_path_is_to_leaf4() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one", 3)
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val gtvDict = MixArrayDictToGtvBinaryTreeHelper.buildGtvDictWithSubArray4()

        val merkleProofTree = gtvDict.generateProof(gtvPaths, calculator)

        val virtualGtv = merkleProofTree.toGtvVirtual()
        val merkleRoot = virtualGtv.merkleHash(calculator)
        assertEquals(MixArrayDictToGtvBinaryTreeHelper.expecedMerkleRoot_dict1_array4, TreeHelper.convertToHex(merkleRoot))


        val orgGtv = gtvDict["one"]!![3]
        val gtvFromVirt = virtualGtv["one"]!![3]
        assertEquals(orgGtv, gtvFromVirt)

        try {
            virtualGtv["one"]!![2]
            fail()
        } catch (e: UserMistake) {
            // Nothing, it's what we expect
        }
    }

    /**
     * Here we try to prove the entire sub array (1,2,3,4)
     */
    @Test
    fun test_dictWithArr_where_path_is_to_sub_arr() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one")
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val gtvDict = MixArrayDictToGtvBinaryTreeHelper.buildGtvDictWithSubArray4()

        val merkleProofTree = gtvDict.generateProof(gtvPaths, calculator)

        val virtualGtv = merkleProofTree.toGtvVirtual()
        val merkleRoot = virtualGtv.merkleHash(calculator)
        assertEquals(MixArrayDictToGtvBinaryTreeHelper.expecedMerkleRoot_dict1_array4, TreeHelper.convertToHex(merkleRoot))

        val orgGtv = gtvDict["one"]!!
        val gtvFromVirt = virtualGtv["one"]!!
        assertEquals(orgGtv, gtvFromVirt)
    }
}