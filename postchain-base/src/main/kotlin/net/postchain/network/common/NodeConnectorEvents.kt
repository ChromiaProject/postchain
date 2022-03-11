package net.postchain.network.common

/**
 * Handles callbacks after the beginning and end of a connection
 *
 * Note: Currently this is only used by [NettyPeerConnector] but the process is rather generic, so could potentially
 *       be used for "master" and "subnode" too.
 */
interface NodeConnectorEvents<HandlerType, DescriptorType> {

    /**
     * Fires AFTER a node has been connected
     *
     * @return a handler that will be used to receive packets from this node
     */
    fun onNodeConnected(connection: NodeConnection<HandlerType, DescriptorType>): HandlerType?

    /**
     * Fires AFTER a disconnect (which could have been caused by this node or the other party).
     */
    fun onNodeDisconnected(connection: NodeConnection<HandlerType, DescriptorType>)
}