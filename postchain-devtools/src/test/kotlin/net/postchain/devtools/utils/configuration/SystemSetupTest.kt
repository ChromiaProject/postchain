package net.postchain.devtools.utils.configuration

import net.postchain.devtools.utils.configuration.pre.BlockchainPreSetup
import net.postchain.devtools.utils.configuration.pre.SystemPreSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemSetupTest {


    val nodeNr1 = NodeSeqNumber(1)
    val nodeNr2 = NodeSeqNumber(2)
    val nodeNr3 = NodeSeqNumber(3)
    val nodeNr4 = NodeSeqNumber(4)
    val nodeNr5 = NodeSeqNumber(5)
    val nodeNr6 = NodeSeqNumber(6)

    val chainId1 = 1
    val chainId2 = 2
    val chainId3 = 3
    val chainId4 = 4

    val bcSetup1 = BlockchainPreSetup.simpleBuild(chainId1, listOf(nodeNr1, nodeNr2))
    val bcSetup2 = BlockchainPreSetup.simpleBuild(chainId2, listOf(nodeNr3, nodeNr4))
    val bcSetup3 = BlockchainPreSetup.buildWithDependencies(chainId3, listOf(nodeNr5), setOf(chainId1)) // chain3 depends on chain1
    val bcSetup4 = BlockchainPreSetup.buildWithDependencies(chainId4, listOf(nodeNr6), setOf(chainId3)) // chain4 depends on chain3

    val bcPreSetupMap = mapOf<Int, BlockchainPreSetup>(
            chainId1 to bcSetup1,
            chainId2 to bcSetup2,
            chainId3 to bcSetup3,
            chainId4 to bcSetup4)

    val sysPreSetup = SystemPreSetup(bcPreSetupMap)

    @Test
    fun checkNoDeps() {

        val sysSetup = SystemSetupFactory.buildSystemSetup(sysPreSetup)
        val blockchainMap = sysSetup.blockchainMap

        val foundSignerBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldSign(nodeNr1, blockchainMap)

        assertEquals(1, foundSignerBcs.size)
        val chainFound = foundSignerBcs.first()
        assertEquals(chainId1, chainFound)

        val foundReadBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldRead(blockchainMap[chainFound]!!.chainDependencies, blockchainMap)
        assertEquals(0, foundReadBcs.size)
    }

    @Test
    fun checkOneDep() {

        val sysSetup = SystemSetupFactory.buildSystemSetup(sysPreSetup)
        val blockchainMap = sysSetup.blockchainMap

        val foundSignerBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldSign(nodeNr5, blockchainMap)

        assertEquals(1, foundSignerBcs.size)
        val chainFound = foundSignerBcs.first()
        assertEquals(chainId3, chainFound)

        val foundReadBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldRead(blockchainMap[chainFound]!!.chainDependencies, blockchainMap)
        assertEquals(1, foundReadBcs.size)
        val depChainFound = foundReadBcs.first()
        assertEquals(chainId1, depChainFound)
    }


    @Test
    fun checkManyDeps() {
        val sysSetup = SystemSetupFactory.buildSystemSetup(sysPreSetup)
        val blockchainMap = sysSetup.blockchainMap


        val foundSignerBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldSign(nodeNr6, blockchainMap)

        assertEquals(1, foundSignerBcs.size)
        val chainFound = foundSignerBcs.first()
        assertEquals(chainId4, chainFound)

        val foundReadBcs = SystemSetupFactory.calculateWhatBlockchainsTheNodeShouldRead(blockchainMap[chainFound]!!.chainDependencies, blockchainMap)
        assertEquals(2, foundReadBcs.size)
        assertTrue(foundReadBcs.contains(chainId1))
        assertTrue(foundReadBcs.contains(chainId3))
    }
}