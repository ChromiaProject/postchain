package net.postchain.client.exception

class NodesDisagree(message: String)
    : ClientError(context = "Nodes disagree", status = null, errorMessage = message, endpoint = null)
