package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import kotlin.test.assertEquals

class BlockchainConfigurationFactoryTest {

    private val appConfigMock: AppConfig = mock {
        on { pubKey } doReturn "ffffffff"
    }

    private val contextMock: PostchainContext = mock {
        on { appConfig } doReturn appConfigMock
    }

    private val bpm = spy(ManagedBlockchainProcessManagerMock(contextMock))

    @Test
    fun `bcConfig for chain0 when correct chain0 factory specified`() {
        expectSuccess(0L, Chain0BlockchainConfigurationFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for chain0 when incorrect dapp factory specified`() {
        expectFailure(0L, DappBlockchainConfigurationFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for chain0 when correct extended chain0 factory specified`() {
        expectSuccess(0L, ExtendedChain0BcCfgFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for chain0 when unknown factory specified`() {
        expectFailure(0L, "com.package.MyFactory")
    }

    @Test
    fun `bcConfig for dapp chain when correct dapp factory specified`() {
        expectSuccess(1L, DappBlockchainConfigurationFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for dapp chain when incorrect chain0 factory specified`() {
        expectFailure(1L, Chain0BlockchainConfigurationFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for dapp chain when correct extended dapp factory specified`() {
        expectSuccess(1L, ExtendedDappBcCfgFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for dapp chain when unknown factory specified`() {
        expectFailure(1L, "com.package.MyFactory")
    }

    private fun expectSuccess(chainId: Long, factoryName: String) {
        val factorySupplier = bpm.getBlockchainConfigurationFactory(chainId)
        val factory = factorySupplier(factoryName)

        assertEquals(
                factoryName,
                factory.javaClass.kotlin.qualifiedName)
    }

    private fun expectFailure(chainId: Long, factoryName: String) {
        val factorySupplier = bpm.getBlockchainConfigurationFactory(chainId)

        assertThrows<UserMistake> {
            factorySupplier(factoryName)
        }
    }
}