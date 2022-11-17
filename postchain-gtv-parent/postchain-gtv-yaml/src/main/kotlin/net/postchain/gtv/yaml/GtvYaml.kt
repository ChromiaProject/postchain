package net.postchain.gtv.yaml

import net.postchain.gtv.Gtv
import org.yaml.snakeyaml.Yaml

class GtvYaml: Yaml(GtvConstructor(Gtv::class.java))