package net.postchain.containers.bpm

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.createLogCaptor
import net.postchain.containers.infra.ContainerNodeConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContainerConfigFactoryTest {

    @Test
    fun `image version tag is not set`() {
        val config: ContainerNodeConfig = mock {
            on { imageVersionTag } doReturn ""
        }
        doReturn("chromaway/chromia-subnode:3.13.1")
                .doReturn("chromaway/chromia-subnode").whenever(config).containerImage

        assertThat(ContainerConfigFactory.getContainerImage(config))
                .isEqualTo("chromaway/chromia-subnode:3.13.1")
        assertThat(ContainerConfigFactory.getContainerImage(config))
                .isEqualTo("chromaway/chromia-subnode")
    }

    @Test
    fun `subnode image version tag equals image version tag`() {
        val subnodeImage = "chromaway/chromia-subnode:3.13.1"
        val config: ContainerNodeConfig = mock {
            on { containerImage } doReturn subnodeImage
            on { imageVersionTag } doReturn "3.13.1"
        }

        val actual = ContainerConfigFactory.getContainerImage(config)
        assertThat(actual).isEqualTo(subnodeImage)
    }

    @Test
    fun `subnode image version tag is not equal to image version tag`() {
        val subnodeImage = "chromaway/chromia-subnode:3.13.1"
        val config: ContainerNodeConfig = mock {
            on { containerImage } doReturn subnodeImage
            on { imageVersionTag } doReturn "3.13.2"
        }

        val appender = createLogCaptor(ContainerConfigFactory::class.java, "List")
        val actual = ContainerConfigFactory.getContainerImage(config)
        assertThat(actual).isEqualTo(subnodeImage)
        assertThat(appender.events.first().message.toString()).isEqualTo(
                "Container image version tag (3.13.1) is not equal to the environment image version tag (3.13.2)"
        )
    }

    @Test
    fun `subnode image version tag is not set, then image version tag will be used`() {
        val subnodeImage = "chromaway/chromia-subnode"
        val config: ContainerNodeConfig = mock {
            on { containerImage } doReturn subnodeImage
            on { imageVersionTag } doReturn "3.13.2"
        }

        val actual = ContainerConfigFactory.getContainerImage(config)
        val expected = subnodeImage + ":" + config.imageVersionTag
        assertThat(actual).isEqualTo(expected)
    }

}
