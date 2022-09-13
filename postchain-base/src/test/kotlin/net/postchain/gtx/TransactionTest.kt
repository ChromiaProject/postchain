// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

val myCS = Secp256K1CryptoSystem()

fun makeNOPGTX(bcRid: BlockchainRid): ByteArray {
    val b = GtxBuilder(bcRid, listOf(pubKey(0)), myCS)
        .addOperation(GtxNop.OP_NAME, gtv(42))
        .finish()
        .sign(myCS.buildSigMaker(pubKey(0), privKey(0)))
        .buildGtx()
    return b.encode()
}

class GTXTransactionTest {

    val module = StandardOpsGTXModule()
    val gtxData = makeNOPGTX(BlockchainRid.ZERO_RID)

    @Test
    fun runtx() {
        val factory = GTXTransactionFactory(BlockchainRid.ZERO_RID, module, myCS)
        val tx = factory.decodeTransaction(gtxData)
        assertTrue(tx.getRID().size > 1)
        assertTrue(tx.getRawData().size > 1)
        assertTrue((tx as GTXTransaction).ops.size == 1)
        assertFalse(tx.isCorrect()) // Since we are not allowed to just use nop.
    }
}