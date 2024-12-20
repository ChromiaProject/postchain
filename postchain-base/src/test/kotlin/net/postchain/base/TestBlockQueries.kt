package net.postchain.base

import net.postchain.common.exception.UserMistake
import net.postchain.core.Storage
import net.postchain.core.block.BlockStore
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.crypto.Digester
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class TestBlockQueries(
        digester: Digester,
        storage: Storage,
        blockStore: BlockStore,
        chainId: Long,
        mySubjectId: ByteArray,
        val merkleHashCalculator: GtvMerkleHashCalculatorBase
) : BaseBlockQueries(digester, storage, blockStore, chainId, mySubjectId) {
    override fun decodeBlockHeader(headerData: ByteArray): BaseBlockHeader =
            BaseBlockHeader(headerData, merkleHashCalculator)

    override fun decodeWitness(witnessData: ByteArray): MultiSigBlockWitness =
            BaseBlockWitness.fromBytes(witnessData)

    override fun query(name: String, args: Gtv): CompletionStage<Gtv> =
             CompletableFuture.failedStage(UserMistake("Queries are not supported"))
}