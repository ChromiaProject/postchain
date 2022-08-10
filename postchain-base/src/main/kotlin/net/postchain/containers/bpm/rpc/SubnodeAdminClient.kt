package net.postchain.containers.bpm.rpc

import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerPorts
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.core.Shutdownable

interface SubnodeAdminClient : Shutdownable {

    companion object {
        fun create(containerNodeConfig: ContainerNodeConfig, containerPorts: ContainerPorts): SubnodeAdminClient {
            return DefaultSubnodeAdminClient(containerNodeConfig, containerPorts)
        }
    }

    fun connect()
    fun isSubnodeConnected(): Boolean
    fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid, config: ByteArray): Boolean
    fun stopBlockchain(chainId: Long): Boolean
    fun isBlockchainRunning(chainId: Long): Boolean
}