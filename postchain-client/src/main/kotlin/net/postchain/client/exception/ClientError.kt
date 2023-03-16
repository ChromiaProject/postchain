package net.postchain.client.exception

import net.postchain.common.exception.UserMistake

class ClientError(message: String) : UserMistake(message)
