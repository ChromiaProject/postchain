package net.postchain.containers.bpm.rpc

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
    fun startBlockchain(chainId: Long, config0: ByteArray): Boolean
    fun stopBlockchain(chainId: Long): Boolean
    fun isBlockchainRunning(chainId: Long): Boolean
}
