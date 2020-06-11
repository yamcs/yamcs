package org.yamcs.tse;

import java.nio.charset.Charset;
import java.util.logging.Logger;

import org.yamcs.tse.api.TseCommand;

/**
 * An interceptor that does just logs both incoming and outgoing messags.
 */
public class LoggingInterceptor implements Interceptor {

    private static final Logger log = Logger.getLogger(LoggingInterceptor.class.getName());

    @Override
    public byte[] interceptCommand(TseCommand metadata, byte[] bytes, Charset encoding) {
        log.info(String.format("%s <<< %s", metadata.getInstrument(), new String(bytes, encoding).trim()));
        return bytes;
    }

    @Override
    public byte[] interceptResponse(TseCommand metadata, byte[] bytes, Charset encoding) {
        log.info(String.format("%s >>> %s", metadata.getInstrument(), new String(bytes, encoding)));
        return bytes;
    }
}
