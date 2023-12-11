package net.postchain.containers.bpm.rpc

import net.postchain.common.BlockchainRid
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Shutdownable
import net.postchain.crypto.PrivKey
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv

interface SubnodeAdminClient : Shutdownable {

    companion object {
        fun create(containerNodeConfig: ContainerNodeConfig, containerPortMapping: Map<Int, Int>, nodeDiagnosticContext: NodeDiagnosticContext): SubnodeAdminClient {
            return DefaultSubnodeAdminClient(containerNodeConfig, containerPortMapping, nodeDiagnosticContext)
        }
    }

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
    fun exportBlock(chainId: Long, height: Long): Gtv
    fun importBlock(chainId: Long, blockData: Gtv)
}
