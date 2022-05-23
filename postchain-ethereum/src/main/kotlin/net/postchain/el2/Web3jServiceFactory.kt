package net.postchain.el2

import net.postchain.el2.config.EifConfig
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.ipc.UnixIpcService
import org.web3j.protocol.ipc.WindowsIpcService
import java.util.concurrent.TimeUnit

object Web3jServiceFactory {
    fun buildService(eifConfig: EifConfig): Web3jService {
        return if (eifConfig.url == "") {
            HttpService(createOkHttpClient(eifConfig))
        } else if (eifConfig.url.startsWith("http")) {
            HttpService(eifConfig.url, createOkHttpClient(eifConfig), false)
        } else if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            WindowsIpcService(eifConfig.url)
        } else {
            UnixIpcService(eifConfig.url)
        }
    }

    private fun createOkHttpClient(eifConfig: EifConfig): OkHttpClient {
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        builder.connectTimeout(eifConfig.connectTimeout, TimeUnit.SECONDS)
        builder.readTimeout(eifConfig.readTimeout, TimeUnit.SECONDS) // Sets the socket timeout too
        builder.writeTimeout(eifConfig.writeTimeout, TimeUnit.SECONDS)
        return builder.build()
    }
}
