package net.postchain.debug

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class JsonNodeDiagnosticContextTest {

    @Test
    fun testEmptyContext() {
        val sut = JsonNodeDiagnosticContext()
        assert(sut.isNotEmpty())
    }

    @Test
    fun testAddPropertyToContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.PUB_KEY] = EagerDiagnosticValue("237823673673")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assert(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assert(sut[DiagnosticProperty.PUB_KEY]?.value).isEqualTo("237823673673")
        assert(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testRemovePropertyOnContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.PUB_KEY] = EagerDiagnosticValue("237823673673")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assert(sut.size).isEqualTo(4)

        // Remove
        sut.remove(DiagnosticProperty.PUB_KEY)

        // Asserts
        assert(sut.size).isEqualTo(3)
        assert(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assert(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testGetPropertyForContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assert(sut[DiagnosticProperty.PUB_KEY]).isNull()
        assert(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assert(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testExceptionWhenFetchingProperties() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { throw Exception("fail") }

        // Asserts
        assert(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assert(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("Unable to fetch value, fail")
    }

    @Test
    fun testFormat() {
        val sut = JsonNodeDiagnosticContext(
                DiagnosticProperty.VERSION withValue "4.4.4",
                DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" }
        )

        val map = DiagnosticData(
                DiagnosticProperty.BLOCKCHAIN_RID withValue "AB12",
                DiagnosticProperty.BLOCKCHAIN_LAST_HEIGHT withLazyValue { 1 }
        )

        val blockchains = LazyDiagnosticValueCollection { mutableSetOf(map) }

        sut[DiagnosticProperty.BLOCKCHAIN] = blockchains

        assert(sut.format()).isEqualTo("""
            {
              "version": "4.4.4",
              "container-name": "my-container",
              "blockchain": [
                {
                  "brid": "AB12",
                  "height": 1
                }
              ]
            }
        """.trimIndent())
    }
}
