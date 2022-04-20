package net.postchain.ebft.heartbeat

import java.lang.Thread.sleep


fun awaitHeartbeatHandler(hbListener: HeartbeatListener, heartbeatConfig: HeartbeatConfig): (Long, () -> Boolean) -> Boolean {
    return hbCheck@{ timestamp, exitCondition ->
        while (!hbListener.checkHeartbeat(timestamp)) {
            if (exitCondition()) {
                return@hbCheck false
            }
            sleep(heartbeatConfig.sleepTimeout)
        }
        true
    }
}