// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonParser
import net.postchain.api.rest.model.ApiStatus
import net.postchain.common.tx.TransactionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApiStatusSerializerTest {

    val gson = JsonFactory.makeJson()

    @Test
    fun notRejectedStatus() {
        val sut = ApiStatus(TransactionStatus.CONFIRMED)

        val actual = gson.toJson(sut)
        val expected = """
            {
                "status": "confirmed"
            }
        """.trimIndent()

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }

    @Test
    fun rejectedStatusWithReason() {
        val sut = ApiStatus(TransactionStatus.REJECTED, "Reject reason here")

        val actual = gson.toJson(sut)
        val expected = """
            {
                "status": "rejected",
                "rejectReason": "Reject reason here"
            }
        """.trimIndent()

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }

    @Test
    fun rejectedStatusWithReasonAndTimestamp() {
        val sut = ApiStatus(TransactionStatus.REJECTED, "Reject reason here", 1740659274153)

        val actual = gson.toJson(sut)
        val expected = """
            {
                "status": "rejected",
                "rejectReason": "Reject reason here",
                "rejectTimestamp": 1740659274153
            }
        """.trimIndent()

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }

    @Test
    fun rejectedStatusWithoutReason() {
        val sut = ApiStatus(TransactionStatus.REJECTED)

        val actual = gson.toJson(sut)
        val expected = """
            {
                "status": "rejected"
            }
        """.trimIndent()

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }

    @Test
    fun notRejectedStatusWithRejectedReason_will_throws_Exception() {
        assertThrows<java.lang.IllegalStateException> {
            ApiStatus(TransactionStatus.WAITING, "Reject reason here")
        }
    }
}