package net.postchain.core

import net.postchain.crypto.Key

/**
 * This is the public key of the node.
 *
 * Note: Sometimes a "node" is also a "peer", but doesn't have to be.
 *
 * (The type [Key] has everything we need)
 */
typealias NodeRid = Key
