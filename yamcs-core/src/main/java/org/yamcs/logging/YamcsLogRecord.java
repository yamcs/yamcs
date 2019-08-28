package org.yamcs.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class YamcsLogRecord extends LogRecord {

    private static final long serialVersionUID = 1L;

    /**
     * @serial Yamcs instance where this log message originated
     */
    private String yamcsInstance;

    /**
     * @serial Additional context desciption.
     */
    private String context;

    public YamcsLogRecord(Level level, String msg, String yamcsInstance) {
        super(level, msg);
        this.yamcsInstance = yamcsInstance;
    }

    public void setYamcsInstance(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getContext() {
        return context;
    }
}
