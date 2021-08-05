package net.postchain.ebft.heartbeat

@Deprecated("To delete")
class RemoteConfig {
    var responseTimestamp: Long = 0L
    var rawConfig: ByteArray? = null

    fun setConfig(rawConfig: ByteArray) {
        this.rawConfig = rawConfig
        responseTimestamp = System.currentTimeMillis()
    }

}