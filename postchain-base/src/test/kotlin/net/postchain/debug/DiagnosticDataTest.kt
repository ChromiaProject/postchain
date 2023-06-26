package net.postchain.debug

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DiagnosticDataTest {
    @Test
    fun diagnosticDataCanBeUpdated() {
        val map = mutableMapOf<DiagnosticProperty, DiagnosticValue>()
        val data = DiagnosticData(map)
        assertThat(data.value).isEqualTo(mapOf<DiagnosticProperty, DiagnosticValue>())
        map[DiagnosticProperty.VERSION] = EagerDiagnosticValue("1")
        assertThat(data.value).isEqualTo(mapOf("version" to "1"))
    }
}
