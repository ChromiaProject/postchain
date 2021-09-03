package net.postchain.ebft.heartbeat

import mu.KLogger

/**
 * Result Logger -- logs only NEW results
 * TODO: Make ResultLogger generic: replace Pair<Int, Boolean>? by <T>
 */
class ResultLogger {

    private var prevRes: Pair<Int, Boolean>? = null // step -> res

    fun log(res: Pair<Int, Boolean>, logger: KLogger, msg: () -> Any): Boolean {
        when {
            prevRes == null -> {
                // Log the first result
                prevRes = res
                debug(logger, msg)
            }
            prevRes != res -> {
                // Log only NEW result (another step or new result)
                prevRes = res
                debug(logger, msg)
            }
            else -> {
                // Don't log if nothing changes
            }
        }

        return res.second
    }

    fun registerOnly(res: Pair<Int, Boolean>) {
        prevRes = res
    }

    private fun debug(logger: KLogger, msg: () -> Any?) {
        if (logger.isDebugEnabled) {
            logger.debug(msg)
        }
    }

}