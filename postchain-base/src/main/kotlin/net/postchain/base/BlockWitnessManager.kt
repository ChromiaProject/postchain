// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.core.BlockHeader
import net.postchain.core.BlockWitness
import net.postchain.core.BlockWitnessBuilder

/**
 * TODO: This stuff is horrible, refactor!
 */
interface BlockWitnessManager {
    // Returns a new witness builder for the current header
    fun createWitnessBuilderWithoutOwnSignature(
        header: BlockHeader
    ): BlockWitnessBuilder

    // Same as above, but "this node" also adds a signature
    fun createWitnessBuilderWithOwnSignature(
        header: BlockHeader
    ): BlockWitnessBuilder

    // Returns true if the signatures check out
    fun validateWitness(
            blockWitness: BlockWitness,
            witnessBuilder: BlockWitnessBuilder // Includes the header we are about to validate
    ): Boolean


}