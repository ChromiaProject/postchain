package net.postchain.base.snapshot

import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeEach
import java.security.MessageDigest
import java.security.Security

open class SnapshotBaseIT {

    protected lateinit var ds: DigestSystem

    companion object {
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
    }

    protected fun getMerkleRoot(proofs: List<Hash>, pos: Int, leaf: Hash): Hash {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0) {
                ds.hash(h, r)
            } else {
                ds.hash(r, h)
            }
        }
        return r
    }
}