package net.postchain.api.rest.controller.http4k

import org.http4k.core.Response
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun Response.expires(duration: Duration, clock: Clock) =
        replaceHeader("Expires", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(clock).plusSeconds(duration.seconds)))
