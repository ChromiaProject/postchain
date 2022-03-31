package net.postchain.debug

import com.google.gson.JsonParser
import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class DefaultDebugInfoQueryTest {

    @Test
    fun testEmptyContext() {
        val sut = DefaultDebugInfoQuery(DefaultNodeDiagnosticContext())

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser().parse(json).asJsonObject

        // Asserts
        assertEquals(0, actual.size())
    }

    @Test
    fun testNonEmptyContext() {
        val debugContext = DefaultNodeDiagnosticContext().apply {
            addProperty(DiagnosticProperty.VERSION, "1.1.1")
            addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser().parse(json).asJsonObject

        // Asserts
        assertEquals(2, actual.size())
        assertEquals("1.1.1", actual.get(DiagnosticProperty.VERSION.prettyName).asString)
        assertEquals("my-container", actual.get(DiagnosticProperty.CONTAINER_NAME.prettyName).asString)
    }

    @Test
    fun testError_When_Subquery_IsGiven() {
        val debugContext = DefaultNodeDiagnosticContext().apply {
            addProperty(DiagnosticProperty.VERSION, "1.1.1")
            addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo("subquery")
        val actual = JsonParser().parse(json).asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assert(actual.has("Error"))
    }

    @Test
    fun testError_When_NullDiagnosticContext_IsGiven() {
        val sut = DefaultDebugInfoQuery(null)

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser().parse(json).asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assert(actual.has("Error"))
    }

}