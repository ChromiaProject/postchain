package net.postchain.client.exception

import net.postchain.client.request.Endpoint
import net.postchain.common.exception.UserMistake
import org.http4k.core.Status

class ClientError(val context: String, val status: Status, val errorMessage: String, val endpoint: Endpoint)
    : UserMistake("$context: $status ${if (errorMessage.isBlank()) "" else "$errorMessage "}from ${endpoint.url}")
