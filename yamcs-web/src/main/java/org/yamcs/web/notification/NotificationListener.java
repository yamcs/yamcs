package org.yamcs.web.notification;

import org.yamcs.web.api.NotificationInfo;

@FunctionalInterface
public interface NotificationListener {

    void onNotification(NotificationInfo notification);
}
