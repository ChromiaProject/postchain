package net.postchain.debug

class DiagnosticData(
        private val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = linkedMapOf()
) : DiagnosticValue, MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) :
            this(values.toMap(linkedMapOf()))

    override val value: Any
        get() = properties.map { it.key.prettyName to it.value.value }.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiagnosticData

        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()
}
