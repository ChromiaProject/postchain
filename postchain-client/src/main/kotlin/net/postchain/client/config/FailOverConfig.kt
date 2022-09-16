package net.postchain.client.config

const val FAIL_OVER_INTERVAL = 500L //ms

data class FailOverConfig(
    val attemptsPerEndpoint: Int = 5,
    val attemptInterval: Long = FAIL_OVER_INTERVAL,
)
