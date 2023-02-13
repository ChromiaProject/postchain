package net.postchain.debug

class DiagnosticData(
        private val properties: MutableMap<DiagnosticProperty, DiagnosticValue> = mutableMapOf()
) : AbstractDiagnosticValue(DiagnosticProperty.NULL), MutableCollection<DiagnosticValue> by properties.values {

    constructor(vararg values: DiagnosticValue) :
            this(values.associateBy { it.property }.toMutableMap())

    override val value: Any?
        get() = properties.map { it.key.prettyName to it.value.value }.toMap()
}
