package net.postchain.common// Copyright (c) 2020 ChromaWay AB. See README for license information.

import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender

fun getLoggerCaptor(cls: Class<*>): ListAppender {
    val context = LoggerContext.getContext(false)
    val logger = context.getLogger(cls) as Logger
    val appender = ListAppender("List").apply {
        start()
    }
    context.configuration.addLoggerAppender(logger, appender)
    return appender
}
