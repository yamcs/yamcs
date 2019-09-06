package org.yamcs.logging;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

import org.yamcs.YamcsServer;

/**
 * A filter that discards log messages that are specific to a Yamcs instance.
 */
public class GlobalFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record instanceof YamcsLogRecord) {
            String yamcsInstance = ((YamcsLogRecord) record).getYamcsInstance();
            return yamcsInstance == null || YamcsServer.GLOBAL_INSTANCE.equals(yamcsInstance);
        }
        return true;
    }
}
