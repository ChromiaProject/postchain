package net.postchain.debug

interface DiagnosticContext: MutableSet<DiagnosticValue>, MutableMap<DiagnosticProperty, DiagnosticValue> {

    fun format(): String
}