package net.postchain.base.data

import net.postchain.base.BaseBlockWitnessBuilder
import net.postchain.base.BlockWitnessProvider
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.getBFTRequiredSignatureCount

/**
 * The [BaseBlockWitnessProvider] can be re-used by block builders at different heights, only the
 * [BlockWitnessBuilder] must be recreated for every block.
 *
 * Note: (probably obvious if you know how Postchain works, but anyway, here we go..)
 * The [BaseBlockWitnessProvider] depends on the signer list, which can change at different block heights
 * When the chain reaches a height that demands a new configuration, the chain will restart and we will get a new
 * configuration and must also get new instance of this class from the config (i.e. we cannot cache
 * the [BaseBlockWitnessProvider] anywhere, it must be destroyed when chain restarts).
 *
 * @property cryptoSystem Crypto utilities
 * @property blockSigMaker is used to sign
 * @property subjects Public keys for nodes authorized to sign blocks
 */
class BaseBlockWitnessProvider(
        private val cryptoSystem: CryptoSystem,
        private val blockSigMaker: SigMaker,
        private val subjects: Array<ByteArray>
) : BlockWitnessProvider {

    override fun createWitnessBuilderWithoutOwnSignature(header: BlockHeader): BlockWitnessBuilder {
        return BaseBlockWitnessBuilder(cryptoSystem, header, subjects, getBFTRequiredSignatureCount(subjects.size))
    }

    override fun createWitnessBuilderWithOwnSignature(
            header: BlockHeader
    ): BlockWitnessBuilder {
        val witnessBuilder = createWitnessBuilderWithoutOwnSignature(header) as BaseBlockWitnessBuilder
        witnessBuilder.applySignature(blockSigMaker.signDigest(header.blockRID)) // TODO: POS-04_sig
        return witnessBuilder
    }

    // TODO: move to witness impl
    // Validates the following:
    //  - Witness and witness builder are multi sig
    override fun validateWitness(blockWitness: BlockWitness, witnessBuilder: BlockWitnessBuilder) {
        if (blockWitness !is MultiSigBlockWitness) {
            throw ProgrammerMistake("Invalid BlockWitness, we need multi sig for the base validation.")
        }
        if (witnessBuilder !is MultiSigBlockWitnessBuilder) {
            throw ProgrammerMistake("Invalid BlockWitnessBuilder, we need a multi sig for the base validation.")
        }

        if (blockWitness.getSignatures().size < witnessBuilder.threshold) {
            throw UserMistake("Insufficient number of witness (needs at least ${witnessBuilder.threshold} but got only ${blockWitness.getSignatures().size})")
        }

        // A bit counterintuitive maybe, but we are using the given [BlockWitnessBuilder] to re-create the witnesses
        // by applying the signatures again to see if it works ("applySignature()" will do a lot of verifications).
        for (signature in blockWitness.getSignatures()) {
            witnessBuilder.applySignature(signature) // Will explode if signature isn't valid.
        }
        if (!witnessBuilder.isComplete()) {
            throw UserMistake("Insufficient number of valid witness signatures")
        }
    }
}
