package net.postchain.client.exception

import net.postchain.client.request.Endpoint
import net.postchain.common.exception.UserMistake
import org.http4k.core.Status

open class ClientError(val context: String, val status: Status?, val errorMessage: String, val endpoint: Endpoint?)
    : UserMistake("$context: ${if (status != null) "$status " else ""} ${if (errorMessage.isBlank()) "" else "$errorMessage "}${if (endpoint != null) "from ${endpoint.url}" else ""}")
