package net.postchain.gtx

import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class GTXValidationWhileSyncingTest {
    private val dummySigner = ByteArray(0)
    private val dummySignature = ByteArray(0)
    private val csMock: CryptoSystem = mock {
        on { verifyDigest(any(), any()) } doReturn true
    }

    @Test
    fun `Check sync correctness check caching`() {
        val mockOp: GTXOperation = mock {}
        val tx = GTXTransaction(
                byteArrayOf(), GtvNull, Gtx(GtxBody(ZERO_RID, arrayOf(), arrayOf()), arrayOf()),
                arrayOf(dummySigner),
                arrayOf(dummySignature),
                arrayOf(mockOp),
                byteArrayOf(), byteArrayOf(), csMock
        )

        tx.checkCorrectnessWhileSyncing()
        verify(mockOp, times(1)).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectness()
        verify(csMock, times(1)).verifyDigest(any(), any())

        // Verify sync correctness check is cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectness()
        verify(csMock, never()).verifyDigest(any(), any())

        // Verify non-sync correctness check is run but signature verification is still cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectness()
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, times(1)).checkCorrectness()
        verify(csMock, never()).verifyDigest(any(), any())
    }

    @Test
    fun `Check correctness check caching`() {
        val mockOp: GTXOperation = mock {}
        val tx = GTXTransaction(
                byteArrayOf(), GtvNull, Gtx(GtxBody(ZERO_RID, arrayOf(), arrayOf()), arrayOf()),
                arrayOf(dummySigner),
                arrayOf(dummySignature),
                arrayOf(mockOp),
                byteArrayOf(), byteArrayOf(), csMock
        )

        tx.checkCorrectness()
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, times(1)).checkCorrectness()
        verify(csMock, times(1)).verifyDigest(any(), any())

        // Verify non-sync correctness check is cached
        clearInvocations(mockOp, csMock)
        tx.checkCorrectness()
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectness()
        verify(csMock, never()).verifyDigest(any(), any())

        // Verify sync correctness check is not run
        clearInvocations(mockOp, csMock)
        tx.checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectnessWhileSyncing()
        verify(mockOp, never()).checkCorrectness()
        verify(csMock, never()).verifyDigest(any(), any())
    }
}
