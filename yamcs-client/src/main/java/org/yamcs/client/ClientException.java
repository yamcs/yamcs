package org.yamcs.client;

import com.google.protobuf.Any;

@SuppressWarnings("serial")
public class ClientException extends Exception {

    private final ExceptionData detail;

    public ClientException(String message) {
        this(message, null);
    }

    public ClientException(Throwable t) {
        this(null, t);
    }

    public ClientException(String message, Throwable t) {
        super(message, t);
        this.detail = null;
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
        private Any detail;

        public ExceptionData(String type, String message, Any detail) {
            this.type = type;
            this.message = message;
            this.detail = detail;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Any getDetail() {
            return detail;
        }

        @Override
        public String toString() {
            return "ExceptionData{" +
                    "type='" + type + '\'' +
                    ", message='" + message + '\'' +
                    ", detail=" + detail +
                    '}';
        }
    }
}
