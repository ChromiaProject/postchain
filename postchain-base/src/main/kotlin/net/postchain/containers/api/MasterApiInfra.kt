package net.postchain.containers.api

import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.core.ApiInfrastructure

interface MasterApiInfra : ApiInfrastructure {
    fun connectContainerProcess(process: ContainerBlockchainProcess)
    fun disconnectContainerProcess(process: ContainerBlockchainProcess)
}