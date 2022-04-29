// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.core.EContext

internal fun dummyHandlerArray(target: Unit, eContext: EContext, args: Gtv): Gtv {
    return GtvArray(emptyArray())
}

internal fun dummyHandlerArray(target: ManagedTestModuleTwoPeersConnect.Companion.Nodes, eContext: EContext, args: Gtv): Gtv {
    return GtvArray(emptyArray())
}
