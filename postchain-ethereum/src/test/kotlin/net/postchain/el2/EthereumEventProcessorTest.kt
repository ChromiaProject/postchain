package net.postchain.el2

import net.postchain.core.BlockQueries
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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.web3j.EVMComposeTest
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EVMComposeTest("src/test/resources/geth-compose/docker-compose.yml", "node1", 8545)
class EthereumEventProcessorTest {

    @Test
    fun `Deposit events on ethereum should be parsed and validated`(
        web3j: Web3j,
        transactionManager: TransactionManager,
        gasProvider: ContractGasProvider
    ) {
        // Deploy ChrL2 contract
        val mockNodes = DynamicArray(Address::class.java)
        val chrL2 = ChrL2.deploy(web3j, transactionManager, gasProvider, mockNodes, mockNodes).send()
        val web3c = Web3Connector(web3j, chrL2.contractAddress)

        // Mock query for last eth block in this test
        val blockQueriesMock: BlockQueries = mock {
            on { query(eq("get_last_eth_block"), any()) } doReturn getMockedBlockHeightResponse(null)
        }

        val ethereumEventProcessor = EthereumEventProcessor(web3c, chrL2, BigInteger.ZERO, blockQueriesMock)

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
    }

    fun getMockedBlockHeightResponse(height: BigInteger?): Promise<Gtv, Exception> {
        return if (height == null) {
            Promise.ofSuccess<Gtv, Exception>(GtvNull)
        } else {
            Promise.ofSuccess<Gtv, Exception>(GtvDictionary.build(mapOf("eth_block_height" to GtvInteger(height))))
        }
    }
}
