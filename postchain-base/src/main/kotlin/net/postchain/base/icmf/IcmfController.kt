package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake


/**
 * The [IcmfController] is a coordinator for ICMF (Inter-Chain Message Facility).
 *
 * ------------------
 * ICMF in general
 * ------------------
 * Communication between blockchains via ICMF is more loosely coupled compared with regular blockchain dependencies.
 * Where a "BC dependency" must know what height a block should depend on another block, ICMF just send a message and
 * doesn't care one bit who reads it and when. But with ICMF we must still know what chains can send what messages to
 * what other chains. We use [IcmfPipe] to represent such a connection.
 *
 * "Source chain" (SC) = a BC that generates messages
 * "Listener chain" (LC) = a BC that receives messages
 *
 * Currently we use a simple mechanics, where a listener gets all messages from a connected source chain,
 * this is a bit wasteful, since usually a listener is only interested in one message type. // TODO: Olle: ok or fix?
 *
 * The first use-case for ICMF is anchoring, where messages sent to anchor chains can be processed at any time by the
 * anchor chain, and a loose connection is preferable.
 *
 * --------
 * The Controller
 * --------
 * The [IcmfController] is the main ICMF class that ties everything together. It ..
 * 1. ... knows if a [BlockchainProcess] needs a [IcmfPipe] or not (via "maybeConnect()")
 * 2. ... adds newly created [IcmfPipe] to the receiver, so that messages added to the pipe will be listened to.
 *
 * -------------
 *  Re startup:
 * -------------
 * A "source chain" (SC) must have a [IcmfPipe] to "listener chain" (LC), but the SC might
 * start before the LC has been started, so we have to know all the LC chainIid before SC starts.
 *
 * Manual mode:
 *            Here we must configure the SC to know in advance what LCs they must use.
 *            When the SC is started, we will use this configuration to create correct amount of [IcmfPipe]s.
 *            (In practice nobody want to do this, too much configuration to write by hand, but useful in tests)
 * Managed mode:
 *            For managed mode we only have to put configuration on the LC, and we use [IcmfPipeConnectionSync] to
 *            handle the initialization logic.
 *            Currently, [IcmfPipeConnectionSync] has a complex initiation process (for managed mode):
 *            1. Call "initialize()" when we have access to DB, then
 *            2. Call "setAllChains()" first when we gotten the "ChainIids" from Chain 0.
 *
 */
class IcmfController : KLogging(){

    private var pipeConnSync: IcmfPipeConnectionSync? = null

    private val listenerChainToFetcherMap = HashMap<BlockchainRid, IcmfFetcher>() // Must use the correct [IcmfFetcher] for each listener chain.

    // --------------
    // The task of sending and receiving of messages is delegated to the [IcmfDispatcher] and the [IcmfReceiver].
    // --------------
    val icmfDispatcher = IcmfDispatcher() // This is intentionally public, anyone can get it
    val icmfReceiver = IcmfReceiver() // This is intentionally public, anyone can get it

    /**
     * @return true if we have a [IcmfPipeConnectionSync]
     */
    fun isInitialized(): Boolean = this.pipeConnSync != null

    /**
     * Why don't we set the [IcmfPipeConnectionSync] in the constructor?
     * A: b/c we usually must wait until we have the DB connection before
     * the [IcmfPipeConnectionSync] can created.
     */
    fun initialize(pipeSync: IcmfPipeConnectionSync) {
        if (this.pipeConnSync != null) {
            logger.error("Why are we initializing the IcmfController twice?")
        }
        this.pipeConnSync = pipeSync
        this.pipeConnSync!!.setDispatcher(icmfDispatcher) // Will be updated when new connections are created
        this.pipeConnSync!!.setReceiver(icmfReceiver) // Will be updated when new connections are created
        logger.info("ICMF properly initialized.")
    }

    fun seenThisChainBefore(chainIid: Long): Boolean = pipeConnSync!!.seenThisChainBefore(chainIid)

