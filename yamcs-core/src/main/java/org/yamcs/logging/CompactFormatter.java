package org.yamcs.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class CompactFormatter extends Formatter {

    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm:ss.SSS");
    Date d = new Date();

    @Override
    public String format(LogRecord r) {
        StringBuffer sb = new StringBuffer();

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
        sb.append(name);

        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getContext() != null) {
                sb.append("[").append(yRec.getContext()).append("]");
            }
        }
        sb.append(" ");

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
