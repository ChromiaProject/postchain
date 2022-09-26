// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.d1.anchor.ClusterAnchorReceiver

interface IcmfSpecialTxExtension {
    val icmfReceiver: ClusterAnchorReceiver
}
