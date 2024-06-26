package net.postchain.debug

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.api.rest.json.JsonFactory
import org.junit.jupiter.api.Test

class JsonNodeDiagnosticContextTest {

    @Test
    fun testEmptyContext() {
        val sut = JsonNodeDiagnosticContext()
        assertThat(sut.isNotEmpty())
    }

    @Test
    fun testAddPropertyToContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.PUB_KEY] = EagerDiagnosticValue("237823673673")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assertThat(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assertThat(sut[DiagnosticProperty.PUB_KEY]?.value).isEqualTo("237823673673")
        assertThat(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testRemovePropertyOnContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.PUB_KEY] = EagerDiagnosticValue("237823673673")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assertThat(sut.size).isEqualTo(4)

        // Remove
        sut.remove(DiagnosticProperty.PUB_KEY)

        // Asserts
        assertThat(sut.size).isEqualTo(3)
        assertThat(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assertThat(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testGetPropertyForContext() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { "my-container" }

        // Asserts
        assertThat(sut[DiagnosticProperty.PUB_KEY]).isNull()
        assertThat(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assertThat(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("my-container")
    }

    @Test
    fun testExceptionWhenFetchingProperties() {
        val sut = JsonNodeDiagnosticContext()
        sut[DiagnosticProperty.VERSION] = EagerDiagnosticValue("4.4.4")
        sut[DiagnosticProperty.CONTAINER_NAME] = LazyDiagnosticValue { throw Exception("fail") }

        // Asserts
        assertThat(sut[DiagnosticProperty.VERSION]?.value).isEqualTo("4.4.4")
        assertThat(sut[DiagnosticProperty.CONTAINER_NAME]?.value).isEqualTo("Unable to fetch value, fail")
    }

    @Test
    fun testFormat() {
        val sut = JsonNodeDiagnosticContext(
                DiagnosticProperty.VERSION withValue "4.4.4",
                DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" }
        )

        val blockchain = DiagnosticData(
                DiagnosticProperty.BLOCKCHAIN_RID withValue "AB12",
                DiagnosticProperty.BLOCKCHAIN_LAST_HEIGHT withLazyValue { 1 }
        )
        val blockchains = LazyDiagnosticValueCollection { mutableSetOf(blockchain) }
        sut[DiagnosticProperty.BLOCKCHAIN] = blockchains

        val errorQueue = DiagnosticQueue(5)
        blockchain[DiagnosticProperty.ERROR] = errorQueue
        errorQueue.add(EagerDiagnosticValue("one"))
        errorQueue.add(EagerDiagnosticValue("two"))

        val blockStats = DiagnosticQueue(5)
        blockchain[DiagnosticProperty.BLOCK_STATS] = blockStats
        blockStats.add(DiagnosticData(
                DiagnosticProperty.BLOCK_RID withValue "1234",
                DiagnosticProperty.BLOCK_HEIGHT withValue 17,
                DiagnosticProperty.BLOCK_BUILDER withValue true,
        ))
        blockStats.add(DiagnosticData(
                DiagnosticProperty.BLOCK_RID withValue "abcd",
                DiagnosticProperty.BLOCK_HEIGHT withValue 4711,
                DiagnosticProperty.BLOCK_BUILDER withValue false,
        ))

        assertThat(JsonFactory.makePrettyJson().toJson(sut.format())).isEqualTo("""
            {
              "version": "4.4.4",
              "container-name": "my-container",
              "blockchain": [
                {
                  "brid": "AB12",
                  "height": 1,
                  "error": [
                    "one",
                    "two"
                  ],
                  "block-statistics": [
                    {
                      "rid": "1234",
                      "height": 17,
                      "builder": true
                    },
                    {
                      "rid": "abcd",
                      "height": 4711,
                      "builder": false
                    }
                  ]
                }
              ]
            }
        """.trimIndent())
    }
}
