package net.postchain.debug

class LazyDiagnosticValueCollection(
        private val lazyCollection: () -> Collection<DiagnosticValue>
) : DiagnosticValue {

    val collection get() = lazyCollection()

    override val value
        get() = synchronized(collection) {
            collection.map { it.value }
        }
}
