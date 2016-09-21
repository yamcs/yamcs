package org.yamcs.yarch.rocksdb2;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;
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
	    dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, false);
	}
	for(int i=0;i<RDBFactory.maxOpenDbs/2;i++) {
	    rdbf.dispose(dbs[i]);
	}
	for(int i=0;i<RDBFactory.maxOpenDbs;i++) {
	    assertTrue(isOpen(dbs[i]));
	}
	for(int i=RDBFactory.maxOpenDbs;i<2*RDBFactory.maxOpenDbs;i++) {
	    dbs[i]=rdbf.getRdb("/tmp/rdbfactorytest"+i, false);
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
}
