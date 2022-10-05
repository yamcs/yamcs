package org.yamcs.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class Log {

    // Kill switch used to force stdout logging
    private static Level stdoutLoggingLevel;

    private final Logger julLogger;
    private String yamcsInstance;
    private String context;

    public Log(Class<?> clazz) {
        julLogger = Logger.getLogger(clazz.getName());
    }

    public Log(Class<?> clazz, String yamcsInstance) {
        julLogger = Logger.getLogger(clazz.getName());
        this.yamcsInstance = yamcsInstance;
    }

    public Logger getJulLogger() {
        return julLogger;
    }

    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Send a log message at INFO level.
     */
    public void info(String msg) {
        if (julLogger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, null);
        }
    }

    /**
     * Send a log message at INFO level.
     */
    public void info(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, t);
        }
    }

    /**
     * Send a log message at INFO level using SLF4J-style formatting. The last argument may be a {@link Throwable}.
     */
    public void info(String msg, Object... args) {
        if (julLogger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    public boolean isDebugEnabled() {
        return julLogger.isLoggable(Level.FINE);
    }

    /**
     * Send a log message at DEBUG level.
     */
    public void debug(String msg) {
        if (julLogger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, null);
        }
    }

    /**
     * Send a log message at DEBUG level.
     */
    public void debug(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, t);
        }
    }

    /**
     * Send a log message at DEBUG level using SLF4J-style formatting. The last argument may be a {@link Throwable}.
     */
    public void debug(String msg, Object... args) {
        if (julLogger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    public boolean isTraceEnabled() {
        return julLogger.isLoggable(Level.FINEST);
    }

    /**
     * Send a log message at TRACE level.
     */
    public void trace(String msg) {
        if (julLogger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, null);
        }
    }

    /**
     * Send a log message at TRACE level.
     */
    public void trace(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, t);
        }
    }

    /**
     * Send a log message at TRACE level using SLF4J-style formatting. The last argument may be a {@link Throwable}.
     */
    public void trace(String msg, Object... args) {
        if (julLogger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Send a log message at WARN level.
     */
    public void warn(String msg) {
        if (julLogger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, null);
        }
    }

    /**
     * Send a log message at WARN level.
     */
    public void warn(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, t);
        }
    }

    /**
     * Send a log message at WARN level using SLF4J-style formatting. The last argument may be a {@link Throwable}.
     */
    public void warn(String msg, Object... args) {
        if (julLogger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    /**
     * Send a log message at ERROR level.
     */
    public void error(String msg) {
        if (julLogger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, null);
        }
    }

    /**
     * Send a log message at ERROR level.
     */
    public void error(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, t);
        }
    }

    /**
     * Send a log message at ERROR level using SLF4J-style formatting. The last argument may be a {@link Throwable}.
     */
    public void error(String msg, Object... args) {
        if (julLogger.isLoggable(Level.SEVERE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    protected void log(Level level, String msg, Throwable t) {
        YamcsLogRecord rec = new YamcsLogRecord(level, msg, yamcsInstance);
        rec.setLoggerName(julLogger.getName());
        rec.setThrown(t);
        rec.setContext(context);

        if (stdoutLoggingLevel != null) {
            logToStdOut(rec);
        } else {
            julLogger.log(rec);
        }
    }

    private void logToStdOut(YamcsLogRecord rec) {
        if (rec.getLevel().intValue() >= stdoutLoggingLevel.intValue()) {
            StringBuilder sb = new StringBuilder("[")
                    .append(rec.getLevel())
                    .append("] ")
                    .append(rec.getMessage());
            if (rec.getThrown() != null) {
                sb.append(": " + rec.getThrown());
            }
            System.out.println(sb.toString());
        }
    }

    /**
     * Force all log message to be printed on stdout instead of the configured logger. This may be of use for short
     * tests and scripts.
     */
    public static void forceStandardStreams(Level level) {
        stdoutLoggingLevel = level;
    }
}
