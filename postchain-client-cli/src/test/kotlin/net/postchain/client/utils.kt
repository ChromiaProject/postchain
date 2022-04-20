package net.postchain.client

import net.postchain.core.BlockchainRid
import net.postchain.crypto.KeyPair

val testConfig = PostchainClientConfig(
        "http://localhost:7740",
        BlockchainRid.buildFromHex("EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"),
        KeyPair.fromStrings(
                "0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57",
                "3132333435363738393031323334353637383930313233343536373839303131"
        ),
)