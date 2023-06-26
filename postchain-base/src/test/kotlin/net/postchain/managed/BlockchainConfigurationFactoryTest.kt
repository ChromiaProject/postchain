package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory
import net.postchain.managed.config.DappBlockchainConfigurationFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals

class BlockchainConfigurationFactoryTest {

    private val appConfigMock: AppConfig = mock {
        on { pubKey } doReturn "ffffffff"
    }

    private val contextMock: PostchainContext = mock {
        on { appConfig } doReturn appConfigMock
        on { nodeDiagnosticContext } doReturn JsonNodeDiagnosticContext()
        on { blockBuilderStorage } doReturn mock()
    }

    private val bpm = spy(ManagedBlockchainProcessManagerMock(contextMock))

    @Test
    fun `bcConfig for chain0 when correct chain0 factory specified`() {
        expectSuccess(0L, GTXBlockchainConfigurationFactory::class, Chain0BlockchainConfigurationFactory::class)
    }

    @Test
    fun `bcConfig for chain0 when incorrect dapp factory specified`() {
        expectFailure(0L, AnyBlockchainConfigFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for chain0 when correct extended chain0 factory specified`() {
        expectSuccess(0L, ExtendedBcConfigFactory::class, Chain0BlockchainConfigurationFactory::class)
    }

    @Test
    fun `bcConfig for chain0 when unknown factory specified`() {
        expectFailure(0L, "com.package.MyFactory")
    }

    @Test
    fun `bcConfig for dapp chain when correct dapp factory specified`() {
        expectSuccess(1L, GTXBlockchainConfigurationFactory::class, DappBlockchainConfigurationFactory::class)
    }

    @Test
    fun `bcConfig for dapp chain when incorrect chain0 factory specified`() {
        expectFailure(1L, AnyBlockchainConfigFactory::class.qualifiedName!!)
    }

    @Test
    fun `bcConfig for dapp chain when correct extended dapp factory specified`() {
        expectSuccess(1L, ExtendedBcConfigFactory::class, DappBlockchainConfigurationFactory::class)
    }

    @Test
    fun `bcConfig for dapp chain when unknown factory specified`() {
        expectFailure(1L, "com.package.MyFactory")
    }

    private fun expectSuccess(chainId: Long, factory: KClass<*>, expectedClass: KClass<*>) {
        val factorySupplier = bpm.getBlockchainConfigurationFactory(chainId)
        val factoryInstance = factorySupplier.supply(factory.qualifiedName!!)

        assertEquals(
                expectedClass.qualifiedName,
                factoryInstance.javaClass.kotlin.qualifiedName)
    }

    private fun expectFailure(chainId: Long, factoryName: String) {
        val factorySupplier = bpm.getBlockchainConfigurationFactory(chainId)

        assertThrows<UserMistake> {
            factorySupplier.supply(factoryName)
        }
    }
}