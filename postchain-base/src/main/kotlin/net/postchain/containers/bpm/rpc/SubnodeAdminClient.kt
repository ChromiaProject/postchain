package net.postchain.containers.bpm.rpc

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.crypto.PrivKey
import net.postchain.gtv.Gtv

interface SubnodeAdminClient : Shutdownable {
    fun connect()
    fun disconnect()
    fun initializePostchainNode(privKey: PrivKey): Boolean
    fun isSubnodeHealthy(): Boolean
    fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid): Boolean
    fun stopBlockchain(chainId: Long): Boolean
    fun isBlockchainRunning(chainId: Long): Boolean
    fun getBlockchainLastBlockHeight(chainId: Long): Long
    fun initializeBlockchain(chainId: Long, config: ByteArray)
    fun addBlockchainConfiguration(chainId: Long, height: Long, config: ByteArray)
    fun exportBlocks(chainId: Long, fromHeight: Long, blockCountLimit: Int, blocksSizeLimit: Int): List<Gtv>
    fun importBlocks(chainId: Long, blockData: List<Gtv>): Long
}
