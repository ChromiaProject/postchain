package net.postchain.network.masterslave.master

import net.postchain.debug.BlockchainProcessName
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.XConnectionManager

/**
 * [MasterConnectionManager] extends [XConnectionManager] interface and
 * adds functionality to work with slave chains
 */
interface MasterConnectionManager : XConnectionManager {

    /**
     * Connects slave chain. Usually is called by [MasterCommunicationManager].
     *
     * @param processName a name of blockchain process which connects slave chain. Used for logging.
     * @param slaveChainConfig a config of slave chain.
     */
    fun connectSlaveChain(processName: BlockchainProcessName, slaveChainConfig: SlaveChainConfig)

    /**
     * Sends a message to slave chain
     *
     * @param message a message to send
     */
    fun sendPacketToSlave(message: MsMessage)

    /**
     * Disconnects slave chain. Usually is called by [MasterCommunicationManager].
     *
     * @param processName a name of blockchain process which connects slave chain. Used for logging.
     * @param chainId a chainId of slave chain.
     */
    fun disconnectSlaveChain(processName: BlockchainProcessName, chainId: Long)
}