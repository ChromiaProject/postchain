package net.postchain.containers.bpm.rpc

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Shutdownable
import net.postchain.debug.NodeDiagnosticContext

interface SubnodeAdminClient : Shutdownable {

    companion object {
        fun create(containerNodeConfig: ContainerNodeConfig, containerPortMapping: Map<Int, Int>, nodeDiagnosticContext: NodeDiagnosticContext): SubnodeAdminClient {
            return DefaultSubnodeAdminClient(containerNodeConfig, containerPortMapping, nodeDiagnosticContext)
        }
    }

    fun connect()
    fun disconnect()
    fun isSubnodeHealthy(): Boolean
    fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: ByteArray): Boolean
    fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid, config: ByteArray): Boolean
    fun stopBlockchain(chainId: Long): Boolean
    fun isBlockchainRunning(chainId: Long): Boolean
    fun getBlockchainLastHeight(chainId: Long): Long
    fun addPeerInfo(peerInfo: PeerInfo): Boolean
}
