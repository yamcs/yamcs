package org.yamcs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.yamcs.api.YamcsLogRecord;

public class CompactFormatter extends Formatter {

    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm:ss.SSS");
    Date d = new Date();

    @Override
    public String format(LogRecord r) {
        StringBuffer sb = new StringBuffer();

        // Instance must be first column because would like to make it
        // easy to filter logs based on instance. Perhaps even adding a
        // "logs" subcommand to yamcsadmin, although that will require
        // that we know where the logs are actually located (not the case
        // as long as we expose JUL logging to our users).
        String yamcsInstance = "_global";
        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getYamcsInstance() != null) {
                yamcsInstance = yRec.getYamcsInstance();
            }
        }
        sb.append(yamcsInstance).append(" ");

        d.setTime(r.getMillis());
        sb.append(sdf.format(d)).append(" ");

        String name = r.getLoggerName();
        sb.append(name).append(" [").append(r.getThreadID()).append("] ");

        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getContext() != null) {
                sb.append(yRec.getContext()).append(" ");
            }
        }

        sb.append("[").append(r.getLevel()).append("] ").append(r.getMessage());

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
}
