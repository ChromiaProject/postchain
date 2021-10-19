package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.UserMistake
import net.postchain.core.ProgrammerMistake
import net.postchain.config.blockchain.SimpleConfigReader
import net.postchain.core.BlockchainRid
import java.util.*

/**
 * Goal of [IcmfPipeConnectionSync] is to know things about the chains ABOUT TO START on this node, BEFORE the first
 * chain has been started. This is important so we don't lose any messages from a potentially early started source chain.
 * (if a source chain starts running and generates messages before the listener chain has been started, we must store
 * these messages in [IcmfPipe]s until the listener is ready to read).
 *
 * It's possible to add new chains to [IcmfPipeConnectionSync] at any time, using the "addChains()" function, but, if
 * a new listener chain is added late, it will have missed all ICMF messages before "its birth-time" (obvious, or?).
 * NOTE: Currently late added chains won't work since these connections are not visible in the IcmfReceiver/Dispatcher // TODO: Olle: fix
 *
 * Discussion:
 * It's likely that source chains will start anytime, but I don't think a "listener chains" will ever be introduced
 * on a running node, but who knows?
 *
 * Limitations:
 * Currently we don't look at the "type" of message, if a chain is a "listener" that's it. // TODO: Olle: this is a simplification of what Alex described, might not be good enough
 *
 * Note 1
 * ... for manual configuration we only have BC RID for the listening chain, therefore we must use BC RID for listeners.
 *
 * Note 2
 * ... that the internal collections of this class often survive BC restarts (since [IcmfPipeConnectionSync] survives),
 * but won't survive a node restart. We deliberately don't delete old chains from the "seen" lists which means
 * the ICMF config settings cannot change without a node restart (but they don't right?).
 */
