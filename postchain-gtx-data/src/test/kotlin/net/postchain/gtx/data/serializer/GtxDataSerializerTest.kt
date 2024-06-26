// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.data.serializer

import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvString
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GtxDataSerializerTest {

    val op_name = "op_name"
    val op_args = listOf(1,2,3,4)
    val blockchainRID = BlockchainRid.buildRepeat(22)
    val aliceSigner: ByteArray = "010101".hexStringToByteArray()
    val aliceSignature: ByteArray = "DDDDDD".hexStringToByteArray()

    @Test
    fun testBuildGtvFromGTXData_one_operation() {


        // ---------- Build da expected GTV struct -----------

        // Operation Data
        val opName: GtvString = gtv(op_name)
        val opArgs: GtvArray = gtv(op_args.map { gtv(it.toLong()) })
        val op1: GtvArray = gtv(listOf(opName, opArgs))

        // Transaction Body Data
        val bcRid: GtvByteArray = gtv(blockchainRID)
        val operations: GtvArray = gtv(listOf(op1))
        val signers: GtvArray = gtv(listOf(gtv(aliceSigner)))
        val txb: GtvArray = gtv(listOf(bcRid, operations, signers))

        // Transaction Data
        val signatures: GtvArray = gtv(listOf(gtv(aliceSignature)))
        val expectedGtvTxStruct = gtv(listOf(txb, signatures))

        // ---------- Build GTXData ---------------------------
        val expectedOp = GtxOp(op_name, *op_args.map { gtv(it.toLong()) }.toTypedArray())
        val body = GtxBody(blockchainRID, arrayOf(expectedOp), arrayOf(aliceSigner))
        val txData = Gtx(body, arrayOf(aliceSignature))

       assertEquals(expectedGtvTxStruct, txData.toGtv())
    }
}