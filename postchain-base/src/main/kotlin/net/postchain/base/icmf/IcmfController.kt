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

    private val pipeConnSync = IcmfPipeConnectionSync()

    private val listenerChainToFetcherMap = HashMap<Long, IcmfFetcher>() // Must use the correct [IcmfFetcher] for each listener chain.

    // --------------
    // The task of sending and receiving of messages is delegated to the [IcmfDispatcher] and the [IcmfReceiver].
    // --------------
    val icmfDispatcher = IcmfDispatcher() // This is intentionally public, anyone can get it
    val icmfReceiver = IcmfReceiver() // This is intentionally public, anyone can get it

    fun getListenerConnChecker(bcIid: Long): ConnectionChecker? {
        return pipeConnSync!!.getListenerConnChecker(bcIid)
    }

    /**
     * When this method is called we know the chain is going down and we don't know when it's going up again.
     * We can afford do be brutal and throw away pipes that are not empty, b/c we won't lose any messages anyway
     * (Any messages potentially still in a pipe will be transferred when both chains are active next time).
     *
     * @param chainIid remove this chain from caches and objects
     */
    @Synchronized
    fun chainStop(chainIid: Long) {
        this.pipeConnSync.chainShuttingDown(chainIid)
        this.icmfDispatcher.chainShuttingDown(chainIid)
        this.icmfReceiver.chainShuttingDown(chainIid)
    }

    /**
     * Decides if a new BC process needs pipes or not, and if they are needed they will be created.
     *
     * - If the chain will send messages, it will act as "source" in a new [IcmfPipe],
     * - If the chain is configured to receive messages it will act as "listener" in a new pipe.
     * A pipe can be both a listener and a source.
     *
     * The pipe will only be created if both chains are active, and it's the job of the [IcmfPipeConnectionSync] to
     * know about these things.
     *
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

        val conf = bcProcess.getEngine().getConfiguration()
        val givenChainIid = conf.chainID
        val givenChainListenerConf = conf.icmfListener

        // Create listener pipes
        if (givenChainListenerConf != null) {
            val connChecker = ConnectionCheckerFactory.build(givenChainIid, givenChainListenerConf)
            val listPipes = getPipesForListenerRole(givenChainIid, height, connChecker)
            this.pipeConnSync.addListenerChain(givenChainIid, connChecker) // We can do this after the getPipes
            newPipes.addAll(listPipes)
        }

        // Create source pipes
        val sourcePipes = getPipesForSourceRole(givenChainIid, bcProcess, height)
        newPipes.addAll(sourcePipes)

        return newPipes
    }


    /**
     * At this point we know the chain is configured as a listener, and our job is to figure out what source chains
     * that should have given chain as a listener, and create pipes for them.
     *
     * @return any [IcmfPipe] where the given chain acts "listener"
     */
    private fun getPipesForListenerRole(
        iid: Long, // the given chain IID
        height: Long,
        connChecker: ConnectionChecker
    ) : List<IcmfPipe> {
        val newPipes = ArrayList<IcmfPipe>()

        // Validation
        val existingPipes = icmfReceiver.getAllPipesForListenerChain(iid)
        if (existingPipes != null && !existingPipes.isEmpty()) {
            logger.warn("We are about to start a chain: $iid that already has ${existingPipes.size} active pipes" +
                    "where it is listener. Very likely something is wrong.")
        }

        // Fetch all possible source chains
        val sourceChains = pipeConnSync!!.getSourceChainsFromListener(iid)
        for (sourceChainIid: Long in sourceChains) {
            // (just in case) If they are already connected, don't connect
            val found = existingPipes.firstOrNull { it.sourceChainIid == sourceChainIid }

            if (found == null) {
                // Not connected, go on to with final check
                if (connChecker.shouldConnect(sourceChainIid, iid, this)) {
                    val newPipe = buildAndAddPipe(sourceChainIid, iid, height)
                    newPipes.add(newPipe)
                }
            }
        }

        return newPipes
    }


    /**
     * We assume this is a source chain, and try to see if it needs to connect to listening chains
     *
     * @return any [IcmfPipe] where the given chain acts "source"
     */
    private fun getPipesForSourceRole(
        iid: Long, // given chain ID
        bcProcess: BlockchainProcess,
        height: Long
    ) : List<IcmfPipe> {
        val newPipes = ArrayList<IcmfPipe>()


        // Validation
        val existingPipes = icmfDispatcher.getAllPipesForSourceChain(iid)
        if (existingPipes != null && !existingPipes.isEmpty()) {
            logger.warn("We are about to start a chain: $iid that already has ${existingPipes.size} active pipes " +
                    "where it is source. Very likely something is wrong.")
        }

        // Fetch all possible listening chains
        val listeningChainMap = pipeConnSync!!.getListeningChainsFromSource(iid)
        for (listenerChainIid: Long in listeningChainMap.keys) {
            // (just in case) If they are already connected, don't connect
            val found = existingPipes.firstOrNull { it.listenerChainIid == listenerChainIid }

            if (found == null) {
                // Not connected, go on to with final check
                if (listeningChainMap[listenerChainIid]!!.shouldConnect(iid, listenerChainIid, this)) {
                    val newPipe = buildAndAddPipe(iid, listenerChainIid, height)
                    newPipes.add(newPipe)
                }
            }
        }
        return newPipes
    }

    /**
     * @return the newly created and added [IcmfPipe]
     */
    private fun buildAndAddPipe(
        sourceChainIid: Long,
        listenerChainIid: Long,
        height: Long
    ): IcmfPipe {
        // 1. Get fetcher
        // --------------------------------
        // Regarding the [ProgrammerMistake]: We should never create a pipe unless both source and listener have
        // started (so the fetcher shouldn't be missing).
        // --------------------------------
        val fetcher: IcmfFetcher = listenerChainToFetcherMap[listenerChainIid] ?: throw ProgrammerMistake(
            "Listener chain: $listenerChainIid must be started and initiated with a fetcher before the pipe " +
                    "can be created (source chain: $sourceChainIid)."
        )

        // 2. Update the receiver and get the pipe in one go
        val newPipe = icmfReceiver.connectPipe(sourceChainIid, listenerChainIid, fetcher)
        // 3. Update the dispatcher
        icmfDispatcher.addMessagePipe(newPipe, height)

        return newPipe
    }


    fun setFetcherForListenerChain(listenerIid: Long, fetcher: IcmfFetcher) {
        val existingFetcher = listenerChainToFetcherMap[listenerIid]
        if (existingFetcher != null) {
            throw ProgrammerMistake("Listener chain $listenerIid already has a fetcher: ${existingFetcher.javaClass}")
        } else {
            listenerChainToFetcherMap[listenerIid] = fetcher
        }
    }

}