// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import net.postchain.server.LazyPostchainNodeProvider
import net.postchain.server.grpc.InitServiceGrpcImpl

class CommandRunSubNode : CommandRunServerBase("run-subnode", "Start postchain sub node server") {

    override fun run() {
        val nodeProvider = LazyPostchainNodeProvider()
        services.add(InitServiceGrpcImpl(nodeProvider))
        runServer(nodeProvider)
    }
}