    /**
     * @return true if we have initiated ICMF with all chains for this node.
     */
    fun areChainsSet(): Boolean {
        return if (pipeConnSync == null) {
            false
        } else {
            pipeConnSync!!.areChainsSet()
        }
    }

    /**
     * For managed mode only! Manual mode cannot do this.
     *
     * Why is done via a setter when is should have been in the constructor?
     * A: b/c we usually must wait until we have the chain IIDs for all chains.
     * (Yes, this is a bit tricky. An alternative would be to use [BlockchainRID] to keep track of chains.)
     *
     * @param allChains are all chains Chain 0 knows about during startup.
     */
    fun setAllChains(allChains: Set<BlockchainRelatedInfo>) {
        if (pipeConnSync == null) {
            throw ProgrammerMistake("Cannot add chains before PipeConnectionSync has been set.")
        } else {
            pipeConnSync!!.addChains(allChains)
        }
    }

    /**
     * Decides if a new BC process needs pipes or not, and if they are needed they will be created.
     * We also update the [IcmfDispatcher] and [IcmfReceiver] during this process.
     *
     * @param bcProcess is the new process that might need a pipe
     * @param height is the current height of the bcProcess we are working with
     * @return a set of new [IcmfPipe] if the given process should be connected to something
     */
    fun maybeConnect(bcProcess: BlockchainProcess, height: Long): List<IcmfPipe> {
        if (pipeConnSync == null) {
            throw ProgrammerMistake("Cannot use ICMF to connect without PipeConnectionSync set.")
        }

        val newPipes = ArrayList<IcmfPipe>()

        val givenChainIid = bcProcess.getEngine().getConfiguration().chainID
        val givenChainBcRid = bcProcess.getEngine().getConfiguration().blockchainRid
        val givenChainInfo = BlockchainRelatedInfo(givenChainBcRid, null, givenChainIid)

        if (!seenThisChainBefore(givenChainIid)) {
            if(logger.isDebugEnabled()) {
                logger.debug("First time we've seen chain id: $givenChainIid , height $height")// Probably b/c we are running manual mode
            }
            pipeConnSync!!.addChains(setOf(givenChainInfo))

            // (manual mode only)
            icmfReceiver.maybeUpdateWithChainIid(givenChainInfo)
        }

        // ------------
        // We assume this is a source chain, and try to see if it needs to connect to listening chains
        // ------------
        val listeningChainMap = pipeConnSync!!.getListeningChainsForSource(givenChainInfo)
        for (listenerChainRid: BlockchainRid in listeningChainMap.keys) {

            if (listeningChainMap[listenerChainRid]!!.shouldConnect(listenerChainRid, bcProcess, this)) {
                // We know we should connect this source with the listener, and this means updating all parts:
                val bcInfo = pipeConnSync!!.getBlockchainInfo(listenerChainRid)

                // 1. Get the relevant fetcher
                // TODO: Olle: Timing problem here, what if listener (e.g. Anchor) chain hasn't started yet and thus nothing in this map?
                val fetcher: IcmfFetcher = listenerChainToFetcherMap[bcInfo.blockchainRid]?:
                    throw ProgrammerMistake("No fetcher for chain: ${bcInfo.blockchainRid.toHex()}, must exist at this point.")

                // 2. Update the receiver and get the pipe in one go
                val newPipe = icmfReceiver.connectPipe(givenChainIid, bcInfo, fetcher)
                // 3. Update the dispatcher
                icmfDispatcher.addMessagePipe(newPipe, height)
                // 4. Add new pipe to the return list
                newPipes.add(newPipe)
            }
        }

        return newPipes
    }


    fun setFetcherForListenerChain(listenerBcRid: BlockchainRid, fetcher: IcmfFetcher) {
        val existingFetcher = listenerChainToFetcherMap[listenerBcRid]
        if (existingFetcher != null) {
            throw ProgrammerMistake("Listener chain ${listenerBcRid.toHex()} already has a fetcher: ${existingFetcher.javaClass}")
        } else {
            listenerChainToFetcherMap[listenerBcRid] = fetcher
        }
    }

}