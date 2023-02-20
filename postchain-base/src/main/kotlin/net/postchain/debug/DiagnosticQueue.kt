package net.postchain.debug

import java.util.AbstractQueue
import kotlin.collections.LinkedHashMap

class DiagnosticQueue<E>(
        private val capacity: Int
) : DiagnosticValue, AbstractQueue<E>() {
    private val map = object : LinkedHashMap<E, Boolean>(capacity, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<E, Boolean>?) = size > capacity
    }
    private val queue get() = map.keys

    override fun poll() = peek()?.also { queue.remove(it) }
    override fun peek() = queue.firstOrNull()
    override fun offer(element: E) = map.put(element, true).let { true }
    override fun iterator() = queue.iterator()
    override val size get() = queue.size
    override val value get() = queue
}
