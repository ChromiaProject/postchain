package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfigurationFactory
import org.mockito.kotlin.mock

class ManagedBlockchainProcessManagerMock(postchainContext: PostchainContext) : ManagedBlockchainProcessManager(
        postchainContext, mock(), mock(), listOf()
) {
    override var dataSource: ManagedNodeDataSource = mock()

    public override fun getBlockchainConfigurationFactory(chainId: Long): (String) -> BlockchainConfigurationFactory {
        return super.getBlockchainConfigurationFactory(chainId)
    }
}
