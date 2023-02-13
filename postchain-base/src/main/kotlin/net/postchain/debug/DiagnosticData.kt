package net.postchain.debug

class DiagnosticData(
        private val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
) : DiagnosticValue, MutableMap<DiagnosticProperty, DiagnosticValue> by properties {

    constructor(vararg values: Pair<DiagnosticProperty, DiagnosticValue>) :
            this(values.toMap().toMutableMap())

    override val value: Any?
        get() = properties.map { it.key.prettyName to it.value.value }.toMap()
}
