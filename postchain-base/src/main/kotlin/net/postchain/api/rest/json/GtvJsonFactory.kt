package net.postchain.api.rest.json

import net.postchain.gtv.make_gtv_gson_builder
import org.http4k.format.ConfigurableGson

object GtvJsonFactory : ConfigurableGson(make_gtv_gson_builder())
