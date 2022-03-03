package net.postchain.network.mastersub.subnode

import net.postchain.network.common.NodeConnection
import net.postchain.network.mastersub.MsMessageHandler

typealias SubConnection = NodeConnection<MsMessageHandler, SubConnectionDescriptor>