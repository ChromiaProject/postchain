// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.Gson
import net.postchain.api.rest.model.ApiStatus
import net.postchain.common.tx.TransactionStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class ApiStatusSerializerTest {

    @Test
    fun notRejectedStatus() {
        val sut = ApiStatus(TransactionStatus.CONFIRMED)

        val actual = Gson().toJson(sut)
        val expected = """
            {
                "status": "confirmed"
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT)
    }

    @Test
    fun rejectedStatusWithReason() {
        val sut = ApiStatus(TransactionStatus.REJECTED, "Reject reason here")

        val actual = Gson().toJson(sut)
        val expected = """
            {
                "status": "rejected",
                "rejectReason": "Reject reason here"
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT)
    }

    @Test
    fun rejectedStatusWithoutReason() {
        val sut = ApiStatus(TransactionStatus.REJECTED)

        val actual = Gson().toJson(sut)
        val expected = """
            {
                "status": "rejected"
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT)
    }

    @Test
    fun notRejectedStatusWithRejectedReason_will_throws_Exception() {
        assertThrows<java.lang.IllegalStateException> {
            ApiStatus(TransactionStatus.WAITING, "Reject reason here")
        }
    }
}