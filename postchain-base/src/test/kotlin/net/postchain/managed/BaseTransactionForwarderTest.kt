package net.postchain.managed

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.random.Random

class BaseTransactionForwarderTest {

    val blockchainRid = BlockchainRid.buildRepeat(1)
    val transactionRid = BlockchainRid.buildRepeat(2).data

    @Test
    fun `should successfully forward transaction to a random API URL`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(transaction.getRawData()).thenReturn(byteArrayOf(1, 2, 3, 4))
        whenever(client(any())).thenReturn(Response(Status.OK))
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(1))

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
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(transaction.getRawData()).thenReturn(byteArrayOf(1, 2, 3, 4))
        whenever(client(any())).thenReturn(Response(Status.BAD_REQUEST))
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(17))

        assertFailure {
            forwarder.forward(transaction)
        }.isInstanceOf(UserMistake::class).hasMessage("Unable to forward transaction ${transactionRid.toHex()} to any signer node")

        verify(client, times(apiUrls.size)).invoke(any())
    }

    @Test
    fun `should return valid ApiStatus on successful response`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(client(any())).thenReturn(
                Response(Status.OK).body("""{"status":"confirmed"}""")
        )
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(1))

        assertThat(forwarder.checkStatus(transaction).status).isEqualTo(TransactionStatus.CONFIRMED.status)
    }

    @Test
    fun `should return reject reason on rejected response`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(client(any())).thenReturn(
                Response(Status.OK).body("""{"status":"rejected", "rejectReason": "Some reason"}""")
        )
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(1))

        val status = forwarder.checkStatus(transaction)
        assertThat(status.status).isEqualTo(TransactionStatus.REJECTED.status)
        assertThat(status.rejectReason).isEqualTo("Some reason")
    }

    @Test
    fun `should return UNKNOWN ApiStatus on unsuccessful response`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(client(any())).thenReturn(
                Response(Status.BAD_REQUEST).body("Some error")
        )
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(1))
        assertThat(forwarder.checkStatus(transaction).status).isEqualTo(TransactionStatus.UNKNOWN.status)
    }

    @Test
    fun `should call correct random API URL for checkStatus`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        val randomSeed = 5
        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(randomSeed))
        whenever(client(any())).thenReturn(Response(Status.OK).body("""{"status":"waiting"}"""))
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        assertThat(forwarder.checkStatus(transaction).status).isEqualTo(TransactionStatus.WAITING.status)

        verify(client).invoke(
                Request(Method.GET, "${apiUrls.shuffled(Random(randomSeed)).first()}/tx/$blockchainRid/${transactionRid.toHex()}/status")
                        .header("Accept", ContentType.APPLICATION_JSON.value)
        )
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `should try all API URLs and return UNKNOWN when none returns definite status`() {
        val client: HttpHandler = mock()
        val apiUrls = listOf("https://example1.com", "https://example2.com", "https://example3.com")
        val transaction: Transaction = mock()
        val dataSource: ManagedNodeDataSource = mock()

        whenever(transaction.getRID()).thenReturn(transactionRid)
        whenever(client(any())).thenReturn(Response(Status.OK).body("""{"status":"unknown"}"""))
        whenever(dataSource.getBlockchainApiUrls(any())).thenReturn(apiUrls)

        val forwarder = BaseTransactionForwarder(client, dataSource, blockchainRid, Random(17))

        assertThat(forwarder.checkStatus(transaction).status).isEqualTo(TransactionStatus.UNKNOWN.status)

        verify(client, times(apiUrls.size)).invoke(any())
    }
}
