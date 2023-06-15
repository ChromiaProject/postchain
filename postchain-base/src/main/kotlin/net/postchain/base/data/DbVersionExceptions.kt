package net.postchain.base.data

import net.postchain.common.exception.UserMistake

class DbVersionUpgradeDisallowedException(message: String, cause: Exception? = null)
    : UserMistake(message, cause)

class DbVersionDowngradeDisallowedException(message: String, cause: Exception? = null)
    : UserMistake(message, cause)
