package net.postchain.debug

import java.util.AbstractQueue
import kotlin.collections.LinkedHashMap

class DiagnosticQueue(
        private val capacity: Int
) : DiagnosticValue, AbstractQueue<DiagnosticValue>() {
    private val map = object : LinkedHashMap<DiagnosticValue, Boolean>(capacity, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DiagnosticValue, Boolean>?) = size > capacity
    }
    private val queue get() = map.keys

    @Synchronized
    override fun poll() = peek()?.also { queue.remove(it) }

    @Synchronized
    override fun peek() = queue.firstOrNull()

    @Synchronized
    override fun offer(element: DiagnosticValue) = map.put(element, true).let { true }

    @Synchronized
    override fun iterator() = queue.iterator()
    override val size @Synchronized get() = queue.size
    override val value @Synchronized get() = queue.map { it.value }
}
