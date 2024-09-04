package net.postchain.config.app

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo

object AssertsHelper {

    fun assertIsEmptyOrEqualsToEnvVar(actual: String, envVar: String) {
        val v = System.getenv(envVar)
        if (v != null) {
            assertThat(actual).isEqualTo(v)
        } else {
            assertThat(actual).isEmpty()
        }
    }

    fun assertIsDefaultOrEqualsToEnvVar(actual: String, default: String, envVar: String) {
        val v = System.getenv(envVar)
        if (v != null) {
            assertThat(actual).isEqualTo(v)
        } else {
            assertThat(actual).isEqualTo(default)
        }
    }

}