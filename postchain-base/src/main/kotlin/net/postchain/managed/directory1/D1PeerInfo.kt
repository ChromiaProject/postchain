package net.postchain.managed.directory1

import net.postchain.crypto.PubKey
import net.postchain.gtv.mapper.Name

data class D1PeerInfo(
        @Name("api_url") val restApiUrl: String,
        @Name("pubkey") val pubKey: PubKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as D1PeerInfo

        if (restApiUrl != other.restApiUrl) return false
        if (pubKey != other.pubKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = restApiUrl.hashCode()
        result = 31 * result + pubKey.hashCode()
        return result
    }
}