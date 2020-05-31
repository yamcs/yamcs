package org.yamcs.tse;

import java.nio.charset.Charset;

public interface Interceptor {

    /**
     * Intercepts a raw command just before passing it to driver-specific write logic.
     * <p>
     * Usually this corresponds to an encoded string, for which the encoding is provided too.
     */
    default byte[] interceptCommand(String instrument, byte[] bytes, Charset encoding) {
        return bytes;
    }

    /**
     * Intercepts the response when it is provided by the driver-specific read logic.
     * <p>
     * This cannot be used for modifying how responses are delimited, because that's a responsibility of the driver.
     */
    default byte[] interceptResponse(String instrument, byte[] bytes, Charset encoding) {
        return bytes;
    }
}
