/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

@file:Suppress("PackageName")

package ee.ria.DigiDoc.utilsLib.logging

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

interface Logging {
    fun errorLog(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun debugLog(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun infoLog(
        tag: String,
        message: String,
    )
}

@Singleton
class LoggingUtil
    @Inject
    constructor() {
        companion object : Logging {
            private lateinit var logger: Logger
            private var isLoggingEnabled: Boolean = false

            fun initialize(
                context: Context,
                appLogger: Logger,
                loggingEnabled: Boolean,
            ) {
                isLoggingEnabled = loggingEnabled

                if (isLoggingEnabled) {
                    logger = appLogger
                    val consoleHandler = ConsoleHandler()
                    consoleHandler.formatter = LogFormatter()
                    logger.addHandler(consoleHandler)
                    logger.level = java.util.logging.Level.ALL
                    consoleHandler.level = java.util.logging.Level.ALL
                }
            }

            override fun errorLog(
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                if (isLoggingEnabled) {
                    throwable?.let {
                        logger.severe("$tag: $message ${it.localizedMessage}")
                    } ?: logger.severe("$tag: $message")
                }
            }

            override fun debugLog(
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                if (isLoggingEnabled) {
                    throwable?.let {
                        logger.fine("$tag: $message ${it.localizedMessage}")
                    } ?: logger.fine("$tag: $message")
                }
            }

            override fun infoLog(
                tag: String,
                message: String,
            ) {
                if (isLoggingEnabled) {
                    logger.info("$tag: $message")
                }
            }
        }
    }

class LogFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        val dateTimeFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = dateTimeFormat.format(Date(record.millis))
        val message = record.message
        return "$timestamp, $message\n"
    }
}

