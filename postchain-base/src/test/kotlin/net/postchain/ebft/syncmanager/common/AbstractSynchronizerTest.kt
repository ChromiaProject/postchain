package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockHeader
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.extension.FAILED_CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.ConfigurationMismatchException
import net.postchain.core.EContext
import net.postchain.core.FailedConfigurationMismatchException
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.crypto.SigMaker
import net.postchain.ebft.message.AppliedConfig
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.Gtv
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.network.CommunicationManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class AbstractSynchronizerTest {

    private val height = 10L
    private val incomingHeight = height + 1
    private val chainId = 52L
    private val bridHex = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockRID = BlockchainRid.buildFromHex(bridHex)
    private val nodeHex = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val nodeRid = NodeRid.fromHex(nodeHex)
    private val peerIds = mutableSetOf<NodeRid>()
    private val configHash = "configHash".toByteArray()
    private val failedConfigHash = "failedConfigHash".toByteArray()
    private val incomingConfigHash = "incomingConfigHash".toByteArray()
    private val myPrivKeyHex = "0000000000000000000000000000000001000000000000000000000000000000"
    private val myPubKeyHex = "03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94"
    private val pubKey1Hex = "0203C6150397F7E4197FF784A8D74357EF20DAF1D09D823FFF8D3FC9150CBAE85D"
    private val pubKey2Hex = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
    private val myPrivKey = PubKey(myPrivKeyHex)
    private val myPubKey = PubKey(myPubKeyHex)
    private val pubKey1 = PubKey(pubKey1Hex)
    private val pubKey2 = PubKey(pubKey2Hex)
    private val currentSigners = mutableListOf(pubKey2.data, myPubKey.data)
    private val pendingSigners = mutableListOf(pubKey1, pubKey2, myPubKey)
    private val header: ByteArray = "header".toByteArray()
    private val witness: ByteArray = "witness".toByteArray()
    private val transactions: List<ByteArray> = listOf("tx1".toByteArray())

    private val commManager: CommunicationManager<EbftMessage> = mock()
    private val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(height)
        on { getLastBlockHeight() } doReturn completionStage
    }
    private val blockchainConfigurationProvider: ManagedBlockchainConfigurationProvider = mock {
        on { isConfigPending(isA(), isA(), anyLong(), isA()) } doReturn true
        on { getPendingConfigSigners(isA(), anyLong(), isA()) } doReturn pendingSigners
    }
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { blockchainRid } doReturn blockRID
        on { chainID } doReturn chainId
        on { configHash } doReturn configHash
        on { signers } doReturn currentSigners
    }
    private val networkNodes: NetworkNodes = mock {
        on { getPeerIds() } doReturn peerIds
    }
    private val peerCommConf: PeerCommConfiguration = mock {
        on { networkNodes } doReturn networkNodes
    }
    private val eContext: EContext = mock()
    private val storage: Storage = mock {
        on { openReadConnection(chainId) } doReturn eContext
    }
    private val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
        on { blockBuilderStorage } doReturn storage
    }
    private val sigMaker: SigMaker = mock()
    private val appCryptoSystem: CryptoSystem = mock {
        on { buildSigMaker(isA()) } doReturn sigMaker
    }
    private val appConfig: AppConfig = mock {
        on { pubKeyByteArray } doReturn myPubKey.data
        on { privKeyByteArray } doReturn myPrivKey.data
        on { cryptoSystem } doReturn appCryptoSystem
    }
    private val restartNotifier: BlockchainRestartNotifier = mock()
    private val workerContext: WorkerContext = mock {
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { blockchainConfigurationProvider } doReturn blockchainConfigurationProvider
        on { appConfig } doReturn appConfig
        on { restartNotifier } doReturn restartNotifier
    }
    private val headerRec: BlockHeaderData = mock {
        on { getHeight() } doReturn height
    }
    private val configHashGtv: Gtv = mock {
        on { asByteArray(anyBoolean()) } doReturn configHash
    }
    private val failedConfigHashGtv: Gtv = mock {
        on { asByteArray(anyBoolean()) } doReturn failedConfigHash
    }
    private val headerExtraData = mapOf(
            CONFIG_HASH_EXTRA_HEADER to configHashGtv,
            FAILED_CONFIG_HASH_EXTRA_HEADER to failedConfigHashGtv
    )
    private val baseBlockHeader: BaseBlockHeader = mock {
        on { blockHeaderRec } doReturn headerRec
        on { rawData } doReturn header
        on { extraData } doReturn headerExtraData
    }
    private val blockWitness: BlockWitness = mock {
        on { getRawData() } doReturn witness
    }
    private val peerStatuses: AbstractPeerStatuses<*> = mock()
    private val blockWitnessBuilder: BlockWitnessBuilder = mock()
    private val baseBlockWitnessProvider: BaseBlockWitnessProvider = mock {
        on { createWitnessBuilderWithoutOwnSignature(isA()) } doReturn blockWitnessBuilder
    }

    private val baseBlockWitnessProviderProvider: BaseBlockWitnessProviderProvider = { _, _, _ -> baseBlockWitnessProvider }

    private lateinit var sut: AbstractSynchronizer

    @BeforeEach
    fun setup() {
        sut = object : AbstractSynchronizer(workerContext, baseBlockWitnessProviderProvider) {}
    }

    ///// check pending config  /////
    @Test
    fun `check pending config  with chain is 0 should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, height)
        doReturn(0L).whenever(blockchainConfiguration).chainID
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(workerContext, never()).blockchainConfigurationProvider
    }

    @Test
    fun `check pending config  with blockchain configuration provider is not managed should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, height)
        val manualBlockchainConfigurationProvider: ManualBlockchainConfigurationProvider = mock()
        doReturn(manualBlockchainConfigurationProvider).whenever(workerContext).blockchainConfigurationProvider
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(workerContext, atLeastOnce()).blockchainConfigurationProvider
    }

    @Test
    fun `check pending config with applied config height is not next height should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, height)
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(blockQueries, times(2)).getLastBlockHeight()
        verify(blockchainConfiguration, never()).configHash
    }

    @Test
    fun `check pending config with not new config should return false`() {
        // setup
        val appliedConfig = AppliedConfig(configHash, incomingHeight)
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(storage, never()).openReadConnection(chainId)
    }

    @Test
    fun `check pending config with config is not pending should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, incomingHeight)
        doReturn(false).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(blockchainConfigurationProvider).isConfigPending(eContext, blockRID, incomingHeight, incomingConfigHash)
        verify(storage).openReadConnection(chainId)
        verify(storage).closeReadConnection(eContext)
    }

    @Test
    fun `check pending config with my pubKey is not part of signers should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, incomingHeight)
        val pendingSigners = listOf(pubKey1, pubKey2)
        doReturn(pendingSigners).whenever(blockchainConfigurationProvider).getPendingConfigSigners(isA(), anyLong(), isA())
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(blockchainConfigurationProvider).getPendingConfigSigners(blockRID, incomingHeight, incomingConfigHash)
    }

    @Test
    fun `check pending config with us as pending signer but do not need to apply it should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, incomingHeight)
        val pendingSigners = listOf(pubKey2, myPubKey)
        doReturn(pendingSigners).whenever(blockchainConfigurationProvider).getPendingConfigSigners(isA(), anyLong(), isA())
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(blockchainConfigurationProvider).getPendingConfigSigners(blockRID, incomingHeight, incomingConfigHash)
    }

    @Test
    fun `check pending config with us as signer but need to wait for others should return false`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, incomingHeight)
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isFalse()
        // verify
        verify(restartNotifier, never()).notifyRestart(true)
    }

    @Test
    fun `check pending config with enough signatures should trigger restart and return true`() {
        // setup
        val appliedConfig = AppliedConfig(incomingConfigHash, incomingHeight)
        currentSigners.remove(pubKey2.data)
        pendingSigners.add(PubKey(nodeHex))
        // execute & verify
        assertThat(sut.checkIfWeNeedToApplyPendingConfig(nodeRid, appliedConfig)).isTrue()
        // verify
        verify(restartNotifier).notifyRestart(true)
    }

    ///// handle Add block exception /////
    @Test
    fun `handle Add block exception with wrong type of block header should throw exception`() {
        // setup
        val exception = Exception()
        val blockHeader: BlockHeader = mock()
        val block = BlockDataWithWitness(blockHeader, transactions, blockWitness)
        // execute & verify
        assertThat(assertThrows<ProgrammerMistake> {
            sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        }.message).isEqualTo("Expected BaseBlockHeader")
    }

    ///// handle Add block exception: ConfigurationMismatchException /////
    @Test
    fun `handle Add block exception with ConfigurationMismatchException and active block needs configuration change should trigger restart`() {
        // setup
        val exception = ConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(true).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider, atLeastOnce()).activeBlockNeedsConfigurationChange(eContext, chainId, false)
        verify(restartNotifier).notifyRestart(false)
    }

    @Test
    fun `handle Add block exception with ConfigurationMismatchException and missing config hash in block should blacklist peer`() {
        // setup
        val exception = ConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(null).whenever(baseBlockHeader).extraData
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider, atLeastOnce()).activeBlockNeedsConfigurationChange(eContext, chainId, false)
        verify(peerStatuses).maybeBlacklist(isA(), anyString())
    }

    @Test
    fun `handle Add block exception with ConfigurationMismatchException and no pending config should blacklist peer`() {
        // setup
        val exception = ConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(false).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider).isConfigPending(eContext, blockRID, height, configHash)
        verify(peerStatuses).maybeBlacklist(isA(), anyString())
    }

    @Test
    fun `handle Add block exception with ConfigurationMismatchException and pending config but failed witness validation should blacklist peer`() {
        // setup
        val exception = ConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(true).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        doThrow(RuntimeException("Failure")).whenever(baseBlockWitnessProvider).validateWitness(isA(), isA())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider).getPendingConfigSigners(blockRID, height, configHash)
        verify(baseBlockWitnessProvider).createWitnessBuilderWithoutOwnSignature(baseBlockHeader)
        verify(baseBlockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
        verify(peerStatuses).maybeBlacklist(isA(), anyString())
    }

    @Test
    fun `handle Add block exception with ConfigurationMismatchException and pending config should trigger restart`() {
        // setup
        val exception = ConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(true).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        doNothing().whenever(baseBlockWitnessProvider).validateWitness(isA(), isA())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider).getPendingConfigSigners(blockRID, height, configHash)
        verify(baseBlockWitnessProvider).createWitnessBuilderWithoutOwnSignature(baseBlockHeader)
        verify(baseBlockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
        verify(peerStatuses, never()).maybeBlacklist(isA(), anyString())
        verify(restartNotifier).notifyRestart(true)
    }

    ///// handle Add block exception: FailedConfigurationMismatchException /////
    @Test
    fun `handle Add block exception with FailedConfigurationMismatchException and missing failed config hash should blacklist peer`() {
        // setup
        val exception = FailedConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(null).whenever(baseBlockHeader).extraData
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(peerStatuses).maybeBlacklist(nodeRid, "Received a block without expected failed config hash")
    }

    @Test
    fun `handle Add block exception with FailedConfigurationMismatchException and no pending config should blacklist peer`() {
        // setup
        val exception = FailedConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(false).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        doNothing().whenever(baseBlockWitnessProvider).validateWitness(isA(), isA())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider).isConfigPending(eContext, blockRID, height, failedConfigHash)
        verify(peerStatuses).maybeBlacklist(isA(), anyString())
    }

    @Test
    fun `handle Add block exception with FailedConfigurationMismatchException and pending config should trigger restart`() {
        // setup
        val exception = FailedConfigurationMismatchException("Failure")
        val block = BlockDataWithWitness(baseBlockHeader, transactions, blockWitness)
        doReturn(false).whenever(blockchainConfigurationProvider).activeBlockNeedsConfigurationChange(isA(), anyLong(), anyBoolean())
        doReturn(true).whenever(blockchainConfigurationProvider).isConfigPending(isA(), isA(), anyLong(), isA())
        doNothing().whenever(baseBlockWitnessProvider).validateWitness(isA(), isA())
        // execute
        sut.handleAddBlockException(exception, block, null, peerStatuses, nodeRid)
        // verify
        verify(blockchainConfigurationProvider).getPendingConfigSigners(blockRID, height, failedConfigHash)
        verify(baseBlockWitnessProvider).createWitnessBuilderWithoutOwnSignature(baseBlockHeader)
        verify(baseBlockWitnessProvider).validateWitness(blockWitness, blockWitnessBuilder)
        verify(peerStatuses, never()).maybeBlacklist(isA(), anyString())
        verify(restartNotifier).notifyRestart(true)
    }
}