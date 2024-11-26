package net.postchain.debug

class EagerDiagnosticValue(override val value: Any?) : DiagnosticValue {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EagerDiagnosticValue

        return value == other.value
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }
}
