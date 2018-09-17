package org.yamcs;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TupleDefinition;

public class StandardTupleDefinitions {

    public static final String TM_GENTIME_COLUMN = "gentime";
    public static final String TM_SEQNUM_COLUMN = "seqNum";
    public static final String TM_RECTIME_COLUMN = "rectime";
    public static final String TM_PACKET_COLUMN = "packet";
    public static final String CMDHIST_TUPLE_COL_CMDNAME = "cmdName";

    public static final String PARAMETER_COL_RECTIME = "rectime";
    public static final String PARAMETER_COL_SEQ_NUM = "seqNum";
    public static final String PARAMETER_COL_GROUP = "group";
    public static final String PARAMETER_COL_GENTIME = "gentime";

    public static final TupleDefinition TM = new TupleDefinition();
    static {
        TM.addColumn(TM_GENTIME_COLUMN, DataType.TIMESTAMP);
        TM.addColumn(TM_SEQNUM_COLUMN, DataType.INT);
        // reception or recording time (useful in case we import data from other recordings which provide this)
        TM.addColumn(TM_RECTIME_COLUMN, DataType.TIMESTAMP);
        TM.addColumn(TM_PACKET_COLUMN, DataType.BINARY);
    }

    public static final TupleDefinition TC = new TupleDefinition();
    // this is the commandId (used as the primary key when recording), ohter columns are handled dynamically
    static {
        TC.addColumn("gentime", DataType.TIMESTAMP);
        TC.addColumn("origin", DataType.STRING);
        TC.addColumn("seqNum", DataType.INT);
        TC.addColumn(CMDHIST_TUPLE_COL_CMDNAME, DataType.STRING);
    }

    public static final TupleDefinition PARAMETER = new TupleDefinition();
    // first columns from the PP tuples
    // the actual values are encoded as separated columns (umi_0x010203040506, value) value is ParameterValue
    static {
        PARAMETER.addColumn(PARAMETER_COL_GENTIME, DataType.TIMESTAMP); // generation time
        PARAMETER.addColumn(PARAMETER_COL_GROUP, DataType.ENUM); // group - used for partitioning
                                                                 // (i.e. splitting the archive
                                                                 // in multiple files)
        PARAMETER.addColumn(PARAMETER_COL_SEQ_NUM, DataType.INT); // sequence number
        PARAMETER.addColumn(PARAMETER_COL_RECTIME, DataType.TIMESTAMP); // recording time

    }

    public static final TupleDefinition EVENT = new TupleDefinition();
    // this is the commandId (used as the primary key when recording), the rest will be handled dynamically
    static {
        EVENT.addColumn("gentime", DataType.TIMESTAMP);
        EVENT.addColumn("source", DataType.ENUM);
        EVENT.addColumn("seqNum", DataType.INT);
        EVENT.addColumn("body", DataType.protobuf("org.yamcs.protobuf.Yamcs$Event"));
    }

    public static final TupleDefinition ALARM = new TupleDefinition();
    // user time, parameter name sequence number and event
    static {
        ALARM.addColumn("triggerTime", DataType.TIMESTAMP);
        ALARM.addColumn("parameter", DataType.STRING);
        ALARM.addColumn("seqNum", DataType.INT);
        ALARM.addColumn("event", DataType.STRING);
    }
}
