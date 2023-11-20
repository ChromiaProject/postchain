package net.postchain.network.common

import java.util.concurrent.CompletableFuture


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

    /**
     * Returns future that is completed when connection is completely closed.
     * Meaning the connection is closed and all packets have been read/sent.
     */
    fun close(): CompletableFuture<Void>
    fun descriptor(): DescriptorType
}