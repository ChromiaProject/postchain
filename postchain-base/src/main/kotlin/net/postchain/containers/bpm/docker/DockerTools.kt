package net.postchain.containers.bpm.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.AsyncDockerCmd
import com.github.dockerjava.api.command.ListContainersCmd
import com.github.dockerjava.api.model.Container
import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.bpm.POSTCHAIN_MASTER_PUBKEY
import net.postchain.containers.infra.ContainerNodeConfig


object DockerTools : KLogging() {

    fun Container.hasName(containerName: String): Boolean {
        return names?.contains("/$containerName") ?: false // Prefix '/'
    }

    fun containerName(container: Container): String {
        return container.names?.get(0) ?: ""
    }

    fun shortContainerId(containerId: String?): String? {
        return containerId?.take(12)
    }

    fun DockerClient.listSubContainersCmd(containerNodeConfig: ContainerNodeConfig): ListContainersCmd {
        return listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(mapOf(POSTCHAIN_MASTER_PUBKEY to containerNodeConfig.masterPubkey))
    }

    /**
     * Executes an asynchronous command and waits for a single response.
     *
     * @return The last result of the execution, or null if no result is returned.
     */
    fun <CMD_T : AsyncDockerCmd<CMD_T, A_RES_T>?, A_RES_T> AsyncDockerCmd<CMD_T, A_RES_T>.asyncExecAwaitSingleResponse(): A_RES_T? {
        var result: A_RES_T? = null
        var error: ProgrammerMistake? = null
        asyncExecAwaitMultiResponse(onNext = {
            result = it
        }, onError = {
            error = if (it is Exception) {
                ProgrammerMistake("Async docker call failed: ${it.message}", it)
            } else {
                ProgrammerMistake("Async docker call failed")
            }
            logger.error("Async docker call failed: ${it?.message}")
        })

        if (error != null) {
            throw error
        }

        return result
    }

    /**
     * Executes an asynchronous command and processes multiple responses.
     *
     * @param onNext The lambda function to be called for every response received from the asynchronous Docker command.
     */
    fun <CMD_T : AsyncDockerCmd<CMD_T, A_RES_T>?, A_RES_T> AsyncDockerCmd<CMD_T, A_RES_T>.asyncExecAwaitMultiResponse(
            onNext: (A_RES_T) -> Unit,
            onError: (Throwable?) -> Unit,
    ) {
        exec(object : ResultCallback.Adapter<A_RES_T>() {
            override fun onNext(item: A_RES_T) {
                onNext(item)
            }

            override fun onError(throwable: Throwable?) {
                onError(throwable)
                this.close()
            }
        }
        ).awaitCompletion()
    }
}
