package net.postchain.config.blockchain

import net.postchain.base.Storage
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.gtv.*

/**
 * Standard implementation of the [SimpleConfigReader] which enables you to do fast lookups from a configuration,
 * using an internal cache for speed.
 *
 * Here we use a primitive [GtvDictionary] to hold the entire configuration. Too expensive to interpret what we've got
 * in it unless actually needed.
 *
 * ------
 * Note!!
 * ------
 * We are using a cache to map the chain Iid to the [GtvDictionary], but we see the config as static, and we DON'T
 * automatically update the cache at different heights! Very easy you shoot yourself in the foot with this tool,
 * b/c if you try to read a value that might change when the configuration change at a specific block height
 * (for example the signer list) you'll get the old value from the cache.
 * If a configuration value might change over time, you should use [HeightAwareConfigReader] instead.
 *
 *
 * Why not use [BlockchainConfiguration]:
 * Sometimes it's too much work to create a real [BlockchainConfiguration] instance, or we are simply worried
 * we might create instances that will cause a conflict somewhere, for those cases the [QuickSimpleConfigReader] is
 * useful, b/c we can lookup whatever setting we need with a minimal effort.
 */
class QuickSimpleConfigReader (
    private val storage: Storage,
    private val bcConfigProvider: BlockchainConfigurationProvider
) : SimpleConfigReader {

    private val cache = HashMap<Long, GtvDictionary>() // Warning: we are caching the config, so don't use this tool to read values that might mutate during runtime

    /**
     * @param chainIid is the BC we are interested in.
     * @return the string representation of the configuration value, if found.
     */
    override fun getSetting(chainIid: Long, confKey: String): String? {
        var gtvDict: GtvDictionary? = cache[chainIid]
        if (gtvDict == null) {
            gtvDict = populateCache(chainIid)!!
        }
        return gtvDict[confKey]?.asString()
    }

    override fun getBcRidArray(chainIid: Long, confKey: String): List<BlockchainRid> {

        var gtvDict: GtvDictionary? = cache[chainIid]
        if (gtvDict == null) {
            gtvDict = populateCache(chainIid)!!
        }

        val setting = gtvDict[confKey]
        return ConfigReaderHelper.readAsBlockchainRid(setting, chainIid, confKey)
    }

    private fun populateCache(chainIid: Long): GtvDictionary? {
        var gtvData: GtvDictionary? = null
        withReadConnection(storage, chainIid) { eContext ->
            val rawConf = bcConfigProvider.getActiveBlocksConfiguration(eContext, chainIid)
            if (rawConf == null) {
                throw ProgrammerMistake("Didn't find the configuration for chain id $chainIid") // Not sure if there is valid reason for this to ever happen?
            } else {
                gtvData = GtvFactory.decodeGtv(rawConf) as GtvDictionary
            }
        }
        return gtvData
    }
}
