package net.postchain.debug

interface NodeDiagnosticContext : MutableMap<DiagnosticProperty, DiagnosticValue> {
    fun format(): String
}
