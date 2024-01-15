package net.postchain.network.mastersub

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvNull
import net.postchain.network.mastersub.protocol.MsQueryResponse
import org.junit.jupiter.api.Test

class MasterSubQueryManagerTest {

    @Test
    fun ensureRequestFuturesAreTriggeredAndProperlyRemoved() {
        val msSubQueryManager = MasterSubQueryManager { _, _ -> true }

        // Make a query request
        val requestFuture = msSubQueryManager.query(BlockchainRid.ZERO_RID, "test", GtvNull).toCompletableFuture()

        assertThat(requestFuture.isDone).isFalse()
        // We know the first request gets ID = 1
        assertThat(msSubQueryManager.isRequestOutstanding(1)).isTrue()

        // Mock receiving a query response
        msSubQueryManager.onMessage(MsQueryResponse(1, GtvNull))

        assertThat(requestFuture.isDone).isTrue()
        assertThat(msSubQueryManager.isRequestOutstanding(1)).isFalse()
    }

}