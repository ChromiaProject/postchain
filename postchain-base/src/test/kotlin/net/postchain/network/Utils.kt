package net.postchain.network

import java.net.ServerSocket

object Utils {

    fun findFreePort(): Int {
        return ServerSocket(0).use {
            it.reuseAddress = true
            it.localPort
        }
    }
}