package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.ProgrammerMistake
import net.postchain.config.blockchain.SimpleConfigReader
import net.postchain.core.BlockchainRid
import java.util.*

/**
 * Goal of [IcmfPipeConnectionSync] is to know what chains are running on this node.
 * This class exists so that a chain that is starting can call "getListeningChainsForSource()"
 * to find out what listeners it potentially should connect to (we say "potentially" since it's the [ConnectionChecker]
 * that really knows if a [IcmfPipe] should exist or not).
 *
 * Note on startup sequence:
 * -------------------------
 * Startup sequence isn't a problem. We usually use the [IcmfFetcher] to pull messages from the receiver side,
 * but if we were to push from the source chain to a pipe, the chains should have been started in the correct
 * order anyway, so the pipe should have living chains on the listener side (ICMF doesn't allow chains to be
 * started in incorrect order).
 *
 * Limitation 1:
 * ------------
 * It's not possible to add new LISTENER chains to [IcmfPipeConnectionSync] after startup, only source chains
 * (using the "addChains()" function)
 *
 * Limitation 2:
 * -----------
 * Currently we don't look at the "type" of message, if a chain is a "listener" that's it. // TODO: Olle: this is a simplification of what Alex described, might not be good enough
 *
 * Note 1
 * ... in the manual configuration we only have BC RID for the listening chain, therefore we use BC RID for listeners,
 * but since we can depend on the listener being started the Chain IID should be available too.
 *
 * Note 2
 * ... that the internal collections of this class often survive BC restarts (since [IcmfPipeConnectionSync] survives),
 * but won't survive a node restart. We deliberately don't delete old chains from the "seen" lists which means the
 * ICMF config settings cannot change without a node restart (the "ICMF configs" are "icmfListener" and "icmfSource").
 */
class IcmfPipeConnectionSync(
    private var simpleConfReader: SimpleConfigReader // Does the ugly part of actually reading BC config (easy to mock)
) {

    companion object: KLogging()

    // All chains we've seen are in here, we want to move back and forth IID <-> RID
    private val internalChainSet = mutableSetOf<Long>()

    // This is only relevant for managed mode
    private val listenerChainToConnCheckerMap = HashMap<Long, ConnectionChecker>() // All listener chains we have and their corresponding [ConnectionChecker]

    /**
     * Useful in test
     */
    fun setSimpleConfReader(scf: SimpleConfigReader) {
        simpleConfReader = scf
    }

    /**
     * @return true if we have read the config for this chain already
     */
    @Synchronized
    fun seenThisChainBefore(chainIid: Long) = internalChainSet.contains(chainIid)

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
     * @return a list of potential source chains, which means everything.
     */
    @Synchronized
    fun getSourceChainsFromListener(listenerChainIid: Long): List<Long> {
        val retList = mutableListOf<Long>()

        if (!seenThisChainBefore(listenerChainIid)) {
            if(logger.isDebugEnabled) {
                logger.debug("First time ICMF seen chain id: $listenerChainIid, let's add it to the cache.")
            }
            internalChainSet.add(listenerChainIid)
        }

        // Loop all started chains use them (weed out already connected chains later)
        for (tmpIid: Long in internalChainSet) {
            if (listenerChainIid != tmpIid) { // No need to add itself
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

        return if (listenerChainToConnCheckerMap.containsKey(sourceChainIid)) {
            // It's a listener chain
            // A listener chain might still want to listen to other listener chains, but not to itself
            listenerChainToConnCheckerMap.filter { (key, value) ->
                key != sourceChainIid  // Remove itself from list
            }.toMap() // Return an immutable version
        } else {
            // This being never seen isn't an error really, it just means this chain didn't exist during node startup
            logger.warn("We didn't notice chain $sourceChainIid during startup. Was it added later?")

            listenerChainToConnCheckerMap.toMap() // Return an immutable version
        }

    }

    /**
     * Safe to use this function to add chains we already know about, previous seen chains will just be ignored.
     */
    @Synchronized
    fun addChains(allNew: Set<BlockchainRelatedInfo>)  {
        val newChains = allNew.filter { it.chainId !in internalChainSet }
        newChains.forEach { addChain(it) }
    }

    /**
     * Find ICMF config parameters from the chain, and add the info to the internal cache/map.
     *
     * NOTE:
     * We DON'T want to create a configuration domain object here (b/c we might instantiate too many objects).
     * Just get the config raw and extract one value we care about.
     *
     * @param chainInfo is the chain we should add (to the list of started chains).
     */
    @Synchronized
    fun addChain(chainInfo: BlockchainRelatedInfo) {
        val chainIid = chainInfo.chainId!! // This must exist

        if (internalChainSet.contains(chainIid)) {
            throw ProgrammerMistake("Don't add a chain if we've seen it already, chainId: $chainIid")
        }

        // Get the listener ICMF setting (which won't exist for sources/normal chains)
        val icmfListenerConf: String? = simpleConfReader.getSetting(chainIid, BaseBlockchainConfigurationData.KEY_ICMF_LISTENER)
        if (icmfListenerConf != null) {
            // A listener chain must be added it to the listener list
            if (listenerChainToConnCheckerMap.containsKey(chainInfo.chainId!!)) {
                throw ProgrammerMistake("We shouldn't reset an existing listener chain id: ${chainInfo.chainId} , RID: $chainInfo.")
            } else {
                val connChecker = ConnectionCheckerFactory.build(chainInfo.chainId!!, icmfListenerConf!!)
                listenerChainToConnCheckerMap[chainInfo.chainId!!] = connChecker
            }
        }
    }

}

