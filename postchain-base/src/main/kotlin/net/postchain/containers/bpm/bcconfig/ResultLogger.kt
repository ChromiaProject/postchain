package net.postchain.containers.bpm.bcconfig

import mu.KLogger

internal data class LogResult(
        val step: Int,
        val result: Boolean
)

/**
 * Result Logger -- logs only NEW results
 */
internal class ResultLogger(val logger: KLogger) {

    private var prevRes: LogResult? = null // step -> result

    fun log(result: LogResult, msg: () -> Any): Boolean {
        when {
            prevRes == null -> {
                prevRes = result // Log the first result
                debug(logger, msg)
            }
            prevRes != result -> {
                prevRes = result // Log only NEW result (another step or new result)
                debug(logger, msg)
            }
            else -> {
                // Don't log if nothing changes
            }
        }

        return result.result
    }

    fun registerStep(result: LogResult) {
        prevRes = result
    }

    private fun debug(logger: KLogger, msg: () -> Any?) {
        if (logger.isDebugEnabled) {
            logger.debug(msg)
        }
    }

}