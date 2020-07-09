package net.postchain.network.masterslave

import net.postchain.network.masterslave.protocol.MsMessage

typealias PacketHandler = (message: MsMessage) -> Unit
