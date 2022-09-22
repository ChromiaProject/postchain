package net.postchain.d1.cluster

import net.postchain.crypto.PubKey
import net.postchain.gtv.mapper.Name

data class PeerApi(
        @Name("api_url") val restApiUrl: String,
        @Name("pubkey") private val key: ByteArray
) {
    val pubkey = PubKey(key)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PeerApi

        if (restApiUrl != other.restApiUrl) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = restApiUrl.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}
