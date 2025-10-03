package net.postchain.gtx

import assertk.assertThat
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GTXValidationWhileSyncingTest {
    private val dummySigner = ByteArray(0)
    private val dummySignature = ByteArray(0)
    private val csMock: CryptoSystem = mock {
        on { verifyDigest(any(), any()) } doReturn true
    }
    private val ctxt: EContext = mock()

    @Test
    fun `Check sync correctness check caching`() {
        val mockOp: GTXOperation = mock {}
        val tx = buildGtxTx(mockOp)

        tx.checkCorrectnessWhileSyncing(ctxt)
        verify(mockOp, times(1)).checkCorrectnessWhileSyncing(ctxt)
        verify(mockOp, never()).checkCorrectness()
        verify(mockOp, never()).checkCorrectness(any())
        verify(csMock, times(1)).verifyDigest(any(), any())

        // Verify sync correctness check is cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectnessWhileSyncing(ctxt)
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectness()
        verify(mockOp, never()).checkCorrectnessWhileSyncing(any())
        verify(mockOp, never()).checkCorrectness(any())
        verify(csMock, never()).verifyDigest(any(), any())

        // Verify non-sync correctness check is run but signature verification is still cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectness(ctxt)
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectnessWhileSyncing(any())
        verify(mockOp, times(1)).checkCorrectness(ctxt)
        verify(csMock, never()).verifyDigest(any(), any())
    }

    @Test
    fun `Check correctness check caching`() {
        val mockOp: GTXOperation = mock {}
        val tx = buildGtxTx(mockOp)

        tx.checkCorrectness(ctxt)
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, times(1)).checkCorrectness(ctxt)
        verify(csMock, times(1)).verifyDigest(any(), any())

        // Verify non-sync correctness check is cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectness(ctxt)
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectnessWhileSyncing(any())
        verify(mockOp, never()).checkCorrectness()
        verify(mockOp, never()).checkCorrectness(any())
        verify(csMock, never()).verifyDigest(any(), any())

        // Verify sync correctness check is not run
        clearInvocations(mockOp, csMock)
        tx.checkCorrectnessWhileSyncing(ctxt)
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectnessWhileSyncing(any())
        verify(mockOp, never()).checkCorrectness()
        verify(mockOp, never()).checkCorrectness(any())
        verify(csMock, never()).verifyDigest(any(), any())
    }

    @Test
    fun `apply succeeds if operation is correct regardless of correctness while syncing`() {
        val mockOp: GTXOperation = mock {}

        doNothing().whenever(mockOp).checkCorrectness(any())
        doReturn(true).whenever(mockOp).apply(any())

        doThrow(UserMistake::class).whenever(mockOp).checkCorrectnessWhileSyncing()
        doReturn(false).whenever(mockOp).applyWhileSyncing(any())

        val tx = buildGtxTx(mockOp)

        assertThat(tx.apply(mock {}))

        verify(mockOp, times(1)).checkCorrectness(any())
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
    }

    @Test
    fun `applyWhileSyncing succeeds if operation is correctWhileSyncing regardless of correctness`() {
        val mockOp: GTXOperation = mock {}

        doThrow(UserMistake::class).whenever(mockOp).checkCorrectness(any())
        doReturn(false).whenever(mockOp).apply(any())

        doNothing().whenever(mockOp).checkCorrectnessWhileSyncing(any())
        doReturn(true).whenever(mockOp).applyWhileSyncing(any())

        val tx = buildGtxTx(mockOp)

        assertThat(tx.applyWhileSyncing(mock {}))

        verify(mockOp, never()).checkCorrectness()
        verify(mockOp, never()).checkCorrectness(any())
        verify(mockOp, times(1)).checkCorrectnessWhileSyncing(any())
    }

    private fun buildGtxTx(vararg ops: Transactor): GTXTransaction {
        return GTXTransaction(
                byteArrayOf(), GtvNull, Gtx(GtxBody(ZERO_RID, arrayOf(), arrayOf()), arrayOf()),
                arrayOf(dummySigner),
                arrayOf(dummySignature),
                arrayOf(*ops),
                byteArrayOf(), byteArrayOf(), csMock
        )
    }
}
