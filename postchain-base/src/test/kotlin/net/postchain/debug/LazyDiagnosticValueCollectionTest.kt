package net.postchain.debug

import org.junit.jupiter.api.Test
import assertk.assert
import assertk.assertions.containsExactly

class LazyDiagnosticValueCollectionTest {
    @Test
    fun lazyCollectionIsUpdated() {
        var name = "initial-name"
        val collection = mutableSetOf<DiagnosticValue>(LazyDiagnosticValue { name })
        val lazyCollection = LazyDiagnosticValueCollection { collection }
        assert(lazyCollection.value).containsExactly("initial-name")
        name = "updated-name"
        assert(lazyCollection.value).containsExactly("updated-name")
        collection.add(EagerDiagnosticValue("1"))
        assert(lazyCollection.value).containsExactly("updated-name", "1")
    }
}