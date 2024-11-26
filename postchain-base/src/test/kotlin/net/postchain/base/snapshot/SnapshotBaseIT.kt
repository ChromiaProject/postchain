package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.StorageBuilder
import net.postchain.base.BaseEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import java.security.MessageDigest
import java.security.Security

open class SnapshotBaseIT {

    protected val cs = Secp256K1CryptoSystem()
    protected lateinit var ds: DigestSystem
    protected val appConfig: AppConfig = testDbConfig("database_snapshot_base")
    protected val hostChainId = 999L
    protected val accounts: Array<Int> = Array(64) { it }
    protected val pageSize = 4
    protected open val protocolVersion: Int = 1

    companion object : KLogging() {
        const val PREFIX: String = "sys.x.eif"
        const val levelsPerPage = 2
        const val snapshotsToKeep = 2
    }

    init {
        // We add this provider so that we can get keccak-256 message digest instances
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @BeforeEach
    fun setUp() {
        ds = SimpleDigestSystem(MessageDigest.getInstance(KECCAK256))
        StorageBuilder.wipeDatabase(appConfig)
    }

    // Good for debugging
    protected fun buildAccountState(id: Long, nonce: Int = 0) = BlockchainRid.buildFromHex("aa${nonce}aa$id".padStart(64, 'f')).data

    protected fun initBlockchain(chainID: Long, parentCtx: EContext): Pair<BaseEContext, DatabaseAccess> {
        val ctx = BaseEContext(
                parentCtx.conn, chainID, DatabaseAccess.of(parentCtx)
        )

        val db = DatabaseAccess.of(ctx).apply {
            initializeBlockchain(ctx, BlockchainRid.buildRepeat(chainID.toByte()))
            createPageTable(ctx, "${PREFIX}_snapshot")
            createStateLeafTable(ctx, PREFIX)
            createStateLeafTableIndex(ctx, PREFIX, 0)
        }

        return ctx to db
    }

    protected fun calculateMerkleRoot(proofs: List<Hash>, pos: Long, leaf: Hash): Hash {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0L) {
                ds.hash(h, r)
            } else {
                ds.hash(r, h)
            }
        }
        return r
    }

    protected fun verifyAccountStateProof(
            expectedRootHash: String,
            account: Long,
            accountState: Hash,
            height: Long,
            snapshot: SnapshotPageStore
    ) {
        val proof = snapshot.getMerkleProof(height, account)
        val accountStateRootHash = calculateMerkleRoot(proof, account, accountState)
        assertEquals(expectedRootHash, accountStateRootHash.toHex())
    }
}