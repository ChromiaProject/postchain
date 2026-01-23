package net.postchain.ebft.syncmanager.validator

import mu.KLogging
import net.postchain.base.configuration.KEY_REVOLT_EXPONENTIAL_DELAY_BASE
import net.postchain.base.configuration.KEY_REVOLT_EXPONENTIAL_DELAY_INITIAL
import net.postchain.base.configuration.KEY_REVOLT_EXPONENTIAL_DELAY_MAX
import net.postchain.base.configuration.KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE
import net.postchain.base.configuration.KEY_REVOLT_FAST_REVOLT_STATUS_TIMEOUT
import net.postchain.base.configuration.KEY_REVOLT_TIMEOUT
import net.postchain.base.configuration.KEY_REVOLT_WHEN_SHOULD_BUILD_BLOCK
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class RevoltConfigurationData(
    @param:RawGtv
    val rawGtv: Gtv,
    @param:Name(KEY_REVOLT_TIMEOUT)
    @param:DefaultValue(defaultLong = 10_000)
    val timeout: Long,
    @param:Name(KEY_REVOLT_EXPONENTIAL_DELAY_INITIAL)
    @param:DefaultValue(defaultLong = 1_000)
    val exponentialDelayInitial: Long,
    // Kept as alias for initial delay (for backward compatibility)
    @param:Name(KEY_REVOLT_EXPONENTIAL_DELAY_BASE)
    @param:Nullable
    val exponentialDelayBase: Long?,
    @param:Name(KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE)
    @param:DefaultValue(defaultString = "1.2")
    val exponentialDelayPowerBase: String,
    @param:Name(KEY_REVOLT_EXPONENTIAL_DELAY_MAX)
    @param:DefaultValue(defaultLong = 600_000)
    val exponentialDelayMax: Long,
    @param:Name(KEY_REVOLT_FAST_REVOLT_STATUS_TIMEOUT)
    @param:DefaultValue(defaultLong = -1) // Default switched off
    val fastRevoltStatusTimeout: Long,
    @param:Name(KEY_REVOLT_WHEN_SHOULD_BUILD_BLOCK)
    @param:DefaultValue(defaultBoolean = false)
    val revoltWhenShouldBuildBlock: Boolean
) {
    companion object : KLogging() {
        @JvmStatic
        val default = GtvFactory.gtv(mapOf()).toObject<RevoltConfigurationData>()
    }

    fun getInitialDelay() = exponentialDelayBase ?: exponentialDelayInitial

    fun getDelayPowerBase() = try {
        val exponentialDelayPowerBase = exponentialDelayPowerBase.toDouble()
        if (exponentialDelayPowerBase <= 1) {
            throw UserMistake("Value '$exponentialDelayPowerBase' configured for $KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE is not allowed, it must be greater than 1")
        } else exponentialDelayPowerBase
    } catch (e: NumberFormatException) {
        throw UserMistake("Value '$exponentialDelayPowerBase' configured for $KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE is not a valid number")
    }
}
