package net.postchain.base.icmf

import mu.KLogging
import java.util.*

/**
 * Goal of [IcmfPipeConnectionSync] is to know what chains are running on this node, and in particular what
 * listeners use what [ConnectionChecker] (Technically this class is just a wrapper around two caches).
 *
 * Note on startup sequence:
 * -------------------------
 * Startup sequence isn't a problem. We usually use the [IcmfFetcher] to pull messages from the receiver side,
 * but if we were to push from the source chain to a pipe, the chains should have been started in the correct
 * order anyway, so the pipe should have living chains on the listener side (ICMF doesn't allow chains to be
 * started in incorrect order).
 *
 * Limitation 1:
 * -----------
 * Currently we don't look at the "type" of message, if a chain is a "listener" that's it. // TODO: Olle: this is a simplification of what Alex described, might not be good enough
 *
 * Note:
 * ... that the internal collections of this class often survive BC restarts (since [IcmfPipeConnectionSync] survives),
 * but won't survive a node restart. When a chain is being shut down we must remember to call "chainShuttingDown()"
 * on this class too.
 */
class IcmfPipeConnectionSync() {

    companion object: KLogging()

    private val internalChainSet = mutableSetOf<Long>() // All chains we've seen are in here
    private val listenerChainToConnCheckerMap = HashMap<Long, ConnectionChecker>() // All listener chains we have and their corresponding [ConnectionChecker]

    /**
     * @return true if we have read the config for this chain already
     */
    @Synchronized
    fun seenThisChainBefore(chainIid: Long) = internalChainSet.contains(chainIid)


    private fun seenThisBeforeOrAdd(chainIid: Long) {
        if (!seenThisChainBefore(chainIid)) {
            if (logger.isDebugEnabled) {
                logger.debug("First time ICMF seen chain id: $chainIid, let's add it to the cache.")
            }
            internalChainSet.add(chainIid)
        }
    }

    /**
     * @param chainIid remove this chain from our caches
     */
    @Synchronized
    fun chainShuttingDown(chainIid: Long) {
        listenerChainToConnCheckerMap.remove(chainIid)
        internalChainSet.remove(chainIid)
    }

    /**
     * Use this method to check if the chain is a listener (or to determine what order it should be started in)
     *
     * @param bcIid is the chain we need the connection checker from
     * @return a [ConnectionChecker] if this is a listener chain, or null if the chain isn't a listener
     */
    @Synchronized
    fun getListenerConnChecker(bcIid: Long): ConnectionChecker? {
        return listenerChainToConnCheckerMap[bcIid]
    }

    /**
     * When a new listener chain is started, this should be called.
     *
     * @param listenerChainIid is the chain iid of the chain we want to add.
     * @param connCheck is the [ConnectionChecker] that is associated with the chain
     */
    @Synchronized
    fun addListenerChain(listenerChainIid: Long, connCheck: ConnectionChecker) {
        seenThisBeforeOrAdd(listenerChainIid)

        if (listenerChainToConnCheckerMap.containsKey(listenerChainIid)) {
            logger.warn("Trying to owerwrite ConnectionChecker for listener chain: $listenerChainIid. Why?")
        } else {
            listenerChainToConnCheckerMap[listenerChainIid] = connCheck
        }
    }

    /**
     * @param listenerChainIid is the listener we are doing a lookup for (not really used for anything)
     * @return a list of potential source chains, which means everything. At this point we don't know what's
     *         already connected.
     */
    @Synchronized
    fun getSourceChainsFromListener(listenerChainIid: Long): List<Long> {
        seenThisBeforeOrAdd(listenerChainIid)

        val retList = mutableListOf<Long>()

        // Loop all started chains use them (weed out already connected chains later)
        for (tmpIid: Long in internalChainSet) {
            if (listenerChainIid != tmpIid) { // No need to add itself (shouldn't happen but yeah)
                retList.add(tmpIid)
            }
        }

        return retList
    }


    /**
     * Figures out what listener chains this source could (perhaps) connect to.
     *
     * @param sourceChainInfo is the chain we want to find listener chains for. It doesn't HAVE to be a source chain,
     *                        but we don't know what it is at this point, so we assume source.
     * @return a map of potential listener chains and their corresponding [ConnectionChecker]s.
     */
    @Synchronized
    fun getListeningChainsFromSource(sourceChainIid: Long): Map<Long, ConnectionChecker> {
        seenThisBeforeOrAdd(sourceChainIid)

        return if (listenerChainToConnCheckerMap.containsKey(sourceChainIid)) {
            // It's a listener chain
            // A listener chain might still want to listen to other listener chains, but not to itself
            listenerChainToConnCheckerMap.filter { (key, value) ->
                key != sourceChainIid  // Remove itself from list
            }.toMap() // Return an immutable version
        } else {
            listenerChainToConnCheckerMap.toMap() // Return all listeners as an immutable version
        }

    }

}

