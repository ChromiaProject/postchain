package net.postchain.debug

interface NodeDiagnosticContext: MutableSet<DiagnosticValue>, MutableMap<DiagnosticProperty, DiagnosticValue> {

    fun format(): String
}