package net.postchain.config.node

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import net.postchain.base.PeerInfo
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.AppContext

object MockStorage {

    fun mock(peerInfos: Array<PeerInfo> = emptyArray()): Storage {
        val mockDb: DatabaseAccess = mock {
            on { getPeerInfoCollection(any()) } doReturn peerInfos
        }

        val mockContext: AppContext = mock {
            on { getInterface(DatabaseAccess::class.java) } doReturn mockDb
        }

        return mock {
            on { openReadConnection() } doReturn mockContext
            on { openWriteConnection() } doReturn mockContext
        }
    }

}