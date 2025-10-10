package net.postchain.gtx

import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockQueries
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.BaseSnapshotDatumRepository
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.block.BlockStore
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.gtv.Gtv
import java.time.Duration
import java.util.concurrent.CompletionStage

class GTXBlockQueries(private val blockchainConfiguration: GTXBlockchainConfiguration,
                      storage: Storage,
                      blockStore: BlockStore,
                      chainId: Long,
                      mySubjectId: ByteArray,
                      private val module: GTXModule,
                      snapshotDatumRepository: BaseSnapshotDatumRepository
) : BaseBlockQueries(storage, blockStore, chainId, mySubjectId, snapshotDatumRepository,
        Duration.ofSeconds(blockchainConfiguration.configData.queryTimeoutSeconds)) {

    override fun query(name: String, args: Gtv): CompletionStage<Gtv> = runOp {
        module.query(it, name, args)
    }

    override fun queryWithHeight(name: String, args: Gtv): CompletionStage<Pair<Gtv, Long>> = runOp {
        module.query(it, name, args) to blockStore.getLastBlockHeight(it)
    }

    override fun queryWithTimeout(name: String, args: Gtv, queryTimeout: Duration, lockTimeout: Duration): CompletionStage<Gtv> = runOp(queryTimeout) {
        val db = DatabaseAccess.of(it)
        db.setLocalLockTimeout(it, lockTimeout.toMillis())
        try {
            module.query(it, name, args)
        } finally {
            db.resetLocalLockTimeout(it)
        }
    }

    override fun getTransaction(txRID: ByteArray): CompletionStage<Transaction?> = runOp {
        val txBytes = blockStore.getTxBytes(it, txRID)
        if (txBytes == null)
            null
        else
            blockchainConfiguration.getTransactionFactory().decodeTransaction(txBytes)
    }

    override fun decodeBlockHeader(headerData: ByteArray): BaseBlockHeader =
            blockchainConfiguration.decodeBlockHeader(headerData) as BaseBlockHeader

    override fun decodeWitness(witnessData: ByteArray): MultiSigBlockWitness =
            blockchainConfiguration.decodeWitness(witnessData) as MultiSigBlockWitness

}
