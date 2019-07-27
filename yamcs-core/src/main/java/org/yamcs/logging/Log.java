package org.yamcs.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class Log {

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

    public void setContext(String context) {
        this.context = context;
    }

    public void info(String msg) {
        if (julLogger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, null);
        }
    }

    public void info(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, t);
        }
    }

    public void info(String msg, Object... args) {
        if (julLogger.isLoggable(Level.INFO)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    public boolean isDebugEnabled() {
        return julLogger.isLoggable(Level.FINE);
    }

    public void debug(String msg) {
        if (julLogger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, null);
        }
    }

    public void debug(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, t);
        }
    }

    public void debug(String msg, Object... args) {
        if (julLogger.isLoggable(Level.FINE)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    public boolean isTraceEnabled() {
        return julLogger.isLoggable(Level.FINEST);
    }

    public void trace(String msg) {
        if (julLogger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, null);
        }
    }

    public void trace(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, t);
        }
    }

    public void trace(String msg, Object... args) {
        if (julLogger.isLoggable(Level.FINEST)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    public void warn(String msg) {
        if (julLogger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, null);
        }
    }

    public void warn(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, t);
        }
    }

    public void warn(String msg, Object... args) {
        if (julLogger.isLoggable(Level.WARNING)) {
            FormattingTuple ft = MessageFormatter.arrayFormat(msg, args);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    public void error(String msg) {
        if (julLogger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, null);
        }
    }

    public void error(String msg, Throwable t) {
        if (julLogger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, t);
        }
    }

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

        julLogger.log(rec);
    }
}
