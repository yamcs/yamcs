package org.yamcs.web.notification;

@FunctionalInterface
public interface NotificationListener {

    void onNotification(Notification notification);
}
