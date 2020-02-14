package org.yamcs.client;

import com.google.protobuf.Any;

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
    }
}
