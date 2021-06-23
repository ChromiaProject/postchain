// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.common.data.Hash

interface DigestSystem {
    val algorithm: String

    fun hash(left: Hash, right: Hash): Hash
    fun digest(data: ByteArray): Hash
}