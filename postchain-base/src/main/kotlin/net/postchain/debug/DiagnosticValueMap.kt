package net.postchain.debug

class DiagnosticValueMap(
        override val property: DiagnosticProperty,
        vararg values: DiagnosticValue
) : DiagnosticValue, AbstractDiagnosticMap(values.associateBy { it.property }.toMutableMap()) {

    override val value: Any?
        get() = properties.map { it.key.prettyName to it.value.value }.toMap()
}
