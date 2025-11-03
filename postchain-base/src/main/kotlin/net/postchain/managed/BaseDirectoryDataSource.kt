package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.containers.ContainerRateLimit
import net.postchain.containers.bpm.ContainerImageInfo
import net.postchain.containers.bpm.ContainerJarExtensionInfo
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.resources.ResourceLimitFactory
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.managed.query.QueryRunner
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

open class BaseDirectoryDataSource(
        queryRunner: QueryRunner,
        appConfig: AppConfig,
) : BaseManagedNodeDataSource(queryRunner, appConfig), DirectoryDataSource {

    override fun getContainersToRun(): List<String>? = try {
        val res = query(
                "nm_get_containers",
                buildArgs("pubkey" to gtv(appConfig.pubKeyByteArray))
        )
        res.asArray().map { it.asString() }
    } catch (_: UserMistake) {
        // This can fail if we are the genesis node before having initialized the network, since we are not registered as node yet
        listOf()
    }

    override fun getAllContainers(): List<String>? {
        if (nmApiVersion >= 24) {
            try {
                val res = query(
                        "nm_get_all_containers",
                        buildArgs()
                )
                return res.asArray().map { it.asString() }
            } catch (_: UserMistake) { // this can fail if we are the genesis node before having initialized the network, since we are not registered as node yet
            }
        }
        return null
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        return if (nmApiVersion >= 3) {
            query(
                    "nm_get_container_for_blockchain",
                    buildArgs("blockchain_rid" to gtv(brid.data))
            ).asString()
        } else {
            throw Exception("Directory1 v.$nmApiVersion doesn't support 'nm_get_container_for_blockchain' query")
        }
    }

    override fun getBlockchainContainersForNode(brid: BlockchainRid): List<String> = try {
        if (nmApiVersion >= 14) {
            query(
                    "nm_get_blockchain_containers_for_node",
                    buildArgs("node_id" to gtv(appConfig.pubKeyByteArray), "blockchain_rid" to gtv(brid.data))
            ).asArray().map { it.asString() }
        } else {
            listOf(getContainerForBlockchain(brid))
        }
    } catch (e: UserMistake) {
        logger.error { "Can't find containers for blockchain ${brid.data.wrap()}: ${e.message}" }
        listOf()
    }

    override fun getResourceLimitForContainer(container: String): ContainerResourceLimits = try {
        val resourceLimits = query(
                "nm_get_container_limits",
                buildArgs("name" to gtv(container))
        ).asDict().mapValues { (_, v) ->
            v.asInteger()
        }.mapNotNull {
            ResourceLimitFactory.fromPair(it.toPair())
        }.toTypedArray()

        ContainerResourceLimits(*resourceLimits)

    } catch (e: UserMistake) {
        logger.error { "Can't find resource limits for container $container: ${e.message}" }
        ContainerResourceLimits.default()
    }

    override fun getImageForContainer(container: String): ContainerImageInfo? {
        try {
            if (nmApiVersion < 20) return null

            val response = query(
                    "nm_get_container_image",
                    buildArgs("name" to gtv(container))
            )

            return if (response.isNull()) null else
                response.toObject<ContainerImageInfo>()

        } catch (e: UserMistake) {
            logger.error { "Can't find image for container $container: ${e.message}" }
            return null
        }
    }

    override fun getImageForContainerOrDefault(container: String): ContainerImageInfo? {
        try {
            if (nmApiVersion < 22) return getImageForContainer(container)

            val response = query(
                    "nm_get_container_image_or_default",
                    buildArgs("name" to gtv(container))
            )

            return if (response.isNull()) null else
                response.toObject<ContainerImageInfo>()

        } catch (e: UserMistake) {
            logger.error { "Can't find image for container $container: ${e.message}" }
            return null
        }
    }

    override fun getContainerCreationTime(container: String): Instant? {
        try {
            if (nmApiVersion < 23) return null

            val response = query(
                    "nm_get_container_creation_time",
                    buildArgs("name" to gtv(container))
            )

            return if (response.isNull()) null else
                Instant.ofEpochMilli(response.asInteger())

        } catch (e: UserMistake) {
            logger.error { "Can't find creation time for container $container: ${e.message}" }
            return null
        }
    }

    override fun getContainerRateLimits(container: String): Map<String, ContainerRateLimit> {
        try {
            if (nmApiVersion < 23) return mapOf()

            val response = query(
                    "nm_get_container_rate_limits",
                    buildArgs("name" to gtv(container))
            )

            return response.asDict().mapValues {
                ContainerRateLimit(
                        periodLength = it.value["period_length_millis"]!!.asInteger().milliseconds,
                        rateLimit = it.value["rate_limit"]!!.asInteger()
                )
            }

        } catch (e: UserMistake) {
            logger.error { "Can't find rate limits for container $container: ${e.message}" }
            return mapOf()
        }
    }

    override fun getContainerConfiguration(container: String): GtvDictionary {
        return if (nmApiVersion >= 26) {
            val response = query(
                    "nm_get_container_configuration",
                    buildArgs("name" to gtv(container))
            )

            if (response.isNull()) {
                GtvDictionary.build(emptyMap())
            } else {
                response as GtvDictionary
            }
        } else GtvDictionary.build(emptyMap())
    }

    override fun getJarExtensionsForContainer(container: String): List<ContainerJarExtensionInfo> {
        try {
            if (nmApiVersion < 27) return listOf()

            val response = query(
                    "nm_get_container_jar_extensions",
                    buildArgs("name" to gtv(container))
            )

            return response.asArray().map { it.toObject<ContainerJarExtensionInfo>() }

        } catch (e: UserMistake) {
            logger.error { "Can't find JAR extensions for container $container: ${e.message}" }
            return listOf()
        }
    }

    override fun getJarExtension(extension: String): ByteArray? {
        return try {
            if (nmApiVersion < 27) return null

            query(
                    "nm_get_jar_extension",
                    buildArgs("name" to gtv(extension))
            ).asByteArray()
        } catch (e: UserMistake) {
            logger.error { "Can't find JAR extension $extension: ${e.message}" }
            null
        }
    }
}
