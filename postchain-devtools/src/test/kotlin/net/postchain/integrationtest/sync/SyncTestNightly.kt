package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.devtools.AbstractSyncTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SyncTestNightly : AbstractSyncTest() {

    companion object : KLogging() {

        @JvmStatic
        fun testArguments() = listOf(
                // Empty blockchain
                arrayOf(1, 1, setOf(0), setOf<Int>(), 0),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 0),

                // Single block test
                arrayOf(1, 1, setOf(0), setOf<Int>(), 1),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 1),
                arrayOf(1, 2, setOf(1), setOf<Int>(0), 1),
                arrayOf(2, 0, setOf(1), setOf<Int>(), 1),

                // Multi block test
                arrayOf(1, 1, setOf(0), setOf<Int>(), 50),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 50),
                arrayOf(1, 2, setOf(1), setOf<Int>(0), 50),
                arrayOf(2, 0, setOf(1), setOf<Int>(), 50),

                // Multi node multi blocks
                arrayOf(4, 4, setOf(0, 1, 2, 4, 5), setOf<Int>(3, 6), 50)

        )
    }

    @ParameterizedTest
    @MethodSource("testArguments")
    fun sync(signers: Int, replicas: Int, syncIndex: Set<Int>, stopIndex: Set<Int>, blocksToSync: Int) {
        System.out.println("++ Sync Nightly, -------- Signs: $signers Repls: $replicas SyncIdx: ${syncIndex?.size} StopIdx: ${stopIndex?.size} blocks: $blocksToSync ------------------")
        runSyncTest(signers, replicas, syncIndex, stopIndex, blocksToSync)
    }
}