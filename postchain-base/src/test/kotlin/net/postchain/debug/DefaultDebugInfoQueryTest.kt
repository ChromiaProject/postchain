package net.postchain.debug

import com.google.gson.JsonParser
import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import net.postchain.api.rest.controller.DisabledDebugInfoQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class DefaultDebugInfoQueryTest {

    @Test
    fun testEmptyContext() {
        val sut = DefaultDebugInfoQuery(JsonNodeDiagnosticContext())

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser.parseString(json).asJsonObject

        // Asserts
        assertEquals(0, actual.size())
    }

    @Test
    fun testNonEmptyContext() {
        val debugContext = JsonNodeDiagnosticContext().apply {
            add(DiagnosticProperty.VERSION withValue  "1.1.1")
            add(DiagnosticProperty.CONTAINER_NAME withLazyValue  { "my-container" })
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser.parseString(json).asJsonObject

        // Asserts
        assertEquals(2, actual.size())
        assertEquals("1.1.1", actual.get(DiagnosticProperty.VERSION.prettyName).asString)
        assertEquals("my-container", actual.get(DiagnosticProperty.CONTAINER_NAME.prettyName).asString)
    }

    @Test
    fun testError_When_Subquery_IsGiven() {
        val debugContext = JsonNodeDiagnosticContext().apply {
            add(DiagnosticProperty.VERSION withValue "1.1.1")
            add(DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" })
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo("subquery")
        val actual = JsonParser.parseString(json).asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assert(actual.has("Error"))
    }

    @Test
    fun testError_When_DisabledDebugInfoQuery() {
        val sut = DisabledDebugInfoQuery()

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser.parseString(json).asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assert(actual.has("error"))
    }

}