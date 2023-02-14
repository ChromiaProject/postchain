package net.postchain.debug

import java.util.AbstractQueue

class DiagnosticQueue<E>(
        private val capacity: Int,
        private val allowDuplicates: Boolean = true
) : DiagnosticValue, AbstractQueue<E>() {
    private val queue = ArrayDeque<E>(capacity)

    override val size get() = queue.size
    override fun poll() = queue.removeFirstOrNull()
    override fun peek() = queue.firstOrNull()
    override fun iterator() = queue.iterator()

    override fun offer(element: E): Boolean {
        if (!allowDuplicates && queue.contains(element)) return true // This is pretty inefficient, consider using a HashSet
        if (queue.size == capacity) {
            queue.removeFirst()
        }
        queue.addLast(element)
        return true
    }

    override val value get() = queue
}
