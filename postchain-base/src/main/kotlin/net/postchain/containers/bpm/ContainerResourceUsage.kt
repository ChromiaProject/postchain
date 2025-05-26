package net.postchain.containers.bpm

import com.github.dockerjava.api.model.Statistics
import net.postchain.containers.bpm.fs.ResourceLimitsInfo
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps resource statistics for a container.
 */
class ContainerResourceUsage(
        memoryUsage: Long? = null,
        memoryLimit: Long? = null,
        memoryUsagePercentage: Double? = null,
        cpuUsagePercentage: Double? = null,
        spaceUsageMiB: Long? = null,
        spaceLimitMiB: Long? = null,
        spaceUsagePercentage: Double? = null,
        spaceLeftMib: Long? = null,
        spaceUpdateTime: Long? = null,
) {

    var memoryUsage: Long? = memoryUsage
        private set
    var memoryLimit: Long? = memoryLimit
        private set
    var memoryUsagePercentage: Double? = memoryUsagePercentage
        private set
    var cpuUsagePercentage: Double? = cpuUsagePercentage
        private set
    var spaceUsageMiB: Long? = spaceUsageMiB
        private set
    var spaceLimitMiB: Long? = spaceLimitMiB
        private set
    var spaceUsagePercentage: Double? = spaceUsagePercentage
        private set
    var spaceLeftMib: Long? = spaceLeftMib
        private set
    var spaceUpdateTime: Long? = spaceUpdateTime

    companion object {

        /**
         * Calculates and creates object from docker and filesystem information.
         */
        fun create(containerStats: Statistics?, fsResourceLimits: ResourceLimitsInfo?): ContainerResourceUsage {

            val containerResourceUsage = ContainerResourceUsage()

            containerStats?.apply {
                memoryStats?.apply {
                    usage?.apply {
                        containerResourceUsage.memoryUsage = usage!! - (stats?.cache ?: 0)
                        if (limit != null && limit!! > 0) {
                            containerResourceUsage.memoryLimit = limit
                            containerResourceUsage.memoryUsagePercentage = ((containerResourceUsage.memoryUsage!!.toFloat() / containerResourceUsage.memoryLimit!!) * 10000).toInt() / 100.0
                        }
                    }
                }

                if (
                        cpuStats?.cpuUsage?.totalUsage != null &&
                        cpuStats?.systemCpuUsage != null &&
                        preCpuStats?.cpuUsage?.totalUsage != null &&
                        preCpuStats?.systemCpuUsage != null
                ) {
                    val cpuDelta = cpuStats.cpuUsage!!.totalUsage!! - preCpuStats.cpuUsage!!.totalUsage!!
                    val systemCpuDelta = cpuStats.systemCpuUsage!! - preCpuStats.systemCpuUsage!!
                    val numberOfCpus = cpuStats?.cpuUsage?.percpuUsage?.size ?: cpuStats?.onlineCpus?.toInt()
                    if (systemCpuDelta > 0 && numberOfCpus != null) {
                        containerResourceUsage.cpuUsagePercentage = ((cpuDelta.toFloat() / systemCpuDelta) * numberOfCpus * 10000).toInt() / 100.0
                    }
                }
            }

            fsResourceLimits?.apply {
                containerResourceUsage.spaceUsageMiB = spaceUsedMiB
                containerResourceUsage.spaceLimitMiB = spaceHardLimitMiB
                containerResourceUsage.spaceUsagePercentage = min(((spaceUsedMiB.toDouble() / spaceHardLimitMiB) * 10000).toInt() / 100.0, 100.0)
                containerResourceUsage.spaceLeftMib = max(spaceHardLimitMiB - spaceUsedMiB, 0)
            }

            return containerResourceUsage
        }
    }

    fun copySpacePropertiesFrom(lastResourceUsage: ContainerResourceUsage) {
        this.spaceUsageMiB = lastResourceUsage.spaceUsageMiB
        this.spaceLimitMiB = lastResourceUsage.spaceLimitMiB
        this.spaceUsagePercentage = lastResourceUsage.spaceUsagePercentage
        this.spaceLeftMib = lastResourceUsage.spaceLeftMib
        this.spaceUpdateTime = lastResourceUsage.spaceUpdateTime
    }
}
