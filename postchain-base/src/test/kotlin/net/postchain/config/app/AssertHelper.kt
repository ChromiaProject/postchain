package net.postchain.config.app

import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo

object AssertsHelper {

    fun assertIsEmptyOrEqualsToEnvVar(actual: String, envVar: String) {
        val v = System.getenv(envVar)
        if (v != null) {
            assertk.assert(actual).isEqualTo(v)
        } else {
            assertk.assert(actual).isEmpty()
        }
    }

    fun assertIsDefaultOrEqualsToEnvVar(actual: String, default: String, envVar: String) {
        val v = System.getenv(envVar)
        if (v != null) {
            assertk.assert(actual).isEqualTo(v)
        } else {
            assertk.assert(actual).isEqualTo(default)
        }
    }

}