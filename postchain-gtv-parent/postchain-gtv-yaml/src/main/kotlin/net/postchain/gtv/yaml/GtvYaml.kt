package net.postchain.gtv.yaml

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

class GtvYaml(constructor: GtvConstructor): Yaml(constructor) {

    constructor() : this(GtvConstructor())
    constructor(theRoot: Class<*>): this(GtvConstructor(theRoot))

    constructor(loaderOptions: LoaderOptions): this(GtvConstructor(loaderOptions))
}
