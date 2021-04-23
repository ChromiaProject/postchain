// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.devtools.KeyPairHelper.privKey
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvFactory.gtv
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFalse

val myCS = SECP256K1CryptoSystem()

fun makeNOPGTX(bcRid: BlockchainRid): ByteArray {
    val b = GTXDataBuilder(bcRid, arrayOf(pubKey(0)), myCS)
    b.addOperation("nop", arrayOf(gtv(42)))
    b.finish()
    b.sign(myCS.buildSigMaker(pubKey(0), privKey(0)))
    return b.serialize()
}

class GTXTransactionTest {
    class DummyOperation : Transactor {
        override fun isSpecial() = false
        override fun isL2() = false
        override fun isCorrect() = true
        override fun apply(ctx: TxEContext) = true
    }

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