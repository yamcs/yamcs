package org.yamcs.http;

import io.netty.util.AttributeKey;

public enum WebSocketFramePriority {

    /**
     * Messages are dropped if causing the channel to become not writable
     */
    LOW,

    /**
     * Messages are dropped if the channel is not writable
     */
    NORMAL,

    /**
     * Messages are written (in fact queued by netty) even if the channel is not writable. Too many of these will cause
     * OOM
     */
    HIGH;

    /**
     * Channel attribute key for get/set of current message priority
     */
    public static final AttributeKey<WebSocketFramePriority> ATTR = AttributeKey.valueOf("wsPriority");
}
