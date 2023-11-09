// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.gtv.GtvString
import org.junit.jupiter.api.Test

class GtvToStringTest {

    @Test
    fun `strings are quoted and escaped`() {
        val gtvString = GtvString("""'foo' "bar" \baz""")
        assertThat(gtvString.toString()).isEqualTo(""""\'foo\' \"bar\" \\baz"""")
    }
}
