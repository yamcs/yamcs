package org.yamcs.parameter;

import java.util.Iterator;
import java.util.List;

import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Archives and retrieves the value of parameters that have the persistence flag activated.
 * 
 */
public class ParameterPersistence {
    final static String TABLE_NAME = "param_persistence";
    final static Log log = new Log(ParameterPersistence.class);

    public static final TupleDefinition TDEF = new TupleDefinition();
    public static final String CNAME_SAVETIME = "savetime";
    public static final String CNAME_PROCESSOR = "processor";
    static {
        TDEF.addColumn(CNAME_SAVETIME, DataType.TIMESTAMP);
        TDEF.addColumn(CNAME_PROCESSOR, DataType.ENUM);
    }

    final Stream stream;
    final String yamcsInstance;
    final String processor;

    public ParameterPersistence(String yamcsInstance, String processor) throws InitException {
        this.yamcsInstance = yamcsInstance;
        this.processor = processor;
        try {
            this.stream = setupRecording();
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    private Stream setupRecording() throws StreamSqlException, ParseException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        String streamName = TABLE_NAME + "_in";
        if (ydb.getTable(TABLE_NAME) == null) {
            String query = "create table " + TABLE_NAME + "(" + TDEF.getStringDefinition1()
                    + ", primary key(savetime, processor))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    /**
     * Called at startup to load the parameters saved before the server shutdown
     * 
     * <p>
     * returns null if no record was found
     */
    public Iterator<ParameterValue> load() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            var r = ydb.execute(
                    "select * from " + TABLE_NAME + " where processor = ? order desc limit 1",
                    processor);
            if (r.hasNext()) {
                var t = r.next();
                r.close();
                List<?> cols = t.getColumns();
                if (cols.size() < 2) {
                    log.error("Invalid record retrieved from the param_persistence table: {}", t);
                    return null;
                }
                return (Iterator<ParameterValue>) t.getColumns().listIterator(2);
            }

        } catch (ParseException | StreamSqlException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    /**
     * Save all parameters with the persistence flag set
     */
    public void save(Iterator<ParameterValue> pvIterator) {
        Tuple tuple = new Tuple();
        tuple.addTimestampColumn(CNAME_SAVETIME, TimeEncoding.getWallclockTime());
        tuple.addColumn(CNAME_PROCESSOR, processor);
        while (pvIterator.hasNext()) {
            var pv = pvIterator.next();
            tuple.addColumn(pv.getParameterQualifiedName(), DataType.PARAMETER_VALUE, pv);
        }

        stream.emitTuple(tuple);
    }
}
