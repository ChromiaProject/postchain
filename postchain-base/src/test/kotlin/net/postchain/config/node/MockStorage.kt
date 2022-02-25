package net.postchain.config.node

import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import net.postchain.base.PeerInfo
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.AppContext
import net.postchain.core.ExecutionContext

object MockStorage {

    fun mockAppContext(peerInfos: Array<PeerInfo> = emptyArray()): Storage {
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

    fun mockEContext(chainId: Long): Storage {
        val mockDb: DatabaseAccess = mock {
            on { getLastBlockHeight(any()) } doReturn 0
            on { findNextConfigurationHeight(any(), any()) } doReturn 0
        }

        val mockContext: ExecutionContext = mock {
            on { chainID } doReturn chainId
            on { getInterface(DatabaseAccess::class.java) } doReturn mockDb
        }

        return mock {
            on { openReadConnection(any()) } doReturn mockContext
            on { openWriteConnection(any()) } doReturn mockContext
        }
    }

}