package net.postchain.integrationtest.managedmode

import net.postchain.base.BlockchainRid
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXModule
import net.postchain.util.TestKLogging


open class DirectoryGTXModule : GTXModule {
    companion object : TestKLogging(LogLevel.DEBUG)

    //    val conf: Unit
    val opmap: Map<String, (Unit, ExtOpData) -> Transactor> = mapOf()
    val querymap: Map<String, (Unit, EContext, Gtv) -> Gtv> = mapOf(
            "nm_get_containers" to ::queryGetContainersToRun,
            "nm_get_blockchains_for_container" to ::queryGetBlockchainsForContainer,
            "nm_get_container_limits" to ::queryGetResourceLimitForContainer
    )

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!(Unit, opData)
        } else {
            throw UserMistake("Unknown operation: ${opData.opName}")
        }
    }

    override fun getOperations(): Set<String> {
        return opmap.keys
    }

    override fun getQueries(): Set<String> {
        return querymap.keys
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        if (name in querymap) {
            return querymap[name]!!(Unit, ctxt, args)
        } else throw UserMistake("Unknown query: $name")
    }


    override fun initializeDB(ctx: EContext) {}

    private var containers: List<String> = emptyList()
    private var blockchainContainerMap = mutableMapOf<String, List<BlockchainRid>>()
    private var resourceContainerMap = mutableMapOf<String, Map<String, GtvInteger>>()

    fun setNewContainers(newContainers: List<String>) {
        containers = newContainers
    }

    fun putBlockchainsInContainer(containerID: String, blockchains: List<BlockchainRid>) {
        blockchainContainerMap.put(containerID, blockchains)
    }

    fun setNewResourceLimitForContainer(containerID: String, resource: Map<String, GtvInteger>) {
        resourceContainerMap.put(containerID, resource)
    }

    fun getContainersToRun(): List<GtvString> {
        return containers.map { gtv(it) }
    }

    fun getBlockchainsForContainer(containerID: String): List<GtvByteArray>? {
        return blockchainContainerMap[containerID]?.map { gtv(it) }
    }

    fun getResourceLimitForContainer(containerID: String): Map<String, GtvInteger>? {
        return resourceContainerMap[containerID]
    }


    fun queryGetContainersToRun(unit: Unit, eContext: EContext, args: Gtv): Gtv {
        logger.log { "Query: nm_get_containers" }
        return gtv(getContainersToRun())
    }

    fun queryGetBlockchainsForContainer(unit: Unit, eContext: EContext, args: Gtv): Gtv {
        logger.log { "Query: nm_get_blockchains_for_container" }
        val containerID = TestModulesHelper.argContainerID(args)
        val bcs = getBlockchainsForContainer(containerID)
        return bcs?.let { gtv(it) } ?: GtvNull
    }

    fun queryGetResourceLimitForContainer(unit: Unit, eContext: EContext, args: Gtv): Gtv {
        val containerID = TestModulesHelper.argContainerID(args)
        val resourceLimit = getResourceLimitForContainer(containerID)
        logger.log { "Query: nm_get_resource_limit" }
        return return resourceLimit?.let { gtv(it) } ?: GtvNull
    }
}