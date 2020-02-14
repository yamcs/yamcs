package org.yamcs.http;

import org.yamcs.api.ExceptionMessage;

import com.google.protobuf.Any;
import com.google.protobuf.Message;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Default HTTP exception.
 */
public abstract class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private Message detail;

    public HttpException() {
        super();
    }

    public HttpException(Throwable t) {
        super(t);
    }

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable t) {
        super(message, t);
    }

    public abstract HttpResponseStatus getStatus();

    public boolean isServerError() {
        int code = getStatus().code();
        return 500 <= code && code < 600;
    }

    public Message getDetail() {
        return detail;
    }

    public void setDetail(Message detail) {
        this.detail = detail;
    }

    public ExceptionMessage toMessage() {
        ExceptionMessage.Builder msgb = ExceptionMessage.newBuilder();
        msgb.setCode(getStatus().code());
        msgb.setType(getClass().getSimpleName());

        // Try to get a specific message. i.e. turn "Type1: Type2: Type3: Message" into "Message"
        Throwable realCause = this;
        while (realCause.getCause() != null) {
            realCause = realCause.getCause();
        }
        if (realCause.getMessage() != null) {
            msgb.setMsg(realCause.getMessage());
        } else {
            msgb.setMsg(realCause.getClass().getSimpleName());
        }

        if (detail != null) {
            msgb.setDetail(Any.pack(detail, HttpServer.TYPE_URL_PREFIX));
        }

        return msgb.build();
    }
}
