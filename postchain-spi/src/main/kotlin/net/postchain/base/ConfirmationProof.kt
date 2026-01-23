package net.postchain.base

import net.postchain.gtv.mapper.Name
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree

/**
 * Encapsulating a proof of a transaction hash in a block header
 *
 * @param hash The transaction hash the proof applies to
 * @param blockHeader The block header the [hash] is supposedly in
 * @param witness The block witness
 * @param merkleProofTree a proof including [hash] (in its raw form)
 * @param txIndex is the index of the proven transaction in the block (i.e. our "path").
 */
class ConfirmationProof(
        @param:Name("hash") val hash: ByteArray,
        @param:Name("blockHeader") val blockHeader: ByteArray,
        @param:Name("witness") val witness: BaseBlockWitness,
        @param:Name("merkleProofTree") val merkleProofTree: GtvMerkleProofTree,
        @param:Name("txIndex") val txIndex: Long
)