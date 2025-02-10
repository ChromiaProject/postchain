package net.postchain.managed

import assertk.assertFailure
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.Transaction
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.MemoryBody
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.random.Random

class BaseTransactionForwarderTest {

    val blockchainRid = BlockchainRid.buildRepeat(1)
    val transactionRid = BlockchainRid.buildRepeat(2).data

    @Test
    fun `should successfully forward transaction to a random API URL`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val blockchainRid = BlockchainRid(ByteArray(32))
        val transaction: Transaction = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(transaction.getRawData()).thenReturn(byteArrayOf(1, 2, 3, 4))
        whenever(client(any())).thenReturn(Response(Status.OK))

        val forwarder = BaseTransactionForwarder(client, apiUrls, blockchainRid, Random(1))

        forwarder.forward(transaction)

        verify(client, times(1)).invoke(
                Request(Method.POST, "${apiUrls[2]}/tx/$blockchainRid")
                        .header("Content-Type", ContentType.OCTET_STREAM.value)
                        .header("Accept", ContentType.OCTET_STREAM.value)
                        .body(MemoryBody(transaction.getRawData()))
        )
    }

    @Test
    fun `should try all API URLs and throw UserMistake when none succeeds`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val blockchainRid = BlockchainRid(ByteArray(32))
        val transaction: Transaction = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(transaction.getRawData()).thenReturn(byteArrayOf(1, 2, 3, 4))
        whenever(client(any())).thenReturn(Response(Status.BAD_REQUEST))

        val forwarder = BaseTransactionForwarder(client, apiUrls, blockchainRid, Random(17))

        assertFailure {
            forwarder.forward(transaction)
        }.isInstanceOf(UserMistake::class).hasMessage("Unable to forward transaction ${transactionRid.toHex()} to any signer node")

        verify(client, times(apiUrls.size)).invoke(any())
    }

    @Test
    fun `should handle empty list of API URLs by throwing exception`() {
        val client: HttpHandler = mock()
        val blockchainRid = BlockchainRid(ByteArray(32))
        val transaction: Transaction = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)

        val forwarder = BaseTransactionForwarder(client, emptyList(), blockchainRid, Random(17))

        assertFailure {
            forwarder.forward(transaction)
        }.isInstanceOf(UserMistake::class).hasMessage("Unable to forward transaction ${transactionRid.toHex()} to any signer node")

        verify(client, never()).invoke(any())
    }
}
