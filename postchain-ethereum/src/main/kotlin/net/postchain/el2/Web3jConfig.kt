package net.postchain.el2

import okhttp3.OkHttpClient
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.ipc.UnixIpcService
import org.web3j.protocol.ipc.WindowsIpcService
import java.util.concurrent.TimeUnit

class Web3jConfig {
    fun buildService(clientAddress: String?): Web3jService {
        val web3jService: Web3jService
        if (clientAddress == null || clientAddress == "") {
            web3jService = HttpService(createOkHttpClient())
        } else if (clientAddress.startsWith("http")) {
            web3jService = HttpService(clientAddress, createOkHttpClient(), false)
        } else if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            web3jService = WindowsIpcService(clientAddress)
        } else {
            web3jService = UnixIpcService(clientAddress)
        }
        return web3jService
    }

    private fun createOkHttpClient(): OkHttpClient {
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        configureTimeouts(builder)
        return builder.build()
    }

    private fun configureTimeouts(builder: OkHttpClient.Builder) {
        val tos = 300L
        builder.connectTimeout(tos, TimeUnit.SECONDS)
        builder.readTimeout(tos, TimeUnit.SECONDS) // Sets the socket timeout too
        builder.writeTimeout(tos, TimeUnit.SECONDS)
    }
}
