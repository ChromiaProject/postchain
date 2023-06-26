package net.postchain.debug

import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import net.postchain.api.rest.controller.DisabledDebugInfoQuery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import assertk.assertThat


class DefaultDebugInfoQueryTest {

    @Test
    fun testEmptyContext() {
        val sut = DefaultDebugInfoQuery(JsonNodeDiagnosticContext())

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = json.asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assertThat(actual.has("blockchain"))
    }

    @Test
    fun testNonEmptyContext() {
        val debugContext = JsonNodeDiagnosticContext(
                DiagnosticProperty.VERSION withValue "1.1.1",
                DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" })

        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = json.asJsonObject

        // Asserts
        assertEquals(3, actual.size())
        assertEquals("1.1.1", actual.get(DiagnosticProperty.VERSION.prettyName).asString)
        assertEquals("my-container", actual.get(DiagnosticProperty.CONTAINER_NAME.prettyName).asString)
    }

    @Test
    fun testError_When_Subquery_IsGiven() {
        val debugContext = JsonNodeDiagnosticContext(
                DiagnosticProperty.VERSION withValue "1.1.1",
                DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" })
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo("subquery")
        val actual = json.asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assertThat(actual.has("Error"))
    }

    @Test
    fun testError_When_DisabledDebugInfoQuery() {
        val sut = DisabledDebugInfoQuery()

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = json.asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assertThat(actual.has("error"))
    }

}