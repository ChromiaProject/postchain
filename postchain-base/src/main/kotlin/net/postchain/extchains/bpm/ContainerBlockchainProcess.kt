package net.postchain.extchains.bpm

import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.master.MasterCommunicationManager

interface ExternalBlockchainProcess

class ContainerBlockchainProcess(
        val processName: BlockchainProcessName,
        val communicationManager: MasterCommunicationManager
) : ExternalBlockchainProcess {

}