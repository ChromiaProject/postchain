package net.postchain.base.data

import org.postgresql.util.PSQLState
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

object SqlUtils {

    fun toTimestamp(time: Instant? = null): Timestamp {
        return if (time == null) {
            Timestamp(Instant.now().toEpochMilli())
        } else {
            Timestamp(time.toEpochMilli())
        }
    }

    /** Insufficient Resources or Internal Error - see https://www.postgresql.org/docs/current/errcodes-appendix.html */
    fun SQLException.isFatal(): Boolean = sqlState.startsWith("53") || sqlState.startsWith("XX")

    /** This connection has been closed */
    fun SQLException.isClosed(): Boolean = sqlState == PSQLState.CONNECTION_DOES_NOT_EXIST.state

    fun SQLException.isUniqueViolation(): Boolean = sqlState == PSQLState.UNIQUE_VIOLATION.state
}
