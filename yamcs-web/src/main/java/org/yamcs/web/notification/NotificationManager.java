package org.yamcs.web.notification;

import static org.yamcs.web.notification.Notification.createError;
import static org.yamcs.web.notification.Notification.createInProgress;
import static org.yamcs.web.notification.Notification.createInfo;
import static org.yamcs.web.notification.Notification.createSuccess;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.timeline.DefaultItemListener;
import org.yamcs.timeline.ItemListener;
import org.yamcs.timeline.TimelineActivity;
import org.yamcs.timeline.TimelineItem;
import org.yamcs.timeline.TimelineService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.api.NotificationInfo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.netty.handler.codec.http.QueryStringEncoder;

/**
 * Keeps track of the last few notifications, at global scope
 */
public class NotificationManager {

    // Random ID, exposed to webapp, so that it can keep track of unread
    // notifications within the current runtime
    public static final String RUNTIME_ID = UUID.randomUUID().toString();

    private static final String TIMELINE_URL = "/timeline";
    private static final String TIMELINE_URL_C = "c";
    private static final String TIMELINE_URL_DATE = "date";

    private ConcurrentMap<String, ItemListener> itemListenersByInstance = new ConcurrentHashMap<>();

    // For now: not persisting this kind of information
    private Cache<String, Notification> tag2notification = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build();

    private Set<NotificationListener> listeners = ConcurrentHashMap.newKeySet();

    public NotificationManager() {
        ManagementService.getInstance().addManagementListener(new ManagementListener() {
            @Override
            public void instanceStateChanged(YamcsServerInstance ysi) {
                switch (ysi.state()) {
                case STARTING:
                    setupItemListener(ysi);
                    break;
                case STOPPING:
                case FAILED:
                    var itemListener = itemListenersByInstance.remove(ysi.getName());
                    if (itemListener != null) {
                        removeItemListener(itemListener, ysi);
                    }
                    break;
                default: // NOP
                }
            }
        });
    }

    public List<NotificationInfo> getNotifications() {
        return tag2notification.asMap().values().stream()
                .sorted()
                .map(Notification::toProto)
                .toList();
    }

    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    private void setupItemListener(YamcsServerInstance instanceObj) {
        var timelineServices = instanceObj.getServices(TimelineService.class);
        if (timelineServices.isEmpty()) {
            return;
        }

        var timelineService = timelineServices.get(0);

        var itemDb = timelineService.getTimelineItemDb();
        var itemListener = new DefaultItemListener() {
            @Override
            public void onItemUpdated(TimelineItem item) {
                if (item instanceof TimelineActivity activity) {
                    onTimelineActivityUpdated(instanceObj, activity);
                }
            }
        };
        itemDb.addItemListener(itemListener);
        itemListenersByInstance.put(instanceObj.getName(), itemListener);
    }

    private void removeItemListener(ItemListener itemListener, YamcsServerInstance instanceObj) {
        var timelineServices = instanceObj.getServices(TimelineService.class);
        if (timelineServices.isEmpty()) {
            return;
        }

        var timelineService = timelineServices.get(0);
        timelineService.getTimelineItemDb().removeItemListener(itemListener);
    }

    private void onTimelineActivityUpdated(YamcsServerInstance instanceObj, TimelineActivity activity) {
        var tag = activity.getId();
        var instance = instanceObj.getName();
        var status = activity.getStatus();
        var title = getActivityItemTitle(activity);
        var url = getActivityLink(instanceObj, activity);
        var type = activity.getActivityDefinition() == null ? "task" : "activity";

        var statusChanged = !Objects.equals(status, activity.getPrevStatus());
        Notification notification = null;
        if (statusChanged && status == ExecutionStatus.READY) {
            notification = createInfo(instance, tag, title);
            notification.setUrl(url);
            notification.setDescription("This " + type + " is ready for execution");
        } else if (statusChanged && status == ExecutionStatus.FAILED) {
            notification = createError(instance, tag, title);
            notification.setUrl(url);
            notification.setDescription(activity.getFailureReason());
        } else if (statusChanged && status == ExecutionStatus.ABORTED) {
            notification = createInfo(instance, tag, title);
            notification.setUrl(url);
            notification.setDescription("This " + type + " was aborted");
        } else if (statusChanged && status == ExecutionStatus.IN_PROGRESS) {
            notification = createInProgress(instance, tag, title);
            notification.setUrl(url);
            notification.setDescription("Executing " + type + "...");
        } else if (statusChanged && status == ExecutionStatus.SUCCEEDED) {
            notification = createSuccess(instance, tag, title);
            notification.setUrl(url);
        }

        if (notification != null) {
            var priorNotification = tag2notification.getIfPresent(notification.getTag());
            if (priorNotification != null) {
                priorNotification.updateState(notification);
                notification = priorNotification;
            } else {
                tag2notification.put(notification.getTag(), notification);
            }
            var proto = notification.toProto();
            listeners.forEach(l -> l.onNotification(proto));
        }
    }

    private String getActivityItemTitle(TimelineActivity activity) {
        var title = activity.getName();
        if (title == null) {
            title = "(no title)";
        }
        return title;
    }

    private String getActivityLink(YamcsServerInstance instanceObj, TimelineActivity activity) {
        var qsEncoder = new QueryStringEncoder(TIMELINE_URL);

        var c = instanceObj.getName();
        var firstProcessor = instanceObj.getFirstProcessor();
        if (firstProcessor != null) {
            c += "__" + firstProcessor.getName();
        }
        qsEncoder.addParam(TIMELINE_URL_C, c);

        var start = activity.getStart();
        qsEncoder.addParam(TIMELINE_URL_DATE, TimeEncoding.toString(start));
        return qsEncoder.toString();
    }
}
