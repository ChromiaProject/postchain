package net.postchain.el2.config

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv
import java.math.BigInteger

data class EifBlockchainConfig(
    @RawGtv
    val rawGtv: Gtv,
    @Name("contract")
    val contract: String,
    @Name("contract_deploy_block")
    @DefaultValue(defaultBigInteger = "0")
    val contractDeployBlock: BigInteger
)
