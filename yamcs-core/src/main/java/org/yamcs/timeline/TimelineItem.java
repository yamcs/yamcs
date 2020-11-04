package org.yamcs.timeline;

import com.google.protobuf.util.Durations;
import org.yamcs.protobuf.RelativeTime;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

import java.util.List;
import java.util.UUID;

import static org.yamcs.timeline.TimelineItemDb.*;

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
    protected final UUID id;
    protected long start, duration;

    // if the item start is relative to another item
    protected UUID relativeItemUuid;
    protected long relativeStart;

    // if the item is part of a group
    protected UUID groupUuid;

    protected String source;

    protected String name;
    protected String tooltip;
    protected String description;
    protected List<String> tags;

    protected TimelineItem(Tuple tuple) {
        this.id = tuple.getColumn(CNAME_ID);
        this.start = tuple.getTimestampColumn(CNAME_START);
        this.duration = tuple.getLongColumn(CNAME_DURATION);
        if (tuple.hasColumn(CNAME_TAGS)) {
            this.tags = tuple.getColumn(CNAME_TAGS);
        }
    }

    public TimelineItem(UUID id) {
        this.id = id;
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

    public UUID getId() {
        return id;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    protected abstract void addToProto(org.yamcs.protobuf.TimelineItem.Builder protob);

    public org.yamcs.protobuf.TimelineItem toProtoBuf() {
        org.yamcs.protobuf.TimelineItem.Builder protob = org.yamcs.protobuf.TimelineItem.newBuilder();
        protob.setUuid(id.toString());
        protob.setStart(TimeEncoding.toProtobufTimestamp(start));
        protob.setDuration(Durations.fromMillis(duration));

        if (relativeItemUuid != null) {
            RelativeTime relTime = RelativeTime.newBuilder()
                    .setUuid(relativeItemUuid.toString())
                    .setRelativeStart(Durations.fromMillis(relativeStart))
                    .build();
            protob.setRelativeTime(relTime);
        }
        if (tags != null) {
            protob.addAllTags(tags);
        }
        addToProto(protob);
        return protob.build();
    }

    public Tuple toTuple() {
        Tuple tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addTimestampColumn(CNAME_START, start);
        tuple.addColumn(CNAME_DURATION, duration);
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
        addToTuple(tuple);
        return tuple;
    }

    protected abstract void addToTuple(Tuple tuple);

    public static TimelineItem fromTuple(Tuple tuple) {
        String types = tuple.getColumn("type");
        TimelineItemType type = TimelineItemType.valueOf(types);
        switch(type) {
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
