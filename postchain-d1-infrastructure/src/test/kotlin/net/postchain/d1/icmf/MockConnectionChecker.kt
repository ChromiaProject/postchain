package net.postchain.d1.icmf
//
//import net.postchain.base.BlockchainRelatedInfo
//
//class MockConnectionChecker(val alwaysReturn: Boolean) : ConnectionChecker {
//
//    override fun shouldConnect(
//        sourceChain: Long,
//        listeningChain: Long,
//        controller: IcmfController
//    ): Boolean = alwaysReturn
//
//    override fun shouldConnect(
//        otherSourceConnChecker: ConnectionChecker
//    ): Boolean = alwaysReturn
//
//}