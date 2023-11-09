package net.postchain.bitcoinj.forks.crypto

import java.util.Arrays


object HDKeyDerivation {

    fun createMasterPrivateKey(seed: ByteArray): ByteArray {
        check(seed.size > 8) { "Seed is too short and could be brute forced" }
        val i = HDUtils.hmacSha512(HDUtils.createHmacSha512Digest("Bitcoin seed".toByteArray()), seed)
        check(i.size == 64) { i.size }
        //TODO VALIDATE KEY
        val il = Arrays.copyOfRange(i, 0, 32)
        val ir = Arrays.copyOfRange(i, 32, 64)
        return il
    }
}
