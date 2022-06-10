package net.postchain.eif

import assertk.assert
import assertk.assertions.*
import net.postchain.common.toHex
import net.postchain.core.BlockQueries
import net.postchain.core.BlockchainEngine
import net.postchain.eif.contracts.TestToken
import net.postchain.eif.contracts.TokenBridge
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
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
import java.math.BigInteger

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
        // Deploy token bridge contract
        val bridge = TokenBridge.deploy(web3j, transactionManager, gasProvider).send()

        // Mock query for last eth block in this test
        val blockQueriesMock: BlockQueries = mock {
            on { query(eq("get_last_eth_block"), any()) } doReturn getMockedBlockHeightResponse(null)
        }
        val engineMock: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueriesMock
        }

        val contractDeployTransactionHash = bridge.transactionReceipt.get().transactionHash
        val contractDeployBlockNumber = web3j.ethGetTransactionByHash(contractDeployTransactionHash)
            .send().result.blockNumber
        val eventsToRead = listOf(TokenBridge.DEPOSITEDERC20_EVENT)
        val ethereumEventProcessor =
            EthereumEventProcessor(web3j, listOf(bridge.contractAddress), eventsToRead, BigInteger.ONE, contractDeployBlockNumber, engineMock).apply {
                start()
            }

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contract
        val testToken = TestToken.deploy(web3j, transactionManager, gasProvider).send().apply {
            mint(Address(transactionManager.fromAddress), Uint256(BigInteger.valueOf(initialMint))).send()
            approve(Address(bridge.contractAddress), Uint256(BigInteger.valueOf(initialMint))).send()
        }

        // Deposit to postchain
        for (i in 1..5) {
            bridge.deposit(Address(testToken.contractAddress), Uint256(BigInteger.TEN)).send()
        }

        Awaitility.await()
            .atMost(Duration.ONE_MINUTE)
            .untilAsserted {
                val eventBlocks = ethereumEventProcessor.getEventData()
                val events = eventBlocks.flatMap { it[EncodedBlock.EVENTS.index].asArray().asList() }
                assert(events.size == 5).isTrue()
            }

        // validate events
        val eventData = ethereumEventProcessor.getEventData()
        val eventBlocksToValidate = eventData
            .map { OpData(OP_ETH_BLOCK, it) }
            .toTypedArray()
        assert(ethereumEventProcessor.isValidEventData(eventBlocksToValidate)).isTrue()
        // Test if NoOp version can also validate
        assert(NoOpEventProcessor().isValidEventData(eventBlocksToValidate)).isTrue()

        // Verify that we can't skip any events by removing a block
        assert(ethereumEventProcessor.isValidEventData(eventBlocksToValidate.sliceArray(1 until eventBlocksToValidate.size))).isFalse()

        // Verify that we can't skip any events by removing them from the first block in the list
        val eventBlocksWithoutEvents = eventBlocksToValidate.mapIndexed { i, eventBlock ->
            if (i == 0) {
                OpData(OP_ETH_BLOCK, arrayOf(
                    eventBlock.args[EncodedBlock.NUMBER.index],
                    eventBlock.args[EncodedBlock.HASH.index],
                    gtv(emptyList())
                ))
            } else {
                eventBlock
            }
        }.toTypedArray()
        assert(ethereumEventProcessor.isValidEventData(eventBlocksWithoutEvents)).isFalse()

        // Mock that the block was validated and committed to DB
        val eventDataBlockNumber = eventData.last()[EncodedBlock.NUMBER.index].asBigInteger()
        whenever(blockQueriesMock.query(eq("get_last_eth_block"), any()))
            .doReturn(getMockedBlockHeightResponse(eventDataBlockNumber))

        // Assert events before last committed block are not included now
        assert(ethereumEventProcessor.getEventData().isEmpty()).isTrue()

        // One more final transaction
        // Maxing out this transaction
        val max = BigInteger.TWO.pow(256) - BigInteger.valueOf(initialMint + 1)
        with (testToken) {
            mint(Address(transactionManager.fromAddress), Uint256(max)).send()
            approve(Address(bridge.contractAddress), Uint256(max)).send()
        }
        bridge.deposit(Address(testToken.contractAddress), Uint256(max)).send()

        Awaitility.await()
            .atMost(Duration.ONE_MINUTE)
            .untilAsserted {
                val eventBlocks = ethereumEventProcessor.getEventData()
                val events = eventBlocks.flatMap { it[EncodedBlock.EVENTS.index].asArray().asList() }
                assert(events.size == 1).isTrue()
            }

        val lastEventBlock = ethereumEventProcessor.getEventData().first()
        val lastEvent = lastEventBlock[EncodedBlock.EVENTS.index].asArray().first()
        val indexedValues = lastEvent[EncodedEvent.INDEXED_VALUES.index].asArray()
        val nonIndexedValues = lastEvent[EncodedEvent.NON_INDEXED_VALUES.index].asArray()

        // Check that data in the event matches what we sent
        assert("0x${indexedValues[0].asByteArray().toHex()}").isEqualTo(transactionManager.fromAddress, true) // owner
        assert("0x${indexedValues[1].asByteArray().toHex()}").isEqualTo(testToken.contractAddress, true) // token
        assert(nonIndexedValues[0].asBigInteger()).isEqualTo(max) // value

        ethereumEventProcessor.shutdown()
    }

    @Test
    fun `Events can be received from multiple contracts`() {
        val initialMint = 20L
        // Deploy two token bridge contracts
        val bridgeFirst = TokenBridge.deploy(web3j, transactionManager, gasProvider).send()
        val bridgeSecond = TokenBridge.deploy(web3j, transactionManager, gasProvider).send()

        // Mock query for last eth block in this test
        val blockQueriesMock: BlockQueries = mock {
            on { query(eq("get_last_eth_block"), any()) } doReturn getMockedBlockHeightResponse(null)
        }
        val engineMock: BlockchainEngine = mock {
            on { getBlockQueries() } doReturn blockQueriesMock
        }

        val contractDeployTransactionHash = bridgeFirst.transactionReceipt.get().transactionHash
        val contractDeployBlockNumber = web3j.ethGetTransactionByHash(contractDeployTransactionHash)
                .send().result.blockNumber
        val contractAddresses = listOf(bridgeFirst.contractAddress, bridgeSecond.contractAddress)
        val eventsToRead = listOf(TokenBridge.DEPOSITEDERC20_EVENT)
        val ethereumEventProcessor =
                EthereumEventProcessor(web3j, contractAddresses, eventsToRead, BigInteger.ONE, contractDeployBlockNumber, engineMock).apply {
                    start()
                }

        // Deploy a test token that we mint and then approve transfer of coins to chrL2 contracts
        val testToken = TestToken.deploy(web3j, transactionManager, gasProvider).send().apply {
            mint(Address(transactionManager.fromAddress), Uint256(BigInteger.valueOf(initialMint))).send()
            approve(Address(bridgeFirst.contractAddress), Uint256(BigInteger.TEN)).send()
            approve(Address(bridgeSecond.contractAddress), Uint256(BigInteger.TEN)).send()
        }

        // Deposit to postchain
        bridgeFirst.deposit(Address(testToken.contractAddress), Uint256(BigInteger.TEN)).send()
        bridgeSecond.deposit(Address(testToken.contractAddress), Uint256(BigInteger.TEN)).send()

        // Verify we got both events from the different contracts
        Awaitility.await()
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    val eventBlocks = ethereumEventProcessor.getEventData()
                    val events = eventBlocks.flatMap { it[EncodedBlock.EVENTS.index].asArray().asList() }
                    assert(events.size == 2).isTrue()
                    val eventContractAddresses = events.map { "0x${it[EncodedEvent.CONTRACT.index].asByteArray().toHex()}".toLowerCase() }
                    assert(eventContractAddresses).containsExactly(*contractAddresses.map(String::toLowerCase).toTypedArray())
                }

        ethereumEventProcessor.shutdown()
    }

    private fun getMockedBlockHeightResponse(height: BigInteger?): Promise<Gtv, Exception> {
        return if (height == null) {
            Promise.ofSuccess<Gtv, Exception>(GtvNull)
        } else {
            Promise.ofSuccess<Gtv, Exception>(GtvDictionary.build(mapOf("eth_block_height" to GtvBigInteger(height))))
        }
    }
}
