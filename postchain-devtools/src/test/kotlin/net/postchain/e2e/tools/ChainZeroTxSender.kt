package net.postchain.e2e.tools

import net.postchain.gtv.GtvString
import net.postchain.gtx.GtxNop
import kotlin.random.Random

class ChainZeroTxSender(
        apiUrl: String,
        blockchainRid: String,
        privKey: String,
        pubKey: String
) : TxSender(
        apiUrl,
        blockchainRid,
        privKey,
        pubKey
) {

    fun postNopTx() {
        postTx { txBuilder ->
            val nonce = Random.Default.nextInt(1000).toString()
            txBuilder.addOperation(GtxNop.OP_NAME, arrayOf(GtvString(nonce)))
        }
    }

}