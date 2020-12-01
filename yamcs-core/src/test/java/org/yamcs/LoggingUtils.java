package org.yamcs;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingUtils {
    /**
     * use to enable logging during junit tests debugging. 
     * Do not leave it enabled as travis will kill the tests if it outputs too much
     */
    public static void enableLogging(Level level) {
        Logger logger = Logger.getLogger("org.yamcs");
        logger.setLevel(level);
        ConsoleHandler ch = null;
        
        for (Handler h: Logger.getLogger("").getHandlers()) {
            if(h instanceof ConsoleHandler) {
                ch = (ConsoleHandler) h;
                break;
            }
        }
        if(ch==null) {
            ch = new ConsoleHandler();
            Logger.getLogger("").addHandler(ch);
        }
        ch.setLevel(level);
    }
}
