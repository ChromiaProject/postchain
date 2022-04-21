// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.crypto.Secp256K1CryptoSystem

/**
 * A collection of cryptographic functions based on the elliptic curve secp256k1
 */
@Deprecated("CryptoSystem has been moved to Common", replaceWith = ReplaceWith("Secp256K1CryptoSystem()", "net.postchain.crypto.Secp256K1CryptoSystem"))
class SECP256K1CryptoSystem : Secp256K1CryptoSystem()