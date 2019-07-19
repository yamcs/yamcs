package org.yamcs.api;

import org.yamcs.YamcsException;

/**
 * Indicates a failure coming from a {@link Plugin}.
 */
@SuppressWarnings("serial")
public class PluginException extends YamcsException {

    public PluginException(String message, Throwable t) {
        super(message, t);
    }

    public PluginException(Throwable t) {
        super(t);
    }

    public PluginException(String message) {
        super(message);
    }
}
