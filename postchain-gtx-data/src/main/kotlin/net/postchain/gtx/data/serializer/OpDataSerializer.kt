// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.data.serializer

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.OpData

object OpDataSerializer {

    /**
     * Elements should be ordered like this:
     * 1. Operation name
     * 2. array of arguments
     */
    fun serializeToGtv(opData: OpData): GtvArray {

        // 1. Operation name
        val name: Gtv = gtv(opData.opName)
        // 2. array of arguments
        val args: Gtv = gtv(opData.args.toList())

        return gtv(name, args)
    }
}