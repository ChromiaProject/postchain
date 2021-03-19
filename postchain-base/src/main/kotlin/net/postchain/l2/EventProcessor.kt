package net.postchain.l2

import net.postchain.core.BlockQueries
import net.postchain.core.BlockValidationMistake
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
        var isValid = true
        for (op in ops) {
            if (op.opName != OP_ETH_EVENT) continue
            val height = DefaultBlockParameter.valueOf(op.args[0].asBigInteger())
            contract.transferEventFlowable(height, height)
                .subscribe {
                    if (it.from != op.args[6].asString()
                        || it.to != op.args[7].asString()
                        || it.value != op.args[8].asBigInteger()
                    ) {
                        isValid = false
                    }
                }
        }
        return isValid
    }

    override fun getEventData(): List<Array<Gtv>> {
        val out = mutableListOf<Array<Gtv>>()
        val to = web3c.web3j.ethBlockNumber().send().blockNumber.minus(BigInteger.valueOf(100L))
        var from = blockQueries.query("get_last_eth_block", GtvNull).get().asBigInteger()
        if (from == BigInteger.ZERO) {
            from = to.minus(BigInteger.valueOf(100L))
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