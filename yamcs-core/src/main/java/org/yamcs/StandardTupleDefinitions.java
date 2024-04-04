package org.yamcs;

import org.yamcs.alarms.AlarmStreamer;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.protobuf.Db.Event;

public class StandardTupleDefinitions {

    public static final String GENTIME_COLUMN = "gentime";
    public static final String SEQNUM_COLUMN = "seqNum";
    public static final String TM_RECTIME_COLUMN = "rectime";
    public static final String TM_STATUS_COLUMN = "status";
    public static final String TM_ERTIME_COLUMN = "ertime";
    public static final String TM_OBT_COLUMN = "obt";
    public static final String TM_PACKET_COLUMN = "packet";
    public static final String TM_LINK_COLUMN = "link";
    public static final String TM_ROOT_CONTAINER_COLUMN = "rootContainer";

    public static final String CMDHIST_TUPLE_COL_CMDNAME = "cmdName";

    public static final String PARAMETER_COL_RECTIME = "rectime";
    public static final String PARAMETER_COL_SEQ_NUM = "seqNum";

    public static final String PARAMETER_COL_GROUP = "group";
    public static final String PARAMETER_COL_GENTIME = "gentime";

    public static final String TC_ORIGIN_COLUMN = "origin";
    public static final String PARAMETER_COLUMN = "parameter";

    public static final String SOURCE_COLUMN = "source";
    public static final String BODY_COLUMN = "body";

    public static final TupleDefinition TM = new TupleDefinition();
    public static final TupleDefinition INVALID_TM = new TupleDefinition();
    static {
        TM.addColumn(GENTIME_COLUMN, DataType.TIMESTAMP);
        TM.addColumn(SEQNUM_COLUMN, DataType.INT);
        // reception or recording time (useful in case we import data from other recordings which provide this)
        TM.addColumn(TM_RECTIME_COLUMN, DataType.TIMESTAMP);
        TM.addColumn(TM_STATUS_COLUMN, DataType.INT);

        TM.addColumn(TM_PACKET_COLUMN, DataType.BINARY);

        // earth reception time
        TM.addColumn(TM_ERTIME_COLUMN, DataType.HRES_TIMESTAMP);
        TM.addColumn(TM_OBT_COLUMN, DataType.LONG);
        TM.addColumn(TM_LINK_COLUMN, DataType.ENUM);
        TM.addColumn(TM_ROOT_CONTAINER_COLUMN, DataType.ENUM);

    }
    static {
        INVALID_TM.addColumn(TM_RECTIME_COLUMN, DataType.TIMESTAMP);
        INVALID_TM.addColumn(SEQNUM_COLUMN, DataType.LONG);
        INVALID_TM.addColumn(TM_PACKET_COLUMN, DataType.BINARY);
    }

    public static final TupleDefinition TC = new TupleDefinition();
    // this is the commandId (used as the primary key when recording), other columns are handled dynamically
    static {
        TC.addColumn(GENTIME_COLUMN, DataType.TIMESTAMP);
        TC.addColumn(TC_ORIGIN_COLUMN, DataType.STRING);
        TC.addColumn(SEQNUM_COLUMN, DataType.INT);
        TC.addColumn(CMDHIST_TUPLE_COL_CMDNAME, DataType.ENUM);
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
        EVENT.addColumn(GENTIME_COLUMN, DataType.TIMESTAMP);
        EVENT.addColumn(SOURCE_COLUMN, DataType.ENUM);
        EVENT.addColumn(SEQNUM_COLUMN, DataType.INT);
        EVENT.addColumn(BODY_COLUMN, DataType.protobuf(Event.class.getName()));
    }

    public static final TupleDefinition PARAMETER_ALARM = new TupleDefinition();
    // user time, parameter name sequence number
    static {
        PARAMETER_ALARM.addColumn(AlarmStreamer.CNAME_TRIGGER_TIME, DataType.TIMESTAMP);
        PARAMETER_ALARM.addColumn(PARAMETER_COLUMN, DataType.STRING);
        PARAMETER_ALARM.addColumn(SEQNUM_COLUMN, DataType.INT);
    }

    public static final TupleDefinition EVENT_ALARM = new TupleDefinition();
    // user time, parameter name sequence number
    static {
        EVENT_ALARM.addColumn(AlarmStreamer.CNAME_TRIGGER_TIME, DataType.TIMESTAMP);
        EVENT_ALARM.addColumn("eventSource", DataType.STRING);
        EVENT_ALARM.addColumn(SEQNUM_COLUMN, DataType.INT);
    }

}
