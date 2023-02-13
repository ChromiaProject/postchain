package net.postchain.debug

interface NodeDiagnosticContext : MutableSet<DiagnosticValue> {

    operator fun get(k: DiagnosticProperty): DiagnosticValue?
    fun remove(k: DiagnosticProperty): DiagnosticValue?

    fun contains(k: DiagnosticProperty): Boolean

    fun format(): String
}
