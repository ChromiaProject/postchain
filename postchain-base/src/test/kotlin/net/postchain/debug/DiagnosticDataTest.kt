package net.postchain.debug

import org.junit.jupiter.api.Test
import assertk.assert
import assertk.assertions.isEqualTo

class DiagnosticDataTest {
    @Test
    fun diagnosticDataCanBeUpdated() {
        val map = mutableMapOf<DiagnosticProperty, DiagnosticValue>()
        val data = DiagnosticData(map)
        assert(data.value).isEqualTo(mapOf<DiagnosticProperty, DiagnosticValue>())
        map[DiagnosticProperty.VERSION] = StandardDiagnosticValue("1")
        assert(data.value).isEqualTo(mapOf("version" to "1"))
    }
}