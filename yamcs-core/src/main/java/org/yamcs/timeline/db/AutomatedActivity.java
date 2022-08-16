package org.yamcs.timeline.db;

import static org.yamcs.timeline.db.TimelineItemDb.CNAME_ACTIVITY_RUN_ID;

import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.protobuf.timeline.TimelineItem.Builder;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class AutomatedActivity extends Activity {

    /**
     * If the automated activity has started this gives the id of the run
     */
    UUID runId;

    public AutomatedActivity(UUID id) {
        super(TimelineItemType.AUTO_ACTIVITY, id);
        this.autoRun = false;
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
        super.addToProto(detail, protob);
        if (detail && runId != null) {
            protob.setActivityRunId(runId.toString());
        }
    }

    AutomatedActivity(Tuple tuple) {
        super(TimelineItemType.AUTO_ACTIVITY, tuple);

        if (tuple.hasColumn(CNAME_ACTIVITY_RUN_ID)) {
            this.runId = tuple.getColumn(CNAME_ACTIVITY_RUN_ID);
        }
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        super.addToTuple(tuple);
        if (runId != null) {
            tuple.addColumn(CNAME_ACTIVITY_RUN_ID, DataType.UUID, runId);
        }
    }
}
