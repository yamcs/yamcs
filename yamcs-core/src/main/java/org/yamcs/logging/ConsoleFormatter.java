package org.yamcs.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Specifically intended for short-term console output. It contains the bare minimum of information. Memory optimization
 * is 'good enough' for console output.
 * 
 * Features:
 * <ul>
 * <li>Hides the day, only the hour is shown
 * <li>Hides severities, except for 'WARNING' and 'ERROR'
 * <li>Hides the method name
 * <li>Supports minimal colors
 * </ul>
 */
public class ConsoleFormatter extends Formatter {

    private static final String COLOR_PREFIX = "\033[";
    private static final String COLOR_SUFFIX = "m";
    private static final String COLOR_RESET = "\033[0;0m";

    private boolean enableAnsiColors = true;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    private Date d = new Date();

    public void setEnableAnsiColors(boolean enableAnsiColors) {
        this.enableAnsiColors = enableAnsiColors;
    }

    @Override
    public String format(LogRecord r) {
        StringBuilder sb = new StringBuilder();

        d.setTime(r.getMillis());
        sb.append(sdf.format(d)).append(" ");

        String yamcsInstance = "_global";
        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getYamcsInstance() != null) {
                yamcsInstance = yRec.getYamcsInstance();
            }
        }
        sb.append(yamcsInstance).append(" ").append("[").append(r.getThreadID()).append("] ");

        String name = r.getLoggerName();
        if (name.lastIndexOf('.') != -1) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getContext() != null) {
                name += " [" + yRec.getContext() + "]";
            }
        }

        if (enableAnsiColors) {
            colorize(sb, name, 0, 36);
            sb.append(" ");
            if (r.getLevel() == Level.WARNING || "stdout".equals(name)) {
                colorize(sb, formatMessage(r), 0, 33);
            } else if (r.getLevel() == Level.SEVERE || "stderr".equals(name)) {
                colorize(sb, formatMessage(r), 0, 31);
            } else {
                sb.append(formatMessage(r));
            }
        } else {
            sb.append(name).append(": ");
            sb.append(r.getLevel().toString()).append(" ").append(formatMessage(r));
        }

        Throwable t = r.getThrown();
        if (t != null) {
            sb.append(": ").append(t.toString()).append("\n");
            for (StackTraceElement ste : t.getStackTrace()) {
                sb.append("\t").append(ste.toString()).append("\n");
            }
            Throwable cause = t.getCause();
            while (cause != null && cause != t) {
                sb.append("Caused by: ").append(cause.toString()).append("\n");
                for (StackTraceElement ste : cause.getStackTrace()) {
                    sb.append("\t").append(ste.toString()).append("\n");
                }
                cause = cause.getCause();
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static void colorize(StringBuilder buf, String s, int brightness, int ansiColor) {
        buf.append(COLOR_PREFIX).append(brightness).append(';').append(ansiColor).append(COLOR_SUFFIX);
        buf.append(s);
        buf.append(COLOR_RESET);
    }
}
