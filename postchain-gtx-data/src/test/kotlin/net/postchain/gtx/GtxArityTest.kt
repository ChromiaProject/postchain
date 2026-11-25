package net.postchain.gtx

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test

class GtxArityTest {

    @Test
    fun `Gtx rejects trailing unsigned elements`() {
        val gtv = gtv(
                gtv(emptyList<Gtv>()),
                gtv(emptyList<Gtv>()),
                gtv(1L)
        )

        assertFailure { Gtx.fromGtv(gtv) }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `GtxBody rejects trailing unsigned elements`() {
        val gtv = gtv(
                gtv(ByteArray(32)),
                gtv(emptyList<Gtv>()),
                gtv(emptyList<Gtv>()),
                gtv(1L)
        )

        assertFailure { GtxBody.fromGtv(gtv) }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `GtxOp rejects trailing unsigned elements`() {
        val gtv = gtv(
                gtv("op"),
                gtv(emptyList<Gtv>()),
                gtv(1L)
        )

        assertFailure { GtxOp.fromGtv(gtv) }.isInstanceOf(IllegalArgumentException::class)
    }
}
