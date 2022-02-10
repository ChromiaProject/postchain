package net.postchain.el2

import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainEngine
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.ethereum.contracts.TestToken
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtx.OpData
import nl.komponents.kovenant.Promise
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.testcontainers.containers.wait.strategy.Wait
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EthereumEventProcessorTest {

    private val gethContainer = GethContainer()
        .withExposedService(
            "geth", 8545,
            Wait.forListeningPort()
        )

    private val gasProvider = DefaultGasProvider()
    // This could be any private key but value must match in /geth-compose/geth/key.txt
    // and the address created must be added to /geth-compose/geth/test.json
    private val credentials = Credentials
        .create("0x0000000000000000000000000000000001000000000000000000000000000000")
    private lateinit var web3j: Web3j
    private lateinit var transactionManager: TransactionManager

    @BeforeEach
    fun setup() {
        gethContainer.start()

        val gethHost = gethContainer.getServiceHost("geth", 8545)
        val gethPort = gethContainer.getServicePort("geth", 8545)
        web3j = Web3j.build(
            HttpService(
                "http://$gethHost:$gethPort"
            )
        )

        transactionManager = FastRawTransactionManager(
            web3j,
            credentials,
            PollingTransactionReceiptProcessor(
                web3j,
                1000,
                30
            )
        )
    }

    @AfterEach
    fun tearDown() {
        web3j.shutdown()
        gethContainer.stop()
    }

    @Test
    fun `Deposit events on ethereum should be parsed and private validated`() {
        // Deploy ChrL2 contract
        val mockNodes = DynamicArray(Address::class.java)
        val chrL2 = ChrL2.deploy(web3j, transactionManager, gasProvider, mockNodes, mockNodes).send()
        val web3c = Web3Connector(web3j, chrL2.contractAddress)

        // Mock query for last eth block in this test
        val blockQueriesMock: BlockQueries = mock {
            on { query(eq("get_last_eth_block"), any()) } doReturn getMockedBlockHeightResponse(null)
        }
        val engineMock: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueriesMock
        }

        val ethereumEventProcessor = EthereumEventProcessor(web3c, chrL2, BigInteger.ONE, engineMock)
        ethereumEventProcessor.start()

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        val testToken = TestToken.deploy(web3j, transactionManager, gasProvider).send()
        testToken.mint(Address(transactionManager.fromAddress), Uint256(BigInteger.valueOf(50))).send()
        testToken.approve(Address(chrL2.contractAddress), Uint256(BigInteger.valueOf(50))).send()

        // Deposit to postchain
        for (i in 1..5) {
            chrL2.deposit(Address(testToken.contractAddress), Uint256(BigInteger.TEN)).send()
        }

        Awaitility.await()
            .atMost(Duration.ONE_MINUTE)
            .untilAsserted {
                assertTrue(ethereumEventProcessor.getEventData().second.size == 5)
            }

        // validate events
        val eventsToValidate = ethereumEventProcessor.getEventData().second
            .map { OpData(OP_ETH_EVENT, it) }
            .toTypedArray()
        assertTrue(ethereumEventProcessor.isValidEventData(eventsToValidate))

        val lastBlockNumber = web3c.web3j.ethBlockNumber().send().blockNumber
        whenever(blockQueriesMock.query(eq("get_last_eth_block"), any()))
            .doReturn(getMockedBlockHeightResponse(lastBlockNumber))

        // Assert things before last committed block is not included
        assertTrue(ethereumEventProcessor.getEventData().second.isEmpty())

        // One more final transaction
        testToken.mint(Address(transactionManager.fromAddress), Uint256(BigInteger.TEN)).send()
        testToken.approve(Address(chrL2.contractAddress), Uint256(BigInteger.TEN)).send()
        chrL2.deposit(Address(testToken.contractAddress), Uint256(BigInteger.TEN)).send()

        Awaitility.await()
            .atMost(Duration.ONE_MINUTE)
            .untilAsserted {
                assertTrue(ethereumEventProcessor.getEventData().second.size == 1)
            }

        val lastEvent = ethereumEventProcessor.getEventData().second.first()
        assertEquals(transactionManager.fromAddress, lastEvent[6].asString()) // owner
        assertEquals(testToken.contractAddress, lastEvent[7].asString()) // token
        assertEquals(BigInteger.TEN, lastEvent[8].asBigInteger()) // value

        ethereumEventProcessor.shutdown()
    }

    fun getMockedBlockHeightResponse(height: BigInteger?): Promise<Gtv, Exception> {
        return if (height == null) {
            Promise.ofSuccess<Gtv, Exception>(GtvNull)
        } else {
            Promise.ofSuccess<Gtv, Exception>(GtvDictionary.build(mapOf("eth_block_height" to GtvInteger(height))))
        }
    }
}
