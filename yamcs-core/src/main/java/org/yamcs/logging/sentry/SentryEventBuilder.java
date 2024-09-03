package org.yamcs.logging.sentry;

import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;

public class SentryEventBuilder {
    private final SentryEvent event;

    public SentryEventBuilder() {
        this.event = new SentryEvent();
    }

    public SentryEventBuilder withMessage(Message message) {
        this.event.setMessage(message);
        return this;
    }

    public SentryEventBuilder withLevel(SentryLevel level) {
        this.event.setLevel(level);
        return this;
    }

    public SentryEventBuilder withLogger(String logger) {
        this.event.setLogger(logger);
        return this;
    }

    public SentryEventBuilder withThrowable(Throwable e) {
        this.event.setThrowable(e);
        return this;
    }

    public SentryEvent build() {
        return this.event;
    }
}
