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