class IcmfPipeConnectionSync(
    private var simpleConfReader: SimpleConfigReader // Does the ugly part of actually reading BC config (easy to mock)
) {

    companion object: KLogging()

    // All chains we've seen are in here, with empty list meaning no manual configuration
    private val chainToManualListenerConfMap = HashMap<Long, List<BlockchainRid>>()

    // Sometimes we have seen the chain ID of a listener, sometimes not. Check here to find out if we know it
    private val internalChainRidToChainIidCache = HashMap<BlockchainRid, Long>()  // TODO: Olle: don't know how this works with forking

    // This is only relevant for managed mode
    private var chainsInitiated = false // false = we never added the initial set of chains

    // This is only relevant for managed mode
    private val listenerChainToConnCheckerMap = HashMap<BlockchainRid, ConnectionChecker>() // All listener chains we have and their corresponding [ConnectionChecker]

    /**
     * Useful in test
     */
    fun setSimpleConfReader(scf: SimpleConfigReader) {
        simpleConfReader = scf
    }

    private lateinit var icmfReceiver: IcmfReceiver
    private lateinit var icmfDispatcher: IcmfDispatcher

    fun setReceiver(r: IcmfReceiver) {
        icmfReceiver = r
    }
    fun setDispatcher(d: IcmfDispatcher) {
        icmfDispatcher = d
    }

    /**
     * @return true if we have read the config for this chain already
     */
    @Synchronized
    fun seenThisChainBefore(chainIid: Long) = chainToManualListenerConfMap.containsKey(chainIid)

    /**
     * @return true if we have the initial set of chains set.
     */
    @Synchronized
    fun areChainsSet(): Boolean = chainsInitiated

    /**
     * Use this method to check if the chain is a listener (or to determine what order it should be started in)
     *
     * @param bcRid is the chain we need the connection checker from
     * @return a [ConnectionChecker] if this is a listener chain, or null if the chain isn't a listener
     */
    @Synchronized
    fun getListenerConnChecker(chainInfo: BlockchainRelatedInfo): ConnectionChecker? {
        if (!internalChainRidToChainIidCache.containsKey(chainInfo.blockchainRid)) {
            if (logger.isDebugEnabled) {
                logger.debug("isListener() - The chain $chainInfo has never been seen. Checking if it's a listener")
            }

            val isListener: Boolean = false
            if (isListener) {
                logger.warn("isListener() - Potentially dangerous to start a listener late in the game, we might have missed messages")
            }
        }

        return listenerChainToConnCheckerMap[chainInfo.blockchainRid]
    }

    /**
     * Figures out what listener chains this source should connect to.
     * Manual override will trumph any setting on the listener side.
     *
     * @param sourceChainInfo is the chain we want to find listener chains for.
     * @return a map of potential listener chains and their corresponding [ConnectionChecker]s.
     */
    @Synchronized
    fun getListeningChainsForSource(sourceChainInfo: BlockchainRelatedInfo): Map<BlockchainRid, ConnectionChecker> {

        if (sourceChainInfo.chainId == null) {
            ProgrammerMistake("We must know chainIid for the source chain at this point")
        } else {
            // Best place to update internal chainIid cache
            internalChainRidToChainIidCache[sourceChainInfo.blockchainRid] = sourceChainInfo.chainId!!
        }

        val manualConf: List<BlockchainRid>? = this.chainToManualListenerConfMap[sourceChainInfo.chainId]
        if (manualConf != null && manualConf.isNotEmpty()) {
            //1. We have a manual conf, use it and ignore everything else
            val retMap = HashMap<BlockchainRid, ConnectionChecker>()
            for (bcRid in manualConf) {
                // Setting manual setting to "high" (= 10) means other listeners with "high" level will ignore it.
                retMap[bcRid] = LevelConnectionChecker(bcRid, LevelConnectionChecker.HIGH_LEVEL)
            }
            return retMap
        } else {
            //2. No manual conf, do we have chains?
            if (this.areChainsSet()) {
                //2.a: we should know about a bunch of chains by now
                if (listenerChainToConnCheckerMap.containsKey(sourceChainInfo.blockchainRid)) {
                    // It's a listener chain
                    // A listener chain might still want to listen to other listener chains, but not to itself
                    return listenerChainToConnCheckerMap.filter { (key, value) ->
                        sourceChainInfo.blockchainRid != key  // Remove itself from list
                    }.toMap() // Return an immutable version
                } else {
                    // This being never seen isn't an error really, it just means this chain didn't exist during node startup
                    logger.warn("We didn't notice chain ${sourceChainInfo.chainId} during startup. Was it added later?")
                }

                return listenerChainToConnCheckerMap.toMap() // Return an immutable version
            } else {
                //2.b: we don't have any configuration at all to go on, this is rare in prod, since we usually have the anchor chain
                return mapOf()
            }
        }
    }

    /**
     * @return a [BlockchainRelatedInfo] object with as much info as we currently have, possibly without chainIid
     */
    fun getBlockchainInfo(listenerChainRid: BlockchainRid): BlockchainRelatedInfo {
        val chainIid = internalChainRidToChainIidCache[listenerChainRid] // If this is manual conf we might not have the chainIid
        return BlockchainRelatedInfo(listenerChainRid, null, chainIid)
    }

    /**
     * Safe to use this function to add chains we already know about, previous seen chains will just be ignored.
     */
    @Synchronized
    fun addChains(allNew: Set<BlockchainRelatedInfo>)  {
        val knownChainIids = chainToManualListenerConfMap.keys
        val newChains = allNew.filter { it.chainId !in knownChainIids }
        newChains.forEach { addSingleChain(it) }
        chainsInitiated = true
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
    private fun addSingleChain(chainInfo: BlockchainRelatedInfo) {
        val chainIid = chainInfo.chainId!! // This must exist
        this.internalChainRidToChainIidCache[chainInfo.blockchainRid] = chainIid // Update cache immediately

        if (chainToManualListenerConfMap.containsKey(chainIid)) {
            throw ProgrammerMistake("Don't add a chain if we've seen it already, chainId: $chainIid")
        }

        val icmfBcRids: List<BlockchainRid> =
            simpleConfReader.getBcRidArray(chainIid, BaseBlockchainConfigurationData.KEY_ICMF_SOURCE)
        chainToManualListenerConfMap[chainIid] = icmfBcRids // This list will be empty unless we have a manual override

        val icmfListener: String? = simpleConfReader.getSetting(chainIid, BaseBlockchainConfigurationData.KEY_ICMF_LISTENER)

        interpretIcmfListenerConfig(chainInfo, icmfListener)
    }

    /**
     * Updates the internal cache/map if this is a listener chains with correct configuration.
     *
     * Currently we can only handle the "number" strategy for "icmflistener" setting but:
     * FUTURE WORK: Olle: The plan is to try to instantiate the given class and use it as a [ConnectionChecker]
     *
     * @param chainInfo is the identifiers of chain we are about to add
     * @param icmfListenerConf is the target setting from the chain's config file.
     */
    private fun interpretIcmfListenerConfig(chainInfo: BlockchainRelatedInfo, icmfListenerConf: String?) {
        if (icmfListenerConf != null) {
            // This is a listener chain, let's add it to the listener list
            if (listenerChainToConnCheckerMap.containsKey(chainInfo.blockchainRid)) {
                throw ProgrammerMistake("We shouldn't reset an existing listener chain id: ${chainInfo.chainId} , RID: $chainInfo.")
            } else {
                val listeningLevel: Int? = icmfListenerConf!!.toIntOrNull()
                if (listeningLevel != null) {
                    // Simple case, we use the level given
                    listenerChainToConnCheckerMap[chainInfo.blockchainRid] = LevelConnectionChecker(chainInfo.blockchainRid, listeningLevel!!)
                } else {
                    // FUTURE WORK: Olle: Implement
                    //val customConnectionChecker = xxx as ConnectionChecker
                    //internalListenerChains[chainIid] = customConnectionChecker
                    throw UserMistake("At this point we cannot handle custom listener chain connection checkers, icmflistener must be set to \"number\" but was \"$icmfListenerConf\".")
                }
            }
        }
    }

}

