package net.postchain.debug

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DiagnosticQueueTest {

    @Test
    fun queueGetUpdated() {
        val queue = DiagnosticQueue(2)
        queue.add(EagerDiagnosticValue(1))
        assertThat(queue.value).containsExactly(1)
        queue.add(EagerDiagnosticValue(2))
        queue.add(EagerDiagnosticValue(3))
        assertThat(queue.value).containsExactly(2, 3)

        assertThat(queue.peek()!!.value).isEqualTo(2)
        queue.add(EagerDiagnosticValue(4))
        assertThat(queue.value).containsExactly(3, 4)

    }

    @Test
    fun duplicatesAreNotAllowed() {
        val queue = DiagnosticQueue(2)
        queue.add(EagerDiagnosticValue("A"))
        queue.add(EagerDiagnosticValue("A"))
        assertThat(queue.size).isEqualTo(1)
    }
}
