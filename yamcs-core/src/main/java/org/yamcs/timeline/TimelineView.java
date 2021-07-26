package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineViewDb.CNAME_BANDS;
import static org.yamcs.timeline.TimelineViewDb.CNAME_DESCRIPTION;
import static org.yamcs.timeline.TimelineViewDb.CNAME_ID;
import static org.yamcs.timeline.TimelineViewDb.CNAME_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class TimelineView {

    private final UUID id;

    private String name;
    private String description;
    private List<UUID> bands = new ArrayList<>();

    public TimelineView(UUID id) {
        this.id = id;
    }

    TimelineView(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        name = tuple.getColumn(CNAME_NAME);
        description = tuple.getColumn(CNAME_DESCRIPTION);
        if (tuple.getColumn(CNAME_BANDS) != null) {
            bands.addAll(tuple.getColumn(CNAME_BANDS));
        }
    }

    public UUID getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<UUID> getBands() {
        return bands;
    }

    public void setBands(List<UUID> bands) {
        this.bands.clear();
        this.bands.addAll(bands);
    }

    public org.yamcs.protobuf.TimelineView toProtobuf() {
        List<org.yamcs.protobuf.TimelineBand> bands = this.bands.stream()
                .map(id -> org.yamcs.protobuf.TimelineBand.newBuilder()
                        .setId(id.toString())
                        .build())
                .collect(Collectors.toList());
        org.yamcs.protobuf.TimelineView.Builder b = org.yamcs.protobuf.TimelineView.newBuilder()
                .setId(id.toString())
                .addAllBands(bands);
        if (name != null) {
            b.setName(name);
        }
        if (description != null) {
            b.setDescription(description);
        }
        return b.build();
    }

    public Tuple toTuple() {
        Tuple tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_NAME, name);
        tuple.addColumn(CNAME_DESCRIPTION, description);
        if (!bands.isEmpty()) {
            tuple.addColumn(CNAME_BANDS, DataType.array(DataType.UUID), bands);
        }
        return tuple;
    }
}
