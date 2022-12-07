// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray

class TestPeerInfos {

    companion object {

        val peerInfo0 = PeerInfo(
                "127.0.0.1",
                9870,
                "03a301697bdfcd704313ba48e51d567543f2a182031efd6915ddc07bbcc4e16070".hexStringToByteArray()
        )

        val peerInfo1 = PeerInfo(
                "127.0.0.1",
                9871,
                "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F".hexStringToByteArray()
        )
    }

}