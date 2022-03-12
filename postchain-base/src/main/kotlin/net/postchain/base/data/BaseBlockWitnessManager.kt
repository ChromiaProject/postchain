package net.postchain.base.data

import net.postchain.base.*
import net.postchain.core.*
import net.postchain.getBFTRequiredSignatureCount

/**
 * The [BaseBlockWitnessManager] can be re-used by block builders at different heights, only the
 * [BlockWitnessBuilder] must be recreated for every block.
 *
 * Note: (probably obvious if you know how Postchain works, but anyway, here we go..)
 * The [BaseBlockWitnessManager] depends on the signer list, which can change at different block heights
 * When the chain reaches a height that demands a new configuration, the chain will restart and we will get a new
 * configuration and must also get new instance of this class from the config (i.e. we cannot cache
 * the [BaseBlockWitnessManager] anywhere, it must be destroyed when chain restarts).
 *
 * @property cryptoSystem Crypto utilities
 * @property blockSigMaker is used to sign
 * @property subjects Public keys for nodes authorized to sign blocks
 */
class BaseBlockWitnessManager(
    private val cryptoSystem: CryptoSystem,
    private val blockSigMaker: SigMaker,
    private val subjects: Array<ByteArray>
) : BlockWitnessManager {

    /**
     * @param header is the header we need a witness builder for
     * @return a fresh [BlockWitnessBuilder] without any signatures
     */
    override fun createWitnessBuilderWithoutOwnSignature(header: BlockHeader): BlockWitnessBuilder {
        return BaseBlockWitnessBuilder(cryptoSystem, header, subjects, getBFTRequiredSignatureCount(subjects.size))
    }

    /**
     * @param header is the header we need a witness builder for
     * @return a fresh [BlockWitnessBuilder] signed by us
     */
    override fun createWitnessBuilderWithOwnSignature(
        header: BlockHeader
    ): BlockWitnessBuilder {
        val witnessBuilder = createWitnessBuilderWithoutOwnSignature(header) as BaseBlockWitnessBuilder
        witnessBuilder.applySignature(blockSigMaker.signDigest(header.blockRID)) // TODO: POS-04_sig
        return witnessBuilder
    }

    /**
     * TODO: move to witness impl
     * Validates the following:
     *  - Witness and witness builder are multi sig
     *  - The signatures are valid with respect to the block being signed
     *  - The number of signatures exceeds the threshold necessary to deem the block itself valid
     *
     *  @param blockWitness is the witness data with signatures we will check
     *  @param witnessBuilder includes the header we should validate
     *  @throws ProgrammerMistake Invalid BlockWitness implementation
     *  @return true if valid
     */
    override fun validateWitness(blockWitness: BlockWitness, witnessBuilder: BlockWitnessBuilder): Boolean {
        if (blockWitness !is MultiSigBlockWitness) {
            throw ProgrammerMistake("Invalid BlockWitness, we need multi sig for the base validation.")
        }
        if (witnessBuilder !is MultiSigBlockWitnessBuilder) {
            throw ProgrammerMistake("Invalid BlockWitnessBuilder, we need a multi sig for the base validation.")
        }

        // A bit counter intuitive maybe, but we are using the given [BlockWitnessBuilder] to re-create the witnesses
        // by applying the signatures again to see if it works ("applySignature()" will do a lot of verifications).
        for (signature in blockWitness.getSignatures()) {
            witnessBuilder.applySignature(signature) // Will explode if signature isn't valid.
        }
        return witnessBuilder.isComplete()
    }
}
