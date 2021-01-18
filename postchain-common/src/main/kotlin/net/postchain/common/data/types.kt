package net.postchain.common.data

typealias Hash = ByteArray

typealias TreeHasher = (Hash, Hash) -> Hash

val EMPTY_HASH = ByteArray(32) { 0 }