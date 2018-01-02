package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Iterator;

import org.junit.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchTestCase;



public class RdbEngineTest extends YarchTestCase {

    @Test
    public void testCreateDrop() throws Exception {
        RdbStorageEngine rse = new RdbStorageEngine();

        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));	        	       
        TableDefinition tblDef = new TableDefinition("RdbEngineTest", tdef, Arrays.asList("gentime"));


        PartitioningSpec pspec=PartitioningSpec.timeAndValueSpec("gentime", "packetid");
        pspec.setValueColumnType(DataType.INT);
        tblDef.setPartitioningSpec(pspec);

        checkNoReaderStreamPossible(rse, tblDef);

        rse.createTable(ydb, tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        Tuple t = new Tuple(tdef, new Object[]{1000L, 10});
        tw.onTuple(null, t);


        rse.dropTable(ydb, tblDef);

        checkNoReaderStreamPossible(rse, tblDef);
    }

    private void checkNoReaderStreamPossible(RdbStorageEngine rse, TableDefinition tblDef) {
        IllegalStateException iae=null;
        try {
            rse.newTableReaderStream(ydb, tblDef, true, true);
        } catch (IllegalStateException e) {
            iae=e;
        }
        assertNotNull(iae);
    }

    public TableDefinition populate() throws Exception {
        String query="create table table1(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name) "
                + "partition by time(gentime) table_format=compressed engine rocksdb2";
        ydb.execute(query);
        long t1 = TimeEncoding.parse("2016-12-16T00:00:00");

        TableDefinition tblDef= ydb.getTable("table1");
        RdbStorageEngine    rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{1000L, 10, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{2000L, 20, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{3000L, 30, "p2"}));

        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{t1, 30, "p2"}));
        tw.close();
        return tblDef;
    }

    @Test
    public void testOpenClose() throws Exception {
        TableDefinition tblDef = populate();
        RdbStorageEngine rse =  RdbStorageEngine.getInstance();
        TimeInterval interval = new TimeInterval();
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();

        rse.shutdown();

        rse.loadTablespaces(false);
        rse.loadTable(ydb, tblDef);
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();
    }
}
