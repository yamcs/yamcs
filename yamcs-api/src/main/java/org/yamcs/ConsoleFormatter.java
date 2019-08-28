package org.yamcs;

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
 * 
 * @deprecated This class used to be used by both client tools and the server. These code bases are gradually being
 *             split from each other. For server logging use org.yamcs.logging.ConsoleFormatter from yamcs-core. For
 *             client logging use org.yamcs.ui.LogFormatter from yamcs-client.
 */
@Deprecated
public class ConsoleFormatter extends Formatter {

    private static final String COLOR_PREFIX = "\033[";
    private static final String COLOR_SUFFIX = "m";
    private static final String COLOR_RESET = "\033[0;0m";

    boolean enableAnsiColors;
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    Date d = new Date();

    public ConsoleFormatter() {
        this(true);
    }

    public ConsoleFormatter(boolean enableAnsiColors) {
        this.enableAnsiColors = enableAnsiColors;
    }

    @Override
    public String format(LogRecord r) {
        StringBuilder sb = new StringBuilder();

        d.setTime(r.getMillis());
        sb.append(sdf.format(d)).append(" ");

        String name = r.getLoggerName();
        if (name.lastIndexOf('.') != -1) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }

        if (enableAnsiColors) {
            colorize(sb, name, 0, 36);
        } else {
            sb.append(name);
        }

        sb.append("[").append(r.getThreadID()).append("]: ");

        if (r.getLevel() == Level.WARNING || r.getLevel() == Level.SEVERE) {
            if (enableAnsiColors) {
                colorize(sb, r.getLevel().toString(), 0, 31);
            } else {
                sb.append(r.getLevel().toString());
            }
            sb.append(" ");
        }
        sb.append(r.getMessage());

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
