package net.postchain.extchains.api

import net.postchain.core.ApiInfrastructure
import net.postchain.extchains.bpm.ExternalBlockchainProcess

interface ExtApiInfrastructure : ApiInfrastructure {
    fun connectExtProcess(process: ExternalBlockchainProcess)
    fun disconnectExtProcess(process: ExternalBlockchainProcess)
}