package net.postchain.config.blockchain

import net.postchain.common.BlockchainRid
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType

object ConfigReaderHelper {


    fun readAsBlockchainRid(setting: Gtv?, chainIid: Long, confKey: String): List<BlockchainRid> {
        var ret = arrayListOf<BlockchainRid>()
        if (setting != null) {
            if (setting.type == GtvType.ARRAY) {
                var i = 0
                for (gtv in setting.asArray()) {
                    if (gtv.type == GtvType.BYTEARRAY) {
                        val bcRidGtv = gtv.asByteArray()
                        if (bcRidGtv != null) {
                            ret.add(BlockchainRid(bcRidGtv))
                        }
                    } else {
                        throw UserMistake("Configuration error: $confKey 's array member pos $i should be Blockchain RIDs (hex), but was ${gtv.type}, chain: $chainIid.")
                    }
                    i++
                }
            } else {
                throw UserMistake("Configuration error: $confKey was expected to hold an array, chain: $chainIid.")
            }
        }
        return ret
    }
}