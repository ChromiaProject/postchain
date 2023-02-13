package net.postchain.debug

abstract class AbstractDiagnosticValue(override val property: DiagnosticProperty): DiagnosticValue {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractDiagnosticValue

        if (property != other.property) return false

        return true
    }

    final override fun hashCode(): Int {
        return property.hashCode()
    }
}