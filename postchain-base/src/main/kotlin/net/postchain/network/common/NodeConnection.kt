package net.postchain.network.common


/**
 * Generic network connection interface. Represents a connection from this node to some other node.
 *
 * @property HandlerType is the type that can handle messages
 * @property DescriptorType is the type that describes the connection
 */
interface NodeConnection<HandlerType, DescriptorType> {
    fun accept(handler: HandlerType)
    fun sendPacket(packet: LazyPacket)
    fun remoteAddress(): String
    fun close()
    fun descriptor(): DescriptorType
}