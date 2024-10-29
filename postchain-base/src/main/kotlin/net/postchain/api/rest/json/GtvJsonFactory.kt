package net.postchain.api.rest.json

import net.postchain.gtv.makeStrictGvtGsonBuilder
import org.http4k.format.ConfigurableGson

object GtvJsonFactory : ConfigurableGson(makeStrictGvtGsonBuilder())
