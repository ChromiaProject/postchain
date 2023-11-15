package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.*
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.ValidationResult
import net.postchain.core.block.InitialBlockData
import net.postchain.gtv.GtvString
import org.junit.jupiter.api.Test

/**
 * The [BaseBlockWitnessProvider] doesn't have any DB dependencies, which makes it pretty easy to do unit test for
 *
 * Note: this is somewhat overlapping the [BaseBlockBuilderValidatonTest]
 */
class BaseBlockHeaderValidatorTest {

    val myMerkleRootHash = "46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val myBlockRid = "3333333333333333333333333333333333333333333333333333333333333333".hexStringToByteArray()

    @Test
    fun testHappy() {

        val myPrevBlockRid = "1234123412341234123412341234123412341234123412341234123412341234".hexStringToByteArray()
        val myBlockchainRid = BlockchainRid.buildRepeat(7)

        val myChainIid = 2L
        val myBlockId = 111L
        val myHeight = 1
        val myTimestamp = 100L

        // Make a header
        val myBlockData = InitialBlockData(myBlockchainRid, myBlockId, myChainIid, myPrevBlockRid, myHeight.toLong(), myTimestamp, arrayOf())
        val header = BaseBlockHeader.make(calculator, myBlockData, myMerkleRootHash, myTimestamp, mapOf("eif" to GtvString("this is root hash of eif event and state tree")))

        val valid = GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(header, myBlockData, ::expectedMerkleHash,
                ::getBlockRid, myTimestamp - 1, 0, -1, 0,
                mapOf("eif" to GtvString("this is root hash of eif event and state tree")))
        assertThat(valid.result).isEqualTo(ValidationResult.Result.OK)
    }

    private fun expectedMerkleHash(): ByteArray {
        return myMerkleRootHash
    }

    @Suppress("UNUSED_PARAMETER")
    fun getBlockRid(height: Long): ByteArray? {
        return myBlockRid
    }
}