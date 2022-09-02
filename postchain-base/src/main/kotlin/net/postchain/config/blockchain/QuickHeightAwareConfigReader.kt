package net.postchain.config.blockchain

import net.postchain.base.Storage
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory


/**
 * Standard implementation of the [HeightAwareConfigReader] which enables quick lookups from a configuration,
 * using an internal cache for speed.
 *
 * Here we use a primitive [GtvDictionary] to hold the entire configuration. Too expensive to interpret what we've
 * got in it unless actually needed.
 *
 * ------
 * Note!!
 * ------
 * We will check for new versions of the configuration for every new height we are given, and this is a bit expensive,
 * so if you KNOW that the value you are looking for won't change over time you might want to consider the
 * [SimpleConfigReader] instead (for better performance).
 *
 *
 * Why not use [BlockchainConfiguration]:
 * Sometimes it's too much work to create a real [BlockchainConfiguration] instance (or we are worried
 * we might create instances that will cause a conflict somewhere) for those cases the [HeightAwareConfigReader] is
 * useful, b/c we can lookup whatever setting we need with a minimal effort.
 */
class QuickHeightAwareConfigReader (
    private val storage: Storage,
    private val bcConfigProvider: BlockchainConfigurationProvider
) : HeightAwareConfigReader {

    /**
     * Just a simple data holder, to remember the last lookup result
     */
    class CachedHeight(
        var lastLookupHeight: Long,
        var lastFoundHeight: Long,
        var lastFonudDict: GtvDictionary
    ) {

        /**
         * @return true if we should check if we have an new config at this height
         */
        fun needToDoNewLookup(currentHeight: Long): Boolean {
            if (currentHeight < lastLookupHeight) {
                throw ProgrammerMistake("This cache cannot handle backwards looking logic, " +
                        "last lookup was for height $lastLookupHeight but now you are looking up $currentHeight")
            }
            return currentHeight > lastLookupHeight
        }

        /**
         * @return true if the found config is the same as we have in the cache
         */
        fun isThisSameConfigAsLastTime(foundConfigHeight: Long, currentHeight: Long): Boolean {
            this.lastLookupHeight = currentHeight
            return foundConfigHeight == lastFoundHeight
        }

        /**
         * When a new config has been found we update the cache
         */
        fun update(foundHeight: Long, foundDict: GtvDictionary) {
            this.lastFoundHeight = foundHeight
            this.lastFonudDict = foundDict
        }
    }

    // Warning Warning
    // Caching is always dangerous, in this one we assume height always move forward for all chains
    // If we start looking for old heights this cache will become unpredictable, but will hopefully throw exception
    private val cache = HashMap<Long, CachedHeight>()  // ChainIid -> CacheHeight

    /**
     * @param chainIid is the BC we are interested in.
     * @param blockHeight is the block height we are at right now (to find the correct version of the config)
     * @return the string representation of the configuration value, if found.
     */
    override fun getSetting(chainIid: Long, blockHeight: Long, confKey: String): String? {
        val gtvDict = getTheDict(chainIid, blockHeight)
        return gtvDict[confKey]?.asString()
    }

    /**
     * @param chainIid is the BC we are interested in.
     * @param currentHeight is the block height we are at right now (to find the correct version of the config)
     * @return a list of [BlockchainRid] from the configuration values, or empty list
     */
    override fun getBcRidArray(chainIid: Long, blockHeight: Long, confKey: String): List<BlockchainRid> {
        var gtvDict: GtvDictionary = getTheDict(chainIid, blockHeight)

        val settings = gtvDict[confKey]
        return ConfigReaderHelper.readAsBlockchainRid(settings, chainIid, confKey)
    }

    /**
     * This function handles a lot of the actual caching
     */
    private fun getTheDict(chainIid: Long, currentHeight: Long): GtvDictionary {
        var gtvDict: GtvDictionary? = null
        var cached = cache[chainIid]
        var foundConfigHeight: Long? = null
        if (cached != null) {
            if (cached.needToDoNewLookup(currentHeight)) {
                // We must do a lookup
                withReadConnection(storage, chainIid) { eContext ->
                    foundConfigHeight = bcConfigProvider.getHistoricConfigurationHeight(eContext, chainIid, currentHeight)
                    if (foundConfigHeight == null) {
                        //This is some sort of error i think
                    } else if (cached!!.isThisSameConfigAsLastTime(foundConfigHeight!!, currentHeight)) {
                        // Nothing new, let's use the cached one
                        gtvDict = cached!!.lastFonudDict
                    } else {
                        // We've got a more recent hit, let's update directly so we don't forget
                        cached!!.lastFoundHeight = foundConfigHeight!!
                        // Must get new dict, so don't set it
                    }
                }
            } else {
                gtvDict = cached.lastFonudDict
            }
        }

        if (gtvDict == null) {
            // Apparently we failed to get anything from the cache, oh well, let's dig it up then
            gtvDict = populateCache(chainIid, currentHeight)!! // We simlpy MUST have a config for the chain.
            if (cached == null) {
                cached = CachedHeight(currentHeight, currentHeight, gtvDict!!) // We can use current height as found height, doesn't matter
                cache[chainIid] = cached
            }
            cached.lastFonudDict = gtvDict!!
        }

        return gtvDict!!
    }

    private fun populateCache(chainIid: Long, blockHeight: Long): GtvDictionary? {
        var gtvData: GtvDictionary? = null
        withReadConnection(storage, chainIid) { eContext ->
            val rawConf = bcConfigProvider.getHistoricConfiguration(eContext, chainIid, blockHeight)
            if (rawConf == null) {
                throw ProgrammerMistake("Didn't find the configuration for chain id $chainIid, height: $blockHeight")
            } else {
                gtvData = GtvFactory.decodeGtv(rawConf) as GtvDictionary
            }
        }
        return gtvData
    }
}