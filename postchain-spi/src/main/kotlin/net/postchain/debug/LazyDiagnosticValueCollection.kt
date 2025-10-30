package net.postchain.debug

class LazyDiagnosticValueCollection(
        private val lazyCollection: () -> Collection<DiagnosticValue>
) : DiagnosticValue {

    val collection get() = lazyCollection()

    override val value
        get() = collection.map { it.value }
}
