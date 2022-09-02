package net.postchain.core.block

import net.postchain.crypto.Signature

interface MultiSigBlockWitness : BlockWitness {
    fun getSignatures(): Array<Signature>
}