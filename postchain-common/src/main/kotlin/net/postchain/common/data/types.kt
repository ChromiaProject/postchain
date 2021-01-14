package net.postchain.common.data

typealias Hash = ByteArray
const val KECCAK256 = "KECCAK-256"
const val SHA256 = "SHA-256"
const val HASH_LENGTH = 32
val EMPTY_HASH = ByteArray(HASH_LENGTH) { 0 }
typealias TreeHasher = (Hash, Hash) -> Hash