package net.postchain.network.peer

import net.postchain.core.NodeRid
import net.postchain.network.CommunicationManager
import kotlin.reflect.KClass

abstract class AbstractMessagePublisher<MType> : CommunicationManager<MType> {

    val subscribers = mutableMapOf<KClass<*>, MutableSet<(NodeRid, MType) -> Unit>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : MType> subscribe(noinline subscriber: (NodeRid, T) -> Unit) {
        subscribers.computeIfAbsent(T::class) { mutableSetOf() }.add(subscriber as (NodeRid, MType) -> Unit)
    }

    protected inline fun <reified T : MType> publish(from: NodeRid, event: T) {
        subscribers[T::class]?.forEach { it.invoke(from, event) }
    }
}