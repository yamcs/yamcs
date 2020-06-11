package org.yamcs.tse;

import java.nio.charset.Charset;

import org.yamcs.tse.api.TseCommand;

public interface Interceptor {

    /**
     * Intercepts a raw command just before passing it to driver-specific write logic.
     * <p>
     * Usually this corresponds to an encoded string, for which the encoding is provided too.
     * 
     * @param metadata
     *            Metadata about the command. Note that full information is only available if the command was issued
     *            through Yamcs. If instead an internal Telnet session was used, only the target instrument is known.
     * @param bytes
     *            The raw command (usually correspons to an encoded string)
     * @param encoding
     *            String encoding
     */
    default byte[] interceptCommand(TseCommand metadata, byte[] bytes, Charset encoding) {
        return bytes;
    }

    /**
     * Intercepts the response when it is provided by the driver-specific read logic.
     * <p>
     * This cannot be used for modifying how responses are delimited, because that's a responsibility of the driver.
     *
     * @param metadata
     *            Metadata about the command. Note that full information is only available if the command was issued
     *            through Yamcs. If instead an internal Telnet session was used, only the target instrument is known.
     * @param bytes
     *            The raw command response (usually corresponds to an encoded string)
     * @param encoding
     *            Expected string encoding
     */
    default byte[] interceptResponse(TseCommand metadata, byte[] bytes, Charset encoding) {
        return bytes;
    }
}
