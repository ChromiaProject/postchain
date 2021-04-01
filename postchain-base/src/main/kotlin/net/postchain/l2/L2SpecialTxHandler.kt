package net.postchain.l2

import net.postchain.base.BlockchainRid
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.core.Transaction
import net.postchain.gtx.*

const val OP_ETH_EVENT = "__eth_event"
const val OP_ETH_BLOCK = "__eth_block"

class L2SpecialTxHandler(
    module: GTXModule,
    blockchainRID: BlockchainRid,
    cs: CryptoSystem,
    factory: GTXTransactionFactory
) : GTXSpecialTxHandler(module, blockchainRID, cs, factory) {

    private lateinit var proc: EventProcessor

    fun useEventProcessor(processor: EventProcessor) {
        proc = processor
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        if (position == SpecialTransactionPosition.EthEvent) {
            return module.getOperations().contains(OP_ETH_EVENT)
        }
        return super.needsSpecialTransaction(position)
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction {
        if (position == SpecialTransactionPosition.EthEvent) {
            val b = GTXDataBuilder(blockchainRID, arrayOf(), cs)
            val data = proc.getEventData()
            val block = data.first
            if (block.isNotEmpty()) {
                b.addOperation(OP_ETH_BLOCK, block)
            }
            val events = data.second
            for (event in events) {
                b.addOperation(OP_ETH_EVENT, event)
            }
            return factory.decodeTransaction(b.serialize())
        }
        return super.createSpecialTransaction(position, bctx)
    }

    override fun validateSpecialTransaction(
        position: SpecialTransactionPosition,
        tx: Transaction,
        ectx: BlockEContext
    ): Boolean {
        if (position == SpecialTransactionPosition.EthEvent) {
            if (!tx.isL2()) return true
            val gtxTnx = tx as GTXTransaction
            val gtxData = gtxTnx.gtxData
            return proc.isValidEventData(gtxData.transactionBodyData.operations)
        }
        return super.validateSpecialTransaction(position, tx, ectx)
    }
}