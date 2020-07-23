package org.yamcs.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * Customized version of {@link LogManager} so that we can keep logging from within shutdown hooks.
 * <p>
 * This LogManager must be activated early via a JVM property <code>java.util.logging.manager</code>
 */
public class YamcsLogManager extends LogManager {

    /**
     * Does nothing.
     */
    @Override
    public void reset() {
        // This gets called via a shutdown hook.
        // Override to do nothing by default.
    }

    private void internalReset() {
        super.reset();
    }

    public static LogManager setup(InputStream inputStream) throws IOException {
        LogManager logManager = getLogManager();
        if (logManager instanceof YamcsLogManager) {
            ((YamcsLogManager) logManager).internalReset();
        }
        logManager.readConfiguration(inputStream);
        return logManager;
    }

    /**
     * Reset the logging configuration
     */
    public static void shutdown() {
        LogManager logManager = getLogManager();
        if (logManager instanceof YamcsLogManager) { // Not the case in unit tests
            ((YamcsLogManager) logManager).internalReset();
        }
    }
}
