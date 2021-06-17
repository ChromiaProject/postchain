package net.postchain.base.icmf

import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.TxEventSink
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.base.merkle.Hash
import net.postchain.core.BlockchainProcess
import net.postchain.core.ProgrammerMistake
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash


/**
 * Can receive pumped messages.
 */
class IcmfPumpStation() {

    private var txHandler: SpecialTransactionHandler? = null
    var bcRidToPipe: Map<BlockchainRid, Pipe>
    val events =

    /**
     * Our handler for incoming messages only knows how to processes headers
     */
    val eventProc = object : TxEventSink {
        override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {

            when (type) {
                "header" -> {
                    val header = BlockHeaderDataFactory.buildFromGtv(data)
                    var hash: Hash?  = header.getMerkleRootHash()
                    val bcRid = header.gtvBlockchainRid
                    val height = header.getHeight()

                    hash = data.merkleHash(  GtvMerkleHashCalculator(cryptoSystem) )
                    events.add(hash)
                    store.writeEvent(bctx, hash, data)
                }
                else -> throw ProgrammerMistake("Cannot handle this message type: $type")
            }
        }
    }

    fun processHeaderEvent(ctxt: TxEContext, header: BlockHeaderData) {

    }

    fun isEmpty(): Boolean  {
        return false // TODO: fix
    }

    fun getMessage(): IcmfMessage? {
        return null // TODO: fix
    }

    /**
     * Returns a MessagePipe if the given process is something that should be connected
     *
     */
    fun maybeConnect(bcProcess: BlockchainProcess): IcmfMessagePipe? {
        if (shouldConnect(bcProcess)) {
            return IcmfMessagePipe()
        } else {
            return null
        }
    }

    /**
     * @return true if we should connect to this process
     */
    private fun shouldConnect(bcProcess: BlockchainProcess): Boolean {
        return false // TODO: fix
    }

    fun setSpecialTransactionHandler(handler: SpecialTransactionHandler) {
        txHandler = handler
    }
}