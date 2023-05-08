// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

enum class DiagnosticProperty(val prettyName: String) {

    VERSION("version"),

    PUB_KEY("pub-key"),
    BLOCKCHAIN_INFRASTRUCTURE("blockchain-infra"),

    BLOCKCHAIN("blockchain"),
    BLOCKCHAIN_RID("brid"),
    BLOCKCHAIN_NODE_TYPE("node-type"),
    BLOCKCHAIN_LAST_HEIGHT("height"),
    BLOCKCHAIN_NODE_PEERS("peers"),
    BLOCKCHAIN_NODE_STATUS("node-status"),
    BLOCKCHAIN_NODE_PEERS_STATUSES("node-peers-statuses"),

    // Containers
    CONTAINER_NAME("container-name"),
    CONTAINER_ID("container-id"),
    ERROR("error"),

    @Deprecated("POS-90")
    PEERS_TOPOLOGY("peers-topology");

    infix fun withLazyValue(value: () -> Any?) = this to LazyDiagnosticValue(value)
    infix fun withValue(value: Any?) = this to EagerDiagnosticValue(value)
}

enum class DpNodeType(val prettyName: String) {
    NODE_TYPE_VALIDATOR("Validator"),
    NODE_TYPE_REPLICA("Replica"),
    NODE_TYPE_HISTORIC_REPLICA("Historic Replica")
}