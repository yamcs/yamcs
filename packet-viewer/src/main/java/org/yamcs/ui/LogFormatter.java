package org.yamcs.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm:ss.SSS");
    Date d = new Date();

    @Override
    public String format(LogRecord r) {
        StringBuilder sb = new StringBuilder();

        d.setTime(r.getMillis());
        sb.append(sdf.format(d)).append(" ");

        String name = r.getLoggerName();
        sb.append(name).append(" [").append(r.getThreadID()).append("] ");
        sb.append("[").append(r.getLevel()).append("] ").append(r.getMessage());

        Throwable t = r.getThrown();
        if (t != null) {
            sb.append(": ").append(t.toString()).append("\n");
            addStack(sb, t);
            Throwable cause = t.getCause();
            while (cause != null && cause != t) {
                sb.append("Caused by: ").append(cause.toString()).append("\n");
                addStack(sb, cause);
                cause = cause.getCause();
            }
        }
        sb.append("\n");
        return sb.toString();
    }
    
    private void addStack(StringBuilder sb, Throwable t) {
        for (StackTraceElement ste : t.getStackTrace()) {
            sb.append("\t").append(ste.toString()).append("\n");
        }
    }
}
