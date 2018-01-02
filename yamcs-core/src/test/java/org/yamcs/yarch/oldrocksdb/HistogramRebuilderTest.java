package org.yamcs.yarch.oldrocksdb;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.TableWriter.InsertMode;


public class HistogramRebuilderTest  extends YarchTestCase {
    String tblName = "HistogramRebuilderTest";
    TableDefinition tblDef;
    RdbStorageEngine rse;
    long t1 = TimeEncoding.parse("2016-12-16T00:00:00");
    
    @Before
    public void populate() throws Exception {
        String query="create table "+tblName+"(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name) partition by time(gentime) table_format=compressed engine oldrocksdb";
        ydb.execute(query);
        
        tblDef= ydb.getTable(tblName);
        rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{1000L, 10, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{2000L, 20, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{3000L, 30, "p2"}));
        
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{t1, 30, "p2"}));
        tw.close();
    }
    
    @After
    public void dropTable() throws Exception {
        ydb.execute("drop table "+tblName);
    }
    
    @Test
    public void testDeleteValues() throws Exception {
        TimeInterval interval = new TimeInterval();
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();
        
        HistogramRebuilder rebuilder = new HistogramRebuilder(ydb,  tblName);
        
        rebuilder.deleteHistograms(new TimeInterval(1000L, 1000L));
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 1);
        iter.close();
        
        assertNotNull(getHistoCf(1000L, "name"));
        
        rebuilder.rebuild(new TimeInterval(0, 2000)).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();
    }

    
    @Test
    public void testDeleteCfh() throws Exception {
        TimeInterval interval = new TimeInterval();
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();
        
        HistogramRebuilder rebuilder = new HistogramRebuilder(ydb,  tblName);
        rebuilder.deleteHistograms(TimeInterval.openStart(1000L));
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 1);
        iter.close();
        
        assertNull(getHistoCf(1000L, "name"));
        assertNull(getHistoCf(t1, "name"));

        rebuilder.rebuild(TimeInterval.openStart(t1)).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        
        assertNumElementsEqual(iter, 3);
        iter.close();
    }
    
    @Test
    public void testRebuildAll() throws Exception {
        TimeInterval interval = new TimeInterval();
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        iter.close();
        
        HistogramRebuilder rebuilder = new HistogramRebuilder(ydb,  tblName);
        rebuilder.deleteHistograms(new TimeInterval());
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 0);
        iter.close();
        
        assertNull(getHistoCf(1000L, "name"));
        assertNull(getHistoCf(t1, "name"));

        rebuilder.rebuild(new TimeInterval()).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        
        assertNumElementsEqual(iter, 3);
        iter.close();
    }
    
    private ColumnFamilyHandle getHistoCf(long start, String colName) throws Exception {
        String cfHistoName = AbstractTableWriter.getHistogramColumnFamilyName(colName);
        
        RdbPartition p0 = getFirstPartition(1000L);
        YRDB rdb = RDBFactory.getInstance(ydb.getName()).getRdb(tblDef.getDataDir()+"/"+p0.dir, false);
        return rdb.getColumnFamilyHandle(cfHistoName);
    }
    
    private RdbPartition getFirstPartition(long start) {
        Iterator<List<Partition>> it = rse.getPartitionManager(tblDef).iterator(start, null);
        if(!it.hasNext()) return null;
        return (RdbPartition) it.next().get(0);
    }
    
}
