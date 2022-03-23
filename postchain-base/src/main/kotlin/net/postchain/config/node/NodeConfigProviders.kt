// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

enum class NodeConfigProviders {

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from *.properties file
     */
    Legacy,

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from *.properties file.
     *
     * Similar to Legacy provider but with a different PeerInfo collection format.
     */
    File,

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from database
     */
    Manual,

    /**
     * PeerInfo collection and other PostchainNode parameters are obtained
     * from system blockchain (chain0)
     */
    Managed
}