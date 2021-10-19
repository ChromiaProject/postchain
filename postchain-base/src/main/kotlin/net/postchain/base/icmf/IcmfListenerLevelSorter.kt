package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo

class IcmfListenerLevelSorter(chain0: BlockchainRelatedInfo) {

    val chainMap = mutableMapOf<Int, MutableSet<BlockchainRelatedInfo>>() // Listener level to chain

    init {
        chainMap[Int.MAX_VALUE] = mutableSetOf(chain0) // We give chain0 the best start number possible
    }

    fun add(level: Int, chainInfo: BlockchainRelatedInfo) {
        val x = chainMap[level]
        if (x == null) {
            chainMap[level] = mutableSetOf(chainInfo)
        } else {
            x.add(chainInfo)
        }

    }

    fun size(): Int {
        var count = 0
        for (key in chainMap.keys) {
            count += chainMap[key]!!.size
        }
        return count
    }

    /**
     * Note: The ordering within a set (of a level) doesn't matter, since chains don't see each other inside the level
     *
     * @return an ordered list of the chains, with the highest listener levels first and the source chains last
     */
    fun getSorted(): Array<BlockchainRelatedInfo> {
        val retList = mutableListOf<BlockchainRelatedInfo>()
        for (key in chainMap.keys.reversed()) {
            retList.addAll(chainMap[key]!!)
        }
        return retList.toTypedArray()
    }
}