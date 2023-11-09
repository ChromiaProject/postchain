package net.postchain.debug

class ErrorDiagnosticValue(val message: String, val timestamp: Long, val height: Long? = null) : DiagnosticValue {

    private val errorValue = ErrorValue(message, timestamp, height)

    override val value: ErrorValue
        get() = errorValue

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ErrorDiagnosticValue

        if (message != other.message) return false
        if (timestamp != other.timestamp) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (height?.hashCode() ?: 0)
        return result
    }
}

data class ErrorValue(
        val message: String,
        val timestamp: Long,
        val height: Long?) {
    override fun toString(): String {
        return "(message=$message, timestamp=$timestamp, height=$height)"
    }
}