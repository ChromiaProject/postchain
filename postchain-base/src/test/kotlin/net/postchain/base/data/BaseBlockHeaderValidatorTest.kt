package net.postchain.base.data

import net.postchain.base.*
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainRid
import net.postchain.core.InitialBlockData
import org.junit.jupiter.api.Test

/**
 * The [BaseBlockWitnessManager] doesn't have any DB dependencies, which makes it pretty easy to do unit test for
 *
 * Note: this is somewhat overlapping the [BaseBlockBuilderValidatonTest]
 */
class BaseBlockHeaderValidatorTest {

    val myMerkleRootHash = "46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val myBlockRid       = "3333333333333333333333333333333333333333333333333333333333333333".hexStringToByteArray()

    @Test
    fun testHappy() {

        val privKey          = "1122334455667788112233445566778811223344556677881122334455667788".hexStringToByteArray()
        val myPrevBlockRid   = "1234123412341234123412341234123412341234123412341234123412341234".hexStringToByteArray()
        val myBlockchainRid  = BlockchainRid.buildRepeat(7)
        val sub1PubKey       = secp256k1_derivePubKey(privKey)
        val sub2PubKey       = "2222222222222222222222222222222222222222222222222222222222222222".hexStringToByteArray()


        val myChainIid = 2L
        val myBlockId = 111L
        val myHeight = 1
        val myTimestamp = 100L

        // Make a header
        val myBlockData = InitialBlockData(myBlockchainRid,myBlockId, myChainIid, myPrevBlockRid, myHeight.toLong(), myTimestamp, arrayOf())
        val header = BaseBlockHeader.make(cryptoSystem, myBlockData, myMerkleRootHash, myTimestamp, mapOf())


        // ------------------
        // Make the validator
        // ------------------
        val cryptoSystem: CryptoSystem = SECP256K1CryptoSystem()
        val subjects = arrayOf(sub1PubKey, sub2PubKey)

        val sigMaker: SigMaker = cryptoSystem.buildSigMaker(sub1PubKey, privKey)

        val validator = BaseBlockWitnessManager(cryptoSystem, sigMaker, subjects)

        // ------------------
        // Validate
        // ------------------
        val witnessBuilder = validator.createWitnessBuilderWithoutOwnSignature(header)

        val valid = GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(header, myBlockData, ::expectedMerkleHash, ::getBlockRid, myTimestamp, 0)

    }

    fun expectedMerkleHash(): ByteArray {
        return myMerkleRootHash
    }

    fun getBlockRid(height: Long): ByteArray? {
        return myBlockRid
    }
}