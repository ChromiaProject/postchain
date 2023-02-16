package net.postchain.debug

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DiagnosticQueueTest {

    @Test
    fun queueGetUpdated() {
        val queue = DiagnosticQueue<Int>(2)
        queue.add(1)
        assert(queue.value).isEqualTo(linkedSetOf(1))
        queue.add(2)
        queue.add(3)
        assert(queue.value).isEqualTo(linkedSetOf(2, 3))

        assert(queue.peek()).isEqualTo(2)
        queue.add(4)
        assert(queue.value).isEqualTo(linkedSetOf(3, 4))

    }

    @Test
    fun duplicatesAreNotAllowed() {
        val queue = DiagnosticQueue<String>(2)
        queue.add("A")
        queue.add("A")
        assert(queue.size).isEqualTo(1)
    }
}
