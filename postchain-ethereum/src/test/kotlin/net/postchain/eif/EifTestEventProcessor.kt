package net.postchain.eif

import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import net.postchain.ethereum.contracts.TokenBridge
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.OpData
import org.web3j.abi.EventEncoder
import java.math.BigInteger
import java.security.MessageDigest

class EifTestEventProcessor : EventProcessor {

    private val ds = SimpleDigestSystem(MessageDigest.getInstance(KECCAK256))

    private var lastBlock = 0

    override fun shutdown() {
        return
    }

    override fun getEventData(): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        val start = lastBlock + 1
        for (i in start..start + 10) {
            lastBlock++
            out.add(generateData(i.toLong(), i))
        }
        return out
    }

    override fun isValidEventData(ops: Array<OpData>): Boolean {
        return true
    }

    private fun generateData(height: Long, i: Int): Array<Gtv> {
        val blockHash = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()).toHex()
        val transactionHash = ds.digest(BigInteger.valueOf((100 - i).toLong()).toByteArray()).toHex()
        val contractAddress = ds.digest(BigInteger.valueOf(999L).toByteArray()).toHex()
        val from = ds.digest(BigInteger.valueOf(1L).toByteArray()).toHex()
        val to = ds.digest(BigInteger.valueOf(2L).toByteArray()).toHex()
        return arrayOf(
            gtv(height), gtv(blockHash), gtv(transactionHash),
            gtv(i.toLong()), gtv(EventEncoder.encode(TokenBridge.DEPOSITED_EVENT)),
            gtv(contractAddress), gtv(from), gtv(to), gtv(BigInteger.valueOf(i.toLong()))
        )
    }
}
