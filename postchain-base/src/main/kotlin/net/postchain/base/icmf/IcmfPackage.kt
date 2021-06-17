package net.postchain.base.icmf

/**
 * Pack all messages that belong to a specific height into a package.
 * This package will get validated as a unit.
 */
data class IcmfPackage (val height: Int) {

    val messages = ArrayList<IcmfMessage>()
}