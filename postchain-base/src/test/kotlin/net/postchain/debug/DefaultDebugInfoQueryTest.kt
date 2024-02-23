package net.postchain.debug

import net.postchain.api.rest.controller.DefaultDebugInfoQuery
import org.junit.jupiter.api.Test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray

class DefaultDebugInfoQueryTest {

    @Test
    fun testEmptyContext() {
        val sut = DefaultDebugInfoQuery(JsonNodeDiagnosticContext())

        // Actions
        val json = sut.default()
        val actual = json.asJsonObject

        // Asserts
        assertThat(actual.size()).isEqualTo(1)
        assertThat(actual.has("blockchain")).isTrue()
    }

    @Test
    fun testNonEmptyContext() {
        val debugContext = JsonNodeDiagnosticContext(
                DiagnosticProperty.VERSION withValue "1.1.1",
                DiagnosticProperty.CONTAINER_NAME withLazyValue { "my-container" })

        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.default()
        val actual = json.asJsonObject

        // Asserts
        assertThat(actual.size()).isEqualTo(3)
        assertThat(actual.get(DiagnosticProperty.VERSION.prettyName).asString).isEqualTo("1.1.1")
        assertThat(actual.get(DiagnosticProperty.CONTAINER_NAME.prettyName).asString).isEqualTo("my-container")
    }

    @Test
    fun testSubqueryWithBlockStatsQuery() {
        val debugContext = JsonNodeDiagnosticContext(DiagnosticProperty.VERSION withValue "1.1.1")
        debugContext.blockchainData(BlockchainRid("4EF13F43A95760D17A267222C61AEEF11542DC62FB8D151AECEAC919F660DD73".hexStringToByteArray()))
        val sut = DefaultDebugInfoQuery(debugContext)

        // Actions
        val json = sut.blockStats()

        // Asserts
        val actual = json.asJsonArray
        assertThat(actual.size()).isEqualTo(1)
        assertThat(actual.get(0).asJsonObject.get(DiagnosticProperty.BLOCKCHAIN_RID.prettyName).asString).isEqualTo("4EF13F43A95760D17A267222C61AEEF11542DC62FB8D151AECEAC919F660DD73")
        assertThat(actual.get(0).asJsonObject.get(DiagnosticProperty.BLOCK_STATS.prettyName).isJsonArray).isTrue()
    }
}
