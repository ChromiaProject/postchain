package net.postchain.network.common

import net.postchain.core.BlockchainRid

/**
 * General info about the connection
 * The only thing we always know is what blockchain it is about.
 */
open class ConnectionDescriptor(val blockchainRid: BlockchainRid)


