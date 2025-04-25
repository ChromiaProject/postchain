package net.postchain.containers

import kotlin.time.Duration

data class ContainerRateLimit(
        val periodLength: Duration,
        val rateLimit: Long
)
