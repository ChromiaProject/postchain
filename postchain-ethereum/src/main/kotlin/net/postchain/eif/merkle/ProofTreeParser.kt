package net.postchain.eif.merkle

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.gtv.merkle.proof.MerkleProofElement
import net.postchain.gtv.merkle.proof.ProofHashedLeaf
import net.postchain.gtv.merkle.proof.ProofNode
import net.postchain.gtv.merkle.proof.ProofValueLeaf
import java.util.*

object ProofTreeParser {

    fun getProofListAndPosition(tree: MerkleProofElement): Pair<List<ByteArray>, Int> {
        val proofs = LinkedList<ByteArray>()
        var position = 0
        var currentNode = tree

        while (true) {
            if (currentNode is ProofValueLeaf<*>) {
                break
            }

            val node = currentNode as ProofNode
            val left = node.left
            val right = node.right
            if (right is ProofHashedLeaf) {
                proofs.addFirst(right.merkleHash)
                position *= 2
                currentNode = left
            } else if (left is ProofHashedLeaf) {
                proofs.addFirst(left.merkleHash)
                position = 2 * position + 1
                currentNode = right
            } else {
                throw ProgrammerMistake(
                    "Expected one side to be ${ProofHashedLeaf::class.simpleName}" +
                            " but was left: ${left::class.simpleName} and right: ${right::class.simpleName}"
                )
            }
        }

        return proofs to position
    }

}
