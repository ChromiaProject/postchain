package net.postchain.ebft.heartbeat

import mu.KLogger

/**
 * Result Logger -- logs only NEW results
 * TODO: Make ResultLogger generic: replace Pair<Int, Boolean>? by <T>
 */
class ResultLogger {

    private var prevRes: Pair<Int, Boolean>? = null // step -> result

    fun log(stepResult: Pair<Int, Boolean>, logger: KLogger, msg: () -> Any): Boolean {
        when {
            prevRes == null -> {
                // Log the first result
                prevRes = stepResult
                debug(logger, msg)
            }
            prevRes != stepResult -> {
                // Log only NEW result (another step or new result)
                prevRes = stepResult
                debug(logger, msg)
            }
            else -> {
                // Don't log if nothing changes
            }
        }

        return stepResult.second
    }

    fun registerOnly(stepResult: Pair<Int, Boolean>) {
        prevRes = stepResult
    }

    private fun debug(logger: KLogger, msg: () -> Any?) {
        if (logger.isDebugEnabled) {
            logger.debug(msg)
        }
    }

}