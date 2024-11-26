// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder

interface BlockWitnessProvider {
    /**
     * Returns a new witness builder for the current header.
     *
     * @param header is the header we need a witness builder for
     * @return a fresh [BlockWitnessBuilder] without any signatures
     */
    fun createWitnessBuilderWithoutOwnSignature(header: BlockHeader): BlockWitnessBuilder

    /**
     * Same as [createWitnessBuilderWithoutOwnSignature], but "this node" also adds a signature.
     *
     * @param header is the header we need a witness builder for
     * @return a fresh [BlockWitnessBuilder] signed by us
     */
    fun createWitnessBuilderWithOwnSignature(header: BlockHeader): BlockWitnessBuilder

    /**
     * Validates the following:
     *  - The signatures are valid with respect to the block being signed
     *  - The number of signatures meets the threshold necessary to deem the block itself valid
     *
     *  @param blockWitness is the witness data with signatures we will check
     *  @param witnessBuilder includes the header we should validate
     *  @throws ProgrammerMistake if invalid BlockWitness or BlockWitnessBuilder implementation
     *  @throws UserMistake if validation fails
     */
    fun validateWitness(
            blockWitness: BlockWitness,
            witnessBuilder: BlockWitnessBuilder
    )

}
