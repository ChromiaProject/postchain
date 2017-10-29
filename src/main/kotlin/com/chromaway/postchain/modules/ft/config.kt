package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.base.SECP256K1CryptoSystem
import com.chromaway.postchain.base.hexStringToByteArray
import org.apache.commons.configuration2.Configuration

fun makeFTIssueRules(ac: AccountUtil, config: Configuration): FTIssueRules {
    val assetIssuerMap: MutableMap<String, Map<ByteArray, ByteArray>> = mutableMapOf()
    val assets = config.getStringArray("assets")
    for (assetName in assets) {
        val issuerMap = mutableMapOf<ByteArray, ByteArray>()
        for (issuer in config.getStringArray("asset.${assetName}.issuers")) {
            val pubKey = issuer.hexStringToByteArray()
            issuerMap[ac.issuerAccountID(pubKey)] = pubKey
        }
        assetIssuerMap[assetName] = issuerMap.toMap()
    }

    fun checkIssuer(data: FTIssueData): Boolean {
        if (data.assetID !in assetIssuerMap) return false
        val issuer = assetIssuerMap[data.assetID]!![data.issuerID]
        if (issuer == null) {
            return false
        } else {
            return data.opData.signers.any { it.contentEquals(issuer) }
        }
    }
    return FTIssueRules(arrayOf(::checkIssuer), arrayOf())
}

fun makeFTRegisterRules(config: Configuration): FTRegisterRules {
    if (config.getBoolean("openRegistration")) {
        return FTRegisterRules(arrayOf(), arrayOf())
    } else {
        val registrators = config.getStringArray("registrators").map { it.hexStringToByteArray() }
        fun checkRegistration(data: FTRegisterData): Boolean {
            return data.opData.signers.any { signer ->
                registrators.any { it.contentEquals(signer) }
            }
        }
        return FTRegisterRules(arrayOf(::checkRegistration), arrayOf())
    }
}

fun makeFTTransferRules(config: Configuration): FTTransferRules {
    return FTTransferRules(arrayOf(), arrayOf(), false)
}

fun makeFTAccountFactory(config: Configuration): AccountFactory {

    return BaseAccountFactory(
            mapOf(
                    NullAccount.entry,
                    BasicAccount.entry
            )
    )
}

fun makeFTConfig(blockchainID: ByteArray, config: Configuration): FTConfig {
    val cs = SECP256K1CryptoSystem()
    val ac = AccountUtil(blockchainID, cs)
    val accFactory = makeFTAccountFactory(config)
    return FTConfig(
            makeFTIssueRules(ac, config),
            makeFTTransferRules(config),
            makeFTRegisterRules(config),
            accFactory,
            BaseAccountResolver(accFactory),
            BaseDBOps(),
            cs,
            blockchainID
    )
}
