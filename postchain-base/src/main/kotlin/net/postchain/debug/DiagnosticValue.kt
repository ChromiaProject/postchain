package net.postchain.debug

interface DiagnosticValue {
    val property: DiagnosticProperty
    val value: Any?
}
