package net.postchain.client.impl

import org.http4k.core.Status
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun isSuccess(status: Status) = status == Status.OK

fun isClientFailure(status: Status) =
        status == Status.BAD_REQUEST
                || status == Status.NOT_FOUND
                || status == Status.CONFLICT
                || status == Status.REQUEST_ENTITY_TOO_LARGE

fun isServerFailure(status: Status) =
        (status == Status.UNKNOWN_HOST && status.description == Status.UNKNOWN_HOST.description) // workaround for https://github.com/http4k/http4k/issues/845
                || status == Status.INTERNAL_SERVER_ERROR
                || status == Status.SERVICE_UNAVAILABLE

fun unreachableDuration(status: Status): Duration =
        if (status == Status.UNKNOWN_HOST && status.description == Status.UNKNOWN_HOST.description) // workaround for https://github.com/http4k/http4k/issues/845
            5.minutes
        else if (status == Status.INTERNAL_SERVER_ERROR)
            10.seconds
        else if (status == Status.SERVICE_UNAVAILABLE)
            2.seconds
        else
            ZERO
