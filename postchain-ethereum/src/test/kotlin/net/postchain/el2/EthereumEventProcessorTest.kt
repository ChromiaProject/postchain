package net.postchain.el2

import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainEngine
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.ethereum.contracts.TestToken
import net.postchain.gtv.*
import net.postchain.gtx.OpData
import nl.komponents.kovenant.Promise
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import org.web3j.utils.Numeric
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
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
        val initialMint = 50L
        // Deploy ChrL2 contract
        val chrL2 = ChrL2.deploy(web3j, transactionManager, gasProvider).send()
        val web3c = Web3Connector(web3j, chrL2.contractAddress)

        // Mock query for last eth block in this test
        val blockQueriesMock: BlockQueries = mock {
            on { query(eq("get_last_eth_block"), any()) } doReturn getMockedBlockHeightResponse(null)
        }
        val engineMock: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueriesMock
        }

        val contractDeployTransactionHash = chrL2.transactionReceipt.get().transactionHash
        val contractDeployBlockNumber = web3j.ethGetTransactionByHash(contractDeployTransactionHash)
            .send().result.blockNumber
        val ethereumEventProcessor =
            EthereumEventProcessor(web3c, chrL2.contractAddress, BigInteger.ONE, contractDeployBlockNumber, engineMock)
        ethereumEventProcessor.start()

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        val testToken = TestToken.deploy(web3j, transactionManager, gasProvider).send()
        testToken.mint(Address(transactionManager.fromAddress), Uint256(BigInteger.valueOf(initialMint))).send()
        testToken.approve(Address(chrL2.contractAddress), Uint256(BigInteger.valueOf(initialMint))).send()

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
        val eventData = ethereumEventProcessor.getEventData()
        val eventsToValidate = eventData.second
            .map { OpData(OP_ETH_EVENT, it) }
            .toTypedArray()
        assertTrue(ethereumEventProcessor.isValidEventData(eventsToValidate))
        // Test if NoOp version can also validate
        assertTrue(NoOpEventProcessor().isValidEventData(eventsToValidate))

        // Mock that the block was validated and committed to DB
        val eventDataBlockNumber = eventData.first.first().asBigInteger()
        whenever(blockQueriesMock.query(eq("get_last_eth_block"), any()))
            .doReturn(getMockedBlockHeightResponse(eventDataBlockNumber))

        // Assert events before last committed block are not included now
        assertTrue(ethereumEventProcessor.getEventData().second.isEmpty())

        // One more final transaction
        // Maxing out this transaction
        val max = BigInteger.TWO.pow(256) - BigInteger.valueOf(initialMint + 1)
        testToken.mint(Address(transactionManager.fromAddress), Uint256(max)).send()
        testToken.approve(Address(chrL2.contractAddress), Uint256(max)).send()
        chrL2.deposit(Address(testToken.contractAddress), Uint256(max)).send()

        Awaitility.await()
            .atMost(Duration.ONE_MINUTE)
            .untilAsserted {
                assertTrue(ethereumEventProcessor.getEventData().second.size == 1)
            }

        val lastEvent = ethereumEventProcessor.getEventData().second.first()
        val eventArgs = lastEvent[7].asArray()
        // Check that data in the event matches what we sent
        assertTrue(
            Numeric.hexStringToByteArray(transactionManager.fromAddress).contentEquals(eventArgs[0].asByteArray())
        ) // owner
        assertTrue(
            Numeric.hexStringToByteArray(testToken.contractAddress).contentEquals(eventArgs[1].asByteArray())
        ) // token
        assertEquals(max, eventArgs[2].asBigInteger()) // value

        ethereumEventProcessor.shutdown()
    }

    fun getMockedBlockHeightResponse(height: BigInteger?): Promise<Gtv, Exception> {
        return if (height == null) {
            Promise.ofSuccess<Gtv, Exception>(GtvNull)
        } else {
            Promise.ofSuccess<Gtv, Exception>(GtvDictionary.build(mapOf("eth_block_height" to GtvBigInteger(height))))
        }
    }
}
