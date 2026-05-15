package ee.ria.DigiDoc.utilsLib.logging

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Locks in the documented contract:
 *
 *  1. Calls before [LoggingUtil.Companion.initialize] are silently ignored.
 *  2. Calls after `initialize(loggingEnabled = false)` are ignored.
 *  3. Calls after `initialize(loggingEnabled = true)` reach the supplied
 *     [Logger].
 *
 * The companion object is process-singleton; we reset its private
 * `isLoggingEnabled` flag via reflection in a `@BeforeEach`/`@AfterEach`
 * pair so state from a previous test cannot leak in. Done from the test
 * side (rather than via a production-side mutator) to keep test-only
 * hooks out of the shipped class.
 */
class LoggingUtilTest {

    private val context: Context = mock(Context::class.java)

    @BeforeEach
    fun resetBefore() {
        resetLoggingEnabledFlag()
    }

    @AfterEach
    fun resetAfter() {
        resetLoggingEnabledFlag()
    }

    private fun resetLoggingEnabledFlag() {
        // Kotlin hoists private companion-object vars to the outer class as
        // private static fields. Flip it back to false so each test sees a
        // consistent starting state. The lateinit `logger` field cannot be
        // un-set once initialized; that's fine because the flag guard
        // short-circuits all log methods before they access it.
        val flag = LoggingUtil::class.java.getDeclaredField("isLoggingEnabled")
        flag.isAccessible = true
        flag.setBoolean(null, false)
    }

    // -------- (1) before initialize --------

    @Test
    fun debugLog_beforeInitialize_isSilentNoOp() {
        // Logger is uninitialized — accessing it would throw. The flag check
        // must short-circuit before we get there.
        LoggingUtil.debugLog("tag", "anything")
        LoggingUtil.errorLog("tag", "anything", RuntimeException("boom"))
        LoggingUtil.infoLog("tag", "anything")
        // If we got here without exception, the guard worked.
    }

    // -------- (2) initialize(loggingEnabled = false) --------

    @Test
    fun debugLog_afterInitializeDisabled_isSilentNoOp() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = false)

        LoggingUtil.debugLog("tag", "should not appear")
        LoggingUtil.errorLog("tag", "should not appear")
        LoggingUtil.infoLog("tag", "should not appear")

        assertThat(recorded).isEmpty()
    }

    // -------- (3) initialize(loggingEnabled = true) --------

    @Test
    fun debugLog_afterInitializeEnabled_delegatesToLogger() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = true)

        LoggingUtil.debugLog("MyTag", "debug message")

        assertThat(recorded).hasSize(1)
        assertThat(recorded[0].message).isEqualTo("MyTag: debug message")
    }

    @Test
    fun errorLog_afterInitializeEnabled_includesThrowableMessage() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = true)

        LoggingUtil.errorLog("MyTag", "kaboom", RuntimeException("upstream"))

        assertThat(recorded).hasSize(1)
        assertThat(recorded[0].message).contains("kaboom")
        assertThat(recorded[0].message).contains("upstream")
    }

    @Test
    fun errorLog_afterInitializeEnabled_withoutThrowable_logsBareMessage() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = true)

        LoggingUtil.errorLog("MyTag", "just text")

        assertThat(recorded).hasSize(1)
        assertThat(recorded[0].message).isEqualTo("MyTag: just text")
    }

    @Test
    fun infoLog_afterInitializeEnabled_delegatesToLogger() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = true)

        LoggingUtil.infoLog("MyTag", "info message")

        assertThat(recorded).hasSize(1)
        assertThat(recorded[0].message).isEqualTo("MyTag: info message")
    }

    // -------- toggling --------

    @Test
    fun disablingAfterEnabling_suppressesSubsequentLogs() {
        val recorded = mutableListOf<LogRecord>()
        val logger = anonymousLoggerCapturing(recorded)

        LoggingUtil.initialize(context, logger, loggingEnabled = true)
        LoggingUtil.debugLog("Tag", "first")
        assertThat(recorded).hasSize(1)

        // Re-initialize disabled — subsequent calls must stop logging.
        LoggingUtil.initialize(context, logger, loggingEnabled = false)
        LoggingUtil.debugLog("Tag", "second")

        assertThat(recorded).hasSize(1) // unchanged
    }

    // ---- helpers ----

    /** Returns a fresh anonymous Logger whose every record is appended to [sink]. */
    private fun anonymousLoggerCapturing(sink: MutableList<LogRecord>): Logger {
        // Anonymous loggers don't inherit the global config and don't get cached
        // by name, so each test starts with a clean slate.
        val logger = Logger.getAnonymousLogger()
        logger.useParentHandlers = false
        logger.level = java.util.logging.Level.ALL
        logger.addHandler(object : Handler() {
            override fun publish(record: LogRecord) { sink.add(record) }
            override fun flush() {}
            override fun close() {}
        })
        return logger
    }
}
