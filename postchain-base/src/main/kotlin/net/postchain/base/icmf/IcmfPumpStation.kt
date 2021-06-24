package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.TxEventSink
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.common.data.Hash
import net.postchain.core.ProgrammerMistake
import net.postchain.core.TxEContext
import net.postchain.core.BlockchainProcess
import net.postchain.gtv.Gtv
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash


/**
 * The [IcmfPumpStation] is a coordinator for ICMF (Inter-Chain Message Facility).
 *
 * --------
 * ICMF
 * --------
 * Communication between blockchains via ICMF is more loosely coupled compared with regular blockchain dependencies.
 * Where a "BC dependency" must know what height a block should depend on another block, ICMF just send a message and
 * doesn't care one bit who reads it and when. But with ICMF we must still know what chains can send what messages to
 * what other chains. We use [IcmfPipe] to represent such a connection.
 *
 * The first use-case for ICMF is anchoring, where messages sent to anchor chains can be processed at any time by the
 * anchor chain, and a loose connection is preferable.
 *
 * --------
 * The Station
 * --------
 * The station has these responsibilities:
 *
 * 1. It knows if a [BlockchainProcess] needs a [IcmfPipe] or not (via "maybeConnect()")
 * 2. It receives new heights from [IcmfPipe]s
 * 3. It uses the [SpecialTransactionHandler] to create transactions from messages
 *
 */
class IcmfPumpStation() {

    companion object : KLogging()

    private val targetChains = ArrayList<Long>() // Targets we can use
    private var txHandler: SpecialTransactionHandler? = null

    private val heightReceiver = IcmfReceiver()

    /**
     * Our handler for incoming messages only knows how to processes headers
     */
    val eventProc = object : TxEventSink {
        override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {

            /*
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
             */
        }
    }


    fun isEmpty(): Boolean  {
        return false // TODO: fix
    }

    /**
     * Decides if a new BC process needs pipes or not
     *
     * @param bcProcess is the new process that might need a pipe
     * @return a set of new [IcmfPipe] if the given process should be connected to something
     */
    fun maybeConnect(bcProcess: BlockchainProcess): List<IcmfPipe> {
        val newPipes = ArrayList<IcmfPipe>()

        for (targetChainIid in targetChains) {
            if (shouldConnect(targetChainIid, bcProcess)) {
                val sourceChainIid = bcProcess.getEngine().getConfiguration().chainID
                val newPipe = heightReceiver.connectPipe(sourceChainIid, targetChainIid)
                newPipes.add(newPipe)
            }
        }
        return newPipes
    }

    /**
     * NOTE: Currently we act stupid here and always connect, but it is OK because right now, we only have anchor chains
     * and therefor we always want to connect a new process to all target chains target. Later this will probably change.
     *
     * @return true if we should connect to this process
     */
    private fun shouldConnect(targetChainIid: Long, bcProcess: BlockchainProcess): Boolean {
        val sourceChainIid: Long = bcProcess.getEngine().getConfiguration().chainID
        if (heightReceiver.isSourceAndTargetConnected(sourceChainIid, targetChainIid)) {
            logger.warn("shouldConnect() -- source chain id: $sourceChainIid and target chain id: $targetChainIid already connected")
            return false
        }
        return true
    }

    fun setSpecialTransactionHandler(handler: SpecialTransactionHandler) {
        txHandler = handler
    }
}