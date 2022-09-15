package net.postchain.d1.anchor

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.d1.icmf.IcmfRemoteSpecialTxExtension
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.DirectoryComponent
import net.postchain.managed.DirectoryDataSource

@Suppress("unused")
class IcmfRemoteProcessManagerExtension(
        val postchainContext: PostchainContext
) : BlockchainProcessManagerExtension, DirectoryComponent {

    private lateinit var directoryDataSource: DirectoryDataSource

    @Synchronized
    override fun connectProcess(process: BlockchainProcess) {
        getIcmfRemoteSpecialTxExtension(process.blockchainEngine.getConfiguration())
                ?.setDirectoryDataSource(directoryDataSource)
    }

    @Synchronized
    override fun disconnectProcess(process: BlockchainProcess) = Unit

    @Synchronized
    override fun afterCommit(process: BlockchainProcess, height: Long) = Unit

    @Synchronized
    override fun shutdown() = Unit

    override fun setDirectoryDataSource(directoryDataSource: DirectoryDataSource) {
        this.directoryDataSource = directoryDataSource
    }

    private fun getIcmfRemoteSpecialTxExtension(cfg: BlockchainConfiguration): IcmfRemoteSpecialTxExtension? {
        return (cfg as? GTXBlockchainConfiguration)?.module?.getSpecialTxExtensions()?.firstOrNull {
            it is IcmfRemoteSpecialTxExtension
        } as IcmfRemoteSpecialTxExtension?
    }
}