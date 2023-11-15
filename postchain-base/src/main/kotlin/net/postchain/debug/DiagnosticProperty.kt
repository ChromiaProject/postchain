// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

enum class DiagnosticProperty(val prettyName: String) {

    VERSION("version"),

    INFRASTRUCTURE_NAME("infrastructure"),
    INFRASTRUCTURE_VERSION("infrastructure-version"),

    PUB_KEY("pub-key"),

    BLOCKCHAIN("blockchain"),
    BLOCKCHAIN_RID("brid"),
    BLOCKCHAIN_NODE_STATE("node-state"),
    BLOCKCHAIN_NODE_TYPE("node-type"),
    BLOCKCHAIN_LAST_HEIGHT("height"),
    BLOCKCHAIN_NODE_PEERS("peers"),
    BLOCKCHAIN_NODE_STATUS("node-status"),
    BLOCKCHAIN_NODE_PEERS_STATUSES("node-peers-statuses"),

    // Containers
    CONTAINER_NAME("container-name"),
    CONTAINER_ID("container-id"),

    ERROR("error"),

    BLOCK_STATS("block-statistics"),
    BLOCK_RID("rid"),
    BLOCK_HEIGHT("height"),
    BLOCK_BUILDER("builder"),
    BLOCK_SIGNER("signer"),

    @Deprecated("POS-90")
    PEERS_TOPOLOGY("peers-topology");

    infix fun withLazyValue(value: () -> Any?) = this to LazyDiagnosticValue(value)
    infix fun withValue(value: Any?) = this to EagerDiagnosticValue(value)
}

enum class DpNodeType(val prettyName: String) {
    NODE_TYPE_VALIDATOR("Validator"),
    NODE_TYPE_REPLICA("Replica"),
    NODE_TYPE_HISTORIC_REPLICA("Historic Replica"),
    NODE_TYPE_FORCE_READ_ONLY("Force read only")
}

enum class DpBlockchainNodeState() {
    RUNNING_HISTORIC,
    RUNNING_VALIDATOR,
    RUNNING_READ_ONLY,
    PAUSED_READ_ONLY,
    FORCED_READ_ONLY,
    IMPORTING_FORCED_READ_ONLY,
    UNARCHIVING_FORCED_READ_ONLY,
    UNARCHIVING_VALIDATOR,
    UNARCHIVING_READ_ONLY
}
