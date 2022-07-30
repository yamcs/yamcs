package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.*;

import java.util.List;
import java.util.UUID;

import org.yamcs.protobuf.RelativeTime;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.util.Durations;

/**
 * A timeline item is the entity that appears in the timeline bars.
 * <p>
 * They can be events or activities.
 * <p>
 * Each item is identified by an id (UUID).
 * 
 * @author nm
 *
 */
public abstract class TimelineItem {
    protected final String id;
    protected final TimelineItemType type;

    protected long start, duration;

    // if relativeItemUuid!= null -> the item start is relative to another item
    protected UUID relativeItemUuid;
    protected long relativeStart;

    // if the item is part of a group
    protected UUID groupUuid;

    protected String source;

    protected String name;
    protected String tooltip;
    protected String description;
    protected List<String> tags;

    protected TimelineItem(TimelineItemType type, Tuple tuple) {
        this.id = ((UUID) tuple.getColumn(CNAME_ID)).toString();
        this.type = type;
        this.start = tuple.getTimestampColumn(CNAME_START);
        this.duration = tuple.getLongColumn(CNAME_DURATION);

        if (tuple.hasColumn(CNAME_NAME)) {
            this.name = tuple.getColumn(CNAME_NAME);
        }
        if (tuple.hasColumn(CNAME_TAGS)) {
            this.tags = tuple.getColumn(CNAME_TAGS);
        }
        if (tuple.hasColumn(CNAME_DESCRIPTION)) {
            this.description = tuple.getColumn(CNAME_DESCRIPTION);
        }
    }

    public TimelineItem(TimelineItemType type, String id) {
        this.id = id;
        this.type = type;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getDuration() {
        return duration;
    }

    public List<String> getTags() {
        return tags;
    }

    public UUID getRelativeItemUuid() {
        return relativeItemUuid;
    }

    public void setRelativeItemUuid(UUID relativeItemUuid) {
        this.relativeItemUuid = relativeItemUuid;
    }

    public long getRelativeStart() {
        return relativeStart;
    }

    public void setRelativeStart(long relativeStart) {
        this.relativeStart = relativeStart;
    }

    public UUID getGroupUuid() {
        return groupUuid;
    }

    public void setGroupUuid(UUID groupUuid) {
        this.groupUuid = groupUuid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public TimelineItemType getType() {
        return type;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    protected abstract void addToProto(boolean detail, org.yamcs.protobuf.TimelineItem.Builder protob);

    public org.yamcs.protobuf.TimelineItem toProtoBuf(boolean detail) {
        org.yamcs.protobuf.TimelineItem.Builder protob = org.yamcs.protobuf.TimelineItem.newBuilder();
        protob.setType(type);
        protob.setId(id.toString());
        protob.setStart(TimeEncoding.toProtobufTimestamp(start));
        protob.setDuration(Durations.fromMillis(duration));
        if (name != null) {
            protob.setName(name);
        }

        if (relativeItemUuid != null) {
            RelativeTime relTime = RelativeTime.newBuilder()
                    .setRelto(relativeItemUuid.toString())
                    .setRelativeStart(Durations.fromMillis(relativeStart))
                    .build();
            protob.setRelativeTime(relTime);
        }
        if (tags != null) {
            protob.addAllTags(tags);
        }

        if (detail) {
            if (description != null) {
                protob.setDescription(description);
            }
        }

        addToProto(detail, protob);
        return protob.build();
    }

    public Tuple toTuple() {
        Tuple tuple = new Tuple();

        tuple.addColumn(CNAME_ID, DataType.UUID, UUID.fromString(id));
        tuple.addEnumColumn(CNAME_TYPE, type.name());
        tuple.addTimestampColumn(CNAME_START, start);
        tuple.addColumn(CNAME_DURATION, duration);
        if (name != null) {
            tuple.addColumn(CNAME_NAME, name);
        }
        if (tags != null) {
            tuple.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM), tags);
        }
        if (relativeItemUuid != null) {
            tuple.addColumn(CNAME_RELTIME_ID, DataType.UUID, relativeItemUuid);
            tuple.addColumn(CNAME_RELTIME_START, DataType.LONG, relativeStart);
        }
        if (groupUuid != null) {
            tuple.addColumn(CNAME_GROUP_ID, DataType.UUID, groupUuid);
        }
        if (description != null) {
            tuple.addColumn(CNAME_DESCRIPTION, DataType.STRING, description);
        }

        addToTuple(tuple);
        return tuple;
    }

    protected abstract void addToTuple(Tuple tuple);

    public static TimelineItem fromTuple(Tuple tuple) {
        String types = tuple.getColumn("type");
        TimelineItemType type = TimelineItemType.valueOf(types);
        switch (type) {
        case ACTIVITY_GROUP:
            return new ActivityGroup(tuple);
        case AUTO_ACTIVITY:
            return new AutomatedActivity(tuple);
        case EVENT:
            return new TimelineEvent(tuple);
        case ITEM_GROUP:
            return new ItemGroup(tuple);
        case MANUAL_ACTIVITY:
            return new ManualActivity(tuple);
        }
        return null;
    }

}
