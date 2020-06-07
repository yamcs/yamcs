package org.yamcs.tse;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.yamcs.YConfiguration;
import org.yamcs.tse.api.TseCommand;

/**
 * Adds a termination pattern to the end of command string. This is typically used on stream-oriented protocols like TCP
 * for delimiting messages.
 */
public class RequestTerminator implements Interceptor {

    public static final String CONFIG_TERMINATION = "termination";

    private byte[] requestTermination;

    public RequestTerminator(YConfiguration config) {
        requestTermination = config.getString(CONFIG_TERMINATION).getBytes();
    }

    @Override
    public byte[] interceptCommand(TseCommand metadata, byte[] bytes, Charset encoding) {
        return concat(bytes, requestTermination);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
