package net.postchain.debug

import org.junit.jupiter.api.Test
import assertk.assertThat
import assertk.assertions.containsExactly

class LazyDiagnosticValueCollectionTest {
    @Test
    fun lazyCollectionIsUpdated() {
        var name = "initial-name"
        val collection = mutableSetOf<DiagnosticValue>(LazyDiagnosticValue { name })
        val lazyCollection = LazyDiagnosticValueCollection { collection }
        assertThat(lazyCollection.value).containsExactly("initial-name")
        name = "updated-name"
        assertThat(lazyCollection.value).containsExactly("updated-name")
        collection.add(EagerDiagnosticValue("1"))
        assertThat(lazyCollection.value).containsExactly("updated-name", "1")
    }
}
