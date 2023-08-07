// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.server.cli

import net.postchain.server.LazyPostchainNodeProvider
import net.postchain.server.grpc.SubnodeServiceGrpcImpl
import net.postchain.server.service.SubnodeService

class CommandRunSubNode : CommandRunServerBase("run-subnode", "Start postchain sub node server") {

    override fun run() {
        val nodeProvider = LazyPostchainNodeProvider(debug)
        services.add(SubnodeServiceGrpcImpl(SubnodeService(nodeProvider)))
        runServer(nodeProvider)
    }
}
