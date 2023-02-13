package net.postchain.debug

import org.junit.jupiter.api.Test
import assertk.assert
import assertk.assertions.containsExactly

class LazyDiagnosticValueCollectionTest {
    @Test
    fun lazyCollectionIsUpdated() {
        var name = "initial-name"
        val collection = mutableSetOf<DiagnosticValue>(DiagnosticProperty.CONTAINER_NAME withLazyValue { name })
        val lazyCollection = LazyDiagnosticValueCollection(DiagnosticProperty.NULL) { collection.toMutableSet() }
        assert(lazyCollection.value).containsExactly("initial-name")
        name = "updated-name"
        assert(lazyCollection.value).containsExactly("updated-name")
        collection.add(DiagnosticProperty.VERSION withValue "1")
        assert(lazyCollection.value).containsExactly("updated-name", "1")
    }
}
