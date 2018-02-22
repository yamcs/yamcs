package org.yamcs.api;

import java.util.HashMap;
import java.util.Map;

/**
 * this was created because hornetq in its last incarnation throws Exception instead of HornetqException Should be used
 * for problems with encoding/decoding and sending/receiving messages via hornetq.
 */
public class YamcsApiException extends Exception {
    private static final long serialVersionUID = 1L;
    RestExceptionData restData;

    public YamcsApiException(String message) {
        super(message);
    }

    public YamcsApiException(RestExceptionData restData) {
        super(restData.getMessage());
        this.restData = restData;
    }

    public YamcsApiException(String message, Throwable t) {
        super(message, t);
    }

    public RestExceptionData getRestData() {
        return restData;
    }

    public static class RestExceptionData {
        private String type;
        private String message;
        private Map<String, Object> details = new HashMap<>(0);

        public RestExceptionData(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public void addDetail(String key, Object detail) {
            details.put(key, detail);
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public boolean hasDetail(String key) {
            return details.containsKey(key);
        }

        public Object getDetail(String key) {
            return details.get(key);
        }
    }
}
