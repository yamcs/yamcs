package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.FileSystemException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;

public class RDBFactoryTest {	
    @BeforeClass
    public static void initRocksDb() {
	RocksDB.loadLibrary();
    }

    private boolean isOpen(YRDB yrdb) {
	return yrdb.isOpen();	
    }
    TableDefinition getTableDef() throws Exception {
	PartitioningSpec spec = PartitioningSpec.timeAndValueSpec("gentime", "packetid");

	TupleDefinition tdef=new TupleDefinition();
	tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
	tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));


	TableDefinition tblDef = new TableDefinition("tbltest", tdef, Arrays.asList("gentime"));
	tblDef.setPartitioningSpec(spec);

	return tblDef;
    }


    @Test
    public void testDispose() throws Exception {
	TableDefinition tblDef= getTableDef();
	YRDB[] dbs=new YRDB[RDBFactory.maxOpenDbs*2];
	RDBFactory rdbf=new RDBFactory("testDispose");
	ColumnValueSerializer cvs= new ColumnValueSerializer(tblDef);

	for(int i=0;i<RDBFactory.maxOpenDbs;i++) {
	    dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, cvs, false);
	}
	for(int i=0;i<RDBFactory.maxOpenDbs/2;i++) {
	    rdbf.dispose(dbs[i]);
	}
	for(int i=0;i<RDBFactory.maxOpenDbs;i++) {
	    assertTrue(isOpen(dbs[i]));
	}
	for(int i=RDBFactory.maxOpenDbs;i<2*RDBFactory.maxOpenDbs;i++) {
	    dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, cvs, false);
	}
	for(int i=0;i<RDBFactory.maxOpenDbs/2;i++) {
	    assertFalse(isOpen(dbs[i]));
	}
	for(int i=RDBFactory.maxOpenDbs/2;i<2*RDBFactory.maxOpenDbs;i++) {
	    assertTrue(isOpen(dbs[i]));
	}
	//cleanup
	for(int i=0; i<2*RDBFactory.maxOpenDbs; i++) {
	    File d=new File("/tmp/rdbfactorytest"+i);     
	    FileUtils.deleteRecursively(d.toPath());
	}
    }
    
    
    @Test
    public void testBackup() throws Exception {
        String dir = "/tmp/rdb_backup_test/";
        FileUtils.deleteRecursively(dir);
        RDBFactory rdbf = new RDBFactory("testBackup");
        new File(dir).mkdirs();
        
        YRDB db1 = rdbf.getRdb(dir+"/db1", new StringColumnFamilySerializer(), false);
        ColumnFamilyHandle cfh = db1.createColumnFamily("c1");
        db1.put(cfh, "aaa".getBytes(), "bbb".getBytes());
        
        db1.createColumnFamily("c2");

        new File(dir+"/db1_back").mkdirs();
        rdbf.doBackup(dir+"/db1", dir+"/db1_back").get();
        
        db1.createColumnFamily("c3");
        rdbf.doBackup(dir+"/db1", dir+"/db1_back").get();
        
        //try to backup on top of existing non backup directory -> should throw an exception
        Throwable e = null;
        try {
            rdbf.doBackup(dir+"/db1", dir+"/db1").get();
        } catch (ExecutionException e1) {
            e = e1.getCause();
        }
        assertNotNull(e);
        assertTrue(e instanceof FileSystemException);
        
        db1.put(cfh, "aaa1".getBytes(), "bbb1".getBytes());
        byte[] b = db1.get(cfh, "aaa1".getBytes());
        assertNotNull(b);
        rdbf.close(db1);
        
        rdbf.restoreBackup(1, dir+"/db1_back", dir+"/db2").get();
        YRDB db2 = rdbf.getRdb(dir+"/db2", new StringColumnFamilySerializer(), false);
        
        assertNotNull(db2.getColumnFamilyHandle("c2"));
        assertNull(db2.getColumnFamilyHandle("c3"));
        
        ColumnFamilyHandle cfh_db2= db2.getColumnFamilyHandle("c1");
        assertNotNull(cfh_db2);
        
        b = db2.get(cfh_db2, "aaa".getBytes());
        assertNotNull(b);
        b = db2.get(cfh_db2, "aaa1".getBytes());
        assertNull(b);
        
        
        rdbf.restoreBackup(-1, dir+"/db1_back", dir+"/db3").get();
        YRDB db3 = rdbf.getRdb(dir+"/db3", new StringColumnFamilySerializer(), false);
        
        assertNotNull(db3.getColumnFamilyHandle("c2"));
        assertNotNull(db3.getColumnFamilyHandle("c3"));
    }

}
