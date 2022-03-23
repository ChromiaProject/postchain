package net.postchain.network.mastersub.master

import net.postchain.core.Shutdownable
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.mastersub.protocol.MsMessage

/**
 * [MasterConnectionManager] enables us work with a sub node.
 */
interface MasterConnectionManager : Shutdownable {

    /**
     * Connects subnode chain. Usually is called by [MasterCommunicationManager].
     *
     * @param processName a name of blockchain process which connects subnode chain. Used for logging.
     * @param subChainConfig a config of subnode chain.
     */
    fun connectSubChain(processName: BlockchainProcessName, subChainConfig: SubChainConfig)

    /**
     * Sends a message to subnode chain
     *
     * @param message a message to send
     */
    fun sendPacketToSub(message: MsMessage)

    /**
     * Disconnects subnode chain. Usually is called by [MasterCommunicationManager].
     *
     * @param processName a name of blockchain process which connects subnode chain. Used for logging.
     * @param chainId a chainId of subnode chain.
     */
    fun disconnectSubChain(processName: BlockchainProcessName, chainId: Long)
}