// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender

internal fun dummyHandlerArray(target: Unit, eContext: EContext, args: Gtv): Gtv {
    return GtvArray(emptyArray())
}

internal fun dummyHandlerArray(target: ManagedTestModuleTwoPeersConnect.Companion.Nodes, eContext: EContext, args: Gtv): Gtv {
    return GtvArray(emptyArray())
}

internal fun getLoggerCaptor(cls: Class<*>): ListAppender {
    val context = LoggerContext.getContext(false)
    val logger = context.getLogger(cls) as Logger
    val appender = ListAppender("List").apply {
        start()
    }
    context.configuration.addLoggerAppender(logger, appender)
    return appender
}