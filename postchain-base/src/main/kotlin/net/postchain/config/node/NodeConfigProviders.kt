// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

enum class NodeConfigProviders(vararg val aliases: String) {

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from *.properties file
     */
    Legacy("legacy", "explicit"),

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from database
     */
    Manual("manual"),

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from system blockchain (chain0)
     */
    Managed("managed");


    companion object {
        @JvmStatic
        fun fromAlias(str: String): NodeConfigProviders? {
            return values().firstOrNull { it.aliases.any { alias -> alias == str.toLowerCase() } }
        }
    }
}