package net.postchain.containers.bpm

import net.postchain.containers.infra.ContainerNodeConfig
import kotlin.properties.Delegates

/**
 * Describes port mappings (host -> container) for REST API port and Admin Rpc Interface port
 */
class ContainerPorts(config: ContainerNodeConfig) {

    val restApiPort = config.subnodeRestApiPort
    var hostRestApiPort by Delegates.notNull<Int>()

    val adminRpcPort = config.subnodeAdminRpcPort
    var hostAdminRpcPort by Delegates.notNull<Int>()

    fun getPorts() = restApiPort to adminRpcPort

    fun setHostPorts(hostPorts: Pair<Int, Int>) {
        hostRestApiPort = hostPorts.first
        hostAdminRpcPort = hostPorts.second
    }
}