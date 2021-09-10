package net.postchain.config.blockchain

import net.postchain.base.Storage
import net.postchain.base.withReadConnection
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import sun.jvm.hotspot.opto.Block

/**
 * Standard implementation of the [SimpleConfigReader].
 */
class DirtSimpleConfigReader (
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
        var ret = arrayListOf<BlockchainRid>()

        var gtvDict: GtvDictionary? = cache[chainIid]
        if (gtvDict == null) {
            gtvDict = populateCache(chainIid)!!
        }

        val settings = gtvDict[confKey]
        if (settings != null) {
            if (settings.type == GtvType.ARRAY) {
                var i = 0
                for (gtv in settings.asArray()) {
                    if (gtv.type == GtvType.BYTEARRAY) {
                        val bcRidGtv = gtv.asByteArray()
                        if (bcRidGtv != null) {
                            ret.add(BlockchainRid(bcRidGtv))
                        }
                    } else {
                        throw UserMistake("Configuration error: $confKey 's array member pos $i should be Blockchain RIDs (hex), but was ${gtv.type}.")
                    }
                    i++
                }
            } else {
                throw UserMistake("Configuration error: $confKey was expected to hold an array.")
            }
        }

        return ret
    }

    private fun populateCache(chainIid: Long): GtvDictionary? {
        var gtvData: GtvDictionary? = null
        withReadConnection(storage, chainIid) { eContext ->
            val rawConf = bcConfigProvider.getConfiguration(eContext, chainIid)
            if (rawConf == null) {
                throw ProgrammerMistake("Didn't find the configuration for chain id $chainIid") // Not sure if there is valid reason for this to ever happen?
            } else {
                gtvData = GtvFactory.decodeGtv(rawConf) as GtvDictionary
            }
        }
        return gtvData
    }
}
