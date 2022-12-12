package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.AppContext
import net.postchain.core.ExecutionContext
import net.postchain.core.Storage
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

object MockStorage {

    class Mock(val db: DatabaseAccess, val context: AppContext, val storage: Storage)

    fun mockAppContext(peerInfos: Array<PeerInfo> = emptyArray()): Mock {
        val mockDb: DatabaseAccess = mock {
            on { getPeerInfoCollection(any()) } doReturn peerInfos
            on { findPeerInfo(any(), eq(null), eq(null), any()) } doReturn arrayOf()
            on { findPeerInfo(any(), eq(null), eq(null), eq(null)) } doReturn arrayOf()
        }

        val mockContext: AppContext = mock {
            on { getInterface(DatabaseAccess::class.java) } doReturn mockDb
        }

        return Mock(
                mockDb,
                mockContext,
                mock {
                    on { openReadConnection() } doReturn mockContext
                    on { openWriteConnection() } doReturn mockContext
                })
    }

    fun mockEContext(chainId: Long): Mock {
        val mockDb: DatabaseAccess = mock {
            on { getLastBlockHeight(any()) } doReturn 0
            on { findNextConfigurationHeight(any(), any()) } doReturn 0
        }

        val mockContext: ExecutionContext = mock {
            on { chainID } doReturn chainId
            on { getInterface(DatabaseAccess::class.java) } doReturn mockDb
        }

        return Mock(
                mockDb,
                mockContext,
                mock {
                    on { openReadConnection(any()) } doReturn mockContext
                    on { openWriteConnection(any()) } doReturn mockContext
                }
        )
    }
}