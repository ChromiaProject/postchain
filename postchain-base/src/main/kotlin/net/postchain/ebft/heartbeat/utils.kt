package net.postchain.ebft.heartbeat

import kotlinx.coroutines.delay


fun awaitHeartbeatHandler(hbListener: HeartbeatListener, heartbeatConfig: HeartbeatConfig): suspend (Long, () -> Boolean) -> Boolean {
    return hbCheck@{ timestamp, exitCondition ->
        while (!hbListener.checkHeartbeat(timestamp)) {
            if (exitCondition()) {
                return@hbCheck false
            }
            delay(heartbeatConfig.sleepTimeout)
        }
        true
    }
}