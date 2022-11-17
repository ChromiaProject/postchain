package net.postchain.gtv.yaml

import net.postchain.gtv.Gtv
import org.yaml.snakeyaml.Yaml

class GtvYaml(theRoot: Class<*>): Yaml(GtvConstructor(theRoot)) {

    constructor() : this(Gtv::class.java)
}
