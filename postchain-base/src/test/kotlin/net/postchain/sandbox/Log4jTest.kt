// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.sandbox

import mu.KLogging
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class Log4jTest {

    companion object : KLogging()

    @Test
    @Disabled
    fun test() {
        var i = 0
        while (true) {
            logger.error { "Hello Kitty - ${i++}" }
        }
    }

}