package org.yamcs.yarch.tokyocabinet;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.PartitioningSpec._type;
import org.yamcs.yarch.tokyocabinet.TcPartitionManager;

import com.google.common.io.Files;
import org.yamcs.utils.TimeEncoding;

import static org.junit.Assert.*;

public class PartitionManagerTest {
    
    static PartitioningSpec spec=new PartitioningSpec();
    static {
        spec.timeColumn="gentime";
        spec.type=_type.TIME_AND_VALUE;
        spec.valueColumn="packetid";
        spec.valueColumnType=DataType.INT;
    }

    
    @BeforeClass
    static public void init() {
        TimeEncoding.setUp();
    }
 
    @Test
    public void createAndIteratePartitions() throws IOException {
        
        TupleDefinition tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        
        String tmpdir=Files.createTempDir().getAbsolutePath();
        
        TcPartitionManager pm=new TcPartitionManager("tbltest", spec, tmpdir);
        String part=pm.createAndGetPartition(TimeEncoding.parse("2011-01-01T00:00:00"), 1);
        assertEquals(tmpdir+"/2011/001/tbltest#1",part);
        
        part=pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"),1);
        assertEquals(tmpdir+"/2011/060/tbltest#1",part);
        
        part=pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"),2);
        assertEquals(tmpdir+"/2011/032/tbltest#2",part);
        
        part=pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"),3);
        assertEquals(tmpdir+"/2011/032/tbltest#3",part);
        
        part=pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"),3);
        assertEquals(tmpdir+"/2011/060/tbltest#3", part);
        
        Set<Object>filter=new HashSet<Object>();
        filter.add(1);
        filter.add(3);
        Iterator<List<String>> it=pm.iterator(TimeEncoding.parse("2011-02-01T00:00:00"), filter);
        assertTrue(it.hasNext());
        List<String> parts=it.next();
        assertEquals(1, parts.size());
        
        assertTrue(it.hasNext());
        parts=it.next();
        assertEquals(tmpdir+"/2011/060/tbltest#1", parts.get(0));
        assertEquals(tmpdir+"/2011/060/tbltest#3", parts.get(1));
        
        Files.deleteRecursively(new File(tmpdir));
    }
    
    @Test
    public void readFromDisk() throws IOException {
        String tmpdir=Files.createTempDir().getAbsolutePath();
        new File(tmpdir+"/2011/001").mkdirs();
        new File(tmpdir+"/2011/032").mkdirs();
        new File(tmpdir+"/2011/060").mkdirs();
        
        Files.touch(new File(tmpdir+"/2011/001/tbltest#1.tcb"));
        Files.touch(new File(tmpdir+"/2011/060/tbltest#1.tcb"));
        Files.touch(new File(tmpdir+"/2011/032/tbltest#2.tcb"));
        Files.touch(new File(tmpdir+"/2011/032/tbltest#3.tcb"));
        Files.touch(new File(tmpdir+"/2011/060/tbltest#3.tcb"));
        TcPartitionManager pm=new TcPartitionManager("tbltest", spec, tmpdir);
        pm.readPartitions();
        
        Set<Object>filter=new HashSet<Object>();
        filter.add(1);
        filter.add(3);
        
        
        Iterator<List<String>> it=pm.iterator(TimeEncoding.parse("2011-02-03T00:00:00"), filter);
        assertTrue(it.hasNext());
        List<String> parts=it.next();
        assertEquals(tmpdir+"/2011/060/tbltest#1", parts.get(0));
        assertEquals(tmpdir+"/2011/060/tbltest#3", parts.get(1));
        assertFalse(it.hasNext());
        
        Files.deleteRecursively(new File(tmpdir));
    }  
    
}
