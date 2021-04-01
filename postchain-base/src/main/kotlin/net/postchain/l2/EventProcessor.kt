package net.postchain.l2

import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import net.postchain.core.BlockQueries
import net.postchain.core.BlockValidationMistake
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.ethereum.contracts.ERC20Token
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.OpData
import org.web3j.abi.EventEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.http.HttpService
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

interface EventProcessor {
    fun shutdown()
    fun getEventData(): List<Array<Gtv>>
    fun getEventDataAtBlockHeight(height: BigInteger): List<Array<Gtv>>
    fun isValidEventData(ops: Array<OpData>): Boolean
}

abstract class BaseEventProcessor(
    val blockQueries: BlockQueries
) : EventProcessor

class L2TestEventProcessor(
    blockQueries: BlockQueries
) : BaseEventProcessor(blockQueries) {

    private val ds = EthereumL2DigestSystem(KECCAK256)

    private var lastBlock = 0

    override fun shutdown() {
        return
    }

    override fun getEventData(): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        val start = lastBlock+1
        for (i in start..start+10) {
            lastBlock++
            out.add(generateData(i.toLong(), i))
        }
        return out
    }

    override fun getEventDataAtBlockHeight(height: BigInteger): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        for (i in 1..10) {
            out.add(generateData(height.toLong(), i))
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
            gtv(i.toLong()), gtv(EventEncoder.encode(ERC20Token.TRANSFER_EVENT)),
            gtv(contractAddress), gtv(from), gtv(to), gtv(BigInteger.valueOf(i.toLong()))
        )
    }
}

class EthereumEventProcessor(
    url: String,
    contractAddress: String,
    blockQueries: BlockQueries
) : BaseEventProcessor(blockQueries) {

    private val web3c: Web3Connector = Web3Connector(Web3j.build(HttpService(url)), contractAddress)
    private val contract: ERC20Token = ERC20Token.load(
        web3c.contractAddress,
        web3c.web3j,
        ClientTransactionManager(web3c.web3j, "0x0"),
        DefaultGasProvider()
    )

    override fun shutdown() {
        web3c.shutdown()
    }

    override fun getEventDataAtBlockHeight(height: BigInteger): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        val blockHeight = DefaultBlockParameter.valueOf(height)
        contract
            .transferEventFlowable(blockHeight, blockHeight)
            .subscribe(
                { event ->
                    out.add(
                        arrayOf(
                            gtv(event.log.blockNumber), gtv(event.log.blockHash), gtv(event.log.transactionHash),
                            gtv(event.log.logIndex), gtv(EventEncoder.encode(ERC20Token.TRANSFER_EVENT)),
                            gtv(contract.contractAddress), gtv(event.from), gtv(event.to), gtv(event.value)
                        )
                    )
                }, {
                    it.printStackTrace()
                    throw BlockValidationMistake("")
                }
            )
        return out
    }

    override fun isValidEventData(ops: Array<OpData>): Boolean {
        var isValid = false
        for (op in ops) {
            if (op.opName != OP_ETH_EVENT) continue
            val height = DefaultBlockParameter.valueOf(op.args[0].asBigInteger())
            contract.transferEventFlowable(height, height)
                .subscribe {
                    if (it.from == op.args[6].asString()
                        && it.to == op.args[7].asString()
                        && it.value == op.args[8].asBigInteger()
                    ) {
                        isValid = true
                    }
                }
            if (!isValid) return false
        }
        return isValid
    }

    override fun getEventData(): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        val to = web3c.web3j.ethBlockNumber().send().blockNumber.minus(BigInteger.valueOf(100L))
        val block = blockQueries.query("get_last_eth_block", gtv(mutableMapOf())).get()
        val from: BigInteger
        if (block == GtvNull) {
            from = to.minus(BigInteger.valueOf(100L))
        } else {
            from = block.asDict()["eth_block_height"]!!.asBigInteger().plus(BigInteger.ONE)
        }
        contract
            .transferEventFlowable(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to))
            .subscribe(
                { event ->
                    out.add(
                        arrayOf(
                            gtv(event.log.blockNumber), gtv(event.log.blockHash), gtv(event.log.transactionHash),
                            gtv(event.log.logIndex), gtv(EventEncoder.encode(ERC20Token.TRANSFER_EVENT)),
                            gtv(contract.contractAddress), gtv(event.from), gtv(event.to), gtv(event.value)
                        )
                    )
                }, {
                    it.printStackTrace()
                    throw BlockValidationMistake("")
                }
            )
        return out
    }
}