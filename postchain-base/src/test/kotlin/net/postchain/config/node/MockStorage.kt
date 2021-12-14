package net.postchain.config.node

import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import net.postchain.base.PeerInfo
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.AppContext

object MockStorage {

    fun mock(peerInfos: Array<PeerInfo>): Storage {
        val mockDb: DatabaseAccess = mock {
            on { getPeerInfoCollection(any()) } doReturn peerInfos
        }

        val mockContext: AppContext = mock {
            on { getInterface(DatabaseAccess::class.java) } doReturn mockDb
        }

        return mock {
            on { openWriteConnection() } doReturn mockContext
            on { openReadConnection() } doReturn mockContext
        }
    }

}