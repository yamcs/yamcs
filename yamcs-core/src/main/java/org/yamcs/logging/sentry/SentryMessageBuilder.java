package org.yamcs.logging.sentry;

import io.sentry.protocol.Message;
import java.util.List;

public class SentryMessageBuilder {

    private final Message message;

    public SentryMessageBuilder() {
        this.message = new Message();
    }

    public SentryMessageBuilder withMessage(String message) {
        this.message.setMessage(message);
        return this;
    }

    public SentryMessageBuilder withParams(List<String> params) {
        this.message.setParams(params);
        return this;
    }

    public SentryMessageBuilder withFormatted(String formatted) {
        this.message.setFormatted(formatted);
        return this;
    }

    public Message build() {
        return this.message;
    }
}