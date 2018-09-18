package org.yamcs.tse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class ResponseBuilder {

    private ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private Charset encoding;
    private String responseTermination;

    public ResponseBuilder(Charset encoding, String responseTermination) {
        this.encoding = encoding;
        this.responseTermination = responseTermination;
    }

    public void append(byte[] b, int off, int len) {
        buf.write(b, off, len);
    }

    /**
     * Reads a 'complete' response. This is defined as a response that ends with the the expected response termination.
     * If no response termination is defined, this will always return null (rely on timeouts).
     * 
     * @return the decoded string with the termination stripped off.
     */
    public String parseCompleteResponse() {
        if (buf.size() > 0) {
            String result = new String(buf.toByteArray(), encoding);
            if (responseTermination != null && result.endsWith(responseTermination)) {
                return result.substring(0, result.length() - responseTermination.length());
            }
        }
        return null;
    }

    /**
     * Returns the decoded string content (complete or not).
     */
    public String parsePartialResponse() {
        String completeResponse = parseCompleteResponse();
        if (completeResponse != null) {
            return completeResponse;
        } else if (buf.size() > 0) {
            return new String(buf.toByteArray(), encoding);
        }
        return null;
    }
}
