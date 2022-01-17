package net.postchain.network.mastersub.master

import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler

typealias MasterConnection = NodeConnection<MsMessageHandler, MasterConnectionDescriptor>