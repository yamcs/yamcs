package org.yamcs.tse;

import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * An interceptor that does just logs both incoming and outgoing messags.
 */
public class LoggingInterceptor implements Interceptor {

    private static final Logger log = Logger.getLogger(LoggingInterceptor.class.getName());

    @Override
    public byte[] interceptCommand(String instrument, byte[] bytes, Charset encoding) {
        log.info(String.format("%s <<< %s", instrument, new String(bytes, encoding).trim()));
        return bytes;
    }

    @Override
    public byte[] interceptResponse(String instrument, byte[] bytes, Charset encoding) {
        log.info(String.format("%s >>> %s", instrument, new String(bytes, encoding)));
        return bytes;
    }
}
