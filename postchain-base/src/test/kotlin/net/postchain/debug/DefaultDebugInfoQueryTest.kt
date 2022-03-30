package net.postchain.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class DefaultDebugInfoQueryTest {

    @Test
    fun testEmptyContextWithDebug() {
        val actual = getDebugInfoFromEmptyContext(true)

        // Asserts
        assertEquals(0, actual.size())
    }

    @Test
    fun testEmptyContextWithoutDebug() {
        val actual = getDebugInfoFromEmptyContext(false)

        // Asserts
        assertEquals(1, actual.size())
        assertEquals(true, actual.has("Error"))
    }

    private fun getDebugInfoFromEmptyContext(debug: Boolean): JsonObject {
        val sut = DefaultDebugInfoQuery(DefaultNodeDiagnosticContext(debug))

        // Actions
        val json = sut.queryDebugInfo(null)
        return JsonParser().parse(json).asJsonObject
    }

    @Test
    fun testContextWithDebug() {
        val debugContext = DefaultNodeDiagnosticContext(true).apply {
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
    fun testError_When_ContextWithoutDebug() {
        val debugContext = DefaultNodeDiagnosticContext(false).apply {
            addProperty(DiagnosticProperty.VERSION, "1.1.1")
            addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo(null)
        val actual = JsonParser().parse(json).asJsonObject

        // Asserts
        assertEquals(1, actual.size())
        assertEquals(true, actual.has("Error"))
    }

    @Test
    fun testError_When_SubqueryIsGiven_WithDebug() {
        val actual = getSubDebugInfoFromContext(true)

        // Asserts
        assertEquals(1, actual.size())
        assertEquals(true, actual.has("Error"))
    }

    @Test
    fun testError_When_SubqueryIsGiven_WithoutDebug() {
        val actual = getSubDebugInfoFromContext(false)

        // Asserts
        assertEquals(1, actual.size())
        assertEquals(true, actual.has("Error"))
    }

    private fun getSubDebugInfoFromContext(debug: Boolean): JsonObject {
        val debugContext = DefaultNodeDiagnosticContext(debug).apply {
            addProperty(DiagnosticProperty.VERSION, "1.1.1")
            addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }
        }
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.queryDebugInfo("subquery")
        return JsonParser().parse(json).asJsonObject
    }

}