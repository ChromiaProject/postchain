package net.postchain.containers.bpm.docker

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitMultiResponse
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitSingleResponse
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit


class DockerToolsIT {

    @Tag("docker")
    @Test
    fun `test async single response - don't get stuck on error`() {
        val dockerClient = DockerClientFactory.create()

        await.atMost(Duration(10, TimeUnit.SECONDS)).untilAsserted() {
            val exception = assertThrows<ProgrammerMistake> {
                dockerClient
                        .statsCmd("you-better-not-have-this-container-locally-running-12312445")
                        .withNoStream(true)
                        .asyncExecAwaitSingleResponse()
            }
            assertThat(exception).isNotNull()
        }
    }

    @Tag("docker")
    @Test
    fun `test async multi response - don't get stuck on error`() {
        val dockerClient = DockerClientFactory.create()

        await.atMost(Duration(10, TimeUnit.SECONDS)).untilAsserted() {
            dockerClient.logContainerCmd("you-better-not-have-this-container-locally-running-12312445")
                    .withStdOut(true)
                    .asyncExecAwaitMultiResponse({
                        throw RuntimeException("This is an error")
                    }, {})
        }
    }
}
