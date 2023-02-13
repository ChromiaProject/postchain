package net.postchain.debug

interface NodeDiagnosticContext : Map<DiagnosticProperty, DiagnosticValue> {

    fun add(v: DiagnosticValue): Boolean
    fun remove(k: DiagnosticProperty): DiagnosticValue?

    fun format(): String
}
