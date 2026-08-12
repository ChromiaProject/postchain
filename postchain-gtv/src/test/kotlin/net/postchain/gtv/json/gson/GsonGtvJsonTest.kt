package net.postchain.gtv.json.gson

import net.postchain.gtv.GtvJsonCodecContract
import net.postchain.gtv.json.GtvJsonCodec
import net.postchain.gtv.json.GtvJsonConfig

class GsonGtvJsonTest : GtvJsonCodecContract() {
    override fun codec(config: GtvJsonConfig, prettyPrint: Boolean): GtvJsonCodec = GtvJson(config, prettyPrint)
}
