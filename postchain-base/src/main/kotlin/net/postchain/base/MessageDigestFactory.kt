package net.postchain.base

import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

object MessageDigestFactory {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun create(algorithm: String): MessageDigest {
        return MessageDigest.getInstance(algorithm)
    }
}