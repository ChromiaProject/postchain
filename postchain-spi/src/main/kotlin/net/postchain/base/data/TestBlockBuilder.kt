package net.postchain.base.data

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.SpecialTransactionHandler
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.block.BlockStore
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import java.time.Clock

class TestBlockBuilder(
        blockchainRID: BlockchainRid,
        cryptoSystem: CryptoSystem,
        eContext: EContext,
        store: BlockStore,
        specialTxHandler: SpecialTransactionHandler,
        subjects: Array<ByteArray>,
        blockSigMaker: SigMaker,
        blockWitnessProvider: BlockWitnessProvider,
        blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
        extensions: List<BaseBlockBuilderExtension>,
        usingHistoricBRID: Boolean,
        maxBlockSize: Long,
        maxBlockTransactions: Long,
        maxSpecialEndTransactionSize: Long,
        suppressSpecialTransactionValidation: Boolean,
        maxBlockFutureTime: Long,
        myPubKey: ByteArray?,
        isSyncing: Boolean,
        merkleHashCalculator: GtvMerkleHashCalculatorBase,
        clock: Clock = Clock.systemUTC(),
) : BaseBlockBuilder(
        blockchainRID,
        cryptoSystem,
        eContext,
        store,
        specialTxHandler,
        subjects,
        blockSigMaker,
        blockWitnessProvider,
        blockchainRelatedInfoDependencyList,
        extensions,
        usingHistoricBRID,
        maxBlockSize,
        maxBlockTransactions,
        maxSpecialEndTransactionSize,
        suppressSpecialTransactionValidation,
        maxBlockFutureTime,
        myPubKey,
        isSyncing,
        merkleHashCalculator,
        clock,
) {
    override fun checkSpecialTransaction(tx: Transaction) {
        // do nothing
    }
}
