package net.postchain.debug

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DiagnosticDataTest {
    @Test
    fun diagnosticDataCanBeUpdated() {
        val map = mutableMapOf<DiagnosticProperty, DiagnosticValue>()
        val data = DiagnosticData(map)
        assert(data.value).isEqualTo(mapOf<DiagnosticProperty, DiagnosticValue>())
        map[DiagnosticProperty.VERSION] = EagerDiagnosticValue("1")
        assert(data.value).isEqualTo(mapOf("version" to "1"))
    }
}
