package net.postchain.ebft.syncmanager.validator

import mu.KLogging
import net.postchain.base.configuration.*
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class RevoltConfigurationData(
    @RawGtv
    val rawGtv: Gtv,
    @Name(KEY_REVOLT_TIMEOUT)
    @DefaultValue(defaultLong = 10_000)
    val timeout: Long,
    @Name(KEY_REVOLT_EXPONENTIAL_DELAY_INITIAL)
    @DefaultValue(defaultLong = 1_000)
    val exponentialDelayInitial: Long,
    // Kept as alias for initial delay (for backward compatibility)
    @Name(KEY_REVOLT_EXPONENTIAL_DELAY_BASE)
    @Nullable
    val exponentialDelayBase: Long?,
    @Name(KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE)
    @DefaultValue(defaultString = "1.2")
    val exponentialDelayPowerBase: String,
    @Name(KEY_REVOLT_EXPONENTIAL_DELAY_MAX)
    @DefaultValue(defaultLong = 600_000)
    val exponentialDelayMax: Long,
    @Name(KEY_REVOLT_FAST_REVOLT_STATUS_TIMEOUT)
    @DefaultValue(defaultLong = -1) // Default switched off
    val fastRevoltStatusTimeout: Long,
) {
    companion object : KLogging() {
        @JvmStatic
        val default = GtvFactory.gtv(mapOf()).toObject<RevoltConfigurationData>()
    }

    fun getInitialDelay() = exponentialDelayBase ?: exponentialDelayInitial

    fun getDelayPowerBase() = try {
        exponentialDelayPowerBase.toDouble()
    } catch (e: NumberFormatException) {
        throw UserMistake("Value '$exponentialDelayPowerBase' configured for $KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE is not a valid number")
    }
}
