package net.postchain.config.blockchain

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType

object ConfigReaderHelper {

    fun readAsBlockchainRid(setting: Gtv?, chainIid: Long, confKey: String): List<BlockchainRid> {
        val ret = arrayListOf<BlockchainRid>()
        if (setting != null) {
            if (setting.type == GtvType.ARRAY) {
                for ((i, gtv) in setting.asArray().withIndex()) {
                    if (gtv.type == GtvType.BYTEARRAY) {
                        val bcRidGtv = gtv.asByteArray()
                        ret.add(BlockchainRid(bcRidGtv))
                    } else {
                        throw UserMistake("Configuration error: $confKey 's array member pos $i should be Blockchain RIDs (hex), but was ${gtv.type}, chain: $chainIid.")
                    }
                }
            } else {
                throw UserMistake("Configuration error: $confKey was expected to hold an array, chain: $chainIid.")
            }
        }
        return ret
    }
}