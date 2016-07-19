package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.io.Files;

import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;

import static org.junit.Assert.*;

public class PartitionManagerTest {

    @BeforeClass
    static public void init() {
	TimeEncoding.setUp();
    }

    TableDefinition getTableDefTimeAndValue() throws Exception {
	TupleDefinition tdef=new TupleDefinition();
	tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
	tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
	PartitioningSpec spec=PartitioningSpec.timeAndValueSpec("gentime", "packetid");
	spec.setTimePartitioningSchema("YYYY/DOY");

	TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
	tblDef.setPartitioningSpec(spec);

	return tblDef;
    }
    
    TableDefinition getTableDefValue() throws Exception {
        TupleDefinition tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec=PartitioningSpec.valueSpec("packetid");

        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);

        return tblDef;
    }
    
    
    TableDefinition getTableDefTime() throws Exception {
        TupleDefinition tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec=PartitioningSpec.timeSpec("gentime");
        spec.setTimePartitioningSchema("YYYY/DOY");

        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);

        return tblDef;
    }

    @Test
    public void createAndIteratePartitions() throws Exception {
	String tmpdir=Files.createTempDir().getAbsolutePath();
	TableDefinition tblDef= getTableDefTimeAndValue();
	tblDef.setDataDir(tmpdir);

	RdbPartitionManager pm=new RdbPartitionManager(YarchDatabase.getInstance("test"), tblDef);
	RdbPartition part= (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-01-01T00:00:00"), 1);
	assertEquals("2011/001/tbltest", part.dir);

	part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"), 1);
	assertEquals("2011/060/tbltest",part.dir);

	part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"), 2);
	assertEquals("2011/032/tbltest",part.dir);

	part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"), 3);
	assertEquals("2011/032/tbltest",part.dir);

	part = (RdbPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"), 3);
	assertEquals("2011/060/tbltest", part.dir);

	Set<Object>filter=new HashSet<Object>();
	filter.add(1);
	filter.add(3);
	Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-02-01T00:00:00"), filter);
	assertTrue(it.hasNext());
	List<Partition> parts=it.next();
	assertEquals(1, parts.size());

	assertTrue(it.hasNext());
	parts=it.next();
	assertEquals("2011/060/tbltest", ((RdbPartition)parts.get(0)).dir);
	assertEquals("2011/060/tbltest", ((RdbPartition)parts.get(1)).dir);

	FileUtils.deleteRecursively(new File(tmpdir).toPath());
    }    


    @Test
    public void readFromDisk() throws Exception {    	
	TableDefinition tblDef= getTableDefTimeAndValue();

	String tmpdir=Files.createTempDir().getAbsolutePath();
	tblDef.setDataDir(tmpdir);

	new File(tmpdir+"/2011/001").mkdirs();
	new File(tmpdir+"/2011/032").mkdirs();
	new File(tmpdir+"/2011/060").mkdirs();

	YarchDatabase ydb = YarchDatabase.getInstance("test");
	RDBFactory rdbFactory = RDBFactory.getInstance("test");
	YRDB rdb = rdbFactory.getRdb(tmpdir+"/2011/001/tbltest", new ColumnValueSerializer(tblDef), false);
	rdb.createColumnFamily((int)1);
	rdbFactory.dispose(rdb);        

	rdb = rdbFactory.getRdb(tmpdir+"/2011/060/tbltest", new ColumnValueSerializer(tblDef), false);
	rdb.createColumnFamily((int)1);
	rdb.createColumnFamily((int)3);
	rdbFactory.dispose(rdb);

	rdb = rdbFactory.getRdb(tmpdir+"/2011/032/tbltest", new ColumnValueSerializer(tblDef), false);
	rdb.createColumnFamily((int)2);
	rdb.createColumnFamily((int)3);
	rdbFactory.dispose(rdb);
	rdbFactory.shutdown();

	RdbPartitionManager pm=new RdbPartitionManager(ydb, tblDef);
	pm.readPartitionsFromDisk();
	Collection<Partition> partitions = pm.getPartitions();
	assertEquals(5, partitions.size());

	Set<Object>filter=new HashSet<Object>();
	filter.add(1);
	filter.add(3);


	Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-02-03T00:00:00"), filter);
	assertTrue(it.hasNext());
	List<Partition> parts=it.next();
	RdbPartition p0 = (RdbPartition)parts.get(0);
	RdbPartition p1 = (RdbPartition)parts.get(1);
	assertEquals("2011/060/tbltest", p0.dir);
	assertEquals(1, p0.getValue());
	assertEquals("2011/060/tbltest", p1.dir);
	assertEquals(3, p1.getValue());
	assertFalse(it.hasNext());

	FileUtils.deleteRecursively(new File(tmpdir).toPath());
    }  
    
    
    @Test
    public void readFromDiskValue() throws Exception {       
        TableDefinition tblDef= getTableDefValue();

        String tmpdir=Files.createTempDir().getAbsolutePath();
        tblDef.setDataDir(tmpdir);


        YarchDatabase ydb = YarchDatabase.getInstance("test");
        RDBFactory rdbFactory = RDBFactory.getInstance("test");
        YRDB rdb = rdbFactory.getRdb(tmpdir+"/tbltest", new ColumnValueSerializer(tblDef), false);
        rdb.createColumnFamily((int)1);
        rdb.createColumnFamily((int)3);
        rdb.createColumnFamily((int)2);
        
        rdbFactory.dispose(rdb);
        rdbFactory.shutdown();

        RdbPartitionManager pm=new RdbPartitionManager(ydb, tblDef);
        pm.readPartitionsFromDisk();
        Collection<Partition> partitions = pm.getPartitions();
        assertEquals(3, partitions.size());

        Set<Object>filter=new HashSet<Object>();
        filter.add(1);
        filter.add(2);


        Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-02-03T00:00:00"), filter);
        assertTrue(it.hasNext());
        List<Partition> parts=it.next();
        RdbPartition p0 = (RdbPartition)parts.get(0);
        RdbPartition p1 = (RdbPartition)parts.get(1);
        assertEquals("tbltest", p0.dir);
        assertEquals(1, p0.getValue());
        assertEquals("tbltest", p1.dir);
        assertEquals(2, p1.getValue());
        assertFalse(it.hasNext());

        FileUtils.deleteRecursively(new File(tmpdir).toPath());
    }  

    
    @Test
    public void readFromDiskTime() throws Exception {       
        TableDefinition tblDef= getTableDefTime();

        String tmpdir=Files.createTempDir().getAbsolutePath();
        Logger.getGlobal().setLevel(Level.ALL);
        
        tblDef.setDataDir(tmpdir);

        new File(tmpdir+"/2011/001").mkdirs();
        new File(tmpdir+"/2011/032").mkdirs();
        new File(tmpdir+"/2011/060").mkdirs();

        YarchDatabase ydb = YarchDatabase.getInstance("test");
        RDBFactory rdbFactory = RDBFactory.getInstance("test");
        YRDB rdb = rdbFactory.getRdb(tmpdir+"/2011/001/tbltest", new ColumnValueSerializer(tblDef), false);
        rdbFactory.dispose(rdb);        

        rdb = rdbFactory.getRdb(tmpdir+"/2011/060/tbltest", new ColumnValueSerializer(tblDef), false);       
        rdbFactory.dispose(rdb);

        rdb = rdbFactory.getRdb(tmpdir+"/2011/032/tbltest", new ColumnValueSerializer(tblDef), false);
        rdbFactory.dispose(rdb);
        rdbFactory.shutdown();

        RdbPartitionManager pm = new RdbPartitionManager(ydb, tblDef);
        pm.readPartitionsFromDisk();
        Collection<Partition> partitions = pm.getPartitions();
        assertEquals(3, partitions.size());


        Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-01-03T00:00:00"), null);
        assertTrue(it.hasNext());
        List<Partition> parts=it.next();
        RdbPartition p0 = (RdbPartition)parts.get(0);
        assertEquals("2011/032/tbltest", p0.dir);
        assertTrue(it.hasNext());
        parts=it.next();
        RdbPartition p1 = (RdbPartition)parts.get(0);
        assertEquals("2011/060/tbltest", p1.dir);
        assertFalse(it.hasNext());

        FileUtils.deleteRecursively(new File(tmpdir).toPath());
    }  

    
    @Test
    public void testNoPartition() throws Exception {
        TupleDefinition tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        PartitioningSpec spec=PartitioningSpec.noneSpec();

        TableDefinition tblDef = new TableDefinition("tblnp", tdef, Arrays.asList("gentime"));
        tblDef.setPartitioningSpec(spec);
        
        String tmpdir=Files.createTempDir().getAbsolutePath();
        tblDef.setDataDir(tmpdir);


        YarchDatabase ydb = YarchDatabase.getInstance("test");
        RDBFactory rdbFactory = RDBFactory.getInstance("test");
        YRDB rdb = rdbFactory.getRdb(tmpdir+"/tblnp", new ColumnValueSerializer(tblDef), false);
        rdbFactory.dispose(rdb);        
    
        rdbFactory.shutdown();

        RdbPartitionManager pm=new RdbPartitionManager(ydb, tblDef);
        pm.readPartitionsFromDisk();
        Collection<Partition> partitions = pm.getPartitions();
        assertEquals(1, partitions.size());
        Partition p = partitions.iterator().next();
        assertNull(p.getValue());

        FileUtils.deleteRecursively(new File(tmpdir).toPath());
    } 
}
