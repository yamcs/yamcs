package org.yamcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * default crash handler that just print a message in the log
 * @author nm
 *
 */
public class LogCrashHandler implements CrashHandler {
    static Logger log = LoggerFactory.getLogger(CrashHandler.class);
    @Override
    public void handleCrash(String type, String msg) {
        log.error("type: {}, msg: {}", type, msg);
    }
    
}
