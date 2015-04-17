package org.yamcs.yarch.tokyocabinet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.tokyocabinet.TcPartitionManager;

import com.google.common.io.Files;

import org.yamcs.utils.TimeEncoding;

import static org.junit.Assert.*;

public class PartitionManagerTest {
    
    @BeforeClass
    static public void init() {
        TimeEncoding.setUp();
    }
 
    TableDefinition getTableDef() throws Exception {
    	
        TupleDefinition tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        
       
        TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
        
        PartitioningSpec spec=PartitioningSpec.timeAndValueSpec("gentime", "packetid");
        spec.setTimePartitioningSchema("YYYY/DOY");
        tblDef.setPartitioningSpec(spec);
        
        return tblDef;
    }
    
    @Test
    public void createAndIteratePartitions() throws Exception {
    	 String tmpdir=Files.createTempDir().getAbsolutePath();
        
    	 TableDefinition tblDef= getTableDef();
        
        TcPartitionManager pm=new TcPartitionManager(tblDef);
        TcPartition part= (TcPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-01-01T00:00:00"), 1);
        assertEquals("2011/001/tbltest#1.tcb", part.filename);
        
        part = (TcPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"),1);
        assertEquals("2011/060/tbltest#1.tcb",part.filename);
        
        part = (TcPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"),2);
        assertEquals("2011/032/tbltest#2.tcb",part.filename);
        
        part = (TcPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-02-01T00:00:00"),3);
        assertEquals("2011/032/tbltest#3.tcb",part.filename);
        
        part = (TcPartition) pm.createAndGetPartition(TimeEncoding.parse("2011-03-01T00:00:00"),3);
        assertEquals("2011/060/tbltest#3.tcb", part.filename);
        
        Set<Object>filter=new HashSet<Object>();
        filter.add(1);
        filter.add(3);
        Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-02-01T00:00:00"), filter);
        assertTrue(it.hasNext());
        List<Partition> parts=it.next();
        assertEquals(1, parts.size());
        
        assertTrue(it.hasNext());
        parts=it.next();
        assertEquals("2011/060/tbltest#1.tcb", ((TcPartition)parts.get(0)).filename);
        assertEquals("2011/060/tbltest#3.tcb", ((TcPartition)parts.get(1)).filename);
        
        deleteRecursively(new File(tmpdir));
    }
    
    @Test
    public void readFromDisk() throws Exception {
    	
    	TableDefinition tblDef= getTableDef();
    	
        String tmpdir=Files.createTempDir().getAbsolutePath();
        tblDef.setDataDir(tmpdir);
        new File(tmpdir+"/2011/001").mkdirs();
        new File(tmpdir+"/2011/032").mkdirs();
        new File(tmpdir+"/2011/060").mkdirs();
        
        Files.touch(new File(tmpdir+"/2011/001/tbltest#1.tcb"));
        Files.touch(new File(tmpdir+"/2011/060/tbltest#1.tcb"));
        Files.touch(new File(tmpdir+"/2011/032/tbltest#2.tcb"));
        Files.touch(new File(tmpdir+"/2011/032/tbltest#3.tcb"));
        Files.touch(new File(tmpdir+"/2011/060/tbltest#3.tcb"));
        TcPartitionManager pm=new TcPartitionManager(tblDef);
        pm.readPartitions();
        
        Set<Object>filter=new HashSet<Object>();
        filter.add(1);
        filter.add(3);
        
        
        Iterator<List<Partition>> it=pm.iterator(TimeEncoding.parse("2011-02-03T00:00:00"), filter);
        assertTrue(it.hasNext());
        List<Partition> parts=it.next();
        assertEquals("2011/060/tbltest#1.tcb", ((TcPartition)parts.get(0)).filename);
        assertEquals("2011/060/tbltest#3.tcb", ((TcPartition)parts.get(1)).filename);
        assertFalse(it.hasNext());
        
        deleteRecursively(new File(tmpdir));
    } 
    
    private void deleteRecursively(File f) throws IOException {
    	Runtime.getRuntime().exec(new String[] {"rm", "-rf", f.getAbsolutePath()});
    }
}
