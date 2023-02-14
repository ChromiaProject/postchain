package net.postchain.debug

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DiagnosticQueueTest {

    @Test
    fun queueGetUpdated() {
        val queue = DiagnosticQueue<Int>(2)
        queue.add(1)
        assert(queue.value).isEqualTo(listOf(1))
        queue.add(2)
        queue.add(3)
        assert(queue.value).isEqualTo(listOf(2, 3))

        assert(queue.contains(2))
    }

    @Test
    fun duplicatesAreNotAllowed() {
        val queue = DiagnosticQueue<String>(2, allowDuplicates = false)
        queue.add("A")
        queue.add("A")
        assert(queue.size).isEqualTo(1)
    }
}
