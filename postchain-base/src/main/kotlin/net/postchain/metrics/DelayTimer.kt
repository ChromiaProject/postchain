package net.postchain.metrics

class DelayTimer {
    var totalDelayTime: Long = 0L
        private set

    private var startTime: Long = 0L

    fun start() {
        if (startTime == 0L) startTime = System.nanoTime()
    }

    fun stop() {
        if (startTime > 0) {
            totalDelayTime += System.nanoTime() - startTime
            startTime = 0
        }
    }

    fun add(time: Long) {
        totalDelayTime += time
    }
}