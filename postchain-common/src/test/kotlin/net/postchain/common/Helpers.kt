package net.postchain.common// Copyright (c) 2020 ChromaWay AB. See README for license information.

import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender

fun createLogCaptor(cls: Class<*>, name: String): ListAppender {
    val context = LoggerContext.getContext(false)
    val logger = context.getLogger(cls) as Logger
    val appender = ListAppender(name).apply {
        start()
    }
    context.configuration.addLoggerAppender(logger, appender)
    return appender
}
