package net.postchain.common// Copyright (c) 2020 ChromaWay AB. See README for license information.

import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.test.appender.ListAppender

// We must only create one per name per run since multiple with mess things up
private var SINGLETON_LOG_APPENDERS = mutableMapOf<String, ListAppender>()

fun createLogCaptor(cls: Class<*>, name: String): ListAppender {
    return SINGLETON_LOG_APPENDERS[name] ?: run{
        val context = LoggerContext.getContext(false)
        val logger = context.getLogger(cls) as Logger
        val appender = ListAppender(name).apply {
            start()
        }
        context.configuration.addLoggerAppender(logger, appender)
        SINGLETON_LOG_APPENDERS[name] = appender
        appender
    }
}
