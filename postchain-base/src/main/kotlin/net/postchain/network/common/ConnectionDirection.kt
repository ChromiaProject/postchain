package net.postchain.network.common

/**
 * OUTGOING = this node created the connection
 * INCOMING = the connection was initiated by external node
 */
enum class ConnectionDirection {
    INCOMING, OUTGOING
}
