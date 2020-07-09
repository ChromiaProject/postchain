package net.postchain.network.masterslave.master

import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.x.XConnectionManager

interface MasterConnectionManager : XConnectionManager {
    fun connectSlaveChain(slaveChainConfig: SlaveChainConfiguration, loggingPrefix: () -> String)
    fun sendPacketToSlave(message: MsMessage)
    fun disconnectSlaveChain(chainId: Long, loggingPrefix: () -> String)
}