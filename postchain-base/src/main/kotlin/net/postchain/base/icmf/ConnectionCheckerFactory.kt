package net.postchain.base.icmf

import net.postchain.core.UserMistake

/**
 * Can create different types of [ConnectionChecker] from the given configuration
 */
object ConnectionCheckerFactory {

    /**
     * Currently we can only handle the "number" strategy for "icmflistener" setting but:
     * FUTURE WORK: Olle: The plan is to try to instantiate the given class and use it as a [ConnectionChecker]
     *
     * @return a [ConnectionChecker] from the listener ICMF configuration from the chains configuration.
     */
    fun build(chainIid: Long, icmfListenerConf: String): ConnectionChecker {
            val listeningLevel: Int? = icmfListenerConf!!.toIntOrNull() // Try to interpret the conf as a number first
            if (listeningLevel != null) {
                return LevelConnectionChecker(chainIid, listeningLevel!!)
            } else {
                // FUTURE WORK: Olle: We could implement something like this:
                //val customConnectionChecker = xxx as ConnectionChecker
                //internalListenerChains[chainIid] = customConnectionChecker
                throw UserMistake("At this point we cannot handle custom listener chain connection checkers, icmflistener must be set to \"number\" but was \"$icmfListenerConf\".")
            }
    }
}