package net.postchain.base.importexport

import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportResultTest {

    @Test
    fun toStringTest() {
        // imported
        assertEquals(
                "imported 1 (0)",
                ImportResult(0, 0, -1, 0, 999, ZERO_RID).toString()
        )
        assertEquals(
                "imported 11 (0..10)",
                ImportResult(0, 10, -1, 0, 999, ZERO_RID).toString()
        )
        assertEquals(
                "imported 1 (10)",
                ImportResult(10, 10, -1, 10, 999, ZERO_RID).toString()
        )
        assertEquals(
                "imported 101 (10..110)",
                ImportResult(10, 110, -1, 10, 999, ZERO_RID).toString()
        )

        // skipped
        assertEquals(
                "skipped 1 (0)",
                ImportResult(0, 0, 0, -1, 999, ZERO_RID).toString()
        )
        assertEquals(
                "skipped 11 (0..10)",
                ImportResult(0, 10, 10, -1, 999, ZERO_RID).toString()
        )
        assertEquals(
                "skipped 1 (10)",
                ImportResult(10, 10, 10, -1, 999, ZERO_RID).toString()
        )
        assertEquals(
                "skipped 101 (10..110)",
                ImportResult(10, 110, 110, -1, 999, ZERO_RID).toString()
        )

        // skipped, imported
        assertEquals(
                "skipped 3 (0..2), imported 8 (3..10)",
                ImportResult(0, 10, 2, 3, 999, ZERO_RID).toString()
        )
        assertEquals(
                "skipped 3 (10..12), imported 98 (13..110)",
                ImportResult(10, 110, 12, 13, 999, ZERO_RID).toString()
        )
    }
}