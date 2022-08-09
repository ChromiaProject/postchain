package net.postchain.common

import java.net.ServerSocket

object Utils {

    fun findFreePort(): Int {
        return ServerSocket(0).use {
            it.reuseAddress = true
            it.localPort
        }
    }

    fun findFreePorts(): Pair<Int, Int> {
        val a = ServerSocket(0).apply { reuseAddress = true }
        val b = ServerSocket(0).apply { reuseAddress = true }

        val res = a.localPort to b.localPort

        a.use { }
        b.use { }

        return res
    }
}