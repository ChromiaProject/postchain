package net.postchain.managed.directory1

import net.postchain.crypto.PubKey
import net.postchain.gtv.mapper.Name

data class D1PeerInfo(
        @Name("api_url") val restApiUrl: String,
        @Name("pubkey") val pubKey: PubKey
)
