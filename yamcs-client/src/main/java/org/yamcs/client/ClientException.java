package org.yamcs.client;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class ClientException extends Exception {

    private ExceptionData detail;

    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable t) {
        super(message, t);
    }

    public ClientException(Throwable t) {
        super(t);
    }

    public ClientException(ExceptionData detail) {
        super(detail.getMessage());
        this.detail = detail;
    }

    public ExceptionData getDetail() {
        return detail;
    }

    public static class ExceptionData {
        private String type;
        private String message;
        private Map<String, Object> details = new HashMap<>(0);

        public ExceptionData(String type, String message) {
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
