package org.yamcs.web.notification;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.web.api.NotificationInfo;
import org.yamcs.web.api.NotificationType;

import com.google.protobuf.Timestamp;

public class Notification implements Comparable<Notification> {

    private static final AtomicInteger SEQ = new AtomicInteger();

    // Globally unique notification identifier
    private final String tag;
    private final String instance;
    // Increments for each notification update. Used by webapp
    // to reliably keep track of unread counter
    private int seq;
    private Instant timestamp;
    private String title;
    private String description;
    private String url;
    private NotificationType type;

    private Notification(String instance, String tag, NotificationType type, String title) {
        this.instance = requireNonNull(instance);
        this.tag = requireNonNull(tag);
        this.type = requireNonNull(type);
        this.title = requireNonNull(title);

        // Not mission time. Notifications are global.
        timestamp = Instant.now();
        seq = SEQ.incrementAndGet();
    }

    public static Notification createInfo(String instance, String tag, String title) {
        return new Notification(instance, tag, NotificationType.INFO, title);
    }

    public static Notification createError(String instance, String tag, String title) {
        return new Notification(instance, tag, NotificationType.ERROR, title);
    }

    public static Notification createInProgress(String instance, String tag, String title) {
        return new Notification(instance, tag, NotificationType.IN_PROGRESS, title);
    }

    public static Notification createSuccess(String instance, String tag, String title) {
        return new Notification(instance, tag, NotificationType.SUCCESS, title);
    }

    public String getTag() {
        return tag;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void updateState(Notification updatedNotification) {
        if (!updatedNotification.getTag().equals(tag)) {
            throw new IllegalArgumentException("Tag mismatch");
        }

        timestamp = updatedNotification.timestamp;
        description = updatedNotification.description;
        type = updatedNotification.type;
        url = updatedNotification.url;
        seq = updatedNotification.seq;
    }

    public NotificationInfo toProto() {
        var builder = NotificationInfo.newBuilder()
                .setTag(tag)
                .setSeq(seq)
                .setInstance(instance)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(timestamp.getEpochSecond())
                        .setNanos(timestamp.getNano()))
                .setTitle(title)
                .setType(type);
        if (description != null) {
            builder.setDescription(description);
        }
        if (url != null) {
            builder.setUrl(url);
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Notification other) {
            return tag.equals(other.tag);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public int compareTo(Notification other) {
        var rc = timestamp.compareTo(other.timestamp);
        return rc != 0 ? Integer.compare(seq, other.seq) : rc;
    }
}
