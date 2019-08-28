package org.yamcs.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A minimalistic formatter that is intended for use with systems that use the systemd journal. Useful for quick error
 * detection. Characteristics:
 * 
 * <ul>
 * <li>Does not print timestamps (responsibility of journald)
 * <li>Does not print full stack traces
 * <li>Does not print Yamcs instance or logger names
 * <li>Converts level messages to syslog conventions which journald also applies
 * </ul>
 * 
 * Note that this class is just a formatter. It does not actually integrate with the systemd journal. If you want to do
 * so you could configure a {@link ConsoleHandler} that uses this formatter and then somehow send the console output to
 * journald (for example by using a systemd service unit).
 */
public class JournalFormatter extends Formatter {

    // Note that there appears to be no way to make use of custom fields other
    // than priority levels when letting journald interpret stdout. It would have
    // been nice if we could for example introduce custom fields "instance" and
    // "logger", but it seems like we can't without really implementing a journald
    // domain socket client (some other day ...).

    @Override
    public String format(LogRecord r) {
        StringBuffer sb = new StringBuffer()
                .append("<").append(toSeverity(r.getLevel())).append(">");

        if (r instanceof YamcsLogRecord) {
            YamcsLogRecord yRec = (YamcsLogRecord) r;
            if (yRec.getContext() != null) {
                sb.append(yRec.getContext()).append(": ");
            }
        }
        sb.append(formatMessage(r));

        Throwable t = r.getThrown();
        if (t != null) {
            sb.append(": ").append(t.toString());
        }
        sb.append("\n");
        return sb.toString();
    }

    private static int toSeverity(Level level) {
        if (level == Level.SEVERE) {
            return 3; // Error
        } else if (level == Level.WARNING) {
            return 4; // Warning
        } else if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) {
            return 7; // Debug
        } else {
            return 6; // Informational
        }
    }
}
