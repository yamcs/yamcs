package org.yamcs.yarch.streamsql;

import org.yamcs.utils.ValueHelper;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class DescribeStatement extends StreamSqlStatement {

    private String objectName;

    public DescribeStatement(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("name");

        TableDefinition tdef = null;
        Stream stream = null;
        synchronized (dict) {
            tdef = dict.getTable(objectName);
            stream = dict.getStream(objectName);
        }
        if (tdef != null) {
            return describeTable(tdef);
        } else if (stream != null) {
            return describeStream(stream);
        } else {
            throw new ResourceNotFoundException(objectName);
        }
    }

    private StreamSqlResult describeTable(TableDefinition tdef) {
        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("column", "type", "key");
        for (ColumnDefinition cdef : tdef.getKeyDefinition().getColumnDefinitions()) {
            res.addRow(
                    ValueHelper.newValue(cdef.getName()),
                    ValueHelper.newValue(cdef.getType().toString()),
                    ValueHelper.newValue("*"));
        }
        for (ColumnDefinition cdef : tdef.getValueDefinition().getColumnDefinitions()) {
            res.addRow(
                    ValueHelper.newValue(cdef.getName()),
                    ValueHelper.newValue(cdef.getType().toString()),
                    null);
        }
        return res;
    }

    private StreamSqlResult describeStream(Stream stream) {
        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("column", "type");
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            res.addRow(
                    ValueHelper.newValue(cdef.getName()),
                    ValueHelper.newValue(cdef.getType().toString()));
        }
        return res;
    }
}
