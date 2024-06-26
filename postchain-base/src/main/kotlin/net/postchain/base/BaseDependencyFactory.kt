package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.core.BadConfigurationException
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvString

object BaseDependencyFactory {

    /**
     * Turns the BC dependency configuration into a number of [BlockchainRelatedInfo] instances.
     */
    fun build(dep: Gtv): List<BlockchainRelatedInfo> {
        return try {
            // Should contain an array of String, ByteArr pairs
            val gtvDepArray = dep as GtvArray
            val depList = mutableListOf<BlockchainRelatedInfo>()
            for (element in gtvDepArray.array) {
                val elemArr = element as GtvArray
                val nickname = elemArr[0] as GtvString
                val blockchainRid = elemArr[1] as GtvByteArray
                depList.add(
                        BlockchainRelatedInfo(BlockchainRid(blockchainRid.bytearray), nickname.string, null)
                )
            }
            depList.toList()
        } catch (e: Exception) {
            throw BadConfigurationException("Dependencies must be array of array and have two parts, one string (description) and one bytea (blokchain RID)", e)
        }
    }

    /**
     * Turns a list of [BlockchainRelatedInfo] into dependency [GtvArray]
     */
    fun buildGtv(deps: List<BlockchainRelatedInfo>): Gtv? {
        return if (deps.isNotEmpty()) {
            val res = mutableListOf<Gtv>()
            var i = 1
            for (dep in deps) {
                val gtvNickname = GtvFactory.gtv(dep.nickname ?: "chain_$i")
                val gtvBcRid = GtvFactory.gtv(dep.blockchainRid.data)
                val gtvElement: GtvArray = GtvFactory.gtv(listOf<Gtv>(gtvNickname, gtvBcRid))
                res.add(gtvElement)
                i++
            }
            return GtvFactory.gtv(res.toList())
        } else {
            null
        }
    }
}