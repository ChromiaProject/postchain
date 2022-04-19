// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.crypto.SECP256K1CryptoSystem

/**
 * A collection of cryptographic functions based on the elliptic curve secp256k1
 */
@Deprecated("CryptoSystem has been moved to Common", replaceWith = ReplaceWith("SECP256K1CryptoSystem()", "net.postchain.crypto.SECP256K1CryptoSystem"))
class SECP256K1CryptoSystem : SECP256K1CryptoSystem()