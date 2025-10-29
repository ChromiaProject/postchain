package net.postchain.debug

class DiagnosticData(
        private val properties: ConcurrentLinkedHashMap<DiagnosticProperty, DiagnosticValue> = ConcurrentLinkedHashMap()
) : DiagnosticValue, MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) :
            this(values.toMap(ConcurrentLinkedHashMap()))

    override val value: Any
        get() = synchronized(properties) {
            properties.map { it.key.prettyName to it.value.value }.toMap()
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiagnosticData

        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()
}
