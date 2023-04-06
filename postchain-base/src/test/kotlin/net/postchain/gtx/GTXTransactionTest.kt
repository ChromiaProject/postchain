// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class GTXTransactionTest {

    private val cs = Secp256K1CryptoSystem()

    @Test
    fun `tx with only nop op is invalid`() {
        val signers = listOf(pubKey(0))
        val sigMaker = cs.buildSigMaker(KeyPair(pubKey(0), privKey(0)))
        val factory = GTXTransactionFactory(ZERO_RID, StandardOpsGTXModule(), cs)
        val gtxData = GtxBuilder(ZERO_RID, signers, cs)
                .addOperation(GtxNop.OP_NAME, gtv(42))
                .finish().sign(sigMaker).buildGtx().encode()
        val tx = factory.decodeTransaction(gtxData) as GTXTransaction
        assertTrue(tx.getRID().size > 1)
        assertTrue(tx.getRawData().size > 1)
        assertTrue(tx.ops.size == 1)

        // Since we are not allowed to just use nop.
        assertThrows<TransactionIncorrect> {
            tx.checkCorrectness()
        }
    }

    @Test
    fun `special __nop and nop are counted as different ops`() {
        val tx = GTXTransaction(
                byteArrayOf(), GtvNull, Gtx(GtxBody(ZERO_RID, arrayOf(), arrayOf()), arrayOf()), arrayOf(), arrayOf(),
                arrayOf(
                        GtxNop(Unit, ExtOpData(GtxNop.OP_NAME, 0, arrayOf(), ZERO_RID, arrayOf(), arrayOf())),
                        GtxSpecNop(Unit, ExtOpData(GtxTimeB.OP_NAME, 1, arrayOf(gtv(1)), ZERO_RID, arrayOf(), arrayOf())),
                        GtxTimeB(Unit, ExtOpData(GtxTimeB.OP_NAME, 2, arrayOf(gtv(1), gtv(2)), ZERO_RID, arrayOf(), arrayOf())),
                        object : Transactor {
                            override fun isSpecial() = false
                            override fun checkCorrectness() {}
                            override fun apply(ctx: TxEContext) = true
                        }
                ),
                byteArrayOf(), byteArrayOf(), cs
        )

        assertDoesNotThrow {
            tx.checkCorrectness()
        }
    }
}
