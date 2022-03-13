// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

interface IcmfReceiver {
    fun getRelevantPipes(): List<IcmfPipe>
}

